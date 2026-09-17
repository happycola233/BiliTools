package com.happycola233.bilitools.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.HistoryItem
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.ui.history.HistoryBody
import com.happycola233.bilitools.ui.history.HistoryLoadedPage
import com.happycola233.bilitools.ui.history.HistoryScrollRequest
import com.happycola233.bilitools.ui.history.HistoryUiState
import com.happycola233.bilitools.ui.parse.PageSelectionSection
import com.happycola233.bilitools.ui.parse.ParseScrollRequest
import com.happycola233.bilitools.ui.parse.ParseUiState
import com.happycola233.bilitools.ui.parse.withAppendedPage
import com.happycola233.bilitools.ui.parse.withNewList
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaginationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun parseScrollAndButtonsInLightTheme() = verifyParse(AppThemeMode.Light)
    @Test fun parseScrollAndButtonsInDarkTheme() = verifyParse(AppThemeMode.Dark)
    @Test fun historyScrollAndPageJumpInLightTheme() = verifyHistory(AppThemeMode.Light)
    @Test fun historyScrollAndPageJumpInDarkTheme() = verifyHistory(AppThemeMode.Dark)
    @Test fun singleItemLastParsePageStillStartsAtTop() = verifyParse(AppThemeMode.Light, lastId = 13)
    @Test fun singleItemLastHistoryPageStillStartsAtTop() = verifyHistory(AppThemeMode.Dark, lastId = 19)

    private fun verifyParse(mode: AppThemeMode, lastId: Int = 24) {
        var state by mutableStateOf(ParseUiState().withNewList(mediaPage(1..12), 1, ParseScrollRequest(1, 1)))
        var appendCount = 0
        var requestId = 1
        fun append() {
            if (!state.pagination.hasMore) return
            appendCount++
            state = state.withAppendedPage(mediaPage(13..lastId, more = false), 2)
        }
        fun jump(page: Int) {
            state = state.copy(pageIndex = page, pagination = state.pagination.copy(scrollRequest = ParseScrollRequest(page, ++requestId)))
        }
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode)) {
                Surface {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        PageSelectionSection(
                            state = state, info = state.mediaInfo!!,
                            selectedCount = state.selectedItemIndices.size, totalItems = state.items.size,
                            onSelectAllItems = {}, onClearSelectedItems = {},
                            onLoadPrevPage = { jump(state.pageIndex - 1) },
                            onLoadNextPage = { jump(state.pageIndex + 1) }, onLoadPage = ::jump,
                            onAppendPage = ::append,
                            onVisiblePageChange = { if (state.pagination.scrollRequest == null) state = state.copy(pageIndex = it) },
                            onPageScrollHandled = { state = state.copy(pagination = state.pagination.copy(scrollRequest = null)) },
                            onItemClick = {}, onItemSelectionChange = { _, _ -> },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val pageTop = compose.onNodeWithText("视频1").fetchSemanticsNode().boundsInRoot.top
        assertEquals(0, appendCount)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(8)
        compose.waitForIdle()
        assertEquals(1, appendCount)
        assertEquals(lastId, state.items.size)
        compose.onNodeWithText("视频9").assertIsDisplayed()
        assertEquals(1, state.pageIndex)
        compose.onNodeWithText("下一页").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("视频13").assertIsDisplayed()
        assertEquals(pageTop, compose.onNodeWithText("视频13").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(2, state.pageIndex)
        assertEquals(1, appendCount)
        capture("parse-${mode.name.lowercase()}-$lastId")
        compose.onNodeWithText("上一页").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("视频1").assertIsDisplayed()
        assertEquals(listOf(0), state.selectedItemIndices)
    }

    private fun verifyHistory(mode: AppThemeMode, lastId: Int = 36) {
        var state by mutableStateOf(HistoryUiState(
            isLoggedIn = true, hasMore = true, totalPages = 2,
            pages = listOf(HistoryLoadedPage(1, historyItems(1..18))),
        ))
        var appendCount = 0
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode)) {
                Surface(Modifier.fillMaxWidth().height(700.dp)) {
                    HistoryBody(
                        state = state, innerPadding = PaddingValues(0.dp),
                        onVisiblePageChange = { if (state.scrollRequest == null) state = state.copy(page = it) },
                        onScrollHandled = { state = state.copy(scrollRequest = null) },
                        onLoadNextPage = {
                            if (state.hasMore) {
                                appendCount++
                                state = state.copy(hasMore = false, pages = state.pages + HistoryLoadedPage(2, historyItems(19..lastId)))
                            }
                        },
                        onLoadPrevPage = {}, onDownload = {}, onOpenAuthor = {}, onCopyTitle = {}, onCopyAuthorName = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        // 第一条带日期标题，以同日的普通行测量对齐位置。
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        val pageTop = compose.onNodeWithText("记录2").fetchSemanticsNode().boundsInRoot.top
        val thumb = compose.onNodeWithTag("lazy_list_scrollbar_thumb").assertIsDisplayed()
        val initialThumbTop = thumb.fetchSemanticsNode().boundsInRoot.top
        assertEquals(0, appendCount)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(14)
        compose.waitForIdle()
        assertEquals(1, appendCount)
        compose.onNodeWithText("记录15").assertIsDisplayed()
        assertEquals(1, state.page)
        compose.runOnIdle { state = state.copy(page = 2, scrollRequest = HistoryScrollRequest(2, 1)) }
        compose.waitForIdle()
        compose.onNodeWithText("记录19").assertIsDisplayed()
        assertEquals(pageTop, compose.onNodeWithText("记录19").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(2, state.page)
        assertTrue(thumb.fetchSemanticsNode().boundsInRoot.top > initialThumbTop)
        capture("history-${mode.name.lowercase()}-$lastId")
        thumb.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, -180f), delayMillis = 16)
            up()
        }
        compose.waitForIdle()
        assertEquals("长按拖动滚动条可以返回前一页", 1, state.page)
        compose.runOnIdle { state = state.copy(page = 1, scrollRequest = HistoryScrollRequest(1, 2)) }
        compose.waitForIdle()
        compose.onNodeWithText("记录1").assertIsDisplayed()
        assertEquals(1, appendCount)
    }

    @Test fun shortHistoryListDoesNotShowScrollbar() {
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                Surface(Modifier.fillMaxWidth().height(500.dp)) {
                    HistoryBody(
                        state = HistoryUiState(isLoggedIn = true, pages = listOf(HistoryLoadedPage(1, historyItems(1..2)))),
                        innerPadding = PaddingValues(0.dp), onVisiblePageChange = {}, onScrollHandled = {},
                        onLoadNextPage = {}, onLoadPrevPage = {}, onDownload = {},
                        onOpenAuthor = {}, onCopyTitle = {}, onCopyAuthorName = {},
                    )
                }
            }
        }
        compose.onNodeWithText("记录1").assertIsDisplayed()
        compose.onNodeWithTag("lazy_list_scrollbar_thumb").assertDoesNotExist()
    }

    @Test fun emptyHistoryAppendFailureHasRetryButton() {
        var retryCount = 0
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                Surface(Modifier.fillMaxWidth().height(500.dp)) {
                    HistoryBody(
                        state = HistoryUiState(isLoggedIn = true, hasMore = true, appendFailed = true, pages = listOf(HistoryLoadedPage(1, emptyList()))),
                        innerPadding = PaddingValues(0.dp), onVisiblePageChange = {}, onScrollHandled = {},
                        onLoadNextPage = { retryCount++ }, onLoadPrevPage = {}, onDownload = {},
                        onOpenAuthor = {}, onCopyTitle = {}, onCopyAuthorName = {},
                    )
                }
            }
        }
        compose.onNodeWithText("加载失败，点击重试").assertIsDisplayed().performClick()
        assertEquals(1, retryCount)
    }

    private fun mediaPage(ids: IntRange, more: Boolean = true) = MediaInfo(
        type = MediaType.Favorite, id = "42", paged = true, hasMore = more, totalPages = 2,
        nfo = MediaNfo(showTitle = "测试收藏夹"),
        list = ids.map { id -> MediaItem(
            title = "视频$id", coverUrl = "", description = "", url = "",
            duration = 60, pubTime = 1, type = MediaType.Video, isTarget = false, index = id - 1, aid = id.toLong(),
        ) },
    )

    private fun historyItems(ids: IntRange) = ids.map { id ->
        HistoryItem(title = "记录$id", oid = id.toLong(), business = "archive", duration = 60, authorName = "测试作者", viewAt = id.toLong())
    }

    private fun capture(name: String) {
        val output = File("../.tmp/pagination-ui/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
