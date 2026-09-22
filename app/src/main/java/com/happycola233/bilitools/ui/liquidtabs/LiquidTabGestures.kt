package com.happycola233.bilitools.ui.liquidtabs

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/** 底板上只有这一个触摸入口；阈值只区分点击与拖动，不延迟按压反馈或吞掉前几像素。 */
internal suspend fun PointerInputScope.detectLiquidTabGestures(
    onPress: (Offset) -> Unit,
    onDrag: (deltaX: Float) -> Unit,
    onRelease: (dragged: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        var pointerId = down.id
        var horizontalTravel = 0f
        var dragged = false
        var released = false
        onPress(down.position)
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (change.isConsumed) break

                val deltaX = change.positionChange().x
                horizontalTravel += deltaX
                dragged = dragged || abs(horizontalTravel) > viewConfiguration.touchSlop
                if (deltaX != 0f) onDrag(deltaX)
                change.consume()

                if (!change.pressed) {
                    val otherDown = event.changes.firstOrNull { it.pressed }
                    if (otherDown != null) {
                        // 接续另一根手指的后续增量，不把两根手指的位置差当成拖动。
                        pointerId = otherDown.id
                    } else {
                        val inside = change.position.x in 0f..size.width.toFloat() &&
                            change.position.y in 0f..size.height.toFloat()
                        if (dragged || inside) {
                            onRelease(dragged)
                            released = true
                        }
                        break
                    }
                }
            }
        } finally {
            // 包括系统取消和 pointerInput 因尺寸/方向变化而重启；取消不能提交预览页。
            if (!released) onCancel()
        }
    }
}

internal fun liquidTabIndexAt(
    x: Float,
    tabWidth: Float,
    horizontalPadding: Float,
    tabsCount: Int,
    isLtr: Boolean,
): Int {
    val physicalIndex = ((x - horizontalPadding) / tabWidth).toInt().coerceIn(0, tabsCount - 1)
    return if (isLtr) physicalIndex else tabsCount - 1 - physicalIndex
}
