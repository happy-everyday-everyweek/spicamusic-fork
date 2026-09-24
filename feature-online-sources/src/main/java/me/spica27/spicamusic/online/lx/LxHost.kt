package me.spica27.spicamusic.online.lx

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * 洛雪音源宿主。
 *
 * 一个 HandlerThread 承载全部 QuickJS 上下文：一个上下文跑打包好的内置音源（负责搜索与详情），
 * 另有一批上下文分别跑用户导入的自定义源脚本（负责取直链、歌词、封面）。
 * 两者通过同一套原生桥通信，因此上层拿到的都是同一个 OnlineSourcePort。
 */
class LxHost(
  context: Context,
  private val bridge: LxNativeBridge = LxNativeBridge(),
) {

  private val appContext = context.applicationContext
  private val thread = HandlerThread("lx-js").apply { start() }
  private val handler = Handler(thread.looper)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val counter = AtomicLong(0)

  private var bundleContext: QuickJSContext? = null
  private var bundleReady = false
  private val bundleKey = "bundle"
  private var loadedScripts: List<LoadedScript> = emptyList()
  private var scriptsReady = false

  private val bundleRequests = ConcurrentHashMap<String, ResultSink>()
  private val scriptRequests = ConcurrentHashMap<String, ResultSink>()
  private val scriptUpdateAlerts = ConcurrentHashMap<String, String>()

  @Volatile
  private var updateAlert: String? = null

  private class ResultSink(val onDone: (String?) -> Unit)

  private class LoadedScript(
    val key: String,
    val definition: LxScriptDefinition,
    val context: QuickJSContext,
    val sources: MutableSet<String> = mutableSetOf(),
  )

  fun lastUpdateAlert(): String? = updateAlert

  fun clearUpdateAlert() {
    updateAlert = null
  }

  /** 装载内置音源与自定义源脚本。可重复调用以刷新脚本列表。 */
  fun start(definitions: List<LxScriptDefinition>) {
    handler.post {
      ensureBundle()
      loadScripts(definitions)
    }
  }

  fun shutdown() {
    handler.post {
      runCatching { bundleContext?.destroy() }
      loadedScripts.forEach { runCatching { it.context.destroy() } }
      bundleContext = null
      loadedScripts = emptyList()
      bundleReady = false
      scriptsReady = false
    }
    thread.quitSafely()
  }

  /** 内置音源支持的平台列表。 */
  suspend fun availablePlatforms(): List<String> {
    ensureStarted()
    return withJs { ctx ->
      val result = ctx.evaluate("JSON.stringify(LxMusicSdk.sources())") as? String ?: "[]"
      runCatching {
        val array = JSONArray(result)
        buildList {
          for (index in 0 until array.length()) {
            add(array.optJSONObject(index)?.optString("id").orEmpty())
          }
        }.filter { it.isNotBlank() }
      }.getOrElse { emptyList() }
    } ?: emptyList()
  }

  /** 单平台搜索：返回该平台的原始列表（JSON 字符串）。 */
  suspend fun searchPlatform(platform: String, keyword: String, limit: Int = 25, timeoutMs: Long = 20_000L): String? {
    ensureStarted()
    return requestBundle(timeoutMs) { ctx, requestId ->
      ctx.evaluate("LxMusicSdk.runSearchSource(${quote(requestId)}, ${quote(platform)}, ${quote(keyword)}, $limit)")
    }
  }

  /** 取直链：先交给自定义源脚本（与落雪一致，取链统一走音源接口）。 */
  suspend fun resolveUrl(platform: String, songInfoJson: String, quality: String, timeoutMs: Long = 20_000L): String? {
    ensureStarted()
    val payload = JSONObject()
      .put("source", platform)
      .put("songInfo", JSONObject(songInfoJson))
      .put("quality", quality)
      .toString()
    return callScript(platform, "musicUrl", payload, timeoutMs)
  }

  suspend fun lyric(platform: String, songInfoJson: String, timeoutMs: Long = 20_000L): String? {
    ensureStarted()
    val payload = JSONObject()
      .put("source", platform)
      .put("songInfo", JSONObject(songInfoJson))
      .toString()
    return callScript(platform, "lyric", payload, timeoutMs)
  }

  suspend fun picture(platform: String, songInfoJson: String, timeoutMs: Long = 20_000L): String? {
    ensureStarted()
    val payload = JSONObject()
      .put("source", platform)
      .put("songInfo", JSONObject(songInfoJson))
      .toString()
    return callScript(platform, "pic", payload, timeoutMs)
  }

  suspend fun detail(platform: String, songInfoJson: String, timeoutMs: Long = 20_000L): String? {
    ensureStarted()
    return requestBundle(timeoutMs) { ctx, requestId ->
      ctx.evaluate(
        "LxMusicSdk.runDetail(${quote(requestId)}, ${quote(platform)}, ${quote(songInfoJson)})",
      )
    }
  }

  // ---- 内部实现 ----

  private fun ensureStarted() {
    if (bundleReady && scriptsReady) return
    val latch = java.util.concurrent.CountDownLatch(1)
    handler.post {
      ensureBundle()
      loadScripts(loadedScripts.map { it.definition })
      latch.countDown()
    }
    latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
  }

  private fun ensureBundle() {
    if (bundleReady) return
    QuickJSLoader.init()
    val ctx = QuickJSContext.create()
    registerNativeBridge(ctx, bundleKey)
    val script = readAsset("lx/musicSdk.js")
    if (script.isNullOrBlank()) {
      Timber.tag("LxHost").e("没有找到内置音源，请先执行音源打包步骤")
      return
    }
    ctx.evaluate(script)
    bundleContext = ctx
    bundleReady = true
  }

  private fun loadScripts(definitions: List<LxScriptDefinition>) {
    loadedScripts.forEach { runCatching { it.context.destroy() } }
    loadedScripts = definitions.mapNotNull { definition ->
      runCatching {
        val ctx = QuickJSContext.create()
        val key = definition.id
        registerNativeBridge(ctx, key)
        val preload = readAsset("lx/user-api-preload.js")
        if (preload.isNullOrBlank()) throw IllegalStateException("缺少预置脚本")
        ctx.evaluate(preload)
        ctx.globalObject
          .getJSFunction("lx_setup")
          .call(key, definition.id, definition.name, definition.description, definition.version, definition.author, definition.homepage, definition.rawScript)
        ctx.evaluate(definition.rawScript)
        LoadedScript(key = key, definition = definition, context = ctx)
      }.getOrElse { error ->
        Timber.tag("LxHost").w(error, "脚本加载失败: ${definition.name}")
        null
      }
    }
    scriptsReady = true
  }

  private fun registerNativeBridge(ctx: QuickJSContext, key: String) {
    ctx.globalObject.setProperty("__lx_native_call__") { args ->
      val requestKey = args.getOrNull(0) as? String
      val action = args.getOrNull(1) as? String
      val payload = args.getOrNull(2) as? String
      if (action != null) {
        scope.launch { dispatchNativeCall(key, requestKey, action, payload) }
      }
      null
    }
    ctx.globalObject.setProperty("__lx_native_call__set_timeout") { args ->
      val id = (args.getOrNull(0) as? Number)?.toInt() ?: 0
      val ms = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
      handler.postDelayed({ callJsFunctionJson(key, "__set_timeout__", id.toString()) }, ms)
      null
    }
    ctx.globalObject.setProperty("__lx_native_call__utils_str2b64") { args ->
      Base64.encodeToString((args.getOrNull(0) as? String).orEmpty().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
    ctx.globalObject.setProperty("__lx_native_call__utils_b642buf") { args ->
      val bytes = Base64.decode((args.getOrNull(0) as? String).orEmpty(), Base64.NO_WRAP)
      bytes.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toString() }
    }
    ctx.globalObject.setProperty("__lx_native_call__utils_str2md5") { args ->
      bridge.md5((args.getOrNull(0) as? String).orEmpty())
    }
    ctx.globalObject.setProperty("__lx_native_call__utils_aes_encrypt") { args ->
      val data = Base64.decode((args.getOrNull(0) as? String).orEmpty(), Base64.NO_WRAP)
      val keyBytes = Base64.decode((args.getOrNull(1) as? String).orEmpty(), Base64.NO_WRAP)
      val ivRaw = (args.getOrNull(2) as? String).orEmpty()
      val iv = if (ivRaw.isBlank()) null else Base64.decode(ivRaw, Base64.NO_WRAP)
      val mode = (args.getOrNull(3) as? String).orEmpty()
      Base64.encodeToString(bridge.aesEncrypt(data, keyBytes, iv, mode), Base64.NO_WRAP)
    }
    ctx.globalObject.setProperty("__lx_native_call__utils_rsa_encrypt") { args ->
      val data = Base64.decode((args.getOrNull(0) as? String).orEmpty(), Base64.NO_WRAP)
      val key = (args.getOrNull(1) as? String).orEmpty()
      val padding = (args.getOrNull(2) as? String).orEmpty()
      Base64.encodeToString(bridge.rsaEncrypt(data, key, padding), Base64.NO_WRAP)
    }
  }

  private suspend fun dispatchNativeCall(key: String, requestKey: String?, action: String, payload: String?) {
    val json = if (payload.isNullOrBlank()) JSONObject() else runCatching { JSONObject(payload) }.getOrElse { JSONObject() }
    when (action) {
      "log" -> bridge.log(json.optString("level", "info"), json.optString("message"))

      "http" -> {
        val response = runCatching { performHttp(json) }
        deliverToJs(key, requestKey, response.getOrNull(), response.exceptionOrNull()?.message)
      }

      "zlib" -> {
        val kind = json.optString("kind")
        val data = Base64.decode(json.optString("data"), Base64.NO_WRAP)
        val result = runCatching { bridge.zlib(kind, data) }
        deliverToJs(
          key,
          requestKey,
          result.getOrNull()?.let { JSONObject().put("data", Base64.encodeToString(it, Base64.NO_WRAP)) },
          result.exceptionOrNull()?.message,
        )
      }

      "crypto" -> {
        val result = runCatching {
          when (json.optString("kind")) {
            "md5" -> JSONObject().put("value", bridge.md5(json.optString("text")))
            "aes" -> JSONObject().put(
              "value",
              Base64.encodeToString(
                bridge.aesEncrypt(
                  Base64.decode(json.optString("data"), Base64.NO_WRAP),
                  Base64.decode(json.optString("key"), Base64.NO_WRAP),
                  json.optString("iv").takeIf { it.isNotBlank() }?.let { Base64.decode(it, Base64.NO_WRAP) },
                  json.optString("mode"),
                ),
                Base64.NO_WRAP,
              ),
            )
            "rsa" -> JSONObject().put(
              "value",
              Base64.encodeToString(
                bridge.rsaEncrypt(
                  Base64.decode(json.optString("data"), Base64.NO_WRAP),
                  json.optString("key"),
                  json.optString("padding"),
                ),
                Base64.NO_WRAP,
              ),
            )
            else -> throw IllegalArgumentException("不支持的加密操作")
          }
        }
        deliverToJs(key, requestKey, result.getOrNull(), result.exceptionOrNull()?.message)
      }

      "searchResult", "detailResult", "urlResult", "lyricResult", "picResult" -> {
        val id = json.optString("requestId")
        val error = json.optString("error").takeIf { it.isNotBlank() }
        val data = json.optString("data").takeIf { it.isNotBlank() }
        resolveBundleRequest(id, if (error != null) null else data, error)
      }

      // 自定义源脚本侧：脚本向应用发起请求
      "init" -> {
        val info = json.optJSONObject("info")
        val sources = info?.optJSONObject("sources")
        val loaded = loadedScripts.firstOrNull { it.key == key }
        if (loaded != null && sources != null) {
          sources.keys().forEach { source ->
            val entry = sources.optJSONObject(source)
            if (entry != null && entry.optString("type") == "music") loaded.sources.add(source)
          }
        }
      }

      "showUpdateAlert" -> {
        updateAlert = json.optString("log").takeIf { it.isNotBlank() }
      }

      "cancelRequest" -> {
        scriptRequests.remove(json.optString("requestKey"))
      }

      // 脚本的 lx.request：替它发 HTTP，再把结果送回脚本
      "request" -> {
        val requestKey = json.optString("requestKey")
        val url = json.optString("url")
        val options = json.optJSONObject("options") ?: JSONObject()
        val result = runCatching { performHttp(JSONObject().put("url", url).put("options", options)) }
        val responseJson = if (result.isSuccess) {
          JSONObject().put("requestKey", requestKey).put("error", JSONObject.NULL).put("response", result.getOrThrow())
        } else {
          JSONObject().put("requestKey", requestKey).put("error", result.exceptionOrNull()?.message ?: "请求失败")
        }
        callJsFunctionJson(key, "response", responseJson.toString())
      }

      // 脚本对请求的应答
      "response" -> {
        val requestKey = json.optString("requestKey")
        val sink = scriptRequests.remove(requestKey) ?: return
        val status = json.optBoolean("status", false)
        val result = json.optJSONObject("result")
        if (!status) {
          sink.onDone(null)
        } else {
          val data = result?.optJSONObject("data")
          sink.onDone(data?.optString("url") ?: data?.toString())
        }
      }
    }
  }

  private fun performHttp(json: JSONObject): JSONObject {
    val url = json.optString("url")
    val options = json.optJSONObject("options") ?: json
    val method = options.optString("method", "get").ifBlank { "get" }
    val headersJson = options.optJSONObject("headers")
    val headers = buildMap {
      headersJson?.keys()?.forEach { name -> put(name, headersJson.optString(name)) }
    }
    val timeout = options.optLong("timeout", 15_000L)
    val body = options.optString("body").takeIf { it.isNotBlank() && it != "null" }
    val result = bridge.http(method, url, headers, body, timeoutMs = timeout)
    val headerObject = JSONObject().apply { result.headers.forEach { (name, value) -> put(name, value) } }
    return JSONObject()
      .put("statusCode", result.statusCode)
      .put("statusMessage", result.statusMessage)
      .put("headers", headerObject)
      .put("body", result.body)
  }

  private fun deliverToJs(key: String, requestId: String?, payload: JSONObject?, error: String?) {
    if (requestId == null) return
    val envelope = JSONObject()
    if (error != null) envelope.put("error", error) else envelope.put("data", payload)
    handler.post {
      runCatching {
        bundleContext?.evaluate("__lx_native_result__(${quote(requestId)}, ${quote(envelope.toString())})")
      }
    }
  }

  private suspend fun requestBundle(timeoutMs: Long, invoke: (QuickJSContext, String) -> Unit): String? =
    suspendCancellableCoroutine { continuation ->
      val requestId = "req-" + counter.incrementAndGet()
      val timeout = handler.postDelayed(
        {
          resolveBundleRequest(requestId, null, "音源响应超时")
        },
        timeoutMs,
      )
      bundleRequests[requestId] = ResultSink { data ->
        handler.removeCallbacksAndMessages(timeout)
        if (continuation.isActive) continuation.resume(data)
      }
      handler.post {
        val ctx = bundleContext
        if (ctx == null) {
          resolveBundleRequest(requestId, null, "音源引擎未就绪")
          return@post
        }
        runCatching { invoke(ctx, requestId) }.onFailure { error ->
          resolveBundleRequest(requestId, null, error.message)
        }
      }
    }

  private fun resolveBundleRequest(requestId: String, data: String?, error: String?) {
    val sink = bundleRequests.remove(requestId) ?: return
    sink.onDone(if (error != null) null else data)
  }

  private suspend fun callScript(platform: String, action: String, payloadJson: String, timeoutMs: Long): String? =
    suspendCancellableCoroutine { continuation ->
      val target = loadedScripts.firstOrNull { it.sources.contains(platform) } ?: loadedScripts.firstOrNull()
      if (target == null) {
        if (continuation.isActive) continuation.resume(null)
        return@suspendCancellableCoroutine
      }
      val requestKey = "script-" + counter.incrementAndGet()
      val timeout = handler.postDelayed({ scriptRequests.remove(requestKey)?.onDone(null) }, timeoutMs)
      scriptRequests[requestKey] = ResultSink { data ->
        handler.removeCallbacksAndMessages(timeout)
        if (continuation.isActive) continuation.resume(data)
      }
      val body = JSONObject()
        .put("requestKey", requestKey)
        .put(
          "data",
          JSONObject()
            .put("source", platform)
            .put("action", action)
            .put(
              "info",
              JSONObject().apply {
                put("type", JSONObject(payloadJson).optString("quality"))
                put("musicInfo", JSONObject(payloadJson).optJSONObject("songInfo"))
              },
            ),
        )
        .toString()
      handler.post { callJsFunctionJson(target.key, "request", body) }
    }

  /** 调用脚本侧的 __lx_native__(key, action, payload)，payload 必须是 JSON 字符串。 */
  private fun callJsFunctionJson(key: String, action: String, payloadJson: String) {
    handler.post {
      val ctx = if (key == bundleKey) bundleContext else loadedScripts.firstOrNull { it.key == key }?.context
      if (ctx == null) return@post
      runCatching {
        ctx.evaluate("__lx_native__(${quote(key)}, ${quote(action)}, ${quote(payloadJson)})")
      }
    }
  }

  private suspend fun <T> withJs(block: (QuickJSContext) -> T): T? {
    ensureStarted()
    val ctx = bundleContext ?: return null
    return runCatching { block(ctx) }.getOrNull()
  }

  private fun readAsset(path: String): String? =
    runCatching { appContext.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

  private fun quote(value: String): String = JSONObject.quote(value)
}