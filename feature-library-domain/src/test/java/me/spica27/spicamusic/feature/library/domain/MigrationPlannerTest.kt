package me.spica27.spicamusic.feature.library.domain

import me.spica27.spicamusic.feature.library.domain.migration.MigratableSong
import me.spica27.spicamusic.feature.library.domain.migration.MigrationPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPlannerTest {

  private fun song(
    id: Long,
    title: String,
    artist: String = "歌手",
    durationMs: Long = 200_000,
    size: Long = 5_000_000,
    path: String = "/storage/emulated/0/Music/$title.mp3",
  ) = MigratableSong(id = id, path = path, title = title, artist = artist, durationMs = durationMs, sizeBytes = size)

  private val namer: (MigratableSong) -> String = { "${it.title} - ${it.artist}.mp3" }

  @Test
  fun `目标目录已有同内容文件时跳过`() {
    val target = song(1, "晴天")
    val plan = MigrationPlanner.plan(
      songs = listOf(target),
      existingFingerprints = setOf(MigrationPlanner.fingerprint(target)),
      nameOf = namer,
    )
    assertEquals(0, plan.copyCount)
    assertEquals(1, plan.skippedCount)
  }

  @Test
  fun `曲库内重复条目只复制一次并记录指向`() {
    val first = song(1, "晴天")
    val second = song(2, "晴天")
    val plan = MigrationPlanner.plan(songs = listOf(first, second), nameOf = namer)
    assertEquals(1, plan.copyCount)
    assertEquals(1, plan.skippedCount)
    assertEquals(1L, plan.duplicateOf[2L])
  }

  @Test
  fun `同名不同曲会追加序号避免互相覆盖`() {
    val a = song(1, "夜曲", artist = "周杰伦", size = 1)
    val b = song(2, "夜曲", artist = "其他", size = 2)
    val plan = MigrationPlanner.plan(songs = listOf(a, b), nameOf = { "${it.title}.mp3" })
    assertEquals(2, plan.copyCount)
    val names = plan.toCopy.map { it.fileName }
    assertEquals("夜曲.mp3", names[0])
    assertEquals("夜曲 (2).mp3", names[1])
  }

  @Test
  fun `文件名里的非法字符会被替换`() {
    assertEquals("a_b_c.mp3", MigrationPlanner.sanitizeFileName("a/b:c.mp3"))
    assertTrue(MigrationPlanner.sanitizeFileName("   ").isNotBlank())
  }

  @Test
  fun `总字节数是待复制文件之和`() {
    val plan = MigrationPlanner.plan(
      songs = listOf(song(1, "A", size = 100), song(2, "B", size = 200)),
      nameOf = namer,
    )
    assertEquals(300L, plan.totalBytes)
  }
}