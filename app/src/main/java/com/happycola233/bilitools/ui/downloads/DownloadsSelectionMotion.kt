package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt

@Stable
internal class DownloadsSelectionMotion(
    val transition: Transition<Boolean>,
    layoutProgress: State<Float>,
    controlsProgress: State<Float>,
    selectionAlpha: State<Float>,
) {
    private val animatedLayoutProgress by layoutProgress
    private val animatedControlsProgress by controlsProgress
    private val animatedAlpha by selectionAlpha
    // 布局平滑减速到位，回弹只作用于控件绘制，避免标题换行和卡片高度随回弹反复变化。
    val layoutProgress get() = animatedLayoutProgress.coerceIn(0f, 1f)
    val selectionScale get() = animatedControlsProgress.coerceAtLeast(0f)
    val actionScale get() = (1f - animatedControlsProgress).coerceAtLeast(0f)
    val selectionAlpha get() = animatedAlpha.coerceIn(0f, 1f)
    val isRunning get() = transition.currentState != transition.targetState || transition.isRunning
    val cardInset get() = lerp(16.dp, 10.dp, layoutProgress)
    val headerInset get() = lerp(16.dp, 12.dp, layoutProgress)
    val actionFraction get() = 1f - layoutProgress
    val actionAlpha get() = 1f - selectionAlpha
}

// 进出共用同一临界阻尼弹簧，让尺寸自然收尾；反向切换仍保留当前速度。
internal fun downloadsSelectionLayoutSpec(visibilityThreshold: Float = 0.001f) = spring(
    dampingRatio = 1f,
    stiffness = 600f,
    visibilityThreshold = visibilityThreshold,
)

// 控件以约 4% 的轻微过冲收尾，为复选框、操作按钮和面板保留同一回弹节奏。
internal fun <T> downloadsSelectionControlsSpec() = spring<T>(
    dampingRatio = 0.72f,
    stiffness = 300f,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberDownloadsSelectionMotion(selectionMode: Boolean): DownloadsSelectionMotion {
    val transition = updateTransition(selectionMode, label = "downloadsSelection")
    val motionScheme = MaterialTheme.motionScheme
    // 所有占位、尺寸和边距共用同一进度；分别启动弹簧会因位移和停止阈值不同而错位。
    val layoutProgress = transition.animateFloat(
        transitionSpec = { downloadsSelectionLayoutSpec() },
        label = "layoutProgress",
    ) { if (it) 1f else 0f }
    val controlsProgress = transition.animateFloat(
        transitionSpec = { downloadsSelectionControlsSpec() },
        label = "controlsProgress",
    ) { if (it) 1f else 0f }
    val alpha = transition.animateFloat(
        transitionSpec = { motionScheme.fastEffectsSpec() }, label = "selectionAlpha",
    ) { if (it) 1f else 0f }
    return remember(transition) { DownloadsSelectionMotion(transition, layoutProgress, controlsProgress, alpha) }
}

/** 占位与绘制分开：完整测量控件，以占位中心缩放，允许轻微回弹而不挤动相邻内容。 */
@Composable
internal fun DownloadsGroupSelectionSlot(
    layoutFraction: Float,
    alpha: Float,
    scale: Float,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (layoutFraction == 0f && alpha == 0f) return
    Box(
        modifier
            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics {})
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val width = (placeable.width * layoutFraction).roundToInt()
                val height = (placeable.height * layoutFraction).roundToInt()
                layout(width, height) {
                    placeable.placeRelative((width - placeable.width) / 2, (height - placeable.height) / 2)
                }
            }
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            },
    ) { content() }
}
