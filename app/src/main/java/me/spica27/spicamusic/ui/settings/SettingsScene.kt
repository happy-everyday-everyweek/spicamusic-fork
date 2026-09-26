package me.spica27.spicamusic.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LensBlur
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.collect.ImmutableList
import me.spica27.spicamusic.R
import me.spica27.spicamusic.common.entity.DynamicCoverType
import me.spica27.spicamusic.common.entity.DynamicSpectrumBackground
import me.spica27.spicamusic.common.entity.ProgressBarStyle
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.navigation.AboutRoute
import me.spica27.spicamusic.ui.navigation.AudioEffectsRoute
import me.spica27.spicamusic.ui.navigation.LocalBackStack
import me.spica27.spicamusic.ui.navigation.SleepTimerRoute
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.player.formatSleepTimerRemaining
import me.spica27.spicamusic.ui.theme.EaseOutEmphasized
import me.spica27.spicamusic.ui.theme.LayoutTokens
import me.spica27.spicamusic.ui.theme.ScaleEnterFrom
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.theme.entrance
import me.spica27.spicamusic.ui.widget.clickHighlight
import me.spica27.spicamusic.ui.widget.rememberIOSOverScrollEffect
import org.koin.compose.viewmodel.koinViewModel

/**
 * 设置页
 */
