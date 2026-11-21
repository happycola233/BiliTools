package com.happycola233.bilitools.ui.eggs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * 点一下：内容轻轻弹一下，上方冒出一句 [text]。[text] 为空（没有彩蛋文案的语言）时只弹不说话。
 * 用在资料卡的等级、硬币这类原本不可点的小元素上。
 *
 * TooltipBox 不会把 Row/Column 的 weight 传给外层，需要占格子时由调用方在外面包一层带 weight 的 Box。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EggTapTooltip(
    text: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState()
    val bounce = remember { Animatable(1f) }
    val onTap: () -> Unit = {
        scope.launch {
            bounce.animateTo(1.18f, tween(90))
            bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
        if (text != null) scope.launch { tooltipState.show() }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { if (text != null) PlainTooltip { Text(text) } },
        state = tooltipState,
        // 只在点按时出现，长按不再额外弹出
        enableUserInput = false,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                ),
        ) {
            content()
        }
    }
}
