package me.spica27.spicamusic.online.original

/** 参与原唱比对的候选版本，来自不同音源。 */
data class VersionCandidate(
  val sourceKey: String,
  val title: String,
  val artist: String,
  val album: String?,
  val releaseYear: Int?,
  val durationMs: Long,
)

enum class OriginalityVerdict {
  Original,
  SuspectedCover,
  Unknown,
}

data class OriginalityResult(
  val verdict: OriginalityVerdict,
  val originalSourceKey: String? = null,
  val earliestYear: Int? = null,
  val reason: String = "",
)

/**
 * 原唱判定器。纯逻辑，不依赖网络与 Android。
 *
 * 两层判定：先用关键字规则做零成本标注（翻唱、伴奏、Live 等），
 * 再用跨来源的发行年份比对——只对用户选中的那一首按需取详情，不依赖搜索结果的分页内容。
 */
object OriginalVersionAdjudicator {

  private val coverKeywords = listOf(
    "cover", "翻唱", "伴奏", "纯音乐", "remix", "dj ", "dj版", "live", "现场", "抖音版",
    "女声版", "男声版", "钢琴版", "吉他版", "demo", "重制版", "片段",
  )

  fun flagByKeyword(title: String): OriginalityResult {
    val normalized = title.lowercase()
    val hit = coverKeywords.firstOrNull { normalized.contains(it) }
    return if (hit != null) {
      OriginalityResult(
        verdict = OriginalityVerdict.SuspectedCover,
        reason = "标题含「$hit」，可能是翻唱或非原版",
      )
    } else {
      OriginalityResult(verdict = OriginalityVerdict.Unknown, reason = "")
    }
  }

  /**
   * 跨来源比对：只考虑同名（去掉空白与大小写差异）且时长接近的候选，
   * 其中发行年份最早、且被多来源登记为同一艺人的版本视为原唱。
   */
  fun adjudicate(
    current: VersionCandidate,
    candidates: List<VersionCandidate>,
    durationToleranceMs: Long = 5_000L,
  ): OriginalityResult {
    val keywordResult = flagByKeyword(current.title)
    if (keywordResult.verdict == OriginalityVerdict.SuspectedCover) return keywordResult

    val normalizedTitle = normalize(current.title)
    val comparable = candidates.filter { candidate ->
      normalize(candidate.title) == normalizedTitle &&
        candidate.releaseYear != null &&
        (candidate.durationMs == 0L || current.durationMs == 0L ||
          kotlin.math.abs(candidate.durationMs - current.durationMs) <= durationToleranceMs)
    }

    if (comparable.isEmpty()) {
      return OriginalityResult(OriginalityVerdict.Unknown, reason = "没有拿到可比的发行年份")
    }

    val earliest = comparable.minByOrNull { it.releaseYear!! }!!
    val agreeOnArtist = comparable.count { normalize(it.artist) == normalize(earliest.artist) } > 1

    val currentNormalizedArtist = normalize(current.artist)
    val isCurrentOriginal =
      normalize(current.artist) == normalize(earliest.artist) &&
        current.releaseYear != null &&
        current.releaseYear == earliest.releaseYear

    return when {
      isCurrentOriginal -> OriginalityResult(
        verdict = OriginalityVerdict.Original,
        originalSourceKey = current.sourceKey,
        earliestYear = earliest.releaseYear,
        reason = "各来源登记一致，发行年份最早",
      )

      agreeOnArtist -> OriginalityResult(
        verdict = OriginalityVerdict.SuspectedCover,
        originalSourceKey = earliest.sourceKey,
        earliestYear = earliest.releaseYear,
        reason = "较早发行的是 ${earliest.artist}（$currentNormalizedArtist 与之不一致）",
      )

      else -> OriginalityResult(
        verdict = OriginalityVerdict.Unknown,
        earliestYear = earliest.releaseYear,
        reason = "各来源登记不一致，无法确认原唱",
      )
    }
  }

  private fun normalize(value: String): String =
    value.lowercase().replace(Regex("""[\s'・·\-—_()（）\[\]【】&]"""), "")
}