package com.happycola233.bilitools.ui.parse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.DownloadMetadataSettings
import com.happycola233.bilitools.data.SubtitleLyricsMode
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParseLyricsSummaryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun explicitLanguageAutoSelectionAndHiddenBatchLanguageHaveMatchingDescriptions() {
        var state by mutableStateOf(videoState())
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                ParseLyricsSummary(state, DownloadMetadataSettings(subtitleLyrics = SubtitleLyricsMode.ManualOnly))
            }
        }
        compose.onNodeWithText("使用字幕语言“英语”", substring = true).assertExists()
        compose.runOnIdle { state = state.copy(subtitleEnabled = false) }
        compose.onNodeWithText("每个文件自动选用一种人工字幕", substring = true).assertExists()
        compose.runOnIdle { state = state.copy(subtitleEnabled = true, subtitleLanguageSelection = SubtitleLanguageSelection.All) }
        compose.onNodeWithText("每个文件自动选用一种人工字幕", substring = true).assertExists()
        compose.runOnIdle {
            state = state.copy(
                selectedItemIndices = listOf(0, 1),
                subtitleLanguageSelection = SubtitleLanguageSelection.Language("en"),
            )
        }
        compose.onNodeWithText("每个文件自动选用一种人工字幕", substring = true).assertExists()
        compose.onNodeWithText("使用字幕语言", substring = true).assertDoesNotExist()
    }

    @Test
    fun excludedAiAndEnabledAiAreExplainedWithoutClaimingToUseAnotherLanguage() {
        var settings by mutableStateOf(DownloadMetadataSettings(subtitleLyrics = SubtitleLyricsMode.ManualOnly))
        val state = videoState().copy(subtitleLanguageSelection = SubtitleLanguageSelection.Language("ai-zh"))
        compose.setContent {
            BiliToolsTheme(AppSettings()) { ParseLyricsSummary(state, settings) }
        }
        compose.onNodeWithText("因此不写入歌词", substring = true).assertExists()
        compose.runOnIdle { settings = settings.copy(subtitleLyrics = SubtitleLyricsMode.PreferManual) }
        compose.onNodeWithText("使用字幕语言“中文（AI）”，允许人工或 AI 字幕", substring = true).assertExists()
    }

    @Test
    fun originalMusicLyricsDoNotFollowVideoSubtitleSettings() {
        val state = videoState().copy(items = listOf(item(MediaType.Music)), selectedItemIndices = listOf(0))
        compose.setContent {
            BiliToolsTheme(AppSettings()) { ParseLyricsSummary(state, DownloadMetadataSettings()) }
        }
        compose.onNodeWithText("音频区歌曲嵌入自身的原始歌词（如有）。").assertExists()
        compose.onNodeWithText("字幕", substring = true).assertDoesNotExist()
    }

    @Test
    fun masterSwitchLyricsSwitchAndOutputTypeAreRespected() {
        var settings by mutableStateOf<DownloadMetadataSettings?>(null)
        var state by mutableStateOf(videoState())
        compose.setContent {
            BiliToolsTheme(AppSettings()) { ParseLyricsSummary(state, settings) }
        }
        compose.onNodeWithText("元数据已关闭", substring = true).assertExists()
        compose.runOnIdle { settings = DownloadMetadataSettings(embedLyrics = false) }
        compose.onNodeWithText("歌词嵌入已关闭", substring = true).assertExists()
        compose.runOnIdle { settings = DownloadMetadataSettings() }
        compose.onNodeWithText("视频音轨不嵌入歌词", substring = true).assertExists()
        listOf(OutputType.AudioVideo, OutputType.VideoOnly, null).forEach { output ->
            compose.runOnIdle { state = state.copy(outputType = output) }
            compose.onNodeWithText("内嵌歌词").assertDoesNotExist()
        }
    }

    private fun videoState() = ParseUiState(
        outputType = OutputType.AudioOnly,
        items = listOf(item(MediaType.Video), item(MediaType.Video)),
        selectedItemIndices = listOf(0),
        subtitleEnabled = true,
        subtitleLanguageSelection = SubtitleLanguageSelection.Language("en"),
        subtitleList = listOf(
            SubtitleInfo("en", "英语", "https://example.com/en"),
            SubtitleInfo("ai-zh", "中文（AI）", "https://example.com/ai", isAi = true),
        ),
    )

    private fun item(type: MediaType) = MediaItem(
        title = "标题", coverUrl = "", description = "", url = "https://example.com/media",
        duration = 60, pubTime = 0, type = type, isTarget = true, index = 0,
    )
}
