package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsDeleteDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightConfirmationFollowsSwipeIntent() = verifyConfirmation(AppThemeMode.Light)
    @Test fun darkConfirmationFollowsSwipeIntent() = verifyConfirmation(AppThemeMode.Dark)
    @Test fun rtlLightConfirmationKeepsRememberButtonInTheMiddle() = verifyConfirmation(AppThemeMode.Light, LayoutDirection.Rtl)
    @Test fun rtlDarkConfirmationKeepsRememberButtonInTheMiddle() = verifyConfirmation(AppThemeMode.Dark, LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "zh-rCN-w280dp-h891dp")
    fun narrowDialogKeepsRememberButtonInTheMiddle() = verifyConfirmation(AppThemeMode.Light, stacked = true)

    @Test
    @Config(qualifiers = "zh-rCN-w280dp-h891dp")
    fun narrowRtlDialogKeepsRememberButtonInTheMiddle() = verifyConfirmation(AppThemeMode.Dark, LayoutDirection.Rtl, stacked = true)

    @Test fun largeFontKeepsRememberButtonInTheMiddle() {
        RuntimeEnvironment.setFontScale(2f)
        verifyConfirmation(AppThemeMode.Dark, stacked = true)
    }

    private fun verifyConfirmation(mode: AppThemeMode, direction: LayoutDirection = LayoutDirection.Ltr, stacked: Boolean = false) {
        var state by mutableStateOf<DownloadsDialogState>(DownloadsDialogState.DeleteGroup(1, false))
        val confirmed = mutableListOf<Boolean>()
        var dismissals = 0
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                BiliToolsTheme(AppSettings(themeMode = mode)) {
                    DownloadsDeleteDialog(state, onDismiss = { dismissals++ }, onConfirm = { confirmed += it })
                }
            }
        }
        val cancel = compose.onNodeWithText("取消")
        val remember = compose.onNodeWithText("确认并不再显示")
        val delete = compose.onNode(hasText("删除") and hasClickAction())
        compose.onNode(isToggleable()).assertDoesNotExist()
        compose.onNodeWithText("清除记录").assertExists()
        compose.onNodeWithText("将从下载列表中移除所选任务，保留已下载的文件。").assertExists()
        val cancelBounds = cancel.getUnclippedBoundsInRoot()
        val rememberBounds = remember.getUnclippedBoundsInRoot()
        val deleteBounds = delete.getUnclippedBoundsInRoot()
        capture("layout-${mode.name}-${direction.name}-$stacked")
        if (stacked) {
            assertTrue("纵向排列仍为取消、记忆、删除：$cancelBounds / $rememberBounds / $deleteBounds", cancelBounds.bottom <= rememberBounds.top && rememberBounds.bottom <= deleteBounds.top)
        } else if (direction == LayoutDirection.Rtl) {
            assertTrue("RTL 中记忆按钮位于取消与删除之间", deleteBounds.right <= rememberBounds.left && rememberBounds.right <= cancelBounds.left)
        } else {
            assertTrue("记忆按钮位于取消与删除之间", cancelBounds.right <= rememberBounds.left && rememberBounds.right <= deleteBounds.left)
        }
        cancel.performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertTrue(confirmed.isEmpty()) }
        delete.performClick()
        compose.runOnIdle { assertEquals(listOf(false), confirmed) }
        remember.performClick()
        compose.runOnIdle { assertEquals(listOf(false, true), confirmed) }
        capture("records-${mode.name}-${direction.name}-$stacked")

        for (next in listOf(
            DownloadsDialogState.DeleteGroup(1, true),
            DownloadsDialogState.DeleteTask(2, true),
            DownloadsDialogState.BatchDelete(setOf(1, 2), true),
        )) {
            compose.runOnIdle { state = next }
            compose.onNode(isToggleable()).assertDoesNotExist()
            compose.onNodeWithText("删除文件").assertExists()
            remember.performClick()
            compose.runOnIdle { assertEquals(true, confirmed.last()) }
        }
        capture("files-${mode.name}-${direction.name}-$stacked")
    }

    private fun capture(name: String) {
        val output = File("../.tmp/swipe-polish/$name.png")
        output.parentFile!!.mkdirs()
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
