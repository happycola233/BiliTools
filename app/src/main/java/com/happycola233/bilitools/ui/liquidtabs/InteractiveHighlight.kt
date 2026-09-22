// 移植自 Kyant0/AndroidLiquidGlass 2.0.0 catalog（Apache-2.0）：
// app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/InteractiveHighlight.kt
package com.happycola233.bilitools.ui.liquidtabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size) -> Offset,
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)

    private val shader =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                if (shader != null) {
                    drawRect(
                        Color.White.copy(0.08f * progress),
                        blendMode = BlendMode.Plus,
                    )
                    shader.apply {
                        val position = position(size)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.15f * progress))
                        setFloatUniform("radius", size.minDimension * 1.5f)
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height),
                        )
                    }
                    drawRect(
                        ShaderBrush(shader.asComposeShader()),
                        blendMode = BlendMode.Plus,
                    )
                } else {
                    drawRect(
                        Color.White.copy(0.25f * progress),
                        blendMode = BlendMode.Plus,
                    )
                }
            }

            drawContent()
        }

    fun press() {
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
        }
    }

    fun release() {
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
        }
    }
}
