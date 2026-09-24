package com.happycola233.bilitools.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.happycola233.bilitools.core.appContainer

/** 仅在下载或处理确实执行时提供前台服务，不持有下载结果的统计范围。 */
class DownloadForegroundService : Service() {
    private val container get() = applicationContext.appContainer
    private val notifications by lazy { DownloadNotificationManager(this) }
    private var foregroundStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = container.downloadRepository
        repository.ensureLoaded()
        if (intent?.action == ACTION_RESUME) {
            repository.resumeNotificationTasks(
                intent.getLongArrayExtra(DownloadNotificationReceiver.EXTRA_TASK_IDS)?.toSet().orEmpty(),
            )
        }
        notifications.ensureChannels()
        // 请求启动后任务可能已经结束；仍先履行 startForegroundService 的契约，再退出。
        showForeground(notifications.buildProgressNotification(repository.notificationState.value, false))
        container.downloadNotifications.attach(this)
        return START_NOT_STICKY
    }

    internal fun showForeground(notification: Notification) {
        if (foregroundStarted) {
            notifications.notifyProgress(notification)
        } else {
            startForeground(
                DownloadNotificationManager.NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            foregroundStarted = true
        }
    }

    internal fun finishForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        container.downloadRepository.pauseForForegroundTimeout()
        container.downloadNotifications.refresh()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        container.downloadNotifications.detach(this)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_SYNC = "com.happycola233.bilitools.download.action.SYNC"
        const val ACTION_RESUME = "com.happycola233.bilitools.download.RESUME_NOTIFICATION_TASKS"

        fun requestSync(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadForegroundService::class.java).setAction(ACTION_SYNC),
            )
        }
    }
}
