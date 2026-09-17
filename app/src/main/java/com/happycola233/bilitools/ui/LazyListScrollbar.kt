package com.happycola233.bilitools.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.ui.haptics.HapticTicker
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import kotlinx.coroutines.launch

private val lazyListScrollbarTouchWidth = 18.dp
private val lazyListScrollbarVerticalInset = 6.dp
private val lazyListScrollbarTrackWidth = 2.dp
private val lazyListScrollbarTrackWidthActive = 6.dp
private val lazyListScrollbarThumbWidth = 4.dp
private val lazyListScrollbarThumbWidthActive = 8.dp
private val lazyListScrollbarMinThumbHeight = 48.dp

/** 跟随已加载内容更新位置；长按右侧滚动条可拖动列表。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LazyListScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hapticFeedback = rememberAppHaptics()
    val dragTicker = remember { HapticTicker() }
    val coroutineScope = rememberCoroutineScope()
    val minThumbHeightPx = with(density) { lazyListScrollbarMinThumbHeight.toPx() }
    var trackHeightPx by remember { mutableStateOf(0) }
    var dragActive by remember { mutableStateOf(false) }
    val metrics = calculateLazyListScrollbarMetrics(
        listState = listState,
        trackHeightPx = trackHeightPx,
        minThumbHeightPx = minThumbHeightPx,
    )

    val trackWidth by animateDpAsState(
        targetValue = if (dragActive) {
            lazyListScrollbarTrackWidthActive
        } else {
            lazyListScrollbarTrackWidth
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (dragActive) {
            lazyListScrollbarThumbWidthActive
        } else {
            lazyListScrollbarThumbWidth
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbTopTarget = with(density) { metrics.thumbOffsetPx.toDp() }
    val thumbTopAnimated by animateDpAsState(
        targetValue = thumbTopTarget,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbTop = if (dragActive) thumbTopTarget else thumbTopAnimated
    val thumbHeight = with(density) { metrics.thumbHeightPx.toDp() }
    val trackColor by animateColorAsState(
        targetValue = if (dragActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val thumbColor by animateColorAsState(
        targetValue = if (dragActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.84f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val thumbScaleX by animateFloatAsState(
        targetValue = if (dragActive) 1f else 0.82f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbScaleY by animateFloatAsState(
        targetValue = if (dragActive) 1f else 0.96f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(lazyListScrollbarTouchWidth)
            .pointerInput(listState, trackHeightPx, minThumbHeightPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        val currentMetrics = calculateLazyListScrollbarMetrics(
                            listState = listState,
                            trackHeightPx = trackHeightPx,
                            minThumbHeightPx = minThumbHeightPx,
                        )
                        if (currentMetrics.scrollable) {
                            dragActive = true
                            hapticFeedback.longPress()
                        }
                    },
                    onDragEnd = {
                        dragActive = false
                        dragTicker.reset()
                    },
                    onDragCancel = {
                        dragActive = false
                        dragTicker.reset()
                    },
                    onDrag = { change, dragAmount ->
                        val currentMetrics = calculateLazyListScrollbarMetrics(
                            listState = listState,
                            trackHeightPx = trackHeightPx,
                            minThumbHeightPx = minThumbHeightPx,
                        )
                        if (currentMetrics.scrollable && currentMetrics.maxThumbOffsetPx > 0f) {
                            change.consume()
                            val scrollDelta = dragAmount.y *
                                (currentMetrics.maxScrollPx / currentMetrics.maxThumbOffsetPx)
                            coroutineScope.launch {
                                listState.scrollBy(scrollDelta)
                            }
                            // 滚动是在协程里异步执行的，这里读到的是上一帧的位置，
                            // 作为「跨过一个条目」的节拍已经足够精确。
                            dragTicker.onStep(listState.firstVisibleItemIndex) {
                                hapticFeedback.tick()
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = lazyListScrollbarVerticalInset)
                .onSizeChanged { size -> trackHeightPx = size.height },
            contentAlignment = Alignment.Center,
        ) {
            if (metrics.scrollable || dragActive) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(trackWidth),
                    shape = RoundedCornerShape(percent = 50),
                    color = trackColor,
                ) {}
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = thumbTop)
                        .testTag("lazy_list_scrollbar_thumb")
                        .width(thumbWidth)
                        .height(thumbHeight)
                        .graphicsLayer {
                            scaleX = thumbScaleX
                            scaleY = thumbScaleY
                        },
                    shape = RoundedCornerShape(percent = 50),
                    color = thumbColor,
                ) {}
            }
        }
    }
}

private data class LazyListScrollbarMetrics(
    val scrollable: Boolean,
    val thumbHeightPx: Float,
    val thumbOffsetPx: Float,
    val maxThumbOffsetPx: Float,
    val maxScrollPx: Float,
) {
    companion object
}

private fun calculateLazyListScrollbarMetrics(
    listState: LazyListState,
    trackHeightPx: Int,
    minThumbHeightPx: Float,
): LazyListScrollbarMetrics {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewportHeightPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .coerceAtLeast(0)

    if (trackHeightPx <= 0 || viewportHeightPx <= 0 || visibleItems.isEmpty() ||
        (!listState.canScrollForward && !listState.canScrollBackward)
    ) {
        return LazyListScrollbarMetrics.empty()
    }

    val totalItems = layoutInfo.totalItemsCount
    val averageItemSizePx = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
    val averageItemStepPx = estimateLazyListAverageItemStep(visibleItems)
        ?: averageItemSizePx
    val averageSpacingPx = (averageItemStepPx - averageItemSizePx).coerceAtLeast(0f)
    val contentHeightPx = averageItemSizePx * totalItems +
        averageSpacingPx * (totalItems - 1).coerceAtLeast(0) +
        layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
    val maxScrollPx = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)

    if (totalItems <= 0 || maxScrollPx <= 0f) {
        return LazyListScrollbarMetrics.empty(trackHeightPx.toFloat())
    }

    val scrollOffsetPx = listState.firstVisibleItemIndex * averageItemStepPx + listState.firstVisibleItemScrollOffset
    // 日期分组、页脚等会使行高不同，中途使用估算值；首尾以列表真实边界为准。
    val scrollFraction = when {
        !listState.canScrollBackward -> 0f
        !listState.canScrollForward -> 1f
        else -> (scrollOffsetPx / maxScrollPx).coerceIn(0f, 1f)
    }
    val thumbHeightPx = ((viewportHeightPx / contentHeightPx) * trackHeightPx)
        .coerceIn(minThumbHeightPx.coerceAtMost(trackHeightPx.toFloat()), trackHeightPx.toFloat())
    val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    return LazyListScrollbarMetrics(
        scrollable = maxThumbOffsetPx > 0f,
        thumbHeightPx = thumbHeightPx,
        thumbOffsetPx = scrollFraction * maxThumbOffsetPx,
        maxThumbOffsetPx = maxThumbOffsetPx,
        maxScrollPx = maxScrollPx,
    )
}

private fun estimateLazyListAverageItemStep(
    visibleItems: List<androidx.compose.foundation.lazy.LazyListItemInfo>,
): Float? {
    if (visibleItems.size < 2) {
        return null
    }

    val steps = visibleItems.zipWithNext().mapNotNull { (current, next) ->
        val indexDelta = next.index - current.index
        if (indexDelta > 0) {
            (next.offset - current.offset).toFloat() / indexDelta
        } else {
            null
        }
    }

    return steps.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

private fun LazyListScrollbarMetrics.Companion.empty(
    trackHeightPx: Float = 0f,
): LazyListScrollbarMetrics {
    return LazyListScrollbarMetrics(
        scrollable = false,
        thumbHeightPx = trackHeightPx,
        thumbOffsetPx = 0f,
        maxThumbOffsetPx = 0f,
        maxScrollPx = 0f,
    )
}

