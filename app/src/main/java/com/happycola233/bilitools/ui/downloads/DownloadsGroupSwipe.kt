package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.SegmentedListShapes
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class DownloadsSwipeStage { RevealRecords, ClearRecords, DeleteFiles }

/**
 * 操作底层的可见区域：图层范围减去滑动后卡片整体的圆角轮廓。
 * 卡片外圆角让出的角落与单张卡片时一样能看到底下的按钮；分段之间的间隙落在轮廓内，
 * 不会透出来，也点不到（图层裁剪同时作用于命中测试）。
 */
private class SwipeRevealShape(
    private val shift: Float,
    private val cardCorner: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (shift <= 0f) return Outline.Rectangle(Rect.Zero)
        // 位移按逻辑方向保存，RTL 下卡片向右滑开，操作区在左侧。
        val cardLeft = if (layoutDirection == LayoutDirection.Rtl) shift else -shift
        val layer = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val card = Path().apply {
            addRoundRect(
                RoundRect(
                    left = cardLeft,
                    top = 0f,
                    right = cardLeft + size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(cardCorner),
                ),
            )
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, layer, card))
    }
}

private val SwipeActionRestingWidth = 80.dp
private val SwipeActionGap = 8.dp

/** 动画、触感与松手动作共用同一档位，不能各自设置提前量。 */
private fun swipeStage(distance: Float, clearThreshold: Float, deleteThreshold: Float) = when {
    distance >= deleteThreshold -> DownloadsSwipeStage.DeleteFiles
    distance >= clearThreshold -> DownloadsSwipeStage.ClearRecords
    else -> DownloadsSwipeStage.RevealRecords
}

