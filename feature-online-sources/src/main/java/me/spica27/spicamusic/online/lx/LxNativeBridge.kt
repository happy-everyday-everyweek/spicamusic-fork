package me.spica27.spicamusic.online.lx

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class LxHttpResult(
  val statusCode: Int,
  val statusMessage: String,
  val headers: Map<String, String>,
  val body: String,
)

/**
 * 原生能力桥：给 QuickJS 里的音源实现与自定义源脚本提供 HTTP、zlib、AES/RSA/MD5。
 * 这些能力对应洛雪移动版预置脚本里 __lx_native_call__utils_* 与 lx.request 的语义。
 */
class LxNativeBridge(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .build(),
) {

  fun http(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String?,
    bodyBase64: Boolean = false,
    timeoutMs: Long = 15_000L,
  ): LxHttpResult {
    val requestBody = when {
      body == null -> null
      bodyBase64 -> Base64.decode(body, Base64.NO_WRAP).toRequestBody("application/octet-stream".toMediaType())
      else -> body.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType())
    }
    val builder = Request.Builder().url(url)
    headers.forEach { (name, value) -> runCatching { builder.header(name, value) } }
    val methodName = method.uppercase()
    builder.method(methodName, requestBody)
    val call = client.newBuilder()
      .readTimeout(timeoutMs.coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)
      .build()
      .newCall(builder.build())
    call.execute().use { response ->
      val bodyString = response.body?.string().orEmpty()
      val headerMap = response.headers.names().associateWith { name -> response.headers.values(name).joinToString(",") }
      return LxHttpResult(
        statusCode = response.code,
        statusMessage = response.message,
        headers = headerMap,
        body = bodyString,
      )
    }
  }

  fun zlib(kind: String, data: ByteArray): ByteArray = when (kind) {
    "inflate" -> inflate(data, raw = false)
    "inflateRaw" -> inflate(data, raw = true)
    "gunzip" -> GZIPInputStream(data.inputStream()).use { it.readBytes() }
    "deflateRaw" -> ByteArrayOutputStream().also { output ->
      object : DeflaterOutputStream(output, java.util.zip.Deflater(true)) {
        init {
          use { it.write(data) }
        }
      }
    }.toByteArray()
    else -> throw IllegalArgumentException("不支持的 zlib 操作: $kind")
  }

  private fun inflate(data: ByteArray, raw: Boolean): ByteArray {
    val inflater = Inflater(raw)
    inflater.setInput(data)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    try {
      while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        if (count == 0 && inflater.needsInput()) break
        output.write(buffer, 0, count)
      }
    } finally {
      inflater.end()
    }
    return output.toByteArray()
  }

  fun aesEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray?, mode: String): ByteArray {
    val transformation = when {
      mode.contains("NoPadding", ignoreCase = true) -> "AES/ECB/NoPadding"
      iv == null || iv.isEmpty() -> "AES/ECB/PKCS5Padding"
      else -> "AES/CBC/PKCS5Padding"
    }
    val cipher = Cipher.getInstance(transformation)
    val keySpec = SecretKeySpec(key, "AES")
    if (transformation.contains("CBC")) {
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv!!))
    } else {
      cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    }
    return cipher.doFinal(data)
  }

  fun rsaEncrypt(data: ByteArray, publicKeyBase64: String, padding: String): ByteArray {
    val keyBytes = Base64.decode(publicKeyBase64.replace("-----", "").replace("\n", "").replace("\r", ""), Base64.DEFAULT)
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
    val transformation = if (padding.contains("OAEP", ignoreCase = true)) {
      "RSA/ECB/OAEPWithSHA1AndMGF1Padding"
    } else {
      "RSA/ECB/NoPadding"
    }
    val cipher = Cipher.getInstance(transformation)
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    return cipher.doFinal(data)
  }

  fun md5(text: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun log(level: String, message: String) {
    when (level) {
      "warn" -> Timber.tag("LxHost").w(message)
      "error" -> Timber.tag("LxHost").e(message)
      else -> Timber.tag("LxHost").d(message)
    }
  }
}