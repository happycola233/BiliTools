package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRestartPolicyTest {
    @Test
    fun `queued task becomes user paused without being marked interrupted`() {
        val restored = DownloadRestartPolicy.restore(DownloadStatus.Pending, userPaused = false)

        assertEquals(DownloadStatus.Paused, restored.status)
        assertTrue(restored.userPaused)
        assertFalse(restored.interrupted)
    }

    @Test
    fun `running and processing tasks become retryable failures`() {
        listOf(DownloadStatus.Running, DownloadStatus.Merging).forEach { status ->
            val restored = DownloadRestartPolicy.restore(status, userPaused = false)

            assertEquals(DownloadStatus.Failed, restored.status)
            assertFalse(restored.userPaused)
            assertTrue(restored.interrupted)
        }
    }

    @Test
    fun `explicitly paused and resolved tasks keep their status`() {
        val paused = DownloadRestartPolicy.restore(DownloadStatus.Paused, userPaused = true)
        val unavailable = DownloadRestartPolicy.restore(
            DownloadStatus.Unavailable,
            userPaused = false,
        )

        assertEquals(DownloadStatus.Paused, paused.status)
        assertTrue(paused.userPaused)
        assertEquals(DownloadStatus.Unavailable, unavailable.status)
        assertFalse(unavailable.interrupted)
    }
}
