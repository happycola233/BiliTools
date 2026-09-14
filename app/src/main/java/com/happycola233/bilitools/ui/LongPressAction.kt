package com.happycola233.bilitools.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics

/** 为仅响应长按的内容补齐项目触感与无障碍操作，避免各页面重复实现手势细节。 */
@Composable
internal fun Modifier.longPressAction(
    interactionKey: Any?,
    actionLabel: String?,
    feedbackShape: Shape? = null,
    feedbackOutset: Dp = 0.dp,
    restrictToBounds: Boolean = false,
    onLongPress: () -> Unit,
): Modifier {
    val haptics = rememberAppHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val action = remember(interactionKey, haptics) {
        {
            haptics.longPress()
            latestOnLongPress()
        }
    }

    val feedbackModifier = if (feedbackShape != null) {
        pressFeedback(interactionSource, feedbackShape, feedbackOutset)
    } else {
        this
    }
    return feedbackModifier.pointerInput(interactionKey, restrictToBounds) {
        // Compose 会自动扩大短文本的触摸目标；独立取值需要排除落在相邻字段名上的触点。
        fun acceptsPress(position: Offset): Boolean = !restrictToBounds ||
            (position.x >= 0f && position.x < size.width && position.y >= 0f && position.y < size.height)
        detectTapGestures(
            onPress = press@{ position ->
                if (!acceptsPress(position)) return@press
                val press = PressInteraction.Press(position)
                var released = false
                try {
                    interactionSource.emit(press)
                    released = tryAwaitRelease()
                } finally {
                    // 滚动接管手势或内容被移除时也要结束反馈，避免留下按压状态。
                    interactionSource.tryEmit(
                        if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                    )
                }
            },
            onLongPress = { position -> if (acceptsPress(position)) action() },
        )
    }.semantics(mergeDescendants = true) {
        onLongClick(label = actionLabel) {
            action()
            true
        }
    }
}
