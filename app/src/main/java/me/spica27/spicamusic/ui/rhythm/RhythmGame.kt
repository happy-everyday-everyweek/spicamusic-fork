package me.spica27.spicamusic.ui.rhythm

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.net.toUri
import com.linc.amplituda.Amplituda
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.feature.library.domain.SongUseCases
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmChart
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmNote
import org.koin.compose.koinInject
import timber.log.Timber
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 音乐游戏：跟着歌曲的节奏点敲四条轨道。
 *
 * 谱面从音频波形现场分析得来，长音会变成需要长按的长条；进入游戏时申请独占音频焦点，
 * 正在播放的音乐会自动让路，退出后恢复。游戏中可随时暂停，暂停后能继续或直接结束本局；
 * 结束时统一进结算页，给得分、最高连击、逐项判定与等级。
 * 界面保持安静：游戏中顶部只有连击与暂停，反馈交给落点、闪光与震动。
 */
/** 启动音乐游戏所需的最小信息，歌曲与播放器当前媒体项都能喂进来。 */
data class MusicGameSource(
  val mediaStoreId: Long,
  val path: String,
  val title: String,
  val artist: String,
  val durationMs: Long,
  val waveformData: String?,
)

@Composable
fun RhythmGameOverlay(song: Song, onClose: () -> Unit) {
  RhythmGameOverlay(
    source =
      MusicGameSource(
        mediaStoreId = song.mediaStoreId,
        path = song.path,
        title = song.displayName,
        artist = song.artist,
        durationMs = song.duration,
        waveformData = song.waveformData,
      ),
    onClose = onClose,
  )
}

@Composable
fun RhythmGameOverlay(source: MusicGameSource, onClose: () -> Unit) {
  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    RhythmGameSurface(song = source, onClose = onClose)
  }
}

private enum class Judge(val score: Int, val vibrationMs: Long, val vibrationAmplitude: Int) {
  Perfect(300, 16L, 170),
  Great(150, 10L, 110),
  Miss(0, 34L, 255),
}

private const val LANE_COUNT = 4
private const val FALL_WINDOW_MS = 1800f
private const val PERFECT_WINDOW_MS = 90f
private const val GREAT_WINDOW_MS = 200f
private const val MIN_GAP_MS = 110L
private const val HOLD_MIN_MS = 420L

