package com.happycola233.bilitools.ui.parse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.PlayUrlInfo
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoStream
import com.happycola233.bilitools.ui.assertMovesInOneMotion
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import com.happycola233.bilitools.ui.traceFrames
import kotlin.math.abs
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 卡片高度必须与卡片内容逐帧同步：卡片内的切换、展开动画进行时，卡片边缘与下方卡片既不能
 * 滞后于内容，也不能裁切内容；选项显隐时，下方内容也要随动画一次平滑到位，不能在动画首尾跳变。
 * 视口足够高，保证被测内容完整位于屏幕内。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h2400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ParseCardMotionTest {
    @get:Rule val compose = createComposeRule()

    private val shortItem = favoriteItem(index = 0, title = "短简介视频", description = "")
    private val longDescription = "这是一段很长的简介，".repeat(12)
    private val longItem = favoriteItem(index = 1, title = "长简介视频", description = longDescription)
    private val info = MediaInfo(
        type = MediaType.Favorite,
        id = "ml1",
        nfo = MediaNfo(showTitle = "收藏夹"),
        list = listOf(shortItem, longItem),
    )
    private val initialState = ParseUiState(
        mediaInfo = info,
        items = info.list,
        selectedItemIndices = listOf(0),
        isLoggedIn = true,
    )
    private val dashVideo = VideoStream(id = 80, format = StreamFormat.Dash, codec = VideoCodec.Avc, url = "v")
    private val dashAudio = AudioStream(id = 30280, url = "a")

    /** 已取到 DASH 流的音视频输出：画质、内嵌字幕与字幕来源等选项齐全。 */
    private val dashState = initialState.copy(
        playUrlInfo = PlayUrlInfo(StreamFormat.Dash, video = listOf(dashVideo), audio = listOf(dashAudio)),
        videoStreams = listOf(dashVideo),
        audioStreams = listOf(dashAudio),
        resolutions = listOf(QualityOption(80, "1080P 高清")),
        codecs = listOf(CodecOption(VideoCodec.Avc, "AVC (H.264)")),
        audioBitrates = listOf(AudioOption(30280, "192K")),
        selectedResolutionId = 80,
        selectedCodec = VideoCodec.Avc,
        selectedAudioId = 30280,
        subtitleList = listOf(SubtitleInfo(lan = "zh-Hans", name = "中文（简体）", url = "https://example.com/zh")),
        subtitleLoadStatus = SubtitleLoadStatus.Ready,
        subtitleTargetCount = 1,
        subtitleCompletedCount = 1,
        subtitleAvailableCounts = mapOf("zh-Hans" to 1),
        warning = DASH_WARNING,
    )

    @Test
    fun switchingPreviewItemMovesOptionsCardInSameFrame() {
        var state by mutableStateOf(initialState)
        setContent { state }

        assertFollowsPromptly("切换到简介更长的条目") { state = state.copy(previewItemIndex = 1) }
        assertFollowsPromptly("切换回没有简介的条目") { state = state.copy(previewItemIndex = 0) }
    }

    @Test
    fun updatingDescriptionOfSameItemMovesOptionsCardInSameFrame() {
        var state by mutableStateOf(initialState.copy(previewItemIndex = 1))
        setContent { state }

        // 同一条目补拉详情后简介变化，卡片内容不切换，只有高度改变。
        val samples = traceOptionsCard {
            state = state.copy(
                items = listOf(shortItem, longItem.copy(description = longDescription.take(8))),
            )
        }
        assertFollowsContent("同一条目的简介变短", samples)
    }

    @Test
    fun expandingDescriptionMovesOptionsCardInSameFrame() {
        setContent { initialState.copy(previewItemIndex = 1) }

        val samples = traceOptionsCard { compose.onNodeWithText(longDescription).performClick() }
        assertFollowsContent("展开简介", samples)
    }

    @Test
    fun togglingOptionNeverClipsOptionsCardContent() {
        var state by mutableStateOf(initialState)
        setContent { state }

        // 卡片边缘落后时展开会裁掉底部内容，抢先时收起会裁掉底部内容，两个方向都要检查。
        listOf(true, false).forEach { enabled ->
            val scenario = if (enabled) "展开字幕来源" else "收起字幕来源"
            val samples = compose.traceFrames(
                change = { state = state.copy(subtitleEnabled = enabled) },
                sample = {
                    val node = compose.onNodeWithText(LAST_OPTION_LABEL).fetchSemanticsNode()
                    val bottom = node.positionInRoot.y + node.size.height
                    bottom to bottom - node.boundsInRoot.bottom
                },
            )
            assertTrue("$scenario 时底部选项没有移动", samples.first().first != samples.last().first)
            val clippedHeights = samples.map { it.second }
            assertTrue("$scenario 时卡片裁切了底部内容：$clippedHeights", clippedHeights.all { it <= 1f })
        }
    }

    /** 切换流媒体格式时，DASH 提示连同上方间距一起收起、展开，下方画质选项一次平滑到位。 */
    @Test
    fun streamFormatHintMovesQualityOptionsInOneMotion() {
        var state by mutableStateOf(dashState)
        setContent { state }

        compose.assertMovesInOneMotion(
            "切到 MP4 收起提示",
            change = { state = state.copy(format = StreamFormat.Mp4, warning = null) },
            position = topOf(RESOLUTION_LABEL),
        )
        compose.assertMovesInOneMotion(
            "切回 DASH 展开提示",
            change = { state = state.copy(format = StreamFormat.Dash, warning = DASH_WARNING) },
            position = topOf(RESOLUTION_LABEL),
        )
    }

    /** 提示文案清空后，收起动画仍显示原文案，文字随动画淡出，而不是第一帧就消失。 */
    @Test
    fun streamFormatHintKeepsTextWhileCollapsing() {
        var state by mutableStateOf(dashState)
        setContent { state }

        val hintShown = compose.traceFrames(change = { state = state.copy(format = StreamFormat.Mp4, warning = null) }) {
            compose.onAllNodesWithText(DASH_WARNING).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("收起过程中提示文案提前消失：$hintShown", hintShown.take(HINT_COLLAPSE_FRAMES).all { it })
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(DASH_WARNING).assertDoesNotExist()
    }

    /** 字幕来源随勾选展开、收起时，与上方选项的间距一起动画，下方选项一次平滑到位。 */
    @Test
    fun subtitleSourcePickersMoveOptionsBelowInOneMotion() {
        var state by mutableStateOf(dashState)
        setContent { state }

        listOf(true, false).forEach { enabled ->
            val action = if (enabled) "展开" else "收起"
            compose.assertMovesInOneMotion(
                "${action}字幕文件来源",
                change = { state = state.copy(subtitleEnabled = enabled) },
                position = topOf(COPY_SUBTITLE_LABEL),
            )
            compose.assertMovesInOneMotion(
                "${action}内嵌字幕来源",
                change = { state = state.copy(embedSubtitlesEnabled = enabled) },
                position = topOf(MISC_SECTION_TITLE),
            )
        }
    }

    /** 卡片在 LookaheadScope 中测量，嵌套的分页列表仍要能定位、滚动并响应跳页。 */
    @Test
    fun pagedListInsideCardStillScrollsToRequestedItems() {
        val pagedInfo = info.copy(
            paged = true,
            hasMore = false,
            totalPages = 1,
            list = (1..12).map { favoriteItem(index = it - 1, title = "视频$it", description = "") },
        )
        var state by mutableStateOf(
            ParseUiState(isLoggedIn = true).withNewList(pagedInfo, 1, ParseScrollRequest(1, 1)),
        )
        setContent(
            onPageScrollHandled = { id ->
                if (state.pagination.scrollRequest?.id == id) {
                    state = state.copy(pagination = state.pagination.copy(scrollRequest = null))
                }
            },
        ) { state }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(state.pagination.scrollRequest) }

        val pagedList = compose.onAllNodes(hasScrollToIndexAction()).onLast()
        pagedList.performScrollToIndex(8)
        compose.onNodeWithText("视频9").assertIsDisplayed()

        // 首项标题同时显示在信息卡片上，列表行重新进入视口后才会出现第二个节点。
        compose.onAllNodesWithText("视频1").assertCountEquals(1)
        state = state.copy(pagination = state.pagination.copy(scrollRequest = ParseScrollRequest(1, 2)))
        compose.waitForIdle()
        compose.onAllNodesWithText("视频1").assertCountEquals(2).onLast().assertIsDisplayed()
        compose.runOnIdle { assertNull(state.pagination.scrollRequest) }
    }

    private fun assertFollowsPromptly(scenario: String, change: () -> Unit) {
        val samples = traceOptionsCard(change)
        assertFollowsContent(scenario, samples)

        // 高度在约 0.3 秒内基本到位，而不是先停顿再追赶。
        val tops = samples.map { it.optionsTitleTop }
        val distance = abs(tops.last() - tops.first())
        val settleFrame = tops.indexOfFirst { abs(it - tops.last()) <= distance * 0.1f }
        assertTrue("$scenario 时卡片到位过慢（第 $settleFrame 帧）：$tops", settleFrame <= SETTLE_FRAME_LIMIT)
    }

    private fun assertFollowsContent(scenario: String, samples: List<OptionsCardSample>) {
        assertTrue(
            "$scenario 时下方卡片没有移动",
            samples.first().optionsTitleTop != samples.last().optionsTitleTop,
        )
        val gaps = samples.map { it.gapBelowList }
        assertTrue(
            "$scenario 时下方卡片没有与内容同步移动：$gaps",
            gaps.all { abs(it - gaps.first()) <= 1f },
        )
    }

    /** 读取 [label] 文字节点的顶边位置，供逐帧追踪选项显隐时下方内容的移动。 */
    private fun topOf(label: String): () -> Float = {
        compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
    }

    /**
     * 逐帧记录「下载与导出」卡片标题的位置，以及它与列表最后一行底边的距离。
     * 节点边界按祖先裁切计算，内容被落后的卡片边缘裁掉时距离也会随之变化。
     */
    private fun traceOptionsCard(change: () -> Unit): List<OptionsCardSample> = compose.traceFrames(change) {
        val lastRowBottom = compose.onAllNodesWithText(longItem.title)
            .fetchSemanticsNodes()
            .maxOf { it.boundsInRoot.bottom }
        val optionsTitleTop = compose.onNodeWithText(OPTIONS_CARD_TITLE).fetchSemanticsNode().boundsInRoot.top
        OptionsCardSample(optionsTitleTop = optionsTitleTop, gapBelowList = optionsTitleTop - lastRowBottom)
    }

    private fun setContent(
        onPageScrollHandled: (Int) -> Unit = {},
        state: () -> ParseUiState,
    ) {
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                ParseScreenContent(
                    state = state(),
                    conversionSettings = ParseConversionSettings(),
                    inputText = "",
                    contentTopPadding = 0.dp,
                    externalMode = false,
                    subtitleCopyPreviewEntries = null,
                    aiSummaryCopyPreviewEntries = null,
                    onInputChange = {}, onPaste = {}, onParse = {}, onMediaTypeChange = {}, onDownload = {},
                    onSectionChange = {}, onOpenUpper = {}, onCopyResultContent = { _, _ -> },
                    onSelectAllItems = {}, onClearSelectedItems = {}, onLoadPrevPage = {}, onLoadNextPage = {},
                    onLoadPage = {}, onItemClick = {}, onItemSelectionChange = { _, _ -> },
                    onFormatChange = {}, onOutputTypeChange = {}, onCollectionModeChange = {},
                    onResolutionModeChange = {}, onResolutionChange = {}, onCodecChange = {},
                    onAudioBitrateModeChange = {}, onAudioBitrateChange = {},
                    onSubtitleEnabledChange = {}, onSubtitleLanguageChange = {},
                    onEmbedSubtitlesEnabledChange = {}, onEmbedSubtitleSelectionChange = {},
                    onEmbedLyricsEnabledChange = {}, onEmbedLyricsLanguageChange = {},
                    onCopySubtitles = {}, onAiSummaryEnabledChange = {}, onCopyAiSummaries = {},
                    onNfoCollectionEnabledChange = {}, onNfoSingleEnabledChange = {},
                    onDanmakuLiveEnabledChange = {}, onDanmakuHistoryEnabledChange = {},
                    onDanmakuDateChange = {}, onDanmakuHourChange = {}, onImageSelectionChange = { _, _ -> },
                    onOpusContentEnabledChange = {}, onOpusImagesEnabledChange = {},
                    onDismissSubtitleCopyPreview = {}, onDismissAiSummaryCopyPreview = {},
                    onCopyCurrentSubtitle = {}, onCopyAllSubtitles = {}, onCopyCurrentAiSummary = {}, onCopyAllAiSummaries = {},
                    onDismissError = {},
                    onPageScrollHandled = onPageScrollHandled,
                )
            }
        }
    }

    private fun favoriteItem(index: Int, title: String, description: String) = MediaItem(
        title = title,
        coverUrl = "",
        description = description,
        url = "https://www.bilibili.com/video/BV1test$index",
        duration = 60,
        pubTime = 1_700_000_000L,
        type = MediaType.Video,
        isTarget = index == 0,
        index = index,
        aid = 100L + index,
        bvid = "BV1test$index",
        cid = 200L + index,
    )

    private data class OptionsCardSample(
        val optionsTitleTop: Float,
        val gapBelowList: Float,
    )

    private companion object {
        const val OPTIONS_CARD_TITLE = "下载与导出"
        const val LAST_OPTION_LABEL = "小时 (00-23)"
        const val DASH_WARNING = "DASH 将自动合并音视频为单文件"
        const val RESOLUTION_LABEL = "分辨率"
        const val COPY_SUBTITLE_LABEL = "复制字幕"
        const val MISC_SECTION_TITLE = "杂项"
        const val SETTLE_FRAME_LIMIT = 20

        /** 提示的收起弹簧约 15 帧基本到位，这段时间内文案都应保留。 */
        const val HINT_COLLAPSE_FRAMES = 15
    }
}
