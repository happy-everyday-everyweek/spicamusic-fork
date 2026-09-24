package me.spica27.spicamusic.feature.library.domain.migration

/** 迁移候选：本地曲库里未被忽略、且已经扫描到的歌曲。 */
data class MigratableSong(
  val id: Long,
  val path: String,
  val title: String,
  val artist: String,
  val durationMs: Long,
  val sizeBytes: Long,
)

/** 迁移目标：落地文件名，以及目标目录里是否已经存在同内容文件。 */
data class MigrationTarget(
  val song: MigratableSong,
  val fileName: String,
  val alreadyPresent: Boolean,
)

/** 迁移计划：要复制的清单、跳过的清单，以及被判定为重复的歌曲指向的原始歌曲 id。 */
data class MigrationPlan(
  val toCopy: List<MigrationTarget>,
  val skipped: List<MigrationTarget>,
  val duplicateOf: Map<Long, Long>,
) {
  val copyCount: Int get() = toCopy.size
  val skippedCount: Int get() = skipped.size
  val totalBytes: Long get() = toCopy.sumOf { it.song.sizeBytes }
}

/**
 * 迁移规划器。纯逻辑，负责去重与命名，不触碰文件系统。
 *
 * 去重键采用“标题 + 艺术家 + 时长 + 大小”的指纹：同一首歌在曲库里出现多条记录、
 * 或目标目录里已经有同内容文件时，都会被识别为重复并跳过，重复执行不会产生副本。
 */
object MigrationPlanner {

  private val illegalChars = Regex("""[\\/:*?"<>|\n\r\t]""")

  fun fingerprint(song: MigratableSong): String {
    val title = song.title.trim().lowercase()
    val artist = song.artist.trim().lowercase()
    val seconds = song.durationMs / 1000
    return "$title|$artist|$seconds|${song.sizeBytes}"
  }

  fun sanitizeFileName(name: String): String {
    val cleaned = illegalChars.replace(name, "_").trim().trimEnd('.')
    return cleaned.ifBlank { "unknown" }
  }

  /**
   * @param songs 本地曲库里未忽略、已扫描到的歌曲
   * @param existingFileNames 目标目录里已存在的文件名集合
   * @param existingFingerprints 目标目录里已存在文件的指纹集合
   * @param nameOf 由调用方决定落地文件名（例如“歌名 - 歌手”）
   */
  fun plan(
    songs: List<MigratableSong>,
    existingFileNames: Set<String> = emptySet(),
    existingFingerprints: Set<String> = emptySet(),
    nameOf: (MigratableSong) -> String,
  ): MigrationPlan {
    val toCopy = mutableListOf<MigrationTarget>()
    val skipped = mutableListOf<MigrationTarget>()
    val duplicateOf = mutableMapOf<Long, Long>()
    val seen = mutableMapOf<String, Long>()
    val usedNames = mutableSetOf<String>()

    for (song in songs) {
      val fingerprint = fingerprint(song)
      val fileName = uniqueName(sanitizeFileName(nameOf(song)), usedNames)

      val previous = seen[fingerprint]
      val alreadyPresent = existingFingerprints.contains(fingerprint) || existingFileNames.contains(fileName)
      val target = MigrationTarget(song = song, fileName = fileName, alreadyPresent = alreadyPresent)

      when {
        previous != null -> {
          duplicateOf[song.id] = previous
          skipped += target
        }
        alreadyPresent -> skipped += target
        else -> {
          seen[fingerprint] = song.id
          usedNames += fileName
          toCopy += target
        }
      }
    }

    return MigrationPlan(toCopy = toCopy, skipped = skipped, duplicateOf = duplicateOf)
  }

  private fun uniqueName(baseName: String, used: Set<String>): String {
    if (!used.contains(baseName)) return baseName
    val dot = baseName.lastIndexOf('.')
    val stem = if (dot > 0) baseName.substring(0, dot) else baseName
    val extension = if (dot > 0) baseName.substring(dot) else ""
    var index = 2
    while (used.contains("$stem ($index)$extension")) index++
    return "$stem ($index)$extension"
  }
}