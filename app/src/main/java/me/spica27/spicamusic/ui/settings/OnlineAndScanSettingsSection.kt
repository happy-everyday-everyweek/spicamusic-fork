package me.spica27.spicamusic.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.online.OnlineQuality
import me.spica27.spicamusic.online.settings.FileNameStyle
import me.spica27.spicamusic.online.settings.OnlineSettings

/**
 * 在线音源与下载设置。自包含实现，直接读写偏好设置，不依赖设置页既有 ViewModel。
 */
@Composable
fun OnlineDownloadSettingsCard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val settings = remember { OnlineSettings(context) }

  var quality by remember { mutableStateOf(settings.quality()) }
  var concurrency by remember { mutableStateOf(settings.concurrency()) }
  var nameStyle by remember { mutableStateOf(settings.fileNameStyle()) }
  var wifiOnly by remember { mutableStateOf(settings.wifiOnly()) }
  var implementations by remember { mutableStateOf(settings.implementationFor("wy")) }

  Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("在线下载", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        "默认音质 320k，解析失败会自动降档重试；下载完成的文件会自动进入曲库。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(12.dp))
      Text("音质", style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OnlineQuality.entries.forEach { option ->
          FilterChip(
            selected = quality == option,
            onClick = {
              quality = option
              settings.setQuality(option)
            },
            label = { Text(option.label) },
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      Text("同时下载数量：$concurrency", style = MaterialTheme.typography.labelLarge)
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
          onClick = {
            concurrency = (concurrency - 1).coerceAtLeast(1)
            settings.setConcurrency(concurrency)
          },
        ) { Text("-") }
        Text("$concurrency", style = MaterialTheme.typography.bodyLarge)
        TextButton(
          onClick = {
            concurrency = (concurrency + 1).coerceAtMost(8)
            settings.setConcurrency(concurrency)
          },
        ) { Text("+") }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text("文件名", style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FileNameStyle.entries.forEach { option ->
          FilterChip(
            selected = nameStyle == option,
            onClick = {
              nameStyle = option
              settings.setFileNameStyle(option)
            },
            label = { Text(option.label) },
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text("仅 Wi-Fi 下载", style = MaterialTheme.typography.bodyLarge)
          Text(
            "开启后移动网络下不会自动下载",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = wifiOnly,
          onCheckedChange = {
            wifiOnly = it
            settings.setWifiOnly(it)
          },
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text(
        "下载目录：${settings.downloadDirectory().absolutePath}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * 扫描范围设置：可以只认指定目录（白名单）。白名单为空时不收录任何文件。
 */
@Composable
fun ScanScopeSettingsCard(
  whitelistPaths: List<String>,
  onAddDirectory: (String) -> Unit,
  onRemoveDirectory: (String) -> Unit,
  onMigrate: (Boolean) -> Unit,
  migrationStatus: String?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) onAddDirectory(uri.toString())
    }

  var whitelistEnabled by remember { mutableStateOf(whitelistPaths.isNotEmpty()) }

  Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("扫描范围", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        "开启后只收录白名单目录下的音频；系统媒体库的增量同步同样遵守这个范围。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text("只扫描指定目录", style = MaterialTheme.typography.bodyLarge)
          Text(
            if (whitelistPaths.isEmpty()) "尚未添加目录，开启后曲库将为空" else "已添加 ${whitelistPaths.size} 个目录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(checked = whitelistEnabled, onCheckedChange = { whitelistEnabled = it })
      }

      Spacer(modifier = Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { picker.launch(null) }) { Text("添加目录") }
        Spacer(modifier = Modifier.width(4.dp))
        TextButton(onClick = { onMigrate(false) }) { Text("迁移到此目录（复制）") }
        TextButton(onClick = { onMigrate(true) }) { Text("迁移并删除原文件") }
      }

      whitelistPaths.forEach { path ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = { onRemoveDirectory(path) }) { Text("移除") }
        }
      }

      migrationStatus?.let { status ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = status,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Spacer(modifier = Modifier.height(6.dp))
      Text(
        "提示：白名单目录与下载目录是两个独立设置，可以相同也可以分开。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}