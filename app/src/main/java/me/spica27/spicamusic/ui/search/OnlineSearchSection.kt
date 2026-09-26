package me.spica27.spicamusic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.spica27.spicamusic.online.OnlineTrack
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.AudioCover
import me.spica27.spicamusic.ui.widget.combinedClickHighlight

/**
 * 在线搜索的界面状态。来源之间平级，任一来源返回即出现，不等全部完成。
 */
data class OnlineSearchUiState(
  val query: String = "",
  val loading: Boolean = false,
  val groups: List<OnlineSourceGroup> = emptyList(),
  val failedSources: List<String> = emptyList(),
  val finished: Boolean = false,
) {
  val hasResults: Boolean get() = groups.any { it.tracks.isNotEmpty() }
}

data class OnlineSourceGroup(
  val platformName: String,
  val tracks: List<OnlineTrack>,
)

/**
 * 唤起在线搜索的独立行：与搜索列表用同一套圆角卡片、间距与高亮反馈。
 */
@Composable
fun OnlineSearchMoreRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.Small)
        .clip(Shapes.ExtraLargeCornerBasedShape)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .combinedClickHighlight(onLongClick = onClick, onClick = onClick)
        .padding(Spacing.Small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
  ) {
    Box(
      modifier =
        Modifier
          .size(56.dp)
          .clip(Shapes.LargeCornerBasedShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(22.dp),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "使用在线搜索搜索更多",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "从内置音源里继续找这首歌",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * 单条在线结果：与搜索列表同一套圆角卡片 + 封面，下载进度以行内底色呈现。
 */
@Composable
fun OnlineTrackRow(
  track: OnlineTrack,
  progress: Float?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  failed: Boolean = false,
) {
  val downloading = progress != null
  val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.Small)
        .clip(Shapes.ExtraLargeCornerBasedShape)
        .background(
          if (downloading) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
          } else {
            MaterialTheme.colorScheme.surfaceContainerLow
          },
        ).drawBehind {
          // 行内底色进度：从左侧开始慢慢铺满。
          val fraction = progress?.coerceIn(0f, 1f) ?: return@drawBehind
          drawRect(color = fillColor, size = Size(size.width * fraction, size.height))
        }
        .combinedClickHighlight(onLongClick = onClick, onClick = onClick)
        .padding(Spacing.Small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
  ) {
    AudioCover(
      uri = track.coverUrl?.takeIf { it.isNotBlank() }?.toUri(),
      modifier = Modifier.size(56.dp).clip(Shapes.LargeCornerBasedShape),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = track.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = buildString {
          append(track.artist.ifBlank { "未知艺术家" })
          track.album?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
          if (downloading) append(" · 下载中 ").append(((progress ?: 0f) * 100).toInt()).append("%")
          if (failed) append(" · 下载失败，点一下重试")
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = track.platformName,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * 在线结果区：按来源平级分组。既可放在“本地无结果”的位置，
 * 也可接在本地结果之后，作为“搜索更多”的落点。
 */
@Composable
fun OnlineResultsSection(
  state: OnlineSearchUiState,
  progressOf: (OnlineTrack) -> Float?,
  onTrackClick: (OnlineTrack) -> Unit,
  modifier: Modifier = Modifier,
  failedOf: (OnlineTrack) -> Boolean = { false },
) {
  val showLoading = state.loading && !state.hasResults
  if (!showLoading && !state.hasResults && state.failedSources.isEmpty()) return

  LazyColumn(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
  ) {
    if (showLoading) {
      item(key = "online_loading") {
        Box(
          modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Large),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
      }
    }

    state.groups.forEach { group ->
      item(key = "online_header_${group.platformName}") {
        SearchGroupHeader(
          title = group.platformName,
          modifier = Modifier.padding(horizontal = Spacing.Small),
        )
      }
      items(items = group.tracks, key = { "${it.sourceKey}:${it.id}" }) { track ->
        OnlineTrackRow(
          track = track,
          progress = progressOf(track),
          failed = failedOf(track),
          onClick = { onTrackClick(track) },
        )
      }
    }

    if (state.failedSources.isNotEmpty()) {
      item(key = "online_failed") {
        Text(
          text = "这些来源没有返回结果：" + state.failedSources.joinToString("、"),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        )
      }
    }
  }
}
