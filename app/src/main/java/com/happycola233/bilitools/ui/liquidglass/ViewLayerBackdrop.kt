package com.happycola233.bilitools.ui.liquidglass

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 将同一 Android 窗口内、位于 Compose 浮层背后的 View 实时录制为 Backdrop 采样源。
 *
 * 这用于跨 View/Compose 边界的液态玻璃：普通 [androidx.compose.ui.window.Dialog] 会创建独立窗口，
 * 无法直接采样主窗口；把浮层挂到 Activity 内容之上后，再录制其下方的 View，既能保持模态覆盖范围，
 * 也能让玻璃获得与页面一致的实时背景。
 */
@Composable
internal fun rememberViewLayerBackdrop(
    backgroundView: View,
    baseColor: Color,
): LayerBackdrop {
    val contentVersion = remember { mutableIntStateOf(0) }
    DisposableEffect(backgroundView) {
        var lastX = backgroundView.x
        var lastY = backgroundView.y
        val listener = ViewTreeObserver.OnPreDrawListener {
            val moved = backgroundView.x != lastX || backgroundView.y != lastY
            if (moved || backgroundView.isDirty) {
                lastX = backgroundView.x
                lastY = backgroundView.y
                contentVersion.intValue++
            }
            true
        }
        backgroundView.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {
            backgroundView.viewTreeObserver
                .takeIf { it.isAlive }
                ?.removeOnPreDrawListener(listener)
        }
    }

    val onDrawBackdrop: ContentDrawScope.() -> Unit = remember(backgroundView, baseColor) {
        {
            contentVersion.intValue // 订阅 View 内容失效，触发采样层重录制
            drawRect(baseColor)
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val checkpoint = nativeCanvas.save()
                nativeCanvas.translate(backgroundView.x, backgroundView.y)
                backgroundView.draw(nativeCanvas)
                nativeCanvas.restoreToCount(checkpoint)
            }
        }
    }
    return rememberLayerBackdrop(onDraw = onDrawBackdrop)
}
