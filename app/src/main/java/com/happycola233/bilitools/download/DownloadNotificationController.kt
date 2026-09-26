package com.happycola233.bilitools.download

import android.content.Context
import android.os.SystemClock
import com.happycola233.bilitools.core.AppLanguage
import com.happycola233.bilitools.data.DownloadNotificationState
import com.happycola233.bilitools.data.DownloadRepository
import com.happycola233.bilitools.data.LiveUpdateIcon
import com.happycola233.bilitools.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 通知跟随应用内的下载会话；暂停后不依靠一个常驻服务维持通知和语言刷新。 */
internal class DownloadNotificationController(
    private val context: Context,
    private val repository: DownloadRepository,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notifications = DownloadNotificationManager(context)
    private var observer: Job? = null
    private var pendingPublish: Job? = null
    private var service: DownloadForegroundService? = null
    private var serviceStartRequested = false
    private var lastPublished = DownloadNotificationState()
    private var lastPublishTime = 0L
    private var dismissedSessionId: Long? = null

    fun start() {
        if (observer != null) return
        repository.ensureLoaded()
        notifications.ensureChannels()
        observer = scope.launch {
            var previousAppearance: Triple<Boolean, LiveUpdateIcon, Long>? = null
            combine(
                repository.notificationState,
                settings.settings.map { it.liveActivityStyleNotificationEnabled to it.liveUpdateIcon }
                    .distinctUntilChanged(),
                AppLanguage.changes,
            ) { state, notificationSettings, language -> Triple(state, notificationSettings, language) }
                .collect { (state, notificationSettings, language) ->
                    val appearance = Triple(notificationSettings.first, notificationSettings.second, language)
                    val appearanceChanged = appearance != previousAppearance
                    previousAppearance = appearance
                    if (appearanceChanged) notifications.ensureChannels()
                    if (appearanceChanged || !state.hasSameStructure(lastPublished)) {
                        refresh()
                    } else {
                        scheduleProgressUpdate()
                    }
                }
        }
    }

    private fun scheduleProgressUpdate() {
        val remaining = UPDATE_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastPublishTime)
        if (remaining <= 0) {
            refresh()
        } else if (pendingPublish == null) {
            // 保留窗口内最后一次更新，网络停顿后也不会丢掉末次状态。
            pendingPublish = scope.launch {
                delay(remaining)
                pendingPublish = null
                publish(repository.notificationState.value)
            }
        }
    }

    fun refresh(refreshChannels: Boolean = false) {
        pendingPublish?.cancel()
        pendingPublish = null
        if (refreshChannels) notifications.ensureChannels()
        publish(repository.notificationState.value)
    }

    fun attach(foregroundService: DownloadForegroundService) {
        serviceStartRequested = false
        service = foregroundService
        refresh()
    }

    fun detach(foregroundService: DownloadForegroundService) {
        if (service === foregroundService) service = null
    }

    fun dismiss(sessionId: Long) {
        dismissedSessionId = sessionId
        // 继续中的前台通知仍需存在，但本次下载不再请求提升为 Live Update。
        if (repository.notificationState.value.hasForegroundWork) refresh()
    }

    private fun publish(state: DownloadNotificationState) {
        if (state.sessionId != lastPublished.sessionId && state.hasForegroundWork) {
            notifications.clearCompletion()
        }
        when {
            state.hasForegroundWork -> {
                val notification = notifications.buildProgressNotification(
                    state,
                    liveActivityStyleEnabled = settings.shouldUseLiveActivityStyleNotification() &&
                        dismissedSessionId != state.sessionId,
                    liveUpdateIcon = settings.currentSettings().liveUpdateIcon,
                )
                val foregroundService = service
                if (foregroundService != null) {
                    foregroundService.showForeground(notification)
                } else if (!serviceStartRequested) {
                    serviceStartRequested = true
                    DownloadForegroundService.requestSync(context)
                }
            }
            else -> {
                service?.let { foregroundService ->
                    service = null
                    foregroundService.finishForeground()
                }
                if (state.isPaused && dismissedSessionId != state.sessionId) {
                    notifications.notifyProgress(notifications.buildProgressNotification(state, false))
                } else {
                    notifications.clearProgress()
                }
                if (state.isFinished && !state.completionReported) {
                    notifications.showCompletion(state.outcome)
                    repository.markNotificationCompletionReported(state)
                }
            }
        }
        lastPublished = state
        lastPublishTime = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 500L
    }
}
