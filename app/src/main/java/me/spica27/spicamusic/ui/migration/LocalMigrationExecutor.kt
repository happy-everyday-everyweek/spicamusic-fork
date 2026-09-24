package me.spica27.spicamusic.ui.migration

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.feature.library.domain.migration.MigratableSong
import me.spica27.spicamusic.feature.library.domain.migration.MigrationPlanner
import timber.log.Timber
import java.io.File

data class MigrationOutcome(
  val copied: Int,
  val skipped: Int,
  val failed: Int,
  val targetDirectory: String,
)

/**
 * 迁移执行器：把系统媒体库里已扫描到、且不在目标目录下的音频复制（或移动）到目标目录。
 *
 * 去重交给 MigrationPlanner（标题 + 艺术家 + 时长 + 大小），因此重复执行不会产生副本；
 * 目标目录已有的同内容文件会被跳过。
 */
class LocalMigrationExecutor(private val context: Context) {

  suspend fun migrate(
    targetDirectory: File,
    moveInsteadOfCopy: Boolean = false,
    onProgress: (copied: Int, total: Int) -> Unit = { _, _ -> },
  ): MigrationOutcome = withContext(Dispatchers.IO) {
    val songs = queryLibrarySongs()
    val targetPrefix = targetDirectory.absolutePath.trimEnd('/')
    val candidates = songs.filterNot { it.path.startsWith("$targetPrefix/") }

    val existingNames = targetDirectory.listFiles()?.map { it.name }?.toSet().orEmpty()
    val plan =
      MigrationPlanner.plan(
        songs = candidates,
        existingFileNames = existingNames,
        nameOf = { song -> fileNameFor(song) },
      )

    var copied = 0
    var failed = 0
    val total = plan.toCopy.size

    plan.toCopy.forEach { target ->
      val source = File(target.song.path)
      val destination = File(targetDirectory, target.fileName)
      val result =
        runCatching {
          if (!source.exists()) error("源文件不存在")
          if (destination.exists()) return@runCatching
          if (moveInsteadOfCopy) {
            if (!source.renameTo(destination)) source.copyTo(destination, overwrite = true).also { source.delete() }
          } else {
            source.copyTo(destination, overwrite = false)
          }
        }
      if (result.isSuccess) {
        copied++
        onProgress(copied, total)
      } else {
        failed++
        Timber.tag("LocalMigration").w(result.exceptionOrNull(), "迁移失败: ${target.song.path}")
      }
    }

    MigrationOutcome(
      copied = copied,
      skipped = plan.skippedCount,
      failed = failed,
      targetDirectory = targetDirectory.absolutePath,
    )
  }

  private fun fileNameFor(song: MigratableSong): String {
    val base = "${song.title} - ${song.artist}".trim().ifBlank { song.title }
    val extension = song.path.substringAfterLast('.', "mp3").take(5)
    return "${MigrationPlanner.sanitizeFileName(base)}.$extension"
  }

  private fun queryLibrarySongs(): List<MigratableSong> {
    val projection =
      arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.SIZE,
      )
    val result = mutableListOf<MigratableSong>()
    runCatching {
      context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        null,
      )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        while (cursor.moveToNext()) {
          val path = cursor.getString(dataIndex) ?: continue
          result +=
            MigratableSong(
              id = cursor.getLong(idIndex),
              path = path,
              title = cursor.getString(titleIndex).orEmpty(),
              artist = cursor.getString(artistIndex).orEmpty(),
              durationMs = cursor.getLong(durationIndex),
              sizeBytes = cursor.getLong(sizeIndex),
            )
        }
      }
    }.onFailure { Timber.tag("LocalMigration").w(it, "读取媒体库失败") }
    return result
  }
}