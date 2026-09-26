package me.spica27.spicamusic.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import me.spica27.spicamusic.R
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing

/** 应用自身的许可证：GNU GPL v3.0。全文放在 assets/licenses/gpl-3.0.txt，避免把大段文本写进源码。 */
private const val LICENSE_ASSET = "licenses/gpl-3.0.txt"

private val LICENSE_FALLBACK =
  """
  本应用以 GNU General Public License v3.0 发布。
  许可证全文见应用资源 licenses/gpl-3.0.txt 与仓库根目录 LICENSE。
  """.trimIndent()

@Composable
fun AppLicenseScreen() {
  val context = LocalContext.current
  val licenseText =
    remember(context) {
      runCatching {
        context.assets.open(LICENSE_ASSET).bufferedReader().use { it.readText() }
      }.getOrDefault(LICENSE_FALLBACK)
    }
  AboutScaffold(title = stringResource(R.string.app_license_title)) {
    item {
      Text(
        text = licenseText,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(Shapes.ExtraLargeCornerBasedShape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f))
            .padding(Spacing.Large),
      )
    }
  }
}
