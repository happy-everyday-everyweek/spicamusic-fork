package me.spica27.spicamusic.ui.rhythm

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmChart
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmChartGenerator
import kotlin.math.abs

/**
 * 用歌曲波形生成的节奏点玩一局：四轨落点、点击判定、连击与计分。
 *
 * 界面刻意保持安静：游戏中顶部只有连击数，落点、命中闪光与震动承担全部反馈，
 * 得分与逐项判定留到结算页再给。
 */
@Composable
fun RhythmGameOverlay(song: Song, onClose: () -> Unit) {
  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    RhythmGameSurface(song = song, onClose = onClose)
  }
}

private enum class Judge(val score: Int, val vibrationMs: Long, val vibrationAmplitude: Int) {
  Perfect(300, 16L, 170),
  Great(150, 10L, 110),
  Miss(0, 34L, 255),
}

private const val LANE_COUNT = 4
private const val FALL_WINDOW_MS = 1600f
private const val PERFECT_WINDOW_MS = 90f
private const val GREAT_WINDOW_MS = 190f

@Composable
private fun RhythmGameSurface(song: Song, onClose: () -> Unit) {
  val context = LocalContext.current
  val chart = remember(song.mediaStoreId) { buildChart(song) }
  val consumed = remember(chart) { mutableStateListOf<Boolean>().apply { repeat(chart.notes.size) { add(false) } } }
  val laneFlash = remember(chart) { mutableStateListOf<Float>().apply { repeat(LANE_COUNT) { add(0f) } } }

  var positionMs by remember { mutableLongStateOf(0L) }
  var score by remember { mutableIntStateOf(0) }
  var combo by remember { mutableIntStateOf(0) }
  var bestCombo by remember { mutableIntStateOf(0) }
  var perfectCount by remember { mutableIntStateOf(0) }
  var greatCount by remember { mutableIntStateOf(0) }
  var missCount by remember { mutableIntStateOf(0) }
  var finished by remember { mutableStateOf(false) }
  var attempt by remember { mutableIntStateOf(0) }
  var player by remember { mutableStateOf<MediaPlayer?>(null) }

  DisposableEffect(song.mediaStoreId, attempt) {
    val mediaPlayer =
      runCatching {
          MediaPlayer().apply {
            setDataSource(song.path)
            prepare()
            start()
          }
        }
        .getOrNull()
    player = mediaPlayer
    onDispose {
      runCatching { mediaPlayer?.release() }
      player = null
    }
  }

  LaunchedEffect(chart, attempt) {
    val endMs = (chart.notes.lastOrNull()?.timeMs ?: 0L) + 1500L
    while (isActive && !finished) {
      positionMs = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
      chart.notes.forEachIndexed { index, note ->
        if (!consumed[index] && positionMs - note.timeMs > GREAT_WINDOW_MS) {
          consumed[index] = true
          combo = 0
          missCount += 1
          vibrate(context, Judge.Miss)
        }
      }
      for (lane in 0 until LANE_COUNT) {
        if (laneFlash[lane] > 0f) laneFlash[lane] = (laneFlash[lane] - 0.07f).coerceAtLeast(0f)
      }
      if (positionMs > endMs && endMs > 1500L) finished = true
      delay(16)
    }
  }

  val surfaceColor = MaterialTheme.colorScheme.surface
  val laneColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val noteColor = MaterialTheme.colorScheme.primary
  val accentColor = MaterialTheme.colorScheme.tertiary
  val comboColor = MaterialTheme.colorScheme.onSurface

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(surfaceColor)
        .pointerInput(chart, finished) {
          if (finished) return@pointerInput
          detectTapGestures { offset ->
            val lane = ((offset.x / (size.width / LANE_COUNT)).toInt()).coerceIn(0, LANE_COUNT - 1)
            val hitIndex =
              chart.notes.indices
                .filter { !consumed[it] && chart.notes[it].lane == lane }
                .minByOrNull { abs(chart.notes[it].timeMs - positionMs) }
            if (hitIndex != null) {
              val delta = abs(chart.notes[hitIndex].timeMs - positionMs).toFloat()
              if (delta <= GREAT_WINDOW_MS) {
                consumed[hitIndex] = true
                val judge = if (delta <= PERFECT_WINDOW_MS) Judge.Perfect else Judge.Great
                when (judge) {
                  Judge.Perfect -> perfectCount += 1
                  Judge.Great -> greatCount += 1
                  Judge.Miss -> missCount += 1
                }
                combo += 1
                bestCombo = maxOf(bestCombo, combo)
                score += judge.score + combo * 2
                laneFlash[lane] = 1f
                vibrate(context, judge)
              }
            }
          }
        },
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val laneWidth = size.width / LANE_COUNT
      val judgeLineY = size.height * 0.82f
      for (lane in 0 until LANE_COUNT) {
        drawRect(
          color = laneColor.copy(alpha = if (lane % 2 == 0) 0.32f else 0.5f),
          topLeft = Offset(lane * laneWidth, 0f),
          size = Size(laneWidth, size.height),
        )
      }
      // 命中闪光：整条轨道在判定线附近亮一下，闪光强度随判定衰减
      for (lane in 0 until LANE_COUNT) {
        val flash = laneFlash[lane]
        if (flash <= 0f) continue
        drawRect(
          color = accentColor.copy(alpha = 0.22f * flash),
          topLeft = Offset(lane * laneWidth, 0f),
          size = Size(laneWidth, judgeLineY),
        )
        drawRect(
          color = accentColor.copy(alpha = 0.9f * flash),
          topLeft = Offset(lane * laneWidth, judgeLineY - 5f),
          size = Size(laneWidth, 10f),
        )
      }
      drawRect(
        color = accentColor.copy(alpha = 0.7f),
        topLeft = Offset(0f, judgeLineY - 2f),
        size = Size(size.width, 4f),
      )
      chart.notes.forEachIndexed { index, note ->
        if (consumed[index]) return@forEachIndexed
        val deltaMs = note.timeMs - positionMs
        if (deltaMs > FALL_WINDOW_MS) return@forEachIndexed
        val y = judgeLineY - deltaMs / FALL_WINDOW_MS * judgeLineY
        if (y < -80f) return@forEachIndexed
        drawRoundRect(
          color = noteColor.copy(alpha = 0.9f),
          topLeft = Offset(note.lane * laneWidth + laneWidth * 0.12f, y - 13f),
          size = Size(laneWidth * 0.76f, 26f),
          cornerRadius = CornerRadius(16f, 16f),
        )
      }
    }

    // 游戏进行时只显示连击，其余信息留到结算
    if (!finished && combo > 1) {
      Text(
        text = "$combo",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = comboColor.copy(alpha = 0.85f),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
      )
    }

    Row(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End,
    ) {
      OutlinedButton(onClick = onClose) { Text("退出") }
    }

    if (finished) {
      Box(
        modifier = Modifier.fillMaxSize().background(surfaceColor.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(song.displayName, style = MaterialTheme.typography.titleMedium, color = comboColor)
          Text("得分 $score", style = MaterialTheme.typography.headlineSmall, color = comboColor, fontWeight = FontWeight.Bold)
          Text("最高连击 $bestCombo", style = MaterialTheme.typography.bodyMedium, color = accentColor)
          Text(
            "完美 $perfectCount · 良好 $greatCount · 错过 $missCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(onClick = {
            score = 0
            combo = 0
            bestCombo = 0
            perfectCount = 0
            greatCount = 0
            missCount = 0
            finished = false
            positionMs = 0L
            for (index in consumed.indices) consumed[index] = false
            for (lane in 0 until LANE_COUNT) laneFlash[lane] = 0f
            attempt += 1
          }) { Text("再来一局") }
          OutlinedButton(onClick = onClose) { Text("回到歌曲") }
        }
      }
    }
  }
}

