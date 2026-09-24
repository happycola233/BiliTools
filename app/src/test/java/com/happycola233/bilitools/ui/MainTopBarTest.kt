package com.happycola233.bilitools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.theme.BiliToolsFonts
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainTopBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstLayoutUsesLatestInsetsInLightTheme() = verifyFirstLayout(AppThemeMode.Light)

    @Test fun firstLayoutUsesLatestInsetsInDarkTheme() = verifyFirstLayout(AppThemeMode.Dark)

    @Test fun restoredCollapsedBarKeepsItsHeightInLightTheme() =
        verifyFirstLayout(AppThemeMode.Light, initiallyCollapsed = true)

    @Test fun restoredCollapsedBarKeepsItsHeightInDarkTheme() =
        verifyFirstLayout(AppThemeMode.Dark, initiallyCollapsed = true)

    @Test fun rtlTitleStaysAtStartWhileCollapsingInLightTheme() =
        verifyTitleStartAlignment(AppThemeMode.Light, LayoutDirection.Rtl)

    @Test fun rtlTitleStaysAtStartWhileCollapsingInDarkTheme() =
        verifyTitleStartAlignment(AppThemeMode.Dark, LayoutDirection.Rtl)

    @Test fun ltrTitleStaysAtStartWhileCollapsingInLightTheme() =
        verifyTitleStartAlignment(AppThemeMode.Light, LayoutDirection.Ltr)

    @Test fun ltrTitleStaysAtStartWhileCollapsingInDarkTheme() =
        verifyTitleStartAlignment(AppThemeMode.Dark, LayoutDirection.Ltr)

    private fun verifyTitleStartAlignment(mode: AppThemeMode, direction: LayoutDirection) {
        val title = if (direction == LayoutDirection.Rtl) "حسابي" else "BiliTools"
        var startPaddingPx = 0f
        lateinit var barState: TopAppBarState
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                BiliToolsTheme(AppSettings(themeMode = mode)) {
                    startPaddingPx = with(LocalDensity.current) { 16.dp.roundToPx().toFloat() }
                    barState = rememberTopAppBarState()
                    Box(Modifier.fillMaxWidth().testTag("top-bar-container")) {
                        MainCollapsingTopBar(title = title, state = barState)
                    }
                }
            }
        }

        // 检查变换后的实际边界，覆盖展开、折叠中、完全折叠及重新展开时的缩放锚点。
        for (fraction in listOf(0f, 0.5f, 1f, 0f)) {
            compose.runOnIdle { barState.heightOffset = barState.heightOffsetLimit * fraction }
            val containerBounds = compose.onNodeWithTag("top-bar-container").fetchSemanticsNode().boundsInRoot
            val titleBounds = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
            val actualStartPadding = if (direction == LayoutDirection.Rtl) {
                containerBounds.right - titleBounds.right
            } else {
                titleBounds.left - containerBounds.left
            }
            assertEquals("标题在折叠进度 $fraction 时应保持起始侧边距", startPaddingPx, actualStartPadding, 1f)
        }
    }

    private fun verifyFirstLayout(mode: AppThemeMode, initiallyCollapsed: Boolean = false) {
        val measuredHeights = mutableListOf<Int>()
        val statusBarHeightPx = 72
        var expandedHeightPx = 0
        var collapsedHeightPx = 0
        var insetsDispatched = false
        lateinit var barState: TopAppBarState

        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode)) {
                val view = LocalView.current
                with(LocalDensity.current) {
                    expandedHeightPx = MainTopBarExpandedHeight.roundToPx()
                    collapsedHeightPx = MainTopBarCollapsedHeight.roundToPx()
                }
                barState = rememberTopAppBarState(
                    initialHeightOffset = if (initiallyCollapsed) {
                        (collapsedHeightPx - expandedHeightPx).toFloat()
                    } else {
                        0f
                    },
                )
                Box(
                    modifier = Modifier.layout { measurable, constraints ->
                        if (!insetsDispatched) {
                            insetsDispatched = true
                            // 模拟启动时系统在组合结束后、首次测量前才下发状态栏 Insets。
                            ViewCompat.dispatchApplyWindowInsets(
                                view,
                                WindowInsetsCompat.Builder()
                                    .setInsets(
                                        WindowInsetsCompat.Type.statusBars(),
                                        Insets.of(0, statusBarHeightPx, 0, 0),
                                    )
                                    .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                                    .build(),
                            )
                        }
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                ) {
                    MainCollapsingTopBar(
                        title = "BiliTools",
                        state = barState,
                        titleFontFamily = BiliToolsFonts.googleSansFlexRond100,
                        modifier = Modifier.onSizeChanged { measuredHeights += it.height },
                    )
                }
            }
        }

        compose.runOnIdle {
            assertTrue("顶栏应完成首次测量", measuredHeights.isNotEmpty())
            val toolbarHeightPx = if (initiallyCollapsed) collapsedHeightPx else expandedHeightPx
            assertEquals(
                "首帧就应包含状态栏高度，无需等待下一次重组",
                statusBarHeightPx + toolbarHeightPx,
                measuredHeights.first(),
            )
            assertEquals(if (initiallyCollapsed) 1f else 0f, barState.collapsedFraction, 0f)
        }
    }
}