@Composable
private fun RhythmGameSurface(song: MusicGameSource, onClose: () -> Unit) {
  val context = LocalContext.current
  val amplituda = koinInject<Amplituda>()
  val songUseCases = koinInject<SongUseCases>()

  var chart by remember(song.mediaStoreId) { mutableStateOf<RhythmChart?>(null) }
  var analyzing by remember(song.mediaStoreId) { mutableStateOf(true) }
  var analysisAttempt by remember(song.mediaStoreId) { mutableIntStateOf(0) }

  LaunchedEffect(song.mediaStoreId, analysisAttempt) {
    analyzing = true
    chart = null
    val amplitudes =
      withContext(Dispatchers.IO) {
        val cached = song.waveformData?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        if (cached.isNotEmpty()) return@withContext cached
        val extracted = analyseWithAmplituda(context, amplituda, song)
        if (extracted.isNotEmpty()) {
          runCatching {
            songUseCases.updateSongWaveform(song.mediaStoreId, extracted.joinToString(","))
          }.onFailure { Timber.tag("RhythmGame").w(it, "回写波形失败") }
        }
        extracted
      }
    if (amplitudes.isEmpty()) {
      analyzing = false
      return@LaunchedEffect
    }
    chart = buildChart(song, amplitudes)
    // 游戏时长同样计入听歌统计：按一次完整播放记入历史。
    runCatching { songUseCases.addPlayHistory(song.mediaStoreId) }
      .onFailure { Timber.tag("RhythmGame").w(it, "写入播放历史失败") }
    analyzing = false
  }

  val readyChart = chart
  if (analyzing) {
    Box(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text("正在分析这首歌的节奏…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    return
  }
  if (readyChart == null || readyChart.notes.isEmpty()) {
    Box(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("没能分析出这首歌的节奏", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
          "这首歌的音频暂时无法解码成波形，可以换一首试试。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { analysisAttempt += 1 }) { Text("重试") }
        OutlinedButton(onClick = onClose) { Text("返回") }
      }
    }
    return
  }

  RhythmPlay(chart = readyChart, song = song, context = context, onClose = onClose)
}

@Composable
private fun RhythmPlay(
  chart: RhythmChart,
  song: MusicGameSource,
  context: Context,
  onClose: () -> Unit,
) {
  val notes = chart.notes
  val totalNotes = notes.size
  val consumed = remember(chart) { mutableStateListOf<Boolean>().apply { repeat(totalNotes) { add(false) } } }
  val laneFlash = remember(chart) { mutableStateListOf<Float>().apply { repeat(LANE_COUNT) { add(0f) } } }

  var positionMs by remember { mutableLongStateOf(0L) }
  var score by remember { mutableIntStateOf(0) }
  var combo by remember { mutableIntStateOf(0) }
  var bestCombo by remember { mutableIntStateOf(0) }
  var perfectCount by remember { mutableIntStateOf(0) }
  var greatCount by remember { mutableIntStateOf(0) }
  var missCount by remember { mutableIntStateOf(0) }
  var paused by remember { mutableStateOf(false) }
  var finished by remember { mutableStateOf(false) }
  var attempt by remember { mutableIntStateOf(0) }
  var player by remember { mutableStateOf<MediaPlayer?>(null) }

  val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

  DisposableEffect(song.mediaStoreId, attempt) {
    val focusRequest =
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setWillPauseWhenDucked(true)
        .build()
    runCatching { audioManager?.requestAudioFocus(focusRequest) }
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
      runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
      player = null
    }
  }

  LaunchedEffect(chart, attempt) {
    val endMs = (notes.maxOfOrNull { it.timeMs + it.durationMs } ?: 0L) + 1500L
    while (isActive && !finished) {
      if (paused) {
        delay(32)
        continue
      }
      positionMs = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
      notes.forEachIndexed { index, note ->
        val tailMs = note.timeMs + note.durationMs
        if (!consumed[index] && positionMs - tailMs > GREAT_WINDOW_MS) {
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
  val muted = MaterialTheme.colorScheme.onSurfaceVariant

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(surfaceColor)
        .pointerInput(chart, finished, paused) {
          if (finished || paused) return@pointerInput
          detectTapGestures { offset ->
            val lane = ((offset.x / (size.width / LANE_COUNT)).toInt()).coerceIn(0, LANE_COUNT - 1)
            val hitIndex =
              notes.indices
                .filter { !consumed[it] && notes[it].lane == lane }
                .minByOrNull { abs(notes[it].timeMs - positionMs) }
            if (hitIndex != null) {
              val delta = abs(notes[hitIndex].timeMs - positionMs).toFloat()
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
      notes.forEachIndexed { index, note ->
        if (consumed[index]) return@forEachIndexed
        val headDelta = note.timeMs - positionMs
        val headY = judgeLineY - headDelta / FALL_WINDOW_MS * judgeLineY
        val laneX = note.lane * laneWidth + laneWidth * 0.12f
        val noteWidth = laneWidth * 0.76f
        if (note.durationMs > 0L) {
          val tailY = judgeLineY - (headDelta + note.durationMs) / FALL_WINDOW_MS * judgeLineY
          val top = minOf(headY, tailY) - 13f
          val bottom = maxOf(headY, tailY) + 13f
          if (bottom > -80f) {
            drawRoundRect(
              color = noteColor.copy(alpha = 0.45f),
              topLeft = Offset(laneX, top),
              size = Size(noteWidth, bottom - top),
              cornerRadius = CornerRadius(16f, 16f),
            )
          }
        } else if (headDelta <= FALL_WINDOW_MS && headY > -80f) {
          drawRoundRect(
            color = noteColor.copy(alpha = 0.9f),
            topLeft = Offset(laneX, headY - 13f),
            size = Size(noteWidth, 26f),
            cornerRadius = CornerRadius(16f, 16f),
          )
        }
      }
    }

    if (!finished && combo > 1) {
      Text(
        text = "$combo",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = comboColor.copy(alpha = 0.85f),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
      )
    }

    if (!finished) {
      OutlinedButton(
        onClick = {
          paused = !paused
          runCatching {
            if (paused) player?.pause() else player?.start()
          }
        },
        modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
      ) { Text(if (paused) "继续" else "暂停") }
    }

    if (paused && !finished) {
      Box(
        modifier = Modifier.fillMaxSize().background(surfaceColor.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("已暂停", style = MaterialTheme.typography.titleLarge, color = comboColor)
          Text(
            "当前连击 $combo · 得分 $score",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
          )
          Button(onClick = {
            paused = false
            runCatching { player?.start() }
          }) { Text("继续") }
          OutlinedButton(onClick = {
            paused = false
            runCatching { player?.pause() }
            finished = true
          }) { Text("结束本局") }
          OutlinedButton(onClick = onClose) { Text("退出游戏") }
        }
      }
    }

    if (finished) {
      RhythmResultDialog(
        song = song,
        score = score,
        bestCombo = bestCombo,
        perfectCount = perfectCount,
        greatCount = greatCount,
        missCount = missCount,
        totalNotes = totalNotes,
        onRestart = {
          score = 0
          combo = 0
          bestCombo = 0
          perfectCount = 0
          greatCount = 0
          missCount = 0
          paused = false
          finished = false
          positionMs = 0L
          for (index in consumed.indices) consumed[index] = false
          for (lane in 0 until LANE_COUNT) laneFlash[lane] = 0f
          attempt += 1
        },
        onClose = onClose,
      )
    }
  }
}

/** 结算页：得分、最高连击、逐项判定、命中率与等级。 */
@Composable
private fun RhythmResultDialog(
  song: Song,
  score: Int,
  bestCombo: Int,
  perfectCount: Int,
  greatCount: Int,
  missCount: Int,
  totalNotes: Int,
  onRestart: () -> Unit,
  onClose: () -> Unit,
) {
  val hitValue = perfectCount + greatCount * 0.6f
  val accuracy = if (totalNotes > 0) (hitValue / totalNotes).coerceIn(0f, 1f) else 0f
  val grade =
    when {
      accuracy >= 0.95f -> "S"
      accuracy >= 0.85f -> "A"
      accuracy >= 0.7f -> "B"
      accuracy >= 0.5f -> "C"
      else -> "D"
    }

  Box(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(song.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      Text(song.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        grade,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
      )
      Text("得分 $score", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
      Text("最高连击 $bestCombo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
      Text(
        "完美 $perfectCount · 良好 $greatCount · 错过 $missCount",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        "命中率 ${(accuracy * 100).roundToInt()}% · 音符 $totalNotes",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onRestart, modifier = Modifier.padding(top = 8.dp)) { Text("再来一局") }
      OutlinedButton(onClick = onClose) { Text("回到歌曲") }
    }
  }
}

/** 现场分析：优先直接用文件路径，路径不可读时再从 Uri 复制到缓存再解。 */
private fun analyseWithAmplituda(context: Context, amplituda: Amplituda, song: Song): List<Int> {
  val direct = File(song.path)
  if (direct.exists() && direct.canRead()) {
    return runAmplituda(amplituda, direct)
  }
  val temp =
    runCatching {
      val source = runCatching { context.contentResolver.openInputStream(song.path.toUri()) }.getOrNull()
      if (source == null) {
        null
      } else {
        val file = File.createTempFile("music_game_wave", null, context.cacheDir)
        source.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        file
      }
    }
      .getOrNull()
      ?: return emptyList()
  return try {
    runAmplituda(amplituda, temp)
  } finally {
    runCatching { temp.delete() }
  }
}

private fun runAmplituda(amplituda: Amplituda, file: File): List<Int> {
  var result: List<Int> = emptyList()
  runCatching {
    amplituda.processAudio(file).get(
      { amplitudes -> result = amplitudes.amplitudesAsList() },
      { error -> Timber.tag("RhythmGame").w(error, "波形分析失败") },
    )
  }
  return result
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
    vibrator?.vibrate(VibrationEffect.createOneShot(judge.vibrationMs, judge.vibrationAmplitude))
  }
}

/**
 * 把波形变成谱面：自适应阈值抓局部能量峰，长音生成长条，轨道按强弱成对左右分配。
 */
private fun buildChart(song: Song, rawAmplitudes: List<Int>): RhythmChart {
  val size = rawAmplitudes.size
  if (size < 8) {
    return RhythmChart(song.displayName, song.artist, song.duration, LANE_COUNT, emptyList())
  }
  val values = FloatArray(size) { rawAmplitudes[it].toFloat().coerceAtLeast(0f) }
  val max = values.max()
  if (max <= 0f) {
    return RhythmChart(song.displayName, song.artist, song.duration, LANE_COUNT, emptyList())
  }
  val norm = FloatArray(size) { values[it] / max }
  val intervalMs =
    if (song.duration > 0L) (song.duration / size).coerceAtLeast(1L) else 20L

  val notes = ArrayList<RhythmNote>()
  val window = 40
  var index = 1
  var lastAcceptedMs = Long.MIN_VALUE / 2
  var seed = 0

  while (index < size - 1) {
    val from = (index - window).coerceAtLeast(0)
    val to = (index + window).coerceAtMost(size - 1)
    var sum = 0f
    for (i in from..to) sum += norm[i]
    val localMean = sum / (to - from + 1)
    val threshold = (localMean * 1.4f).coerceAtLeast(0.18f)
    val strength = norm[index]
    val isPeak = strength >= threshold && strength >= norm[index - 1] && strength >= norm[index + 1]
    val timeMs = index * intervalMs

    if (isPeak && timeMs - lastAcceptedMs >= MIN_GAP_MS) {
      var tail = index
      while (tail + 1 < size && norm[tail + 1] >= localMean * 0.85f) tail++
      val holdMs = (tail - index) * intervalMs
      val durationMs = if (holdMs >= HOLD_MIN_MS) holdMs.coerceAtMost(4000L) else 0L
      val left = seed % 2 == 0
      val lane =
        when {
          strength >= 0.8f -> if (left) 0 else LANE_COUNT - 1
          strength >= 0.55f -> if (left) 1 else LANE_COUNT - 2
          else -> if (left) LANE_COUNT / 2 else LANE_COUNT / 2 - 1
        }.coerceIn(0, LANE_COUNT - 1)
      notes += RhythmNote(timeMs = timeMs, lane = lane, strength = strength, durationMs = durationMs)
      lastAcceptedMs = timeMs
      seed++
      index = (tail + 1).coerceAtLeast(index + 1)
      continue
    }
    index++
  }

  return RhythmChart(song.displayName, song.artist, song.duration, LANE_COUNT, notes)
}
