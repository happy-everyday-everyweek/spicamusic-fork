package me.spica27.spicamusic.ui.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.feature.library.domain.SongUseCases
import me.spica27.spicamusic.online.OnlineSearchEvent
import me.spica27.spicamusic.online.OnlineSourcePort
import me.spica27.spicamusic.online.OnlineTrack
import me.spica27.spicamusic.online.download.DownloadState
import me.spica27.spicamusic.online.download.OnlineDownloader

/**
 * SearchPage 列表项的密封类：分组头 or 歌曲
 */
sealed class SearchListItem {
    data class Header(
        val title: String,
    ) : SearchListItem()

    data class SongItem(
        val song: Song,
    ) : SearchListItem()
}

/**
 * 搜索页面 ViewModel
 * 空关键词不加载数据（WelcomeHolder），有关键词时使用 Paging 3 + InsertSeparators 实现分组
 */
@Stable
class SearchViewModel(
    private val songRepository: SongUseCases,
    private val onlineSource: OnlineSourcePort? = null,
    private val onlineDownloader: OnlineDownloader? = null,
) : ViewModel() {
    // 搜索关键词
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    /**
     * 分页搜索结果（带分组头）
     * 空关键词时返回空的 PagingData，不做任何查询
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchPagingResults: Flow<PagingData<SearchListItem>> =
        _searchKeyword
            .debounce(300)
            .flatMapLatest { keyword ->
                if (keyword.isBlank()) {
                    flowOf(
                        PagingData.empty(
                            sourceLoadStates =
                                LoadStates(
                                    refresh = LoadState.Loading,
                                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                                    append = LoadState.NotLoading(endOfPaginationReached = true),
                                ),
                        ),
                    )
                } else {
                    songRepository
                        .getSongsBySortNamePagingFlow(keyword)
                        .map { pagingData ->
                            pagingData
                                .map<Song, SearchListItem> { song -> SearchListItem.SongItem(song) }
                                .insertSeparators { before, after ->
                                    val beforeSort = (before as? SearchListItem.SongItem)?.song?.sortName
                                    val afterSort = (after as? SearchListItem.SongItem)?.song?.sortName
                                    if (afterSort != null && beforeSort != afterSort) {
                                        SearchListItem.Header(afterSort)
                                    } else {
                                        null
                                    }
                                }
                        }
                }
            }.cachedIn(viewModelScope)

    /**
     * 更新搜索关键词
     */
    fun updateSearchKeyword(keyword: String) {
        _searchKeyword.value = keyword
    }

    /**
     * 清空搜索关键词
     */
    fun clearSearch() {
        _searchKeyword.value = ""
    }

    // ---- 在线搜索：与本地结果混排，本地无结果时自动发起 ----

    private val _onlineState = MutableStateFlow(OnlineSearchUiState())
    val onlineState: StateFlow<OnlineSearchUiState> = _onlineState.asStateFlow()

    private var onlineJob: Job? = null

    init {
        // 本地搜不到时自动走在线搜索；与本地有结果时的手动入口共用同一实现。
        viewModelScope.launch {
            _searchKeyword
                .debounce(500)
                .collectLatest { keyword ->
                    if (keyword.isBlank()) return@collectLatest
                    if (onlineSource == null) return@collectLatest
                    val localCount = runCatching { songRepository.getFilteredMediaStoreIds(keyword).size }.getOrDefault(0)
                    if (localCount == 0) searchOnlineMore(keyword)
                }
        }
    }

    /** 手动“使用在线搜索搜索更多”，也可以在本地无结果时自动调用。 */
    fun searchOnlineMore(keyword: String = _searchKeyword.value) {
        val source = onlineSource ?: return
        if (keyword.isBlank()) return
        onlineJob?.cancel()
        _onlineState.value = OnlineSearchUiState(query = keyword, loading = true)
        onlineJob =
            viewModelScope.launch {
                source.search(keyword, ONLINE_SEARCH_LIMIT).collect { event ->
                    when (event) {
                        is OnlineSearchEvent.SourceResult -> _onlineState.update { state ->
                            val groups =
                                state.groups
                                    .filterNot { it.platformName == event.platformName }
                                    .plus(OnlineSourceGroup(event.platformName, event.tracks))
                            state.copy(groups = groups, loading = false)
                        }

                        is OnlineSearchEvent.SourceFailed -> _onlineState.update { state ->
                            if (state.failedSources.contains(event.platformName)) state
                            else state.copy(failedSources = state.failedSources + event.platformName)
                        }

                        OnlineSearchEvent.Finished -> _onlineState.update { it.copy(loading = false, finished = true) }
                    }
                }
            }
    }

    fun clearOnline() {
        onlineJob?.cancel()
        _onlineState.value = OnlineSearchUiState()
    }

    /**
     * 点击在线歌曲：先完整下载到下载目录，再注册进媒体库。
     * 下载过程中的进度以行内底色呈现，完成后的文件会由媒体库同步进入曲库。
     */
    fun downloadOnline(track: OnlineTrack) {
        val downloader = onlineDownloader ?: return
        viewModelScope.launch { downloader.download(track) }
    }

    /** 某条在线结果的下载进度，未在下载则返回 null（用于行内底色）。 */
    fun onlineProgress(track: OnlineTrack): Float? {
        val state = onlineDownloader?.states?.value?.get("${track.sourceKey}:${track.id}") ?: return null
        return when (state) {
            is DownloadState.Running -> state.progress
            DownloadState.Idle, is DownloadState.Done, is DownloadState.Failed -> null
        }
    }

    private companion object {
        const val ONLINE_SEARCH_LIMIT = 25
    }
}
