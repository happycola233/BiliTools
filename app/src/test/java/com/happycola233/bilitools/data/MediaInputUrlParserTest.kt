package com.happycola233.bilitools.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaInputUrlParserTest {
    @Test
    fun parse_extractsUrlWhetherOrNotShareTitleIsSeparatedByWhitespace() {
        val cases = mapOf(
            "【在新西兰能拍到什么？-哔哩哔哩】 https://www.bilibili.com/video/av115925831458349" to
                "https://www.bilibili.com/video/av115925831458349",
            "【《熊出没·年年有熊》-哔哩哔哩电影】https://b23.tv/ep4029955" to
                "https://b23.tv/ep4029955",
            "复制https://www.bilibili.com/video/BV1xx411c7mD，打开客户端观看" to
                "https://www.bilibili.com/video/BV1xx411c7mD",
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, MediaInputUrlParser.parse(input)?.toString())
        }
    }

    @Test
    fun parse_normalizesSchemeLessBiliUrl() {
        assertEquals(
            "https://b23.tv/ep4029955",
            MediaInputUrlParser.parse("分享链接：b23.tv/ep4029955")?.toString(),
        )
    }

    @Test
    fun parse_skipsExternalUrlBeforeBiliUrl() {
        val input = "来源 https://example.com/detail，视频 https://www.bilibili.com/video/BV1xx411c7mD"

        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            MediaInputUrlParser.parse(input)?.toString(),
        )
    }

    @Test
    fun parse_returnsNullForTextWithoutUrl() {
        assertNull(MediaInputUrlParser.parse("没有链接的分享文案"))
    }

    @Test
    fun isBiliMediaHost_acceptsOnlyBiliDomains() {
        assertTrue("https://bilibili.com/video/av1".toHttpUrl().isBiliMediaHost())
        assertTrue("https://space.bilibili.com/1".toHttpUrl().isBiliMediaHost())
        assertTrue("https://b23.tv/abc".toHttpUrl().isBiliMediaHost())
        assertFalse("https://evilbilibili.com/video/av1".toHttpUrl().isBiliMediaHost())
        assertFalse("https://www.b23.tv/abc".toHttpUrl().isBiliMediaHost())
    }
}
