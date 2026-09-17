package com.happycola233.bilitools.data

import org.junit.Assert.*
import org.junit.Test

class MediaPaginationTest {
    @Test
    fun seriesUsesPnAndPsSoSecondPageDoesNotRepeatFirstPage() {
        val url = buildUploadsListUrl("1", 99, isSeason = false, page = 2)
        assertEquals("/x/series/archives", url.encodedPath)
        assertEquals("2", url.queryParameter("pn"))
        assertEquals("30", url.queryParameter("ps"))
        assertNull(url.queryParameter("page_num"))
        assertNull(url.queryParameter("page_size"))
    }

    @Test
    fun seasonUsesItsOwnPageParameters() {
        val url = buildUploadsListUrl("1", 99, isSeason = true, page = 3)
        assertEquals("/x/polymer/web-space/seasons_archives_list", url.encodedPath)
        assertEquals("3", url.queryParameter("page_num"))
        assertEquals("30", url.queryParameter("page_size"))
        assertEquals("99", url.queryParameter("season_id"))
        assertNull(url.queryParameter("pn"))
        assertNull(url.queryParameter("ps"))
    }
}