@Composable
fun SettingsScreen() {
    val backStack = LocalBackStack.current
    val viewModel: SettingsViewModel = koinViewModel()

    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val liquidGlassEnabled by viewModel.liquidGlassEnabled.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val backgroundValue by viewModel.dynamicSpectrumBackground.collectAsStateWithLifecycle()
    val coverTapValue by viewModel.dynamicCoverType.collectAsStateWithLifecycle()
    val progressWaveformValue by viewModel.progressBarStyle.collectAsStateWithLifecycle()
    val colorStyleValue by viewModel.themeColorStyle.collectAsStateWithLifecycle()
    val colorSourceValue by viewModel.colorSource.collectAsStateWithLifecycle()
    val playerViewModel = LocalPlayerViewModel.current
    val sleepTimer by playerViewModel.sleepTimer.collectAsStateWithLifecycle()

    // 只在页面首次呈现时播放一次入场
    var entrancePlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entrancePlayed = true
    }

    // 一次只展开一个选项组：避免整个页面同时膨胀成一大片选项海
    var expandedRowKey by rememberSaveable { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val mastheadGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = statusBarTop + 56.dp,
                    bottom = 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
            overscrollEffect = rememberIOSOverScrollEffect(Orientation.Vertical),
        ) {
            item(key = "settings_masthead") {
                SettingsMasthead(
                    modifier =
                        Modifier
                            .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                            .padding(top = Spacing.Large)
                            .entrance(order = 0, play = !entrancePlayed)
                            .graphicsLayer {
                                val t = mastheadCollapse(listState)
                                transformOrigin = TransformOrigin(0f, 0f)
                                alpha = 1f - t
                                translationY = -t * 16.dp.toPx()
                                scaleX = 1f - 0.18f * t
                                scaleY = 1f - 0.18f * t
                            },
                )
            }

            item(key = "settings_appearance") {
                val colorStyleOptions = rememberColorStyleOptions()
                val colorSourceOptions = rememberColorSourceOptions()
                SettingsSectionCard(
                    title = stringResource(R.string.settings_appearance),
                    subtitle = stringResource(R.string.settings_appearance_subtitle),
                    modifier =
                        Modifier
                            .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                            .entrance(order = 1, play = !entrancePlayed),
                ) {
                    InlineSelectRow(
                        rowKey = "color_style",
                        title = stringResource(R.string.settings_color_style),
                        summary = stringResource(R.string.settings_color_style_subtitle),
                        icon = Icons.Default.Palette,
                        options = colorStyleOptions,
                        currentValue = colorStyleValue,
                        expandedKey = expandedRowKey,
                        onExpandChange = { expandedRowKey = it },
                        onValueChange = viewModel::setThemeColorStyle,
                    )
                    SettingsItemDivider()
                    InlineSelectRow(
                        rowKey = "color_source",
                        title = stringResource(R.string.settings_color_source),
                        summary = stringResource(R.string.settings_color_source_subtitle),
                        icon = Icons.Default.AutoAwesome,
                        options = colorSourceOptions,
                        currentValue = colorSourceValue,
                        expandedKey = expandedRowKey,
                        onExpandChange = { expandedRowKey = it },
                        onValueChange = viewModel::setColorSource,
                    )
                    SettingsItemDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_dark_mode_title),
                        summary = stringResource(R.string.settings_dark_mode_toggle_subtitle),
                        icon = Icons.Default.DarkMode,
                        checked = darkMode,
                        onCheckedChange = viewModel::setDarkMode,
                    )
                    SettingsItemDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_liquid_glass_title),
                        summary = stringResource(R.string.settings_liquid_glass_subtitle),
                        icon = Icons.Default.LensBlur,
                        checked = liquidGlassEnabled,
                        onCheckedChange = viewModel::setLiquidGlassEnabled,
                    )
                }
            }

            item(key = "settings_online_download") {
                OnlineDownloadSettingsSection(
                    modifier =
                        Modifier
                            .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                            .entrance(order = 6, play = !entrancePlayed),
                )
            }

            item(key = "settings_now_playing") {
                val backgroundOptions = rememberBackgroundOptions()
                val coverTapOptions = rememberCoverTapOptions()
                val progressWaveformOptions = rememberProgressWaveformOptions()
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_player_visual),
                    subtitle = stringResource(R.string.settings_section_player_visual_subtitle),
                    modifier =
                        Modifier
                            .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                            .entrance(order = 2, play = !entrancePlayed),
                ) {
                    InlineSelectRow(
                        rowKey = "player_background",
                        title = stringResource(R.string.settings_player_background),
                        summary = stringResource(R.string.settings_player_background_subtitle),
                        icon = Icons.Default.Landscape,
                        options = backgroundOptions,
                        currentValue = backgroundValue,
                        expandedKey = expandedRowKey,
                        onExpandChange = { expandedRowKey = it },
                        onValueChange = viewModel::setDynamicSpectrumBackground,
                    )
//                        SettingsItemDivider()
//                        InlineSelectRow(
//                            rowKey = "cover_tap",
//                            title = stringResource(R.string.settings_cover_tap_effect),
//                            summary = stringResource(R.string.settings_cover_tap_effect_subtitle),
//                            icon = Icons.Default.Album,
//                            options = coverTapOptions,
//                            currentValue = coverTapValue,
//                            expandedKey = expandedRowKey,
//                            onExpandChange = { expandedRowKey = it },
//                            onValueChange = viewModel::setDynamicCoverType,
//                        )
                    SettingsItemDivider()
                    InlineSelectRow(
                        rowKey = "progress_waveform",
                        title = stringResource(R.string.settings_progress_waveform),
                        summary = stringResource(R.string.settings_progress_waveform_subtitle),
                        icon = Icons.Default.GraphicEq,
                        options = progressWaveformOptions,
                        currentValue = progressWaveformValue,
                        expandedKey = expandedRowKey,
                        onExpandChange = { expandedRowKey = it },
                        onValueChange = viewModel::setProgressBarStyle,
                    )
                }
            }

            item(key = "settings_playback") {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_playback),
                    subtitle = stringResource(R.string.settings_section_playback_subtitle),
                    modifier =
                        Modifier
                            .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                            .entrance(order = 3, play = !entrancePlayed),
                ) {
                    NavigationRow(
                        title = stringResource(R.string.settings_sound_effects),
                        summary = stringResource(R.string.settings_sound_effects_subtitle),
                        icon = Icons.Default.Tune,
                        onClick = { backStack.add(AudioEffectsRoute) },
                    )
                    SettingsItemDivider()
                    NavigationRow(
                        title = stringResource(R.string.settings_sleep_timer),
                        summary =
                            sleepTimer?.let {
                                stringResource(
                                    R.string.settings_sleep_timer_active,
                                    formatSleepTimerRemaining(it.remainingMs),
                                )
                            } ?: stringResource(R.string.settings_sleep_timer_subtitle),
                        icon = Icons.Default.Bedtime,
                        onClick = { backStack.add(SleepTimerRoute) },
                    )
                    SettingsItemDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_keep_screen_on),
                        summary = stringResource(R.string.settings_keep_screen_on_subtitle),
                        icon = Icons.Default.Visibility,
                        checked = keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn,
                    )
                }
            }

            item(key = "settings_about") {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_about),
                    subtitle = null,
                    modifier =
                        Modifier
                            .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                            .entrance(order = 4, play = !entrancePlayed),
                ) {
                    NavigationRow(
                        title = stringResource(R.string.settings_about),
                        summary = stringResource(R.string.settings_about_subtitle),
                        icon = Icons.Default.Info,
                        onClick = { backStack.add(AboutRoute) },
                    )
                }
            }
        }

        SettingsTopBar(
            title = stringResource(R.string.finder_settings_title),
            listState = listState,
            solid = mastheadGone,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

// -- 页面结构 --

/** 大标题刊头 */
@Composable
private fun SettingsMasthead(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.finder_settings_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_masthead_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 固定顶栏：背景透明度跟随刊头收缩进度，收起后出现分隔线。 */
@Composable
private fun SettingsTopBar(
    title: String,
    listState: LazyListState,
    solid: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(statusBarTop + 56.dp)
                .drawBehind {
                    drawRect(color = backgroundColor.copy(alpha = mastheadCollapse(listState)))
                },
    ) {
        if (solid) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomStart),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop)
                    .padding(horizontal = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = mastheadCollapse(listState) },
            )
        }
    }
}

