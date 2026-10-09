package com.happycola233.bilitools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.appcompat.app.AppCompatActivity
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.ReleaseInfo
import com.happycola233.bilitools.data.UpdateCheckResult
import com.happycola233.bilitools.ui.downloads.DownloadsRoute
import com.happycola233.bilitools.ui.downloads.DownloadsRouteUiState
import com.happycola233.bilitools.ui.downloads.DownloadsSelectAllButton
import com.happycola233.bilitools.ui.downloads.DownloadsSelectionCloseButton
import com.happycola233.bilitools.ui.downloads.DownloadsTaskActionsOverlay
import com.happycola233.bilitools.ui.downloads.DownloadsTaskActionsOverlayState
import com.happycola233.bilitools.ui.downloads.DownloadsViewModel
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.liquidtabs.LiquidGlassStyle
import com.happycola233.bilitools.ui.liquidtabs.MainLiquidBottomBar
import com.happycola233.bilitools.ui.liquidtabs.MainMaterialBottomBar
import com.happycola233.bilitools.ui.login.LoginViewModel
import com.happycola233.bilitools.ui.me.MeRoute
import com.happycola233.bilitools.ui.parse.ParseRoute
import com.happycola233.bilitools.ui.parse.ParseViewModel
import com.happycola233.bilitools.ui.theme.BiliToolsFonts
import com.happycola233.bilitools.ui.update.UpdateDialogContent
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

private const val TAB_PARSE = 0
private const val TAB_DOWNLOADS = 1
private const val TAB_ME = 2

