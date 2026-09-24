package me.spica27.spicamusic.online.lx

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import me.spica27.spicamusic.online.OnlineImplementations
import me.spica27.spicamusic.online.OnlinePlatforms
import me.spica27.spicamusic.online.OnlineQuality
import me.spica27.spicamusic.online.OnlineSearchEvent
import me.spica27.spicamusic.online.OnlineSourcePort
import me.spica27.spicamusic.online.OnlineTrack
import me.spica27.spicamusic.online.OnlineTrackDetail
import org.json.JSONObject

/**
 * 基于洛雪内置音源与自定义源脚本的在线音源实现。
 *
 * 搜索与详情由打包好的内置音源提供，取直链、歌词与封面按洛雪的设计交给音源接口
 * （也就是用户导入的自定义源脚本）。任一来源返回即推送一条事件，不等全部完成。
 */
class LxOnlineSource(
  private val host: LxHost,
  private val enabledPlatformsProvider: () -> List<String> = { OnlinePlatforms.all },
) : OnlineSourcePort {

  override fun search(query: String, limit: Int): Flow<OnlineSearchEvent> = channelFlow {
    val platforms = enabledPlatformsProvider()
    coroutineScope {
      platforms.forEach { platform ->
        launch {
          val payload = runCatching { host.searchPlatform(platform, query, limit) }.getOrNull()
          if (payload == null) {
            send(
              OnlineSearchEvent.SourceFailed(
                platform = platform,
                implementation = OnlineImplementations.LX,
                platformName = OnlinePlatforms.displayName(platform),
                message = "该来源没有返回结果",
              ),
            )
            return@launch
          }
          val tracks = parseTracks(platform, payload)
          send(
            OnlineSearchEvent.SourceResult(
              platform = platform,
              implementation = OnlineImplementations.LX,
              platformName = OnlinePlatforms.displayName(platform),
              tracks = tracks,
            ),
          )
        }
      }
    }
    send(OnlineSearchEvent.Finished)
    awaitClose { }
  }

  override suspend fun detail(track: OnlineTrack): OnlineTrackDetail? {
    val payload = runCatching { host.detail(track.platform, songInfoJson(track)) }.getOrNull() ?: return null
    val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    val year = listOf("year", "publishTime", "publish_time", "releaseDate", "release_year", "time")
      .firstNotNullOfOrNull { key ->
        val raw = json.optString(key)
        raw.takeIf { it.length >= 4 }?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toIntOrNull() }
      }
    return OnlineTrackDetail(
      track = track,
      releaseYear = year,
      album = json.optString("album").takeIf { it.isNotBlank() } ?: track.album,
      durationMs = track.durationMs,
    )
  }

  override suspend fun resolveUrl(track: OnlineTrack, quality: OnlineQuality): String? {
    val songInfo = songInfoJson(track)
    for (candidate in quality.fallbackLadder()) {
      val url = runCatching { host.resolveUrl(track.platform, songInfo, candidate.key) }.getOrNull()
      if (!url.isNullOrBlank()) return url
    }
    return null
  }

  override suspend fun lyric(track: OnlineTrack): String? =
    runCatching { host.lyric(track.platform, songInfoJson(track)) }.getOrNull()

  override suspend fun cover(track: OnlineTrack): String? =
    runCatching { host.picture(track.platform, songInfoJson(track)) }.getOrNull() ?: track.coverUrl

  private fun songInfoJson(track: OnlineTrack): String = JSONObject().apply {
    put("source", track.platform)
    put("id", track.id)
    put("songmid", track.id)
    put("name", track.title)
    put("singer", track.artist)
    put("albumName", track.album.orEmpty())
    put("interval", formatInterval(track.durationMs))
    put("img", track.coverUrl.orEmpty())
  }.toString()

  private fun parseTracks(platform: String, payload: String): List<OnlineTrack> {
    val json = runCatching { JSONObject(payload) }.getOrNull() ?: return emptyList()
    val list = json.optJSONArray("list") ?: return emptyList()
    return buildList {
      for (index in 0 until list.length()) {
        val item = list.optJSONObject(index) ?: continue
        val id = item.optString("songmid").ifBlank { item.optString("id") }
        if (id.isBlank()) continue
        add(
          OnlineTrack(
            platform = platform,
            implementation = OnlineImplementations.LX,
            id = id,
            title = item.optString("name"),
            artist = item.optString("singer"),
            album = item.optString("albumName").takeIf { it.isNotBlank() },
            durationMs = parseInterval(item.optString("interval")),
            coverUrl = item.optString("img").takeIf { it.isNotBlank() },
            qualityKeys = item.optJSONArray("types")?.let { types ->
              buildList { for (i in 0 until types.length()) add(types.optJSONObject(i)?.optString("type").orEmpty()) }
            }.orEmpty(),
          ),
        )
      }
    }
  }

  private fun parseInterval(interval: String): Long {
    if (interval.isBlank()) return 0L
    val parts = interval.split(":")
    var seconds = 0L
    parts.forEach { seconds = seconds * 60 + (it.toLongOrNull() ?: 0L) }
    return seconds * 1000
  }

  private fun formatInterval(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
  }
}