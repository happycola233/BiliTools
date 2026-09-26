package com.happycola233.bilitools.ui.downloads

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.happycola233.bilitools.core.formatEstimatedTime
import com.happycola233.bilitools.core.localized
import com.happycola233.bilitools.core.localizedStatusDetail
import com.happycola233.bilitools.core.localizedErrorMessage
import com.happycola233.bilitools.core.localizedEmbedWarning
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadMessageCode
import com.happycola233.bilitools.data.model.DownloadProgressRules
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.isManagedTransfer
import com.happycola233.bilitools.data.model.isResolvedWithoutFailure
import com.happycola233.bilitools.ui.haptics.HapticThresholdGate
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.AppDestructiveColors
import com.happycola233.bilitools.ui.theme.AppSurfaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class DownloadsSectionUi(
    val type: DownloadSectionType,
    val groups: List<DownloadGroup>,
    val count: Int,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val collapsed: Boolean,
)

private enum class DownloadsProgressVisualState {
    WaveDeterminate,
    WaveIndeterminate,
    FlatDeterminate,
}

private const val GROUP_FADE_IN_DURATION_MILLIS = 180
private const val GROUP_FADE_OUT_DURATION_MILLIS = 140
private const val GROUP_EXPAND_DURATION_MILLIS = 220
private const val GROUP_ARROW_DURATION_MILLIS = 220
private const val GROUP_PLACEMENT_RESUME_DELAY_MILLIS = GROUP_EXPAND_DURATION_MILLIS + 32
private val downloadsGroupPlacementSpec =
    spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

private class DownloadsProgressIndicatorController(
    private val indicator: LinearProgressIndicator,
) {
    private var currentState: DownloadsProgressVisualState? = null
    private var currentIndicatorColor: Int? = null
    private var currentTrackColor: Int? = null
    private var waveAmplitudeAnimator: ValueAnimator? = null
    private val defaultWaveAmplitude = indicator.getWaveAmplitude()

    fun bindColors(indicatorColor: Int, trackColor: Int) {
        if (currentIndicatorColor != indicatorColor) {
            indicator.setIndicatorColor(indicatorColor)
            currentIndicatorColor = indicatorColor
        }
        if (currentTrackColor != trackColor) {
            indicator.setTrackColor(trackColor)
            currentTrackColor = trackColor
        }
    }

    fun bind(
        state: DownloadsProgressVisualState,
        progress: Int,
        animateStateChange: Boolean,
        animateProgress: Boolean,
    ) {
        val normalizedProgress = progress.coerceIn(0, 100)
        val previousState = currentState
        val sameState = previousState == state
        val useIndeterminate = state == DownloadsProgressVisualState.WaveIndeterminate

        if (indicator.isIndeterminate != useIndeterminate) {
            indicator.isIndeterminate = useIndeterminate
        }

        if (!useIndeterminate) {
            indicator.setProgressCompat(
                normalizedProgress,
                animateProgress && sameState,
            )
        }

        val targetAmplitude =
            if (state == DownloadsProgressVisualState.FlatDeterminate) 0 else defaultWaveAmplitude
        val shouldAnimateAmplitude =
            animateStateChange && previousState != null && previousState != state
        setWaveAmplitude(targetAmplitude, shouldAnimateAmplitude)
        currentState = state
    }

    private fun setWaveAmplitude(target: Int, animate: Boolean) {
        waveAmplitudeAnimator?.cancel()
        val start = indicator.getWaveAmplitude()
        if (!animate || start == target) {
            indicator.setWaveAmplitude(target)
            return
        }

        indicator.setWaveAmplitude(start)
        waveAmplitudeAnimator = ValueAnimator.ofInt(start, target).apply {
            duration = 220L
            addUpdateListener { animator ->
                indicator.setWaveAmplitude(animator.animatedValue as Int)
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        indicator.setWaveAmplitude(target)
                        waveAmplitudeAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        waveAmplitudeAnimator = null
                    }
                },
            )
            start()
        }
    }
}

