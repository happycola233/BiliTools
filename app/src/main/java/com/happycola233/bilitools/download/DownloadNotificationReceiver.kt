package com.happycola233.bilitools.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.happycola233.bilitools.core.appContainer

class DownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        when (intent.action) {
            ACTION_PAUSE -> container.downloadRepository.pauseNotificationTasks(
                intent.getLongArrayExtra(EXTRA_TASK_IDS)?.toSet().orEmpty(),
            )
            ACTION_DISMISS -> container.downloadNotifications.dismiss(
                intent.getLongExtra(EXTRA_SESSION_ID, 0),
            )
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.happycola233.bilitools.download.PAUSE_NOTIFICATION_TASKS"
        const val ACTION_DISMISS = "com.happycola233.bilitools.download.DISMISS_NOTIFICATION"
        const val EXTRA_TASK_IDS = "notification_task_ids"
        const val EXTRA_SESSION_ID = "notification_session_id"
    }
}
