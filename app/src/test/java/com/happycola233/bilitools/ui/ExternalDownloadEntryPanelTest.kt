package com.happycola233.bilitools.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExternalDownloadEntryPanelTest {
    @get:Rule val compose = createComposeRule()
    private var dismissalCount = 0
    private lateinit var backDispatcher: OnBackPressedDispatcher
    private lateinit var requestDismissFromContent: () -> Unit

    @Test fun toolbarCloseWaitsForExitInLightTheme() = verifyToolbarClose(AppThemeMode.Light)

    @Test fun toolbarCloseWaitsForExitInDarkTheme() = verifyToolbarClose(AppThemeMode.Dark)

    @Test fun backWaitsForExit() {
        showPanel()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { backDispatcher.onBackPressed() }
        verifyExitCompletesOnce()
    }

    @Test fun outsideTapWaitsForExit() {
        showPanel()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("entry-root").performTouchInput { click(Offset(1f, 1f)) }
        verifyExitCompletesOnce()
    }

    @Test fun downloadQueuedWaitsForExit() {
        showPanel()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { requestDismissFromContent() }
        verifyExitCompletesOnce()
    }

    @Test fun closeDuringEntranceStillCompletes() {
        compose.mainClock.autoAdvance = false
        showPanel()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { backDispatcher.onBackPressed() }
        verifyExitCompletesOnce()
    }

    private fun verifyToolbarClose(mode: AppThemeMode) {
        showPanel(mode)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("取消").performClick()
        verifyExitCompletesOnce()
    }

    private fun showPanel(mode: AppThemeMode = AppThemeMode.Light) {
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BiliToolsTheme(AppSettings(themeMode = mode)) {
                Box(Modifier.fillMaxSize().testTag("entry-root")) {
                    ExternalDownloadEntryPanel(onDismissed = { dismissalCount++ }) { onDismissRequest ->
                        requestDismissFromContent = onDismissRequest
                        Text("解析资源")
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals("打开浮窗不能触发关闭回调", 0, dismissalCount) }
    }

    private fun verifyExitCompletesOnce() {
        compose.runOnIdle {
            assertEquals("退场开始时不能立即结束 Activity", 0, dismissalCount)
            // 退场过程中重复返回不能绕过动画，也不能重复派发关闭回调。
            backDispatcher.onBackPressed()
            backDispatcher.onBackPressed()
            assertEquals(0, dismissalCount)
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals("退场结束后关闭一次", 1, dismissalCount) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals(1, dismissalCount) }
    }
}
