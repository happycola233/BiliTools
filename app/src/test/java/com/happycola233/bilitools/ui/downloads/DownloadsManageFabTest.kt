package com.happycola233.bilitools.ui.downloads

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsManageFabTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuBackAndAccessibilityInLightTheme() = verifyMenu(AppThemeMode.Light)
    @Test fun menuBackAndAccessibilityInDarkTheme() = verifyMenu(AppThemeMode.Dark)

    private fun verifyMenu(mode: AppThemeMode) {
        lateinit var backDispatcher: OnBackPressedDispatcher
        var navigationBackCount = 0
        var batchManageCount = 0
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BackHandler { navigationBackCount++ }
            BiliToolsTheme(AppSettings(themeMode = mode)) {
                Box(Modifier.fillMaxSize()) {
                    DownloadsManageFab(
                        bottomPadding = 16.dp,
                        resumeAllCount = 1,
                        pauseAllCount = 1,
                        completedGroupCount = 1,
                        onBatchManage = { batchManageCount++ },
                        onResumeAll = {},
                        onPauseAll = {},
                        onClearCompleted = {},
                        onClearAll = {},
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }

        val button = compose.onNodeWithContentDescription("下载管理操作")
        fun assertMenuState(description: String) {
            button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description))
        }

        assertMenuState("已收起")
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, navigationBackCount) }

        // 长按只显示说明，不应误触展开或执行任何管理操作。
        compose.onNodeWithText("下载管理操作").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        button.performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithText("下载管理操作").assertIsDisplayed()
        assertMenuState("已收起")
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true

        button.performClick()
        assertMenuState("已展开")
        compose.onNodeWithText("批量管理").assertIsDisplayed()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        assertMenuState("已收起")
        compose.onNodeWithText("批量管理").assertDoesNotExist()
        compose.runOnIdle { assertEquals("菜单展开时返回不能离开页面", 1, navigationBackCount) }

        // 点击菜单项后也要更新无障碍状态，并把返回操作交还给页面。
        button.performClick()
        compose.onNodeWithText("批量管理").performClick()
        assertMenuState("已收起")
        compose.runOnIdle {
            assertEquals(1, batchManageCount)
            backDispatcher.onBackPressed()
        }
        compose.runOnIdle { assertEquals(2, navigationBackCount) }
    }
}
