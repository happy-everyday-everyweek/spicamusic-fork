package me.spica27.spicamusic.online.download

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.online.OnlineQuality
import me.spica27.spicamusic.online.OnlineSourcePort
import me.spica27.spicamusic.online.OnlineTrack
import me.spica27.spicamusic.online.settings.FileNameStyle
import me.spica27.spicamusic.online.settings.OnlineSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/** 下载状态：用于行内底色进度呈现。 */
sealed interface DownloadState {
  data object Idle : DownloadState
  data class Running(val progress: Float) : DownloadState
  data class Done(val path: String) : DownloadState
  data class Failed(val message: String) : DownloadState
}

/**
 * 在线歌曲下载器：先完整下载到下载目录，再写歌词旁挂文件并把文件注册进系统媒体库，
 * 完成后交给上层触发播放。不做边下边播。
 */
class OnlineDownloader(
  private val context: Context,
  private val source: OnlineSourcePort,
  private val settings: OnlineSettings,
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build(),
) {

  private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
  val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

  fun stateOf(trackKey: String): DownloadState = _states.value[trackKey] ?: DownloadState.Idle

  /** 下载并返回落地文件；失败返回 null。 */
  suspend fun download(track: OnlineTrack, quality: OnlineQuality = settings.quality()): File? {
    val trackKey = "${track.sourceKey}:${track.id}"
    _states.update { it + (trackKey to DownloadState.Running(0f)) }

    val targetDir = settings.downloadDirectory()
    val fileName = buildFileName(track)
    val target = File(targetDir, fileName)

    if (target.exists() && target.length() > 0) {
      _states.update { it + (trackKey to DownloadState.Done(target.absolutePath)) }
      return target
    }

    var attempt = 0
    while (attempt <= settings.retryCount()) {
      val url = source.resolveUrl(track, quality)
      if (url.isNullOrBlank()) {
        attempt++
        continue
      }
      val result = runCatching { fetch(url, target, trackKey) }
      if (result.isSuccess) {
        writeSidecarLyric(track, target)
        registerInMediaStore(target)
        _states.update { it + (trackKey to DownloadState.Done(target.absolutePath)) }
        return target
      }
      Timber.tag("OnlineDownloader").w(result.exceptionOrNull(), "下载失败，准备重试: $fileName")
      attempt++
    }

    val message = "下载失败：${track.title}"
    _states.update { it + (trackKey to DownloadState.Failed(message)) }
    return null
  }

  fun clearState(track: OnlineTrack) {
    val trackKey = "${track.sourceKey}:${track.id}"
    _states.update { it - trackKey }
  }

  private suspend fun fetch(url: String, target: File, trackKey: String) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
      .url(url)
      .header(
        "User-Agent",
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36",
      )
      .build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) error("HTTP ${response.code}")
      val body = response.body ?: error("空响应")
      val total = body.contentLength()
      val temp = File(target.parentFile, "${target.name}.part")
      body.byteStream().use { input ->
        temp.outputStream().use { output ->
          val buffer = ByteArray(64 * 1024)
          var read = input.read(buffer)
          var written = 0L
          while (read >= 0) {
            output.write(buffer, 0, read)
            written += read
            if (total > 0) {
              val progress = (written.toFloat() / total).coerceIn(0f, 1f)
              _states.update { it + (trackKey to DownloadState.Running(progress)) }
            }
            read = input.read(buffer)
          }
        }
      }
      if (target.exists()) target.delete()
      if (!temp.renameTo(target)) error("无法写入目标文件")
    }
  }

  /** 歌词先以同名 .lrc 旁挂，保证离线可用。 */
  private suspend fun writeSidecarLyric(track: OnlineTrack, audio: File) {
    val lyric = source.lyric(track) ?: return
    runCatching {
      val stem = audio.name.substringBeforeLast('.')
      File(audio.parentFile, "$stem.lrc").writeText(lyric)
    }.onFailure { Timber.tag("OnlineDownloader").w(it, "写入歌词失败") }
  }

  private fun registerInMediaStore(file: File) {
    runCatching {
      MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }.onFailure { Timber.tag("OnlineDownloader").w(it, "注册媒体库失败") }
  }

  private fun buildFileName(track: OnlineTrack): String {
    val artist = track.artist.takeIf { it.isNotBlank() } ?: "未知艺术家"
    val title = track.title.takeIf { it.isNotBlank() } ?: "未知歌曲"
    val base = when (settings.fileNameStyle()) {
      FileNameStyle.TITLE_ARTIST -> "$title - $artist"
      FileNameStyle.ARTIST_TITLE -> "$artist - $title"
      FileNameStyle.TITLE_ONLY -> title
    }
    return "${sanitize(base)}.${extensionOf(track)}"
  }

  private fun extensionOf(track: OnlineTrack): String = when {
    track.qualityKeys.any { it.contains("flac") } -> "flac"
    else -> "mp3"
  }

  private fun sanitize(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_").trim().take(120)
}