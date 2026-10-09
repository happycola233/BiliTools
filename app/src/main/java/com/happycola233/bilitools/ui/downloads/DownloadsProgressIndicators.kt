package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// 当前 Material3 的振幅过渡为 500ms，刷新窗口覆盖被打断的旧动画和最终目标各一次过渡。
private const val AMPLITUDE_TARGET_REFRESH_DURATION_MILLIS = 1_000

/**
 * 波浪进度条的振幅回调。Material3 1.5.0-alpha27 在振幅动画运行时忽略新目标，完成后也不刷新绘制缓存；
 * 目标变化后读取一段有限的刷新时钟，使最终目标被重新读取。时钟只在绘制阶段读取，避免逐帧重组，
 * 并同步遵循系统动画倍率；振幅插值与运动相位仍由原生组件保留。
 */
@Composable
private fun rememberWavyAmplitude(targetAmplitude: () -> Float): (Float) -> Float {
    val latestTarget = rememberUpdatedState(targetAmplitude)
    val target = remember { derivedStateOf { latestTarget.value() } }
    val refresh = remember { Animatable(0f) }
    LaunchedEffect(target.value) {
        refresh.snapTo(0f)
        refresh.animateTo(1f, tween(durationMillis = AMPLITUDE_TARGET_REFRESH_DURATION_MILLIS, easing = LinearEasing))
    }
    return remember {
        { _: Float ->
            refresh.value
            target.value
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
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
    val modeAnimationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // 不定进度组件自身不动画化振幅；让收拢与模式交接共用 Transition，
    // 退出组件会保留原有运动相位，直到过渡结束，快速暂停/继续也能从当前形态反向。
    val indeterminateAmplitude by modeTransition.animateFloat(
        transitionSpec = { modeAnimationSpec },
        label = "downloadsGroupIndeterminateAmplitude",
    ) { if (it) 1f else 0f }
    modeTransition.Crossfade(
        modifier = modifier.size(WavyProgressIndicatorDefaults.CircularContainerSize),
        animationSpec = modeAnimationSpec,
    ) { progressUnknown ->
        if (progressUnknown) {
            CircularWavyProgressIndicator(
                color = color,
                trackColor = trackColor,
                amplitude = indeterminateAmplitude,
            )
        } else {
            val executing = presentation.executing
            CircularWavyProgressIndicator(
                progress = { animatedProgress },
                color = color,
                trackColor = trackColor,
                // 保留默认波速：设为 0 会立即重置相位。库会在振幅归零后自动停止波浪动画。
                amplitude = rememberWavyAmplitude {
                    if (executing) WavyProgressIndicatorDefaults.indicatorAmplitude(animatedProgress) else 0f
                },
            )
        }
    }
}

/**
 * 单个任务的线性进度：传输中为波浪，暂停后收平；排队、合并和总量未知时切到不定进度。
 * 颜色沿用组件默认的 `primary` 前景与 `secondaryContainer` 底轨，与组进度圆环一致。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
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
    val modeAnimationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val indeterminateAmplitude by modeTransition.animateFloat(
        transitionSpec = { modeAnimationSpec },
        label = "downloadsTaskIndeterminateAmplitude",
    ) { if (it) 1f else 0f }
    modeTransition.Crossfade(modifier = modifier.fillMaxWidth(), animationSpec = modeAnimationSpec) { isIndeterminate ->
        if (isIndeterminate) {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                amplitude = indeterminateAmplitude,
            )
        } else {
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                amplitude = rememberWavyAmplitude {
                    if (transferring) WavyProgressIndicatorDefaults.indicatorAmplitude(animatedProgress) else 0f
                },
            )
        }
    }
}
