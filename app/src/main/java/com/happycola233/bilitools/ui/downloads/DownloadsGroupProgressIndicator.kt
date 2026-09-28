package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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
            CircularWavyProgressIndicator(
                progress = { animatedCompletion },
                color = color,
                trackColor = trackColor,
                amplitude = { if (presentation.executing) WavyProgressIndicatorDefaults.indicatorAmplitude(it) else 0f },
                // 保留默认波速：设为 0 会立即重置相位。库会在振幅归零后自动停止波浪动画。
            )
        }
    }
}
