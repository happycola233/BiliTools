package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/*
 * 确定进度与不定进度之间用淡入淡出交接：原生组件只为「不定 → 确定」提供连贯交接，
 * 反方向（下载完成进入合并、暂停后继续一个总量未知的任务组）会瞬间清空已有进度。
 *
 * 振幅只传目标值，过渡由组件自身动画化：被打断时从当前形态转向新目标，并保留波浪运动相位。
 * 波速保持默认，设为 0 会立即重置相位；组件会在振幅归零后自行停止波浪动画。
 */

private val FlatAmplitude: (progress: Float) -> Float = { 0f }

/** 确定进度：运动中按原生规则起伏（接近起点与终点时收平），暂停后收平。 */
private fun determinateAmplitude(moving: Boolean): (progress: Float) -> Float =
    if (moving) WavyProgressIndicatorDefaults.indicatorAmplitude else FlatAmplitude

/** 不定进度：交接淡出时同步收平。`true` 状态即不定进度。 */
private val Transition<Boolean>.indeterminateAmplitude: Float
    get() = if (targetState) 1f else 0f

@Composable
internal fun DownloadsGroupProgressIndicator(
    presentation: DownloadsGroupPresentation,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        presentation.progressFraction,
        WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "downloadsGroupProgress",
    )
    val modeTransition = updateTransition(presentation.progressUnknown, label = "downloadsGroupProgressMode")
    modeTransition.Crossfade(
        modifier = modifier.size(WavyProgressIndicatorDefaults.CircularContainerSize),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    ) { progressUnknown ->
        if (progressUnknown) {
            CircularWavyProgressIndicator(
                color = color,
                trackColor = trackColor,
                amplitude = modeTransition.indeterminateAmplitude,
            )
        } else {
            CircularWavyProgressIndicator(
                progress = { animatedProgress },
                color = color,
                trackColor = trackColor,
                amplitude = determinateAmplitude(moving = presentation.executing),
            )
        }
    }
}

/**
 * 单个任务的线性进度：传输中为波浪，暂停后收平；排队、合并和总量未知时切到不定进度。
 * 颜色沿用组件默认的 `primary` 前景与 `secondaryContainer` 底轨，与组进度圆环一致。
 */
@Composable
internal fun DownloadsTaskProgressIndicator(
    progress: Float,
    indeterminate: Boolean,
    transferring: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        progress,
        WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "downloadsTaskProgress",
    )
    val modeTransition = updateTransition(indeterminate, label = "downloadsTaskProgressMode")
    modeTransition.Crossfade(
        modifier = modifier.fillMaxWidth(),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    ) { isIndeterminate ->
        if (isIndeterminate) {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                amplitude = modeTransition.indeterminateAmplitude,
            )
        } else {
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                amplitude = determinateAmplitude(moving = transferring),
            )
        }
    }
}
