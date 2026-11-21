package com.happycola233.bilitools.ui.parse

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 点击下载后按钮立即转为添加中的状态，多个条目时展示添加进度，并在添加完成前拒绝重复点击。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ParseDownloadFabTest {
    @get:Rule val compose = createComposeRule()

    private val items = (0 until 40).map { index ->
        MediaItem(
            title = "收藏视频 $index",
            coverUrl = "",
            description = "",
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
    }
    private val info = MediaInfo(type = MediaType.Favorite, id = "ml1", nfo = MediaNfo(showTitle = "收藏夹"), list = items)

    // 不输出音视频时无需等待取流，按钮可直接点击。
    private val readyState = ParseUiState(
        mediaInfo = info,
        items = items,
        selectedItemIndices = items.indices.toList(),
        outputType = null,
        isLoggedIn = true,
    )

    @Test fun lightBatchShowsWhichItemIsBeingAdded() = verifyBatchProgress(AppThemeMode.Light)

    @Test fun darkBatchShowsWhichItemIsBeingAdded() = verifyBatchProgress(AppThemeMode.Dark)

    @Test fun singleItemShowsLoadingWithoutExpanding() {
        var downloads = 0
        setContent(onDownload = { downloads += 1 }) {
            readyState.copy(downloadSubmission = DownloadSubmissionProgress(addedCount = 0, totalCount = 1))
        }
        compose.onNodeWithContentDescription("正在添加下载任务").performClick()
        assertEquals(0, downloads)
    }

    private fun verifyBatchProgress(mode: AppThemeMode) {
        var downloads = 0
        var state by mutableStateOf(readyState)
        setContent(mode, onDownload = { downloads += 1 }) { state }

        compose.onNodeWithContentDescription("下载").performClick()
        assertEquals(1, downloads)
        capture("idle-${mode.name}")

        state = readyState.copy(downloadSubmission = DownloadSubmissionProgress(addedCount = 2, totalCount = 40))
        // 已加入 2 项，正在添加第 3 项；添加完成前再次点击不会重复提交。
        compose.onNodeWithContentDescription("正在添加 3/40").performClick()
        assertEquals(1, downloads)
        capture("adding-${mode.name}")

        // 最后一项加入后序号不超过总数。
        state = readyState.copy(downloadSubmission = DownloadSubmissionProgress(addedCount = 40, totalCount = 40))
        compose.onNodeWithContentDescription("正在添加 40/40").assertExists()

        state = readyState
        compose.onNodeWithContentDescription("下载").assertExists()
    }

    private fun setContent(
        mode: AppThemeMode = AppThemeMode.Light,
        onDownload: () -> Unit,
        state: () -> ParseUiState,
    ) {
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode)) {
                ParseScreenContent(
                    state = state(),
                    conversionSettings = ParseConversionSettings(),
                    inputText = "",
                    contentTopPadding = 0.dp,
                    externalMode = false,
                    subtitleCopyPreviewEntries = null,
                    aiSummaryCopyPreviewEntries = null,
                    onInputChange = {}, onPaste = {}, onParse = {}, onMediaTypeChange = {}, onDownload = onDownload,
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
                )
            }
        }
    }

    private fun capture(name: String) {
        val output = File("../.tmp/parse-download-fab/$name.png")
        output.parentFile?.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
