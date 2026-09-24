package me.spica27.spicamusic.feature.library.domain.scope

/** 扫描模式：全部目录，或只认白名单目录。 */
enum class ScanMode {
  AllDirectories,
  OnlySelectedDirectories,
}

/**
 * 扫描范围策略。纯逻辑，不依赖 Android，便于脱离设备测试。
 *
 * 白名单生效时，它是最终过滤条件：全量扫描、额外目录扫描与媒体库增量同步都走这里，
 * 只有路径落在白名单目录之下的音频才会被收录。白名单为空表示不收录任何文件，
 * 避免“开了白名单却等于没开”的误判。
 */
data class ScanScope(
  val mode: ScanMode = ScanMode.AllDirectories,
  val whitelistPrefixes: List<String> = emptyList(),
) {
  val isWhitelistOnly: Boolean get() = mode == ScanMode.OnlySelectedDirectories

  fun includes(path: String?): Boolean {
    if (mode == ScanMode.AllDirectories) return true
    if (path.isNullOrBlank()) return false
    val normalized = normalize(path)
    return normalizedPrefixes().any { normalized.startsWith(it) }
  }

  private fun normalizedPrefixes(): List<String> =
    whitelistPrefixes
      .map { normalize(it) }
      .distinct()

  companion object {
    /** 统一为以 / 结尾的绝对路径，便于做前缀比较。 */
    fun normalize(raw: String): String {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) return "/"
      val absolute = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
      return if (absolute.endsWith("/")) absolute else "$absolute/"
    }
  }
}