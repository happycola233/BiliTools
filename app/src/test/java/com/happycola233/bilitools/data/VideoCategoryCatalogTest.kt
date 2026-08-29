package com.happycola233.bilitools.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCategoryCatalogTest {
    @Test
    fun resolvesLegacyAndModernParentChildNamesWhenViewOmitsTname() {
        assertEquals("音乐 > MV", VideoCategoryCatalog.legacyLabel(193, null))
        assertEquals("音乐 > MV", VideoCategoryCatalog.modernLabel(2017, null))
        assertEquals("鬼畜 > 人力VOCALOID", VideoCategoryCatalog.modernLabel(2061, null))
    }

    @Test
    fun marksOfflineLegacyCategories() {
        assertEquals("知识 > 演讲·公开课（已下线）", VideoCategoryCatalog.legacyLabel(39, null))
    }

    @Test
    fun prefersNameReturnedByApiWhileKeepingResolvedParent() {
        assertEquals("音乐 > 接口名称", VideoCategoryCatalog.modernLabel(2017, "接口名称"))
    }
}