/** 圆角容器分组卡 */
@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f))
                .padding(vertical = Spacing.Large),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.Small))
        content()
    }
}

// -- 行类型 --

/**
 * 内联选择行
 */
@Composable
private fun InlineSelectRow(
    rowKey: String,
    title: String,
    summary: String,
    icon: ImageVector,
    options: ImmutableList<SettingsOption>,
    currentValue: String,
    expandedKey: String?,
    onExpandChange: (String?) -> Unit,
    onValueChange: (String) -> Unit,
) {
    val expanded = expandedKey == rowKey
    val currentLabel =
        remember(options, currentValue) {
            options.firstOrNull { it.value == currentValue }?.label
        }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = EaseOutEmphasized),
        label = "settings_row_chevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsRowFrame(
            icon = icon,
            highlighted = expanded,
            onClick = { onExpandChange(if (expanded) null else rowKey) },
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    // 当前选中值直接摆在标题下：一眼看得到，不必展开
                    text = currentLabel ?: summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (currentLabel != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (currentLabel != null) FontWeight.Medium else FontWeight.Normal,
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription =
                    if (expanded) {
                        stringResource(R.string.settings_collapse_options)
                    } else {
                        stringResource(R.string.settings_expand_options)
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter =
                expandVertically(
                    animationSpec = tween(durationMillis = 220, easing = EaseOutEmphasized),
                ) + fadeIn(tween(durationMillis = 180)),
            exit =
                shrinkVertically(
                    animationSpec = tween(durationMillis = 180, easing = EaseOutEmphasized),
                ) + fadeOut(tween(durationMillis = 140)),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(
                            start = SettingsRowContentInset,
                            end = Spacing.Large,
                            top = Spacing.ExtraSmall,
                            bottom = Spacing.Small,
                        ),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                options.forEach { option ->
                    OptionCard(
                        option = option,
                        selected = option.value == currentValue,
                        onClick = { onValueChange(option.value) },
                    )
                }
            }
        }
    }
}

