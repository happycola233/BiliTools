package com.happycola233.bilitools.ui.parse

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
        SubtitleInfo(lan = "ai-en", name = "英语", url = "https://example.com/en", isAi = true),
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
        embedSubtitleSelection = SubtitleLanguageSelection.Languages(setOf("zh-Hans", "ai-en")),
        subtitleTargetCount = 1,
        subtitleCompletedCount = 1,
        subtitleAvailableCounts = mapOf("zh-Hans" to 1, "ai-en" to 1),
        embedLyricsEnabled = true,
        embedLyricsLanguage = "zh-Hans",
        isLoggedIn = true,
    )

    @Test fun subtitleTracksInLightTheme() = verifySubtitleTracks(AppThemeMode.Light)
    @Test fun subtitleTracksInDarkTheme() = verifySubtitleTracks(AppThemeMode.Dark)
    @Test fun lyricsInLightTheme() = verifyLyrics(AppThemeMode.Light)
    @Test fun lyricsInDarkTheme() = verifyLyrics(AppThemeMode.Dark)
    @Test fun externalSubtitleSelectionInLightTheme() = verifyExternalSubtitles(AppThemeMode.Light)
    @Test fun externalSubtitleSelectionInDarkTheme() = verifyExternalSubtitles(AppThemeMode.Dark)

    private fun verifyExternalSubtitles(theme: AppThemeMode) {
        var state by mutableStateOf(readyState.copy(subtitleEnabled = true, embedSubtitlesEnabled = false))
        val savedEmbeddingSelection = state.embedSubtitleSelection
        setContent(theme, { state }) { state = it }
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("保存字幕文件"))
        openLanguagePicker("字幕语言")
        dialogText("中文（简体）").assertIsOn()
        dialogText("英语").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(SubtitleLanguageSelection.All, state.subtitleLanguageSelection) }
        capture("external-subtitles-sheet-${theme.name.lowercase()}", dialog = true)
        dialogText("取消").performClick()
        compose.runOnIdle { assertEquals(SubtitleLanguageSelection.All, state.subtitleLanguageSelection) }

        openLanguagePicker("字幕语言")
        dialogText("英语").assertIsOn().performClick()
        dialogText("完成").performClick()
        compose.runOnIdle {
            assertEquals(SubtitleLanguageSelection.Languages(setOf("zh-Hans")), state.subtitleLanguageSelection)
            assertEquals(savedEmbeddingSelection, state.embedSubtitleSelection)
        }
        compose.onNode(hasText("英语") and isToggleable()).assertDoesNotExist()
        compose.onNodeWithText("复制字幕").performScrollTo().assertIsEnabled()
        capture("external-subtitles-page-${theme.name.lowercase()}")
    }

    /** 视频输出：逐项选择保持一致，FLV明确告知本次改用MP4，批量不再改变所选语言。 */
    private fun verifySubtitleTracks(theme: AppThemeMode) {
        var state by mutableStateOf(readyState)
        setContent(theme, { state }) { state = it }
        val list = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
        list.performScrollToNode(hasText("内嵌字幕"))
        compose.onNodeWithText("嵌入到视频文件").performScrollTo()
        checkbox("嵌入到视频文件").assertIsOn()
        openLanguagePicker("字幕语言")
        dialogText("中文（简体）").assertIsOn()
        dialogText("英语").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(readyState.embedSubtitleSelection, state.embedSubtitleSelection) }
        capture("embed-subtitles-sheet-${theme.name.lowercase()}", dialog = true)
        dialogText("完成").performClick()
        compose.runOnIdle { assertEquals(SubtitleLanguageSelection.Languages(setOf("zh-Hans")), state.embedSubtitleSelection) }
        capture("embed-subtitles-page-${theme.name.lowercase()}")
        compose.onNodeWithText("嵌入到视频文件").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false, state.embedSubtitlesEnabled) }
        compose.onNodeWithText("字幕语言").assertDoesNotExist()
        capture("embed-subtitles-off-${theme.name.lowercase()}")

        // FLV开启内嵌时由下载流程为本次任务改用MP4。
        state = readyState.copy(
            format = StreamFormat.Flv,
            playUrlInfo = PlayUrlInfo(StreamFormat.Flv, video = listOf(video.copy(format = StreamFormat.Flv))),
            videoStreams = listOf(video.copy(format = StreamFormat.Flv)),
            audioStreams = emptyList(),
        )
        list.performScrollToNode(hasText("内嵌字幕"))
        compose.onNodeWithText("嵌入到视频文件").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("内嵌字幕后将保存为 MP4。").performScrollTo().assertExists()
        capture("embed-subtitles-flv-${theme.name.lowercase()}")
        compose.onNodeWithText("嵌入到视频文件").performScrollTo().performClick()
        compose.onNodeWithText("内嵌字幕后将保存为 MP4。").assertDoesNotExist()

        // 批量沿用同一选择面板，来源覆盖数量不占用解析页的主要空间。
        state = readyState.copy(
            items = listOf(item, item.copy(index = 1, cid = 3)), selectedItemIndices = listOf(0, 1),
            subtitleTargetCount = 2, subtitleCompletedCount = 2,
            subtitleAvailableCounts = mapOf("zh-Hans" to 2, "ai-en" to 1),
        )
        openLanguagePicker("字幕语言")
        dialogText("中文（简体）").assertIsOn()
        dialogText("英语").assertIsOn()
        dialogText("1 / 2 个条目可用").assertExists()
        capture("embed-subtitles-batch-sheet-${theme.name.lowercase()}", dialog = true)
        dialogText("取消").performClick()
        capture("embed-subtitles-batch-page-${theme.name.lowercase()}")
    }

    /** 音频输出：歌词单选，杜比可保留音轨并写入歌词。 */
    private fun verifyLyrics(theme: AppThemeMode) {
        var state by mutableStateOf(readyState.copy(outputType = OutputType.AudioOnly))
        var conversion by mutableStateOf(ParseConversionSettings())
        setContent(theme, { state }, { conversion }) { state = it }
        val list = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
        list.performScrollToNode(hasText("内嵌歌词"))
        compose.onNodeWithText("嵌入到音频文件").performScrollTo()
        checkbox("嵌入到音频文件").assertIsOn()
        openLanguagePicker("歌词语言")
        dialogText("中文（简体）").assertIsSelected()
        dialogText("英语").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("zh-Hans", state.embedLyricsLanguage) }
        capture("embed-lyrics-sheet-${theme.name.lowercase()}", dialog = true)
        dialogText("取消").performClick()
        compose.runOnIdle { assertEquals("zh-Hans", state.embedLyricsLanguage) }

        openLanguagePicker("歌词语言")
        dialogText("中文（简体）").assertIsSelected()
        dialogText("英语").performClick()
        dialogText("完成").performClick()
        compose.runOnIdle { assertEquals("ai-en", state.embedLyricsLanguage) }
        capture("embed-lyrics-page-${theme.name.lowercase()}")
        compose.onNodeWithText("嵌入到视频文件").assertDoesNotExist()

        state = state.copy(
            audioStreams = listOf(AudioStream(id = AudioQualities.DOLBY_ATMOS, url = "dolby")),
            audioBitrates = listOf(AudioOption(AudioQualities.DOLBY_ATMOS, "杜比全景声")),
            selectedAudioId = AudioQualities.DOLBY_ATMOS,
        )
        list.performScrollToNode(hasText("内嵌歌词"))
        compose.onNodeWithText("嵌入到音频文件").performScrollTo().assertIsEnabled()
        val lossyConversionWarning = "已开启转为 MP3，将不再保留杜比全景声。"
        compose.onNodeWithText(lossyConversionWarning).assertDoesNotExist()
        capture("embed-lyrics-dolby-${theme.name.lowercase()}")
        compose.runOnIdle { conversion = conversion.copy(convertAudioToMp3 = true) }
        compose.onNodeWithText(lossyConversionWarning).performScrollTo().assertExists()
        capture("embed-lyrics-dolby-mp3-${theme.name.lowercase()}")
        compose.runOnIdle { conversion = conversion.copy(convertAudioToMp3 = false) }
        compose.onNodeWithText(lossyConversionWarning).assertDoesNotExist()
    }

    /** 勾选项是可点击的整行，勾选状态在行内的 Checkbox 节点上。 */
    private fun checkbox(text: String) = compose.onNodeWithText(text).onChildren().filterToOne(isToggleable())

    private fun openLanguagePicker(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.onNode(isDialog()).assertExists()
    }

    private fun dialogText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun setContent(
        theme: AppThemeMode,
        state: () -> ParseUiState,
        conversionSettings: () -> ParseConversionSettings = { ParseConversionSettings() },
        update: (ParseUiState) -> Unit,
    ) {
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = theme)) {
                ParseScreenContent(
                    state = state(),
                    conversionSettings = conversionSettings(),
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
                    onSubtitleEnabledChange = { update(state().copy(subtitleEnabled = it)) },
                    onSubtitleLanguageChange = { update(state().copy(subtitleLanguageSelection = it)) },
                    onEmbedSubtitlesEnabledChange = { update(state().copy(embedSubtitlesEnabled = it)) },
                    onEmbedSubtitleSelectionChange = { update(state().copy(embedSubtitleSelection = it)) },
                    onEmbedLyricsEnabledChange = { update(state().copy(embedLyricsEnabled = it)) },
                    onEmbedLyricsLanguageChange = { update(state().copy(embedLyricsLanguage = it)) },
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

    private fun capture(name: String, dialog: Boolean = false) {
        val output = File("../.tmp/subtitle-redesign/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            val root = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
            root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
