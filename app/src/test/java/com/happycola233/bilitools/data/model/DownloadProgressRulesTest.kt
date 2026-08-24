package com.happycola233.bilitools.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressRulesTest {
    @Test
    fun normalizeTaskProgress_capsActiveTaskAtNinetyNine() {
        assertEquals(
            99,
            DownloadProgressRules.normalizeTaskProgress(
                status = DownloadStatus.Merging,
                progress = 100,
            ),
        )
        assertEquals(
            99,
            DownloadProgressRules.normalizeTaskProgress(
                status = DownloadStatus.Running,
                progress = 120,
            ),
        )
    }

    @Test
    fun normalizeTaskProgress_keepsSuccessAtOneHundred() {
        assertEquals(
            100,
            DownloadProgressRules.normalizeTaskProgress(
                status = DownloadStatus.Success,
                progress = 99,
            ),
        )
    }

    @Test
    fun normalizeTaskProgress_marksUnavailableTaskAsResolved() {
        assertEquals(
            100,
            DownloadProgressRules.normalizeTaskProgress(
                status = DownloadStatus.Unavailable,
                progress = 0,
            ),
        )
    }

    @Test
    fun normalizeAggregateProgress_capsIncompleteGroupAtNinetyNine() {
        assertEquals(
            99,
            DownloadProgressRules.normalizeAggregateProgress(
                progress = 100,
                allTasksResolved = false,
            ),
        )
        assertEquals(
            100,
            DownloadProgressRules.normalizeAggregateProgress(
                progress = 100,
                allTasksResolved = true,
            ),
        )
    }

    @Test
    fun resolvedStatus_includesSuccessAndUnavailableOnly() {
        assertTrue(DownloadStatus.Success.isResolvedWithoutFailure)
        assertTrue(DownloadStatus.Unavailable.isResolvedWithoutFailure)
        assertFalse(DownloadStatus.Failed.isResolvedWithoutFailure)
        assertFalse(DownloadStatus.Cancelled.isResolvedWithoutFailure)
    }
}
