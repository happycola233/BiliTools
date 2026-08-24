package com.happycola233.bilitools.data.model

object DownloadProgressRules {
    fun normalizeTaskProgress(status: DownloadStatus, progress: Int): Int {
        val clamped = progress.coerceIn(0, 100)
        return when (status) {
            DownloadStatus.Success,
            DownloadStatus.Unavailable -> 100
            DownloadStatus.Pending,
            DownloadStatus.Running,
            DownloadStatus.Paused,
            DownloadStatus.Merging -> clamped.coerceAtMost(99)
            DownloadStatus.Failed,
            DownloadStatus.Cancelled -> clamped
        }
    }

    fun normalizeTask(item: DownloadItem): DownloadItem {
        val normalizedProgress = normalizeTaskProgress(item.status, item.progress)
        return if (normalizedProgress == item.progress) {
            item
        } else {
            item.copy(progress = normalizedProgress)
        }
    }

    fun normalizeAggregateProgress(progress: Int, allTasksResolved: Boolean): Int {
        val clamped = progress.coerceIn(0, 100)
        return if (allTasksResolved) 100 else clamped.coerceAtMost(99)
    }
}
