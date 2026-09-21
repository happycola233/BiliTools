package com.happycola233.bilitools.core

import android.app.Notification
import android.app.NotificationManager
import android.content.res.Configuration
import com.happycola233.bilitools.BiliToolsApp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.DownloadNotificationState
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.download.DownloadForegroundService
import com.happycola233.bilitools.download.DownloadNotificationManager
import com.happycola233.bilitools.update.UpdateDownloadService
import com.happycola233.bilitools.update.UpdateNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 32], qualifiers = "zh-rCN")
class NotificationLanguageRefreshTest {
    @Test
    fun pausedDownloadRefreshesWithoutANewProgressEvent() {
        val app = RuntimeEnvironment.getApplication() as BiliToolsApp
        val repository = app.container.downloadRepository
        repository.ensureLoaded()
        val state = ReflectionHelpers.getField<MutableStateFlow<DownloadNotificationState>>(repository, "_notificationState")
        state.value = DownloadNotificationState(
            activeTaskIds = setOf(1),
            pausedCount = 1,
            primaryStatus = DownloadStatus.Paused,
            hasForegroundWork = true,
        )
        val controller = Robolectric.buildService(DownloadForegroundService::class.java).create()
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            val oldTitle = progress(manager, DownloadNotificationManager.NOTIFICATION_ID_PROGRESS).extras.getString(Notification.EXTRA_TITLE)
            AppLanguage.select(AppLanguage.English)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            val translated = progress(manager, DownloadNotificationManager.NOTIFICATION_ID_PROGRESS)
            val expected = app.localizedContext()
            assertNotEquals(oldTitle, translated.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(expected.getString(R.string.notification_title_paused_multiple, 1), translated.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(expected.getString(R.string.download_resume), translated.actions.single().title.toString())
            assertEquals(expected.getString(R.string.downloads_title), manager.getNotificationChannel(translated.channelId).name.toString())
            assertEquals(1, state.value.pausedCount)
        } finally {
            controller.destroy()
            AppLanguage.select(AppLanguage.System)
        }
    }

    @Test
    fun updateDownloadRefreshesTheLastProgressSnapshotWhileNetworkIsIdle() {
        val app = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildService(UpdateDownloadService::class.java).create()
        val service = controller.get()
        val snapshotClass = Class.forName("com.happycola233.bilitools.update.UpdateDownloadService\$UpdateProgress")
        val snapshot = ReflectionHelpers.callConstructor(
            snapshotClass,
            ClassParameter.from(String::class.java, "v4"),
            ClassParameter.from(Long::class.javaPrimitiveType, 50L),
            ClassParameter.from(Long::class.javaPrimitiveType, 100L),
        )
        ReflectionHelpers.setField(service, "activeProgress", snapshot)
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            AppLanguage.select(AppLanguage.English)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            val translated = progress(manager, UpdateNotificationManager.NOTIFICATION_ID_PROGRESS)
            val expected = app.localizedContext()
            assertEquals(expected.getString(R.string.update_notification_progress_title, "v4"), translated.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(50, translated.extras.getInt(Notification.EXTRA_PROGRESS))
            assertEquals(expected.getString(R.string.update_notification_channel_progress_name), manager.getNotificationChannel(translated.channelId).name.toString())
        } finally {
            controller.destroy()
            AppLanguage.select(AppLanguage.System)
        }
    }

    @Test
    fun languageSwitchDoesNotStartForegroundWorkInIdleServices() {
        val download = Robolectric.buildService(DownloadForegroundService::class.java).create()
        val update = Robolectric.buildService(UpdateDownloadService::class.java).create()
        try {
            AppLanguage.select(AppLanguage.English)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertNull(shadowOf(download.get()).lastForegroundNotification)
            assertNull(shadowOf(update.get()).lastForegroundNotification)
            assertTrue(RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
        } finally {
            download.destroy()
            update.destroy()
            AppLanguage.select(AppLanguage.System)
        }
    }

    @Test
    @Config(sdk = [35])
    fun systemLocaleChangesRefreshChannelNamesWithoutStartingADownload() {
        val controller = Robolectric.buildService(UpdateDownloadService::class.java).create()
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            val originalNames = manager.notificationChannels.map { it.name.toString() }
            RuntimeEnvironment.setQualifiers("en-rUS")
            controller.get().onConfigurationChanged(Configuration(app.resources.configuration))
            assertNotEquals(originalNames, manager.notificationChannels.map { it.name.toString() })
            assertTrue(manager.notificationChannels.any { it.name.toString() == app.getString(R.string.update_notification_channel_progress_name) })
            assertNull(shadowOf(controller.get()).lastForegroundNotification)
        } finally {
            controller.destroy()
        }
    }

    private fun progress(manager: NotificationManager, id: Int): Notification =
        manager.activeNotifications.single { it.id == id }.notification
}
