package com.happycola233.bilitools.ui.downloads

import android.content.Context
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.formatEstimatedTime
import com.happycola233.bilitools.core.localizedStatusDetail
import com.happycola233.bilitools.data.DownloadTransferEstimate
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.isManagedTransfer
import com.happycola233.bilitools.data.model.isResolvedWithoutFailure

internal enum class DownloadsGroupAction { Pause, Resume, Retry, Expand }

internal data class DownloadsGroupPresentation(
    val action: DownloadsGroupAction,
    val completed: Boolean,
    val executing: Boolean,
    val completionFraction: Float,
    val resolvedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val missingCount: Int,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
) {
    // 尚无任务产出结果时只表示正在工作，不用传输字节冒充整组任务的完成比例。
    val awaitingFirstResult: Boolean get() = executing && resolvedCount == 0
}

internal fun resolveDownloadsGroupPresentation(group: DownloadGroup): DownloadsGroupPresentation {
    val tasks = group.tasks
    val completed = tasks.isNotEmpty() && tasks.all { it.status.isResolvedWithoutFailure }
    val executing = tasks.any {
        it.status == DownloadStatus.Running || it.status == DownloadStatus.Merging
    }
    // 附属资源开始写入后不可暂停，按钮能力与 DownloadRepository.pauseGroup 保持一致。
    val canPause = tasks.any {
        it.status == DownloadStatus.Pending ||
            (it.taskType.isManagedTransfer && (it.status == DownloadStatus.Running || it.status == DownloadStatus.Merging))
    }
    val canResume = tasks.any { it.status == DownloadStatus.Paused && it.userPaused }
    val failedCount = tasks.count { it.status == DownloadStatus.Failed }
    val resolvedCount = tasks.count { it.status.isResolvedWithoutFailure }
    return DownloadsGroupPresentation(
        action = when {
            canPause -> DownloadsGroupAction.Pause
            canResume -> DownloadsGroupAction.Resume
            executing -> DownloadsGroupAction.Expand
            failedCount > 0 -> DownloadsGroupAction.Retry
            else -> DownloadsGroupAction.Expand
        },
        completed = completed,
        executing = executing,
        // 圆环与「已完成 x/y 项」使用同一口径；失败、取消、暂停和合并中的任务均不提前计入。
        // 已确认无资源的任务属于正常处理完毕，额外以「跳过」说明。
        completionFraction = if (tasks.isEmpty()) 0f else resolvedCount.toFloat() / tasks.size,
        resolvedCount = resolvedCount,
        skippedCount = tasks.count { it.status == DownloadStatus.Unavailable },
        failedCount = failedCount,
        missingCount = tasks.count { it.status == DownloadStatus.Success && it.outputMissing },
        speedBytesPerSec = tasks.sumOf { if (it.status == DownloadStatus.Running) it.speedBytesPerSec else 0L },
        etaSeconds = calculateDownloadingEtaSeconds(listOf(group)),
    )
}

internal fun DownloadsGroupPresentation.headerSupportingText(context: Context, group: DownloadGroup): String = when {
    completed -> formatDownloadCreatedAt(context, group.createdAt)
    else -> context.getString(R.string.downloads_group_resolved_summary, resolvedCount, group.tasks.size)
}

internal fun DownloadsGroupPresentation.footerStatus(
    context: Context,
    group: DownloadGroup,
    transferEstimate: DownloadTransferEstimate,
): String? {
    val status = when {
        completed -> null
        transferEstimate.speedBytesPerSec > 0 -> listOfNotNull(
            context.getString(R.string.download_speed_format, context.formatDownloadBytes(transferEstimate.speedBytesPerSec)),
            transferEstimate.etaSeconds?.let { context.getString(R.string.download_eta_format, context.formatEstimatedTime(it)) },
        ).joinToString(" · ")
        executing -> group.tasks.firstNotNullOfOrNull { task ->
            if (task.status == DownloadStatus.Running || task.status == DownloadStatus.Merging) {
                task.localizedStatusDetail(context)?.takeIf { it.isNotBlank() }
            } else null
        } ?: context.getString(when {
            group.tasks.any { it.status == DownloadStatus.Merging } -> R.string.download_detail_merging
            group.tasks.any { it.status == DownloadStatus.Running && !it.progressIndeterminate } -> R.string.downloads_section_downloading
            else -> R.string.downloads_group_preparing
        })
        action == DownloadsGroupAction.Pause -> context.getString(R.string.download_status_pending)
        action == DownloadsGroupAction.Resume -> context.getString(R.string.downloads_group_paused)
        failedCount > 0 -> null
        group.tasks.any { it.status == DownloadStatus.Paused } -> context.getString(R.string.downloads_group_waiting)
        else -> context.getString(R.string.download_status_cancelled)
    }
    return listOfNotNull(
        status,
        failedCount.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.downloads_group_failed_count, it, it) },
        skippedCount.takeIf { it > 0 }?.let { context.getString(R.string.downloads_group_progress_unavailable, it) },
        missingCount.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.downloads_group_missing_count, it, it) },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
}

internal fun calculateDownloadingEtaSeconds(groups: List<DownloadGroup>): Long? {
    val remainingTasks = groups.flatMap { it.tasks }.filter { !it.status.isResolvedWithoutFailure }
    // 失败、暂停、合并或未知工作量尚未解决时，不能承诺整组/整个分区的剩余时间。
    if (remainingTasks.any {
        (it.status != DownloadStatus.Pending && it.status != DownloadStatus.Running) ||
            !it.taskType.isManagedTransfer || it.totalBytes <= 0L || it.progressIndeterminate ||
            // 合并任务的已知字节传完后仍可能有未知大小的流；以下载器的可估算状态为准。
            // 启动采样或某条流停滞时，也不能借用其他任务的速度承诺完成时间。
            (it.status == DownloadStatus.Running && it.etaSeconds == null)
    }) return null
    val speed = remainingTasks.sumOf { if (it.status == DownloadStatus.Running) it.speedBytesPerSec else 0L }
    if (speed <= 0L) return null
    val remainingBytes = remainingTasks.sumOf { (it.totalBytes - it.downloadedBytes).coerceAtLeast(0L) }
    return if (remainingBytes > 0L) (remainingBytes + speed - 1L) / speed else null
}
