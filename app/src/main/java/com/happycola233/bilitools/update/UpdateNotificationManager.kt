package com.happycola233.bilitools.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.happycola233.bilitools.R
import java.util.Locale

internal class UpdateNotificationManager(
    private val context: Context,
) {
    private val manager: NotificationManagerCompat = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(NotificationManager::class.java) ?: return

        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS_ID,
            context.getString(R.string.update_notification_channel_progress_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.update_notification_channel_progress_desc)
            setShowBadge(false)
        }

        val resultChannel = NotificationChannel(
            CHANNEL_RESULT_ID,
            context.getString(R.string.update_notification_channel_result_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.update_notification_channel_result_desc)
            setShowBadge(false)
        }

        systemManager.createNotificationChannel(progressChannel)
        systemManager.createNotificationChannel(resultChannel)
    }

    fun buildProgressNotification(
        versionLabel: String,
        downloadedBytes: Long,
        totalBytes: Long,
    ): Notification {
        val progress = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val contentText = if (totalBytes > 0L) {
            context.getString(
                R.string.update_notification_progress_content_known,
                progress,
                formatBytes(downloadedBytes),
                formatBytes(totalBytes),
            )
        } else {
            context.getString(
                R.string.update_notification_progress_content_unknown,
                formatBytes(downloadedBytes),
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_update_24)
            .setContentTitle(
                context.getString(R.string.update_notification_progress_title, versionLabel),
            )
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, totalBytes <= 0L)
            .build()
    }

    fun notifyProgress(notification: Notification) {
        runCatching {
            manager.notify(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    fun showReadyToInstall(
        versionLabel: String,
        apkPath: String,
    ) {
        val installIntent = UpdateInstallActivity.createIntent(context, apkPath)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_INSTALL,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT_ID)
            .setSmallIcon(R.drawable.ic_update_24)
            .setContentTitle(context.getString(R.string.update_notification_ready_title))
            .setContentText(
                context.getString(R.string.update_notification_ready_content, versionLabel),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_download_for_offline_24,
                context.getString(R.string.update_notification_action_install),
                pendingIntent,
            )
            .build()

        runCatching {
            manager.notify(NOTIFICATION_ID_RESULT, notification)
        }
    }

    fun showFailure(
        versionLabel: String,
        errorMessage: String,
        releasePageUrl: String,
    ) {
        val openReleaseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(releasePageUrl))
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_RELEASE,
            openReleaseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT_ID)
            .setSmallIcon(R.drawable.ic_update_24)
            .setContentTitle(context.getString(R.string.update_notification_failed_title, versionLabel))
            .setContentText(
                context.getString(R.string.update_notification_failed_content, errorMessage),
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.update_notification_failed_content, errorMessage),
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_github_invertocat_black,
                context.getString(R.string.update_notification_action_open_release),
                pendingIntent,
            )
            .build()

        runCatching {
            manager.notify(NOTIFICATION_ID_RESULT, notification)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }

    companion object {
        const val NOTIFICATION_ID_PROGRESS = 1201
        private const val NOTIFICATION_ID_RESULT = 1202

        private const val CHANNEL_PROGRESS_ID = "update_download_progress"
        private const val CHANNEL_RESULT_ID = "update_download_result"

        private const val REQUEST_CODE_INSTALL = 2201
        private const val REQUEST_CODE_OPEN_RELEASE = 2202
    }
}
