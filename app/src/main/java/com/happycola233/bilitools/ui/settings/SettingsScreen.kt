package com.happycola233.bilitools.ui.settings

import android.os.Build
import android.text.format.Formatter
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import com.happycola233.bilitools.core.naming.NamingContext
import com.happycola233.bilitools.core.naming.NamingPreviewSegment
import com.happycola233.bilitools.core.naming.NamingRenderer
import com.happycola233.bilitools.core.naming.NamingSegmentKind
import com.happycola233.bilitools.core.naming.NamingShape
import com.happycola233.bilitools.core.naming.NamingTemplateScope
import com.happycola233.bilitools.core.naming.NamingTemplateSource
import com.happycola233.bilitools.core.naming.NamingToken
import com.happycola233.bilitools.core.naming.NamingTokenGroup
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.DefaultDownloadQualitySettings
import com.happycola233.bilitools.data.DefaultDownloadVideoCodec
import com.happycola233.bilitools.data.DownloadQualityMode
import com.happycola233.bilitools.data.HapticFeedbackLevel
import com.happycola233.bilitools.data.IssueReportLogState
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.data.TopLevelFolderMode
import com.happycola233.bilitools.ui.BiliTvLaunchMotion
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.displayNameRes
import com.happycola233.bilitools.ui.overlayStyleResOrNull
import com.happycola233.bilitools.ui.resolveOverlaySwatch
import com.happycola233.bilitools.ui.theme.AppAccents
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsFonts
import com.happycola233.bilitools.ui.theme.BiliToolsSettingsTheme
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
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
    onConvertAudioToMp3Change: (Boolean) -> Unit,
    onConvertVideoToMp4Change: (Boolean) -> Unit,
    onMaxConcurrentDownloadsChange: (Int) -> Unit,
    onConfirmCellularChange: (Boolean) -> Unit,
    onHideInAlbumChange: (Boolean) -> Unit,
    onNamingTopLevelFolderModeChange: (TopLevelFolderMode) -> Unit,
    onNamingOverwriteExistingFilesChange: (Boolean) -> Unit,
    onNamingCleanSeparatorsChange: (Boolean) -> Unit,
    onNamingShowSinglePageNumberChange: (Boolean) -> Unit,
    onNamingTemplateChange: (NamingShape, NamingTemplateScope, String) -> Unit,
    onNamingTemplateReset: (NamingShape, NamingTemplateScope) -> Unit,
    onRestoreNamingDefaults: () -> Unit,
    onBlackThemeChange: (Boolean) -> Unit,
    onLaunchSplashAnimationChange: (Boolean) -> Unit,
    onLiquidBottomTabsChange: (Boolean) -> Unit,
    onLiquidBarWidthChange: (Float) -> Unit,
    onHapticFeedbackLevelChange: (HapticFeedbackLevel) -> Unit,
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
                        onHapticFeedbackLevelChange = onHapticFeedbackLevelChange,
                        onLaunchSplashAnimationChange = onLaunchSplashAnimationChange,
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
                        onConvertAudioToMp3Change = onConvertAudioToMp3Change,
                        onConvertVideoToMp4Change = onConvertVideoToMp4Change,
                        onMaxConcurrentDownloadsChange = onMaxConcurrentDownloadsChange,
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
                        onShowSinglePageNumberChange = onNamingShowSinglePageNumberChange,
                        onTemplateChange = onNamingTemplateChange,
                        onTemplateReset = onNamingTemplateReset,
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
                        onLiquidBottomTabsChange = onLiquidBottomTabsChange,
                        onLiquidBarWidthChange = onLiquidBarWidthChange,
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
                        onOpenSourceLicenses = {
                            onNavigate(SettingsDestination.OpenSourceLicenses)
                        },
                        onIssueReportLoggingChange = onIssueReportLoggingChange,
                        onExportIssueReport = onExportIssueReport,
                        onClearIssueReport = onClearIssueReport,
                        onBack = onNavigateBack,
                        modifier = modifier,
                    )
                }

                entry<SettingsDestination.OpenSourceLicenses> {
                    OpenSourceLicensesScreen(
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
    val fillColor: Color,
    val onFillColor: Color,
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
    onHapticFeedbackLevelChange: (HapticFeedbackLevel) -> Unit,
    onLaunchSplashAnimationChange: (Boolean) -> Unit,
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
                    items = 4,
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
                    items = 4,
                    index = 1,
                    onCheckedChange = onLiveActivityStyleNotificationChange,
                )
            }
            item {
                HapticFeedbackPickerListItem(
                    level = settings.hapticFeedbackLevel,
                    items = 4,
                    index = 2,
                    onLevelChange = onHapticFeedbackLevelChange,
                )
            }
            item {
                ExpressiveSwitchListItem(
                    checked = settings.launchSplashAnimationEnabled,
                    iconRes = R.drawable.ic_animation_24,
                    title = stringResource(R.string.settings_launch_splash_animation),
                    description = stringResource(R.string.settings_launch_splash_animation_desc),
                    items = 4,
                    index = 3,
                    onCheckedChange = onLaunchSplashAnimationChange,
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
    onConvertAudioToMp3Change: (Boolean) -> Unit,
    onConvertVideoToMp4Change: (Boolean) -> Unit,
    onMaxConcurrentDownloadsChange: (Int) -> Unit,
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
                MaxConcurrentDownloadsListItem(
                    value = settings.maxConcurrentDownloads,
                    onValueChange = onMaxConcurrentDownloadsChange,
                )
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                ClickableListItem(
                    items = 2,
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
                    items = 2,
                    index = 1,
                    onCheckedChange = onAddMetadataChange,
                )
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.convertXmlDanmakuToAss,
                    iconRes = R.drawable.ic_transform_24,
                    title = stringResource(R.string.settings_convert_xml_danmaku_to_ass),
                    description = stringResource(R.string.settings_convert_xml_danmaku_to_ass_desc),
                    items = 3,
                    index = 0,
                    onCheckedChange = onConvertXmlDanmakuToAssChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.convertAudioToMp3,
                    iconRes = R.drawable.ic_mp3_24,
                    title = stringResource(R.string.settings_convert_audio_to_mp3),
                    description = stringResource(R.string.settings_convert_audio_to_mp3_desc),
                    items = 3,
                    index = 1,
                    onCheckedChange = onConvertAudioToMp3Change,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.convertVideoToMp4,
                    iconRes = R.drawable.ic_mp4_24,
                    title = stringResource(R.string.settings_convert_video_to_mp4),
                    description = stringResource(R.string.settings_convert_video_to_mp4_desc),
                    items = 3,
                    index = 2,
                    onCheckedChange = onConvertVideoToMp4Change,
                )
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.confirmCellularDownload,
                    iconRes = R.drawable.ic_cell_tower_24,
                    title = stringResource(R.string.settings_confirm_cellular),
                    description = stringResource(R.string.settings_confirm_cellular_desc),
                    items = 2,
                    index = 0,
                    onCheckedChange = onConfirmCellularChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.hideDownloadedVideosInSystemAlbum,
                    iconRes = R.drawable.ic_hide_image_24,
                    title = stringResource(R.string.settings_hide_download_video_in_system_album),
                    description = stringResource(R.string.settings_hide_download_video_in_system_album_desc),
                    items = 2,
                    index = 1,
                    onCheckedChange = onHideInAlbumChange,
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaxConcurrentDownloadsListItem(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember {
        (SettingsRepository.MIN_MAX_CONCURRENT_DOWNLOADS..SettingsRepository.MAX_MAX_CONCURRENT_DOWNLOADS)
            .toList()
    }
    Column(
        modifier = modifier.clip(SettingsExpressiveShapes.groupShape(index = 0, items = 1)),
    ) {
        ListItem(
            leadingContent = {
                SettingsItemIcon(R.drawable.ic_arrow_shape_up_stack_2_24)
            },
            supportingContent = {
                Text(stringResource(R.string.settings_max_concurrent_downloads_desc))
            },
            colors = SettingsExpressiveDefaults.listItemColors,
        ) {
            SettingsItemTitle(stringResource(R.string.settings_max_concurrent_downloads))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier
                .fillMaxWidth()
                .background(SettingsExpressiveDefaults.listItemColors.containerColor)
                .padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
        ) {
            options.fastForEachIndexed { index, option ->
                ToggleButton(
                    checked = option == value,
                    onCheckedChange = { checked ->
                        if (checked) onValueChange(option)
                    },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = AppAccents.toggleButtonColors(),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                ) {
                    Text(option.toString())
                }
            }
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
    onLiquidBottomTabsChange: (Boolean) -> Unit,
    onLiquidBarWidthChange: (Float) -> Unit,
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
                    items = 3,
                    index = 0,
                    onThemeChange = onThemeModeChange,
                )
            }

            item {
                ColorSchemePickerListItem(
                    color = settings.themeColor,
                    items = 3,
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
                    items = 3,
                    index = 2,
                    onCheckedChange = onBlackThemeChange,
                )
            }

            item { Spacer(Modifier.height(12.dp)) }

            // 液态玻璃相关设置独立一组；开启液态底栏时追加「底栏宽度」滑条项
            val liquidBarWidthVisible = settings.liquidBottomTabsEnabled
            val liquidGroupItems = if (liquidBarWidthVisible) 3 else 2

            item {
                ExpressiveSwitchListItem(
                    checked = settings.liquidBottomTabsEnabled,
                    iconRes = R.drawable.ic_bottom_navigation_24,
                    title = stringResource(R.string.settings_liquid_bottom_tabs),
                    description = stringResource(R.string.settings_liquid_bottom_tabs_desc),
                    items = liquidGroupItems,
                    index = 0,
                    onCheckedChange = onLiquidBottomTabsChange,
                )
            }

            if (liquidBarWidthVisible) {
                item {
                    ExpressiveSliderListItem(
                        value = settings.liquidBarWidthFraction,
                        valueRange =
                            SettingsRepository.MIN_LIQUID_BAR_WIDTH_FRACTION..1f,
                        steps = 7,
                        iconRes = R.drawable.ic_width_24,
                        title = stringResource(R.string.settings_liquid_bar_width),
                        description = stringResource(R.string.settings_liquid_bar_width_desc),
                        valueLabel = "${(settings.liquidBarWidthFraction * 100).roundToInt()}%",
                        items = liquidGroupItems,
                        index = 1,
                        onValueChange = onLiquidBarWidthChange,
                    )
                }
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.downloadsGlassDebugEnabled,
                    iconRes = R.drawable.ic_blur_on_24,
                    title = stringResource(R.string.settings_downloads_glass_debug),
                    description = stringResource(R.string.settings_downloads_glass_debug_desc),
                    items = liquidGroupItems,
                    index = if (liquidBarWidthVisible) 2 else 1,
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
    onShowSinglePageNumberChange: (Boolean) -> Unit,
    onTemplateChange: (NamingShape, NamingTemplateScope, String) -> Unit,
    onTemplateReset: (NamingShape, NamingTemplateScope) -> Unit,
    onRestoreDefaults: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val previewContexts = rememberNamingPreviewContexts()
    var showRestoreDefaultsConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var selectedShapeName by rememberSaveable { mutableStateOf(NamingShape.Video.name) }

    val showTopLevelFolderTemplate =
        settings.naming.topLevelFolderMode != TopLevelFolderMode.Disabled
    val shapeOptions = remember(showTopLevelFolderTemplate) {
        NamingShape.entries.filter { showTopLevelFolderTemplate || it != NamingShape.Listing }
    }
    val selectedShape = shapeOptions
        .firstOrNull { it.name == selectedShapeName }
        ?: NamingShape.Video

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
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ExpressiveSwitchListItem(
                        checked = settings.naming.overwriteExistingFiles,
                        iconRes = R.drawable.ic_file_save_24,
                        title = stringResource(R.string.settings_naming_overwrite_existing),
                        description = stringResource(R.string.settings_naming_overwrite_existing_desc),
                        items = 3,
                        index = 0,
                        onCheckedChange = onOverwriteExistingFilesChange,
                    )
                    ExpressiveSwitchListItem(
                        checked = settings.naming.cleanSeparators,
                        iconRes = R.drawable.ic_wand_shine_24,
                        title = stringResource(R.string.settings_naming_clean_separators),
                        description = stringResource(R.string.settings_naming_clean_separators_desc),
                        items = 3,
                        index = 1,
                        onCheckedChange = onCleanSeparatorsChange,
                    )
                    ExpressiveSwitchListItem(
                        checked = settings.naming.showSinglePageNumber,
                        iconRes = R.drawable.ic_format_list_bulleted_24,
                        title = stringResource(R.string.settings_naming_single_page_number),
                        description = stringResource(R.string.settings_naming_single_page_number_desc),
                        items = 3,
                        index = 2,
                        onCheckedChange = onShowSinglePageNumberChange,
                    )
                }
            }

            item {
                TopLevelFolderModeListItem(
                    mode = settings.naming.topLevelFolderMode,
                    items = 1,
                    index = 0,
                    onModeChange = onTopLevelFolderModeChange,
                )
            }

            // 形态选择器与它控制的三层模板串成一组，视觉上是同一块内容。
            item {
                val scopes = buildList {
                    if (showTopLevelFolderTemplate) add(NamingTemplateScope.TopFolder)
                    if (NamingTemplateScope.ItemFolder in selectedShape.supportedScopes) {
                        add(NamingTemplateScope.ItemFolder)
                    }
                    if (NamingTemplateScope.File in selectedShape.supportedScopes) {
                        add(NamingTemplateScope.File)
                    }
                }
                val groupItems = scopes.size + 1
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NamingShapeSelectorCard(
                        shapes = shapeOptions,
                        selected = selectedShape,
                        customizedShapes = settings.naming.overrides.keys,
                        onSelect = { selectedShapeName = it.name },
                        containerShape = SettingsExpressiveShapes.groupShape(0, groupItems),
                    )
                    scopes.fastForEachIndexed { index, scope ->
                        NamingTemplateEditorPanel(
                            shape = selectedShape,
                            scope = scope,
                            value = settings.naming.template(selectedShape, scope),
                            source = settings.naming.templateSource(selectedShape, scope),
                            previewContext = previewContexts.getValue(selectedShape),
                            previewExtension = namingPreviewExtension(selectedShape, scope),
                            cleanSeparators = settings.naming.cleanSeparators,
                            containerShape = SettingsExpressiveShapes.groupShape(
                                index = index + 1,
                                items = groupItems,
                            ),
                            onValueChange = { onTemplateChange(selectedShape, scope, it) },
                            onReset = { onTemplateReset(selectedShape, scope) },
                        )
                    }
                }
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
                    colors = AppAccents.toggleButtonColors(),
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

/** 形态选择器：切换正在编辑哪一类资源的模板，同时标出哪些类型已经被单独设置过。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NamingShapeSelectorCard(
    shapes: List<NamingShape>,
    selected: NamingShape,
    customizedShapes: Set<NamingShape>,
    onSelect: (NamingShape) -> Unit,
    containerShape: CornerBasedShape,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AppSurfaces.cardContainerColor,
        shape = containerShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            ListItem(
                leadingContent = { SettingsItemIcon(R.drawable.ic_tune_24) },
                headlineContent = {
                    SettingsItemTitle(stringResource(R.string.settings_naming_shape_title))
                },
                supportingContent = {
                    Text(namingShapeDescription(selected))
                },
                colors = SettingsExpressiveDefaults.listItemColors,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                items(shapes, key = { it.name }) { shape ->
                    NamingShapeChip(
                        text = namingShapeLabel(shape),
                        selected = shape == selected,
                        customized = shape in customizedShapes,
                        onSelect = { onSelect(shape) },
                    )
                }
            }
        }
    }
}

private val NAMING_SHAPE_CHIP_HEIGHT = 44.dp

/** 按压时圆角收成方角、松开弹回，是 M3 Expressive 里给选择器的标准反馈。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NamingShapeChip(
    text: String,
    selected: Boolean,
    customized: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    ToggleButton(
        checked = selected,
        onCheckedChange = { checked ->
            if (checked) {
                haptics.select()
                onSelect()
            }
        },
        shapes = ToggleButtonDefaults.shapesFor(NAMING_SHAPE_CHIP_HEIGHT),
        colors = AppAccents.toggleButtonColors(),
        contentPadding = PaddingValues(horizontal = 18.dp),
        modifier = modifier
            .height(NAMING_SHAPE_CHIP_HEIGHT)
            .semantics { role = Role.RadioButton },
    ) {
        Text(text = text, maxLines = 1)
        if (customized) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LocalContentColor.current),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NamingTemplateEditorPanel(
    shape: NamingShape,
    scope: NamingTemplateScope,
    value: String,
    source: NamingTemplateSource,
    previewContext: NamingContext,
    previewExtension: String?,
    cleanSeparators: Boolean,
    containerShape: CornerBasedShape,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(scope.name) { mutableStateOf(false) }
    var textFieldValue by remember(shape, scope) {
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
        NamingRenderer.previewSegments(textFieldValue.text)
    }
    val previewValue = remember(
        textFieldValue.text,
        previewContext,
        previewExtension,
        cleanSeparators,
    ) {
        val rendered = NamingRenderer.renderComponent(
            template = textFieldValue.text,
            context = previewContext,
            cleanSeparators = cleanSeparators,
        )
        previewExtension?.let {
            NamingRenderer.appendExtension(
                baseName = rendered,
                extension = it,
                cleanSeparators = cleanSeparators,
            )
        } ?: rendered
    }
    val tokenSections = remember(shape, scope) { namingTokenSections(shape, scope) }
    val interactionSource = remember { MutableInteractionSource() }
    val expandedRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = 180,
            easing = FastOutSlowInEasing,
        ),
        label = "${scope.name}ChevronRotation",
    )

    Surface(
        color = AppSurfaces.cardContainerColor,
        shape = containerShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            ListItem(
                leadingContent = { SettingsItemIcon(namingScopeIcon(scope)) },
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SettingsItemTitle(stringResource(namingScopeTitleRes(scope)))
                        NamingTemplateSourceBadge(source = source)
                    }
                },
                supportingContent = {
                    Text(
                        text = stringResource(namingScopeDescriptionRes(scope)),
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
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            onValueChange(it.text)
                        },
                        label = {
                            Text(stringResource(R.string.settings_naming_template_editor_label))
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))
                    NamingTemplatePreviewCard(
                        segments = previewSegments,
                        renderedName = previewValue,
                        emptyHint = stringResource(R.string.settings_naming_empty_template_hint),
                    )

                    if (source == NamingTemplateSource.Custom) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onReset,
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh_24),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.settings_naming_clear_custom))
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.settings_naming_token_usage_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.settings_naming_group_optional),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_naming_group_optional_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    NamingTokenChip(
                        text = stringResource(R.string.settings_naming_optional_chip),
                        accent = true,
                        onClick = {
                            val wrapped = wrapSelectionAsOptional(textFieldValue)
                            textFieldValue = wrapped
                            onValueChange(wrapped.text)
                        },
                    )

                    tokenSections.forEach { section ->
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = namingTokenGroupLabel(section.group),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        namingTokenGroupDescription(section.group)?.let { description ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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

@Composable
private fun NamingTemplateSourceBadge(
    source: NamingTemplateSource,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (source) {
        NamingTemplateSource.Custom -> scheme.primaryContainer to scheme.onPrimaryContainer
        NamingTemplateSource.Default -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    val labelRes = when (source) {
        NamingTemplateSource.Custom -> R.string.settings_naming_source_custom
        NamingTemplateSource.Default -> R.string.settings_naming_source_default
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NamingTokenChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "namingTokenChipScale",
    )
    val restingContainer = if (accent) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val restingContent = if (accent) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val containerColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            restingContainer
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "namingTokenChipContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            restingContent
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

/**
 * 模板的两种预览合并成一张卡片：上半部分展示模板结构，下半部分展示套用示例数据后的实际名称。
 */
@Composable
private fun NamingTemplatePreviewCard(
    segments: List<NamingPreviewSegment>,
    renderedName: String,
    emptyHint: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AppSurfaces.insetContainerColor,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (segments.isEmpty()) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                NamingPreviewSectionLabel(
                    text = stringResource(R.string.settings_naming_preview_structure),
                )
                Spacer(Modifier.height(6.dp))
                NamingTemplateStructurePreview(segments = segments)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                NamingPreviewSectionLabel(
                    text = stringResource(R.string.settings_naming_preview_result),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = renderedName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun NamingPreviewSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** 把模板拆成一串彩色标签，直观地看出哪些位置会被变量替换、哪些片段是可选的。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NamingTemplateStructurePreview(
    segments: List<NamingPreviewSegment>,
    modifier: Modifier = Modifier,
) {
    val optionalColor = MaterialTheme.colorScheme.tertiary
    val tokenContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val tokenContentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val optionalContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    val optionalContentColor = MaterialTheme.colorScheme.onTertiaryContainer
    val invalidContainerColor = MaterialTheme.colorScheme.errorContainer
    val invalidContentColor = MaterialTheme.colorScheme.onErrorContainer
    val annotatedText = buildAnnotatedString {
        segments.fastForEachIndexed { index, segment ->
            when (segment.kind) {
                NamingSegmentKind.Token,
                NamingSegmentKind.Unknown,
                -> appendInlineContent(
                    id = "naming_preview_$index",
                    alternateText = segment.token
                        ?.let { namingTokenPreviewLabel(it) }
                        ?: segment.raw,
                )

                NamingSegmentKind.OptionalStart -> withStyle(
                    SpanStyle(color = optionalColor, fontWeight = FontWeight.Bold),
                ) { append("⟨") }

                NamingSegmentKind.OptionalEnd -> withStyle(
                    SpanStyle(color = optionalColor, fontWeight = FontWeight.Bold),
                ) { append("⟩") }

                NamingSegmentKind.Literal -> append(segment.raw)
            }
        }
    }
    val inlineContent = buildMap<String, InlineTextContent> {
        segments.fastForEachIndexed { index, segment ->
            if (segment.kind != NamingSegmentKind.Token &&
                segment.kind != NamingSegmentKind.Unknown
            ) {
                return@fastForEachIndexed
            }
            val label = segment.token?.let { namingTokenPreviewLabel(it) } ?: segment.raw
            val container = when {
                segment.kind == NamingSegmentKind.Unknown -> invalidContainerColor
                segment.optional -> optionalContainerColor
                else -> tokenContainerColor
            }
            val content = when {
                segment.kind == NamingSegmentKind.Unknown -> invalidContentColor
                segment.optional -> optionalContentColor
                else -> tokenContentColor
            }
            put(
                key = "naming_preview_$index",
                value = InlineTextContent(
                    placeholder = Placeholder(
                        width = namingPreviewChipWidthEm(label).em,
                        height = 1.7.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    ),
                ) { _ ->
                    NamingPreviewInlineChip(
                        text = label,
                        containerColor = container,
                        contentColor = content,
                    )
                },
            )
        }
    }
    BasicText(
        text = annotatedText,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 2.0.em,
        ),
        modifier = modifier.fillMaxWidth(),
    )
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
                .padding(horizontal = NAMING_PREVIEW_CHIP_HORIZONTAL_PADDING, vertical = 3.dp),
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

/**
 * 估算内联胶囊要占的宽度。占位宽度以正文字号为 1em，而胶囊里的文字用的是更小的 labelMedium，
 * 因此每个字符按两者的字号比例折算，避免留出多余的空白。
 */
private fun namingPreviewChipWidthEm(text: String): Float {
    var width = NAMING_PREVIEW_CHIP_PADDING_EM
    text.forEach { char ->
        width += when {
            char.isWhitespace() -> 0.28f
            char.code in 0x4E00..0x9FFF -> 0.9f
            char.isLetterOrDigit() -> 0.55f
            else -> 0.42f
        }
    }
    return max(width, 3.2f)
}

/** 胶囊左右内边距（[NAMING_PREVIEW_CHIP_HORIZONTAL_PADDING] 的两倍）换算成 em，另留一点点余量防止文字被截断。 */
private const val NAMING_PREVIEW_CHIP_PADDING_EM = 1.5f

private val NAMING_PREVIEW_CHIP_HORIZONTAL_PADDING = 8.dp

private data class NamingTokenSection(
    val group: NamingTokenGroup,
    val tokens: List<NamingToken>,
)

private fun namingTokenSections(
    shape: NamingShape,
    scope: NamingTemplateScope,
): List<NamingTokenSection> {
    val available = NamingToken.forEditor(shape, scope)
    return NamingTokenGroup.entries.mapNotNull { group ->
        val tokens = available.filter { it.group == group }
        if (tokens.isEmpty()) null else NamingTokenSection(group = group, tokens = tokens)
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
    return TextFieldValue(
        text = next,
        selection = TextRange(start + insertion.length),
    )
}

/** 把选中内容包成可选片段；没有选中时插入一对空标记并把光标停在里面。 */
private fun wrapSelectionAsOptional(current: TextFieldValue): TextFieldValue {
    val start = minOf(current.selection.start, current.selection.end)
    val end = maxOf(current.selection.start, current.selection.end)
    val selected = current.text.substring(start, end)
    val next = buildString {
        append(current.text.substring(0, start))
        append(NamingRenderer.wrapAsOptional(selected))
        append(current.text.substring(end))
    }
    return TextFieldValue(
        text = next,
        selection = TextRange(start + 2 + selected.length),
    )
}

@Composable
private fun rememberNamingPreviewContexts(): Map<NamingShape, NamingContext> {
    val videoWork = stringResource(R.string.settings_naming_preview_video_title)
    val videoPart = stringResource(R.string.settings_naming_preview_item_title)
    val videoCollection = stringResource(R.string.settings_naming_preview_collection_title)
    val videoSection = stringResource(R.string.settings_naming_preview_section)
    val episodeSeason = stringResource(R.string.settings_naming_preview_episode_season)
    val episodeTitle = stringResource(R.string.settings_naming_preview_episode_title)
    val trackTitle = stringResource(R.string.settings_naming_preview_track_title)
    val trackCollection = stringResource(R.string.settings_naming_preview_track_collection)
    val trackArtist = stringResource(R.string.settings_naming_preview_track_artist)
    val opusTitle = stringResource(R.string.settings_naming_preview_opus_title)
    val opusCollection = stringResource(R.string.settings_naming_preview_opus_collection)
    val listCollection = stringResource(R.string.settings_naming_preview_list_collection)
    val upper = stringResource(R.string.settings_naming_preview_upper)
    val videoLabel = stringResource(R.string.parse_media_type_video)
    val bangumiLabel = stringResource(R.string.parse_media_type_bangumi)
    val musicLabel = stringResource(R.string.parse_media_type_music)
    val musicListLabel = stringResource(R.string.parse_media_type_music_list)
    val opusLabel = stringResource(R.string.parse_media_type_opus)
    val favoriteLabel = stringResource(R.string.parse_media_type_favorite)
    val audioVideoTask = stringResource(R.string.output_audio_video)
    val audioTask = stringResource(R.string.output_audio)
    val opusImageTask = stringResource(R.string.download_task_opus_image)
    val resolution = stringResource(R.string.parse_resolution_1080)
    val codec = stringResource(R.string.parse_codec_avc)
    val audioBitrate = stringResource(R.string.parse_bitrate_192)
    val format = stringResource(R.string.format_mp4)

    return remember(videoWork, episodeSeason, trackTitle, opusTitle, listCollection) {
        val pubTime = 1_719_331_200L
        val downTime = 1_744_412_800L
        val video = NamingContext(
            title = videoPart,
            work = videoWork,
            collection = videoCollection,
            p = "2",
            section = videoSection,
            container = videoLabel,
            mediaType = videoLabel,
            taskType = audioVideoTask,
            index = 1,
            pubTimeEpochSeconds = pubTime,
            downTimeEpochSeconds = downTime,
            upper = upper,
            upperId = "946974",
            id = "BV1xx411c7mD",
            aid = "123456789",
            bvid = "BV1xx411c7mD",
            cid = "99887766",
            fid = "556677",
            res = resolution,
            abr = audioBitrate,
            enc = codec,
            fmt = format,
        )
        mapOf(
            NamingShape.Video to video,
            NamingShape.Episode to NamingContext(
                title = episodeTitle,
                work = episodeSeason,
                collection = episodeSeason,
                ep = "01",
                section = videoSection,
                container = bangumiLabel,
                mediaType = bangumiLabel,
                taskType = audioVideoTask,
                index = 1,
                pubTimeEpochSeconds = pubTime,
                downTimeEpochSeconds = downTime,
                upper = upper,
                upperId = "946974",
                id = "ep606591",
                aid = "555940677",
                bvid = "BV1xx411c7mD",
                cid = "772096113",
                epid = "606591",
                ssid = "44227",
                res = resolution,
                abr = audioBitrate,
                enc = codec,
                fmt = format,
            ),
            NamingShape.Track to NamingContext(
                title = trackTitle,
                work = trackTitle,
                collection = trackCollection,
                container = musicListLabel,
                mediaType = musicLabel,
                taskType = audioTask,
                index = 1,
                pubTimeEpochSeconds = pubTime,
                downTimeEpochSeconds = downTime,
                upper = upper,
                upperId = "946974",
                artist = trackArtist,
                id = "au10001",
                sid = "10001",
                amid = "20002",
                abr = audioBitrate,
                fmt = format,
            ),
            NamingShape.Opus to NamingContext(
                title = opusTitle,
                work = opusTitle,
                collection = opusCollection,
                img = "01",
                container = opusLabel,
                mediaType = opusLabel,
                taskType = opusImageTask,
                index = 1,
                pubTimeEpochSeconds = pubTime,
                downTimeEpochSeconds = downTime,
                upper = upper,
                upperId = "946974",
                id = "cv12345",
                opid = "889900112233",
                cvid = "12345",
            ),
            NamingShape.Listing to NamingContext(
                collection = listCollection,
                container = favoriteLabel,
                index = 1,
                pubTimeEpochSeconds = pubTime,
                downTimeEpochSeconds = downTime,
                upper = upper,
                upperId = "946974",
                fid = "556677",
            ),
        )
    }
}

private fun namingPreviewExtension(
    shape: NamingShape,
    scope: NamingTemplateScope,
): String? {
    if (scope != NamingTemplateScope.File) return null
    return when (shape) {
        NamingShape.Track -> "flac"
        NamingShape.Opus -> "jpg"
        else -> "mp4"
    }
}

@DrawableRes
private fun namingScopeIcon(scope: NamingTemplateScope): Int = when (scope) {
    NamingTemplateScope.TopFolder -> R.drawable.ic_folder_special_24
    NamingTemplateScope.ItemFolder -> R.drawable.ic_bookmark_manager_24
    NamingTemplateScope.File -> R.drawable.ic_save_as_24
}

@StringRes
private fun namingScopeTitleRes(scope: NamingTemplateScope): Int = when (scope) {
    NamingTemplateScope.TopFolder -> R.string.settings_naming_top_level_folder_template
    NamingTemplateScope.ItemFolder -> R.string.settings_naming_item_folder_template
    NamingTemplateScope.File -> R.string.settings_naming_file_template
}

@StringRes
private fun namingScopeDescriptionRes(scope: NamingTemplateScope): Int = when (scope) {
    NamingTemplateScope.TopFolder -> R.string.settings_naming_top_level_folder_template_desc
    NamingTemplateScope.ItemFolder -> R.string.settings_naming_item_folder_template_desc
    NamingTemplateScope.File -> R.string.settings_naming_file_template_desc
}

@Composable
private fun namingShapeLabel(shape: NamingShape): String = when (shape) {
    NamingShape.Video -> stringResource(R.string.settings_naming_shape_video)
    NamingShape.Episode -> stringResource(R.string.settings_naming_shape_episode)
    NamingShape.Track -> stringResource(R.string.settings_naming_shape_track)
    NamingShape.Opus -> stringResource(R.string.settings_naming_shape_opus)
    NamingShape.Listing -> stringResource(R.string.settings_naming_shape_listing)
}

@Composable
private fun namingShapeDescription(shape: NamingShape): String = when (shape) {
    NamingShape.Video -> stringResource(R.string.settings_naming_shape_video_desc)
    NamingShape.Episode -> stringResource(R.string.settings_naming_shape_episode_desc)
    NamingShape.Track -> stringResource(R.string.settings_naming_shape_track_desc)
    NamingShape.Opus -> stringResource(R.string.settings_naming_shape_opus_desc)
    NamingShape.Listing -> stringResource(R.string.settings_naming_shape_listing_desc)
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

/** 只有时间变量需要额外解释格式写法，其余分组看名字即可。 */
@Composable
private fun namingTokenGroupDescription(group: NamingTokenGroup): String? {
    return when (group) {
        NamingTokenGroup.Time -> stringResource(R.string.settings_naming_group_time_desc)
        else -> null
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
        NamingToken.Title -> stringResource(R.string.settings_naming_token_title)
        NamingToken.Work -> stringResource(R.string.settings_naming_token_work)
        NamingToken.Collection -> stringResource(R.string.settings_naming_token_collection)
        NamingToken.P -> stringResource(R.string.settings_naming_token_p)
        NamingToken.Ep -> stringResource(R.string.settings_naming_token_ep)
        NamingToken.Section -> stringResource(R.string.settings_naming_token_section)
        NamingToken.Img -> stringResource(R.string.settings_naming_token_img)
        NamingToken.Container -> stringResource(R.string.settings_naming_token_container)
        NamingToken.MediaType -> stringResource(R.string.settings_naming_token_media_type)
        NamingToken.TaskType -> stringResource(R.string.settings_naming_token_task_type)
        NamingToken.Index -> stringResource(R.string.settings_naming_token_index)
        NamingToken.PubTime -> stringResource(R.string.settings_naming_token_pub_time)
        NamingToken.DownTime -> stringResource(R.string.settings_naming_token_down_time)
        NamingToken.Id -> stringResource(R.string.settings_naming_token_id)
        NamingToken.Upper -> stringResource(R.string.settings_naming_token_upper)
        NamingToken.UpperId -> stringResource(R.string.settings_naming_token_upper_id)
        NamingToken.Artist -> stringResource(R.string.settings_naming_token_artist)
        NamingToken.Aid -> stringResource(R.string.settings_naming_token_aid)
        NamingToken.Bvid -> stringResource(R.string.settings_naming_token_bvid)
        NamingToken.Cid -> stringResource(R.string.settings_naming_token_cid)
        NamingToken.Epid -> stringResource(R.string.settings_naming_token_epid)
        NamingToken.Ssid -> stringResource(R.string.settings_naming_token_ssid)
        NamingToken.Sid -> stringResource(R.string.settings_naming_token_sid)
        NamingToken.Amid -> stringResource(R.string.settings_naming_token_amid)
        NamingToken.Fid -> stringResource(R.string.settings_naming_token_fid)
        NamingToken.Opid -> stringResource(R.string.settings_naming_token_opid)
        NamingToken.Cvid -> stringResource(R.string.settings_naming_token_cvid)
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
    onOpenSourceLicenses: () -> Unit,
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
                                fontFamily = BiliToolsFonts.googleSansFlexRond100,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "$versionName ($versionCode)",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = BiliToolsFonts.googleSansFlexRond100,
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
                    items = 3,
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
                    items = 3,
                    index = 1,
                    leadingContent = { SettingsItemIcon(R.drawable.ic_gavel_24) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(R.string.settings_license_title))
                    },
                    supportingContent = { Text(stringResource(R.string.settings_license_summary)) },
                    onClick = { showLicense = true },
                )
            }

            item {
                ClickableListItem(
                    items = 3,
                    index = 2,
                    leadingContent = { SettingsItemIcon(R.drawable.ic_code_24) },
                    headlineContent = {
                        SettingsItemTitle(stringResource(R.string.settings_opensource_licenses_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.settings_opensource_licenses_summary))
                    },
                    trailingContent = {
                        SettingsItemIcon(R.drawable.ic_chevron_right_24)
                    },
                    onClick = onOpenSourceLicenses,
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
        LicenseBottomSheet(onDismiss = { showLicense = false })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        color = AppSurfaces.cardContainerColor,
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
                colors = AppAccents.toggleButtonColors(),
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
                colors = AppAccents.toggleButtonColors(),
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
                    colors = AppAccents.toggleButtonColors(),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HapticFeedbackPickerListItem(
    level: HapticFeedbackLevel,
    items: Int,
    index: Int,
    onLevelChange: (HapticFeedbackLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val options = remember {
        listOf(
            HapticFeedbackLevel.Off to R.string.settings_haptic_feedback_off,
            HapticFeedbackLevel.Light to R.string.settings_haptic_feedback_light,
            HapticFeedbackLevel.Full to R.string.settings_haptic_feedback_full,
        )
    }

    Column(
        modifier = modifier.clip(SettingsExpressiveShapes.groupShape(index, items)),
    ) {
        ListItem(
            leadingContent = { SettingsItemIcon(R.drawable.ic_mobile_vibrate_24) },
            headlineContent = {
                SettingsItemTitle(stringResource(R.string.settings_haptic_feedback))
            },
            supportingContent = { Text(stringResource(R.string.settings_haptic_feedback_desc)) },
            colors = SettingsExpressiveDefaults.listItemColors,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier
                .background(SettingsExpressiveDefaults.listItemColors.containerColor)
                .padding(start = 52.dp, end = 16.dp, bottom = 8.dp),
        ) {
            options.fastForEachIndexed { optionIndex, (option, labelRes) ->
                ToggleButton(
                    checked = option == level,
                    onCheckedChange = {
                        onLevelChange(option)
                        // 先落库再触发，让用户立刻感受到新档位的实际手感；Confirm 在轻量与完整档都启用。
                        haptics.confirm()
                    },
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
                        text = stringResource(labelRes),
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
    // 色块直接取自各配色 overlay，与实际按钮同色，改配色表时这里无需同步
    val context = LocalContext.current
    val options = remember(context) {
        AppThemeColor.entries.mapNotNull { themeColor ->
            themeColor.overlayStyleResOrNull()?.let { overlayStyleRes ->
                val swatch = context.resolveOverlaySwatch(overlayStyleRes)
                ColorSchemeOption(themeColor, Color(swatch.fill), Color(swatch.onFill))
            }
        }
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
                            onColorChange(if (it) AppThemeColor.Dynamic else AppThemeColor.Sakura)
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
                        colors = AppAccents.switchColors(),
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
            supportingContent = { Text(stringResource(color.displayNameRes())) },
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
                        color = option.fillColor,
                        checkColor = option.onFillColor,
                        name = stringResource(
                            R.string.settings_color_scheme_swatch_desc,
                            stringResource(option.themeColor.displayNameRes()),
                        ),
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
    checkColor: Color,
    name: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.iconButtonColors(containerColor = color),
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = name
                selected = isSelected
            },
        onClick = onClick,
    ) {
        AnimatedContent(targetState = isSelected) { showCheck ->
            if (showCheck) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_24),
                    contentDescription = null,
                    tint = checkColor,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExpressiveSliderListItem(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    valueLabel: String,
    items: Int,
    index: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    // 分档 Slider（steps > 0）拖动时滑块逐格吸附、无过渡动画，观感生硬。
    // 改为滑块连续跟手，仅数值上报吸附到档位（过档给 tick 触感），松手后滑块弹性回吸到最近档位。
    val position = remember { Animatable(value) }
    var dragging by remember { mutableStateOf(false) }
    var lastNotified by remember { mutableFloatStateOf(value) }
    val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
    fun snapToStep(raw: Float): Float =
        (valueRange.start + ((raw - valueRange.start) / stepSize).roundToInt() * stepSize)
            .coerceIn(valueRange.start, valueRange.endInclusive)

    val snapSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(value) {
        // 非拖动中被外部改值（如恢复默认）时，同步滑块位置
        if (!dragging && position.value != value) {
            lastNotified = value
            position.animateTo(value, snapSpec)
        }
    }

    ListItem(
        leadingContent = { SettingsItemIcon(iconRes) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsItemTitle(title, modifier = Modifier.weight(1f))
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        supportingContent = {
            val sliderColors = AppAccents.sliderColors()
            Column {
                Text(description)
                Slider(
                    colors = sliderColors,
                    value = position.value,
                    onValueChange = { raw ->
                        dragging = true
                        scope.launch { position.snapTo(raw) }
                        val snapped = snapToStep(raw)
                        if (snapped != lastNotified) {
                            lastNotified = snapped
                            haptics.tick()
                            onValueChange(snapped)
                        }
                    },
                    valueRange = valueRange,
                    onValueChangeFinished = {
                        dragging = false
                        scope.launch { position.animateTo(snapToStep(position.value), snapSpec) }
                    },
                    track = { sliderState ->
                        // Slider 未设 steps（为保证滑块连续跟手），组件自身不画档位刻度；
                        // 这里去掉轨道末端的停止指示点，并自绘各档位圆点（滑块附近的点隐去）
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = sliderColors,
                            drawStopIndicator = null,
                            modifier = Modifier.drawWithContent {
                                drawContent()
                                val range = valueRange.endInclusive - valueRange.start
                                val thumbX = size.width *
                                    ((sliderState.value - valueRange.start) / range).coerceIn(0f, 1f)
                                val clearance = 10.dp.toPx()
                                for (i in 1..steps) {
                                    val x = size.width * i / (steps + 1)
                                    if (abs(x - thumbX) < clearance) continue
                                    drawCircle(
                                        color = if (x < thumbX) {
                                            sliderColors.activeTickColor
                                        } else {
                                            sliderColors.inactiveTickColor
                                        },
                                        radius = 2.dp.toPx(),
                                        center = Offset(x, center.y),
                                    )
                                }
                            },
                        )
                    },
                )
            }
        },
        colors = SettingsExpressiveDefaults.listItemColors,
        modifier = modifier.clip(SettingsExpressiveShapes.groupShape(index, items)),
    )
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
    val haptics = rememberAppHaptics()
    ListItem(
        leadingContent = { SettingsItemIcon(iconRes) },
        headlineContent = { SettingsItemTitle(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { next ->
                    haptics.toggle(next)
                    onCheckedChange(next)
                },
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
                colors = AppAccents.switchColors(),
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
    val haptics = rememberAppHaptics()
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
                onClick = {
                    haptics.tap()
                    onClick()
                },
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val context = LocalContext.current
    // aboutlibraries.json 由 AboutLibraries Gradle 插件在构建期生成并打进 res/raw
    val libraries = remember {
        val json = context.resources.openRawResource(R.raw.aboutlibraries)
            .bufferedReader()
            .use { it.readText() }
        Libs.Builder().withJson(json).build().libraries
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_opensource_licenses_title),
        subtitle = stringResource(R.string.settings_about_title),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(libraries) { library ->
                OpenSourceLibraryItem(library)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenSourceLibraryItem(library: Library) {
    val uriHandler = LocalUriHandler.current
    val haptics = rememberAppHaptics()
    val website = library.website?.takeIf { it.isNotBlank() }
    val author = remember(library) {
        library.developers
            .mapNotNull { developer -> developer.name?.takeIf(String::isNotBlank) }
            .joinToString(", ")
            .ifEmpty { library.organization?.name.orEmpty() }
    }
    val version = library.artifactVersion?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = website != null) {
                haptics.tap()
                website?.let(uriHandler::openUri)
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = BiliToolsFonts.googleSansFlexRond100,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (author.isNotEmpty()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = BiliToolsFonts.googleSansFlex,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (library.licenses.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    library.licenses.forEach { license ->
                        LicenseNameChip(license.name)
                    }
                }
            }
        }
        if (version != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = version,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 132.dp),
            )
        }
    }
}

@Composable
private fun LicenseNameChip(name: String) {
    // 按许可证名称哈希取基准色，深浅色模式分别调和出柔和底色与可读前景色
    val isDarkTheme = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val baseColor = licenseChipBaseColors[abs(name.hashCode()) % licenseChipBaseColors.size]
    val containerColor = baseColor.copy(alpha = if (isDarkTheme) 0.28f else 0.14f)
    val contentColor = if (isDarkTheme) {
        lerp(baseColor, Color.White, 0.55f)
    } else {
        lerp(baseColor, Color.Black, 0.30f)
    }
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = BiliToolsFonts.googleSansFlex,
        color = contentColor,
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private val licenseChipBaseColors = listOf(
    Color(0xFF7E57C2),
    Color(0xFF5C6BC0),
    Color(0xFF1E88E5),
    Color(0xFF00897B),
    Color(0xFF43A047),
    Color(0xFFF57C00),
    Color(0xFFD81B60),
)

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
            .background(AppSurfaces.pageContainerColor),
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
                                containerColor = AppSurfaces.cardContainerColor,
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
            containerColor = AppSurfaces.pageContainerColor,
            modifier = modifier
                .widthIn(max = SettingsExpressiveShapes.paneMaxWidth)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) { innerPadding ->
            content(innerPadding)
        }
    }
}

private object SettingsExpressiveDefaults {
    val topBarColors: TopAppBarColors
        @Composable
        get() = TopAppBarDefaults.topAppBarColors(
            containerColor = AppSurfaces.pageContainerColor,
            scrolledContainerColor = AppSurfaces.pageContainerColor,
        )

    val listItemColors: ListItemColors
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        get() = ListItemDefaults.segmentedColors(
            containerColor = AppSurfaces.cardContainerColor,
        )

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
