package com.happycola233.bilitools.ui.settings

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.graphics.drawable.toBitmap
import com.happycola233.bilitools.BiliToolsApp
import com.happycola233.bilitools.core.AppLanguage
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.DownloadNotificationState
import com.happycola233.bilitools.data.LiveUpdateIcon
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.download.DownloadNotificationManager
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class LiveUpdateIconSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun iconSelectionIsSavedInLightTheme() = verifySelection(AppThemeMode.Light)

    @Test fun iconSelectionIsSavedInDarkTheme() = verifySelection(AppThemeMode.Dark)

    @Test fun liveUpdateUsesTheWhiteCircleWithItsOriginalColorsWithoutADuplicateLargeIcon() {
        val app = RuntimeEnvironment.getApplication()
        val notification = DownloadNotificationManager(app).buildProgressNotification(
            DownloadNotificationState(sessionId = 1, taskIds = setOf(1), runningCount = 1),
        )
        assertNull(notification.getLargeIcon())
        val bitmap = notification.smallIcon.loadDrawable(app)!!.toBitmap(96, 96)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(Color.WHITE, bitmap.getPixel(48, 12))
        assertEquals(Color.WHITE, bitmap.getPixel(48, 48))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.any { Color.alpha(it) == 255 && Color.blue(it) > Color.red(it) + 80 })
        saveImage(bitmap, "bilitools-icon")
    }

    private fun verifySelection(mode: AppThemeMode) {
        val app = RuntimeEnvironment.getApplication() as BiliToolsApp
        val repository = app.container.settingsRepository
        assertEquals(LiveUpdateIcon.BiliTools, repository.currentSettings().liveUpdateIcon)
        compose.setContent {
            val savedSettings by repository.settings.collectAsState()
            val settings = savedSettings.copy(themeMode = mode)
            BiliToolsTheme(settings) {
                GeneralSettingsScreen(
                    settings = settings,
                    liveUpdateSupported = true,
                    onLiveActivityStyleNotificationChange = repository::setLiveActivityStyleNotificationEnabled,
                    onLiveUpdateIconChange = repository::setLiveUpdateIcon,
                    onHapticFeedbackLevelChange = repository::setHapticFeedbackLevel,
                    onLaunchSplashAnimationChange = repository::setLaunchSplashAnimationEnabled,
                    selectedLanguage = AppLanguage.System,
                    onOpenLanguage = {},
                    onBack = {},
                )
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("BiliTools"))
        compose.onNodeWithText("BiliTools").assertIsSelected()
        compose.onNodeWithText("下载箭头").performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(LiveUpdateIcon.Download, SettingsRepository(app).currentSettings().liveUpdateIcon)
        }
        saveImage(compose.onRoot().captureToImage().asAndroidBitmap(), "settings-${mode.name.lowercase()}-download")
        compose.onNodeWithText("BiliTools").performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(LiveUpdateIcon.BiliTools, SettingsRepository(app).currentSettings().liveUpdateIcon)
        }
        saveImage(compose.onRoot().captureToImage().asAndroidBitmap(), "settings-${mode.name.lowercase()}-bilitools")
    }

    private fun saveImage(bitmap: Bitmap, name: String) {
        val output = File("../.tmp/live-update-icon/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
