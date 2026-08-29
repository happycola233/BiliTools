package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadStatus

/** 将持久化状态映射为进程重启后可安全展示的状态，不在恢复阶段自动启动任务。 */
internal object DownloadRestartPolicy {
    fun restore(status: DownloadStatus, userPaused: Boolean): RestoredDownloadLifecycle {
        return when {
            status == DownloadStatus.Pending -> RestoredDownloadLifecycle(
                status = DownloadStatus.Paused,
                userPaused = true,
                interrupted = false,
            )

            status == DownloadStatus.Running ||
                status == DownloadStatus.Merging ||
                (status == DownloadStatus.Paused && !userPaused) -> RestoredDownloadLifecycle(
                status = DownloadStatus.Failed,
                userPaused = false,
                interrupted = true,
            )

            else -> RestoredDownloadLifecycle(
                status = status,
                userPaused = userPaused,
                interrupted = false,
            )
        }
    }
}

internal data class RestoredDownloadLifecycle(
    val status: DownloadStatus,
    val userPaused: Boolean,
    val interrupted: Boolean,
)
