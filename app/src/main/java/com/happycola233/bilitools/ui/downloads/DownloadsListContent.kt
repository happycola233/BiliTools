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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadProgressRules
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

private data class DownloadsGroupActionState(
    val hasActiveDownloads: Boolean,
    val hasUserPausedDownloads: Boolean,
)

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
    private var waveAmplitudeAnimator: ValueAnimator? = null
    private val defaultWaveAmplitude = indicator.getWaveAmplitude()

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

@Composable
private fun downloadsThemeColor(
    attrName: String,
    fallback: Color,
): Color {
    val view = LocalView.current
    val context = view.context
    return remember(view, context, attrName, fallback) {
        val attrId = context.resources.getIdentifier(attrName, "attr", context.packageName)
        if (attrId == 0) {
            fallback
        } else {
            Color(MaterialColors.getColor(view, attrId, fallback.toArgb()))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadsListContent(
    groups: List<DownloadGroup>,
    selectionMode: Boolean,
    selectedGroupIds: Set<Long>,
    expandedGroupIds: Set<Long>,
    collapsedSections: Set<DownloadSectionType>,
    swipedGroupId: Long?,
    listBottomPadding: PaddingValues,
    onToggleSection: (DownloadSectionType) -> Unit,
    onToggleGroupExpanded: (Long) -> Unit,
    onSwipedGroupChange: (Long?) -> Unit,
    onGroupSelectionToggle: (Long) -> Unit,
    onGroupDelete: (DownloadGroup) -> Unit,
    onGroupPause: (DownloadGroup) -> Unit,
    onGroupResume: (DownloadGroup) -> Unit,
    onTaskPauseResume: (DownloadItem) -> Unit,
    onTaskRetry: (DownloadItem) -> Unit,
    onTaskDelete: (DownloadItem) -> Unit,
    onTaskClick: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
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
    )

    LazyColumn(
        state = listState,
        contentPadding = listBottomPadding,
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(nestedScrollInterop),
    ) {
        sections.forEach { section ->
            val useVisibilitySectionAnimation = section.groups.size <= SECTION_VISIBILITY_ANIMATION_THRESHOLD
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

    // During a group height animation, placement animation chases every layout frame and lags.
    return if (hasVisibleGroupHeightChange || suppressPlacementAnimation) {
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
    val title = when (section.type) {
        DownloadSectionType.Downloading -> stringResource(R.string.downloads_section_downloading)
        DownloadSectionType.Downloaded -> stringResource(R.string.downloads_section_downloaded)
    }
    val iconRes = when (section.type) {
        DownloadSectionType.Downloading -> R.drawable.ic_downloading_24
        DownloadSectionType.Downloaded -> R.drawable.ic_download_done_24
    }
    val displayTitle = title.ifBlank {
        when (section.type) {
            DownloadSectionType.Downloading -> "正在下载"
            DownloadSectionType.Downloaded -> "已下载"
        }
    }
    val rotation by animateFloatAsState(
        targetValue = if (section.collapsed) -90f else 0f,
        animationSpec = tween(durationMillis = GROUP_ARROW_DURATION_MILLIS),
        label = "downloadsSectionArrow",
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
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
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }

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

@Composable
private fun DownloadsGroupCard(
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
    onTaskPauseResume: (DownloadItem) -> Unit,
    onTaskRetry: (DownloadItem) -> Unit,
    onTaskDelete: (DownloadItem) -> Unit,
    onTaskClick: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val errorColor = downloadsThemeColor("colorError", MaterialTheme.colorScheme.error)
    val onErrorColor = downloadsThemeColor("colorOnError", MaterialTheme.colorScheme.onError)
    val coverPlaceholderColor = downloadsThemeColor(
        "colorSurfaceVariant",
        MaterialTheme.colorScheme.surfaceVariant,
    )
    val scope = rememberCoroutineScope()
    val swipeTargetPx = with(density) { 88.dp.toPx() }
    val dismissThresholdPx = with(density) { 140.dp.toPx() }
    val offsetX = remember(group.id) { Animatable(0f) }
    var dragOffsetX by remember(group.id) { mutableFloatStateOf(0f) }
    var dragging by remember(group.id) { mutableStateOf(false) }
    val interactionSource = remember(group.id) { MutableInteractionSource() }
    val groupContainerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(durationMillis = 180),
        label = "downloadsGroupContainerColor",
    )
    val groupHeadlineColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 180),
        label = "downloadsGroupHeadlineColor",
    )
    val groupSupportingColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(durationMillis = 180),
        label = "downloadsGroupSupportingColor",
    )
    val groupAccentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 180),
        label = "downloadsGroupAccentColor",
    )
    val groupIconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "downloadsGroupIconColor",
    )
    val groupDividerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "downloadsGroupDividerColor",
    )

    val allMissing = remember(group.tasks) {
        group.tasks.isNotEmpty() && group.tasks.all {
            it.status == DownloadStatus.Success && it.outputMissing
        }
    }
    val isCompletedGroup = remember(group.tasks) {
        group.tasks.isNotEmpty() && group.tasks.all { it.status == DownloadStatus.Success }
    }
    val completedCount = remember(group.tasks) {
        group.tasks.count { it.status == DownloadStatus.Success }
    }
    val groupProgress = remember(group.tasks) { calculateGroupProgress(group.tasks) }
    val groupActionState = remember(group.tasks) { resolveDownloadsGroupActionState(group) }
    val showActionButton = !selectionMode &&
        (groupActionState.hasActiveDownloads || groupActionState.hasUserPausedDownloads)
    val coverModel = remember(group.coverUrl, context) {
        group.coverUrl?.trim()?.takeIf { it.isNotBlank() }?.let { coverUrl ->
            ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(true)
                .build()
        }
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded && !selectionMode) 180f else 0f,
        animationSpec = tween(durationMillis = GROUP_ARROW_DURATION_MILLIS),
        label = "downloadsGroupArrow",
    )

    LaunchedEffect(swiped, selectionMode, dragging, swipeTargetPx) {
        if (dragging) return@LaunchedEffect
        val target = when {
            selectionMode -> 0f
            swiped -> -swipeTargetPx
            else -> 0f
        }
        dragOffsetX = target
        if (offsetX.value != target) {
            offsetX.animateTo(target, tween(durationMillis = 200))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (selectionMode) 10.dp else 16.dp,
                end = if (selectionMode) 10.dp else 16.dp,
                bottom = 8.dp,
            )
            .pointerInput(group.id, selectionMode, swiped, anyGroupSwiped) {
                if (selectionMode) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (anyGroupSwiped && !swiped) {
                            onSwipedGroupChange(null)
                        }
                        dragOffsetX = offsetX.value
                        dragging = true
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val target = (dragOffsetX + dragAmount).coerceAtMost(0f)
                        dragOffsetX = target
                        scope.launch {
                            offsetX.snapTo(target)
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        val finalOffset = dragOffsetX
                        when {
                            finalOffset <= -dismissThresholdPx -> {
                                dragOffsetX = -swipeTargetPx
                                onSwipedGroupChange(group.id)
                                onDelete()
                            }

                            finalOffset <= -(swipeTargetPx / 2f) -> {
                                dragOffsetX = -swipeTargetPx
                                onSwipedGroupChange(group.id)
                            }

                            else -> {
                                dragOffsetX = 0f
                                onSwipedGroupChange(null)
                            }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffsetX = if (swiped) -swipeTargetPx else 0f
                        onSwipedGroupChange(if (swiped) group.id else null)
                    },
                )
            },
    ) {
        if (!selectionMode) {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 16.dp),
            ) {
                Surface(
                    color = errorColor,
                    shape = RoundedCornerShape(20.dp),
                    onClick = {
                        onDelete()
                    },
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .alpha(if (offsetX.value < 0f || swiped) 1f else 0f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_24),
                            contentDescription = stringResource(R.string.delete),
                            tint = onErrorColor,
                            modifier = Modifier.size(24.dp),
                        )
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
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        when {
                            selectionMode -> onToggleSelection()
                            anyGroupSwiped -> onSwipedGroupChange(null)
                            else -> onToggleExpanded()
                        }
                    },
                    onLongClick = {
                        if (!selectionMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleSelection()
                        }
                    },
                ),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        start = if (selectionMode) 12.dp else 16.dp,
                        end = if (selectionMode) 12.dp else 16.dp,
                        top = 16.dp,
                        bottom = 16.dp,
                    ),
                ) {
                    AnimatedVisibility(visible = selectionMode) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelection() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(
                                width = if (selectionMode) 72.dp else 80.dp,
                                height = if (selectionMode) 50.dp else 56.dp,
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(coverPlaceholderColor),
                    ) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = stringResource(R.string.downloads_group_cover_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                start = if (selectionMode) 12.dp else 16.dp,
                                end = if (selectionMode) 0.dp else 8.dp,
                            ),
                    ) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = groupHeadlineColor,
                            maxLines = if (selectionMode || isCompletedGroup) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (allMissing) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier.alpha(if (allMissing) 0.6f else 1f),
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            group.bvid?.takeIf { it.isNotBlank() }?.let { bvid ->
                                Text(
                                    text = bvid,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = groupAccentColor,
                                    maxLines = 1,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            }

                            Text(
                                text = buildCreatedAtText(context, group.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = groupSupportingColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (!isCompletedGroup) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                DownloadProgressIndicator(
                                    visualState = resolveDownloadsGroupProgressVisualState(groupActionState),
                                    progress = groupProgress,
                                    animateStateChange = true,
                                    animateProgress = true,
                                    modifier = Modifier.weight(1f),
                                )

                                Text(
                                    text = buildGroupProgressSummaryText(
                                        context = context,
                                        completed = completedCount,
                                        totalCount = group.tasks.size,
                                        progress = groupProgress,
                                        hasFailedTask = group.tasks.any { it.status == DownloadStatus.Failed },
                                        errorColor = errorColor,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = groupHeadlineColor,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = !selectionMode) {
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_more_24),
                            contentDescription = stringResource(R.string.downloads_group_toggle),
                            tint = groupIconColor,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = arrowRotation },
                        )
                    }
                }

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
                    Column(
                        modifier = Modifier.animateContentSize(
                            animationSpec = tween(durationMillis = GROUP_EXPAND_DURATION_MILLIS, easing = FastOutSlowInEasing),
                        ),
                    ) {
                        HorizontalDivider(
                            color = groupDividerColor,
                        )

                        if (showActionButton) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
                            ) {
                                Text(
                                    text = buildGroupActionsSummaryText(context, group),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = groupHeadlineColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )

                                FilledTonalButton(
                                    onClick = {
                                        if (groupActionState.hasActiveDownloads) {
                                            onPauseGroup()
                                        } else {
                                            onResumeGroup()
                                        }
                                    },
                                    modifier = Modifier.padding(start = 12.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (groupActionState.hasActiveDownloads) {
                                                R.drawable.ic_pause_24
                                            } else {
                                                R.drawable.ic_play_arrow_24
                                            }
                                        ),
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (groupActionState.hasActiveDownloads) {
                                            stringResource(R.string.download_pause)
                                        } else {
                                            stringResource(R.string.download_resume)
                                        },
                                    )
                                }
                            }
                        }

                        Column {
                            group.tasks.forEach { task ->
                                DownloadTaskRow(
                                    item = task,
                                    onPauseResume = { onTaskPauseResume(task) },
                                    onRetry = { onTaskRetry(task) },
                                    onDelete = { onTaskDelete(task) },
                                    onClick = { onTaskClick(task) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(
    item: DownloadItem,
    onPauseResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val errorColor = downloadsThemeColor("colorError", MaterialTheme.colorScheme.error)
    val managed = isManagedTask(item)
    val isMissing = item.status == DownloadStatus.Success && item.outputMissing
    val progress = DownloadProgressRules.normalizeTaskProgress(item.status, item.progress)
    val visualState = resolveDownloadsTaskProgressVisualState(item)
    val actionType = when {
        !managed -> null
        item.status == DownloadStatus.Running -> TaskAction.Pause
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
            .combinedClickable(
                enabled = !isMissing,
                interactionSource = remember(item.id) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
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

                Text(
                    text = buildTaskDetailText(context, item),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = when (item.status) {
                        DownloadStatus.Failed,
                        DownloadStatus.Cancelled -> errorColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .alpha(if (isMissing) 0.6f else 1f),
                )
            }

            if (actionType != null) {
                IconButton(
                    onClick = {
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
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24),
                    contentDescription = null,
                    tint = errorColor,
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

@Composable
private fun DownloadProgressIndicator(
    visualState: DownloadsProgressVisualState,
    progress: Int,
    animateStateChange: Boolean,
    animateProgress: Boolean,
    modifier: Modifier = Modifier,
) {
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

private const val SECTION_VISIBILITY_ANIMATION_THRESHOLD = 24

private fun buildDownloadsSections(
    groups: List<DownloadGroup>,
    collapsedSections: Set<DownloadSectionType>,
): List<DownloadsSectionUi> {
    if (groups.isEmpty()) return emptyList()
    val downloadingGroups = groups.filter { group ->
        group.tasks.any { it.status != DownloadStatus.Success }
    }
    val downloadedGroups = groups.filter { group ->
        group.tasks.isNotEmpty() && group.tasks.all { it.status == DownloadStatus.Success }
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
            groups = downloadedGroups,
            count = downloadedGroups.size,
            collapsed = collapsedSections.contains(DownloadSectionType.Downloaded),
        ),
    )
}

private fun buildSectionMetaTextLegacy(section: DownloadsSectionUi): String {
    val countText = "${section.count} 项"
    if (section.type != DownloadSectionType.Downloading) {
        return countText
    }
    val parts = buildList {
        add(countText)
        add("${formatBytes(section.speedBytesPerSec)}/s")
        section.etaSeconds?.let { add("剩余 ${formatEta(it)}") }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun buildSectionMetaLabelText(section: DownloadsSectionUi): String {
    val countText = stringResource(R.string.downloads_section_count, section.count)
    if (section.type != DownloadSectionType.Downloading) {
        return countText
    }
    val speedText = stringResource(
        R.string.download_speed_format,
        formatBytes(section.speedBytesPerSec),
    )
    val etaText = section.etaSeconds?.let { seconds ->
        stringResource(R.string.download_eta_format, formatEta(seconds))
    }
    return listOfNotNull(countText, speedText, etaText).joinToString(" · ")
}

private fun buildCreatedAtText(context: Context, createdAt: Long): String {
    if (createdAt <= 0L) {
        return context.getString(R.string.download_time_unknown)
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return "创建于：${formatter.format(Date(createdAt))}"
}

private fun buildGroupProgressSummaryText(
    context: Context,
    completed: Int,
    totalCount: Int,
    progress: Int,
    hasFailedTask: Boolean,
    errorColor: Color,
): AnnotatedString {
    val baseText = context.getString(
        R.string.downloads_group_progress_compact,
        completed,
        totalCount,
        progress,
    )
    if (!hasFailedTask) {
        return AnnotatedString(baseText)
    }
    return buildAnnotatedString {
        append(baseText)
        append(" · ")
        pushStyle(SpanStyle(color = errorColor))
        append(context.getString(R.string.downloads_group_progress_failed))
        pop()
    }
}

private fun buildGroupActionsSummaryText(
    context: Context,
    group: DownloadGroup,
): String {
    val taskCount = group.tasks.size
    val sizeTasks = group.tasks.filter { it.totalBytes > 0L }
    val downloadedBytes = sizeTasks.sumOf { item ->
        item.downloadedBytes.coerceAtMost(item.totalBytes)
    }
    val totalBytes = sizeTasks.sumOf { it.totalBytes }
    val sizeSummary = if (totalBytes > 0L) {
        context.getString(
            R.string.download_size_progress,
            formatBytes(downloadedBytes),
            formatBytes(totalBytes),
        )
    } else {
        val fallbackDownloaded = group.tasks.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
        context.getString(
            R.string.download_size_downloaded,
            formatBytes(fallbackDownloaded),
        )
    }
    val totalSpeedBytesPerSec = group.tasks.sumOf { item ->
        if (item.status == DownloadStatus.Running) item.speedBytesPerSec else 0L
    }
    return if (totalSpeedBytesPerSec > 0L) {
        context.getString(
            R.string.downloads_group_actions_summary_size_speed,
            taskCount,
            sizeSummary,
            context.getString(
                R.string.download_speed_format,
                formatBytes(totalSpeedBytesPerSec),
            ),
        )
    } else {
        context.getString(
            R.string.downloads_group_actions_summary_size,
            taskCount,
            sizeSummary,
        )
    }
}

private fun buildTaskDetailText(
    context: Context,
    item: DownloadItem,
): String {
    val progress = DownloadProgressRules.normalizeTaskProgress(item.status, item.progress)
    val downloaded = formatBytes(item.downloadedBytes)
    val sizeText = if (item.totalBytes > 0L) {
        context.getString(
            R.string.download_size_progress,
            downloaded,
            formatBytes(item.totalBytes),
        )
    } else if (item.downloadedBytes > 0L) {
        context.getString(R.string.download_size_downloaded, downloaded)
    } else {
        ""
    }
    val baseText = when (item.status) {
        DownloadStatus.Running -> {
            val statusDetail = item.statusDetail?.takeIf { it.isNotBlank() }
            val speedText = if (item.speedBytesPerSec > 0L) {
                context.getString(R.string.download_speed_format, formatBytes(item.speedBytesPerSec))
            } else {
                ""
            }
            when {
                statusDetail != null && sizeText.isNotBlank() && speedText.isNotBlank() ->
                    "$statusDetail - $sizeText - $speedText"
                statusDetail != null && sizeText.isNotBlank() -> "$statusDetail - $sizeText"
                statusDetail != null && speedText.isNotBlank() -> "$statusDetail - $speedText"
                statusDetail != null -> statusDetail
                sizeText.isNotBlank() && speedText.isNotBlank() -> "$sizeText - $speedText"
                sizeText.isNotBlank() -> sizeText
                speedText.isNotBlank() -> speedText
                else -> context.getString(R.string.download_status_running, progress)
            }
        }

        DownloadStatus.Pending -> context.getString(R.string.download_status_pending)
        DownloadStatus.Paused -> context.getString(R.string.download_status_paused, progress)
        DownloadStatus.Failed -> context.getString(R.string.download_status_failed)
        DownloadStatus.Merging -> context.getString(R.string.download_status_merging)
        DownloadStatus.Success -> if (item.outputMissing) {
            context.getString(R.string.download_status_missing)
        } else {
            context.getString(R.string.download_status_success)
        }

        DownloadStatus.Cancelled -> context.getString(R.string.download_status_cancelled)
    }
    val params = buildMediaParams(context, item.mediaParams, item.fileName, item.taskType)
    return if (params.isNullOrBlank()) {
        baseText
    } else {
        "$baseText\n参数：$params"
    }
}

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
        fileName.endsWith(".m4s", ignoreCase = true) -> fileName.dropLast(4)
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

private fun calculateDownloadingEtaSeconds(groups: List<DownloadGroup>): Long? {
    val activeTasks = groups.flatMap { it.tasks }
        .filter { task ->
            isManagedTask(task) && when (task.status) {
                DownloadStatus.Pending,
                DownloadStatus.Running,
                DownloadStatus.Paused,
                DownloadStatus.Merging -> true
                else -> false
            }
        }
    if (activeTasks.isEmpty()) return null

    val speedBytesPerSec = activeTasks.sumOf { task ->
        if (task.status == DownloadStatus.Running) task.speedBytesPerSec else 0L
    }
    if (speedBytesPerSec <= 0L) return null

    val sizeTasks = activeTasks.filter { it.totalBytes > 0L }
    if (sizeTasks.isEmpty()) return null

    val totalBytes = sizeTasks.sumOf { it.totalBytes }
    val downloadedBytes = sizeTasks.sumOf { task ->
        task.downloadedBytes.coerceAtMost(task.totalBytes)
    }
    return if (totalBytes > downloadedBytes) {
        (totalBytes - downloadedBytes) / speedBytesPerSec
    } else {
        null
    }
}

private fun calculateGroupProgress(tasks: List<DownloadItem>): Int {
    if (tasks.isEmpty()) return 0
    val allSucceeded = tasks.all { it.status == DownloadStatus.Success }
    if (allSucceeded) return 100
    val sizeTasks = tasks.filter { it.totalBytes > 0L }
    if (sizeTasks.isEmpty()) {
        val average = tasks.sumOf { item ->
            DownloadProgressRules.normalizeTaskProgress(item.status, item.progress)
        } / tasks.size
        return DownloadProgressRules.normalizeAggregateProgress(average, allSucceeded)
    }
    val total = sizeTasks.sumOf { it.totalBytes }
    if (total <= 0L) return 0
    val downloaded = sizeTasks.sumOf { item ->
        item.downloadedBytes.coerceAtMost(item.totalBytes)
    }
    val progress = ((downloaded * 100) / total).toInt()
    return DownloadProgressRules.normalizeAggregateProgress(progress, allSucceeded)
}

private fun resolveDownloadsGroupActionState(group: DownloadGroup): DownloadsGroupActionState {
    var hasActiveDownloads = false
    var hasUserPausedDownloads = false
    group.tasks.forEach { item ->
        if (!isManagedTask(item)) return@forEach
        when (item.status) {
            DownloadStatus.Pending,
            DownloadStatus.Running,
            DownloadStatus.Merging -> hasActiveDownloads = true
            DownloadStatus.Paused -> if (item.userPaused) {
                hasUserPausedDownloads = true
            }

            else -> Unit
        }
    }
    return DownloadsGroupActionState(
        hasActiveDownloads = hasActiveDownloads,
        hasUserPausedDownloads = hasUserPausedDownloads,
    )
}

private fun resolveDownloadsGroupProgressVisualState(
    actionState: DownloadsGroupActionState,
): DownloadsProgressVisualState {
    return if (actionState.hasActiveDownloads) {
        DownloadsProgressVisualState.WaveDeterminate
    } else {
        DownloadsProgressVisualState.FlatDeterminate
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
        DownloadStatus.Success,
        DownloadStatus.Cancelled -> DownloadsProgressVisualState.FlatDeterminate
    }
}

private fun isManagedTask(item: DownloadItem): Boolean = isManagedTask(item.taskType)

private fun isManagedTask(taskType: DownloadTaskType): Boolean {
    return when (taskType) {
        DownloadTaskType.Video,
        DownloadTaskType.Audio,
        DownloadTaskType.AudioVideo -> true
        else -> false
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

private fun formatEta(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remain = seconds % 60
    return when {
        hours > 0L -> String.format(Locale.getDefault(), "%d 小时 %02d 分", hours, minutes)
        minutes > 0L -> String.format(Locale.getDefault(), "%d 分 %02d 秒", minutes, remain)
        else -> String.format(Locale.getDefault(), "%d 秒", remain)
    }
}

private fun formatEtaLegacy(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remain = seconds % 60
    return when {
        hours > 0L -> String.format(Locale.getDefault(), "%d小时%02d分", hours, minutes)
        minutes > 0L -> String.format(Locale.getDefault(), "%d分%02d秒", minutes, remain)
        else -> String.format(Locale.getDefault(), "%d秒", remain)
    }
}
