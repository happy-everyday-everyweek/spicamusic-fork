package me.spica27.spicamusic.ui.rhythm

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmChart
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmChartGenerator
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmNote
import kotlin.math.abs

/**
 * 用歌曲波形生成的节奏点玩一局：四轨落点、点击判定、连击与计分。
 *
 * 玩法刻意保持简单——音符从顶部下落，到判定线时点对应轨道；
 * 判定分 完美 / 良好 / 错过三档，连击累乘加分。
 * 播放用系统 MediaPlayer 直接读本地文件，不依赖播放器状态，避免和播放队列互相影响。
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

/** 判定结果。 */
private enum class Judge(val label: String, val score: Int) {
  Perfect("完美", 300),
  Great("良好", 150),
  Miss("错过", 0),
}

private const val LANE_COUNT = 4
private const val FALL_WINDOW_MS = 1600f
private const val PERFECT_WINDOW_MS = 90f
private const val GREAT_WINDOW_MS = 190f

@Composable
private fun RhythmGameSurface(song: Song, onClose: () -> Unit) {
  val chart = remember(song.mediaStoreId) { buildChart(song) }
  val consumed = remember(chart) { mutableStateListOf<Boolean>().apply { repeat(chart.notes.size) { add(false) } } }

  var positionMs by remember { mutableLongStateOf(0L) }
  var score by remember { mutableStateOf(0) }
  var combo by remember { mutableStateOf(0) }
  var bestCombo by remember { mutableStateOf(0) }
  var lastJudge by remember { mutableStateOf<Judge?>(null) }
  var finished by remember { mutableStateOf(false) }
  var attempt by remember { mutableStateOf(0) }

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

  // 帧循环：读播放位置驱动落点，顺带把错过判定补上。
  LaunchedEffect(chart, attempt) {
    val endMs = (chart.notes.lastOrNull()?.timeMs ?: 0L) + 1500L
    while (isActive && !finished) {
      positionMs = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
      // 已经越过判定窗还没点的音符记为错过
      chart.notes.forEachIndexed { index, note ->
        if (!consumed[index] && positionMs - note.timeMs > GREAT_WINDOW_MS) {
          consumed[index] = true
          combo = 0
          lastJudge = Judge.Miss
        }
      }
      if (positionMs > endMs && endMs > 1500L) finished = true
      delay(16)
    }
  }

  val surfaceColor = MaterialTheme.colorScheme.surface
  val laneColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val noteColor = MaterialTheme.colorScheme.primary
  val accentColor = MaterialTheme.colorScheme.tertiary
  val onSurface = MaterialTheme.colorScheme.onSurface
  val muted = MaterialTheme.colorScheme.onSurfaceVariant

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
                val judge =
                  when {
                    delta <= PERFECT_WINDOW_MS -> Judge.Perfect
                    else -> Judge.Great
                  }
                lastJudge = judge
                combo += 1
                bestCombo = maxOf(bestCombo, combo)
                score += judge.score + combo * 2
              }
            }
          }
        },
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val laneWidth = size.width / LANE_COUNT
      val judgeLineY = size.height * 0.82f
      // 轨道分隔与底色
      for (lane in 0 until LANE_COUNT) {
        drawRect(
          color = laneColor.copy(alpha = if (lane % 2 == 0) 0.35f else 0.55f),
          topLeft = Offset(lane * laneWidth, 0f),
          size = Size(laneWidth, size.height),
        )
      }
      // 判定线
      drawRect(
        color = accentColor.copy(alpha = 0.85f),
        topLeft = Offset(0f, judgeLineY - 3f),
        size = Size(size.width, 6f),
      )
      // 下落中的音符
      chart.notes.forEachIndexed { index, note ->
        if (consumed[index]) return@forEachIndexed
        val deltaMs = note.timeMs - positionMs
        if (deltaMs > FALL_WINDOW_MS) return@forEachIndexed
        val y = judgeLineY - deltaMs / FALL_WINDOW_MS * judgeLineY
        if (y < -80f) return@forEachIndexed
        val noteHeight = 26f
        drawRoundRect(
          color = noteColor.copy(alpha = 0.9f),
          topLeft = Offset(note.lane * laneWidth + laneWidth * 0.12f, y - noteHeight / 2f),
          size = Size(laneWidth * 0.76f, noteHeight),
          cornerRadius = CornerRadius(16f, 16f),
        )
      }
    }

    // 顶部信息：分数 / 连击 / 判定
    Column(
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(song.displayName, style = MaterialTheme.typography.titleSmall, color = onSurface, fontWeight = FontWeight.SemiBold)
      Text("${song.artist}", style = MaterialTheme.typography.labelSmall, color = muted)
      Spacer(modifier = Modifier.height(8.dp))
      Text("$score", style = MaterialTheme.typography.headlineMedium, color = onSurface, fontWeight = FontWeight.Bold)
      Text("连击 $combo", style = MaterialTheme.typography.labelLarge, color = accentColor)
      lastJudge?.let { judge ->
        Text(
          judge.label,
          style = MaterialTheme.typography.labelMedium,
          color = if (judge == Judge.Miss) muted else accentColor,
        )
      }
    }

    // 底部：进度与关闭
    Row(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        "音符 ${chart.notes.size} · 最高连击 $bestCombo",
        style = MaterialTheme.typography.labelSmall,
        color = muted,
      )
      OutlinedButton(onClick = onClose) { Text("退出") }
    }

    if (finished) {
      Box(
        modifier = Modifier.fillMaxSize().background(surfaceColor.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("本局结束", style = MaterialTheme.typography.titleLarge, color = onSurface)
          Text("得分 $score · 最高连击 $bestCombo", style = MaterialTheme.typography.bodyMedium, color = muted)
          Button(onClick = {
            score = 0
            combo = 0
            bestCombo = 0
            lastJudge = null
            finished = false
            positionMs = 0L
            for (index in consumed.indices) consumed[index] = false
            attempt += 1
          }) { Text("再来一局") }
          OutlinedButton(onClick = onClose) { Text("回到歌曲") }
        }
      }
    }
  }
}

/** 从歌曲已有的波形数据生成谱面；没有波形时用一份密集兜底，保证能玩。 */
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

/** 让 `RhythmNote` 在本文件内可直接当数据用。 */
private typealias GameNote = RhythmNote