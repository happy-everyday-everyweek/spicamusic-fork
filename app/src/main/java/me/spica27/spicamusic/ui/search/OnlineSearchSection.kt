package me.spica27.spicamusic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.online.OnlineTrack

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

/** 搜索结果里用于唤起在线搜索的独立行。 */
@Composable
fun OnlineSearchMoreRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "使用在线搜索搜索更多",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Medium,
    )
  }
}

/** 单条在线结果：来源标签 + 歌名歌手 + 行内底色进度。 */
@Composable
fun OnlineTrackRow(
  track: OnlineTrack,
  progress: Float?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
  ) {
    if (progress != null) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth(progress.coerceIn(0f, 1f))
            .height(56.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
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
            if (progress != null) append(" · 下载中 ").append((progress * 100).toInt()).append("%")
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = track.platformName,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
      )
    }
  }
}

/**
 * 在线结果区：按来源平级分组展示。既可放在“本地无结果”的位置，
 * 也可接在本地结果之后，作为“搜索更多”的落点。
 */
@Composable
fun OnlineResultsSection(
  state: OnlineSearchUiState,
  progressOf: (OnlineTrack) -> Float?,
  onTrackClick: (OnlineTrack) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!state.loading && !state.hasResults && state.failedSources.isEmpty()) return

  LazyColumn(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (state.loading) {
      item(key = "online_loading") {
        Text(
          text = "正在搜索在线音源…",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }

    state.groups.forEach { group ->
      item(key = "online_header_${group.platformName}") {
        Text(
          text = group.platformName,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
      items(items = group.tracks, key = { "${it.sourceKey}:${it.id}" }) { track ->
        OnlineTrackRow(
          track = track,
          progress = progressOf(track),
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
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
  }
}