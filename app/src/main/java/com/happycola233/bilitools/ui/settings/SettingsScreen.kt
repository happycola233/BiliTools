package com.happycola233.bilitools.ui.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.theme.BiliToolsSettingsTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiliToolsSettingsContent(
    settings: AppSettings,
    backStack: SnapshotStateList<SettingsDestination>,
    checkUpdateSummary: String,
    versionName: String,
    versionCode: Long,
    onExit: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onNavigateBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenDownloadLocationPicker: (String) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemeColorChange: (AppThemeColor) -> Unit,
    onParseQuickActionChange: (Boolean) -> Unit,
    onAddMetadataChange: (Boolean) -> Unit,
    onConfirmCellularChange: (Boolean) -> Unit,
    onHideInAlbumChange: (Boolean) -> Unit,
    onBlackThemeChange: (Boolean) -> Unit,
    onGlassDebugChange: (Boolean) -> Unit,
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
                        enabled = settings.parseQuickActionEnabled,
                        onEnabledChange = onParseQuickActionChange,
                        onBack = onNavigateBack,
                        modifier = modifier,
                    )
                }

                entry<SettingsDestination.Download> {
                    DownloadSettingsScreen(
                        settings = settings,
                        onOpenDownloadLocationPicker = onOpenDownloadLocationPicker,
                        onAddMetadataChange = onAddMetadataChange,
                        onConfirmCellularChange = onConfirmCellularChange,
                        onHideInAlbumChange = onHideInAlbumChange,
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
                        onCheckUpdate = onCheckUpdate,
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
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                ExpressiveSwitchListItem(
                    checked = enabled,
                    iconRes = R.drawable.ic_switch_access_shortcut_24,
                    title = stringResource(R.string.settings_parse_quick_action),
                    description = stringResource(R.string.settings_parse_quick_action_desc),
                    items = 1,
                    index = 0,
                    onCheckedChange = onEnabledChange,
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
                    items = 4,
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
                    items = 4,
                    index = 1,
                    onCheckedChange = onAddMetadataChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.confirmCellularDownload,
                    iconRes = R.drawable.ic_cell_tower_24,
                    title = stringResource(R.string.settings_confirm_cellular),
                    description = stringResource(R.string.settings_confirm_cellular_desc),
                    items = 4,
                    index = 2,
                    onCheckedChange = onConfirmCellularChange,
                )
            }

            item {
                ExpressiveSwitchListItem(
                    checked = settings.hideDownloadedVideosInSystemAlbum,
                    iconRes = R.drawable.ic_hide_image_24,
                    title = stringResource(R.string.settings_hide_download_video_in_system_album),
                    description = stringResource(R.string.settings_hide_download_video_in_system_album_desc),
                    items = 4,
                    index = 3,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutSettingsScreen(
    versionName: String,
    versionCode: Long,
    checkUpdateSummary: String,
    onCheckUpdate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showLicense by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val appIconPainter = painterResource(R.drawable.about_bilitools_icon)

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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    color = Color.White,
                                    shape = MaterialShapes.Cookie9Sided.toShape(),
                                ),
                        ) {
                            Image(
                                painter = appIconPainter,
                                contentDescription = null,
                                modifier = Modifier.size(46.dp),
                            )
                        }
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
            subtitle = stringResource(R.string.settings_license_summary),
            licenseRawRes = R.raw.license_gpl3,
            onDismiss = { showLicense = false },
        )
    }
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
                                    if (checked) R.drawable.ic_check_24 else R.drawable.ic_close_24,
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

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            modifier = Modifier
                .background(SettingsExpressiveDefaults.listItemColors.containerColor)
                .padding(bottom = 8.dp),
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
                thumbContent = {
                    Icon(
                        painter = painterResource(
                            if (checked) R.drawable.ic_check_24 else R.drawable.ic_close_24,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
                colors = SettingsExpressiveDefaults.switchColors,
            )
        },
        colors = SettingsExpressiveDefaults.listItemColors,
        modifier = modifier.clip(SettingsExpressiveShapes.groupShape(index, items)),
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
    subtitle: String,
    @RawRes licenseRawRes: Int,
    onDismiss: () -> Unit,
) {
    val licenseText = rememberRawTextResource(licenseRawRes)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }
            item {
                Text(
                    text = licenseText,
                    style = MaterialTheme.typography.bodyMedium,
                )
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
