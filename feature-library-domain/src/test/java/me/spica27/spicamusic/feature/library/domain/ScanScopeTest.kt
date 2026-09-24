package me.spica27.spicamusic.feature.library.domain

import me.spica27.spicamusic.feature.library.domain.scope.ScanMode
import me.spica27.spicamusic.feature.library.domain.scope.ScanScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanScopeTest {

  @Test
  fun `全部目录模式下任何路径都收录`() {
    val scope = ScanScope(mode = ScanMode.AllDirectories)
    assertTrue(scope.includes("/storage/emulated/0/Music/a.mp3"))
    assertTrue(scope.includes("/storage/emulated/0/DCIM/rec.mp3"))
    assertTrue(scope.includes(null))
  }

  @Test
  fun `白名单模式下只收录白名单目录之下的文件`() {
    val scope = ScanScope(
      mode = ScanMode.OnlySelectedDirectories,
      whitelistPrefixes = listOf("/storage/emulated/0/Music"),
    )
    assertTrue(scope.includes("/storage/emulated/0/Music/a.mp3"))
    assertTrue(scope.includes("/storage/emulated/0/Music/2024/b.flac"))
    assertFalse(scope.includes("/storage/emulated/0/Download/c.mp3"))
    assertFalse(scope.includes("/storage/emulated/0/Musication/d.mp3"))
  }

  @Test
  fun `白名单目录本身与尾斜杠写法都能匹配`() {
    val scope = ScanScope(
      mode = ScanMode.OnlySelectedDirectories,
      whitelistPrefixes = listOf("/storage/emulated/0/Music/", " Music2 "),
    )
    assertTrue(scope.includes("/storage/emulated/0/Music"))
    assertTrue(scope.includes("/storage/emulated/0/Music/song.mp3"))
    assertTrue(scope.includes("/Music2/song.mp3"))
  }

  @Test
  fun `白名单为空时不收录任何文件`() {
    val scope = ScanScope(mode = ScanMode.OnlySelectedDirectories, whitelistPrefixes = emptyList())
    assertFalse(scope.includes("/storage/emulated/0/Music/a.mp3"))
  }
}