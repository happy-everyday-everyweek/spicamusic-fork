package me.spica27.spicamusic.feature.library.domain.rhythm

/** 音游谱面里的一个音符。 */
data class RhythmNote(
  val timeMs: Long,
  /** 横向位置，0..1 的自由落点（没有固定轨道）。 */
  val lane: Float,
  val strength: Float,
  /** 长按音符的持续时长；0 表示单点。 */
  val durationMs: Long = 0L,
)

/** 一份可导出的音游谱面。 */
data class RhythmChart(
  val title: String,
  val artist: String,
  val durationMs: Long,
  val laneCount: Int,
  val notes: List<RhythmNote>,
) {
  /** 与社区通用的简易谱面 JSON（note 的 time 为毫秒，lane 从 0 开始）。 */
  fun toJson(): String = buildString {
    append("{\n")
    append("  \"title\": \"").append(title.escape()).append("\",\n")
    append("  \"artist\": \"").append(artist.escape()).append("\",\n")
    append("  \"durationMs\": ").append(durationMs).append(",\n")
    append("  \"laneCount\": ").append(laneCount).append(",\n")
    append("  \"notes\": [\n")
    notes.forEachIndexed { index, note ->
      append("    { \"timeMs\": ").append(note.timeMs)
      append(", \"lane\": ").append(note.lane)
      append(", \"strength\": ").append(String.format("%.2f", note.strength))
      append(" }")
      if (index != notes.lastIndex) append(",")
      append("\n")
    }
    append("  ]\n")
    append("}\n")
  }

  private fun String.escape(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}

/**
 * 从歌曲的波形幅度数据生成一份简易音游谱面。
 *
 * 输入的波形是等间隔采样后的归一化幅度（0..1），[intervalMs] 表示两个采样点之间的时间。
 * 判定方式与音游常见的“踩点”口径一致：幅度越过阈值、且离开上一个音符至少有
 * [minGapMs] 的间隔才算一个音符；相邻两拍的强度差用于决定落在哪条轨道，
 * 让密集段落自然分散、稀疏段落回到中线。
 */
object RhythmChartGenerator {

  data class Parameters(
    val laneCount: Int = 4,
    val threshold: Float = 0.55f,
    val minGapMs: Long = 130L,
    val maxNotes: Int = 2000,
  )

  fun generate(
    title: String,
    artist: String,
    durationMs: Long,
    waveform: FloatArray,
    intervalMs: Long,
    parameters: Parameters = Parameters(),
  ): RhythmChart {
    val notes = mutableListOf<RhythmNote>()
    if (waveform.isEmpty() || intervalMs <= 0L) {
      return RhythmChart(title, artist, durationMs, parameters.laneCount, notes)
    }

    var lastAcceptedMs = Long.MIN_VALUE / 2
    var lastStrength = 0f

    waveform.forEachIndexed { index, raw ->
      val strength = raw.coerceIn(0f, 1f)
      val timeMs = index * intervalMs
      if (strength < parameters.threshold) return@forEachIndexed
      if (timeMs - lastAcceptedMs < parameters.minGapMs) return@forEachIndexed
      if (notes.size >= parameters.maxNotes) return@forEachIndexed

      // 与上一拍的能量对比决定轨道：更强就往右偏，更弱往左偏，持平留在中间。
      // 自由落点：不设固定轨道。横向位置由这一拍的强弱决定（响的偏右、轻的偏左），
      // 再叠一点随时间来回的摆动，避免整首歌都落在同一条竖线上。
      val wobble = if ((timeMs / 400L) % 2L == 0L) 0.12f else -0.12f
      val lane = (0.14f + 0.72f * strength + wobble).coerceIn(0.06f, 0.94f)

      notes += RhythmNote(timeMs = timeMs, lane = lane, strength = strength)
      lastAcceptedMs = timeMs
      lastStrength = strength
    }

    return RhythmChart(
      title = title,
      artist = artist,
      durationMs = if (durationMs > 0L) durationMs else waveform.size * intervalMs,
      laneCount = parameters.laneCount,
      notes = notes,
    )
  }
}