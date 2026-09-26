package me.spica27.spicamusic.ui.rhythm

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.beatroot.BeatRootOnsetEventHandler
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.TarsosDSPAudioInputStream
import be.tarsos.dsp.onsets.ComplexOnsetDetector
import be.tarsos.dsp.onsets.OnsetHandler
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmChart
import me.spica27.spicamusic.feature.library.domain.rhythm.RhythmNote
import timber.log.Timber
import java.io.File
import java.nio.ByteOrder

/**
 * 音游谱面分析。
 *
 * 分析链完全采用成熟项目 TarsosDSP（Joren Six，2011 年至今维护，2.2k Star）的做法：
 * 先把音频解码成单声道 PCM，交给 [ComplexOnsetDetector] 做 Complex-Domain 起音检测
 * （aubio 算法的 Java 移植），再用 BeatRoot（Simon Dixon，JNMR 论文算法）从起音序列
 * 推断节拍。谱面音符全部来自真实检测结果；分析失败时返回空谱面，由界面如实提示，
 * 不生成任何兜底内容。
 */
object TarsosChartAnalyzer {

  /** TarsosDSP 缓冲区大小与重叠，与官方示例一致。 */
  private const val BUFFER_SIZE = 1024
  private const val OVERLAP = 512

  /** 起音阈值：官方建议 0.1~0.8；取 0.4 抑制密集段落的误检。 */
  private const val PEAK_THRESHOLD = 0.4

  /** 两个音符之间的最短间隔（毫秒），避免同一次敲击被重复计数。 */
  private const val MIN_NOTE_GAP_MS = 90L

  /** 长条上限 4 秒。 */
  private const val HOLD_CAP_MS = 4000L

  fun analyze(
    file: File,
    title: String,
    artist: String,
    fallbackDurationMs: Long,
  ): RhythmChart {
    val decoded = runCatching { decodeMonoPcm(file) }.getOrNull()
    if (decoded == null || decoded.samples.size < BUFFER_SIZE * 2) {
      Timber.tag("TarsosChart").w("解码结果不可用：${file.name}")
      return emptyChart(title, artist, fallbackDurationMs)
    }

    val onsets = ArrayList<DoubleArray>(1024)
    val beatRoot = BeatRootOnsetEventHandler()
    val detector = ComplexOnsetDetector(BUFFER_SIZE, PEAK_THRESHOLD)
    detector.setHandler(
      OnsetHandler { time, salience ->
        onsets.add(doubleArrayOf(time, salience))
        beatRoot.handleOnset(time, salience)
      },
    )

    val dispatcher =
      AudioDispatcher(
        PcmStream(decoded.samples, decoded.sampleRate.toFloat()),
        BUFFER_SIZE,
        OVERLAP,
      )
    dispatcher.addAudioProcessor(detector)
    runCatching { dispatcher.run() }
      .onFailure { Timber.tag("TarsosChart").w(it, "起音分析失败") }

    val beats = ArrayList<Double>()
    runCatching {
      beatRoot.trackBeats(OnsetHandler { time, _ -> beats.add(time) })
    }.onFailure { Timber.tag("TarsosChart").w(it, "节拍推断失败") }

    val durationMs =
      if (decoded.samples.isNotEmpty()) {
        decoded.samples.size * 1000L / decoded.sampleRate
      } else {
        fallbackDurationMs
      }

    val beatMs = medianBeatMs(beats)
    val notes = buildNotes(onsets, beatMs)
    if (notes.isEmpty()) {
      Timber.tag("TarsosChart").i("未检测到有效起音，返回空谱面")
    }
    return RhythmChart(
      title = title,
      artist = artist,
      durationMs = if (durationMs > 0L) durationMs else fallbackDurationMs,
      laneCount = 1,
      notes = notes,
    )
  }

  /** 把起音序列变成音符：按显著度过滤、维持最小间隔、按节拍间隔判定长条。 */
  private fun buildNotes(onsets: List<DoubleArray>, beatMs: Long): List<RhythmNote> {
    if (onsets.isEmpty()) return emptyList()
    val saliences = onsets.map { it[1] }.sorted()
    val threshold = saliences[(saliences.size * 3) / 5]
    val accepted =
      onsets
        .filter { it[1] >= threshold && it[0] >= 0.0 }
        .sortedBy { it[0] }
    if (accepted.isEmpty()) return emptyList()

    val maxSalience = saliences.last().coerceAtLeast(1e-6)
    val notes = ArrayList<RhythmNote>(accepted.size)
    var index = 0
    for (onset in accepted) {
      val timeMs = (onset[0] * 1000.0).toLong()
      val last = notes.lastOrNull()
      if (last != null && timeMs - last.timeMs < MIN_NOTE_GAP_MS) continue
      val strength = (onset[1] / maxSalience).toFloat().coerceIn(0f, 1f)
      notes += RhythmNote(timeMs = timeMs, lane = spreadLane(index), strength = strength)
      index++
    }

    // 长条：与下一个音符的间隔超过一拍半时，这一段视为持续音。
    if (beatMs > 0L && notes.size > 1) {
      val result = ArrayList<RhythmNote>(notes.size)
      for (i in notes.indices) {
        val note = notes[i]
        val nextGapMs = if (i + 1 < notes.size) notes[i + 1].timeMs - note.timeMs else Long.MAX_VALUE
        val holdMs = nextGapMs - beatMs / 2
        result +=
          if (holdMs >= beatMs + beatMs / 2) {
            note.copy(durationMs = holdMs.coerceIn(beatMs, HOLD_CAP_MS))
          } else {
            note
          }
      }
      return result
    }
    return notes
  }

