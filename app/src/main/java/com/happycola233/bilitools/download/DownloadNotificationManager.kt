package com.happycola233.bilitools.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.happycola233.bilitools.core.formatByteCount
import com.happycola233.bilitools.core.localizedContext
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.DownloadNotificationState
import com.happycola233.bilitools.data.DownloadOutcomeSummary
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.notification.applyPromotedOngoing
import com.happycola233.bilitools.notification.notifyIfAllowed
import com.happycola233.bilitools.ui.MainActivity

internal class DownloadNotificationManager(
    private val baseContext: Context,
) {
    private val context: Context
        get() = baseContext.localizedContext()
    private val manager: NotificationManagerCompat = NotificationManagerCompat.from(baseContext)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(NotificationManager::class.java) ?: return

        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS_ID,
            context.getString(R.string.downloads_title),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_download_progress_desc)
            setShowBadge(false)
        }

        val completionChannel = NotificationChannel(
            CHANNEL_COMPLETION_ID,
            context.getString(R.string.notification_channel_download_result_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_download_result_desc)
            setShowBadge(false)
        }

        systemManager.createNotificationChannel(progressChannel)
        systemManager.createNotificationChannel(completionChannel)
    }

    fun buildProgressNotification(
        state: DownloadNotificationState,
        liveActivityStyleEnabled: Boolean = true,
    ): Notification {
        val statusText = buildStatusText(state)
        val title = when {
            state.isPreparing -> context.getString(R.string.notification_status_preparing)
            state.isBatch -> context.getString(
                R.string.notification_batch_completed, state.completedCount, state.totalCount,
            )
            state.isPaused -> context.getString(R.string.notification_title_paused_multiple, state.pausedCount)
            else -> state.singleTitle ?: context.getString(R.string.downloads_title)
        }
        val detail = when {
            state.isPreparing -> statusText
            state.isBatch -> buildBatchDetails(state)
            state.singleStatus == DownloadStatus.Merging -> statusText
            state.transferProgress != null -> context.getString(
                R.string.notification_content_progress_bytes,
                state.transferProgress, formatBytes(state.downloadedBytes), formatBytes(state.totalBytes!!),
            )
            state.downloadedBytes > 0 -> context.getString(
                R.string.notification_downloaded_bytes, formatBytes(state.downloadedBytes),
            )
            else -> statusText
        }
        val speed = state.speedBytesPerSec.takeIf { it > 0 && !state.isPreparing }?.let {
            context.getString(R.string.download_speed_format, formatBytes(it))
        }
        val content = listOfNotNull(detail, speed).joinToString(" · ")
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_download_for_offline_24)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSubText(statusText)
            .setOngoing(state.hasForegroundWork)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentPendingIntent())
            .setDeleteIntent(dismissPendingIntent(state.sessionId))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        when {
            state.isPreparing -> builder.setProgress(100, 0, true)
            state.isBatch -> builder.setProgress(state.totalCount, state.completedCount, false)
            state.singleStatus == DownloadStatus.Merging -> builder.setProgress(100, 0, true)
            state.transferProgress != null -> builder.setProgress(100, state.transferProgress!!, false)
            state.hasForegroundWork -> builder.setProgress(100, 0, true)
        }
        if (!state.isPreparing && state.pausableTaskIds.isNotEmpty()) {
            builder.addAction(
                R.drawable.ic_pause_24, context.getString(R.string.download_pause),
                pausePendingIntent(state.pausableTaskIds),
            )
        } else if (state.isPaused) {
            builder.addAction(
                R.drawable.ic_play_arrow_24, context.getString(R.string.download_resume),
                resumePendingIntent(state.pausedTaskIds),
            )
        }
        if (liveActivityStyleEnabled && state.hasForegroundWork) {
            builder.applyPromotedOngoing(buildShortCriticalText(state))
        }
        return builder.build()
    }

    private fun buildStatusText(state: DownloadNotificationState): String = context.getString(
        when {
            state.isPreparing -> R.string.notification_status_preparing
            state.isPaused -> R.string.notification_status_paused
            state.isBatch -> R.string.downloads_title
            state.singleStatus == DownloadStatus.Merging -> R.string.notification_status_merging
            state.singleStatus == DownloadStatus.Pending -> R.string.notification_status_waiting
            else -> R.string.notification_status_downloading
        },
    )

    private fun buildBatchDetails(state: DownloadNotificationState): String = buildList {
        fun count(value: Int, resource: Int) {
            if (value > 0) add(context.getString(resource, value))
        }
        count(state.runningCount, R.string.notification_count_downloading)
        count(state.processingCount, R.string.notification_count_processing)
        count(state.pendingCount, R.string.notification_count_waiting)
        count(state.pausedCount, R.string.notification_content_paused_count)
        count(state.failedCount, R.string.notification_count_failed)
        count(state.cancelledCount, R.string.notification_count_cancelled)
        count(state.skippedCount, R.string.notification_count_skipped)
    }.joinToString(" · ")

    private fun buildShortCriticalText(state: DownloadNotificationState): String = when {
        state.isPreparing -> context.getString(R.string.notification_short_preparing)
        state.isBatch -> context.getString(
            R.string.notification_short_completed, state.completedCount, state.totalCount,
        )
        state.singleStatus == DownloadStatus.Merging -> context.getString(R.string.notification_short_merging)
        state.transferProgress != null -> context.getString(R.string.notification_short_progress, state.transferProgress)
        else -> context.getString(R.string.notification_short_downloading)
    }

    fun clearProgress() = manager.cancel(NOTIFICATION_ID_PROGRESS)

    fun clearCompletion() = manager.cancel(NOTIFICATION_ID_COMPLETION)

    fun notifyProgress(notification: Notification) {
        manager.notifyIfAllowed(context, NOTIFICATION_ID_PROGRESS, notification)
    }

    fun showCompletion(summary: DownloadOutcomeSummary) {
        val total = summary.successCount + summary.failedCount + summary.cancelledCount + summary.skippedCount
        if (total <= 0) return

        val title = if (summary.failedCount == 0 && summary.cancelledCount == 0) {
            context.getString(R.string.notification_summary_all_completed)
        } else {
            context.getString(R.string.notification_summary_finished)
        }
        val content = context.getString(
            R.string.notification_summary_content,
            summary.successCount,
            summary.failedCount,
            summary.cancelledCount,
        ) + if (summary.skippedCount > 0) {
            " · " + context.getString(R.string.notification_count_skipped, summary.skippedCount)
        } else {
            ""
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETION_ID)
            .setSmallIcon(R.drawable.ic_download_for_offline_24)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent())
            .build()

        manager.notifyIfAllowed(context, NOTIFICATION_ID_COMPLETION, notification)
    }

    private fun contentPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_DOWNLOADS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pausePendingIntent(taskIds: Set<Long>): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_PAUSE_ALL,
        Intent(context, DownloadNotificationReceiver::class.java)
            .setAction(DownloadNotificationReceiver.ACTION_PAUSE)
            .putExtra(DownloadNotificationReceiver.EXTRA_TASK_IDS, taskIds.toLongArray()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun resumePendingIntent(taskIds: Set<Long>): PendingIntent = PendingIntent.getForegroundService(
        context,
        REQUEST_CODE_RESUME_ALL,
        Intent(context, DownloadForegroundService::class.java)
            .setAction(DownloadForegroundService.ACTION_RESUME)
            .putExtra(DownloadNotificationReceiver.EXTRA_TASK_IDS, taskIds.toLongArray()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun dismissPendingIntent(sessionId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_DISMISS,
        Intent(context, DownloadNotificationReceiver::class.java)
            .setAction(DownloadNotificationReceiver.ACTION_DISMISS)
            .putExtra(DownloadNotificationReceiver.EXTRA_SESSION_ID, sessionId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun formatBytes(bytes: Long): String = context.formatByteCount(bytes)



    companion object {
        const val NOTIFICATION_ID_PROGRESS: Int = 1101
        private const val NOTIFICATION_ID_COMPLETION: Int = 1102

        private const val CHANNEL_PROGRESS_ID = "download_progress"
        private const val CHANNEL_COMPLETION_ID = "download_completion"

        private const val REQUEST_CODE_OPEN_DOWNLOADS = 2101
        private const val REQUEST_CODE_PAUSE_ALL = 2102
        private const val REQUEST_CODE_RESUME_ALL = 2103
        private const val REQUEST_CODE_DISMISS = 2104
    }
}
