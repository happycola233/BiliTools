package com.happycola233.bilitools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
