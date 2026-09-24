package me.spica27.spicamusic.online.settings

import android.content.Context
import me.spica27.spicamusic.online.OnlinePlatforms
import me.spica27.spicamusic.online.OnlineQuality
import java.io.File

/** 文件名档位，取值与落雪一致。 */
enum class FileNameStyle(val key: String, val label: String) {
  TITLE_ARTIST("title-artist", "歌名 - 歌手"),
  ARTIST_TITLE("artist-title", "歌手 - 歌名"),
  TITLE_ONLY("title", "歌名"),
  ;

  companion object {
    val DEFAULT = TITLE_ARTIST

    fun fromKey(key: String?): FileNameStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
  }
}

/**
 * 在线音源与下载相关的设置。全部落在偏好设置里，默认值按既定决策：
 * 默认 320k、并发 3、文件名“歌名 - 歌手”、九个内置音源全部启用。
 */
class OnlineSettings(private val context: Context) {

  private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun quality(): OnlineQuality = OnlineQuality.fromKey(prefs.getString(KEY_QUALITY, null))

  fun setQuality(quality: OnlineQuality) {
    prefs.edit().putString(KEY_QUALITY, quality.key).apply()
  }

  fun concurrency(): Int = prefs.getInt(KEY_CONCURRENCY, DEFAULT_CONCURRENCY).coerceIn(1, 8)

  fun setConcurrency(value: Int) {
    prefs.edit().putInt(KEY_CONCURRENCY, value.coerceIn(1, 8)).apply()
  }

  fun retryCount(): Int = prefs.getInt(KEY_RETRY, DEFAULT_RETRY).coerceIn(0, 5)

  fun setRetryCount(value: Int) {
    prefs.edit().putInt(KEY_RETRY, value.coerceIn(0, 5)).apply()
  }

  fun fileNameStyle(): FileNameStyle = FileNameStyle.fromKey(prefs.getString(KEY_NAME_STYLE, null))

  fun setFileNameStyle(style: FileNameStyle) {
    prefs.edit().putString(KEY_NAME_STYLE, style.key).apply()
  }

  fun wifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)

  fun setWifiOnly(value: Boolean) {
    prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
  }

  /** 下载目录与白名单目录是两个独立设置，这里只负责下载目录。 */
  fun downloadDirectory(): File {
    val configured = prefs.getString(KEY_DOWNLOAD_DIR, null)
    val dir = when {
      configured.isNullOrBlank() -> File(context.getExternalFilesDir(null), "online-downloads")
      else -> File(configured)
    }
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun setDownloadDirectory(path: String) {
    prefs.edit().putString(KEY_DOWNLOAD_DIR, path).apply()
  }

  fun enabledPlatforms(): List<String> {
    val stored = prefs.getStringSet(KEY_ENABLED_PLATFORMS, null) ?: return OnlinePlatforms.all
    return OnlinePlatforms.all.filter { stored.contains(it) }
  }

  fun setPlatformEnabled(platform: String, enabled: Boolean) {
    val current = enabledPlatforms().toMutableSet()
    if (enabled) current.add(platform) else current.remove(platform)
    prefs.edit().putStringSet(KEY_ENABLED_PLATFORMS, current).apply()
  }

  /** 搜索与下载都可用“网易/QQ 用哪套实现”，这里记录实现切换。 */
  fun implementationFor(platform: String): String =
    prefs.getString(KEY_IMPL_PREFIX + platform, null) ?: DEFAULT_IMPLEMENTATION

  fun setImplementationFor(platform: String, implementation: String) {
    prefs.edit().putString(KEY_IMPL_PREFIX + platform, implementation).apply()
  }

  companion object {
    const val DEFAULT_CONCURRENCY = 3
    const val DEFAULT_RETRY = 2
    const val DEFAULT_IMPLEMENTATION = me.spica27.spicamusic.online.OnlineImplementations.LX

    private const val PREFS = "online_settings"
    private const val KEY_QUALITY = "quality"
    private const val KEY_CONCURRENCY = "concurrency"
    private const val KEY_RETRY = "retry"
    private const val KEY_NAME_STYLE = "name_style"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_DOWNLOAD_DIR = "download_dir"
    private const val KEY_ENABLED_PLATFORMS = "enabled_platforms"
    private const val KEY_IMPL_PREFIX = "impl_"
  }
}