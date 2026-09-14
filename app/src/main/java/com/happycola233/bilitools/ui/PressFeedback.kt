package com.happycola233.bilitools.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 圆角仅用于绘制按压色层，不裁切文字，也不改变内容的尺寸、间距或对齐。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.pressFeedback(
    interactionSource: InteractionSource,
    shape: Shape = MaterialTheme.shapes.medium,
    outset: Dp = 0.dp,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val opacity = animateFloatAsState(
        targetValue = if (pressed) 0.1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "PressFeedbackOpacity",
    )
    val color = MaterialTheme.colorScheme.onSurface
    return drawWithCache {
        // 只向外扩展绘制范围，为文字留出呼吸空间，不参与布局测量。
        val expansion = outset.toPx()
        val outline = shape.createOutline(
            Size(size.width + expansion * 2, size.height + expansion * 2),
            layoutDirection,
            this,
        )
        onDrawWithContent {
            drawContent()
            translate(-expansion, -expansion) {
                drawOutline(outline, color, alpha = opacity.value)
            }
        }
    }
}