/** 开关行：整行可点，图标在开启时轻微放大，动作快速收敛不喧宾。 */
@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (checked) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180, easing = EaseOutEmphasized),
        label = "settings_switch_icon_scale",
    )
    SettingsRowFrame(
        icon = icon,
        highlighted = checked,
        onClick = { onCheckedChange(!checked) },
        iconModifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
        )
    }
}

/** 跳转行：整行可点，右侧显示 chevron。 */
@Composable
private fun NavigationRow(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    SettingsRowFrame(
        icon = icon,
        highlighted = false,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 所有设置行共享的骨架：46dp 圆角图标筐 + 内容槽。整行 clickHighlight。 */
@Composable
private fun SettingsRowFrame(
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val iconBackground by animateColorAsState(
        targetValue =
            if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        animationSpec = tween(durationMillis = 220, easing = EaseOutEmphasized),
        label = "settings_row_icon_bg",
    )
    val iconTint by animateColorAsState(
        targetValue =
            if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = tween(durationMillis = 220, easing = EaseOutEmphasized),
        label = "settings_row_icon_tint",
    )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickHighlight(onClick = onClick)
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(SettingsRowIconSize)
                    .clip(Shapes.LargeCornerBasedShape)
                    .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = iconModifier,
            )
        }
        content()
    }
}