private data class ProgressIndicatorHolder(
    val controller: DownloadsProgressIndicatorController,
)

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
    onGroupDelete: (DownloadGroup) -> Unit,
    onGroupPause: (DownloadGroup) -> Unit,
    onGroupResume: (DownloadGroup) -> Unit,
    onGroupReparse: (DownloadGroup) -> Unit,
    onGroupShowDetails: (DownloadGroup) -> Unit,
    onTaskPauseResume: (DownloadItem) -> Unit,
    onTaskRetry: (DownloadItem) -> Unit,
    onTaskDelete: (DownloadItem) -> Unit,
    onTaskClick: (DownloadItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
    selectionMotion: DownloadsSelectionMotion = rememberDownloadsSelectionMotion(selectionMode),
) {
    val listState = rememberLazyListState()
    val sections = remember(groups, collapsedSections) {
        buildDownloadsSections(groups, collapsedSections)
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

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = contentTopPadding,
            bottom = listBottomPadding,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        sections.forEach { section ->
            val useVisibilitySectionAnimation = section.groups.size <= MAX_GROUP_COUNT_FOR_FULL_SECTION_ANIMATION
            stickyHeader(key = "section-${section.type}") {
                DownloadsSectionHeader(
                    section = section,
                    enabled = !selectionMode,
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
                            fadeInSpec = tween(durationMillis = GROUP_FADE_IN_DURATION_MILLIS, easing = FastOutSlowInEasing),
                            placementSpec = groupPlacementSpec,
                            fadeOutSpec = tween(durationMillis = GROUP_FADE_OUT_DURATION_MILLIS, easing = FastOutSlowInEasing),
                        ),
                        enter =
                            fadeIn(animationSpec = tween(durationMillis = GROUP_FADE_IN_DURATION_MILLIS, easing = FastOutSlowInEasing)) +
                                expandVertically(
                                    animationSpec = tween(durationMillis = GROUP_EXPAND_DURATION_MILLIS, easing = FastOutSlowInEasing),
                                ),
                        exit =
                            fadeOut(animationSpec = tween(durationMillis = GROUP_FADE_OUT_DURATION_MILLIS, easing = FastOutSlowInEasing)) +
                                shrinkVertically(
                                    animationSpec = tween(durationMillis = GROUP_EXPAND_DURATION_MILLIS, easing = FastOutSlowInEasing),
                                ),
                    ) {
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
                            onDelete = { onGroupDelete(group) },
                            onPauseGroup = { onGroupPause(group) },
                            onResumeGroup = { onGroupResume(group) },
                            onReparse = { onGroupReparse(group) },
                            onShowDetails = { onGroupShowDetails(group) },
                            onTaskPauseResume = onTaskPauseResume,
                            onTaskRetry = onTaskRetry,
                            onTaskDelete = onTaskDelete,
                            onTaskClick = onTaskClick,
                        )
                    }
                }
            } else if (!section.collapsed) {
                items(
                    items = section.groups,
                    key = { group -> group.id },
                    contentType = { "download_group" },
                ) { group ->
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
                        onDelete = { onGroupDelete(group) },
                        onPauseGroup = { onGroupPause(group) },
                        onResumeGroup = { onGroupResume(group) },
                        onReparse = { onGroupReparse(group) },
                        onShowDetails = { onGroupShowDetails(group) },
                        onTaskPauseResume = onTaskPauseResume,
                        onTaskRetry = onTaskRetry,
                        onTaskDelete = onTaskDelete,
                        onTaskClick = onTaskClick,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = GROUP_FADE_IN_DURATION_MILLIS, easing = FastOutSlowInEasing),
                            placementSpec = groupPlacementSpec,
                            fadeOutSpec = tween(durationMillis = GROUP_FADE_OUT_DURATION_MILLIS, easing = FastOutSlowInEasing),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberDownloadsGroupPlacementSpec(
    visibleExpandedGroupIds: Set<Long>,
    currentGroupIds: Set<Long>,
    selectionTransitionRunning: Boolean,
): FiniteAnimationSpec<IntOffset>? {
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
        delay(GROUP_PLACEMENT_RESUME_DELAY_MILLIS.toLong())
        suppressPlacementAnimation = false
    }

    // 卡片尺寸和底部留白已随模式切换逐帧变化，列表不能再对这些位移追加一层追赶动画。
    return if (selectionTransitionRunning || hasVisibleGroupHeightChange || suppressPlacementAnimation) {
        null
    } else {
        downloadsGroupPlacementSpec
    }
}

