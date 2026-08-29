package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseErrorHandlingTest {
    @Test
    fun formatBiliApiErrorMessage_includesServerMessageAndCode() {
        assertEquals(
            "啥都木有 (-404)",
            formatBiliApiErrorMessage(
                message = "啥都木有",
                code = -404,
                fallback = "解析失败，请稍后重试",
            ),
        )
    }

    @Test
    fun formatBiliApiErrorMessage_usesFallbackForEmptyServerMessage() {
        assertEquals(
            "解析失败，请稍后重试 (-400)",
            formatBiliApiErrorMessage(
                message = " ",
                code = -400,
                fallback = "解析失败，请稍后重试",
            ),
        )
    }

    @Test
    fun canAutoLoadStream_errorKeepsRetainedResultFromOverwritingMessage() {
        val item = MediaItem(
            title = "已解析资源",
            coverUrl = "",
            description = "",
            url = "https://www.bilibili.com/video/BV1xx411c7mD",
            duration = 60,
            pubTime = 1_700_000_000L,
            type = MediaType.Video,
            isTarget = true,
            index = 0,
        )
        val info = MediaInfo(
            type = MediaType.Video,
            id = "BV1xx411c7mD",
            nfo = MediaNfo(showTitle = item.title),
            list = listOf(item),
        )
        val state = ParseUiState(
            error = "啥都木有 (-404)",
            mediaInfo = info,
            items = info.list,
            selectedItemIndices = listOf(0),
        )

        assertFalse(state.canAutoLoadStream())
        assertTrue(state.copy(error = null).canAutoLoadStream())
    }
}
