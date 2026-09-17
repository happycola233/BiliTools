package com.happycola233.bilitools.ui.parse

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.core.AudioQualities
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.PlayUrlInfo
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoStream
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
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
class ParseEmbeddingControlsTest {
    @get:Rule val compose = createComposeRule()

    private val subtitles = listOf(
        SubtitleInfo(lan = "zh-Hans", name = "中文（简体）", url = "https://example.com/zh"),
        SubtitleInfo(lan = "ai-en", name = "英语（自动生成）", url = "https://example.com/en", isAi = true),
    )
    private val item = MediaItem(
        title = "测试视频", coverUrl = "https://example.com/cover.jpg", description = "简介",
        url = "https://www.bilibili.com/video/BV1test", duration = 60, pubTime = 1_700_000_000L,
        type = MediaType.Video, isTarget = true, index = 0, aid = 1, bvid = "BV1test", cid = 2,
    )
    private val info = MediaInfo(type = MediaType.Video, id = "BV1test", nfo = MediaNfo(showTitle = "测试视频"), list = listOf(item))
    private val video = VideoStream(id = 80, format = StreamFormat.Dash, width = 1920, height = 1080, codec = VideoCodec.Avc, url = "v")
    private val audio = AudioStream(id = 30280, url = "a")
    private val readyState = ParseUiState(
        mediaInfo = info,
        items = info.list,
        selectedItemIndices = listOf(0),
        outputType = OutputType.AudioVideo,
        playUrlInfo = PlayUrlInfo(StreamFormat.Dash, video = listOf(video), audio = listOf(audio)),
        videoStreams = listOf(video),
        audioStreams = listOf(audio),
        resolutions = listOf(QualityOption(80, "1080P 高清")),
        codecs = listOf(CodecOption(VideoCodec.Avc, "AVC (H.264)")),
        audioBitrates = listOf(AudioOption(30280, "192K")),
        selectedResolutionId = 80,
        selectedCodec = VideoCodec.Avc,
        selectedAudioId = 30280,
        subtitleList = subtitles,
        subtitleLoadStatus = SubtitleLoadStatus.Ready,
        subtitleLanguageSelection = SubtitleLanguageSelection.All,
        embedSubtitlesEnabled = true,
        embedSubtitleLanguages = setOf("zh-Hans", "ai-en"),
        embedLyricsEnabled = true,
        embedLyricsLanguage = "zh-Hans",
        isLoggedIn = true,
    )

    @Test fun subtitleTracksInLightTheme() = verifySubtitleTracks(AppThemeMode.Light)
    @Test fun subtitleTracksInDarkTheme() = verifySubtitleTracks(AppThemeMode.Dark)
    @Test fun lyricsInLightTheme() = verifyLyrics(AppThemeMode.Light)
    @Test fun lyricsInDarkTheme() = verifyLyrics(AppThemeMode.Dark)

    /** 视频输出：多语言可勾选，取消勾选一种语言只回调那一种；FLV 直出时整组禁用并给出出路。 */
    private fun verifySubtitleTracks(theme: AppThemeMode) {
        var state by mutableStateOf(readyState)
        setContent(theme, { state }) { state = it }
        val list = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
        list.performScrollToNode(hasText("内嵌字幕"))
        compose.onNodeWithText("嵌入到视频文件").performScrollTo()
        checkbox("嵌入到视频文件").assertIsOn()
        compose.onNodeWithText("字幕作为独立轨道写入，播放时可切换或关闭。").performScrollTo().assertExists()
        compose.onNodeWithText("中文（简体）").performScrollTo().assertIsSelected()
        capture("embed-subtitles-${theme.name.lowercase()}")

        compose.onNodeWithText("英语（自动生成）").performClick()
        compose.runOnIdle { assertEquals(setOf("zh-Hans"), state.embedSubtitleLanguages) }
        compose.onNodeWithText("嵌入到视频文件").performClick()
        compose.runOnIdle { assertEquals(false, state.embedSubtitlesEnabled) }
        compose.onNodeWithText("中文（简体）").assertDoesNotExist()
        capture("embed-subtitles-off-${theme.name.lowercase()}")

        // FLV 音视频没有地方放字幕轨；仅视频仍是 DASH 的 MP4，可以嵌入。
        state = readyState.copy(
            format = StreamFormat.Flv,
            playUrlInfo = PlayUrlInfo(StreamFormat.Flv, video = listOf(video.copy(format = StreamFormat.Flv))),
            videoStreams = listOf(video.copy(format = StreamFormat.Flv)),
            audioStreams = emptyList(),
        )
        list.performScrollToNode(hasText("内嵌字幕"))
        compose.onNodeWithText("嵌入到视频文件").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("FLV 文件不能存放字幕轨。", substring = true).performScrollTo().assertExists()
        capture("embed-subtitles-flv-${theme.name.lowercase()}")

        // 批量下载不再逐条列语言，改为是否连同 AI 字幕一起嵌入。
        state = readyState.copy(items = listOf(item, item.copy(index = 1, cid = 3)), selectedItemIndices = listOf(0, 1))
        list.performScrollToNode(hasText("内嵌字幕"))
        compose.onNodeWithText("包含 AI 字幕").performScrollTo().assertIsEnabled()
        checkbox("包含 AI 字幕").assertIsOn()
        compose.onNodeWithText("每个条目嵌入自己的全部可用字幕，播放时可切换或关闭。").performScrollTo().assertExists()
        compose.onNodeWithText("中文（简体）").assertDoesNotExist()
        compose.onNodeWithText("包含 AI 字幕").performClick()
        compose.runOnIdle { assertEquals(false, state.embedIncludeGeneratedSubtitles) }
        capture("embed-subtitles-batch-${theme.name.lowercase()}")
    }

