package com.happycola233.bilitools.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics

/** 为仅响应长按的内容补齐项目触感与无障碍操作，避免各页面重复实现手势细节。 */
@Composable
internal fun Modifier.longPressAction(
    interactionKey: Any?,
    actionLabel: String,
    onLongPress: () -> Unit,
): Modifier {
    val haptics = rememberAppHaptics()
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val action = remember(interactionKey, haptics) {
        {
            haptics.longPress()
            latestOnLongPress()
        }
    }

    return pointerInput(interactionKey) {
        detectTapGestures(onLongPress = { action() })
    }.semantics {
        onLongClick(label = actionLabel) {
            action()
            true
        }
    }
}
