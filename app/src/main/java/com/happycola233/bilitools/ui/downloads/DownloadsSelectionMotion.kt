package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt

@Stable
internal class DownloadsSelectionMotion(
    val transition: Transition<Boolean>,
    progress: State<Float>,
    selectionAlpha: State<Float>,
) {
    private val animatedProgress by progress
    private val animatedAlpha by selectionAlpha
    // Expressive 弹簧允许越过终点，但布局占位与透明度必须保持在有效范围内。
    val progress get() = animatedProgress.coerceIn(0f, 1f)
    val selectionAlpha get() = animatedAlpha.coerceIn(0f, 1f)
    val isRunning get() = transition.currentState != transition.targetState || transition.isRunning
    val cardInset get() = lerp(16.dp, 10.dp, progress)
    val headerInset get() = lerp(16.dp, 12.dp, progress)
    val actionFraction get() = 1f - progress
    val actionAlpha get() = 1f - selectionAlpha
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberDownloadsSelectionMotion(selectionMode: Boolean): DownloadsSelectionMotion {
    val transition = updateTransition(selectionMode, label = "downloadsSelection")
    val motionScheme = MaterialTheme.motionScheme
    // 所有占位、尺寸和边距共用同一进度；分别启动弹簧会因位移和停止阈值不同而错位。
    val progress = transition.animateFloat(
        // 进入时交代模式变化，退出时快速归位；全页使用同一方向、同一进度。
        transitionSpec = { if (targetState) motionScheme.defaultSpatialSpec() else motionScheme.fastSpatialSpec() },
        label = "layoutProgress",
    ) { if (it) 1f else 0f }
    val alpha = transition.animateFloat(
        transitionSpec = { motionScheme.fastEffectsSpec() }, label = "selectionAlpha",
    ) { if (it) 1f else 0f }
    return remember(transition) { DownloadsSelectionMotion(transition, progress, alpha) }
}

/** 控件按完整尺寸测量，随占位等比缩放、淡入淡出，避免裁掉半个图标或挤压文字。 */
@Composable
internal fun DownloadsGroupSelectionSlot(
    fraction: Float,
    alpha: Float,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (fraction == 0f && alpha == 0f) return
    val transformOrigin = TransformOrigin(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else 0f, 0f)
    Box(
        modifier
            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics {})
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout((placeable.width * fraction).roundToInt(), (placeable.height * fraction).roundToInt()) {
                    placeable.placeRelative(0, 0)
                }
            }
            .graphicsLayer {
                this.alpha = alpha
                scaleX = fraction
                scaleY = fraction
                this.transformOrigin = transformOrigin
            },
    ) { content() }
}
