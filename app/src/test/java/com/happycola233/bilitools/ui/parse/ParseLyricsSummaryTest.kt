package com.happycola233.bilitools.ui.parse

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.DownloadMetadataSettings
import com.happycola233.bilitools.data.SubtitleLyricsMode
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.SubtitleInfo
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
class ParseLyricsSummaryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun explicitLanguageAndAutomaticChoiceShowTheSelectedSubtitleWithoutReusingItForTheBatch() {
        var state by mutableStateOf(videoState())
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                ParseLyricsSummary(state, DownloadMetadataSettings(subtitleLyrics = SubtitleLyricsMode.ManualOnly))
            }
        }
        compose.onNodeWithText("将嵌入英语人工字幕").assertExists()
        compose.runOnIdle { state = state.copy(subtitleEnabled = false) }
        compose.onNodeWithText("将嵌入简体中文人工字幕").assertExists()
        compose.runOnIdle { state = state.copy(subtitleEnabled = true, subtitleLanguageSelection = SubtitleLanguageSelection.All) }
        compose.onNodeWithText("将嵌入简体中文人工字幕").assertExists()
        compose.runOnIdle {
            state = state.copy(
                selectedItemIndices = listOf(0, 1),
                subtitleLanguageSelection = SubtitleLanguageSelection.Language("en"),
            )
        }
        compose.onNodeWithText("内嵌歌词已开启").assertExists()
        compose.onNodeWithText("将嵌入", substring = true).assertDoesNotExist()
    }

    @Test
    fun previewUsesTheSameManualPriorityAndExplicitAiChoiceAsTheDownload() {
        var settings by mutableStateOf(DownloadMetadataSettings(subtitleLyrics = SubtitleLyricsMode.ManualOnly))
        var state by mutableStateOf(videoState().copy(subtitleLanguageSelection = SubtitleLanguageSelection.Language("ai-zh")))
        compose.setContent {
            BiliToolsTheme(AppSettings()) { ParseLyricsSummary(state, settings) }
        }
        compose.onNodeWithText("不嵌入歌词 · 未允许 AI 字幕").assertExists()
        compose.runOnIdle { settings = settings.copy(subtitleLyrics = SubtitleLyricsMode.PreferManual) }
        compose.onNodeWithText("将嵌入简体中文 AI 字幕").assertExists()
        compose.runOnIdle {
            state = state.copy(
                subtitleEnabled = false,
                subtitleList = state.subtitleList.filterNot { it.lan == "zh-Hans" },
            )
        }
        compose.onNodeWithText("将嵌入英语人工字幕").assertExists()
        compose.runOnIdle {
            state = state.copy(subtitleList = listOf(SubtitleInfo("ai-en", "英语（自动生成）", "https://example.com/ai-en", isAi = true)))
        }
        compose.onNodeWithText("将嵌入英语 AI 字幕").assertExists()
    }

    @Test
    fun loadingFailureAndMissingSubtitlesAreDistinct() {
        var state by mutableStateOf(videoState().copy(subtitleLoadStatus = SubtitleLoadStatus.Loading))
        var settings by mutableStateOf(DownloadMetadataSettings(subtitleLyrics = SubtitleLyricsMode.PreferManual))
        compose.setContent { BiliToolsTheme(AppSettings()) { ParseLyricsSummary(state, settings) } }
        compose.onNodeWithText("正在检查可用字幕…").assertExists()
        compose.runOnIdle { state = state.copy(subtitleLoadStatus = SubtitleLoadStatus.Failed) }
        compose.onNodeWithText("暂未获取到字幕").assertExists()
        compose.runOnIdle { state = state.copy(subtitleLoadStatus = SubtitleLoadStatus.Ready, subtitleList = emptyList()) }
        compose.onNodeWithText("无可用字幕，不嵌入歌词").assertExists()
        compose.runOnIdle { settings = settings.copy(subtitleLyrics = SubtitleLyricsMode.ManualOnly) }
        compose.onNodeWithText("无可用人工字幕，不嵌入歌词").assertExists()
    }

    @Test
    fun originalMusicLyricsDoNotFollowVideoSubtitleSettings() {
        val state = videoState().copy(items = listOf(item(MediaType.Music)), selectedItemIndices = listOf(0))
        compose.setContent {
            BiliToolsTheme(AppSettings()) { ParseLyricsSummary(state, DownloadMetadataSettings()) }
        }
        compose.onNodeWithText("将嵌入原始歌词（如有）").assertExists()
        compose.onNodeWithText("字幕", substring = true).assertDoesNotExist()
    }

    @Test
    fun masterSwitchLyricsSwitchAndOutputTypeAreRespected() {
        var settings by mutableStateOf<DownloadMetadataSettings?>(null)
        var state by mutableStateOf(videoState())
        compose.setContent {
            BiliToolsTheme(AppSettings()) { ParseLyricsSummary(state, settings) }
        }
        compose.onNodeWithText("内嵌歌词已关闭").assertExists()
        compose.runOnIdle { settings = DownloadMetadataSettings(embedLyrics = false) }
        compose.onNodeWithText("内嵌歌词已关闭").assertExists()
        compose.runOnIdle { settings = DownloadMetadataSettings() }
        compose.onNodeWithText("内嵌歌词已关闭").assertExists()
        listOf(OutputType.AudioVideo, OutputType.VideoOnly, null).forEach { output ->
            compose.runOnIdle { state = state.copy(outputType = output) }
            compose.onNodeWithText("内嵌歌词已关闭").assertDoesNotExist()
            compose.onNodeWithContentDescription(helpTitle).assertDoesNotExist()
        }
    }

    @Test
    fun switchingOutputAnimatesTheWholeRowAndItsSpacing() {
        var state by mutableStateOf(videoState().copy(outputType = OutputType.AudioVideo))
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                Column(Modifier.padding(16.dp)) {
                    Text("切换输出类型", Modifier.clickable {
                        state = state.copy(
                            outputType = if (state.outputType == OutputType.AudioOnly) OutputType.VideoOnly else OutputType.AudioOnly,
                        )
                    })
                    ParseLyricsSummary(state, DownloadMetadataSettings(subtitleLyrics = SubtitleLyricsMode.ManualOnly))
                    Text("后续内容")
                }
            }
        }
        val after = compose.onNodeWithText("后续内容")
        val collapsedTop = after.getUnclippedBoundsInRoot().top.value
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("切换输出类型").performClick()
        compose.mainClock.advanceTimeBy(80)
        val midwayTop = after.getUnclippedBoundsInRoot().top.value
        capture("expanding", compose.onRoot())
        compose.mainClock.advanceTimeBy(1500)
        val expandedTop = after.getUnclippedBoundsInRoot().top.value
        assertTrue(
            "提示随动画展开：收起=" + collapsedTop + "，途中=" + midwayTop + "，展开=" + expandedTop,
            midwayTop > collapsedTop && midwayTop < expandedTop,
        )
        compose.onNodeWithText("切换输出类型").performClick()
        compose.mainClock.advanceTimeBy(80)
        val closingTop = after.getUnclippedBoundsInRoot().top.value
        assertTrue("收起也保留过渡", closingTop > collapsedTop && closingTop < expandedTop)
        compose.mainClock.advanceTimeBy(1500)
        assertEquals("提示收起后不残留间距", collapsedTop, after.getUnclippedBoundsInRoot().top.value, 0.5f)
        compose.onNodeWithContentDescription(helpTitle).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun helpPopoverInLightTheme() = verifyHelp(AppThemeMode.Light)
    @Test fun helpPopoverInDarkTheme() = verifyHelp(AppThemeMode.Dark)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h720dp", fontScale = 1.6f)
    fun helpPopoverWithLargeTextInNarrowLightTheme() = verifyHelp(AppThemeMode.Light)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h720dp", fontScale = 1.6f)
    fun helpPopoverWithLargeTextInNarrowDarkTheme() = verifyHelp(AppThemeMode.Dark)

    private fun verifyHelp(theme: AppThemeMode) {
        val fontScale = RuntimeEnvironment.getFontScale()
        var state by mutableStateOf(videoState())
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = theme)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("输出类型", style = MaterialTheme.typography.titleSmall)
                        Text("音视频      仅视频      仅音频")
                        ParseLyricsSummary(state, DownloadMetadataSettings(subtitleLyrics = SubtitleLyricsMode.ManualOnly))
                        Text("格式", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        val help = compose.onNodeWithContentDescription(helpTitle)
        compose.onNodeWithText(paragraphs.first()).assertDoesNotExist()
        capture("status-" + theme.name.lowercase() + "-" + fontScale, compose.onRoot())
        help.performClick()
        compose.onNode(isPopup()).assertExists()
        compose.onNodeWithText(helpTitle).assertExists()
        capture("help-" + theme.name.lowercase() + "-" + fontScale, compose.onNode(isPopup()))
        paragraphs.forEach { compose.onNodeWithText(it).performScrollTo().assertExists() }
        capture("help-end-" + theme.name.lowercase() + "-" + fontScale, compose.onNode(isPopup()))
        compose.runOnIdle { state = state.copy(outputType = OutputType.VideoOnly) }
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(outputType = OutputType.AudioOnly) }
        compose.onNodeWithContentDescription(helpTitle).assertExists()
        compose.onNode(isPopup()).assertDoesNotExist()
    }

    private fun capture(name: String, node: SemanticsNodeInteraction) {
        val output = File("../.tmp/lyrics-preview/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun videoState() = ParseUiState(
        outputType = OutputType.AudioOnly,
        items = listOf(item(MediaType.Video), item(MediaType.Video)),
        selectedItemIndices = listOf(0),
        subtitleEnabled = true,
        subtitleLoadStatus = SubtitleLoadStatus.Ready,
        subtitleLanguageSelection = SubtitleLanguageSelection.Language("en"),
        subtitleList = listOf(
            SubtitleInfo("en", "英语", "https://example.com/en"),
            SubtitleInfo("zh-Hans", "中文（简体）", "https://example.com/zh"),
            SubtitleInfo("ai-zh", "中文（AI）", "https://example.com/ai", isAi = true),
        ),
    )

    private fun item(type: MediaType) = MediaItem(
        title = "标题", coverUrl = "", description = "", url = "https://example.com/media",
        duration = 60, pubTime = 0, type = type, isTarget = true, index = 0,
        aid = 1L, cid = 2L,
    )

    private val helpTitle = "APP 将如何选择歌词语言？"
    private val paragraphs = listOf(
        "每个音频文件只嵌入一种语言的歌词。",
        "下载单个条目时，若额外下载「字幕」并指定了一种语言，歌词将使用该语言。",
        "其余情况将自动选择一种语言，按简体中文、繁体中文、其他语言的顺序选择。",
        "音频区歌曲使用自身的原始歌词，不受字幕语言选择影响。音视频和仅视频文件不嵌入歌词。",
    )
}
