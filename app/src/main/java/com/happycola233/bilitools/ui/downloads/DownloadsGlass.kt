package com.happycola233.bilitools.ui.downloads

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.happycola233.bilitools.data.AppSettings
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.sign

/** 下载页玻璃浮层的共享配方，由 DBG 面板中的「下载页玻璃浮窗」参数统一驱动。 */
internal data class DownloadsGlassStyle(
    val cornerRadiusDp: Float,
    val blurRadiusDp: Float,
    val refractionHeightDp: Float,
    val refractionAmountFrac: Float,
    val chromaticAberration: Boolean,
    val surfaceAlpha: Float,
)

internal fun AppSettings.toDownloadsGlassStyle(): DownloadsGlassStyle {
    return DownloadsGlassStyle(
        cornerRadiusDp = downloadsGlassCornerRadiusDp,
        blurRadiusDp = downloadsGlassBlurRadiusDp,
        refractionHeightDp = downloadsGlassRefractionHeightDp,
        refractionAmountFrac = downloadsGlassRefractionAmountFrac,
        chromaticAberration = downloadsGlassChromaticAberration,
        surfaceAlpha = downloadsGlassSurfaceAlpha,
    )
}

/**
 * 批量管理浮窗与任务操作弹窗共用的 Backdrop 玻璃表面，避免两处效果和调试参数逐渐分叉。
 */
@Composable
internal fun Modifier.downloadsGlassSurface(
    backdrop: Backdrop,
    style: DownloadsGlassStyle,
): Modifier {
    val isLightTheme = !isSystemInDarkTheme()
    val luminance = if (isLightTheme) 0.58f else 0.42f
    val surfaceOverlayColor = if (isLightTheme) Color.White else Color.Black
    return drawBackdrop(
        backdrop = backdrop,
        shape = { androidx.compose.foundation.shape.RoundedCornerShape(style.cornerRadiusDp.dp) },
        effects = {
            val adjustedLuminance =
                (luminance * 2f - 1f).let { sign(it) * it * it }
            colorControls(
                brightness = if (adjustedLuminance > 0f) {
                    lerp(0.1f, 0.5f, adjustedLuminance)
                } else {
                    lerp(0.1f, -0.2f, -adjustedLuminance)
                },
                contrast = if (adjustedLuminance > 0f) {
                    lerp(1f, 0f, adjustedLuminance)
                } else {
                    1f
                },
                saturation = 1.5f,
            )
            blur(style.blurRadiusDp.dp.toPx())
            lens(
                style.refractionHeightDp.dp.toPx(),
                size.minDimension * style.refractionAmountFrac.coerceIn(0f, 1f),
                depthEffect = true,
                chromaticAberration = style.chromaticAberration,
            )
        },
        highlight = { Highlight.Plain },
        onDrawSurface = {
            drawRect(
                surfaceOverlayColor.copy(alpha = style.surfaceAlpha.coerceIn(0f, 1f)),
            )
        },
    )
}

/** 阻止悬浮控件未处理的触摸继续落到其背后的页面。 */
internal fun Modifier.blockTouchThrough(): Modifier {
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                event.changes.forEach { it.consume() }
            }
        }
    }
}
