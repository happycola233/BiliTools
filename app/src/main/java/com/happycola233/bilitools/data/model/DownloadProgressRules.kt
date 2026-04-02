package com.happycola233.bilitools.data.model

object DownloadProgressRules {
    fun normalizeTaskProgress(status: DownloadStatus, progress: Int): Int {
        val clamped = progress.coerceIn(0, 100)
        return when (status) {
            DownloadStatus.Success -> 100
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

    fun normalizeAggregateProgress(progress: Int, allTasksSucceeded: Boolean): Int {
        val clamped = progress.coerceIn(0, 100)
        return if (allTasksSucceeded) 100 else clamped.coerceAtMost(99)
    }
}
