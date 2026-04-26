package com.happycola233.bilitools.ui.settings

import android.os.Build
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AudioQualities
import com.happycola233.bilitools.core.DownloadNaming
import com.happycola233.bilitools.core.NamingPreviewSegment
import com.happycola233.bilitools.core.NamingRenderContext
import com.happycola233.bilitools.core.NamingTemplateScope
import com.happycola233.bilitools.core.NamingToken
import com.happycola233.bilitools.core.NamingTokenGroup
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.DefaultDownloadQualitySettings
import com.happycola233.bilitools.data.DefaultDownloadVideoCodec
import com.happycola233.bilitools.data.DownloadQualityMode
import com.happycola233.bilitools.data.IssueReportLogState
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.data.TopLevelFolderMode
import com.happycola233.bilitools.ui.BiliTvLaunchMotion
import com.happycola233.bilitools.ui.theme.BiliToolsSettingsTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiliToolsSettingsContent(
    settings: AppSettings,
    liveUpdateSupported: Boolean,
    issueReportState: IssueReportLogState,
    backStack: SnapshotStateList<SettingsDestination>,
    checkUpdateSummary: String,
    versionName: String,
    versionCode: Long,
    issueReportExporting: Boolean,
    issueReportClearing: Boolean,
    onExit: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onNavigateBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenDownloadLocationPicker: (String) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemeColorChange: (AppThemeColor) -> Unit,
    onLiveActivityStyleNotificationChange: (Boolean) -> Unit,
    onDefaultDownloadQualityChange: (DefaultDownloadQualitySettings) -> Unit,
    onAddMetadataChange: (Boolean) -> Unit,
    onConvertXmlDanmakuToAssChange: (Boolean) -> Unit,
    onConfirmCellularChange: (Boolean) -> Unit,
    onHideInAlbumChange: (Boolean) -> Unit,
    onNamingTopLevelFolderModeChange: (TopLevelFolderMode) -> Unit,
    onNamingOverwriteExistingFilesChange: (Boolean) -> Unit,
    onNamingCleanSeparatorsChange: (Boolean) -> Unit,
    onNamingTopLevelFolderTemplateChange: (String) -> Unit,
    onNamingItemFolderTemplateChange: (String) -> Unit,
    onNamingFileTemplateChange: (String) -> Unit,
    onRestoreNamingDefaults: () -> Unit,
    onBlackThemeChange: (Boolean) -> Unit,
    onGlassDebugChange: (Boolean) -> Unit,
    onIssueReportLoggingChange: (Boolean) -> Unit,
    onExportIssueReport: () -> Unit,
    onClearIssueReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BiliToolsSettingsTheme(settings = settings) {
        NavDisplay(
            backStack = backStack,
            onBack = onNavigateBack,
            transitionSpec = {
                slideInHorizontally(initialOffsetX = { it }).togetherWith(
                    slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut(),
                )
            },
            popTransitionSpec = {
                (slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()).togetherWith(
                    slideOutHorizontally(targetOffsetX = { it }),
                )
            },
            predictivePopTransitionSpec = {
                (slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()).togetherWith(
                    slideOutHorizontally(targetOffsetX = { it }),
                )
            },
            entryProvider = entryProvider {
                entry<SettingsDestination.Main> {
                    MainSettingsScreen(
                        onBack = onExit,
                        onNavigate = onNavigate,
                        modifier = modifier,
                    )
                }

                entry<SettingsDestination.General> {
                    GeneralSettingsScreen(
                        settings = settings,
                        liveUpdateSupported = liveUpdateSupported,
                        onLiveActivityStyleNotificationChange = onLiveActivityStyleNotificationChange,
                        onNavigate = onNavigate,
                        onBack = onNavigateBack,
                        modifier = modifier,
                    )
                }

                entry<SettingsDestination.DefaultDownloadQuality> {
                    DefaultDownloadQualityScreen(
                        settings = settings,
                        onDefaultDownloadQualityChange = onDefaultDownloadQualityChange,
                        onBack = onNavigateBack,
                        modifier = modifier,
                    )
                }

                entry<SettingsDestination.Download> {
                    DownloadSettingsScreen(
                        settings = settings,
                        onOpenDownloadLocationPicker = onOpenDownloadLocationPicker,
                        onAddMetadataChange = onAddMetadataChange,
                        onConvertXmlDanmakuToAssChange = onConvertXmlDanmakuToAssChange,
                        onConfirmCellularChange = onConfirmCellularChange,
                        onHideInAlbumChange = onHideInAlbumChange,
                        onBack = onNavigateBack,
                        modifier = modifier,
                    )
                }

                entry<SettingsDestination.Naming> {
                    NamingSettingsScreen(
                        settings = settings,
                        onTopLevelFolderModeChange = onNamingTopLevelFolderModeChange,
                        onOverwriteExistingFilesChange = onNamingOverwriteExistingFilesChange,
                        onCleanSeparatorsChange = onNamingCleanSeparatorsChange,
                        onTopLevelFolderTemplateChange = onNamingTopLevelFolderTemplateChange,
                        onItemFolderTemplateChange = onNamingItemFolderTemplateChange,
                        onFileTemplateChange = onNamingFileTemplateChange,
                        onRestoreDefaults = onRestoreNamingDefaults,
                        onBack = onNavigateBack,
                        modifier = modifier,
                    )
                }

                entry<SettingsDestination.Appearance> {
                    AppearanceSettingsScreen(
                        settings = settings,
                        onThemeModeChange = onThemeModeChange,
                        onThemeColorChange = onThemeColorChange,
                        onBlackThemeChange = onBlackThemeChange,
                        onGlassDebugChange = onGlassDebugChange,
                        onBack = onNavigateBack,
                        modifier = modifier,
                    )
                }

                entry<SettingsDestination.About> {
                    AboutSettingsScreen(
                        versionName = versionName,
                        versionCode = versionCode,
                        checkUpdateSummary = checkUpdateSummary,
                        issueReportState = issueReportState,
                        issueReportExporting = issueReportExporting,
                        issueReportClearing = issueReportClearing,
                        onCheckUpdate = onCheckUpdate,
                        onIssueReportLoggingChange = onIssueReportLoggingChange,
                        onExportIssueReport = onExportIssueReport,
                        onClearIssueReport = onClearIssueReport,
                        onBack = onNavigateBack,
                        modifier = modifier,
                    )
                }
            },
            modifier = modifier,
        )
    }
}

private data class SettingsEntry(
    val destination: SettingsDestination,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
)

private data class ThemeOption(
    val mode: AppThemeMode,
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
)

private data class QualityModeOption(
    val mode: DownloadQualityMode,
    @StringRes val labelRes: Int,
)

private data class ResolutionOption(
    val id: Int,
    @StringRes val labelRes: Int,
)

private data class CodecQualityOption(
    val codec: DefaultDownloadVideoCodec,
    @StringRes val labelRes: Int,
)

private data class AudioBitrateOption(
    val id: Int,
    @StringRes val labelRes: Int,
)

private data class TopLevelFolderModeOption(
    val mode: TopLevelFolderMode,
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
)