@Composable
private fun SettingsItemDivider() {
    Box(
        modifier =
            Modifier
                .padding(start = SettingsRowContentInset, end = Spacing.Large)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

// -- 选项卡（展开态里的单个选项）--

@Composable
private fun OptionCard(
    option: SettingsOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
            },
        animationSpec = tween(durationMillis = 200, easing = EaseOutEmphasized),
        label = "option_card_background",
    )
    val titleColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        animationSpec = tween(durationMillis = 200, easing = EaseOutEmphasized),
        label = "option_card_title",
    )
    val descriptionColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = tween(durationMillis = 200, easing = EaseOutEmphasized),
        label = "option_card_desc",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(Shapes.MediumCornerBasedShape)
                .background(background)
                .clickHighlight(onClick = onClick)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            tint = titleColor,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = titleColor,
            )
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = descriptionColor,
            )
        }
        // 选中态：小圆点里含 Check，比 RadioButton 更收敛
        AnimatedVisibility(
            visible = selected,
            enter =
                fadeIn(tween(durationMillis = 180)) +
                    scaleIn(
                        animationSpec = tween(durationMillis = 220, easing = EaseOutEmphasized),
                        initialScale = ScaleEnterFrom,
                    ),
            exit = fadeOut(tween(durationMillis = 120)),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// -- 在线音源与曲库范围：沿用设置页统一的卡片与行样式 --

@Composable
private fun OnlineDownloadSettingsSection(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { me.spica27.spicamusic.online.settings.OnlineSettings(context) }
    var expandedRowKey by remember { mutableStateOf<String?>(null) }
    var qualityKey by remember { mutableStateOf(settings.quality().key) }
    var concurrency by remember { mutableStateOf(settings.concurrency()) }
    var nameStyle by remember { mutableStateOf(settings.fileNameStyle()) }
    var wifiOnly by remember { mutableStateOf(settings.wifiOnly()) }

    val qualityOptions =
        remember {
            ImmutableList.of(
                SettingsOption(
                    value = me.spica27.spicamusic.online.OnlineQuality.Q128.key,
                    label = "128k",
                    description = "体积最小",
                    icon = Icons.Default.MusicNote,
                ),
                SettingsOption(
                    value = me.spica27.spicamusic.online.OnlineQuality.Q320.key,
                    label = "320k",
                    description = "默认档位",
                    icon = Icons.Default.LibraryMusic,
                ),
                SettingsOption(
                    value = me.spica27.spicamusic.online.OnlineQuality.FLAC.key,
                    label = "无损",
                    description = "体积较大",
                    icon = Icons.Default.Waves,
                ),
                SettingsOption(
                    value = me.spica27.spicamusic.online.OnlineQuality.FLAC24.key,
                    label = "Hi-Res",
                    description = "最高规格",
                    icon = Icons.Default.Layers,
                ),
            )
        }
    val concurrencyOptions =
        remember {
            ImmutableList.copyOf(
                (1..8).map { count ->
                    SettingsOption(
                        value = "$count",
                        label = "$count 首",
                        description = "同时下载 $count 首",
                        icon = Icons.Default.Tune,
                    )
                },
            )
        }
    val nameStyleOptions =
        remember {
            ImmutableList.of(
                SettingsOption(
                    value = me.spica27.spicamusic.online.settings.FileNameStyle.TITLE_ARTIST.name,
                    label = "歌名 - 歌手",
                    description = "默认",
                    icon = Icons.Default.MusicNote,
                ),
                SettingsOption(
                    value = me.spica27.spicamusic.online.settings.FileNameStyle.ARTIST_TITLE.name,
                    label = "歌手 - 歌名",
                    description = "便于按歌手归拢",
                    icon = Icons.Default.MusicNote,
                ),
                SettingsOption(
                    value = me.spica27.spicamusic.online.settings.FileNameStyle.TITLE_ONLY.name,
                    label = "歌名",
                    description = "最简洁",
                    icon = Icons.Default.MusicNote,
                ),
            )
        }

    SettingsSectionCard(
        title = "在线下载",
        subtitle = "默认 320k，解析失败自动降档重试；下载完成的文件自动进入曲库。",
        modifier = modifier,
    ) {
        InlineSelectRow(
            rowKey = "online_quality",
            title = "下载音质",
            summary = "在线音源使用的音质档位",
            icon = Icons.Default.LibraryMusic,
            options = qualityOptions,
            currentValue = qualityKey,
            expandedKey = expandedRowKey,
            onExpandChange = { expandedRowKey = it },
            onValueChange = { value ->
                val picked = me.spica27.spicamusic.online.OnlineQuality.fromKey(value)
                qualityKey = picked.key
                settings.setQuality(picked)
            },
        )
        SettingsItemDivider()
        InlineSelectRow(
            rowKey = "online_concurrency",
            title = "同时下载数量",
            summary = "批量下载时的并发数",
            icon = Icons.Default.Tune,
            options = concurrencyOptions,
            currentValue = "$concurrency",
            expandedKey = expandedRowKey,
            onExpandChange = { expandedRowKey = it },
            onValueChange = { value ->
                value.toIntOrNull()?.let { count ->
                    concurrency = count
                    settings.setConcurrency(count)
                }
            },
        )
        SettingsItemDivider()
        InlineSelectRow(
            rowKey = "online_name_style",
            title = "文件名",
            summary = "下载文件保存时的命名方式",
            icon = Icons.Default.MusicNote,
            options = nameStyleOptions,
            currentValue = nameStyle.name,
            expandedKey = expandedRowKey,
            onExpandChange = { expandedRowKey = it },
            onValueChange = { value ->
                me.spica27.spicamusic.online.settings.FileNameStyle.entries
                    .firstOrNull { it.name == value }
                    ?.let { style ->
                        nameStyle = style
                        settings.setFileNameStyle(style)
                    }
            },
        )
        SettingsItemDivider()
        SwitchRow(
            title = "仅 Wi-Fi 下载",
            summary = "移动网络下不自动下载",
            icon = Icons.Default.Wifi,
            checked = wifiOnly,
            onCheckedChange = { enabled ->
                wifiOnly = enabled
                settings.setWifiOnly(enabled)
            },
        )
    }
}

/** 扫描范围与迁移设置：统一放在“扫描音乐”页呈现，设置页不再单独展示。 */
@Composable
fun ScanScopeSettingsSection(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { me.spica27.spicamusic.online.settings.ScanScopeStore(context) }
    val songUseCases = org.koin.compose.koinInject<me.spica27.spicamusic.feature.library.domain.SongUseCases>()
    val executor = remember { me.spica27.spicamusic.ui.migration.LocalMigrationExecutor(context, songUseCases) }

    var whitelistEnabled by remember {
        mutableStateOf(store.mode() == me.spica27.spicamusic.feature.library.domain.scope.ScanMode.OnlySelectedDirectories)
    }
    var paths by remember { mutableStateOf(store.whitelistPaths()) }
    var migrating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(store.migrationStatus()) }
    var migrateRequest by remember { mutableStateOf<String?>(null) }

    val picker =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                val realPath = resolveScanDirectoryPath(uri)
                if (realPath != null) {
                    store.addWhitelistPath(realPath)
                    paths = store.whitelistPaths()
                    whitelistEnabled = true
                    status = "已添加目录：$realPath"
                } else {
                    status = "这个目录无法解析为本地路径，请换一个（例如内部存储下的 Music）"
                }
                store.setMigrationStatus(status)
            }
        }

    androidx.compose.runtime.LaunchedEffect(migrateRequest) {
        val target = migrateRequest ?: return@LaunchedEffect
        migrating = true
        val outcome =
            executor.migrate(java.io.File(target), moveInsteadOfCopy = false) { copied, total ->
                status = "正在迁移：$copied/$total"
            }
        status =
                    buildString {
                        append("迁移完成：复制 ${outcome.copied} 首，跳过 ${outcome.skipped} 首，失败 ${outcome.failed} 首")
                        outcome.firstError?.let { reason -> append("（首个失败原因：").append(reason).append("）") }
                    }
        store.setMigrationStatus(status)
        migrating = false
        migrateRequest = null
    }

    SettingsSectionCard(
        title = "扫描范围",
        subtitle = "开启后只收录白名单目录下的音频，系统媒体库的增量同步同样遵守该范围。",
        modifier = modifier,
    ) {
        SwitchRow(
            title = "只扫描指定目录",
            summary =
                if (paths.isEmpty()) {
                    "尚未添加目录，开启后曲库将为空"
                } else {
                    "已添加 ${paths.size} 个目录"
                },
            icon = Icons.Default.Folder,
            checked = whitelistEnabled,
            onCheckedChange = { enabled ->
                whitelistEnabled = enabled
                store.setMode(
                    if (enabled) {
                        me.spica27.spicamusic.feature.library.domain.scope.ScanMode.OnlySelectedDirectories
                    } else {
                        me.spica27.spicamusic.feature.library.domain.scope.ScanMode.AllDirectories
                    },
                )
            },
        )
        SettingsItemDivider()
        NavigationRow(
            title = "添加目录",
            summary = "从系统文件选择器里挑一个目录",
            icon = Icons.Default.Folder,
            onClick = { picker.launch(null) },
        )
        paths.forEach { path ->
            SettingsItemDivider()
            NavigationRow(
                title = path,
                summary = "点击移除这个目录",
                icon = Icons.Default.Delete,
                onClick = {
                    store.removeWhitelistPath(path)
                    paths = store.whitelistPaths()
                },
            )
        }
        SettingsItemDivider()
        NavigationRow(
            title = if (migrating) "正在迁移…" else "迁移到白名单目录",
            summary = status ?: "把已扫描到、且不在该目录下的音乐复制过去",
            icon = Icons.Default.Download,
            onClick = {
                val target = paths.firstOrNull() ?: return@NavigationRow
                if (!migrating) migrateRequest = target
            },
        )
    }
}

