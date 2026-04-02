package com.happycola233.bilitools.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNamingTest {
    @Test
    fun renderComponent_cleansOuterSeparatorsWhenEnabled() {
        val rendered = DownloadNaming.renderComponent(
            template = "xxx - {res}",
            context = NamingRenderContext(),
            cleanSeparators = true,
        )

        assertEquals("xxx", rendered)
    }

    @Test
    fun renderComponent_keepsOuterSeparatorsWhenDisabled() {
        val rendered = DownloadNaming.renderComponent(
            template = "xxx - {res}",
            context = NamingRenderContext(),
            cleanSeparators = false,
        )

        assertEquals("xxx -", rendered)
    }

    @Test
    fun appendExtension_cleansDanglingSeparatorBeforeSuffix() {
        val fileName = DownloadNaming.appendExtension(
            baseName = "xxx -",
            extension = "ass",
            cleanSeparators = true,
        )

        assertEquals("xxx.ass", fileName)
    }
}
