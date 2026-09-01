package com.happycola233.bilitools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatActivity
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.ReleaseInfo
import com.happycola233.bilitools.data.UpdateCheckResult
import com.happycola233.bilitools.ui.downloads.DownloadsRoute
import com.happycola233.bilitools.ui.downloads.DownloadsTaskActionsGlassOverlay
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
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsFonts
import com.happycola233.bilitools.ui.update.UpdateDialogContent
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private const val TAB_PARSE = 0
private const val TAB_DOWNLOADS = 1
private const val TAB_ME = 2
private val MainTopBarCollapsedHeight = 56.dp
private val MainTopBarExpandedHeight = 96.dp

/** 主界面的单一 Compose 根：内容采样层、底栏与所有模态浮层按顺序叠放。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val saveableStateHolder = rememberSaveableStateHolder()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val contentBackdrop = rememberLayerBackdrop()
    val taskActionsOverlayState = remember { DownloadsTaskActionsOverlayState() }
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
        modifier = modifier
            .fillMaxSize()
            .background(AppSurfaces.pageContainerColor),
    ) {
        MainContentLayer(
            selectedTabIndex = selectedTabIndex,
            saveableStateHolder = saveableStateHolder,
            scrollBehavior = scrollBehavior,
            contentBackdrop = contentBackdrop,
            taskActionsOverlayState = taskActionsOverlayState,
            parseViewModel = parseViewModel,
            downloadsViewModel = downloadsViewModel,
            loginViewModel = loginViewModel,
            onOpenParseUrl = onOpenParseUrl,
        )

        MainBottomBarOverlay(
            settings = settings,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = selectTab,
            contentBackdrop = contentBackdrop,
        )

        MainUpdatePromptHost(
            activity = activity,
            checkForUpdates = checkForUpdates,
        )

        DownloadsTaskActionsGlassOverlay(
            state = taskActionsOverlayState,
            backdrop = contentBackdrop,
        )
    }
}

/**
 * 只有这里读取当前 Tab；底栏点击不会让主壳、更新检查与其他模态层跟着重组。
 * 页面容器固定全屏，顶栏折叠仅改变滚动内容的 top padding，底部锚定控件不参与位移。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainContentLayer(
    selectedTabIndex: () -> Int,
    saveableStateHolder: SaveableStateHolder,
    scrollBehavior: TopAppBarScrollBehavior,
    contentBackdrop: LayerBackdrop,
    taskActionsOverlayState: DownloadsTaskActionsOverlayState,
    parseViewModel: ParseViewModel,
    downloadsViewModel: DownloadsViewModel,
    loginViewModel: LoginViewModel,
    onOpenParseUrl: (String) -> Unit,
) {
    val selectedIndex = selectedTabIndex()
    val density = LocalDensity.current
    val safeTopPadding = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val currentTopBarHeight = with(density) {
        MainTopBarExpandedHeight + scrollBehavior.state.heightOffset.toDp()
    }.coerceAtLeast(MainTopBarCollapsedHeight)
    val contentTopPadding = safeTopPadding + currentTopBarHeight

    LaunchedEffect(selectedIndex) {
        if (selectedIndex != TAB_DOWNLOADS) {
            taskActionsOverlayState.dismissImmediately()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(contentBackdrop)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        saveableStateHolder.SaveableStateProvider(selectedIndex) {
            when (selectedIndex) {
                TAB_PARSE -> ParseRoute(
                    viewModel = parseViewModel,
                    contentTopPadding = contentTopPadding,
                )

                TAB_DOWNLOADS -> DownloadsRoute(
                    viewModel = downloadsViewModel,
                    contentTopPadding = contentTopPadding,
                    taskActionsOverlayState = taskActionsOverlayState,
                    modifier = Modifier.fillMaxSize(),
                )

                TAB_ME -> MeRoute(
                    viewModel = loginViewModel,
                    contentTopPadding = contentTopPadding,
                    onOpenParseUrl = onOpenParseUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        MediumFlexibleTopAppBar(
            title = {
                Text(
                    text = when (selectedIndex) {
                        TAB_DOWNLOADS -> stringResource(R.string.nav_downloads)
                        TAB_ME -> stringResource(R.string.nav_me)
                        else -> stringResource(R.string.app_name)
                    },
                    // 旧折叠栏在 96dp 展开高度下使用 28dp 底边距，而 M3 Medium 固定为
                    // 24dp。仅在展开态上移差值，折叠态仍保持 actionBarSize 内垂直居中。
                    modifier = Modifier.graphicsLayer {
                        translationY = -4.dp.toPx() *
                            (1f - scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f))
                    },
                    fontWeight = FontWeight.Bold,
                    // 解析页标题延续旧壳的品牌字形，其他页面使用系统标题字体。
                    fontFamily = BiliToolsFonts.googleSansFlexRond100
                        .takeIf { selectedIndex == TAB_PARSE },
                )
            },
            collapsedHeight = MainTopBarCollapsedHeight,
            expandedHeight = MainTopBarExpandedHeight,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AppSurfaces.pageContainerColor,
                scrolledContainerColor = AppSurfaces.pageContainerColor,
            ),
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun BoxScope.MainBottomBarOverlay(
    settings: AppSettings,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    contentBackdrop: LayerBackdrop,
) {
    if (settings.liquidBottomTabsEnabled) {
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
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        MainMaterialBottomBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
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
