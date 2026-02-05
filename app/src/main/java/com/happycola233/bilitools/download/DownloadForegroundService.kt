package com.happycola233.bilitools.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.DownloadNotificationState
import com.happycola233.bilitools.data.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var notificationManager: DownloadNotificationManager

    private var isForegroundStarted = false
    private var trackedTaskIds: Set<Long> = emptySet()
    private var lastPublishedState: DownloadNotificationState = DownloadNotificationState()
    private var lastPublishTimeMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        downloadRepository = applicationContext.appContainer.downloadRepository
        downloadRepository.ensureLoaded()

        notificationManager = DownloadNotificationManager(this)
        notificationManager.ensureChannels()

        serviceScope.launch {
            downloadRepository.notificationState.collect { state ->
                publishState(state, allowThrottle = true)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> downloadRepository.pauseAllManaged()
            ACTION_RESUME_ALL -> downloadRepository.resumeAllManaged()
        }
        publishState(downloadRepository.notificationState.value, allowThrottle = false)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun publishState(
        state: DownloadNotificationState,
        allowThrottle: Boolean,
    ) {
        val now = SystemClock.elapsedRealtime()
        val stateChanged = state.hasForegroundWork != lastPublishedState.hasForegroundWork ||
            state.inProgressCount != lastPublishedState.inProgressCount ||
            state.pausedCount != lastPublishedState.pausedCount
        if (allowThrottle && !stateChanged && now - lastPublishTimeMs < UPDATE_INTERVAL_MS) {
            return
        }

        val previousTaskIds = trackedTaskIds
        trackedTaskIds = state.activeTaskIds

        if (state.hasForegroundWork) {
            val notification = notificationManager.buildProgressNotification(state)
            if (!isForegroundStarted) {
                startForeground(
                    DownloadNotificationManager.NOTIFICATION_ID_PROGRESS,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                isForegroundStarted = true
            } else {
                notificationManager.notifyProgress(notification)
            }
        } else {
            if (isForegroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundStarted = false
            }
            if (previousTaskIds.isNotEmpty()) {
                val summary = downloadRepository.summarizeTaskOutcomes(previousTaskIds)
                notificationManager.showCompletion(summary)
            }
            stopSelf()
        }

        lastPublishedState = state
        lastPublishTimeMs = now
    }

    companion object {
        const val ACTION_SYNC = "com.happycola233.bilitools.download.action.SYNC"
        const val ACTION_PAUSE_ALL = "com.happycola233.bilitools.download.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.happycola233.bilitools.download.action.RESUME_ALL"

        fun requestSync(context: Context) {
            val serviceIntent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_SYNC
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        private const val UPDATE_INTERVAL_MS = 500L
    }
}
