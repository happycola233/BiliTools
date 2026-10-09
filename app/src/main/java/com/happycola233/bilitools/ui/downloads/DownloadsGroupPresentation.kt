package com.happycola233.bilitools.ui.downloads

import android.content.Context
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.formatEstimatedTime
import com.happycola233.bilitools.core.localizedStatusDetail
import com.happycola233.bilitools.data.DownloadTransferEstimate
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadProgressRules
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.isManagedTransfer
import com.happycola233.bilitools.data.model.isResolvedWithoutFailure

internal enum class DownloadsGroupAction { Pause, Resume, Retry, Expand }

internal data class DownloadsGroupPresentation(
    val action: DownloadsGroupAction,
    val completed: Boolean,
    val executing: Boolean,
    /** 组进度圆环的比例，按预计耗时加权，见 [estimateGroupProgress]。 */
    val progressFraction: Float,
    /** 「x / y 项已完成」中的 x：保存成功或确认无资源的任务数。 */
    val resolvedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val missingCount: Int,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
) {
    // 正在工作却还没有任何可计的进度（准备中、总量未知）时，圆环只表示忙碌。
    val progressUnknown: Boolean get() = executing && progressFraction == 0f
}

/** 组进度中媒体传输所占的比重；字幕、封面、弹幕等附属任务平分其余部分。 */
private const val MEDIA_PROGRESS_SHARE = 0.9f

/** 媒体传输结束、进入合并或转码等后处理时计入的比例；保存完成才算满。 */
private const val MEDIA_POST_PROCESS_PROGRESS = 0.95f

/** 仍有任务未处理完时圆环不画满。 */
private const val MAX_UNFINISHED_PROGRESS = 0.99f

/**
 * 按预计耗时加权估算组进度。耗时主要花在音视频等媒体传输上，按字节计入；
 * 字幕、封面、AI 总结等附属任务体积小、耗时与字节无关，只按是否完成平分一小份。
 * 没有媒体任务时退回按项数计算。
 *
 * 失败与取消的媒体任务不计已传输量：重试可能从头开始，圆环不能因此倒退。
 * 已确认无资源的任务属于正常处理完毕，与保存成功同样计满。
 */
internal fun estimateGroupProgress(tasks: List<DownloadItem>): Float {
    if (tasks.isEmpty()) return 0f
    if (tasks.all { it.status.isResolvedWithoutFailure }) return 1f
    val (media, auxiliary) = tasks.partition { it.taskType.isManagedTransfer }
    val auxiliaryProgress = if (auxiliary.isEmpty()) 0f
        else auxiliary.count { it.status.isResolvedWithoutFailure }.toFloat() / auxiliary.size
    if (media.isEmpty()) return auxiliaryProgress.coerceAtMost(MAX_UNFINISHED_PROGRESS)

    val knownWeights = media.mapNotNull { it.progressWeightBytes }
    // 大小未知的媒体任务按已知任务的平均大小计；全部未知时各占一份。
    val fallbackWeight = if (knownWeights.isEmpty()) 1.0 else knownWeights.average()
    var weightedProgress = 0.0
    var totalWeight = 0.0
    media.forEach { task ->
        val weight = task.progressWeightBytes?.toDouble() ?: fallbackWeight
        weightedProgress += weight * task.mediaProgress()
        totalWeight += weight
    }
    val mediaProgress = (weightedProgress / totalWeight).toFloat()
    val estimate = if (auxiliary.isEmpty()) mediaProgress
        else MEDIA_PROGRESS_SHARE * mediaProgress + (1f - MEDIA_PROGRESS_SHARE) * auxiliaryProgress
    return estimate.coerceIn(0f, MAX_UNFINISHED_PROGRESS)
}

private val DownloadItem.progressWeightBytes: Long?
    get() = totalBytes.takeIf { it > 0L } ?: outputBytes?.takeIf { it > 0L }

private fun DownloadItem.mediaProgress(): Float = when (status) {
    DownloadStatus.Success,
    DownloadStatus.Unavailable -> 1f
    DownloadStatus.Merging -> MEDIA_POST_PROCESS_PROGRESS
    DownloadStatus.Pending,
    DownloadStatus.Running,
    DownloadStatus.Paused -> if (progressIndeterminate) 0f
        else DownloadProgressRules.normalizeTaskProgress(status, progress) / 100f * MEDIA_POST_PROCESS_PROGRESS
    DownloadStatus.Failed,
    DownloadStatus.Cancelled -> 0f
}

/** 所有任务都已保存或确认无资源；决定该组归入「已完成」分区。 */
internal val DownloadGroup.isCompleted: Boolean
    get() = tasks.isNotEmpty() && tasks.all { it.status.isResolvedWithoutFailure }

internal fun resolveDownloadsGroupPresentation(group: DownloadGroup): DownloadsGroupPresentation {
    val tasks = group.tasks
    val completed = group.isCompleted
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
        progressFraction = estimateGroupProgress(tasks),
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
