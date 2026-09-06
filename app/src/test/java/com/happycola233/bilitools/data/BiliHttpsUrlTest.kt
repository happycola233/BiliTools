package com.happycola233.bilitools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiliHttpsUrlTest {
    @Test
    fun upgradesCleartextAndProtocolRelativeCdnUrls() {
        assertEquals(
            "https://i0.hdslb.com/bfs/vip/label.png",
            normalizeBiliHttpsUrl("http://i0.hdslb.com/bfs/vip/label.png"),
        )
        assertEquals(
            "https://i0.hdslb.com/bfs/vip/label.png",
            normalizeBiliHttpsUrl("//i0.hdslb.com/bfs/vip/label.png"),
        )
        assertEquals(
            "https://i0.hdslb.com/bfs/vip/label.png",
            normalizeBiliHttpsUrl("https://i0.hdslb.com/bfs/vip/label.png"),
        )
        assertNull(normalizeBiliHttpsUrl("   "))
        assertNull(normalizeBiliHttpsUrl(null))
    }
}