private data class ColorSchemeOption(
    val themeColor: AppThemeColor,
    val seedColor: Color,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val primaryEntries = remember {
        listOf(
            SettingsEntry(
                destination = SettingsDestination.General,
                iconRes = R.drawable.ic_tune_24,
                titleRes = R.string.settings_general_title,
                summaryRes = R.string.settings_general_summary,
            ),
            SettingsEntry(
                destination = SettingsDestination.Download,
                iconRes = R.drawable.ic_download_for_offline_24,
                titleRes = R.string.settings_download_title,
                summaryRes = R.string.settings_download_summary,
            ),
            SettingsEntry(
                destination = SettingsDestination.Naming,
                iconRes = R.drawable.ic_save_as_filled_24,
                titleRes = R.string.settings_naming_title,
                summaryRes = R.string.settings_naming_summary,
            ),
            SettingsEntry(
                destination = SettingsDestination.Appearance,
                iconRes = R.drawable.ic_palette_24,
                titleRes = R.string.settings_appearance_title,
                summaryRes = R.string.settings_appearance_summary,
            ),
        )
    }
    val aboutEntry = remember {
        SettingsEntry(
            destination = SettingsDestination.About,
            iconRes = R.drawable.ic_info_24,
            titleRes = R.string.settings_about_title,
            summaryRes = R.string.settings_about_summary,
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_screen_title),
        subtitle = stringResource(R.string.app_name),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }

            items(primaryEntries.size) { index ->
                val entry = primaryEntries[index]
                ClickableListItem(
                    items = primaryEntries.size,
                    index = index,
                    leadingContent = { SettingsItemIcon(entry.iconRes) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(entry.titleRes))
                    },
                    supportingContent = {
                        Text(
                            stringResource(entry.summaryRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        SettingsItemIcon(R.drawable.ic_chevron_right_24)
                    },
                    onClick = { onNavigate(entry.destination) },
                )
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                ClickableListItem(
                    items = 1,
                    index = 0,
                    leadingContent = { SettingsItemIcon(aboutEntry.iconRes) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(aboutEntry.titleRes))
                    },
                    supportingContent = { Text(stringResource(aboutEntry.summaryRes)) },
                    trailingContent = {
                        SettingsItemIcon(R.drawable.ic_chevron_right_24)
                    },
                    onClick = { onNavigate(aboutEntry.destination) },
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GeneralSettingsScreen(
    settings: AppSettings,
    liveUpdateSupported: Boolean,
    onLiveActivityStyleNotificationChange: (Boolean) -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liveUpdateChecked = liveUpdateSupported && settings.liveActivityStyleNotificationEnabled
    val liveUpdateDescription = if (liveUpdateSupported) {
        stringResource(R.string.settings_live_activity_style_notification_desc)
    } else {
        "当前系统不支持 Live Update 能力。"
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    SettingsScaffold(
        title = stringResource(R.string.settings_general_title),
        subtitle = stringResource(R.string.settings_screen_title),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }
            item {
                ClickableListItem(
                    items = 2,
                    index = 0,
                    leadingContent = { SettingsItemIcon(R.drawable.ic_high_quality_24) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(R.string.settings_default_download_quality))
                    },
                    supportingContent = {
                        Text(
                            text = defaultDownloadQualitySummary(settings.defaultDownloadQuality),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        SettingsItemIcon(R.drawable.ic_chevron_right_24)
                    },
                    onClick = { onNavigate(SettingsDestination.DefaultDownloadQuality) },
                )
            }
            item {
                ExpressiveSwitchListItem(
                    checked = liveUpdateChecked,
                    iconRes = R.drawable.ic_dynamic_feed_24,
                    title = stringResource(R.string.settings_live_activity_style_notification),
                    description = liveUpdateDescription,
                    enabled = liveUpdateSupported,
                    items = 2,
                    index = 1,
                    onCheckedChange = onLiveActivityStyleNotificationChange,
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadSettingsScreen(
    settings: AppSettings,
    onOpenDownloadLocationPicker: (String) -> Unit,
    onAddMetadataChange: (Boolean) -> Unit,
    onConvertXmlDanmakuToAssChange: (Boolean) -> Unit,
    onConfirmCellularChange: (Boolean) -> Unit,
    onHideInAlbumChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    SettingsScaffold(
        title = stringResource(R.string.settings_download_title),
        subtitle = stringResource(R.string.settings_screen_title),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }

            item {
                ClickableListItem(
                    items = 5,
                    index = 0,
                    leadingContent = { SettingsItemIcon(R.drawable.ic_folder_24) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(R.string.settings_download_location))
                    },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.settings_download_location_value,
                                settings.downloadRootRelativePath,
                            )
                        )
                    },
                    trailingContent = {
                        SettingsItemIcon(R.drawable.ic_chevron_right_24)
                    },
                    onClick = {
                        onOpenDownloadLocationPicker(settings.downloadRootRelativePath)
                    },
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.addMetadata,
                    iconRes = R.drawable.ic_metadata_24,
                    title = stringResource(R.string.settings_add_metadata),
                    description = stringResource(R.string.settings_add_metadata_desc),
                    items = 5,
                    index = 1,
                    onCheckedChange = onAddMetadataChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.convertXmlDanmakuToAss,
                    iconRes = R.drawable.ic_transform_24,
                    title = stringResource(R.string.settings_convert_xml_danmaku_to_ass),
                    description = stringResource(R.string.settings_convert_xml_danmaku_to_ass_desc),
                    items = 5,
                    index = 2,
                    onCheckedChange = onConvertXmlDanmakuToAssChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.confirmCellularDownload,
                    iconRes = R.drawable.ic_cell_tower_24,
                    title = stringResource(R.string.settings_confirm_cellular),
                    description = stringResource(R.string.settings_confirm_cellular_desc),
                    items = 5,
                    index = 3,
                    onCheckedChange = onConfirmCellularChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.hideDownloadedVideosInSystemAlbum,
                    iconRes = R.drawable.ic_hide_image_24,
                    title = stringResource(R.string.settings_hide_download_video_in_system_album),
                    description = stringResource(R.string.settings_hide_download_video_in_system_album_desc),
                    items = 5,
                    index = 4,
                    onCheckedChange = onHideInAlbumChange,
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearanceSettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemeColorChange: (AppThemeColor) -> Unit,
    onBlackThemeChange: (Boolean) -> Unit,
    onGlassDebugChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    SettingsScaffold(
        title = stringResource(R.string.settings_appearance_title),
        subtitle = stringResource(R.string.settings_screen_title),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }

            item {
                ThemePickerListItem(
                    mode = settings.themeMode,
                    items = 4,
                    index = 0,
                    onThemeChange = onThemeModeChange,
                )
            }

            item {
                ColorSchemePickerListItem(
                    color = settings.themeColor,
                    items = 4,
                    index = 1,
                    onColorChange = onThemeColorChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.darkModePureBlack,
                    iconRes = R.drawable.ic_contrast_24,
                    title = stringResource(R.string.settings_black_theme_title),
                    description = stringResource(R.string.settings_black_theme_desc),
                    items = 4,
                    index = 2,
                    onCheckedChange = onBlackThemeChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.downloadsGlassDebugEnabled,
                    iconRes = R.drawable.ic_blur_on_24,
                    title = stringResource(R.string.settings_downloads_glass_debug),
                    description = stringResource(R.string.settings_downloads_glass_debug_desc),
                    items = 4,
                    index = 3,
                    onCheckedChange = onGlassDebugChange,
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
private fun NamingSettingsScreen(
    settings: AppSettings,
    onTopLevelFolderModeChange: (TopLevelFolderMode) -> Unit,
    onOverwriteExistingFilesChange: (Boolean) -> Unit,
    onCleanSeparatorsChange: (Boolean) -> Unit,
    onTopLevelFolderTemplateChange: (String) -> Unit,
    onItemFolderTemplateChange: (String) -> Unit,
    onFileTemplateChange: (String) -> Unit,
    onRestoreDefaults: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val previewContext = rememberNamingPreviewContext()
    var showRestoreDefaultsConfirmDialog by rememberSaveable { mutableStateOf(false) }

    if (showRestoreDefaultsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDefaultsConfirmDialog = false },
            title = {
                Text(stringResource(R.string.settings_naming_restore_defaults))
            },
            text = {
                Text(stringResource(R.string.settings_naming_restore_defaults_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDefaultsConfirmDialog = false
                        onRestoreDefaults()
                    },
                ) {
                    Text(stringResource(R.string.settings_naming_restore_defaults_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDefaultsConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_naming_title),
        subtitle = stringResource(R.string.settings_screen_title),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    ) { innerPadding ->
        val showTopLevelFolderTemplate =
            settings.naming.topLevelFolderMode != TopLevelFolderMode.Disabled
        val topLevelFolderGroupItems = if (showTopLevelFolderTemplate) 2 else 1
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.naming.overwriteExistingFiles,
                    iconRes = R.drawable.ic_file_save_24,
                    title = stringResource(R.string.settings_naming_overwrite_existing),
                    description = stringResource(R.string.settings_naming_overwrite_existing_desc),
                    items = 1,
                    index = 0,
                    onCheckedChange = onOverwriteExistingFilesChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.naming.cleanSeparators,
                    iconRes = R.drawable.ic_wand_shine_24,
                    title = stringResource(R.string.settings_naming_clean_separators),
                    description = stringResource(R.string.settings_naming_clean_separators_desc),
                    items = 1,
                    index = 0,
                    onCheckedChange = onCleanSeparatorsChange,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TopLevelFolderModeListItem(
                        mode = settings.naming.topLevelFolderMode,
                        items = topLevelFolderGroupItems,
                        index = 0,
                        onModeChange = onTopLevelFolderModeChange,
                    )
                    if (showTopLevelFolderTemplate) {
                        NamingTemplateEditorPanel(
                            iconRes = R.drawable.ic_folder_special_24,
                            title = stringResource(R.string.settings_naming_top_level_folder_template),
                            description = stringResource(R.string.settings_naming_top_level_folder_template_desc),
                            value = settings.naming.topLevelFolderTemplate,
                            scope = NamingTemplateScope.TopFolder,
                            previewContext = previewContext,
                            previewExtension = null,
                            cleanSeparators = settings.naming.cleanSeparators,
                            shape = SettingsExpressiveShapes.groupShape(
                                index = 1,
                                items = topLevelFolderGroupItems,
                            ),
                            onValueChange = onTopLevelFolderTemplateChange,
                        )
                    }
                }
            }

            item {
                NamingTemplateEditorPanel(
                    iconRes = R.drawable.ic_bookmark_manager_24,
                    title = stringResource(R.string.settings_naming_item_folder_template),
                    description = stringResource(R.string.settings_naming_item_folder_template_desc),
                    value = settings.naming.itemFolderTemplate,
                    scope = NamingTemplateScope.ItemFolder,
                    previewContext = previewContext,
                    previewExtension = null,
                    cleanSeparators = settings.naming.cleanSeparators,
                    onValueChange = onItemFolderTemplateChange,
                )
            }

            item {
                NamingTemplateEditorPanel(
                    iconRes = R.drawable.ic_save_as_24,
                    title = stringResource(R.string.settings_naming_file_template),
                    description = stringResource(R.string.settings_naming_file_template_desc),
                    value = settings.naming.fileTemplate,
                    scope = NamingTemplateScope.File,
                    previewContext = previewContext,
                    previewExtension = "mp4",
                    cleanSeparators = settings.naming.cleanSeparators,
                    onValueChange = onFileTemplateChange,
                )
            }

            item {
                ClickableListItem(
                    items = 1,
                    index = 0,
                    leadingContent = { SettingsItemIcon(R.drawable.ic_refresh_24) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(R.string.settings_naming_restore_defaults))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.settings_naming_restore_defaults_desc))
                    },
                    onClick = { showRestoreDefaultsConfirmDialog = true },
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopLevelFolderModeListItem(
    mode: TopLevelFolderMode,
    items: Int,
    index: Int,
    onModeChange: (TopLevelFolderMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember {
        listOf(
            TopLevelFolderModeOption(
                mode = TopLevelFolderMode.Auto,
                iconRes = R.drawable.ic_brightness_auto_24,
                labelRes = R.string.settings_naming_top_level_folder_mode_auto,
            ),
            TopLevelFolderModeOption(
                mode = TopLevelFolderMode.Enabled,
                iconRes = R.drawable.ic_folder_managed_24,
                labelRes = R.string.settings_naming_top_level_folder_mode_enabled,
            ),
            TopLevelFolderModeOption(
                mode = TopLevelFolderMode.Disabled,
                iconRes = R.drawable.ic_close_rounded_24,
                labelRes = R.string.settings_naming_top_level_folder_mode_disabled,
            ),
        )
    }

    Column(
        modifier = modifier.clip(SettingsExpressiveShapes.groupShape(index, items)),
    ) {
        ListItem(
            leadingContent = {
                SettingsItemIcon(R.drawable.ic_folder_managed_24)
            },
            headlineContent = {
                SettingsItemTitle(stringResource(R.string.settings_naming_top_level_folder_mode_title))
            },
            supportingContent = {
                Text(namingTopLevelFolderModeDescription(mode))
            },
            colors = SettingsExpressiveDefaults.listItemColors,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier
                .background(SettingsExpressiveDefaults.listItemColors.containerColor)
                .padding(start = 52.dp, end = 16.dp, bottom = 8.dp),
        ) {
            options.fastForEachIndexed { optionIndex, option ->
                ToggleButton(
                    checked = option.mode == mode,
                    onCheckedChange = { onModeChange(option.mode) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (optionIndex) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NamingTemplateEditorPanel(
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    value: String,
    scope: NamingTemplateScope,
    previewContext: NamingRenderContext,
    previewExtension: String?,
    cleanSeparators: Boolean,
    onValueChange: (String) -> Unit,
    shape: CornerBasedShape = SettingsExpressiveShapes.cardShape,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(scope.name) { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
        )
    }
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            val safeCursor = textFieldValue.selection.start.coerceAtMost(value.length)
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(safeCursor),
            )
        }
    }
    val previewSegments = remember(textFieldValue.text) {
        DownloadNaming.previewSegments(textFieldValue.text)
    }
    val previewValue = remember(
        textFieldValue.text,
        previewContext,
        previewExtension,
        cleanSeparators,
    ) {
        val rendered = DownloadNaming.renderComponent(
            template = textFieldValue.text,
            context = previewContext,
            cleanSeparators = cleanSeparators,
        )
        previewExtension?.let {
            DownloadNaming.appendExtension(
                baseName = rendered,
                extension = it,
                cleanSeparators = cleanSeparators,
            )
        } ?: rendered
    }
    val previewLabel = stringResource(R.string.settings_naming_preview_value, "")
    val tokenSections = remember(scope) { namingTokenSections(scope) }
    val interactionSource = remember { MutableInteractionSource() }
    val expandedRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = 180,
            easing = FastOutSlowInEasing,
        ),
        label = "${scope.name}ChevronRotation",
    )
    val previewColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "${scope.name}PreviewColor",
    )

    Surface(
        color = SettingsExpressiveDefaults.listItemContainerColor,
        shape = shape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            ListItem(
                leadingContent = { SettingsItemIcon(iconRes) },
                headlineContent = { SettingsItemTitle(title) },
                supportingContent = {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more_24),
                        contentDescription = if (expanded) {
                            stringResource(R.string.settings_naming_collapse_template)
                        } else {
                            stringResource(R.string.settings_naming_expand_template)
                        },
                        modifier = Modifier.graphicsLayer { rotationZ = expandedRotation },
                    )
                },
                colors = SettingsExpressiveDefaults.listItemColors,
                modifier = Modifier.clickable(
                    interactionSource = interactionSource,
                    onClick = { expanded = !expanded },
                ),
            )

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 120,
                        easing = FastOutSlowInEasing,
                    ),
                ) +
                    expandVertically(
                        animationSpec = tween(
                            durationMillis = 220,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 90,
                        easing = FastOutLinearInEasing,
                    ),
                ) +
                    shrinkVertically(
                        animationSpec = tween(
                            durationMillis = 180,
                            easing = FastOutLinearInEasing,
                        ),
                    ),
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.settings_naming_rich_preview),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    NamingTemplateRichPreview(
                        segments = previewSegments,
                        emptyHint = stringResource(R.string.settings_naming_empty_template_hint),
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            onValueChange(it.text)
                        },
                        label = {
                            Text(stringResource(R.string.settings_naming_template_editor_label))
                        },
                        supportingText = {
                            Text(
                                buildAnnotatedString {
                                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                    append(previewLabel)
                                    pop()
                                    append(previewValue)
                                },
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))
                    tokenSections.fastForEachIndexed { index, section ->
                        if (index > 0) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            text = namingTokenGroupLabel(section.group),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            section.tokens.forEach { token ->
                                NamingTokenChip(
                                    text = namingTokenButtonLabel(token),
                                    onClick = {
                                        val inserted = insertTokenAtSelection(
                                            current = textFieldValue,
                                            token = token,
                                        )
                                        textFieldValue = inserted
                                        onValueChange(inserted.text)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NamingTokenChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "namingTokenChipScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "namingTokenChipContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "namingTokenChipContent",
    )

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NamingTemplateRichPreview(
    segments: List<NamingPreviewSegment>,
    emptyHint: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (segments.isEmpty()) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            val bodyStyle = MaterialTheme.typography.bodyMedium
            val previewLineHeight = 2.5.em
            val normalColor = MaterialTheme.colorScheme.onSurfaceVariant
            val tokenContainerColor = MaterialTheme.colorScheme.secondaryContainer
            val tokenContentColor = MaterialTheme.colorScheme.onSecondaryContainer
            val invalidContainerColor = MaterialTheme.colorScheme.errorContainer
            val invalidContentColor = MaterialTheme.colorScheme.onErrorContainer
            val annotatedText = buildAnnotatedString {
                segments.fastForEachIndexed { index, segment ->
                    val token = segment.token
                    val isTokenLiteral = segment.raw.startsWith("{") && segment.raw.endsWith("}")
                    when {
                        token != null -> {
                            appendInlineContent(
                                id = "naming_preview_$index",
                                alternateText = namingTokenPreviewLabel(token),
                            )
                        }

                        isTokenLiteral -> {
                            appendInlineContent(
                                id = "naming_preview_$index",
                                alternateText = segment.raw,
                            )
                        }

                        else -> append(segment.raw)
                    }
                }
            }
            val inlineContent = buildMap<String, InlineTextContent> {
                segments.fastForEachIndexed { index, segment ->
                    val token = segment.token
                    val isTokenLiteral = segment.raw.startsWith("{") && segment.raw.endsWith("}")
                    if (token == null && !isTokenLiteral) return@fastForEachIndexed
                    val label = token?.let { namingTokenPreviewLabel(it) } ?: segment.raw
                    val isError = token == null && isTokenLiteral
                    put(
                        key = "naming_preview_$index",
                        value = InlineTextContent(
                            placeholder = Placeholder(
                                width = namingPreviewChipWidthEm(label).em,
                                height = 1.9.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                            ),
                        ) { _ ->
                            NamingPreviewInlineChip(
                                text = label,
                                containerColor = if (isError) {
                                    invalidContainerColor
                                } else {
                                    tokenContainerColor
                                },
                                contentColor = if (isError) {
                                    invalidContentColor
                                } else {
                                    tokenContentColor
                                },
                            )
                        },
                    )
                }
            }
            BasicText(
                text = annotatedText,
                inlineContent = inlineContent,
                style = bodyStyle.copy(
                    color = normalColor,
                    lineHeight = previewLineHeight,
                ),
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun NamingPreviewInlineChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun namingPreviewChipWidthEm(text: String): Float {
    var width = 2.4f
    text.forEach { char ->
        width += when {
            char.isWhitespace() -> 0.35f
            char.code in 0x4E00..0x9FFF -> 1.0f
            char.isLetterOrDigit() -> 0.62f
            else -> 0.5f
        }
    }
    return max(width, 4.5f)
}

private data class NamingTokenSection(
    val group: NamingTokenGroup,
    val tokens: List<NamingToken>,
)

private fun namingTokenSections(scope: NamingTemplateScope): List<NamingTokenSection> {
    return listOf(
        NamingTokenGroup.General,
        NamingTokenGroup.Time,
        NamingTokenGroup.Ids,
        NamingTokenGroup.Stream,
    ).mapNotNull { group ->
        val tokens = DownloadNaming.tokensForScope(scope)
            .filter { it.group == group }
            .sortedBy(::namingTokenDisplayOrder)
        if (tokens.isEmpty()) {
            null
        } else {
            NamingTokenSection(group = group, tokens = tokens)
        }
    }
}

private fun namingTokenDisplayOrder(token: NamingToken): Int {
    return when (token) {
        NamingToken.VideoTitle -> 0
        NamingToken.CollectionTitle -> 1
        NamingToken.Title -> 2
        NamingToken.P -> 3
        NamingToken.Container -> 4
        NamingToken.MediaType -> 5
        NamingToken.TaskType -> 6
        NamingToken.Index -> 10
        NamingToken.PubTime -> 11
        NamingToken.DownTime -> 12
        NamingToken.Upper -> 20
        NamingToken.UpperId -> 21
        NamingToken.Aid -> 22
        NamingToken.Bvid -> 23
        NamingToken.Sid -> 24
        NamingToken.Fid -> 25
        NamingToken.Cid -> 26
        NamingToken.Epid -> 27
        NamingToken.Ssid -> 28
        NamingToken.Opid -> 29
        NamingToken.Res -> 30
        NamingToken.Abr -> 31
        NamingToken.Enc -> 32
        NamingToken.Fmt -> 33
    }
}

private fun insertTokenAtSelection(
    current: TextFieldValue,
    token: NamingToken,
): TextFieldValue {
    val insertion = "{${token.key}}"
    val start = minOf(current.selection.start, current.selection.end)
    val end = maxOf(current.selection.start, current.selection.end)
    val next = buildString {
        append(current.text.substring(0, start))
        append(insertion)
        append(current.text.substring(end))
    }
    val cursor = start + insertion.length
    return TextFieldValue(
        text = next,
        selection = TextRange(cursor),
    )
}

@Composable
private fun rememberNamingPreviewContext(): NamingRenderContext {
    val videoTitle = stringResource(R.string.settings_naming_preview_video_title)
    val collectionTitle = stringResource(R.string.settings_naming_preview_collection_title)
    val title = stringResource(R.string.settings_naming_preview_item_title)
    val container = stringResource(R.string.parse_media_type_video)
    val mediaType = stringResource(R.string.parse_media_type_video)
    val taskType = stringResource(R.string.output_audio_video)
    val resolution = stringResource(R.string.parse_resolution_1080)
    val codec = stringResource(R.string.parse_codec_avc)
    val audioBitrate = stringResource(R.string.parse_bitrate_192)
    val format = stringResource(R.string.format_mp4)
    val upper = stringResource(R.string.settings_naming_preview_upper)
    return remember(
        videoTitle,
        collectionTitle,
        title,
        container,
        mediaType,
        taskType,
        resolution,
        codec,
        audioBitrate,
        format,
    ) {
        NamingRenderContext(
            videoTitle = videoTitle,
            collectionTitle = collectionTitle,
            title = title,
            p = "1",
            container = container,
            mediaType = mediaType,
            taskType = taskType,
            index = 1,
            pubTimeEpochSeconds = 1_719_331_200L,
            downTimeEpochSeconds = 1_744_412_800L,
            upper = upper,
            upperId = "2333333",
            aid = "123456789",
            sid = "10001",
            fid = "556677",
            cid = "99887766",
            bvid = "BV1xx411c7mD",
            epid = "20001",
            ssid = "30001",
            opid = "opus-42",
            res = resolution,
            abr = audioBitrate,
            enc = codec,
            fmt = format,
        )
    }
}

@Composable
private fun namingTopLevelFolderModeDescription(mode: TopLevelFolderMode): String {
    return when (mode) {
        TopLevelFolderMode.Auto -> {
            stringResource(R.string.settings_naming_top_level_folder_mode_auto_desc)
        }
        TopLevelFolderMode.Enabled -> {
            stringResource(R.string.settings_naming_top_level_folder_mode_enabled_desc)
        }
        TopLevelFolderMode.Disabled -> {
            stringResource(R.string.settings_naming_top_level_folder_mode_disabled_desc)
        }
    }
}

@Composable
private fun namingTokenGroupLabel(group: NamingTokenGroup): String {
    return when (group) {
        NamingTokenGroup.General -> stringResource(R.string.settings_naming_group_general)
        NamingTokenGroup.Time -> stringResource(R.string.settings_naming_group_time)
        NamingTokenGroup.Ids -> stringResource(R.string.settings_naming_group_ids)
        NamingTokenGroup.Stream -> stringResource(R.string.settings_naming_group_stream)
    }
}

@Composable
private fun namingTokenButtonLabel(token: NamingToken): String {
    return stringResource(
        R.string.settings_naming_token_button_format,
        namingTokenPreviewLabel(token),
        token.key,
    )
}

@Composable
private fun namingTokenPreviewLabel(token: NamingToken): String {
    return when (token) {
        NamingToken.VideoTitle -> stringResource(R.string.settings_naming_token_video_title)
        NamingToken.CollectionTitle -> stringResource(R.string.settings_naming_token_collection_title)
        NamingToken.Title -> stringResource(R.string.settings_naming_token_title)
        NamingToken.P -> stringResource(R.string.settings_naming_token_p)
        NamingToken.Container -> stringResource(R.string.settings_naming_token_container)
        NamingToken.MediaType -> stringResource(R.string.settings_naming_token_media_type)
        NamingToken.TaskType -> stringResource(R.string.settings_naming_token_task_type)
        NamingToken.Index -> stringResource(R.string.settings_naming_token_index)
        NamingToken.PubTime -> stringResource(R.string.settings_naming_token_pub_time)
        NamingToken.DownTime -> stringResource(R.string.settings_naming_token_down_time)
        NamingToken.Upper -> stringResource(R.string.settings_naming_token_upper)
        NamingToken.UpperId -> stringResource(R.string.settings_naming_token_upper_id)
        NamingToken.Aid -> stringResource(R.string.settings_naming_token_aid)
        NamingToken.Sid -> stringResource(R.string.settings_naming_token_sid)
        NamingToken.Fid -> stringResource(R.string.settings_naming_token_fid)
        NamingToken.Cid -> stringResource(R.string.settings_naming_token_cid)
        NamingToken.Bvid -> stringResource(R.string.settings_naming_token_bvid)
        NamingToken.Epid -> stringResource(R.string.settings_naming_token_epid)
        NamingToken.Ssid -> stringResource(R.string.settings_naming_token_ssid)
        NamingToken.Opid -> stringResource(R.string.settings_naming_token_opid)
        NamingToken.Res -> stringResource(R.string.settings_naming_token_res)
        NamingToken.Abr -> stringResource(R.string.settings_naming_token_abr)
        NamingToken.Enc -> stringResource(R.string.settings_naming_token_enc)
        NamingToken.Fmt -> stringResource(R.string.settings_naming_token_fmt)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutSettingsScreen(
    versionName: String,
    versionCode: Long,
    checkUpdateSummary: String,
    issueReportState: IssueReportLogState,
    issueReportExporting: Boolean,
    issueReportClearing: Boolean,
    onCheckUpdate: () -> Unit,
    onIssueReportLoggingChange: (Boolean) -> Unit,
    onExportIssueReport: () -> Unit,
    onClearIssueReport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showLicense by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appIconPainter = painterResource(R.drawable.bilitools_app_icon)
    val iconBackgroundRotation by rememberInfiniteTransition(
        label = "aboutIconBackgroundRotation",
    ).animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 24000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aboutIconBackgroundRotationValue",
    )
    val issueReportActiveColor = MaterialTheme.colorScheme.error
    val issueReportSummary = remember(
        context,
        issueReportState,
        issueReportExporting,
        issueReportClearing,
        issueReportActiveColor,
    ) {
        buildIssueReportSummary(
            context = context,
            state = issueReportState,
            exporting = issueReportExporting,
            clearing = issueReportClearing,
            activeColor = issueReportActiveColor,
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_about_title),
        subtitle = stringResource(R.string.app_name),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            SettingsExpressiveDefaults.listItemColors.containerColor,
                            SettingsExpressiveShapes.cardShape,
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        AnimatedAboutAppIcon(
                            painter = appIconPainter,
                            backgroundRotation = iconBackgroundRotation,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "$versionName ($versionCode)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        FilledTonalIconButton(
                            onClick = { uriHandler.openUri("https://github.com/happycola233/BiliTools") },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_github_invertocat_black),
                                contentDescription = stringResource(R.string.settings_about_open_github),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                ClickableListItem(
                    items = 2,
                    index = 0,
                    leadingContent = { SettingsItemIcon(R.drawable.ic_update_24) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(R.string.settings_check_update_title))
                    },
                    supportingContent = {
                        Text(
                            text = checkUpdateSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = onCheckUpdate,
                )
            }

            item {
                ClickableListItem(
                    items = 2,
                    index = 1,
                    leadingContent = { SettingsItemIcon(R.drawable.ic_gavel_24) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(R.string.settings_license_title))
                    },
                    supportingContent = { Text(stringResource(R.string.settings_license_summary)) },
                    onClick = { showLicense = true },
                )
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                ExpressiveSwitchListItem(
                    checked = issueReportState.enabled,
                    iconRes = R.drawable.ic_troubleshoot_24,
                    title = stringResource(R.string.settings_issue_report_enabled_title),
                    description = stringResource(R.string.settings_issue_report_enabled_desc),
                    items = 3,
                    index = 0,
                    onCheckedChange = onIssueReportLoggingChange,
                )
            }

            item {
                ClickableListItem(
                    items = 3,
                    index = 1,
                    leadingContent = { SettingsItemIcon(R.drawable.ic_save_alt_24) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(R.string.settings_issue_report_export_title))
                    },
                    supportingContent = {
                        Text(
                            text = issueReportSummary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = onExportIssueReport,
                )
            }

            item {
                ClickableListItem(
                    items = 3,
                    index = 2,
                    leadingContent = { SettingsItemIcon(R.drawable.ic_delete_sweep_24) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(R.string.settings_issue_report_clear_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.settings_issue_report_clear_desc))
                    },
                    onClick = onClearIssueReport,
                )
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            SettingsExpressiveShapes.cardShape,
                        )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_disclaimer_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.settings_about_disclaimer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showLicense) {
        LicenseBottomSheet(
            title = stringResource(R.string.settings_license_title),
            licenseRawRes = R.raw.license_gpl3,
            onDismiss = { showLicense = false },
        )
    }
}

@Composable
private fun AnimatedAboutAppIcon(
    painter: Painter,
    backgroundRotation: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val appName = stringResource(R.string.app_name)
    val interactionSource = remember { MutableInteractionSource() }
    var hopRequest by remember { mutableStateOf(0) }
    val iconHopState = remember { AboutIconHopState() }

    LaunchedEffect(hopRequest) {
        if (hopRequest == 0) return@LaunchedEffect

        iconHopState.snapTo(aboutIconRestTarget)
        iconHopState.animateTo(
            target = aboutIconSquashTarget,
            durationMillis = BiliTvLaunchMotion.SQUASH_DURATION_MILLIS,
            easing = aboutIconStandardEasing,
        )
        iconHopState.animateTo(
            target = aboutIconJumpTarget,
            durationMillis = BiliTvLaunchMotion.JUMP_DURATION_MILLIS,
            easing = aboutIconEmphasizedEasing,
        )
        iconHopState.animateTo(
            target = aboutIconRestTarget,
            durationMillis = BiliTvLaunchMotion.SETTLE_DURATION_MILLIS,
            easing = aboutIconSettleEasing,
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = appName,
                role = Role.Button,
            ) {
                hopRequest += 1
            }
            .semantics { contentDescription = appName },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = backgroundRotation }
                .background(
                    color = Color.White,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                ),
        )
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    translationY = with(density) {
                        iconHopState.translationYDp.value.dp.toPx()
                    }
                    scaleX = iconHopState.scaleX.value
                    scaleY = iconHopState.scaleY.value
                    rotationZ = iconHopState.rotationDegrees.value
                },
        )
    }
}

private class AboutIconHopState {
    val translationYDp = Animatable(0f)
    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)
    val rotationDegrees = Animatable(0f)

    suspend fun snapTo(target: AboutIconHopTarget) {
        translationYDp.snapTo(target.translationYDp)
        scaleX.snapTo(target.scaleX)
        scaleY.snapTo(target.scaleY)
        rotationDegrees.snapTo(target.rotationDegrees)
    }

    suspend fun animateTo(
        target: AboutIconHopTarget,
        durationMillis: Int,
        easing: Easing,
    ) {
        val animationSpec = tween<Float>(
            durationMillis = durationMillis,
            easing = easing,
        )
        coroutineScope {
            launch { translationYDp.animateTo(target.translationYDp, animationSpec) }
            launch { scaleX.animateTo(target.scaleX, animationSpec) }
            launch { scaleY.animateTo(target.scaleY, animationSpec) }
            launch { rotationDegrees.animateTo(target.rotationDegrees, animationSpec) }
        }
    }
}

private data class AboutIconHopTarget(
    val translationYDp: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDegrees: Float,
)

private val aboutIconRestTarget = AboutIconHopTarget(
    translationYDp = 0f,
    scaleX = 1f,
    scaleY = 1f,
    rotationDegrees = 0f,
)

private val aboutIconSquashTarget = AboutIconHopTarget(
    translationYDp = BiliTvLaunchMotion.ICON_SQUASH_OFFSET_DP,
    scaleX = BiliTvLaunchMotion.ICON_SQUASH_SCALE_X,
    scaleY = BiliTvLaunchMotion.ICON_SQUASH_SCALE_Y,
    rotationDegrees = BiliTvLaunchMotion.ICON_SQUASH_ROTATION_DEGREES,
)

private val aboutIconJumpTarget = AboutIconHopTarget(
    translationYDp = BiliTvLaunchMotion.ICON_JUMP_OFFSET_DP,
    scaleX = BiliTvLaunchMotion.ICON_JUMP_SCALE_X,
    scaleY = BiliTvLaunchMotion.ICON_JUMP_SCALE_Y,
    rotationDegrees = BiliTvLaunchMotion.ICON_JUMP_ROTATION_DEGREES,
)

private val aboutIconStandardEasing = CubicBezierEasing(
    BiliTvLaunchMotion.STANDARD_EASE_X1,
    BiliTvLaunchMotion.STANDARD_EASE_Y1,
    BiliTvLaunchMotion.STANDARD_EASE_X2,
    BiliTvLaunchMotion.STANDARD_EASE_Y2,
)

private val aboutIconEmphasizedEasing = CubicBezierEasing(
    BiliTvLaunchMotion.EMPHASIZED_EASE_X1,
    BiliTvLaunchMotion.EMPHASIZED_EASE_Y1,
    BiliTvLaunchMotion.EMPHASIZED_EASE_X2,
    BiliTvLaunchMotion.EMPHASIZED_EASE_Y2,
)

private val aboutIconSettleEasing = Easing { fraction ->
    BiliTvLaunchMotion.settleOvershoot(fraction)
}

@Composable
private fun SettingsItemIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .wrapContentHeight(Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
        )
    }
}

@Composable
private fun SettingsItemTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
private fun defaultDownloadQualitySummary(quality: DefaultDownloadQualitySettings): String {
    val resolution = when (quality.resolutionMode) {
        DownloadQualityMode.Highest -> stringResource(R.string.settings_default_quality_resolution_highest)
        DownloadQualityMode.Lowest -> stringResource(R.string.settings_default_quality_resolution_lowest)
        DownloadQualityMode.Fixed -> stringResource(resolutionLabelRes(quality.fixedResolutionId))
    }
    val audio = when (quality.audioBitrateMode) {
        DownloadQualityMode.Highest -> stringResource(R.string.settings_default_quality_audio_highest)
        DownloadQualityMode.Lowest -> stringResource(R.string.settings_default_quality_audio_lowest)
        DownloadQualityMode.Fixed -> stringResource(AudioQualities.labelRes(quality.fixedAudioBitrateId))
    }
    return stringResource(
        R.string.settings_default_download_quality_summary_format,
        resolution,
        stringResource(codecLabelRes(quality.codec)),
        audio,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DefaultDownloadQualityScreen(
    settings: AppSettings,
    onDefaultDownloadQualityChange: (DefaultDownloadQualitySettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var draft by remember(settings.defaultDownloadQuality) {
        mutableStateOf(settings.defaultDownloadQuality)
    }
    val modeOptions = remember {
        listOf(
            QualityModeOption(DownloadQualityMode.Highest, R.string.settings_default_quality_mode_highest),
            QualityModeOption(DownloadQualityMode.Lowest, R.string.settings_default_quality_mode_lowest),
            QualityModeOption(DownloadQualityMode.Fixed, R.string.settings_default_quality_mode_fixed),
        )
    }
    val resolutionOptions = remember {
        SettingsRepository.DEFAULT_DOWNLOAD_RESOLUTION_IDS.map { id ->
            ResolutionOption(id, resolutionLabelRes(id))
        }
    }
    val codecOptions = remember {
        listOf(
            CodecQualityOption(DefaultDownloadVideoCodec.Avc, R.string.parse_codec_avc),
            CodecQualityOption(DefaultDownloadVideoCodec.Hevc, R.string.parse_codec_hevc),
            CodecQualityOption(DefaultDownloadVideoCodec.Av1, R.string.parse_codec_av1),
        )
    }
    val audioOptions = remember {
        AudioQualities.sortDescending(AudioQualities.allIds).map { id ->
            AudioBitrateOption(id, AudioQualities.labelRes(id))
        }
    }
    fun updateQuality(quality: DefaultDownloadQualitySettings) {
        draft = quality
        onDefaultDownloadQualityChange(quality)
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_default_download_quality),
        subtitle = stringResource(R.string.settings_general_title),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }

            item {
                DefaultQualityCard {
                    Text(
                        text = stringResource(R.string.settings_default_download_quality_dialog_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = defaultDownloadQualitySummary(draft),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            item {
                DefaultQualityCard {
                    DefaultQualityModeSection(
                        title = stringResource(R.string.settings_default_quality_resolution_title),
                        mode = draft.resolutionMode,
                        modeOptions = modeOptions,
                        fixedLabel = stringResource(R.string.settings_default_quality_resolution_fixed_label),
                        fixedDescription = stringResource(R.string.settings_default_quality_resolution_fixed_desc),
                        onModeChange = {
                            updateQuality(draft.copy(resolutionMode = it))
                        },
                    ) {
                        HorizontalConnectedToggleButtons(
                            options = resolutionOptions,
                            selected = resolutionOptions.firstOrNull { it.id == draft.fixedResolutionId }
                                ?: resolutionOptions.first(),
                            onSelect = {
                                updateQuality(
                                    draft.copy(
                                        resolutionMode = DownloadQualityMode.Fixed,
                                        fixedResolutionId = it.id,
                                    ),
                                )
                            },
                        ) { option ->
                            Text(
                                text = stringResource(option.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            item {
                DefaultQualityCard {
                    DefaultQualitySectionTitle(
                        title = stringResource(R.string.settings_default_quality_codec_title),
                    )
                    HorizontalConnectedToggleButtons(
                        options = codecOptions,
                        selected = codecOptions.firstOrNull { it.codec == draft.codec } ?: codecOptions.first(),
                        onSelect = {
                            updateQuality(draft.copy(codec = it.codec))
                        },
                    ) { option ->
                        Text(
                            text = stringResource(option.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_default_quality_codec_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                DefaultQualityCard {
                    DefaultQualityModeSection(
                        title = stringResource(R.string.settings_default_quality_audio_title),
                        mode = draft.audioBitrateMode,
                        modeOptions = modeOptions,
                        fixedLabel = stringResource(R.string.settings_default_quality_audio_fixed_label),
                        fixedDescription = stringResource(R.string.settings_default_quality_audio_fixed_desc),
                        onModeChange = {
                            updateQuality(draft.copy(audioBitrateMode = it))
                        },
                    ) {
                        HorizontalConnectedToggleButtons(
                            options = audioOptions,
                            selected = audioOptions.firstOrNull { it.id == draft.fixedAudioBitrateId }
                                ?: audioOptions.first(),
                            onSelect = {
                                updateQuality(
                                    draft.copy(
                                        audioBitrateMode = DownloadQualityMode.Fixed,
                                        fixedAudioBitrateId = it.id,
                                    ),
                                )
                            },
                        ) { option ->
                            Text(
                                text = stringResource(option.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun DefaultQualityCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = SettingsExpressiveDefaults.listItemContainerColor,
        shape = SettingsExpressiveShapes.cardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DefaultQualityModeSection(
    title: String,
    mode: DownloadQualityMode,
    modeOptions: List<QualityModeOption>,
    fixedLabel: String,
    fixedDescription: String,
    onModeChange: (DownloadQualityMode) -> Unit,
    fixedContent: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DefaultQualitySectionTitle(title)
        ConnectedToggleButtons(
            options = modeOptions,
            selected = modeOptions.firstOrNull { it.mode == mode } ?: modeOptions.first(),
            onSelect = { onModeChange(it.mode) },
        ) { option ->
            Text(
                text = stringResource(option.labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = mode == DownloadQualityMode.Fixed,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = fixedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                fixedContent()
                Text(
                    text = fixedDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DefaultQualitySectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedToggleButtons(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        options.fastForEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = connectedButtonShapes(index, options.lastIndex),
            ) {
                label(option)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> HorizontalConnectedToggleButtons(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(options.size) { index ->
            val option = options[index]
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier
                    .widthIn(min = 112.dp)
                    .semantics { role = Role.RadioButton },
                shapes = connectedButtonShapes(index, options.lastIndex),
            ) {
                label(option)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun connectedButtonShapes(index: Int, lastIndex: Int) = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

@StringRes
private fun resolutionLabelRes(id: Int): Int {
    return when (id) {
        127 -> R.string.parse_resolution_8k
        126 -> R.string.parse_resolution_dolby
        125 -> R.string.parse_resolution_hdr
        120 -> R.string.parse_resolution_4k
        116 -> R.string.parse_resolution_1080_60
        112 -> R.string.parse_resolution_1080_high
        80 -> R.string.parse_resolution_1080
        64 -> R.string.parse_resolution_720
        32 -> R.string.parse_resolution_480
        16 -> R.string.parse_resolution_360
        6 -> R.string.parse_resolution_240
        else -> R.string.parse_resolution_other
    }
}

@StringRes
private fun codecLabelRes(codec: DefaultDownloadVideoCodec): Int {
    return when (codec) {
        DefaultDownloadVideoCodec.Avc -> R.string.parse_codec_avc
        DefaultDownloadVideoCodec.Hevc -> R.string.parse_codec_hevc
        DefaultDownloadVideoCodec.Av1 -> R.string.parse_codec_av1
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemePickerListItem(
    mode: AppThemeMode,
    items: Int,
    index: Int,
    onThemeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember {
        listOf(
            ThemeOption(
                mode = AppThemeMode.System,
                iconRes = R.drawable.ic_brightness_auto_24,
                labelRes = R.string.settings_theme_system,
            ),
            ThemeOption(
                mode = AppThemeMode.Light,
                iconRes = R.drawable.ic_light_mode_24,
                labelRes = R.string.settings_theme_light,
            ),
            ThemeOption(
                mode = AppThemeMode.Dark,
                iconRes = R.drawable.ic_dark_mode_24,
                labelRes = R.string.settings_theme_dark,
            ),
        )
    }

    Column(
        modifier = modifier.clip(SettingsExpressiveShapes.groupShape(index, items)),
    ) {
        ListItem(
            leadingContent = {
                AnimatedContent(targetState = options.first { it.mode == mode }.iconRes) { iconRes ->
                    SettingsItemIcon(iconRes)
                }
            },
            headlineContent = {
                SettingsItemTitle(stringResource(R.string.settings_theme))
            },
            colors = SettingsExpressiveDefaults.listItemColors,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier
                .background(SettingsExpressiveDefaults.listItemColors.containerColor)
                .padding(start = 52.dp, end = 16.dp, bottom = 8.dp),
        ) {
            options.fastForEachIndexed { optionIndex, option ->
                ToggleButton(
                    checked = option.mode == mode,
                    onCheckedChange = { onThemeChange(option.mode) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (optionIndex) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSchemePickerListItem(
    color: AppThemeColor,
    items: Int,
    index: Int,
    onColorChange: (AppThemeColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember {
        listOf(
            ColorSchemeOption(AppThemeColor.Coral, Color(0xFFFEB4A7)),
            ColorSchemeOption(AppThemeColor.Rose, Color(0xFFFFB3C0)),
            ColorSchemeOption(AppThemeColor.Orchid, Color(0xFFFCAAFF)),
            ColorSchemeOption(AppThemeColor.Periwinkle, Color(0xFFB9C3FF)),
            ColorSchemeOption(AppThemeColor.Sky, Color(0xFF62D3FF)),
            ColorSchemeOption(AppThemeColor.Cyan, Color(0xFF44D9F1)),
            ColorSchemeOption(AppThemeColor.Turquoise, Color(0xFF52DBC9)),
            ColorSchemeOption(AppThemeColor.Leaf, Color(0xFF78DD77)),
            ColorSchemeOption(AppThemeColor.Lime, Color(0xFF9FD75C)),
            ColorSchemeOption(AppThemeColor.Olive, Color(0xFFC1D02D)),
            ColorSchemeOption(AppThemeColor.Gold, Color(0xFFFABD00)),
            ColorSchemeOption(AppThemeColor.Apricot, Color(0xFFFFB86E)),
        )
    }
    val zeroCorner = remember { CornerSize(0) }

    Column(
        modifier = modifier.clip(SettingsExpressiveShapes.groupShape(index, items)),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItem(
                leadingContent = {
                    SettingsItemIcon(R.drawable.ic_colors_24)
                },
                headlineContent = {
                    SettingsItemTitle(stringResource(R.string.settings_dynamic_color_title))
                },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_desc)) },
                trailingContent = {
                    val checked = color == AppThemeColor.Dynamic
                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            onColorChange(if (it) AppThemeColor.Dynamic else AppThemeColor.Lime)
                        },
                        thumbContent = {
                            Icon(
                                painter = painterResource(
                                    if (checked) R.drawable.ic_check_rounded_24 else R.drawable.ic_close_rounded_24,
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        },
                        colors = SettingsExpressiveDefaults.switchColors,
                    )
                },
                colors = SettingsExpressiveDefaults.listItemColors,
                modifier = Modifier.clip(SettingsExpressiveShapes.middleListItemShape),
            )
            Spacer(Modifier.height(2.dp))
        }

        ListItem(
            leadingContent = {
                SettingsItemIcon(R.drawable.ic_palette_24)
            },
            headlineContent = {
                SettingsItemTitle(stringResource(R.string.settings_color_scheme_title))
            },
            supportingContent = {
                Text(
                    stringResource(
                        if (color == AppThemeColor.Dynamic) {
                            R.string.settings_color_scheme_dynamic
                        } else {
                            R.string.settings_color_scheme_custom
                        }
                    )
                )
            },
            colors = SettingsExpressiveDefaults.listItemColors,
            modifier = Modifier.clip(
                RoundedCornerShape(
                    topStart = SettingsExpressiveShapes.middleListItemShape.topStart,
                    topEnd = SettingsExpressiveShapes.middleListItemShape.topEnd,
                    bottomStart = zeroCorner,
                    bottomEnd = zeroCorner,
                )
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SettingsExpressiveDefaults.listItemColors.containerColor)
                .padding(bottom = 8.dp),
        ) {
            LazyRow(
                contentPadding = PaddingValues(end = 48.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp)
                    .clipToBounds(),
            ) {
                items(options) { option ->
                    ColorPickerButton(
                        color = option.seedColor,
                        isSelected = option.themeColor == color,
                        modifier = Modifier.padding(4.dp),
                        onClick = { onColorChange(option.themeColor) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorPickerButton(
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.iconButtonColors(containerColor = color),
        modifier = modifier.size(48.dp),
        onClick = onClick,
    ) {
        AnimatedContent(targetState = isSelected) { selected ->
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_24),
                    contentDescription = null,
                    tint = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun ExpressiveSwitchListItem(
    checked: Boolean,
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    enabled: Boolean = true,
    items: Int,
    index: Int,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        leadingContent = { SettingsItemIcon(iconRes) },
        headlineContent = { SettingsItemTitle(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        painter = painterResource(
                            if (checked) R.drawable.ic_check_rounded_24 else R.drawable.ic_close_rounded_24,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
                colors = SettingsExpressiveDefaults.switchColors,
            )
        },
        colors = SettingsExpressiveDefaults.listItemColors,
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.48f }
            .clip(SettingsExpressiveShapes.groupShape(index, items)),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClickableListItem(
    items: Int,
    index: Int,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = SettingsExpressiveDefaults.listItemColors,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val top by animateDpAsState(
        targetValue = if (isPressed) 40.dp else if (items == 1 || index == 0) 20.dp else 4.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "settingsListItemTop",
    )
    val bottom by animateDpAsState(
        targetValue = if (isPressed) 40.dp else if (items == 1 || index == items - 1) 20.dp else 4.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "settingsListItemBottom",
    )

    ListItem(
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = colors,
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = top,
                    topEnd = top,
                    bottomStart = bottom,
                    bottomEnd = bottom,
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LicenseBottomSheet(
    title: String,
    @RawRes licenseRawRes: Int,
    onDismiss: () -> Unit,
) {
    val licenseText = rememberRawTextResource(licenseRawRes)
    val licenseHorizontalScrollState = rememberScrollState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }
            item {
                SelectionContainer {
                    Text(
                        text = licenseText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(licenseHorizontalScrollState),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberRawTextResource(@RawRes rawRes: Int): String {
    val context = LocalContext.current
    return remember(rawRes) {
        context.resources.openRawResource(rawRes).bufferedReader().use { it.readText() }
    }
}

private fun buildIssueReportSummary(
    context: android.content.Context,
    state: IssueReportLogState,
    exporting: Boolean,
    clearing: Boolean,
    activeColor: Color,
) = buildAnnotatedString {
    if (exporting) {
        append(context.getString(R.string.settings_issue_report_export_running))
        return@buildAnnotatedString
    }
    if (clearing) {
        append(context.getString(R.string.settings_issue_report_clear_running))
        return@buildAnnotatedString
    }

    val sizeLabel = Formatter.formatShortFileSize(context, state.totalBytes)
    if (state.enabled) {
        val enabledSince = formatIssueReportTimestamp(state.loggingStartedAtMillis)
        pushStyle(
            SpanStyle(
                color = activeColor,
                fontWeight = FontWeight.Bold,
            ),
        )
        append(context.getString(R.string.settings_issue_report_status_enabled))
        pop()
        if (enabledSince != null) {
            append(
                context.getString(
                    R.string.settings_issue_report_status_enabled_since_suffix,
                    enabledSince,
                ),
            )
        } else {
            append('。')
        }
    } else {
        append(context.getString(R.string.settings_issue_report_status_disabled))
    }
    append('\n')
    append(
        context.getString(
            R.string.settings_issue_report_status_files,
            state.fileCount,
            sizeLabel,
        ),
    )
    state.latestLogAtMillis?.let { latest ->
        formatIssueReportTimestamp(latest)?.let { label ->
            append('\n')
            append(context.getString(R.string.settings_issue_report_status_last_capture, label))
        }
    }
    state.lastExportedAtMillis?.let { exported ->
        formatIssueReportTimestamp(exported)?.let { label ->
            append('\n')
            append(context.getString(R.string.settings_issue_report_status_last_export, label))
        }
    }
}

private fun formatIssueReportTimestamp(epochMillis: Long?): String? {
    if (epochMillis == null || epochMillis <= 0L) return null
    return ISSUE_REPORT_TIME_FORMATTER.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsExpressiveDefaults.pageContainerColor),
    ) {
        androidx.compose.material3.Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    subtitle = { Text(subtitle) },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBack,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = SettingsExpressiveDefaults.listItemContainerColor,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back_24),
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                    colors = SettingsExpressiveDefaults.topBarColors,
                    scrollBehavior = scrollBehavior,
                )
            },
            containerColor = SettingsExpressiveDefaults.pageContainerColor,
            modifier = modifier
                .widthIn(max = SettingsExpressiveShapes.paneMaxWidth)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) { innerPadding ->
            content(innerPadding)
        }
    }
}

private object SettingsExpressiveDefaults {
    val pageContainerColor: Color
        @Composable
        get() = if (!MaterialTheme.colorScheme.usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    val listItemContainerColor: Color
        @Composable
        get() = if (!MaterialTheme.colorScheme.usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceBright
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val topBarColors: TopAppBarColors
        @Composable
        get() = TopAppBarDefaults.topAppBarColors(
            containerColor = pageContainerColor,
            scrolledContainerColor = pageContainerColor,
        )

    val listItemColors: ListItemColors
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        get() = ListItemDefaults.segmentedColors(
            containerColor = listItemContainerColor,
        )

    val switchColors: SwitchColors
        @Composable
        get() = SwitchDefaults.colors(
            checkedIconColor = MaterialTheme.colorScheme.primary,
        )
}

private fun androidx.compose.material3.ColorScheme.usesPureBlackSurfaces(): Boolean {
    return surface == Color.Black && background == Color.Black
}

private object SettingsExpressiveShapes {
    val topListItemShape: RoundedCornerShape
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        get() = RoundedCornerShape(
            topStart = MaterialTheme.shapes.largeIncreased.topStart,
            topEnd = MaterialTheme.shapes.largeIncreased.topEnd,
            bottomStart = MaterialTheme.shapes.extraSmall.bottomStart,
            bottomEnd = MaterialTheme.shapes.extraSmall.bottomEnd,
        )

    val middleListItemShape: RoundedCornerShape
        @Composable
        get() = RoundedCornerShape(MaterialTheme.shapes.extraSmall.topStart)

    val bottomListItemShape: RoundedCornerShape
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        get() = RoundedCornerShape(
            topStart = MaterialTheme.shapes.extraSmall.topStart,
            topEnd = MaterialTheme.shapes.extraSmall.topEnd,
            bottomStart = MaterialTheme.shapes.largeIncreased.bottomStart,
            bottomEnd = MaterialTheme.shapes.largeIncreased.bottomEnd,
        )

    val cardShape: CornerBasedShape
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable get() = MaterialTheme.shapes.largeIncreased

    val paneMaxWidth = 600.dp

    @Composable
    fun groupShape(index: Int, items: Int): CornerBasedShape {
        return when {
            items <= 1 -> cardShape
            index == 0 -> topListItemShape
            index == items - 1 -> bottomListItemShape
            else -> middleListItemShape
        }
    }
}

private val ISSUE_REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd HH:mm",
    Locale.ROOT,
)
