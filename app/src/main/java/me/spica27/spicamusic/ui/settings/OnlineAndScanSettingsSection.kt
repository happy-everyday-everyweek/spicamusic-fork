package me.spica27.spicamusic.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.spica27.spicamusic.feature.library.domain.scope.ScanMode
import me.spica27.spicamusic.online.OnlineQuality
import me.spica27.spicamusic.online.settings.FileNameStyle
import me.spica27.spicamusic.online.settings.OnlineSettings
import me.spica27.spicamusic.online.settings.ScanScopeStore
import me.spica27.spicamusic.ui.migration.LocalMigrationExecutor
import java.io.File

/** 在线下载与扫描范围设置。两张卡片都自包含，直接读写偏好设置。 */
@Composable
fun OnlineDownloadSettingsCard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val settings = remember { OnlineSettings(context) }

  var quality by remember { mutableStateOf(settings.quality()) }
  var concurrency by remember { mutableStateOf(settings.concurrency()) }
  var nameStyle by remember { mutableStateOf(settings.fileNameStyle()) }
  var wifiOnly by remember { mutableStateOf(settings.wifiOnly()) }

  Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("在线下载", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        "默认 320k，解析失败会自动降档重试；下载完成的文件会自动进入曲库。",
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
      Text("同时下载数量", style = MaterialTheme.typography.labelLarge)
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
 * 扫描范围：可以只认指定目录。白名单为空时不收录任何文件；
 * 迁移会把媒体库里已扫描到、且不在目标目录下的音频复制（或移动）过去。
 */
@Composable
fun ScanScopeSettingsCard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val store = remember { ScanScopeStore(context) }
  val executor = remember { LocalMigrationExecutor(context) }
  val scope = rememberCoroutineScope()

  var paths by remember { mutableStateOf(store.whitelistPaths()) }
  var whitelistEnabled by remember { mutableStateOf(store.mode() == ScanMode.OnlySelectedDirectories) }
  var status by remember { mutableStateOf(store.migrationStatus()) }
  var busy by remember { mutableStateOf(false) }

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        val realPath = resolveRealPath(context, uri)
        if (realPath != null) {
          store.addWhitelistPath(realPath)
          paths = store.whitelistPaths()
          whitelistEnabled = true
          status = "已添加目录：$realPath"
        } else {
          status = "这个目录无法解析为本地路径，请换一个目录（例如内部存储下的 Music）"
        }
        store.setMigrationStatus(status)
      }
    }

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
            if (paths.isEmpty()) "尚未添加目录，开启后曲库将为空" else "已添加 ${paths.size} 个目录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = whitelistEnabled,
          onCheckedChange = {
            whitelistEnabled = it
            store.setMode(if (it) ScanMode.OnlySelectedDirectories else ScanMode.AllDirectories)
          },
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { picker.launch(null) }) { Text("添加目录") }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
          enabled = !busy && paths.isNotEmpty(),
          onClick = {
            val target = paths.firstOrNull() ?: return@TextButton
            busy = true
            status = "正在迁移…"
            scope.launch {
              val outcome = executor.migrate(File(target), moveInsteadOfCopy = false) { copied, total ->
                status = "正在迁移：$copied/$total"
              }
              status = "迁移完成：复制 ${outcome.copied} 首，跳过 ${outcome.skipped} 首，失败 ${outcome.failed} 首"
              store.setMigrationStatus(status)
              busy = false
            }
          },
        ) { Text("迁移到白名单目录") }
      }

      paths.forEach { path ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          TextButton(
            onClick = {
              store.removeWhitelistPath(path)
              paths = store.whitelistPaths()
            },
          ) { Text("移除") }
        }
      }

      status?.let {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = it,
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

/** 把 SAF 目录树 URI 解析成绝对路径，失败返回 null。 */
private fun resolveRealPath(context: Context, treeUri: Uri): String? = runCatching {
  val documentId = DocumentsContract.getTreeDocumentId(treeUri)
  val parts = documentId.split(":", limit = 2)
  if (parts.size < 2) return@runCatching null
  val volume = parts[0]
  val relative = parts[1]
  val base =
    if (volume.equals("primary", ignoreCase = true)) {
      Environment.getExternalStorageDirectory().absolutePath
    } else {
      "/storage/$volume"
    }
  if (relative.isBlank()) base else "$base/$relative"
}.getOrNull()