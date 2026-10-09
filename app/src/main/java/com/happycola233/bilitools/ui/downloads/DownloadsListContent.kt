package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.formatEstimatedTime
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.pressFeedback
import com.happycola233.bilitools.ui.theme.AppSurfaces
import kotlinx.coroutines.delay

private data class DownloadsSectionUi(
    val type: DownloadSectionType,
    val groups: List<DownloadGroup>,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val collapsed: Boolean,
)

/**
 * 展开或收起某组后，在该组高度弹簧大致落定之前不给列表项追加位移动画，
 * 否则下方条目会在尺寸动画之外再追赶一层位移。取值覆盖 expressive 默认空间弹簧的收敛时间。
 */
private const val GROUP_EXPAND_SETTLE_MILLIS = 400L

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadsListContent(
    groups: List<DownloadGroup>,
    selectionMode: Boolean,
    selectedGroupIds: Set<Long>,
    expandedGroupIds: Set<Long>,
    collapsedSections: Set<DownloadSectionType>,
    swipedGroupId: Long?,
    contentTopPadding: Dp,
    listBottomPadding: Dp,
    onToggleSection: (DownloadSectionType) -> Unit,
    onToggleGroupExpanded: (Long) -> Unit,
    onSwipedGroupChange: (Long?) -> Unit,
    onGroupSelectionToggle: (Long) -> Unit,
    onGroupDelete: (group: DownloadGroup, deleteFiles: Boolean) -> Unit,
    onGroupPause: (DownloadGroup) -> Unit,
    onGroupResume: (DownloadGroup) -> Unit,
    onGroupReparse: (DownloadGroup) -> Unit,
    onGroupShowDetails: (DownloadGroup) -> Unit,
    onTaskPauseResume: (DownloadItem) -> Unit,
    onTaskRetry: (DownloadItem) -> Unit,
    onTaskClick: (DownloadItem, Rect) -> Unit,
    onDragSelectionStart: (anchorGroupId: Long) -> Unit,
    onDragSelectionRange: (Set<Long>) -> Unit,
    onDragSelectionEnd: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMotion: DownloadsSelectionMotion = rememberDownloadsSelectionMotion(selectionMode),
) {
    val listState = rememberLazyListState()
    val motionScheme = MaterialTheme.motionScheme
    val sections = remember(groups, collapsedSections) {
        buildDownloadsSections(groups, collapsedSections)
    }
    // 拖动多选按屏幕上的排列顺序取范围，折叠分区里的组不参与。
    val visibleGroupIds = remember(sections) {
        sections.filterNot { it.collapsed }.flatMap { section -> section.groups.map { it.id } }
    }
    val visibleExpandedGroupIds = remember(selectionMode, expandedGroupIds) {
        if (selectionMode) emptySet() else expandedGroupIds.toSet()
    }
    val currentGroupIds = remember(groups) {
        groups.asSequence().map { it.id }.toSet()
    }
    val groupPlacementSpec = rememberDownloadsGroupPlacementSpec(
        visibleExpandedGroupIds = visibleExpandedGroupIds,
        currentGroupIds = currentGroupIds,
        selectionTransitionRunning = selectionMotion.isRunning,
    )

    @Composable
    fun GroupCard(group: DownloadGroup, modifier: Modifier = Modifier) {
        DownloadsGroupCard(
            group = group,
            selectionMode = selectionMode,
            selectionMotion = selectionMotion,
            selected = selectedGroupIds.contains(group.id),
            expanded = expandedGroupIds.contains(group.id),
            swiped = swipedGroupId == group.id,
            anyGroupSwiped = swipedGroupId != null,
            onSwipedGroupChange = onSwipedGroupChange,
            onToggleSelection = { onGroupSelectionToggle(group.id) },
            onToggleExpanded = { onToggleGroupExpanded(group.id) },
            onDelete = { deleteFiles -> onGroupDelete(group, deleteFiles) },
            onPauseGroup = { onGroupPause(group) },
            onResumeGroup = { onGroupResume(group) },
            onReparse = { onGroupReparse(group) },
            onShowDetails = { onGroupShowDetails(group) },
            onTaskPauseResume = onTaskPauseResume,
            onTaskRetry = onTaskRetry,
            onTaskClick = onTaskClick,
            modifier = modifier,
        )
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = contentTopPadding,
            bottom = listBottomPadding,
        ),
        modifier = modifier
            .fillMaxWidth()
            .dragToSelectGroups(
                listState = listState,
                orderedGroupIds = visibleGroupIds,
                visibleTop = contentTopPadding,
                visibleBottom = listBottomPadding,
                onStart = onDragSelectionStart,
                onRangeChange = onDragSelectionRange,
                onEnd = onDragSelectionEnd,
            ),
    ) {
        sections.forEach { section ->
            val useVisibilitySectionAnimation = section.groups.size <= MAX_GROUP_COUNT_FOR_FULL_SECTION_ANIMATION
            stickyHeader(key = "section-${section.type}") {
                DownloadsSectionHeader(
                    section = section,
                    onClick = { onToggleSection(section.type) },
                )
            }
            if (useVisibilitySectionAnimation) {
                items(
                    items = section.groups,
                    key = { group -> group.id },
                    contentType = { "download_group" },
                ) { group ->
                    AnimatedVisibility(
                        visible = !section.collapsed,
                        modifier = Modifier.animateItem(
                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                            placementSpec = groupPlacementSpec,
                            fadeOutSpec = motionScheme.fastEffectsSpec(),
                        ),
                        enter = fadeIn(motionScheme.defaultEffectsSpec()) + expandVertically(motionScheme.defaultSpatialSpec()),
                        exit = fadeOut(motionScheme.fastEffectsSpec()) + shrinkVertically(motionScheme.defaultSpatialSpec()),
                    ) {
                        GroupCard(group)
                    }
                }
            } else if (!section.collapsed) {
                items(
                    items = section.groups,
                    key = { group -> group.id },
                    contentType = { "download_group" },
                ) { group ->
                    GroupCard(
                        group = group,
                        modifier = Modifier.animateItem(
                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                            placementSpec = groupPlacementSpec,
                            fadeOutSpec = motionScheme.fastEffectsSpec(),
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberDownloadsGroupPlacementSpec(
    visibleExpandedGroupIds: Set<Long>,
    currentGroupIds: Set<Long>,
    selectionTransitionRunning: Boolean,
): FiniteAnimationSpec<IntOffset>? {
    val placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    var suppressPlacementAnimation by remember { mutableStateOf(false) }
    val previousVisibleExpandedGroupIds = remember { arrayOf(visibleExpandedGroupIds.toSet()) }
    val visibleExpandedGroupsChanged = previousVisibleExpandedGroupIds[0] != visibleExpandedGroupIds
    val collapsedVisibleGroup = previousVisibleExpandedGroupIds[0].any { id ->
        id !in visibleExpandedGroupIds && id in currentGroupIds
    }
    val expandedVisibleGroup = visibleExpandedGroupIds.any { id ->
        id !in previousVisibleExpandedGroupIds[0] && id in currentGroupIds
    }
    val hasVisibleGroupHeightChange =
        visibleExpandedGroupsChanged && (collapsedVisibleGroup || expandedVisibleGroup)

    SideEffect {
        previousVisibleExpandedGroupIds[0] = visibleExpandedGroupIds.toSet()
    }

    LaunchedEffect(visibleExpandedGroupIds) {
        if (!hasVisibleGroupHeightChange) return@LaunchedEffect
        suppressPlacementAnimation = true
        delay(GROUP_EXPAND_SETTLE_MILLIS)
        suppressPlacementAnimation = false
    }

    // 卡片尺寸和底部留白已随模式切换逐帧变化，列表不能再对这些位移追加一层追赶动画。
    return if (selectionTransitionRunning || hasVisibleGroupHeightChange || suppressPlacementAnimation) {
        null
    } else {
        placementSpec
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsSectionHeader(
    section: DownloadsSectionUi,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val title = when (section.type) {
        DownloadSectionType.Downloading -> stringResource(R.string.downloads_section_downloading)
        DownloadSectionType.Downloaded -> stringResource(R.string.downloads_section_downloaded)
    }
    val collapsedRotation = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 90f else -90f
    val rotation by animateFloatAsState(
        targetValue = if (section.collapsed) collapsedRotation else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "downloadsSectionArrow",
    )

    Surface(
        color = AppSurfaces.pageContainerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .pressFeedback(interactionSource, MaterialTheme.shapes.large)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptics.tap()
                        onClick()
                    },
                )
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            // 与「我」页分区标题同一字重与配色。
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )

            Text(
                text = buildSectionMetaLabelText(section),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )

            Icon(
                painter = painterResource(R.drawable.ic_expand_more_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

// 垂直伸缩会在动画期间持续触发布局计算，超大分区仍使用轻量列表项动画。
private const val MAX_GROUP_COUNT_FOR_FULL_SECTION_ANIMATION = 64

/** 空分区不显示标题，避免「已完成 0 项」这类没有内容的占位。 */
private fun buildDownloadsSections(
    groups: List<DownloadGroup>,
    collapsedSections: Set<DownloadSectionType>,
): List<DownloadsSectionUi> {
    val (completedGroups, downloadingGroups) = groups.partition { it.isCompleted }
    val totalSpeed = downloadingGroups.sumOf { group ->
        group.tasks.sumOf { task ->
            if (task.status == DownloadStatus.Running) task.speedBytesPerSec else 0L
        }
    }
    return listOfNotNull(
        DownloadsSectionUi(
            type = DownloadSectionType.Downloading,
            groups = downloadingGroups,
            speedBytesPerSec = totalSpeed,
            etaSeconds = calculateDownloadingEtaSeconds(downloadingGroups),
            collapsed = collapsedSections.contains(DownloadSectionType.Downloading),
        ).takeIf { downloadingGroups.isNotEmpty() },
        DownloadsSectionUi(
            type = DownloadSectionType.Downloaded,
            groups = completedGroups,
            collapsed = collapsedSections.contains(DownloadSectionType.Downloaded),
        ).takeIf { completedGroups.isNotEmpty() },
    )
}

/**
 * 分区标题右侧的摘要。速度与剩余时间在每张卡片底部已有；只有一组时不再重复，
 * 多组同时下载（汇总才有意义）或分区折叠（卡片不可见）时才在标题上给出汇总。
 */
@Composable
private fun buildSectionMetaLabelText(section: DownloadsSectionUi): String {
    val context = LocalContext.current
    val countText = stringResource(R.string.downloads_section_count, section.groups.size)
    val transferEstimate = rememberDownloadTransferEstimate(
        tasks = section.groups.flatMap { it.tasks },
        speedBytesPerSec = section.speedBytesPerSec,
        etaSeconds = section.etaSeconds,
    )
    if (section.type != DownloadSectionType.Downloading ||
        (section.groups.size < 2 && !section.collapsed) ||
        transferEstimate.speedBytesPerSec <= 0L
    ) {
        return countText
    }
    val speedText = stringResource(
        R.string.download_speed_format,
        context.formatDownloadBytes(transferEstimate.speedBytesPerSec),
    )
    val etaText = transferEstimate.etaSeconds?.let { seconds ->
        stringResource(R.string.download_eta_format, context.formatEstimatedTime(seconds))
    }
    return listOfNotNull(countText, speedText, etaText).joinToString(" · ")
}