@Composable
internal fun DownloadsGroupSwipe(
    groupId: Long,
    enabled: Boolean,
    swiped: Boolean,
    anyGroupSwiped: Boolean,
    onSwipedGroupChange: (Long?) -> Unit,
    onDelete: (deleteFiles: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val revealOffset = with(density) { (SwipeActionRestingWidth + SwipeActionGap).toPx() }
    val clearThreshold = with(density) { 140.dp.toPx() }
    val deleteThreshold = with(density) { 220.dp.toPx() }
    val offset = remember(groupId) { Animatable(0f) }
    var dragOffset by remember(groupId) { mutableFloatStateOf(0f) }
    var dragging by remember(groupId) { mutableStateOf(false) }
    var stage by remember(groupId) { mutableStateOf(DownloadsSwipeStage.RevealRecords) }
    val currentSwiped by rememberUpdatedState(swiped)
    val currentAnyGroupSwiped by rememberUpdatedState(anyGroupSwiped)
    val currentOnSwipedGroupChange by rememberUpdatedState(onSwipedGroupChange)
    val currentOnDelete by rememberUpdatedState(onDelete)

    LaunchedEffect(swiped, anyGroupSwiped) {
        // 另一根手指先停靠时，旧手势不能重新抢回停靠状态或触发删除。
        if (dragging && anyGroupSwiped && !swiped) {
            dragging = false
            stage = DownloadsSwipeStage.RevealRecords
        }
    }
    LaunchedEffect(swiped, enabled, dragging, revealOffset) {
        if (dragging) return@LaunchedEffect
        // 等待确认时保留松手档位；关闭弹窗、进入多选和取消手势都会复位。
        if (!swiped || !enabled) stage = DownloadsSwipeStage.RevealRecords
        val target = if (swiped && enabled) -revealOffset else 0f
        dragOffset = target
        if (offset.value != target) offset.animateTo(target, tween(200))
    }

    Box(
        modifier.pointerInput(groupId, enabled, density.density, layoutDirection) {
            if (!enabled) return@pointerInput
            try {
                // 仅按横向阈值启动，但启动后同时消费纵向移动；否则停留时的纵向抖动
                // 会被列表重新接管，带动列表滚动，并在顶部展开折叠栏。
                detectDragGestures(
                    orientationLock = Orientation.Horizontal,
                    onDragStart = { _, _, _ ->
                        if (currentAnyGroupSwiped && !currentSwiped) currentOnSwipedGroupChange(null)
                        dragOffset = offset.value
                        stage = swipeStage(-dragOffset, clearThreshold, deleteThreshold)
                        dragging = true
                    },
                    onDrag = drag@{ change, dragAmount ->
                        change.consume()
                        if (!dragging) return@drag
                        // 位移始终存为逻辑方向；offset 与操作区的 end 对齐一起负责 RTL 镜像。
                        val horizontalDelta = if (layoutDirection == LayoutDirection.Rtl) -dragAmount.x else dragAmount.x
                        dragOffset = (dragOffset + horizontalDelta).coerceIn(-size.width.toFloat(), 0f)
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(dragOffset) }
                        val nextStage = swipeStage(-dragOffset, clearThreshold, deleteThreshold)
                        if (nextStage != stage) {
                            // 一帧跨过两个档位只反馈最终档位，避免快速滑动叠加两次震动。
                            if (nextStage.ordinal > stage.ordinal) haptics.thresholdActivate()
                            else haptics.thresholdDeactivate()
                            stage = nextStage
                        }
                    },
                    onDragEnd = end@{
                        if (!dragging) return@end
                        dragging = false
                        when {
                            stage != DownloadsSwipeStage.RevealRecords -> {
                                currentOnSwipedGroupChange(groupId)
                                currentOnDelete(stage == DownloadsSwipeStage.DeleteFiles)
                            }
                            // 未进入移除阈值时，浅滑松手停靠，供用户点击图标确认移除。
                            -dragOffset >= revealOffset / 2f -> currentOnSwipedGroupChange(groupId)
                            else -> currentOnSwipedGroupChange(null)
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        stage = DownloadsSwipeStage.RevealRecords
                    },
                )
            } finally {
                // 切换多选会取消 pointerInput，此时框架不保证调用 onDragCancel。
                if (dragging) {
                    dragging = false
                    stage = DownloadsSwipeStage.RevealRecords
                }
            }
        },
    ) {
        // 停靠前保持完整按钮藏在卡片后方；完全露出后才随继续拖动拉伸，图标始终居中。
        val actionWidth = (with(density) { (-offset.value).toDp() } - SwipeActionGap)
            .coerceAtLeast(SwipeActionRestingWidth)
        val actionVisible = offset.value < 0f
        if (enabled) {
            val description = stringResource(
                when (stage) {
                    DownloadsSwipeStage.RevealRecords,
                    DownloadsSwipeStage.ClearRecords -> R.string.downloads_multi_clear_records
                    DownloadsSwipeStage.DeleteFiles -> R.string.downloads_multi_delete_files
                },
            )
            // 展开后的分段卡片之间有间隙，藏在卡片后方的按钮只在卡片轮廓之外可见。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape = SwipeRevealShape(
                            shift = -offset.value,
                            cardCorner = SegmentedListShapes.OuterCorner.toPx(),
                        )
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Surface(
                    enabled = actionVisible,
                    onClick = {
                        haptics.tap()
                        onDelete(stage == DownloadsSwipeStage.DeleteFiles)
                    },
                    // 背景由 Canvas 绘制，外层只裁剪一次，避免深红边缘与浅红底叠色。
                    color = Color.Transparent,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.width(actionWidth).fillMaxHeight()
                        .alpha(if (actionVisible) 1f else 0f)
                        .then(
                            if (actionVisible) Modifier.semantics { contentDescription = description }
                            else Modifier.clearAndSetSemantics {},
                        ),
                ) {
                    // 保留动画实例，快速跨过两个触发阈值时仍能连续播放图标与背景动画。
                    DownloadsSwipeAction(stage)
                }
            }
        }
        content(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) })
    }
}
