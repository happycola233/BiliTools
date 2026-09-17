package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.PlayUrlInfo
import com.happycola233.bilitools.data.model.StreamFormat
import org.junit.Assert.*
import org.junit.Test

class ParsePaginationTest {
    @Test
    fun shortAndEmptyPagesKeepTheirServerPageNumbers() {
        val first = ParseUiState().withNewList(info(1, 2), 1, ParseScrollRequest(1, 1))
        val empty = first.withAppendedPage(info(), 2)
        val last = empty.withAppendedPage(info(3, more = false), 3)

        assertTrue(empty.pagination.hasMore)
        assertEquals(listOf(1, 2, 3), last.pagination.pages.map { it.page })
        assertEquals(2, last.pagination.firstIndexAtOrAfter(2))
        assertEquals(2, last.pagination.firstIndexAtOrAfter(3))
        assertEquals(3, last.pagination.pageAt(2))
        assertFalse(last.pagination.hasMore)
    }

    @Test
    fun appendRetainsSelectionPreviewDetailsAndStreamWithoutDuplicateItems() {
        val first = ParseUiState().withNewList(info(1, 2), 1, ParseScrollRequest(1, 1))
        val detailed = first.items[0].copy(cid = 900, description = "已补全的详情")
        val stream = PlayUrlInfo(format = StreamFormat.Dash, video = emptyList(), audio = emptyList())
        val selected = first.copy(
            items = listOf(detailed, first.items[1]),
            selectedItemIndices = listOf(0, 1),
            selectedItemIndex = 1,
            previewItemIndex = 0,
            playUrlInfo = stream,
        )
        val appended = selected.withAppendedPage(info(2, 3, 3, more = false), 2)

        assertEquals(listOf(1L, 2L, 3L), appended.items.map { it.aid })
        assertEquals(listOf(0, 1), appended.selectedItemIndices)
        assertEquals(1, appended.selectedItemIndex)
        assertEquals(0, appended.previewItemIndex)
        assertSame(stream, appended.playUrlInfo)
        assertEquals(detailed, appended.items.first())
        assertEquals(first.pagination.keyAt(0), appended.pagination.keyAt(0))
        assertEquals(first.pagination.generation, appended.pagination.generation)
        assertEquals(2, appended.pagination.firstIndexAtOrAfter(2))
        assertTrue(appended.canGoToNextPage) // 后一页已缓存，即便上游没有更多也可以跳转。
        assertFalse(appended.copy(pageIndex = 2).canGoToNextPage)
    }

    @Test
    fun initialEmptyPageSelectsFirstActualItemWhenItArrives() {
        val empty = ParseUiState().withNewList(info(), 1, ParseScrollRequest(1, 1))
        val loaded = empty.withAppendedPage(info(3), 2)
        assertEquals(listOf(0), loaded.selectedItemIndices)
        assertEquals(2, loaded.pagination.pageAt(0))
        assertEquals(0, loaded.pagination.firstIndexAtOrAfter(1))
    }

    @Test
    fun naturalScrollingAndAppendingDoNotRequestTheSameStreamAgain() {
        val first = ParseUiState().withNewList(info(1, 2), 1, ParseScrollRequest(1, 1))
        val scrolled = first.withAppendedPage(info(3), 2).copy(pageIndex = 2)
        assertEquals(first.streamRequestKeyOrNull(), scrolled.streamRequestKeyOrNull())
        val replaced = scrolled.withNewList(info(4, 5), 4, ParseScrollRequest(4, 2))
        assertNotEquals(scrolled.streamRequestKeyOrNull(), replaced.streamRequestKeyOrNull())
    }

    @Test
    fun trailingEmptyPageDoesNotLeaveNextButtonEnabled() {
        val first = ParseUiState().withNewList(info(1, 2), 1, ParseScrollRequest(1, 1))
        val last = first.withAppendedPage(info(more = false), 2)
        assertFalse(last.canGoToNextPage)
    }

    @Test
    fun songsSharingAssociatedVideoKeepTheirOwnIdentity() {
        val songs = info(1, 2).copy(list = info(1, 2).list.map { it.copy(type = MediaType.Music, sid = it.aid, aid = 0) })
        val state = ParseUiState().withNewList(songs, 1, ParseScrollRequest(1, 1))
        val next = songs.copy(list = songs.list.map { it.copy(sid = it.sid!! + 1) })
        assertEquals(listOf(1L, 2L, 3L), state.withAppendedPage(next, 2).items.map { it.sid })
    }

    @Test
    fun explicitMoreFlagTakesPrecedenceOverEstimatedPageCount() {
        assertTrue(info().copy(totalPages = 1, hasMore = true).hasNextPage(1))
        assertFalse(info(1).copy(totalPages = 99, hasMore = false).hasNextPage(1))
        assertTrue(info().copy(totalPages = 3, hasMore = null).hasNextPage(2))
        assertFalse(info(1).copy(totalPages = 3, hasMore = null).hasNextPage(3))
    }

    private fun info(vararg ids: Int, more: Boolean = true) = MediaInfo(
        type = MediaType.Favorite, id = "42", paged = true, hasMore = more, totalPages = 3,
        nfo = MediaNfo(showTitle = "测试收藏夹"),
        list = ids.map { id ->
            MediaItem(
                title = "视频$id", coverUrl = "", description = "", url = "https://example.com/$id",
                duration = 60, pubTime = 1, type = MediaType.Video,
                isTarget = false, index = id, aid = id.toLong(),
            )
        },
    )
}
