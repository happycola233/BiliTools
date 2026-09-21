package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.MediaCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCategoryCatalogTest {
    @Test
    fun resolvesLegacyAndModernParentChildNamesWhenViewOmitsTname() {
        assertEquals(MediaCategory("音乐 > MV"), VideoCategoryCatalog.legacyCategory(193, null))
        assertEquals(MediaCategory("音乐 > MV"), VideoCategoryCatalog.modernCategory(2017, null))
        assertEquals(MediaCategory("鬼畜 > 人力VOCALOID"), VideoCategoryCatalog.modernCategory(2061, null))
    }

    @Test
    fun marksOfflineLegacyCategories() {
        assertEquals(MediaCategory("知识 > 演讲·公开课", offline = true), VideoCategoryCatalog.legacyCategory(39, null))
    }

    @Test
    fun prefersNameReturnedByApiWhileKeepingResolvedParent() {
        assertEquals(MediaCategory("音乐 > 接口名称"), VideoCategoryCatalog.modernCategory(2017, "接口名称"))
    }
}
