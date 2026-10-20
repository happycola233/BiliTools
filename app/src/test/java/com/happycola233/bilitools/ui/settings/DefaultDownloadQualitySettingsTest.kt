package com.happycola233.bilitools.ui.settings

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.DownloadQualityMode
import com.happycola233.bilitools.ui.assertMovesInOneMotion
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 视口足够高，保证所有质量卡片都完整位于屏幕内。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h1600dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DefaultDownloadQualitySettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedResolutionPanelMovesCardsBelowInOneMotionInLightTheme() = verifyFixedResolutionPanel(AppThemeMode.Light)

    @Test fun fixedResolutionPanelMovesCardsBelowInOneMotionInDarkTheme() = verifyFixedResolutionPanel(AppThemeMode.Dark)

    /** 「固定」档位面板展开、收起时，与上方按钮组的间距一起动画，下方的视频编码卡片一次平滑到位。 */
    private fun verifyFixedResolutionPanel(theme: AppThemeMode) {
        var settings by mutableStateOf(AppSettings(themeMode = theme))
        compose.setContent {
            BiliToolsTheme(settings) {
                DefaultDownloadQualityScreen(
                    settings = settings,
                    onDefaultDownloadQualityChange = { settings = settings.copy(defaultDownloadQuality = it) },
                    onBack = {},
                )
            }
        }
        val codecTitleTop = { compose.onNodeWithText(CODEC_TITLE).fetchSemanticsNode().boundsInRoot.top }
        capture("default-quality-highest-${theme.name.lowercase()}")

        // 分辨率卡片排在音频卡片之前，两组按钮中的第一组属于分辨率。
        compose.assertMovesInOneMotion("展开固定分辨率", { clickFirst(MODE_FIXED) }, codecTitleTop)
        assertEquals(DownloadQualityMode.Fixed, settings.defaultDownloadQuality.resolutionMode)
        capture("default-quality-fixed-${theme.name.lowercase()}")

        compose.assertMovesInOneMotion("收起固定分辨率", { clickFirst(MODE_HIGHEST) }, codecTitleTop)
        assertEquals(DownloadQualityMode.Highest, settings.defaultDownloadQuality.resolutionMode)
    }

    private fun clickFirst(text: String) {
        compose.onAllNodesWithText(text).onFirst().performClick()
    }

    private fun capture(name: String) {
        val output = File("../.tmp/layout-motion/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val CODEC_TITLE = "视频编码"
        const val MODE_FIXED = "固定"
        const val MODE_HIGHEST = "最高"
    }
}
