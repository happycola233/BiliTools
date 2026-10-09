package com.happycola233.bilitools.ui.downloads

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.localized
import com.happycola233.bilitools.core.localizedEmbedWarning
import com.happycola233.bilitools.core.localizedErrorMessage
import com.happycola233.bilitools.core.localizedStatusDetail
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadMessageCode
import com.happycola233.bilitools.data.model.DownloadProgressRules
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.isManagedTransfer
import com.happycola233.bilitools.data.model.isResolvedWithoutFailure
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.SegmentedListShapes
import com.happycola233.bilitools.ui.theme.animateSegmentShape

/**
 * 下载卡片比单行设置项高得多，按下只收圆一档（`MaterialTheme.shapes.extraLarge`），
 * 不像设置项那样收成胶囊，否则整张卡片会从圆角矩形变成一团。
 */
private val GroupPressedCorner = 28.dp

/** 任务段按下采用 M3 Expressive 分段列表的按压形状（`MaterialTheme.shapes.large`）。 */
private val TaskPressedCorner = 16.dp

/**
 * 一个下载组在列表中的整体：头部段始终可见；展开后任务段与操作段以分段列表的间隙接在下方，
 * 头部底边随之收成组内小圆角，整组读作一张分段卡片。多选时收起任务，只保留头部段参与选择。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadsGroupCard(
    group: DownloadGroup,
    selectionMode: Boolean,
    selected: Boolean,
    expanded: Boolean,
    swiped: Boolean,
    anyGroupSwiped: Boolean,
    onSwipedGroupChange: (Long?) -> Unit,
    onToggleSelection: () -> Unit,
    onToggleExpanded: () -> Unit,
    onDelete: (deleteFiles: Boolean) -> Unit,
    onPauseGroup: () -> Unit,
    onResumeGroup: () -> Unit,
    onReparse: () -> Unit,
    onShowDetails: () -> Unit,
    onTaskPauseResume: (DownloadItem) -> Unit,
    onTaskRetry: (DownloadItem) -> Unit,
    onTaskClick: (DownloadItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
    selectionMotion: DownloadsSelectionMotion = rememberDownloadsSelectionMotion(selectionMode),
) {
    val haptics = rememberAppHaptics()
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember(group.id) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val groupContainerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else AppSurfaces.cardContainerColor,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "downloadsGroupContainerColor",
    )
    val groupHeadlineColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "downloadsGroupHeadlineColor",
    )
    val groupSupportingColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "downloadsGroupSupportingColor",
    )
    val longClickLabel = stringResource(R.string.downloads_multi_manage)
    val tasksVisible = expanded && !selectionMode
    // 展开时头部底边收成组内小圆角，与下方任务段连成一组；选中只换底色，不改形状。
    val headerShape = animateSegmentShape(
        topCorner = SegmentedListShapes.OuterCorner,
        bottomCorner = SegmentedListShapes.edgeCorner(!tasksVisible),
        pressed = pressed,
        pressedCorner = GroupPressedCorner,
    )

    val allMissing = remember(group.tasks) {
        val savedTasks = group.tasks.filter { it.status == DownloadStatus.Success }
        savedTasks.isNotEmpty() &&
            savedTasks.all { it.outputMissing } &&
            group.tasks.all { it.status.isResolvedWithoutFailure }
    }
    val presentation = remember(group.tasks) { resolveDownloadsGroupPresentation(group) }

    DownloadsGroupSwipe(
        groupId = group.id,
        enabled = !selectionMode,
        swiped = swiped,
        anyGroupSwiped = anyGroupSwiped,
        onSwipedGroupChange = onSwipedGroupChange,
        onDelete = onDelete,
        modifier = modifier.fillMaxWidth().padding(
            start = selectionMotion.cardInset,
            end = selectionMotion.cardInset,
            bottom = 8.dp,
        ),
    ) { swipeModifier ->
        Column(swipeModifier.fillMaxWidth()) {
            Surface(
                color = groupContainerColor,
                shape = headerShape,
                modifier = Modifier
                    .fillMaxWidth()
                    // 长按与拖动多选由列表统一识别（见 dragToSelectGroups），这里只为无障碍保留长按入口。
                    .then(
                        if (selectionMode) Modifier
                        else Modifier.semantics {
                            onLongClick(longClickLabel) {
                                onToggleSelection()
                                true
                            }
                        },
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        // 按压反馈由形状变圆承担，与设置页列表一致，不叠加涟漪。
                        indication = null,
                    ) {
                        when {
                            selectionMode -> {
                                haptics.toggle(!selected)
                                onToggleSelection()
                            }

                            anyGroupSwiped -> onSwipedGroupChange(null)
                            else -> {
                                haptics.tap()
                                onToggleExpanded()
                            }
                        }
                    },
            ) {
                DownloadsGroupHeader(
                    group = group,
                    presentation = presentation,
                    selectionMode = selectionMode,
                    selectionMotion = selectionMotion,
                    selected = selected,
                    expanded = expanded,
                    allMissing = allMissing,
                    headlineColor = groupHeadlineColor,
                    supportingColor = groupSupportingColor,
                    coverPlaceholderColor = AppSurfaces.insetContainerColor,
                    onToggleSelection = onToggleSelection,
                    onToggleExpanded = onToggleExpanded,
                    onAction = {
                        when (presentation.action) {
                            DownloadsGroupAction.Pause -> onPauseGroup()
                            DownloadsGroupAction.Resume -> onResumeGroup()
                            DownloadsGroupAction.Retry -> group.tasks
                                .filter { it.status == DownloadStatus.Failed }.forEach(onTaskRetry)
                            DownloadsGroupAction.Expand -> onToggleExpanded()
                        }
                    },
                )
            }

            AnimatedVisibility(
                visible = tasksVisible,
                enter = fadeIn(motionScheme.defaultEffectsSpec()) + expandVertically(motionScheme.defaultSpatialSpec()),
                exit = fadeOut(motionScheme.fastEffectsSpec()) + shrinkVertically(motionScheme.defaultSpatialSpec()),
            ) {
                Column(
                    modifier = Modifier.padding(top = SegmentedListShapes.Gap),
                    verticalArrangement = Arrangement.spacedBy(SegmentedListShapes.Gap),
                ) {
                    group.tasks.forEach { task ->
                        DownloadTaskSegment(
                            item = task,
                            onPauseResume = { onTaskPauseResume(task) },
                            onRetry = { onTaskRetry(task) },
                            onClick = { bounds -> onTaskClick(task, bounds) },
                        )
                    }
                    Surface(
                        color = AppSurfaces.cardContainerColor,
                        shape = RoundedCornerShape(
                            topStart = SegmentedListShapes.InnerCorner,
                            topEnd = SegmentedListShapes.InnerCorner,
                            bottomStart = SegmentedListShapes.OuterCorner,
                            bottomEnd = SegmentedListShapes.OuterCorner,
                        ),
                    ) {
                        DownloadsGroupActions(
                            reparseEnabled = group.sourceUrl() != null,
                            onReparse = onReparse,
                            onShowDetails = onShowDetails,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 任务段的辅助文字比组头部低一级，多行时保持与组头部辅助文字相同的节奏。 */
