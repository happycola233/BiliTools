package com.happycola233.bilitools.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.BiliToolsApp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.SubtitleLyricsMode
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
class MetadataSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun subtitleConsentAndParentSwitchesInLightTheme() = verify(AppThemeMode.Light)
    @Test fun subtitleConsentAndParentSwitchesInDarkTheme() = verify(AppThemeMode.Dark)

    @Test fun downloadSubpagesReturnToDownloadSettingsInLightTheme() = verifyDownloadEntryPoints(AppThemeMode.Light)
    @Test fun downloadSubpagesReturnToDownloadSettingsInDarkTheme() = verifyDownloadEntryPoints(AppThemeMode.Dark)

    @Test
    fun explanationKeepsTextStationaryAndRemovesSpacingWithinTheCollapseAnimation() {
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    LyricsLanguageExplanation()
                    Text("后续内容")
                }
            }
        }
        val after = compose.onNodeWithText("后续内容")
        val collapsedTop = after.getUnclippedBoundsInRoot().top.value
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("歌词语言如何选择").performClick()
        compose.mainClock.advanceTimeBy(96)
        val firstParagraph = "字幕转歌词时，每个音频文件只嵌入一种语言。"
        val midwayBounds = compose.onNodeWithText(firstParagraph).getUnclippedBoundsInRoot()
        capture("expansion-midway")
        compose.mainClock.advanceTimeBy(1500)
        val expandedBounds = compose.onNodeWithText(firstParagraph).getUnclippedBoundsInRoot()
        assertEquals("展开时文字不横移", expandedBounds.left.value, midwayBounds.left.value, 0.5f)
        assertEquals("展开时文字不纵移", expandedBounds.top.value, midwayBounds.top.value, 0.5f)
        assertEquals("展开时不改变文本换行宽度", expandedBounds.right.value, midwayBounds.right.value, 0.5f)
        capture("expansion-open")

        compose.onNodeWithText("歌词语言如何选择").performClick()
        var previousTop = after.getUnclippedBoundsInRoot().top.value
        var removalObserved = false
        repeat(100) {
            compose.mainClock.advanceTimeByFrame()
            val currentTop = after.getUnclippedBoundsInRoot().top.value
            assertTrue("收起时高度不反弹", currentTop <= previousTop + 0.5f)
            if (!removalObserved && compose.onAllNodesWithText(firstParagraph).fetchSemanticsNodes().isEmpty()) {
                // 内容退出组合的这一帧不能再单独移除外层间距，造成最后一次 8dp 跳动。
                assertEquals("动画结束后不能再次收紧", previousTop, currentTop, 1f)
                removalObserved = true
            }
            previousTop = currentTop
        }
        assertTrue("收起后移除展开内容", removalObserved)
        assertEquals(collapsedTop, previousTop, 0.5f)
        compose.mainClock.advanceTimeBy(500)
        assertEquals("收起后位置保持稳定", previousTop, after.getUnclippedBoundsInRoot().top.value, 0.5f)
        capture("expansion-closed")
        compose.mainClock.autoAdvance = true
    }

    private fun verifyDownloadEntryPoints(theme: AppThemeMode) {
        val container = (RuntimeEnvironment.getApplication() as BiliToolsApp).container
        val viewModel = SettingsViewModel(container.settingsRepository, container.issueReportRepository)
        viewModel.navigateTo(SettingsDestination.Download)
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = theme)) {
                DownloadSettingsScreen(
                    settings = AppSettings(themeMode = theme),
                    onOpenDownloadLocationPicker = {},
                    onOpenDefaultDownloadQuality = { viewModel.navigateTo(SettingsDestination.DefaultDownloadQuality) },
                    onAddMetadataChange = {},
                    onOpenMetadataOptions = { viewModel.navigateTo(SettingsDestination.Metadata) },
                    onConvertXmlDanmakuToAssChange = {},
                    onConvertAudioToMp3Change = {},
                    onConvertVideoToMp4Change = {},
                    onMaxConcurrentDownloadsChange = {},
                    onConfirmCellularChange = {},
                    onHideInAlbumChange = {},
                    onBack = viewModel::popDestination,
                )
            }
        }
        capture("download-${theme.name.lowercase()}")
        compose.onNodeWithText("默认下载质量").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(SettingsDestination.Main, SettingsDestination.Download, SettingsDestination.DefaultDownloadQuality),
                viewModel.backStack.toList(),
            )
            viewModel.popDestination()
            assertEquals(SettingsDestination.Download, viewModel.backStack.last())
        }
        compose.onNodeWithText("元数据选项").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(SettingsDestination.Main, SettingsDestination.Download, SettingsDestination.Metadata),
                viewModel.backStack.toList(),
            )
            viewModel.popDestination()
            assertEquals(SettingsDestination.Download, viewModel.backStack.last())
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h720dp")
    fun lyricsControlsRemainUsableWithLargeTextOnNarrowScreen() = verify(AppThemeMode.Light, fontScale = 1.6f)

    private fun verify(theme: AppThemeMode, fontScale: Float = 1f) {
        var settings by mutableStateOf(AppSettings(themeMode = theme))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BiliToolsTheme(settings) {
                    MetadataSettingsScreen(settings, { settings = settings.copy(metadata = it) }, {})
                }
            }
        }
        compose.onNodeWithText("不使用").performScrollTo().assertIsOn()
        capture("settings-${theme.name.lowercase()}-$fontScale")
        compose.onNodeWithText("允许 AI").performScrollTo().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(SubtitleLyricsMode.PreferManual, settings.metadata.subtitleLyrics) }
        compose.onNodeWithText("允许使用 B 站 AI 字幕；自动选择时优先人工字幕。").assertExists()
        compose.onNodeWithText("歌词语言如何选择").performScrollTo().performClick()
        compose.onNodeWithText("字幕转歌词时，每个音频文件只嵌入一种语言。").performScrollTo().assertExists()
        compose.onNodeWithText("其余情况将自动选择", substring = true).performScrollTo().assertExists()
        capture("language-${theme.name.lowercase()}-$fontScale")
        compose.onNodeWithText("歌词语言如何选择").performScrollTo().performClick()
        compose.runOnIdle { settings = settings.copy(metadata = settings.metadata.copy(embedLyrics = false)) }
        compose.onNodeWithText("允许 AI").performScrollTo().assertIsNotEnabled().assertIsOn()
        capture("disabled-${theme.name.lowercase()}-$fontScale")
        compose.runOnIdle { settings = settings.copy(metadata = settings.metadata.copy(embedLyrics = true), addMetadata = false) }
        compose.onNodeWithText("仅人工").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(SubtitleLyricsMode.PreferManual, settings.metadata.subtitleLyrics) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("将合集作为专辑"))
        compose.onNodeWithText("将合集作为专辑").performScrollTo()
        capture("fields-${theme.name.lowercase()}-$fontScale")
    }

    private fun capture(name: String) {
        val output = File("../.tmp/metadata-ui-refine/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