/** 把 SAF 目录树 URI 解析成绝对路径，失败返回 null。 */
private fun resolveScanDirectoryPath(treeUri: android.net.Uri): String? =
    runCatching {
        val documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        val parts = documentId.split(":", limit = 2)
        if (parts.size < 2) return@runCatching null
        val volume = parts[0]
        val relative = parts[1]
        val base =
            if (volume.equals("primary", ignoreCase = true)) {
                android.os.Environment.getExternalStorageDirectory().absolutePath
            } else {
                "/storage/$volume"
            }
        if (relative.isBlank()) base else "$base/$relative"
    }.getOrNull()

// -- 选项配置：每个选项配一个直观的图标 + 一句话说明 --

@Immutable
private data class SettingsOption(
    val value: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
private fun rememberColorStyleOptions(): ImmutableList<SettingsOption> {
    val texturedLabel = stringResource(R.string.theme_color_style_textured)
    val texturedDesc = stringResource(R.string.color_style_textured_desc)
    val flatLabel = stringResource(R.string.theme_color_style_flat)
    val flatDesc = stringResource(R.string.color_style_flat_desc)
    return remember(texturedLabel, texturedDesc, flatLabel, flatDesc) {
        ImmutableList.copyOf(
            listOf(
                SettingsOption(
                    ThemeColorStyle.Textured.value,
                    texturedLabel,
                    texturedDesc,
                    Icons.Default.AutoAwesome,
                ),
                SettingsOption(
                    ThemeColorStyle.Flat.value,
                    flatLabel,
                    flatDesc,
                    Icons.Default.Layers,
                ),
            ),
        )
    }
}

@Composable
private fun rememberColorSourceOptions(): ImmutableList<SettingsOption> {
    val dynamicLabel = stringResource(R.string.color_source_dynamic)
    val dynamicDesc = stringResource(R.string.color_source_dynamic_desc)
    val coverLabel = stringResource(R.string.color_source_cover)
    val coverDesc = stringResource(R.string.color_source_cover_desc)
    return remember(dynamicLabel, dynamicDesc, coverLabel, coverDesc) {
        ImmutableList.copyOf(
            listOf(
                SettingsOption(
                    me.spica27.spicamusic.common.entity.ColorSource.Dynamic.value,
                    dynamicLabel,
                    dynamicDesc,
                    Icons.Default.AutoAwesome,
                ),
                SettingsOption(
                    me.spica27.spicamusic.common.entity.ColorSource.Cover.value,
                    coverLabel,
                    coverDesc,
                    Icons.Default.Palette,
                ),
            ),
        )
    }
}

@Composable
private fun rememberBackgroundOptions(): ImmutableList<SettingsOption> {
    val off = stringResource(R.string.settings_option_off)
    val offDesc = stringResource(R.string.bg_off_desc)
    val topGlow = stringResource(R.string.bg_top_glow)
    val topGlowDesc = stringResource(R.string.bg_top_glow_desc)
    val aurora = stringResource(R.string.bg_liquid_aurora)
    val auroraDesc = stringResource(R.string.bg_liquid_aurora_desc)
    val shader = stringResource(R.string.bg_effect_shader)
    val shaderDesc = stringResource(R.string.bg_effect_shader_desc)
    val warp = stringResource(R.string.bg_fluid_warp)
    val warpDesc = stringResource(R.string.bg_fluid_warp_desc)
    val blur = stringResource(R.string.bg_blur_cover)
    val blurDesc = stringResource(R.string.bg_blur_cover_desc)
    return remember(
        off,
        offDesc,
        topGlow,
        topGlowDesc,
        aurora,
        auroraDesc,
        shader,
        shaderDesc,
        warp,
        warpDesc,
        blur,
        blurDesc,
    ) {
        ImmutableList.copyOf(
            listOf(
                SettingsOption(
                    DynamicSpectrumBackground.TopGlow.value,
                    topGlow,
                    topGlowDesc,
                    Icons.Default.WbSunny,
                ),
                SettingsOption(
                    DynamicSpectrumBackground.LiquidAurora.value,
                    aurora,
                    auroraDesc,
                    Icons.Default.Waves,
                ),
                SettingsOption(
                    DynamicSpectrumBackground.EffectShader.value,
                    shader,
                    shaderDesc,
                    Icons.Default.BlurOn,
                ),
                SettingsOption(
                    DynamicSpectrumBackground.FluidWarp.value,
                    warp,
                    warpDesc,
                    Icons.Default.LensBlur,
                ),
                SettingsOption(
                    DynamicSpectrumBackground.BlurCover.value,
                    blur,
                    blurDesc,
                    Icons.Default.BlurOn,
                ),
                SettingsOption(
                    DynamicSpectrumBackground.OFF.value,
                    off,
                    offDesc,
                    Icons.Default.PowerSettingsNew,
                ),
            ),
        )
    }
}

@Composable
private fun rememberCoverTapOptions(): ImmutableList<SettingsOption> {
    val off = stringResource(R.string.settings_option_off)
    val offDesc = stringResource(R.string.cover_off_desc)
    val stars = stringResource(R.string.cover_shining_stars)
    val starsDesc = stringResource(R.string.cover_shining_stars_desc)
    val city = stringResource(R.string.cover_audio_city)
    val cityDesc = stringResource(R.string.cover_audio_city_desc)
    return remember(off, offDesc, stars, starsDesc, city, cityDesc) {
        ImmutableList.copyOf(
            listOf(
                SettingsOption(
                    DynamicCoverType.ShiningStars.value,
                    stars,
                    starsDesc,
                    Icons.Default.AutoAwesome,
                ),
                SettingsOption(
                    DynamicCoverType.AudioCity.value,
                    city,
                    cityDesc,
                    Icons.Default.LocationCity,
                ),
                SettingsOption(
                    DynamicCoverType.OFF.value,
                    off,
                    offDesc,
                    Icons.Default.PowerSettingsNew,
                ),
            ),
        )
    }
}

@Composable
private fun rememberProgressWaveformOptions(): ImmutableList<SettingsOption> {
    val dynamic = stringResource(R.string.progress_bar_style_dynamic_waveform)
    val dynamicDesc = stringResource(R.string.waveform_dynamic_desc)
    val timeDomain = stringResource(R.string.progress_bar_style_time_domain_waveform)
    val timeDomainDesc = stringResource(R.string.waveform_time_domain_desc)
    return remember(dynamic, dynamicDesc, timeDomain, timeDomainDesc) {
        ImmutableList.copyOf(
            listOf(
                SettingsOption(
                    ProgressBarStyle.DynamicWaveform.value,
                    dynamic,
                    dynamicDesc,
                    Icons.Default.GraphicEq,
                ),
                SettingsOption(
                    ProgressBarStyle.TimeDomainWaveform.value,
                    timeDomain,
                    timeDomainDesc,
                    Icons.AutoMirrored.Filled.ShowChart,
                ),
            ),
        )
    }
}

// -- 布局常量 & 刊头收缩计算 --

/** 图标筐尺寸；行内其他元素的横向对齐（分隔线、展开选项区域）以它为基准。 */
private val SettingsRowIconSize = 46.dp

/**
 * 让分隔线和展开出来的选项都对齐到标题的起始位置。
 */
private val SettingsRowContentInset = Spacing.Large + SettingsRowIconSize + Spacing.Medium

private val MastheadCollapseDistance = 140.dp

/** 刊头收缩进度：0f = 完全展开，1f = 完全滚进顶栏。 */
private fun Density.mastheadCollapse(listState: LazyListState): Float =
    if (listState.firstVisibleItemIndex > 0) {
        1f
    } else {
        (listState.firstVisibleItemScrollOffset / MastheadCollapseDistance.toPx()).coerceIn(0f, 1f)
    }