private val TaskDetailLineHeight = 18.sp

/** 缓存任务段的布局坐标，供操作菜单锚定；用普通引用持有以免每次布局都触发重组。 */
private class TaskRowCoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

/**
 * 组内的一个任务段。点按弹出锚定在本段旁的操作菜单（打开、分享、删除）；
 * 行尾只保留与任务状态相关的暂停、继续或重试。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadTaskSegment(
    item: DownloadItem,
    onPauseResume: () -> Unit,
    onRetry: () -> Unit,
    onClick: (Rect) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = animateSegmentShape(
        topCorner = SegmentedListShapes.InnerCorner,
        bottomCorner = SegmentedListShapes.InnerCorner,
        pressed = pressed,
        pressedCorner = TaskPressedCorner,
    )
    val managed = item.taskType.isManagedTransfer
    val isMissing = item.status == DownloadStatus.Success && item.outputMissing
    val transferEstimate = rememberDownloadTransferEstimate(listOf(item), item.speedBytesPerSec, item.etaSeconds)
    val actionType = when {
        item.status == DownloadStatus.Pending -> TaskAction.Pause
        managed && (item.status == DownloadStatus.Running ||
            item.status == DownloadStatus.Merging) -> TaskAction.Pause
        item.status == DownloadStatus.Paused && item.userPaused -> TaskAction.Resume
        item.status == DownloadStatus.Failed -> TaskAction.Retry
        else -> null
    }
    val showProgress = when (item.status) {
        DownloadStatus.Pending,
        DownloadStatus.Running,
        DownloadStatus.Paused,
        DownloadStatus.Merging -> true
        else -> false
    }

    val coordinatesHolder = remember { TaskRowCoordinatesHolder() }
    Surface(
        color = AppSurfaces.cardContainerColor,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinatesHolder.coordinates = it }
                .clickable(interactionSource = interactionSource, indication = null) {
                    val bounds = coordinatesHolder.coordinates?.boundsInWindow() ?: return@clickable
                    haptics.tap()
                    onClick(bounds)
                }
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                ) {
                    Text(
                        text = item.title.ifBlank { item.fileName },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isMissing) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.alpha(if (isMissing) 0.6f else 1f),
                    )

                    if (item.status == DownloadStatus.Failed || item.status == DownloadStatus.Unavailable) {
                        TaskOutcomeMessage(
                            item = item,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    } else {
                        Text(
                            text = buildTaskDetailText(context, item, transferEstimate.speedBytesPerSec),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = TaskDetailLineHeight),
                            color = if (item.status == DownloadStatus.Cancelled || isMissing) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                if (actionType != null) {
                    IconButton(
                        onClick = {
                            haptics.tap()
                            when (actionType) {
                                TaskAction.Pause,
                                TaskAction.Resume -> onPauseResume()
                                TaskAction.Retry -> onRetry()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                when (actionType) {
                                    TaskAction.Pause -> R.drawable.ic_pause_24
                                    TaskAction.Resume -> R.drawable.ic_play_arrow_24
                                    TaskAction.Retry -> R.drawable.ic_retry_24
                                }
                            ),
                            contentDescription = stringResource(
                                when (actionType) {
                                    TaskAction.Pause -> R.string.download_pause
                                    TaskAction.Resume -> R.string.download_resume
                                    TaskAction.Retry -> R.string.download_retry
                                },
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (showProgress) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                ) {
                    DownloadsTaskProgressIndicator(
                        progress = DownloadProgressRules.normalizeTaskProgress(item.status, item.progress) / 100f,
                        indeterminate = item.status == DownloadStatus.Pending ||
                            item.status == DownloadStatus.Merging ||
                            (item.status == DownloadStatus.Running && item.progressIndeterminate),
                        transferring = item.status == DownloadStatus.Running,
                        modifier = Modifier.weight(1f),
                    )

                    buildTaskProgressText(context, item)?.let { progressText ->
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 失败与「无对应资源」的说明文案。与普通任务的辅助文案保持同一层级，
 * 只用图标与语义色区分，避免在任务段之内再叠一层色块容器。
 */
