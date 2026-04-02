package com.happycola233.bilitools.data.model

import org.junit.Assert.assertEquals
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
    fun normalizeAggregateProgress_capsIncompleteGroupAtNinetyNine() {
        assertEquals(
            99,
            DownloadProgressRules.normalizeAggregateProgress(
                progress = 100,
                allTasksSucceeded = false,
            ),
        )
        assertEquals(
            100,
            DownloadProgressRules.normalizeAggregateProgress(
                progress = 100,
                allTasksSucceeded = true,
            ),
        )
    }
}
