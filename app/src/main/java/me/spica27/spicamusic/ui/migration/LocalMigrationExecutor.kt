package me.spica27.spicamusic.ui.migration

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.feature.library.domain.SongUseCases
import me.spica27.spicamusic.feature.library.domain.migration.MigratableSong
import me.spica27.spicamusic.feature.library.domain.migration.MigrationPlanner
import timber.log.Timber
import java.io.File

data class MigrationOutcome(
  val copied: Int,
  val skipped: Int,
  val failed: Int,
  val targetDirectory: String,
  val firstError: String? = null,
)

/**
 * 迁移执行器：把曲库里已扫描到的歌曲复制（或移动）到指定目录。
 *
 * 数据源不再直接扫系统媒体库，而是走应用自己的曲库——这样被忽略的歌曲天然不会进迁移，
 * 拿到的也是应用记录的真实路径与时长。复制时优先用文件路径，路径不可读（content:// 等）
 * 时回退到 ContentResolver 流式拷贝，单个文件失败不会中断整批，并把首个失败原因回传给界面。
 */
class LocalMigrationExecutor(
  private val context: Context,
  private val songUseCases: SongUseCases,
) {

  suspend fun migrate(
    targetDirectory: File,
    moveInsteadOfCopy: Boolean = false,
    onProgress: (copied: Int, total: Int) -> Unit = { _, _ -> },
  ): MigrationOutcome = withContext(Dispatchers.IO) {
    if (!targetDirectory.exists()) targetDirectory.mkdirs()

    // 曲库里的歌曲（已排除被忽略的歌曲）
    val songs =
      runCatching { songUseCases.getAllSongs() }
        .onFailure { Timber.tag("LocalMigration").w(it, "读取曲库失败") }
        .getOrDefault(emptyList())

    val targetPrefix = targetDirectory.absolutePath.trimEnd('/')
    val candidates =
      songs.map { song ->
        MigratableSong(
          id = song.mediaStoreId,
          path = song.path,
          title = song.displayName,
          artist = song.artist,
          durationMs = song.duration,
          sizeBytes = song.size,
        )
      }.filterNot { it.path.startsWith("$targetPrefix/") }

    val existingNames = targetDirectory.listFiles()?.map { it.name }?.toSet().orEmpty()
    val plan =
      MigrationPlanner.plan(
        songs = candidates,
        existingFileNames = existingNames,
        nameOf = { song -> fileNameFor(song) },
      )

    var copied = 0
    var failed = 0
    var firstError: String? = null
    val total = plan.toCopy.size

    plan.toCopy.forEach { target ->
      val destination = File(targetDirectory, target.fileName)
      val result =
        runCatching {
          if (destination.exists() && destination.length() > 0L) return@runCatching
          copyInto(context, target.song.path, destination)
          if (moveInsteadOfCopy) {
            runCatching { File(target.song.path).delete() }
          }
        }
      if (result.isSuccess) {
        copied++
        onProgress(copied, total)
      } else {
        failed++
        val reason = result.exceptionOrNull()?.message ?: "未知错误"
        if (firstError == null) firstError = reason
        Timber.tag("LocalMigration").w(result.exceptionOrNull(), "迁移失败: ${target.song.path}")
      }
    }

    MigrationOutcome(
      copied = copied,
      skipped = plan.skippedCount,
      failed = failed,
      targetDirectory = targetDirectory.absolutePath,
      firstError = firstError,
    )
  }

  /** 来源可能是普通文件路径，也可能是媒体库的 content:// 路径。 */
  private fun copyInto(context: Context, sourcePath: String, destination: File) {
    val source = File(sourcePath)
    if (source.exists() && source.canRead()) {
      source.copyTo(destination, overwrite = true)
      return
    }
    val input =
      context.contentResolver.openInputStream(sourcePath.toUri())
        ?: error("源文件不可读")
    input.use { stream ->
      destination.outputStream().use { output -> stream.copyTo(output) }
    }
  }

  private fun fileNameFor(song: MigratableSong): String {
    val base = "${song.title} - ${song.artist}".trim().ifBlank { song.title }
    val extension = song.path.substringAfterLast('.', "mp3").take(5)
    return "${MigrationPlanner.sanitizeFileName(base)}.$extension"
  }
}
