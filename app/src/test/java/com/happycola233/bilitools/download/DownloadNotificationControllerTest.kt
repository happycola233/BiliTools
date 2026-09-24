package com.happycola233.bilitools.download

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import com.happycola233.bilitools.BiliToolsApp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.DownloadNotificationState
import com.happycola233.bilitools.data.model.DownloadStatus
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], qualifiers = "zh-rCN")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DownloadNotificationControllerTest {
    private val app get() = RuntimeEnvironment.getApplication() as BiliToolsApp
    private val states: MutableStateFlow<DownloadNotificationState>
        get() = ReflectionHelpers.getField(app.container.downloadRepository, "_notificationState")
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    private fun active() = DownloadNotificationState(
        sessionId = 1, taskIds = setOf(1), pausableTaskIds = setOf(1),
        runningCount = 1, singleStatus = DownloadStatus.Running,
        singleTitle = "视频.mp4", downloadedBytes = 10, totalBytes = 100,
    )

    private fun prepare() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        states.value = active()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test fun pausingStopsTheServiceButKeepsAResumableOrdinaryNotification() {
        prepare()
        val controller = Robolectric.buildService(DownloadForegroundService::class.java).create().startCommand(0, 1)
        try {
            states.value = active().copy(
                runningCount = 0, pausableTaskIds = emptySet(), pausedTaskIds = setOf(1),
                singleStatus = DownloadStatus.Paused,
            )
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertTrue(shadowOf(controller.get()).isStoppedBySelf)
            assertEquals(0, progress().flags and Notification.FLAG_ONGOING_EVENT)
            assertEquals(app.getString(R.string.download_resume), progress().actions.single().title.toString())
        } finally {
            controller.destroy()
        }
    }

    @Test fun processingIsPublishedImmediatelyEvenInsideTheThrottleWindow() {
        prepare()
        val controller = Robolectric.buildService(DownloadForegroundService::class.java).create().startCommand(0, 1)
        try {
            states.value = active().copy(
                runningCount = 0, processingCount = 1, singleStatus = DownloadStatus.Merging,
                downloadedBytes = 100,
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(app.getString(R.string.notification_status_merging), progress().extras.getString(Notification.EXTRA_TEXT))
        } finally {
            controller.destroy()
        }
    }

    @Test fun aFinalByteUpdateIsPublishedWithoutAnotherDownloadEvent() {
        prepare()
        val controller = Robolectric.buildService(DownloadForegroundService::class.java).create().startCommand(0, 1)
        try {
            states.value = active().copy(downloadedBytes = 20)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            assertEquals(20, progress().extras.getInt(Notification.EXTRA_PROGRESS))
        } finally {
            controller.destroy()
        }
    }

    @Test fun aLateServiceStartAfterPauseImmediatelyLeavesForegroundMode() {
        prepare()
        states.value = active().copy(
            runningCount = 0, singleStatus = DownloadStatus.Paused,
            pausableTaskIds = emptySet(), pausedTaskIds = setOf(1),
        )
        val controller = Robolectric.buildService(DownloadForegroundService::class.java).create().startCommand(0, 1)
        try {
            assertTrue(shadowOf(controller.get()).isStoppedBySelf)
            assertEquals(0, progress().flags and Notification.FLAG_ONGOING_EVENT)
        } finally {
            controller.destroy()
        }
    }

    private fun progress(): Notification = manager.activeNotifications.single {
        it.id == DownloadNotificationManager.NOTIFICATION_ID_PROGRESS
    }.notification
}
