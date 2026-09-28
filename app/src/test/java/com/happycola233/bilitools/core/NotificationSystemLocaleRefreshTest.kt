package com.happycola233.bilitools.core

import android.app.NotificationManager
import android.content.res.Configuration
import com.happycola233.bilitools.R
import com.happycola233.bilitools.update.UpdateDownloadService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// API 35 的原生运行时单独放在一个测试类，避免跨 SDK 沙箱共享字体 JAR 文件系统。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationSystemLocaleRefreshTest {
    @Test
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

}
