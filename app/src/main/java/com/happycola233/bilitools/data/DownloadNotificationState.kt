package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.isManagedTransfer

data class DownloadNotificationState(
    val sessionId: Long = 0,
    val taskIds: Set<Long> = emptySet(),
    val pausableTaskIds: Set<Long> = emptySet(),
    val pausedTaskIds: Set<Long> = emptySet(),
    val runningCount: Int = 0,
    val processingCount: Int = 0,
    val pendingCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val cancelledCount: Int = 0,
    val skippedCount: Int = 0,
    val isPreparing: Boolean = false,
    val singleTitle: String? = null,
    val singleStatus: DownloadStatus? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val speedBytesPerSec: Long = 0,
    val completionReported: Boolean = false,
) {
    val totalCount: Int get() = taskIds.size
    val completedCount: Int get() = successCount + skippedCount
    val pausedCount: Int get() = pausedTaskIds.size
    val isBatch: Boolean get() = totalCount > 1
    val hasForegroundWork: Boolean
        get() = isPreparing || runningCount + processingCount + pendingCount > 0
    val isPaused: Boolean get() = !hasForegroundWork && pausedCount > 0
    val isFinished: Boolean get() = totalCount > 0 && !hasForegroundWork && pausedCount == 0
    val transferProgress: Int?
        get() = totalBytes?.takeIf { it > 0L }?.let {
            ((downloadedBytes.toDouble() / it) * 100).toInt().coerceIn(0, 100)
        }
    val outcome: DownloadOutcomeSummary
        get() = DownloadOutcomeSummary(successCount, failedCount, cancelledCount, skippedCount)

    /** 字节与速度允许限频；任务范围、阶段和结果变化必须立即呈现。 */
    internal fun hasSameStructure(other: DownloadNotificationState): Boolean =
        copy(downloadedBytes = 0, totalBytes = null, speedBytesPerSec = 0) ==
            other.copy(downloadedBytes = 0, totalBytes = null, speedBytesPerSec = 0) &&
            (totalBytes != null) == (other.totalBytes != null)
}

data class DownloadOutcomeSummary(
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val cancelledCount: Int = 0,
    val skippedCount: Int = 0,
)

internal data class NotificationTaskOutcome(val id: Long, val status: DownloadStatus)

internal data class DownloadNotificationSessionSnapshot(
    val id: Long = 0,
    val outcomes: List<NotificationTaskOutcome> = emptyList(),
    val completionReported: Boolean = false,
)

/** 由仓库的 lock 保护。终态保留到下一次下载，删除任务也不会让分母和结果丢失。 */
internal class DownloadNotificationSession {
    private var id = 0L
    private val statuses = linkedMapOf<Long, DownloadStatus>()
    private var submissions = 0
    private var completionReported = false

    private val hasActiveTasks: Boolean
        get() = statuses.values.any { it == DownloadStatus.Pending || it == DownloadStatus.Running || it == DownloadStatus.Merging }

    fun beginSubmission() {
        // 新下载不夹带此前整批暂停的任务；并行提交则加入当前通知。
        if (submissions == 0 && !hasActiveTasks &&
            (statuses.values.any { it == DownloadStatus.Paused } || completionReported || statuses.isEmpty())
        ) reset()
        submissions++
    }

    fun endSubmission() {
        submissions--
    }

    private fun reset() {
        id++
        statuses.clear()
        completionReported = false
    }

    fun record(item: DownloadItem, previous: DownloadItem? = null) {
        val startsTask = previous == null ||
            (item.status == DownloadStatus.Pending && previous.status != DownloadStatus.Pending)
        if (startsTask && submissions == 0 && (completionReported || statuses.isEmpty())) reset()
        if (startsTask || item.id in statuses) statuses[item.id] = item.status
    }

    fun remove(taskId: Long) {
        val status = statuses[taskId] ?: return
        if (status == DownloadStatus.Pending || status == DownloadStatus.Running ||
            status == DownloadStatus.Merging || status == DownloadStatus.Paused
        ) statuses[taskId] = DownloadStatus.Cancelled
    }

    fun markCompletionReported(sessionId: Long) {
        if (id == sessionId) completionReported = true
    }

    fun saved(): DownloadNotificationSessionSnapshot = DownloadNotificationSessionSnapshot(
        id, statuses.map { (taskId, status) -> NotificationTaskOutcome(taskId, status) }, completionReported,
    )

    fun restore(saved: DownloadNotificationSessionSnapshot, tasks: Map<Long, DownloadItem>) {
        id = saved.id
        completionReported = saved.completionReported
        statuses.clear()
        saved.outcomes.forEach { outcome ->
            statuses[outcome.id] = tasks[outcome.id]?.status ?: outcome.status
            if (outcome.id !in tasks) remove(outcome.id)
        }
    }

    fun snapshot(
        tasks: Map<Long, DownloadItem>,
        isTotalKnown: (DownloadItem) -> Boolean = { it.totalBytes > 0 && !it.progressIndeterminate },
    ): DownloadNotificationState {
        val items = statuses.keys.mapNotNull(tasks::get)
        val single = if (statuses.size == 1) items.singleOrNull() else null
        return DownloadNotificationState(
            sessionId = id,
            taskIds = statuses.keys.toSet(),
            pausableTaskIds = items.filter { item ->
                item.status == DownloadStatus.Pending ||
                    (item.taskType.isManagedTransfer &&
                        (item.status == DownloadStatus.Running || item.status == DownloadStatus.Merging))
            }.mapTo(linkedSetOf()) { it.id },
            pausedTaskIds = statuses.filterValues { it == DownloadStatus.Paused }.keys.toSet(),
            runningCount = items.count { it.status == DownloadStatus.Running && it.taskType.isManagedTransfer },
            processingCount = items.count {
                it.status == DownloadStatus.Merging ||
                    (it.status == DownloadStatus.Running && !it.taskType.isManagedTransfer)
            },
            pendingCount = statuses.values.count { it == DownloadStatus.Pending },
            successCount = statuses.values.count { it == DownloadStatus.Success },
            failedCount = statuses.values.count { it == DownloadStatus.Failed },
            cancelledCount = statuses.values.count { it == DownloadStatus.Cancelled },
            skippedCount = statuses.values.count { it == DownloadStatus.Unavailable },
            isPreparing = submissions > 0,
            singleTitle = single?.fileName?.ifBlank { single.title },
            singleStatus = if (single?.status == DownloadStatus.Running && !single.taskType.isManagedTransfer) {
                DownloadStatus.Merging
            } else single?.status,
            downloadedBytes = single?.downloadedBytes ?: 0,
            totalBytes = single?.takeIf(isTotalKnown)?.totalBytes,
            speedBytesPerSec = items.filter { it.status == DownloadStatus.Running }.sumOf { it.speedBytesPerSec },
            completionReported = completionReported,
        )
    }
}
