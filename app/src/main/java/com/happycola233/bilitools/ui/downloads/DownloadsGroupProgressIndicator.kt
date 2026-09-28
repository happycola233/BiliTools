package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// 当前 Material3 的振幅过渡为 500ms，刷新窗口覆盖被打断的旧动画和最终目标各一次过渡。
private const val AMPLITUDE_TARGET_REFRESH_DURATION_MILLIS = 1_000

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
internal fun DownloadsGroupProgressIndicator(
    presentation: DownloadsGroupPresentation,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val animatedCompletion by animateFloatAsState(
        presentation.completionFraction,
        WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "downloadsGroupCompletion",
    )
    val modeTransition = updateTransition(presentation.awaitingFirstResult, label = "downloadsGroupProgressMode")
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
    ) { awaitingFirstResult ->
        if (awaitingFirstResult) {
            CircularWavyProgressIndicator(
                color = color,
                trackColor = trackColor,
                amplitude = indeterminateAmplitude,
            )
        } else {
            val executing = presentation.executing
            val targetAmplitude by remember(executing) {
                derivedStateOf {
                    if (executing) WavyProgressIndicatorDefaults.indicatorAmplitude(animatedCompletion) else 0f
                }
            }
            // Material3 1.5.0-alpha27 在振幅动画运行时忽略新目标，完成后也不刷新绘制缓存。
            // 有限刷新使最终目标被重新读取；只在绘制阶段读取，避免逐帧重组，并同步遵循系统动画倍率。
            val amplitudeTargetRefresh = remember { Animatable(0f) }
            LaunchedEffect(targetAmplitude) {
                amplitudeTargetRefresh.snapTo(0f)
                amplitudeTargetRefresh.animateTo(
                    1f,
                    tween(durationMillis = AMPLITUDE_TARGET_REFRESH_DURATION_MILLIS, easing = LinearEasing),
                )
            }
            CircularWavyProgressIndicator(
                progress = { animatedCompletion },
                color = color,
                trackColor = trackColor,
                amplitude = {
                    // 读取刷新时钟以使绘制缓存失效；振幅插值与运动相位仍由原生组件保留。
                    amplitudeTargetRefresh.value
                    targetAmplitude
                },
                // 保留默认波速：设为 0 会立即重置相位。库会在振幅归零后自动停止波浪动画。
            )
        }
    }
}
