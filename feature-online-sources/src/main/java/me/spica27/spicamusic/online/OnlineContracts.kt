package me.spica27.spicamusic.online

/**
 * 在线音源的实现来源。同一个平台可能有两套实现（落雪实现与 FuoEvolve 实现），
 * 上层只依赖 [OnlineSourcePort]，通过这里的标识区分与切换。
 */
object OnlineImplementations {
  /** 落雪音乐的内置音源实现（打包为 JS，在 QuickJS 中运行）。 */
  const val LX = "lx"

  /** FuoEvolve 的 Kotlin 音源实现。 */
  const val FUOEVOLVE = "fuo"

  /** 用户导入的洛雪自定义源脚本。 */
  const val SCRIPT = "script"
}

/** 平台标识，与落雪一致：kw/kg/tx/wy/mg/bd/xm，另加 bilibili 与 ytmusic。 */
object OnlinePlatforms {
  const val KW = "kw"
  const val KG = "kg"
  const val TX = "tx"
  const val WY = "wy"
  const val MG = "mg"
  const val BD = "bd"
  const val XM = "xm"
  const val BILIBILI = "bilibili"
  const val YTMUSIC = "ytmusic"

  // 与洛雪移动版对齐：百度接口已停用（上游注释）、虾米不在 SDK 内。
  val all: List<String> = listOf(KW, KG, TX, WY, MG, BILIBILI, YTMUSIC)

  fun displayName(platform: String): String = when (platform) {
    KW -> "酷我音乐"
    KG -> "酷狗音乐"
    TX -> "QQ 音乐"
    WY -> "网易云音乐"
    MG -> "咪咕音乐"
    BD -> "百度音乐"
    XM -> "虾米音乐"
    BILIBILI -> "哔哩哔哩"
    YTMUSIC -> "YouTube Music"
    else -> platform
  }
}

/** 音质档位，取值与落雪一致，默认取 320k。 */
enum class OnlineQuality(val key: String, val label: String, val rank: Int) {
  Q128("128k", "128k", 1),
  Q320("320k", "320k", 2),
  FLAC("flac", "无损", 3),
  FLAC24("flac24bit", "Hi-Res", 4),
  ;

  /** 解析失败时的降档顺序：从当前档位向下依次重试。 */
  fun fallbackLadder(): List<OnlineQuality> = entries.filter { it.rank <= rank }.sortedByDescending { it.rank }

  companion object {
    val DEFAULT = Q320

    fun fromKey(key: String?): OnlineQuality = entries.firstOrNull { it.key == key } ?: DEFAULT
  }
}

/** 一条在线搜索结果。在线结果不入库，只在下载完成后才进入本地曲库。 */
data class OnlineTrack(
  val platform: String,
  val implementation: String,
  val id: String,
  val title: String,
  val artist: String,
  val album: String? = null,
  val durationMs: Long = 0L,
  val coverUrl: String? = null,
  val qualityKeys: List<String> = emptyList(),
  val extra: Map<String, String> = emptyMap(),
) {
  val sourceKey: String get() = "$implementation:$platform"

  val platformName: String get() = OnlinePlatforms.displayName(platform)
}

/** 曲目详情，用于原唱判定。 */
data class OnlineTrackDetail(
  val track: OnlineTrack,
  val releaseYear: Int?,
  val album: String?,
  val durationMs: Long,
)

/** 搜索事件：任一来源返回即推送一条，不等待全部来源完成。 */
sealed interface OnlineSearchEvent {
  data class SourceResult(
    val platform: String,
    val implementation: String,
    val platformName: String,
    val tracks: List<OnlineTrack>,
  ) : OnlineSearchEvent

  data class SourceFailed(
    val platform: String,
    val implementation: String,
    val platformName: String,
    val message: String,
  ) : OnlineSearchEvent

  data object Finished : OnlineSearchEvent
}

/** 在线音源端口：搜索、详情、直链、歌词、封面。 */
interface OnlineSourcePort {
  /** 每启用一个来源即产出一条事件，任一来源先返回就先推送。 */
  fun search(query: String, limit: Int = 25): kotlinx.coroutines.flow.Flow<OnlineSearchEvent>

  suspend fun detail(track: OnlineTrack): OnlineTrackDetail?

  suspend fun resolveUrl(track: OnlineTrack, quality: OnlineQuality): String?

  suspend fun lyric(track: OnlineTrack): String?

  suspend fun cover(track: OnlineTrack): String?
}

/** 下载过程中的一个来源解析失败。 */
class OnlineSourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
