package com.happycola233.bilitools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowSourceDetectionTest {
    private val policy = SlowSourcePolicy(graceMillis = 1_000, windowMillis = 2_000)

    @Test fun windowIgnoresTheGracePeriodAndWaitsForAFullWindow() {
        val window = ConnectionSpeedWindow(startMillis = 0, policy = policy)
        assertNull(window.record(500, 5_000_000))
        assertNull(window.record(1_000, 5_000_000))
        assertNull(window.record(2_500, 6_000_000))
        // 宽限期内的 5 MB 不计入：窗口从 1 秒处起算，2 秒内又收到 2 MB。
        assertEquals(1_000_000L, window.record(3_000, 7_000_000))
    }

    @Test fun windowSlidesSoAStallAfterAFastStartReadsAsZero() {
        val window = ConnectionSpeedWindow(startMillis = 0, policy = policy)
        val speeds = (1_000L..4_000L step 500).map { time ->
            // 前一秒以 1 MB/s 传输，之后完全卡住。
            val bytes = (time.coerceAtMost(2_000) - 1_000) * 1_000
            window.record(time, bytes)
        }
        assertEquals(listOf(null, null, null, null, 500_000L, 250_000L, 0L), speeds)
    }

    @Test fun referenceKeepsTheFastestRecentSpeedAndHalvesEachHalfLife() {
        val reference = ThroughputReference(halfLifeMillis = 1_000)
        assertEquals(1_000L, reference.observe(10_000, 1_000))
        assertEquals(500L, reference.observe(11_000, 0))
        assertEquals(250L, reference.observe(12_000, 100))
        assertEquals(800L, reference.observe(12_000, 800))
    }

    @Test fun slowMeansBelowTheFloorOrFarBelowTheReference() {
        val defaults = SlowSourcePolicy()
        assertTrue(defaults.isSlow(windowSpeed = 2_000_000, referenceSpeed = 0))
        assertFalse(defaults.isSlow(windowSpeed = 5_000_000, referenceSpeed = 0))
        assertTrue(defaults.isSlow(windowSpeed = 5_000_000, referenceSpeed = 50_000_000))
        assertFalse(defaults.isSlow(windowSpeed = 8_000_000, referenceSpeed = 50_000_000))
    }
}