/** 命中反馈：完美/良好/错过用不同的震动时长与强度区分。 */
private fun vibrate(context: Context, judge: Judge) {
  runCatching {
    val vibrator =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
      } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
    vibrator?.vibrate(
      VibrationEffect.createOneShot(judge.vibrationMs, judge.vibrationAmplitude),
    )
  }
}

/** 从歌曲已有的波形数据生成谱面；没有波形时用一份兜底节奏，保证能玩。 */
private fun buildChart(song: Song): RhythmChart {
  val amplitudes = parseWaveform(song.waveformData)
  val intervalMs =
    if (amplitudes.isNotEmpty() && song.duration > 0L) song.duration / amplitudes.size else 20L
  if (amplitudes.isEmpty()) {
    val fallbackCount = ((song.duration.coerceAtLeast(30_000L) / 500L).toInt()).coerceAtMost(400)
    val fallback = FloatArray(fallbackCount) { index -> if (index % 2 == 0) 0.9f else 0.2f }
    return RhythmChartGenerator.generate(
      title = song.displayName,
      artist = song.artist,
      durationMs = song.duration,
      waveform = fallback,
      intervalMs = 500L,
    )
  }
  return RhythmChartGenerator.generate(
    title = song.displayName,
    artist = song.artist,
    durationMs = song.duration,
    waveform = amplitudes,
    intervalMs = intervalMs,
  )
}

private fun parseWaveform(raw: String?): FloatArray {
  val values = raw?.split(",")?.mapNotNull { it.trim().toFloatOrNull() } ?: return FloatArray(0)
  val max = values.maxOrNull() ?: 0f
  if (max <= 0f) return FloatArray(0)
  return FloatArray(values.size) { index -> (values[index] / max).coerceIn(0f, 1f) }
}
