package me.spica27.spicamusic.online

import me.spica27.spicamusic.online.original.OriginalVersionAdjudicator
import me.spica27.spicamusic.online.original.OriginalityVerdict
import me.spica27.spicamusic.online.original.VersionCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalVersionAdjudicatorTest {

  private fun candidate(
    source: String,
    title: String,
    artist: String,
    year: Int?,
    durationMs: Long = 200_000,
  ) = VersionCandidate(
    sourceKey = source,
    title = title,
    artist = artist,
    album = null,
    releaseYear = year,
    durationMs = durationMs,
  )

  @Test
  fun `标题带翻唱关键字直接标记疑似翻唱`() {
    val result = OriginalVersionAdjudicator.flagByKeyword("晴天（Cover 周杰伦）")
    assertEquals(OriginalityVerdict.SuspectedCover, result.verdict)
    assertTrue(result.reason.isNotBlank())
  }

  @Test
  fun `各来源登记一致且年份最早时判定为原唱`() {
    val current = candidate("lx:kw", "晴天", "周杰伦", 2003)
    val candidates = listOf(
      current,
      candidate("lx:tx", "晴天", "周杰伦", 2003),
      candidate("script:wy", "晴天", "周杰伦", 2003),
    )
    val result = OriginalVersionAdjudicator.adjudicate(current, candidates)
    assertEquals(OriginalityVerdict.Original, result.verdict)
    assertEquals(2003, result.earliestYear)
  }

  @Test
  fun `当前版本晚于其他来源且艺人不同则标记疑似翻唱并给出原唱来源`() {
    val current = candidate("lx:kg", "晴天", "某翻唱歌手", 2019)
    val candidates = listOf(
      candidate("lx:tx", "晴天", "周杰伦", 2003),
      candidate("script:wy", "晴天", "周杰伦", 2003),
    )
    val result = OriginalVersionAdjudicator.adjudicate(current, candidates)
    assertEquals(OriginalityVerdict.SuspectedCover, result.verdict)
    assertEquals("lx:tx", result.originalSourceKey)
  }

  @Test
  fun `时长差异过大不参与比对`() {
    val current = candidate("lx:kw", "夜曲", "周杰伦", 2005, durationMs = 200_000)
    val candidates = listOf(candidate("lx:tx", "夜曲", "周杰伦", 2005, durationMs = 400_000))
    val result = OriginalVersionAdjudicator.adjudicate(current, candidates)
    assertEquals(OriginalityVerdict.Unknown, result.verdict)
  }

  @Test
  fun `没有任何年份信息时保持未知`() {
    val current = candidate("lx:kw", "无名曲", "某人", null)
    val result = OriginalVersionAdjudicator.adjudicate(current, listOf(current))
    assertEquals(OriginalityVerdict.Unknown, result.verdict)
  }
}