@Composable
private fun TaskOutcomeMessage(
    item: DownloadItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val unavailable = item.status == DownloadStatus.Unavailable
    val accentColor = if (unavailable) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.error
    }
    val label = if (unavailable) {
        stringResource(R.string.download_status_unavailable)
    } else {
        stringResource(R.string.download_status_failed)
    }
    val detail = if (unavailable) {
        stringResource(
            R.string.download_detail_unavailable,
            item.localizedStatusDetail(context)?.trim()?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.download_unavailable_generic),
        )
    } else {
        resolveFailureReason(item.localizedErrorMessage(context))
    }
    val paramsText = listOfNotNull(
        buildDownloadSizeText(context, item)?.keepSizeUnitsTogether(),
        buildTaskParamsText(context, item),
    ).joinToString("\n").takeIf(String::isNotBlank)
    val message = buildAnnotatedString {
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(label)
        pop()
        append(" · ")
        append(detail)
    }
    val messageLineHeight = TaskDetailLineHeight
    val inlineIconSize = 14.dp
    val firstLineHeight = with(LocalDensity.current) { messageLineHeight.toDp() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // 固定在首行行高的中央，避免文案换行后图标跟随整段文字居中。
            Box(
                modifier = Modifier
                    .width(inlineIconSize)
                    .height(firstLineHeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (unavailable) R.drawable.ic_info_24 else R.drawable.ic_error_24,
                    ),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(inlineIconSize),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = messageLineHeight),
                color = accentColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )
        }
        paramsText?.let { mediaParamsText ->
            Text(
                text = mediaParamsText,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = messageLineHeight),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun resolveFailureReason(rawMessage: String?): String {
    val message = rawMessage?.trim()?.takeIf { it.isNotBlank() }
        ?: return stringResource(R.string.download_reason_unknown)
    return when (message) {
        "Save failed" -> stringResource(R.string.download_failure_save)
        "Download failed" -> stringResource(R.string.download_failure_download_unknown)
        "Merge failed" -> stringResource(R.string.download_failure_merge)
        "Resume data missing" -> stringResource(R.string.download_failure_resume_data_missing)
        else -> message
    }
}

private enum class TaskAction {
    Pause,
    Resume,
    Retry,
}

/**
 * 任务的状态说明。进度百分比只在进度条末端出现一次；传输中由波浪进度条表达「正在下载」，
 * 这里先给已传输量再给速度，暂停、排队等非传输状态才写出状态词。
 */
private fun buildTaskDetailText(
    context: Context,
    item: DownloadItem,
    speedBytesPerSec: Long,
): String {
    val sizeText = buildDownloadSizeText(context, item)?.keepSizeUnitsTogether()
    val statusParts = when (item.status) {
        DownloadStatus.Running -> {
            val speedText = if (speedBytesPerSec > 0L) {
                context.getString(R.string.download_speed_format, context.formatDownloadBytes(speedBytesPerSec))
            } else {
                null
            }
            val statusDetail = item.localizedStatusDetail(context)?.takeIf { it.isNotBlank() }
            listOfNotNull(
                statusDetail ?: context.getString(R.string.downloads_section_downloading).takeIf { sizeText == null },
                sizeText,
                speedText,
            )
        }

        DownloadStatus.Pending -> listOfNotNull(context.getString(R.string.download_status_pending), sizeText)
        DownloadStatus.Paused -> listOfNotNull(context.getString(R.string.downloads_group_paused), sizeText)
        DownloadStatus.Merging -> listOf(
            item.localizedStatusDetail(context)?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.download_detail_merging),
        )
        DownloadStatus.Failed -> listOfNotNull(context.getString(R.string.download_status_failed), sizeText)
        DownloadStatus.Unavailable -> listOf(context.getString(R.string.download_status_unavailable))
        DownloadStatus.Success -> if (item.outputMissing) {
            listOfNotNull(context.getString(R.string.download_status_missing), sizeText)
        } else {
            listOfNotNull(
                context.getString(R.string.download_status_success),
                item.localizedEmbedWarning(context, excludedCodes = setOf(DownloadMessageCode.EmbedSubtitlesUnavailable)),
                sizeText,
            )
        }

        DownloadStatus.Cancelled -> listOfNotNull(context.getString(R.string.download_status_cancelled), sizeText)
    }
    return listOfNotNull(statusParts.joinToString(" · "), buildTaskParamsText(context, item)).joinToString("\n")
}