  private fun medianBeatMs(beats: List<Double>): Long {
    if (beats.size < 2) return 0L
    val intervals = ArrayList<Long>(beats.size - 1)
    for (i in 1 until beats.size) {
      val ms = ((beats[i] - beats[i - 1]) * 1000.0).toLong()
      if (ms > 0L) intervals.add(ms)
    }
    if (intervals.isEmpty()) return 0L
    intervals.sort()
    return intervals[intervals.size / 2]
  }

  /** 自由落点：黄金分割在横向均匀铺开，没有固定轨道。 */
  private fun spreadLane(seed: Int): Float {
    val spread = (seed * 0.6180339f) % 1f
    return (0.12f + 0.76f * spread).coerceIn(0.08f, 0.92f)
  }

  private fun emptyChart(title: String, artist: String, durationMs: Long): RhythmChart =
    RhythmChart(title, artist, durationMs, 1, emptyList())

  // ────────────────────────── 音频解码 ──────────────────────────

  private class DecodedAudio(val samples: ShortArray, val sampleRate: Int)

  /** MediaExtractor + MediaCodec 解码成单声道 PCM；采样率高于 24kHz 时按 2:1 降采样。 */
  private fun decodeMonoPcm(file: File): DecodedAudio? {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(file.absolutePath)
      var trackIndex = -1
      var format: MediaFormat? = null
      for (i in 0 until extractor.trackCount) {
        val candidate = extractor.getTrackFormat(i)
        val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("audio/")) {
          trackIndex = i
          format = candidate
          break
        }
      }
      val trackFormat = format ?: return null
      if (trackIndex < 0) return null
      extractor.selectTrack(trackIndex)

      val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
      val codec = MediaCodec.createDecoderByType(mime)
      try {
        codec.configure(trackFormat, null, null, 0)
        codec.start()

        var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var decimate = sampleRate > 24_000
        val samples = ShortList()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var frameIndex = 0L

        while (!outputDone) {
          if (!inputDone) {
            val inIndex = codec.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
              val inBuffer = codec.getInputBuffer(inIndex)
              val size = if (inBuffer == null) -1 else extractor.readSampleData(inBuffer, 0)
              if (size < 0) {
                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
              } else {
                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                extractor.advance()
              }
            }
          }

          val outIndex = codec.dequeueOutputBuffer(info, 10_000)
          if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val outFormat = codec.outputFormat
            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            decimate = sampleRate > 24_000
          } else if (outIndex >= 0) {
            val outBuffer = codec.getOutputBuffer(outIndex)
            if (outBuffer != null && info.size > 0) {
              outBuffer.position(info.offset)
              outBuffer.limit(info.offset + info.size)
              val shorts = outBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
              val frames = shorts.remaining() / channels
              for (f in 0 until frames) {
                var acc = 0
                for (c in 0 until channels) {
                  val value = shorts.get().toInt()
                  if (c < 2) acc += value
                }
                val mono = if (channels >= 2) acc / 2 else acc
                if (!decimate || frameIndex % 2L == 0L) samples.add(mono.toShort())
                frameIndex++
              }
            }
            codec.releaseOutputBuffer(outIndex, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
          }
        }
        codec.stop()
        val targetRate = if (decimate) sampleRate / 2 else sampleRate
        return DecodedAudio(samples.toArray(), targetRate)
      } finally {
        runCatching { codec.release() }
      }
    } finally {
      runCatching { extractor.release() }
    }
  }

  /** 按 TarsosDSP 的音频流接口，把解码后的 PCM 喂给 [AudioDispatcher]。 */
  private class PcmStream(
    private val samples: ShortArray,
    private val sampleRate: Float,
  ) : TarsosDSPAudioInputStream {
    private var position = 0
    private val totalBytes = samples.size * 2

    override fun skip(bytesToSkip: Long): Long {
      val aligned = (bytesToSkip - bytesToSkip % 2).coerceAtLeast(0L)
      val actual = minOf(aligned, (totalBytes - position).toLong())
      position += actual.toInt()
      return actual
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (position >= totalBytes) return -1
      val aligned = (minOf(len, totalBytes - position)) and -2
      var out = off
      var pos = position
      var written = 0
      while (written < aligned) {
        val value = samples[pos / 2].toInt()
        b[out] = (value and 0xFF).toByte()
        b[out + 1] = ((value shr 8) and 0xFF).toByte()
        out += 2
        pos += 2
        written += 2
      }
      position = pos
      return written
    }

    override fun close() = Unit

    override fun getFormat(): TarsosDSPAudioFormat =
      TarsosDSPAudioFormat(sampleRate, 16, 1, true, false)

    override fun getFrameLength(): Long = samples.size.toLong()
  }

  /** 轻量 Short 增长数组，避免自动装箱。 */
  private class ShortList(initialCapacity: Int = 1 shl 16) {
    private var data = ShortArray(initialCapacity)
    private var size = 0

    fun add(value: Short) {
      if (size == data.size) data = data.copyOf(data.size * 2)
      data[size++] = value
    }

    fun toArray(): ShortArray = data.copyOf(size)
  }
}