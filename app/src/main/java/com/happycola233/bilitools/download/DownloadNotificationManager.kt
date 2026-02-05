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
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.DownloadNotificationState
import com.happycola233.bilitools.data.DownloadOutcomeSummary
import com.happycola233.bilitools.ui.MainActivity
import java.util.Locale

internal class DownloadNotificationManager(
    private val context: Context,
) {
    private val manager: NotificationManagerCompat = NotificationManagerCompat.from(context)

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

    fun buildProgressNotification(state: DownloadNotificationState): Notification {
        val title = when {
            state.inProgressCount > 1 -> context.getString(
                R.string.notification_title_downloading_multiple,
                state.inProgressCount,
            )
            state.inProgressCount == 0 && state.pausedCount > 0 -> context.getString(
                R.string.notification_title_paused_multiple,
                state.pausedCount,
            )
            !state.primaryTitle.isNullOrBlank() -> state.primaryTitle
            else -> context.getString(R.string.notification_title_downloading_default)
        }
        val progressText = if (state.totalBytes > 0L) {
            context.getString(
                R.string.notification_content_progress_bytes,
                state.progress,
                formatBytes(state.downloadedBytes),
                formatBytes(state.totalBytes),
            )
        } else {
            context.getString(R.string.notification_content_progress_percent, state.progress)
        }
        val speedText = if (state.speedBytesPerSec > 0L) {
            context.getString(
                R.string.download_speed_format,
                formatBytes(state.speedBytesPerSec),
            )
        } else {
            null
        }
        val etaText = state.etaSeconds?.let { seconds ->
            context.getString(R.string.download_eta_format, formatEta(seconds))
        }

        val content = listOfNotNull(progressText, speedText, etaText).joinToString(" | ")
        val subText = if (state.pausedCount > 0) {
            context.getString(R.string.notification_content_paused_count, state.pausedCount)
        } else {
            null
        }

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_download_for_offline_24)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, state.progress, state.totalBytes <= 0L)
            .addAction(
                if (state.inProgressCount > 0) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24,
                if (state.inProgressCount > 0) {
                    context.getString(R.string.download_pause)
                } else {
                    context.getString(R.string.download_resume)
                },
                actionPendingIntent(
                    if (state.inProgressCount > 0) {
                        DownloadForegroundService.ACTION_PAUSE_ALL
                    } else {
                        DownloadForegroundService.ACTION_RESUME_ALL
                    },
                    if (state.inProgressCount > 0) {
                        REQUEST_CODE_PAUSE_ALL
                    } else {
                        REQUEST_CODE_RESUME_ALL
                    },
                ),
            )
            .build()
    }

    fun notifyProgress(notification: Notification) {
        runCatching {
            manager.notify(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    fun showCompletion(summary: DownloadOutcomeSummary) {
        val total = summary.successCount + summary.failedCount + summary.cancelledCount
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
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETION_ID)
            .setSmallIcon(R.drawable.ic_download_for_offline_24)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent())
            .build()

        runCatching {
            manager.notify(NOTIFICATION_ID_COMPLETION, notification)
        }
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

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    private fun formatEta(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0L)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remain = seconds % 60
        return when {
            hours > 0L -> String.format(Locale.US, "%d小时%02d分", hours, minutes)
            minutes > 0L -> String.format(Locale.US, "%d分%02d秒", minutes, remain)
            else -> String.format(Locale.US, "%d秒", remain)
        }
    }

    companion object {
        const val NOTIFICATION_ID_PROGRESS: Int = 1101
        private const val NOTIFICATION_ID_COMPLETION: Int = 1102

        private const val CHANNEL_PROGRESS_ID = "download_progress"
        private const val CHANNEL_COMPLETION_ID = "download_completion"

        private const val REQUEST_CODE_OPEN_DOWNLOADS = 2101
        private const val REQUEST_CODE_PAUSE_ALL = 2102
        private const val REQUEST_CODE_RESUME_ALL = 2103
    }
}
