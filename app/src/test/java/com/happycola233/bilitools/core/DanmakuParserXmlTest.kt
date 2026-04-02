package com.happycola233.bilitools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuParserXmlTest {
    @Test
    fun toXml_preservesUtf8ChineseAndEmoji() {
        val xml = DanmakuParser.toXml(
            listOf(
                DanmakuElem(
                    progressMs = 1234L,
                    mode = 1,
                    fontSize = 25,
                    color = 0xFFFFFF,
                    ctime = 1_712_345_678L,
                    pool = 0,
                    midHash = "mid",
                    idStr = "id",
                    content = "中文弹幕测试😄",
                ),
            ),
        )

        val utf8RoundTrip = String(xml.toByteArray(Charsets.UTF_8), Charsets.UTF_8)

        assertEquals(xml, utf8RoundTrip)
        assertTrue(utf8RoundTrip.contains("中文弹幕测试😄"))
        assertTrue(utf8RoundTrip.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
    }

    @Test
    fun toXml_escapesSpecialCharactersWithoutCorruptingText() {
        val xml = DanmakuParser.toXml(
            listOf(
                DanmakuElem(
                    progressMs = 2000L,
                    mode = 1,
                    fontSize = 25,
                    color = 0xFFFFFF,
                    ctime = 1_712_345_678L,
                    pool = 0,
                    midHash = "mid",
                    idStr = "id",
                    content = "测试 & <标签> \"引号\" '单引号'",
                ),
            ),
        )

        assertTrue(xml.contains("测试"))
        assertTrue(xml.contains("&amp;"))
        assertTrue(xml.contains("&lt;标签&gt;"))
        assertTrue(xml.contains("&quot;引号&quot;"))
        assertTrue(xml.contains("&apos;单引号&apos;"))
    }
}