@Composable
private fun DownloadsSectionHeader(
    section: DownloadsSectionUi,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val title = when (section.type) {
        DownloadSectionType.Downloading -> stringResource(R.string.downloads_section_downloading)
        DownloadSectionType.Downloaded -> stringResource(R.string.downloads_section_downloaded)
    }
    val collapsedRotation = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 90f else -90f
    val rotation by animateFloatAsState(
        targetValue = if (section.collapsed) collapsedRotation else 0f,
        animationSpec = tween(durationMillis = GROUP_ARROW_DURATION_MILLIS),
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
                .combinedClickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptics.tap()
                        onClick()
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Text(
                text = buildSectionMetaLabelText(section),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
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
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

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
    onDelete: () -> Unit,
    onPauseGroup: () -> Unit,
    onResumeGroup: () -> Unit,
    onReparse: () -> Unit,
    onShowDetails: () -> Unit,
    onTaskPauseResume: (DownloadItem) -> Unit,
    onTaskRetry: (DownloadItem) -> Unit,
    onTaskDelete: (DownloadItem) -> Unit,
    onTaskClick: (DownloadItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
    selectionMotion: DownloadsSelectionMotion = rememberDownloadsSelectionMotion(selectionMode),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val haptics = rememberAppHaptics()
    val coverPlaceholderColor = AppSurfaces.insetContainerColor
    val scope = rememberCoroutineScope()
    val deleteActionWidth = 80.dp
    val deleteActionGap = 8.dp
    val swipeRevealOffsetPx = with(density) { (deleteActionWidth + deleteActionGap).toPx() }
    val swipeDeleteThresholdPx = with(density) { 140.dp.toPx() }
    // 开盖是停靠后的预备动作，与删除阈值分开；8dp 的回退缓冲避免轻微抖动反复开合。
    val deleteIconOpenThresholdPx = swipeRevealOffsetPx + with(density) { 12.dp.toPx() }
    val deleteIconCloseThresholdPx = swipeRevealOffsetPx + with(density) { 4.dp.toPx() }
    val swipeOffsetX = remember(group.id) { Animatable(0f) }
    var dragOffsetX by remember(group.id) { mutableFloatStateOf(0f) }
    var dragging by remember(group.id) { mutableStateOf(false) }
    var deleteIconOpen by remember(group.id) { mutableStateOf(false) }
    val interactionSource = remember(group.id) { MutableInteractionSource() }
    val deleteThresholdGate = remember(group.id) { HapticThresholdGate() }
    val currentSwiped by rememberUpdatedState(swiped)
    val currentAnyGroupSwiped by rememberUpdatedState(anyGroupSwiped)
    val currentOnSwipedGroupChange by rememberUpdatedState(onSwipedGroupChange)
    val currentOnDelete by rememberUpdatedState(onDelete)
    // 停靠后向 start 延长背景，end 边缘固定；RTL 时整套操作区随布局镜像。
    val deleteContainerWidth = with(density) {
        (-swipeOffsetX.value).toDp() - deleteActionGap
    }.coerceAtLeast(deleteActionWidth)
    val groupContainerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            AppSurfaces.cardContainerColor
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "downloadsGroupContainerColor",
    )
    val groupHeadlineColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "downloadsGroupHeadlineColor",
    )
    val groupSupportingColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "downloadsGroupSupportingColor",
    )
    val groupDividerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "downloadsGroupDividerColor",
    )

    val allMissing = remember(group.tasks) {
        val savedTasks = group.tasks.filter { it.status == DownloadStatus.Success }
        savedTasks.isNotEmpty() &&
            savedTasks.all { it.outputMissing } &&
            group.tasks.all { it.status.isResolvedWithoutFailure }
    }
    val presentation = remember(group.tasks) { resolveDownloadsGroupPresentation(group) }

    LaunchedEffect(swiped, anyGroupSwiped) {
        // 另一个组先停靠时释放本组拖动；旧手指之后的移动和松开不能抢回停靠状态。
        if (dragging && anyGroupSwiped && !swiped) {
            dragging = false
            deleteIconOpen = false
        }
    }

    LaunchedEffect(swiped, selectionMode, dragging, swipeRevealOffsetPx) {
        if (dragging) return@LaunchedEffect
        // 删除确认期间回到停靠点仍保持开盖；弹窗关闭后，操作区收起时再统一复位。
        if (!swiped || selectionMode) deleteIconOpen = false
        val target = when {
            selectionMode -> 0f
            swiped -> -swipeRevealOffsetPx
            else -> 0f
        }
        dragOffsetX = target
        if (swipeOffsetX.value != target) {
            swipeOffsetX.animateTo(target, tween(durationMillis = 200))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = selectionMotion.cardInset,
                end = selectionMotion.cardInset,
                bottom = 8.dp,
            )
            .pointerInput(group.id, selectionMode, density.density, layoutDirection) {
                if (selectionMode) return@pointerInput
                // 共享停靠状态只更新回调读到的值，避免收起旧组时重启正在拖动的新组手势。
                try {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (currentAnyGroupSwiped && !currentSwiped) {
                                currentOnSwipedGroupChange(null)
                            }
                            dragOffsetX = swipeOffsetX.value
                            deleteThresholdGate.reset(dragOffsetX <= -swipeDeleteThresholdPx)
                            dragging = true
                        },
                        onHorizontalDrag = drag@{ change, dragAmount ->
                            change.consume()
                            if (!dragging) return@drag
                            // offset 使用逻辑方向，原始手势是物理方向；统一后阈值和停靠逻辑无需分叉。
                            val logicalDelta = if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                            val target = (dragOffsetX + logicalDelta).coerceIn(-size.width.toFloat(), 0f)
                            dragOffsetX = target
                            when {
                                -target >= deleteIconOpenThresholdPx -> deleteIconOpen = true
                                -target <= deleteIconCloseThresholdPx -> deleteIconOpen = false
                            }
                            scope.launch {
                                swipeOffsetX.snapTo(target)
                            }
                            deleteThresholdGate.update(target <= -swipeDeleteThresholdPx) { readyToDelete ->
                                if (readyToDelete) haptics.thresholdActivate() else haptics.thresholdDeactivate()
                            }
                        },
                        onDragEnd = end@{
                            if (!dragging) return@end
                            dragging = false
                            val finalOffset = dragOffsetX
                            deleteIconOpen = finalOffset <= -swipeDeleteThresholdPx
                            when {
                                finalOffset <= -swipeDeleteThresholdPx -> {
                                    dragOffsetX = -swipeRevealOffsetPx
                                    currentOnSwipedGroupChange(group.id)
                                    currentOnDelete()
                                }

                                finalOffset <= -(swipeRevealOffsetPx / 2f) -> {
                                    dragOffsetX = -swipeRevealOffsetPx
                                    currentOnSwipedGroupChange(group.id)
                                }

                                else -> {
                                    dragOffsetX = 0f
                                    currentOnSwipedGroupChange(null)
                                }
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            deleteIconOpen = false
                        },
                    )
                } finally {
                    // pointerInput 被取消（如进入多选）时不保证调用 onDragCancel，仍需释放回弹。
                    if (dragging) {
                        dragging = false
                        deleteIconOpen = false
                    }
                }
            },
    ) {
        if (!selectionMode) {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.matchParentSize(),
            ) {
                Surface(
                    color = AppDestructiveColors.container,
                    contentColor = AppDestructiveColors.onContainer,
                    shape = RoundedCornerShape(20.dp),
                    onClick = {
                        haptics.tap()
                        onDelete()
                    },
                    modifier = Modifier
                        .width(deleteContainerWidth)
                        .fillMaxHeight()
                        .alpha(if (swipeOffsetX.value < 0f || swiped) 1f else 0f),
                ) {
                    Box(contentAlignment = Alignment.CenterEnd) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.width(deleteActionWidth).fillMaxHeight(),
                        ) {
                            SwipeDeleteIcon(isOpen = deleteIconOpen, isDragging = dragging)
                        }
                    }
                }
            }
        }

        Surface(
            color = groupContainerColor,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
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
                    onLongClick = {
                        if (!selectionMode) {
                            haptics.longPress()
                            onToggleSelection()
                        }
                    },
                    hapticFeedbackEnabled = false,
                ),
        ) {
            Column {
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
                    coverPlaceholderColor = coverPlaceholderColor,
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

                AnimatedVisibility(
                    visible = expanded && !selectionMode,
                    enter =
                        fadeIn(animationSpec = tween(durationMillis = GROUP_FADE_IN_DURATION_MILLIS, easing = FastOutSlowInEasing)) +
                            expandVertically(
                                animationSpec = tween(durationMillis = GROUP_EXPAND_DURATION_MILLIS, easing = FastOutSlowInEasing),
                            ),
                    exit =
                        fadeOut(animationSpec = tween(durationMillis = GROUP_FADE_OUT_DURATION_MILLIS, easing = FastOutSlowInEasing)) +
                            shrinkVertically(
                                animationSpec = tween(durationMillis = GROUP_EXPAND_DURATION_MILLIS, easing = FastOutSlowInEasing),
                            ),
                ) {
                    // 展开区做成卡片内的内嵌面板：底色比卡片更沉、带独立圆角，
                    // 与「我」页、设置页的层级语言一致，避免用通宽分割线把卡片劈成两块
                    Surface(
                        color = AppSurfaces.insetContainerColor,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                            .animateContentSize(
                                animationSpec = tween(durationMillis = GROUP_EXPAND_DURATION_MILLIS, easing = FastOutSlowInEasing),
                            ),
                    ) {
                        Column {
                            DownloadsGroupActions(
                                reparseEnabled = group.sourceUrl() != null,
                                onReparse = onReparse,
                                onShowDetails = onShowDetails,
                                modifier = Modifier.padding(12.dp),
                            )
                            group.tasks.forEachIndexed { index, task ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = groupDividerColor,
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                    )
                                }
                                DownloadTaskRow(
                                    item = task,
                                    onPauseResume = { onTaskPauseResume(task) },
                                    onRetry = { onTaskRetry(task) },
                                    onDelete = { onTaskDelete(task) },
                                    onClick = { bounds -> onTaskClick(task, bounds) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 缓存任务行的布局坐标，供操作菜单锚定；用普通引用持有以免每次布局都触发重组。 */
private class TaskRowCoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

@Composable
private fun DownloadTaskRow(
    item: DownloadItem,
    onPauseResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onClick: (Rect) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val errorColor = MaterialTheme.colorScheme.error
    val managed = isManagedTask(item)
    val isMissing = item.status == DownloadStatus.Success && item.outputMissing
    val isUnavailable = item.status == DownloadStatus.Unavailable
    val progress = DownloadProgressRules.normalizeTaskProgress(item.status, item.progress)
    val visualState = resolveDownloadsTaskProgressVisualState(item)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
            .onGloballyPositioned { coordinatesHolder.coordinates = it }
            .clickable(enabled = !isMissing && !isUnavailable) {
                val bounds = coordinatesHolder.coordinates?.boundsInWindow() ?: return@clickable
                haptics.tap()
                onClick(bounds)
            }
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = item.title.ifBlank { item.fileName },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isMissing) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.alpha(if (isMissing) 0.6f else 1f),
                )

                if (item.status == DownloadStatus.Failed || isUnavailable) {
                    TaskOutcomeMessage(
                        item = item,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                } else {
                    Text(
                        text = buildTaskDetailText(context, item, transferEstimate.speedBytesPerSec),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = if (item.status == DownloadStatus.Cancelled) {
                            errorColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .alpha(if (isMissing) 0.6f else 1f),
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
                    modifier = Modifier.size(40.dp),
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

            IconButton(
                onClick = {
                    haptics.tap()
                    onDelete()
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_outline_rounded_24),
                    contentDescription = stringResource(R.string.download_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showProgress) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                DownloadProgressIndicator(
                    visualState = visualState,
                    progress = progress,
                    animateStateChange = true,
                    animateProgress = true,
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

/**
 * 失败与「无对应资源」的说明文案。与普通任务的辅助文案保持同一层级，
 * 只用图标与语义色区分，避免在卡片、内嵌面板之外再叠一层色块容器。
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
    val messageLineHeight = 18.sp
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

@Composable
private fun DownloadProgressIndicator(
    visualState: DownloadsProgressVisualState,
    progress: Int,
    animateStateChange: Boolean,
    animateProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    val indicatorColor = MaterialTheme.colorScheme.primary.toArgb()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        factory = { context ->
            LinearProgressIndicator(
                android.view.ContextThemeWrapper(
                    context,
                    R.style.Widget_BiliTools_DownloadProgress,
                ),
                null,
            ).apply {
                // Keep the old XML behavior even if style resolution changes.
                runCatching {
                    javaClass.getMethod("setTrackStopIndicatorSize", Int::class.javaPrimitiveType)
                        .invoke(this, 0)
                }
                tag = ProgressIndicatorHolder(
                    controller = DownloadsProgressIndicatorController(this),
                )
            }
        },
        update = { indicator ->
            val holder = indicator.tag as? ProgressIndicatorHolder ?: return@AndroidView
            // AndroidView 的 factory 不会因 AppSettings 配色变化而重建；颜色必须在 update
            // 中从当前 Compose ColorScheme 同步，否则主界面不 recreate 后会一直保留旧色。
            holder.controller.bindColors(
                indicatorColor = indicatorColor,
                trackColor = trackColor,
            )
            holder.controller.bind(
                state = visualState,
                progress = progress,
                animateStateChange = animateStateChange,
                animateProgress = animateProgress,
            )
        },
    )
}

private enum class TaskAction {
    Pause,
    Resume,
    Retry,
}

// 垂直伸缩会在动画期间持续触发布局计算，超大分区仍使用轻量列表项动画。
private const val MAX_GROUP_COUNT_FOR_FULL_SECTION_ANIMATION = 64

private fun buildDownloadsSections(
    groups: List<DownloadGroup>,
    collapsedSections: Set<DownloadSectionType>,
): List<DownloadsSectionUi> {
    if (groups.isEmpty()) return emptyList()
    val downloadingGroups = groups.filter { group ->
        group.tasks.any { !it.status.isResolvedWithoutFailure }
    }
    val completedGroups = groups.filter { group ->
        group.tasks.isNotEmpty() && group.tasks.all { it.status.isResolvedWithoutFailure }
    }
    val totalSpeed = downloadingGroups.sumOf { group ->
        group.tasks.sumOf { task ->
            if (task.status == DownloadStatus.Running) task.speedBytesPerSec else 0L
        }
    }
    return listOf(
        DownloadsSectionUi(
            type = DownloadSectionType.Downloading,
            groups = downloadingGroups,
            count = downloadingGroups.size,
            speedBytesPerSec = totalSpeed,
            etaSeconds = calculateDownloadingEtaSeconds(downloadingGroups),
            collapsed = collapsedSections.contains(DownloadSectionType.Downloading),
        ),
        DownloadsSectionUi(
            type = DownloadSectionType.Downloaded,
            groups = completedGroups,
            count = completedGroups.size,
            collapsed = collapsedSections.contains(DownloadSectionType.Downloaded),
        ),
    )
}

@Composable
private fun buildSectionMetaLabelText(section: DownloadsSectionUi): String {
    val context = LocalContext.current
    val countText = stringResource(R.string.downloads_section_count, section.count)
    if (section.type != DownloadSectionType.Downloading) {
        return countText
    }
    val transferEstimate = rememberDownloadTransferEstimate(
        tasks = section.groups.flatMap { it.tasks },
        speedBytesPerSec = section.speedBytesPerSec,
        etaSeconds = section.etaSeconds,
    )
    val speedText = stringResource(
        R.string.download_speed_format,
        context.formatDownloadBytes(transferEstimate.speedBytesPerSec),
    )
    val etaText = transferEstimate.etaSeconds?.let { seconds ->
        stringResource(R.string.download_eta_format, LocalContext.current.formatEstimatedTime(seconds))
    }
    return listOfNotNull(countText, speedText, etaText).joinToString(" · ")
}

private fun buildTaskDetailText(
    context: Context,
    item: DownloadItem,
    speedBytesPerSec: Long,
): String {
    val progress = DownloadProgressRules.normalizeTaskProgress(item.status, item.progress)
    val baseText = when (item.status) {
        DownloadStatus.Running -> {
            val statusDetail = item.localizedStatusDetail(context)?.takeIf { it.isNotBlank() }
            val speedText = if (speedBytesPerSec > 0L) {
                context.getString(R.string.download_speed_format, context.formatDownloadBytes(speedBytesPerSec))
            } else {
                ""
            }
            when {
                statusDetail != null && speedText.isNotBlank() -> "$statusDetail - $speedText"
                statusDetail != null -> statusDetail
                speedText.isNotBlank() -> "${context.getString(R.string.download_status_running, progress)} · $speedText"
                else -> context.getString(R.string.download_status_running, progress)
            }
        }

        DownloadStatus.Pending -> context.getString(R.string.download_status_pending)
        DownloadStatus.Paused -> context.getString(R.string.download_status_paused, progress)
        DownloadStatus.Failed -> context.getString(R.string.download_status_failed)
        DownloadStatus.Unavailable -> context.getString(R.string.download_status_unavailable)
        DownloadStatus.Merging -> item.localizedStatusDetail(context)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.download_detail_merging)
        DownloadStatus.Success -> if (item.outputMissing) {
            context.getString(R.string.download_status_missing)
        } else {
            listOfNotNull(
                context.getString(R.string.download_status_success),
                item.localizedEmbedWarning(context, excludedCodes = setOf(DownloadMessageCode.EmbedSubtitlesUnavailable)),
            ).joinToString(" · ")
        }

        DownloadStatus.Cancelled -> context.getString(R.string.download_status_cancelled)
    }
    val statusAndSize = listOfNotNull(baseText, buildDownloadSizeText(context, item)?.keepSizeUnitsTogether()).joinToString(" · ")
    return listOfNotNull(statusAndSize, buildTaskParamsText(context, item)).joinToString("\n")
}

private fun buildTaskParamsText(context: Context, item: DownloadItem): String? =
    buildMediaParams(context, item.mediaParams?.localized(context), item.fileName, item.taskType)

/** 字节单位与数值一起换行，避免窄屏只把 MB 挤到下一行。 */
private fun String.keepSizeUnitsTogether(): String =
    replace(" B", "\u00A0B").replace(" KB", "\u00A0KB").replace(" MB", "\u00A0MB")
        .replace(" GB", "\u00A0GB").replace(" TB", "\u00A0TB")

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
    if (!isManagedTask(taskType)) {
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

private fun resolveDownloadsTaskProgressVisualState(
    item: DownloadItem,
): DownloadsProgressVisualState {
    return when (item.status) {
        DownloadStatus.Pending,
        DownloadStatus.Merging -> DownloadsProgressVisualState.WaveIndeterminate
        DownloadStatus.Running -> if (item.progressIndeterminate) {
            DownloadsProgressVisualState.WaveIndeterminate
        } else {
            DownloadsProgressVisualState.WaveDeterminate
        }

        DownloadStatus.Paused,
        DownloadStatus.Failed,
        DownloadStatus.Unavailable,
        DownloadStatus.Success,
        DownloadStatus.Cancelled -> DownloadsProgressVisualState.FlatDeterminate
    }
}

private fun isManagedTask(item: DownloadItem): Boolean = isManagedTask(item.taskType)

private fun isManagedTask(taskType: DownloadTaskType): Boolean {
    return taskType.isManagedTransfer
}
