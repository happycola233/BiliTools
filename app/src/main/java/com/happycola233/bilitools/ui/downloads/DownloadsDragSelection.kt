package com.happycola233.bilitools.ui.downloads

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import kotlin.math.sign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 手指进入可见区域上下边缘这段距离内时开始自动滚动。 */
private val AutoScrollEdge = 72.dp

/** 手指贴到可见区域边缘时每帧滚动的距离，越靠里越慢。 */
private val AutoScrollMaxStep = 16.dp

/**
 * 长按下载组后不松手上下拖动，快速选中一段连续的组；往回拖时退出范围的组恢复原状。
 *
 * 长按只在列表这一层识别：按下后在 Final 阶段观察，期间手指抬起、移动超过触摸阈值，
 * 或事件已被横滑删除、列表滚动消费，都让给它们；到达长按时长后改在 Initial 阶段消费后续事件，
 * 卡片自身的点击、横滑和列表滚动随之取消，松手时也不会再触发一次点击。
 *
 * [visibleTop] 与 [visibleBottom] 是列表被顶栏、底部浮层盖住的距离，决定自动滚动的边缘位置。
 */
@Composable
internal fun Modifier.dragToSelectGroups(
    listState: LazyListState,
    orderedGroupIds: List<Long>,
    visibleTop: Dp,
    visibleBottom: Dp,
    onStart: (anchorGroupId: Long) -> Unit,
    onRangeChange: (Set<Long>) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    val haptics = rememberAppHaptics()
    val currentOrder by rememberUpdatedState(orderedGroupIds)
    val currentTop by rememberUpdatedState(visibleTop)
    val currentBottom by rememberUpdatedState(visibleBottom)
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnRangeChange by rememberUpdatedState(onRangeChange)
    val currentOnEnd by rememberUpdatedState(onEnd)

    return pointerInput(listState) {
        var anchor: Long? = null
        var target: Long? = null
        // 拖动中的手指纵坐标，非拖动时为 NaN；自动滚动据此决定方向与速度。
        val pointerY = mutableFloatStateOf(Float.NaN)

        fun groupAt(y: Float): Long? {
            val info = listState.layoutInfo
            val hits = info.visibleItemsInfo.filter { item ->
                val top = item.offset - info.viewportStartOffset
                y >= top && y < top + item.size
            }
            // 吸顶的分区标题盖在卡片上方，手指落在标题上时不算选中其下的卡片。
            return if (hits.any { it.key !is Long }) null else hits.firstOrNull()?.key as? Long
        }

        fun updateRange(y: Float) {
            val start = anchor ?: return
            val next = groupAt(y) ?: return
            if (next == target) return
            val order = currentOrder
            val from = order.indexOf(start)
            val to = order.indexOf(next)
            if (from < 0 || to < 0) return
            target = next
            currentOnRangeChange(order.subList(minOf(from, to), maxOf(from, to) + 1).toSet())
            haptics.tick()
        }

        fun autoScrollStep(y: Float): Float {
            if (y.isNaN()) return 0f
            val edge = AutoScrollEdge.toPx()
            val top = currentTop.toPx() + edge
            val bottom = size.height - currentBottom.toPx() - edge
            return AutoScrollMaxStep.toPx() * when {
                y < top -> -((top - y) / edge).coerceAtMost(1f)
                y > bottom -> ((y - bottom) / edge).coerceAtMost(1f)
                else -> 0f
            }
        }

        coroutineScope {
            launch {
                // 只在手指停在边缘时逐帧滚动，离开边缘或松手即停。
                snapshotFlow { autoScrollStep(pointerY.floatValue).sign }.collectLatest { direction ->
                    if (direction == 0f) return@collectLatest
                    while (true) {
                        withFrameNanos { }
                        val y = pointerY.floatValue
                        listState.scrollBy(autoScrollStep(y))
                        updateRange(y)
                    }
                }
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val anchorId = groupAt(down.position.y) ?: return@awaitEachGesture
                if (!awaitLongPress(down)) return@awaitEachGesture
                anchor = anchorId
                target = anchorId
                haptics.longPress()
                currentOnStart(anchorId)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) break
                        pointerY.floatValue = change.position.y
                        updateRange(change.position.y)
                    }
                } finally {
                    pointerY.floatValue = Float.NaN
                    anchor = null
                    target = null
                    currentOnEnd()
                }
            }
        }
    }
}

/**
 * 等待长按；期间手指抬起、移动超过触摸阈值或移动已被横滑、滚动等手势消费时放弃。
 * 按下事件本身会被卡片的点击消费，不能据此判断取消。
 */
private suspend fun AwaitPointerEventScope.awaitLongPress(down: PointerInputChange): Boolean = try {
    withTimeout(viewConfiguration.longPressTimeoutMillis) {
        var cancelled = false
        while (!cancelled) {
            val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
            cancelled = change == null || !change.pressed ||
                (change.isConsumed && !change.changedToDownIgnoreConsumed()) ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
        }
        false
    }
} catch (_: PointerEventTimeoutCancellationException) {
    true
}