/** 主界面的单一 Compose 根：内容采样层、底栏与所有模态浮层按顺序叠放。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    activity: AppCompatActivity,
    checkForUpdates: Boolean,
    settings: AppSettings,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    parseViewModel: ParseViewModel,
    downloadsViewModel: DownloadsViewModel,
    loginViewModel: LoginViewModel,
    onOpenParseUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val contentBackdrop = rememberLayerBackdrop()
    val taskActionsOverlayState = remember { DownloadsTaskActionsOverlayState() }
    // 下载页多选会接管顶栏与底栏，状态由主壳持有。
    val downloadsRouteState = rememberSaveable(saver = DownloadsRouteUiState.Saver) {
        DownloadsRouteUiState()
    }
    val haptics = rememberAppHaptics()
    val currentSelectedTabIndex = rememberUpdatedState(selectedTabIndex)
    val currentOnTabSelected = rememberUpdatedState(onTabSelected)
    val selectTab: (Int) -> Unit = remember(haptics) {
        { index ->
            // 重复点击当前 Tab 不算切换，不给反馈；液态气泡始终订阅真实页面状态自行校正。
            if (index != currentSelectedTabIndex.value()) {
                currentOnTabSelected.value(index)
                haptics.select()
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        MainContentLayer(
            selectedTabIndex = selectedTabIndex,
            scrollBehavior = scrollBehavior,
            contentBackdrop = contentBackdrop,
            taskActionsOverlayState = taskActionsOverlayState,
            downloadsRouteState = downloadsRouteState,
            parseViewModel = parseViewModel,
            downloadsViewModel = downloadsViewModel,
            loginViewModel = loginViewModel,
            onOpenParse = { selectTab(TAB_PARSE) },
            onOpenParseUrl = onOpenParseUrl,
        )

        MainBottomBarOverlay(
            settings = settings,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = selectTab,
            contentBackdrop = contentBackdrop,
            // 多选期间底栏让位给下载页的批量工具栏。
            hidden = { selectedTabIndex() == TAB_DOWNLOADS && downloadsRouteState.selectionMode },
        )

        MainUpdatePromptHost(
            activity = activity,
            checkForUpdates = checkForUpdates,
        )

        DownloadsTaskActionsOverlay(
            state = taskActionsOverlayState,
            backdrop = contentBackdrop,
            liquidGlassEnabled = settings.liquidGlassPanelsEnabled,
        )
    }
}

/**
 * 只有这里读取当前 Tab；底栏点击不会让主壳、更新检查与其他模态层跟着重组。
 *
 * 页面内边距固定按展开态预留，顶栏折叠改由 [collapsingTopBarOffset] 在布局阶段整体上移页面，
 * 因此滚动过程中三个页面都不会重组，底部锚定控件的位置也不受影响。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContentLayer(
    selectedTabIndex: () -> Int,
    scrollBehavior: TopAppBarScrollBehavior,
    contentBackdrop: LayerBackdrop,
    taskActionsOverlayState: DownloadsTaskActionsOverlayState,
    downloadsRouteState: DownloadsRouteUiState,
    parseViewModel: ParseViewModel,
    downloadsViewModel: DownloadsViewModel,
    loginViewModel: LoginViewModel,
    onOpenParse: () -> Unit,
    onOpenParseUrl: (String) -> Unit,
) {
    val selectedIndex = selectedTabIndex()
    val focusManager = LocalFocusManager.current
    val downloadsSelecting = selectedIndex == TAB_DOWNLOADS && downloadsRouteState.selectionMode

    LaunchedEffect(selectedIndex) {
        // 页面常驻组合，切走时输入焦点不会自动释放，这里主动收起键盘
        focusManager.clearFocus()
        if (selectedIndex != TAB_DOWNLOADS) {
            taskActionsOverlayState.dismissImmediately()
            // 多选会隐藏底栏，从其他入口切走时一并退出，回到下载页时底栏完整。
            downloadsRouteState.exitSelectionMode()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(contentBackdrop)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        Box(
            modifier = Modifier
                // 与顶栏在同一帧处理顶部 Insets；页面只需预留顶栏本体高度。
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .collapsingTopBarOffset { scrollBehavior.state.heightOffset },
        ) {
            MainTabHost(active = selectedIndex == TAB_PARSE) {
                ParseRoute(
                    viewModel = parseViewModel,
                    contentTopPadding = MainTopBarExpandedHeight,
                )
            }

            MainTabHost(active = selectedIndex == TAB_DOWNLOADS) {
                DownloadsRoute(
                    viewModel = downloadsViewModel,
                    routeState = downloadsRouteState,
                    contentTopPadding = MainTopBarExpandedHeight,
                    taskActionsOverlayState = taskActionsOverlayState,
                    onOpenParse = onOpenParse,
                    onOpenParseUrl = onOpenParseUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            MainTabHost(active = selectedIndex == TAB_ME) {
                MeRoute(
                    viewModel = loginViewModel,
                    contentTopPadding = MainTopBarExpandedHeight,
                    onOpenParseUrl = onOpenParseUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val selectedCount = downloadsRouteState.selectedGroupIds.size
        MainCollapsingTopBar(
            title = when {
                downloadsSelecting -> pluralStringResource(R.plurals.downloads_selection_title, selectedCount, selectedCount)
                selectedIndex == TAB_DOWNLOADS -> stringResource(R.string.nav_downloads)
                selectedIndex == TAB_ME -> stringResource(R.string.nav_me)
                else -> stringResource(R.string.app_name)
            },
            state = scrollBehavior.state,
            // 解析页标题延续旧壳的品牌字形，其他页面使用系统标题字体
            titleFontFamily = BiliToolsFonts.googleSansFlexRond100
                .takeIf { selectedIndex == TAB_PARSE },
            titleKey = downloadsSelecting,
            navigationIcon = {
                DownloadsSelectionCloseButton(
                    visible = downloadsSelecting,
                    onClick = downloadsRouteState::exitSelectionMode,
                )
            },
            actions = {
                DownloadsSelectAllButton(
                    visible = downloadsSelecting,
                    selectedGroupIds = downloadsRouteState.selectedGroupIds,
                    groups = downloadsViewModel.groups,
                    onToggleSelectAll = downloadsRouteState::toggleSelectAll,
                )
            },
        )
    }
}

/**
 * 页面宿主：首次激活后常驻组合，未激活的页面只测量、不放置。
 *
 * 常驻组合让切页不再重建整个页面（旧的 ViewPager2 同样保留已创建的页面）；不放置则意味着
 * 它既不绘制、不参与玻璃采样，也收不到触摸，与激活页互不干扰。
 */
@Composable
private fun MainTabHost(
    active: Boolean,
    content: @Composable () -> Unit,
) {
    // active 变化本身就会触发重组，用普通标记即可，不必额外引入可观察状态
    val visit = remember { MainTabVisit() }
    if (active) {
        visit.visited = true
    }
    if (!visit.visited) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tabPlacement(active),
    ) {
        content()
    }
}

