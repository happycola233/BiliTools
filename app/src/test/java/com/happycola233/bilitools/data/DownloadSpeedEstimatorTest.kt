package com.happycola233.bilitools.data

import org.junit.Assert.*
import org.junit.Test

class DownloadSpeedEstimatorTest {
    @Test fun resumedBytesAreExcludedAndEstimatesWaitForAFullInitialSample() {
        val estimator = DownloadSpeedEstimator(10_000, 1_000_000)
        assertEquals(DownloadTransferEstimate(), estimator.update(10_500, 1_005_000, 1_100_000))
        assertEquals(DownloadTransferEstimate(), estimator.update(11_900, 1_019_000, 1_100_000))
        assertEquals(DownloadTransferEstimate(10_000, 8), estimator.update(12_000, 1_020_000, 1_100_000))
        assertEquals(DownloadTransferEstimate(10_000, 8), estimator.update(12_100, 1_030_000, 1_100_000))
    }

    @Test fun burstsAreSmoothedButSustainedRateChangesConverge() {
        val estimator = DownloadSpeedEstimator(0, 0)
        estimator.update(2_000, 20_000, 1_000_000)
        val spike = estimator.update(3_000, 60_000, 1_000_000)
        assertTrue("单秒突发不能直接把显示速度放大四倍", spike.speedBytesPerSec in 10_001..20_000)
        var estimate = spike
        for (second in 4L..15L) estimate = estimator.update(second * 1000, 60_000 + (second - 3) * 40_000, 1_000_000)
        assertTrue("持续提速应在数秒后反映出来", estimate.speedBytesPerSec in 38_000..40_000)
    }

    @Test fun alternatingDeliveryProducesLessVariationThanInstantaneousSpeed() {
        val estimator = DownloadSpeedEstimator(0, 0)
        var bytes = 20_000L
        estimator.update(2_000, bytes, 1_000_000)
        val speeds = (3L..20L).map { second ->
            bytes += if (second % 2L == 0L) 18_000 else 2_000
            estimator.update(second * 1000, bytes, 1_000_000).speedBytesPerSec
        }
        assertTrue(speeds.max() - speeds.min() < 4_000)
        assertTrue(speeds.all { it in 7_000..13_000 })
    }

    @Test fun aStalledTransferClearsOldSpeedAndWarmsUpAgainOnRecovery() {
        val estimator = DownloadSpeedEstimator(0, 0)
        estimator.update(2_000, 20_000, 100_000)
        assertTrue(estimator.update(3_000, 20_000, 100_000).speedBytesPerSec in 1..9_999)
        estimator.update(4_000, 20_000, 100_000)
        estimator.update(5_000, 20_000, 100_000)
        assertEquals(DownloadTransferEstimate(), estimator.update(6_000, 20_000, 100_000))
        assertEquals(DownloadTransferEstimate(), estimator.update(7_000, 22_000, 100_000))
        assertEquals(DownloadTransferEstimate(2_000, 38), estimator.update(8_000, 24_000, 100_000))
    }

    @Test fun elapsedTimeWeightsSamplesAndUnknownOrCompletedSizesHaveNoEta() {
        val estimator = DownloadSpeedEstimator(0, 0)
        assertEquals(DownloadTransferEstimate(10_000, null), estimator.update(2_000, 20_000, 0))
        val afterDelay = estimator.update(6_000, 60_000, 60_001)
        assertEquals(DownloadTransferEstimate(10_000, 1), afterDelay)
        assertNull(estimator.update(7_000, 70_000, 70_000).etaSeconds)
    }
}
