package com.happycola233.bilitools.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIdentifierTest {
    @Test
    fun aidToBvid_matchesPublicIdentifiers() {
        assertEquals("BV17x411w7KC", convertAidToBvid(170001L))
        assertEquals("BV1Q541167Qg", convertAidToBvid(455017605L))
    }
}
