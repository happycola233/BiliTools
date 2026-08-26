package com.happycola233.bilitools.ui.history

import com.happycola233.bilitools.data.model.HistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryParseUrlTest {
    @Test
    fun archiveUsesBvid() {
        val url = historyItem(business = "archive", bvid = "BV1sK4y147ob").toParseUrl()

        assertEquals("https://www.bilibili.com/video/BV1sK4y147ob", url)
    }

    @Test
    fun articleUsesCvidFromOid() {
        val url = historyItem(business = "article", oid = 6470274).toParseUrl()

        assertEquals("https://www.bilibili.com/read/cv6470274", url)
    }

    @Test
    fun articleListPrefersViewedArticleOverCollection() {
        val url = historyItem(
            business = "article-list",
            oid = 268656,
            cid = 6233590,
        ).toParseUrl()

        assertEquals("https://www.bilibili.com/read/cv6233590", url)
    }

    @Test
    fun articleListFallsBackToCollectionWhenArticleIdMissing() {
        val url = historyItem(business = "article-list", oid = 268656).toParseUrl()

        assertEquals("https://www.bilibili.com/read/readlist/rl268656", url)
    }

    @Test
    fun articleKeepsPublicReadOrOpusUri() {
        val readUrl = historyItem(
            business = "article",
            uri = "https://www.bilibili.com/read/cv1001",
        ).toParseUrl()
        val opusUrl = historyItem(
            business = "article",
            uri = "https://www.bilibili.com/opus/123456789",
        ).toParseUrl()

        assertEquals("https://www.bilibili.com/read/cv1001", readUrl)
        assertEquals("https://www.bilibili.com/opus/123456789", opusUrl)
    }

    @Test
    fun liveNeverJumpsToParseEvenWithRoomUri() {
        val url = historyItem(
            business = "live",
            uri = "https://live.bilibili.com/14047",
            oid = 14047,
        ).toParseUrl()

        assertNull(url)
    }

    @Test
    fun unsupportedBusinessWithoutPlayableUriStaysUndownloadable() {
        assertNull(historyItem(business = "goods", oid = 99).toParseUrl())
        assertNull(historyItem(business = "show", uri = "https://show.bilibili.com/1").toParseUrl())
        assertNull(historyItem(business = "article").toParseUrl())
    }

    @Test
    fun playableUriStillWorksForPgcAndCheese() {
        val pgc = historyItem(
            business = "pgc",
            uri = "https://www.bilibili.com/bangumi/play/ss26193",
        ).toParseUrl()
        val cheese = historyItem(
            business = "cheese",
            uri = "https://www.bilibili.com/cheese/play/ss61",
        ).toParseUrl()

        assertEquals("https://www.bilibili.com/bangumi/play/ss26193", pgc)
        assertEquals("https://www.bilibili.com/cheese/play/ss61", cheese)
    }

    private fun historyItem(
        business: String? = null,
        bvid: String? = null,
        oid: Long? = null,
        cid: Long? = null,
        uri: String? = null,
    ): HistoryItem {
        return HistoryItem(
            title = "sample",
            uri = uri,
            oid = oid,
            bvid = bvid,
            cid = cid,
            business = business,
        )
    }
}