private class MainTabVisit {
    var visited = false
}

private fun Modifier.tabPlacement(active: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (active) {
            placeable.place(0, 0)
        }
    }
}

/**
 * 顶栏折叠时页面整体等量上移，并等量增高以保持底边贴住屏幕底部，
 * 效果与旧 CoordinatorLayout 的滚动联动一致。
 *
 * [heightOffset] 在布局阶段读取（取值为 0 到负的折叠区间），滚动时不会触发页面重组。
 */
internal fun Modifier.collapsingTopBarOffset(heightOffset: () -> Float): Modifier =
    layout { measurable, constraints ->
        val offset = heightOffset().roundToInt().coerceAtMost(0)
        val extraHeight = -offset
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = constraints.minHeight + extraHeight,
                maxHeight = if (constraints.maxHeight == Constraints.Infinity) {
                    Constraints.Infinity
                } else {
                    constraints.maxHeight + extraHeight
                },
            ),
        )
        layout(placeable.width, (placeable.height - extraHeight).coerceAtLeast(0)) {
            placeable.place(0, offset)
        }
    }

/**
 * 主导航栏。[hidden] 为真时整体沉到屏幕下沿之外；位移在布局阶段读取，
 * 动画过程中底栏不重组，玻璃采样也按实际位置进行。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.MainBottomBarOverlay(
    settings: AppSettings,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    contentBackdrop: LayerBackdrop,
    hidden: () -> Boolean,
) {
    val hiddenTarget by remember(hidden) { derivedStateOf(hidden) }
    val hideProgress by animateFloatAsState(
        targetValue = if (hiddenTarget) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "mainBottomBarHide",
    )
    val hideDistancePx = with(LocalDensity.current) { (mainBottomBarBottomInset() + 16.dp).toPx() }
    val hideModifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            // 完全收起后不再放置，避免屏幕外的底栏仍参与无障碍与触摸。
            if (hideProgress < 1f) placeable.place(0, (hideDistancePx * hideProgress).roundToInt())
        }
    }
    if (settings.liquidBottomTabsEnabled && isLiquidGlassSupported()) {
        MainLiquidBottomBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            backdrop = contentBackdrop,
            glassStyle = LiquidGlassStyle(
                blurRadiusDp = settings.liquidBarGlassBlurRadiusDp,
                refractionHeightDp = settings.liquidBarGlassRefractionHeightDp,
                refractionAmountFrac = settings.liquidBarGlassRefractionAmountFrac,
                chromaticAberration = settings.liquidBarGlassChromaticAberration,
            ),
            surfaceAlpha = settings.liquidBarGlassSurfaceAlpha,
            widthFraction = settings.liquidBarWidthFraction,
            modifier = Modifier.fillMaxSize().then(hideModifier),
        )
    } else {
        MainMaterialBottomBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = Modifier.align(Alignment.BottomCenter).then(hideModifier),
        )
    }
}

/** 自动更新提示也是主壳模态层的一部分，避免 MainActivity 再持有界面状态。 */
@Composable
private fun MainUpdatePromptHost(
    activity: AppCompatActivity,
    checkForUpdates: Boolean,
) {
    var prompt by remember { mutableStateOf<MainUpdatePrompt?>(null) }
    LaunchedEffect(activity, checkForUpdates) {
        if (!checkForUpdates) return@LaunchedEffect
        val container = activity.applicationContext.appContainer
        when (val result = container.updateRepository.checkForUpdate()) {
            is UpdateCheckResult.UpdateAvailable -> {
                if (!container.settingsRepository.shouldIgnoreUpdate(result.release.versionName)) {
                    prompt = MainUpdatePrompt(result.release, result.currentVersion)
                }
            }

            is UpdateCheckResult.UpToDate -> Unit
            is UpdateCheckResult.Failed -> Unit
        }
    }
    prompt?.let { update ->
        UpdateDialogContent(
            activity = activity,
            release = update.release,
            currentVersion = update.currentVersion,
            onDismiss = { prompt = null },
        )
    }
}

private data class MainUpdatePrompt(
    val release: ReleaseInfo,
    val currentVersion: String,
)
