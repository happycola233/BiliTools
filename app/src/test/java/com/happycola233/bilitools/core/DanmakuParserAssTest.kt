package com.happycola233.bilitools.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuParserAssTest {
    @Test
    fun subtitleFileSyntaxRemainsAsciiWhenTheInterfaceUsesArabicDigits() {
        val previousLocale = Locale.getDefault()
        val elements = listOf(DanmakuElem(
            progressMs = 1234L, mode = 1, fontSize = 25, color = 0x123456,
            ctime = 1L, pool = 0, midHash = "mid", idStr = "1", content = "مرحبا",
        ))
        try {
            Locale.setDefault(Locale.ENGLISH)
            val english = DanmakuParser.toAss(elements)
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            val arabic = DanmakuParser.toAss(elements)
            assertEquals(english, arabic)
            assertTrue(arabic.contains("0:00:01.23"))
            assertTrue(arabic.contains("مرحبا"))
            assertTrue(arabic.contains("&H00563412&"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
