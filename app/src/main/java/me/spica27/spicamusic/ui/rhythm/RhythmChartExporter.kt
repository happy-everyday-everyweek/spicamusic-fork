package me.spica27.spicamusic.ui.rhythm

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmChartGenerator
import java.io.File

/**
 * 把一首歌导出成音游谱面。
 *
 * 节奏点来自歌曲已有的波形数据（播放时由波形提取生成，按整首歌等间隔采样），
 * 因此不需要重新解码音频；导出结果是一份 JSON 谱面，落在应用的外部目录下，
 * 方便后续导入到音游或自己做可视化。
 */
object RhythmChartExporter {

  fun export(context: Context, song: Song) {
    val amplitudes = parseWaveform(song.waveformData)
    if (amplitudes.isEmpty()) {
      Toast.makeText(context, "这首歌还没有波形数据，先播放一次再导出", Toast.LENGTH_SHORT).show()
      return
    }

    val intervalMs =
      if (song.duration > 0L) song.duration / amplitudes.size else DEFAULT_INTERVAL_MS
    val appContext = context.applicationContext

    CoroutineScope(Dispatchers.IO).launch {
      val chart =
        RhythmChartGenerator.generate(
          title = song.displayName,
          artist = song.artist,
          durationMs = song.duration,
          waveform = amplitudes,
          intervalMs = intervalMs,
        )
      val result = runCatching {
        val dir = File(appContext.getExternalFilesDir(null), "rhythm-charts").apply { mkdirs() }
        val file = File(dir, "${sanitize(song.displayName)}.json")
        file.writeText(chart.toJson())
        file.absolutePath
      }
      withContext(Dispatchers.Main) {
        val message =
          result.fold(
            onSuccess = { path -> "谱面已导出：$path（${chart.notes.size} 个音符）" },
            onFailure = { error -> "导出失败：${error.message}" },
          )
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
      }
    }
  }

  /** 波形是逗号分隔的幅度整数，归一化到 0..1。 */
  private fun parseWaveform(raw: String?): FloatArray {
    val values = raw?.split(",")?.mapNotNull { it.trim().toFloatOrNull() } ?: return FloatArray(0)
    val max = values.maxOrNull() ?: 0f
    if (max <= 0f) return FloatArray(0)
    return FloatArray(values.size) { index -> (values[index] / max).coerceIn(0f, 1f) }
  }

  private fun sanitize(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().ifBlank { "rhythm-chart" }.take(120)

  private const val DEFAULT_INTERVAL_MS = 20L
}