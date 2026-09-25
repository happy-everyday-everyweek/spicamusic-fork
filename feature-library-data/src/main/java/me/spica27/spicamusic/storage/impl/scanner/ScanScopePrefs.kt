package me.spica27.spicamusic.storage.impl.scanner

import android.content.Context

/**
 * 扫描白名单设置的只读视图。
 *
 * 写入方在设置页，读取方在扫描服务，两边共用同一份 SharedPreferences；
 * 这里刻意不依赖 domain 模块，判定逻辑与 `ScanScope` 保持一致：
 * 路径统一成以 / 结尾的绝对路径后做前缀比较，白名单为空则不收录任何文件。
 */
class ScanScopePrefs(private val context: Context) {

  private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun isWhitelistActive(): Boolean = prefs.getString(KEY_MODE, null) == MODE_ONLY_SELECTED

  fun whitelistPaths(): List<String> = prefs.getStringSet(KEY_PATHS, emptySet()).orEmpty().sorted()

  /** 某条路径是否应当被收录。非白名单模式恒为 true。 */
  fun allows(path: String?): Boolean {
    if (!isWhitelistActive()) return true
    val value = path?.trim().orEmpty()
    if (value.isEmpty()) return false
    val normalized = normalize(value)
    return whitelistPaths().any { normalized.startsWith(normalize(it)) }
  }

  private fun normalize(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "/"
    val absolute = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return if (absolute.endsWith("/")) absolute else "$absolute/"
  }

  private companion object {
    const val PREFS = "scan_scope"
    const val KEY_MODE = "mode"
    const val KEY_PATHS = "whitelist_paths"
    const val MODE_ONLY_SELECTED = "OnlySelectedDirectories"
  }
}