private fun buildTaskParamsText(context: Context, item: DownloadItem): String? =
    buildMediaParams(context, item.mediaParams?.localized(context), item.fileName, item.taskType)

/** 字节单位与数值一起换行，避免窄屏只把 MB 挤到下一行。 */
private fun String.keepSizeUnitsTogether(): String =
    replace(" B", " B").replace(" KB", " KB").replace(" MB", " MB")
        .replace(" GB", " GB").replace(" TB", " TB")

private fun buildTaskProgressText(
    context: Context,
    item: DownloadItem,
): String? {
    val progress = DownloadProgressRules.normalizeTaskProgress(item.status, item.progress)
    return when (item.status) {
        DownloadStatus.Running -> if (item.progressIndeterminate) {
            null
        } else {
            context.getString(R.string.download_progress_percent, progress)
        }

        DownloadStatus.Paused -> context.getString(R.string.download_progress_percent, progress)
        DownloadStatus.Merging -> progress
            .takeIf { it > 0 }
            ?.let { context.getString(R.string.download_progress_percent, it) }

        else -> null
    }
}

private fun buildMediaParams(
    context: Context,
    params: DownloadMediaParams?,
    fileName: String,
    taskType: DownloadTaskType,
): String? {
    if (!taskType.isManagedTransfer) {
        return null
    }
    if (params != null) {
        val parts = listOfNotNull(
            params.resolution?.takeIf { it.isNotBlank() },
            params.codec?.takeIf { it.isNotBlank() },
            params.audioBitrate?.takeIf { it.isNotBlank() },
        )
        if (parts.isNotEmpty()) {
            return parts.joinToString(" / ")
        }
    }
    val baseName = when {
        fileName.endsWith(".mp4", ignoreCase = true) -> fileName.dropLast(4)
        fileName.endsWith(".flv", ignoreCase = true) -> fileName.dropLast(4)
        fileName.endsWith(".m4a", ignoreCase = true) -> fileName.dropLast(4)
        else -> fileName
    }
    if (baseName.isBlank()) return null
    val parts = baseName.split("-").filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    return when (taskType) {
        DownloadTaskType.Audio -> parts.lastOrNull()
        DownloadTaskType.Video,
        DownloadTaskType.AudioVideo -> {
            val last = parts.lastOrNull() ?: return null
            val codecs = setOf(
                context.getString(R.string.parse_codec_avc),
                context.getString(R.string.parse_codec_hevc),
                context.getString(R.string.parse_codec_av1),
            )
            if (last in codecs) {
                val resolution = parts.dropLast(1).lastOrNull()
                if (resolution.isNullOrBlank()) last else "$resolution / $last"
            } else {
                last
            }
        }

        else -> null
    }
}
