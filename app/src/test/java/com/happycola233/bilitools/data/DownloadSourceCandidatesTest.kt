package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSourceCandidatesTest {
    @Test fun prioritizesMirrorsWithoutDroppingOrRewritingOtherSignedUrls() {
        val pcdn = "https://xy.example.mcdn.bilivideo.cn:8082/file?os=mcdn&sig=original"
        val bcache = "https://cn-sccd-ct-02-01.bilivideo.com/file?os=bcache&sig=bcache"
        val upos = "https://upos-sz.bilivideo.com/file?os=upos&sig=upos"
        val mirror = "https://upos-sz-mirrorhw.bilivideo.com/file?os=hwbv&sig=a%2Bb"
        val source = DownloadSource(pcdn, listOf(bcache, mirror, upos, pcdn, mirror))

        assertEquals(listOf(mirror, upos, bcache, pcdn), source.orderedUrls())
    }

    private val a = "https://a.bilivideo.com/v?sig=1"
    private val b = "https://b.bilivideo.com/v?sig=2"
    private val c = "https://c.bilivideo.com/v?sig=3"

    @Test fun slowNodesAreEachTriedOnceThenTheClearlyFasterOneWinsBack() {
        val rotation = DownloadSourceRotation(listOf(a, b, c))
        assertEquals(a, rotation.first())
        assertTrue(rotation.leaveForSpeed(a, 300))
        assertEquals(b, rotation.takeSpeedSwitchTarget())
        assertTrue(rotation.leaveForSpeed(b, 100))
        assertEquals(c, rotation.takeSpeedSwitchTarget())
        assertTrue(rotation.leaveForSpeed(c, 120))
        assertEquals(a, rotation.takeSpeedSwitchTarget())
        // 回到最快节点后不再按速度换源。
        assertFalse(rotation.leaveForSpeed(a, 10))
    }

    @Test fun staysPutWhenNoTriedNodeIsAtLeastTwiceAsFast() {
        val rotation = DownloadSourceRotation(listOf(a, b))
        assertEquals(a, rotation.first())
        assertTrue(rotation.leaveForSpeed(a, 100))
        assertEquals(b, rotation.takeSpeedSwitchTarget())
        assertFalse(rotation.leaveForSpeed(b, 80))
        assertFalse(rotation.leaveForSpeed(b, 10))
    }

    @Test fun speedSwitchesSkipTheSameHostButFailuresStillUseIt() {
        val sameHost = "https://a.bilivideo.com/v?sig=backup"
        val otherPort = "https://a.bilivideo.com:4483/v?sig=pcdn"
        val rotation = DownloadSourceRotation(listOf(a, sameHost, otherPort, b))
        assertEquals(a, rotation.first())
        assertTrue(rotation.leaveForSpeed(a, 100))
        assertEquals(otherPort, rotation.takeSpeedSwitchTarget())
        assertEquals(sameHost, rotation.afterFailure(otherPort))
    }

    @Test fun failedNodesNeverReturnButASlowWorkingNodeBeatsGivingUp() {
        val rotation = DownloadSourceRotation(listOf(a, b, c))
        assertEquals(a, rotation.first())
        assertTrue(rotation.leaveForSpeed(a, 100))
        assertEquals(b, rotation.takeSpeedSwitchTarget())
        assertEquals(c, rotation.afterFailure(b))
        assertEquals(a, rotation.afterFailure(c))
        assertNull(rotation.afterFailure(a))
    }

    @Test fun preservesApiOrderWithinTheSamePriorityAndIgnoresMalformedUrls() {
        val primary = "https://cdn.example.com/media?sig=first"
        val backup = "https://cdn.example.org/media?sig=second"
        assertEquals(
            listOf(primary, backup),
            DownloadSource(primary, listOf("", "invalid", backup)).orderedUrls(),
        )
    }
}