    /** 音频输出：歌词只能选一种语言（单选 chip）；杜比全景声不能写歌词时说明改法。 */
    private fun verifyLyrics(theme: AppThemeMode) {
        var state by mutableStateOf(readyState.copy(outputType = OutputType.AudioOnly))
        setContent(theme, { state }) { state = it }
        val list = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
        list.performScrollToNode(hasText("内嵌歌词"))
        compose.onNodeWithText("写入音频文件").performScrollTo()
        checkbox("写入音频文件").assertIsOn()
        compose.onNodeWithText("所选字幕将转换为歌词写入音频文件，只能选择一种语言。").performScrollTo().assertExists()
        compose.onNodeWithText("中文（简体）").performScrollTo().assertIsSelected()
        capture("embed-lyrics-${theme.name.lowercase()}")
        compose.onNodeWithText("英语（自动生成）").performClick()
        compose.runOnIdle { assertEquals("ai-en", state.embedLyricsLanguage) }
        compose.onNodeWithText("英语（自动生成）").assertIsSelected()
        compose.onNodeWithText("嵌入到视频文件").assertDoesNotExist()

        state = state.copy(
            audioStreams = listOf(AudioStream(id = AudioQualities.DOLBY_ATMOS, url = "dolby")),
            audioBitrates = listOf(AudioOption(AudioQualities.DOLBY_ATMOS, "杜比全景声")),
            selectedAudioId = AudioQualities.DOLBY_ATMOS,
        )
        list.performScrollToNode(hasText("内嵌歌词"))
        compose.onNodeWithText("写入音频文件").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("杜比全景声音频不支持写入歌词。", substring = true).performScrollTo().assertExists()
        capture("embed-lyrics-dolby-${theme.name.lowercase()}")
    }

    /** 勾选项是可点击的整行，勾选状态在行内的 Checkbox 节点上。 */
    private fun checkbox(text: String) = compose.onNodeWithText(text).onChildren().filterToOne(isToggleable())

    private fun setContent(theme: AppThemeMode, state: () -> ParseUiState, update: (ParseUiState) -> Unit) {
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = theme)) {
                ParseScreenContent(
                    state = state(),
                    conversionSettings = ParseConversionSettings(),
                    inputText = "",
                    contentTopPadding = 0.dp,
                    externalMode = false,
                    subtitleCopyDialogEntries = null,
                    aiSummaryCopyDialogEntries = null,
                    onInputChange = {}, onPaste = {}, onParse = {}, onMediaTypeChange = {}, onDownload = {},
                    onSectionChange = {}, onOpenUpper = {}, onCopyResultContent = { _, _ -> },
                    onSelectAllItems = {}, onClearSelectedItems = {}, onLoadPrevPage = {}, onLoadNextPage = {},
                    onLoadPage = {}, onItemClick = {}, onItemSelectionChange = { _, _ -> },
                    onFormatChange = {}, onOutputTypeChange = {}, onCollectionModeChange = {},
                    onResolutionModeChange = {}, onResolutionChange = {}, onCodecChange = {},
                    onAudioBitrateModeChange = {}, onAudioBitrateChange = {},
                    onSubtitleEnabledChange = {}, onSubtitleLanguageChange = {},
                    onEmbedSubtitlesEnabledChange = { update(state().copy(embedSubtitlesEnabled = it)) },
                    onEmbedSubtitleLanguageChange = { lan, selected ->
                        val current = state()
                        update(current.copy(embedSubtitleLanguages = if (selected) current.embedSubtitleLanguages + lan else current.embedSubtitleLanguages - lan))
                    },
                    onEmbedLyricsEnabledChange = { update(state().copy(embedLyricsEnabled = it)) },
                    onEmbedLyricsLanguageChange = { update(state().copy(embedLyricsLanguage = it)) },
                    onEmbedIncludeGeneratedChange = { update(state().copy(embedIncludeGeneratedSubtitles = it)) },
                    onCopySubtitles = {}, onAiSummaryEnabledChange = {}, onCopyAiSummaries = {},
                    onNfoCollectionEnabledChange = {}, onNfoSingleEnabledChange = {},
                    onDanmakuLiveEnabledChange = {}, onDanmakuHistoryEnabledChange = {},
                    onDanmakuDateChange = {}, onDanmakuHourChange = {}, onImageSelectionChange = { _, _ -> },
                    onOpusContentEnabledChange = {}, onOpusImagesEnabledChange = {},
                    onDismissSubtitleCopyDialog = {}, onDismissAiSummaryCopyDialog = {},
                    onCopyCurrentSubtitle = {}, onCopyAllSubtitles = {}, onCopyCurrentAiSummary = {}, onCopyAllAiSummaries = {},
                    onDismissError = {},
                )
            }
        }
    }

    private fun capture(name: String) {
        val output = File("../.tmp/metadata-ui-refine/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
