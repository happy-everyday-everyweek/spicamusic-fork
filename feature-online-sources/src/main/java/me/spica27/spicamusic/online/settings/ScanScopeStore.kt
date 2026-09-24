package me.spica27.spicamusic.online.settings

import android.content.Context
import me.spica27.spicamusic.feature.library.domain.scope.ScanMode

/**
 * 扫描范围设置的持久化：模式 + 白名单目录。
 * 目录以 SAF 树 URI 的原字符串保存，同时保存可解析出的绝对路径用于媒体库前缀过滤。
 */
class ScanScopeStore(private val context: Context) {

  private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun mode(): ScanMode = when (prefs.getString(KEY_MODE, null)) {
    ScanMode.OnlySelectedDirectories.name -> ScanMode.OnlySelectedDirectories
    else -> ScanMode.AllDirectories
  }

  fun setMode(mode: ScanMode) {
    prefs.edit().putString(KEY_MODE, mode.name).apply()
  }

  /** 白名单目录的绝对路径列表（用于扫描过滤）。 */
  fun whitelistPaths(): List<String> = prefs.getStringSet(KEY_PATHS, emptySet()).orEmpty().sorted()

  fun addWhitelistPath(path: String) {
    val normalized = path.trim().trimEnd('/').ifBlank { return }
    val current = whitelistPaths().toMutableSet()
    current.add(normalized)
    prefs.edit().putStringSet(KEY_PATHS, current).putString(KEY_MODE, ScanMode.OnlySelectedDirectories.name).apply()
  }

  fun removeWhitelistPath(path: String) {
    val current = whitelistPaths().toMutableSet()
    current.remove(path)
    prefs.edit().putStringSet(KEY_PATHS, current).apply()
  }

  /** 迁移完成后的状态文案，供设置页展示。 */
  fun migrationStatus(): String? = prefs.getString(KEY_MIGRATION_STATUS, null)

  fun setMigrationStatus(status: String?) {
    prefs.edit().putString(KEY_MIGRATION_STATUS, status).apply()
  }

  private companion object {
    const val PREFS = "scan_scope"
    const val KEY_MODE = "mode"
    const val KEY_PATHS = "whitelist_paths"
    const val KEY_MIGRATION_STATUS = "migration_status"
  }
}