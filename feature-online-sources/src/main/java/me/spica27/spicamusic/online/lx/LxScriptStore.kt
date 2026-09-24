package me.spica27.spicamusic.online.lx

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一个已导入的自定义源脚本。 */
data class LxScriptDefinition(
  val id: String,
  val name: String,
  val description: String = "",
  val version: String = "",
  val author: String = "",
  val homepage: String = "",
  val rawScript: String,
  val enabled: Boolean = true,
  val order: Int = 0,
) {
  /** 脚本头部注释里的元信息，导入时解析。 */
  companion object {
    private val nameRegex = Regex("""@name\s+(.+)""")
    private val descRegex = Regex("""@description\s+(.+)""")
    private val versionRegex = Regex("""@version\s+(.+)""")
    private val authorRegex = Regex("""@author\s+(.+)""")
    private val homepageRegex = Regex("""@homepage\s+(.+)""")

    fun parse(rawScript: String, fallbackId: String): LxScriptDefinition {
      val header = rawScript.take(2_000)
      fun pick(regex: Regex, fallback: String): String =
        regex.find(header)?.groupValues?.getOrNull(1)?.trim()?.removeSuffix("*/")?.trim() ?: fallback

      val name = pick(nameRegex, "未命名音源")
      return LxScriptDefinition(
        id = fallbackId,
        name = name,
        description = pick(descRegex, ""),
        version = pick(versionRegex, ""),
        author = pick(authorRegex, ""),
        homepage = pick(homepageRegex, ""),
        rawScript = rawScript,
      )
    }
  }
}

/** 自定义源脚本的持久化：元信息存偏好设置，脚本原文按 id 单独存文件。 */
class LxScriptStore(private val context: Context) {

  private val preferences get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val scriptDir get() = context.filesDir.resolve("lx-scripts").apply { mkdirs() }

  fun list(): List<LxScriptDefinition> {
    val raw = preferences.getString(KEY_LIST, null) ?: return emptyList()
    return runCatching {
      val array = JSONArray(raw)
      buildList {
        for (index in 0 until array.length()) {
          val item = array.optJSONObject(index) ?: continue
          val id = item.optString("id")
          val script = readScriptBody(id) ?: continue
          add(
            LxScriptDefinition(
              id = id,
              name = item.optString("name"),
              description = item.optString("description"),
              version = item.optString("version"),
              author = item.optString("author"),
              homepage = item.optString("homepage"),
              rawScript = script,
              enabled = item.optBoolean("enabled", true),
              order = item.optInt("order", index),
            ),
          )
        }
      }
    }.getOrElse {
      emptyList()
    }
  }

  fun enabled(): List<LxScriptDefinition> = list().filter { it.enabled }.sortedBy { it.order }

  fun add(rawScript: String): LxScriptDefinition {
    val id = "script-" + System.currentTimeMillis()
    val parsed = LxScriptDefinition.parse(rawScript, id)
    scriptDir.resolve(id).writeText(rawScript)
    val current = list().map { it.copy() }
    save(current + parsed.copy(order = current.size))
    return parsed
  }

  fun remove(id: String) {
    scriptDir.resolve(id).delete()
    save(list().filterNot { it.id == id }.mapIndexed { index, item -> item.copy(order = index) })
  }

  fun setEnabled(id: String, enabled: Boolean) {
    save(list().map { if (it.id == id) it.copy(enabled = enabled) else it })
  }

  fun move(id: String, delta: Int) {
    val sorted = list().sortedBy { it.order }.toMutableList()
    val index = sorted.indexOfFirst { it.id == id }
    if (index < 0) return
    val target = (index + delta).coerceIn(0, sorted.size - 1)
    if (target == index) return
    val item = sorted.removeAt(index)
    sorted.add(target, item)
    save(sorted.mapIndexed { order, definition -> definition.copy(order = order) })
  }

  private fun save(list: List<LxScriptDefinition>) {
    val array = JSONArray()
    list.sortedBy { it.order }.forEachIndexed { index, item ->
      array.put(
        JSONObject().apply {
          put("id", item.id)
          put("name", item.name)
          put("description", item.description)
          put("version", item.version)
          put("author", item.author)
          put("homepage", item.homepage)
          put("enabled", item.enabled)
          put("order", index)
        },
      )
    }
    preferences.edit().putString(KEY_LIST, array.toString()).apply()
  }

  private fun readScriptBody(id: String): String? =
    runCatching { scriptDir.resolve(id).takeIf { it.exists() }?.readText() }.getOrNull()

  private companion object {
    const val PREFS = "lx_scripts"
    const val KEY_LIST = "list"
  }
}