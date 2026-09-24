package com.happycola233.bilitools.download

import android.app.Notification
import android.content.Context
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.DownloadNotificationState
import com.happycola233.bilitools.data.model.DownloadStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], qualifiers = "zh-rCN")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DownloadNotificationManagerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun build(state: DownloadNotificationState, live: Boolean = false): Notification =
        DownloadNotificationManager(context).buildProgressNotification(state, live)

    @Test fun batchShowsCountsAndMixedPhasesWithoutBytePercentOrEta() {
        val state = DownloadNotificationState(
            taskIds = (1L..15L).toSet(), successCount = 5, runningCount = 3,
            processingCount = 1, pendingCount = 6, speedBytesPerSec = 1024,
        )
        val notification = build(state)
        val title = notification.extras.getString(Notification.EXTRA_TITLE)!!
        val content = notification.extras.getString(Notification.EXTRA_TEXT)!!
        assertEquals(context.getString(R.string.notification_batch_completed, 5, 15), title)
        assertTrue(content.contains(context.getString(R.string.notification_count_downloading, 3)))
        assertTrue(content.contains(context.getString(R.string.notification_count_processing, 1)))
        assertTrue(content.contains(context.getString(R.string.notification_count_waiting, 6)))
        assertFalse(content.contains("%"))
        assertEquals(15, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(5, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        val live = build(state, true)
        assertEquals(content, live.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(title, live.extras.getString(Notification.EXTRA_TITLE))
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            assertEquals("5/15", live.extras.getString("android.shortCriticalText"))
        }
    }

    @Test fun knownSingleFileShowsItsOwnByteProgress() {
        val notification = build(single().copy(downloadedBytes = 25, totalBytes = 100))
        assertEquals(25, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("25%"))
    }

    @Test fun unknownSingleFileShowsDownloadedBytesWithoutInventingAPercentage() {
        val notification = build(single().copy(downloadedBytes = 25))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertFalse(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("%"))
    }

    @Test fun processingHasTheSameExplicitIndeterminateStateInBothStyles() {
        val state = single().copy(
            runningCount = 0, processingCount = 1, singleStatus = DownloadStatus.Merging,
            totalBytes = 100, downloadedBytes = 100,
        )
        for (live in listOf(false, true)) {
            val notification = build(state, live)
            assertEquals(context.getString(R.string.notification_status_merging), notification.extras.getString(Notification.EXTRA_TEXT))
            assertTrue(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        }
    }

    @Test fun pausedNotificationIsOrdinaryAndResumeIntentContainsOnlyItsPausedTasks() {
        val notification = build(
            single().copy(runningCount = 0, singleStatus = DownloadStatus.Paused, pausedTaskIds = setOf(1)),
            live = true,
        )
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
        assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
        val action = shadowOf(notification.actions.single().actionIntent)
        assertTrue(action.isForegroundService)
        assertArrayEquals(longArrayOf(1), action.savedIntent.getLongArrayExtra(DownloadNotificationReceiver.EXTRA_TASK_IDS))
    }

    private fun single() = DownloadNotificationState(
        sessionId = 1, taskIds = setOf(1), runningCount = 1,
        singleTitle = "视频.mp4", singleStatus = DownloadStatus.Running,
    )
}
