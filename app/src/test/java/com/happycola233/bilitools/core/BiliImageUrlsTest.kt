package com.happycola233.bilitools.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BiliImageUrlsTest {
    @Test
    fun bfsImageGetsCoverScaledWebpSuffix() {
        assertEquals(
            "https://i0.hdslb.com/bfs/archive/abc.jpg@144w_108h_1e.webp",
            BiliImageUrls.thumbnail("https://i0.hdslb.com/bfs/archive/abc.jpg", 144, 108),
        )
        assertEquals(
            "https://archive.biliimg.com/bfs/archive/abc.png@96w_72h_1e.webp",
            BiliImageUrls.thumbnail("https://archive.biliimg.com/bfs/archive/abc.png", 96, 72),
        )
    }

    @Test
    fun alreadyFormattedOrForeignUrlIsKept() {
        val formatted = "https://i0.hdslb.com/bfs/archive/abc.jpg@100w.webp"
        assertEquals(formatted, BiliImageUrls.thumbnail(formatted, 144, 108))

        val foreign = "https://example.com/bfs/archive/abc.jpg"
        assertEquals(foreign, BiliImageUrls.thumbnail(foreign, 144, 108))

        val nonBfs = "https://i0.hdslb.com/static/abc.jpg"
        assertEquals(nonBfs, BiliImageUrls.thumbnail(nonBfs, 144, 108))
    }
}
