package me.spica27.spicamusic.storage.impl.scanner

import android.content.Context
import me.spica27.spicamusic.feature.library.domain.scope.ScanMode
import me.spica27.spicamusic.feature.library.domain.scope.ScanScope

/**
 * 扫描白名单设置的只读视图。
 *
 * 写入方在设置页（`:feature-online-sources` 的 `ScanScopeStore`），读取方在扫描服务，
 * 两边共用同一份 SharedPreferences，避免扫描层反向依赖设置模块。
 */
class ScanScopePrefs(private val context: Context) {

  private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun mode(): ScanMode =
    when (prefs.getString(KEY_MODE, null)) {
      ScanMode.OnlySelectedDirectories.name -> ScanMode.OnlySelectedDirectories
      else -> ScanMode.AllDirectories
    }

  fun whitelistPaths(): List<String> = prefs.getStringSet(KEY_PATHS, emptySet()).orEmpty().sorted()

  /** 当前是否处于白名单模式。 */
  fun isWhitelistActive(): Boolean = mode() == ScanMode.OnlySelectedDirectories

  /**
   * 某条路径是否应当被收录。全部目录模式下恒为 true；
   * 白名单模式下要求落在某个白名单目录之下，白名单为空时不收录任何文件。
   */
  fun allows(path: String?): Boolean {
    if (!isWhitelistActive()) return true
    val value = path?.trim().orEmpty()
    if (value.isEmpty()) return false
    return ScanScope(
      mode = ScanMode.OnlySelectedDirectories,
      whitelistPrefixes = whitelistPaths(),
    ).includes(value)
  }

  private companion object {
    const val PREFS = "scan_scope"
    const val KEY_MODE = "mode"
    const val KEY_PATHS = "whitelist_paths"
  }
}