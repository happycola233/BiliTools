package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R

/** 复用静态图标路径；手势只切换 [isOpen]，弹簧自行完成动作，不按拖动距离逐帧插值。 */
@Composable
internal fun SwipeDeleteIcon(isOpen: Boolean, isDragging: Boolean, modifier: Modifier = Modifier) {
    val vector = ImageVector.vectorResource(R.drawable.ic_delete_outline_rounded_24)
    val paths = remember(vector) {
        vector.root.filterIsInstance<VectorPath>().associate { path ->
            path.name to PathParser().addPathNodes(path.pathData).toPath()
        }
    }
    val body = paths.getValue("body")
    val lid = paths.getValue("lid")
    val tint = LocalContentColor.current
    val description = stringResource(R.string.delete)

    val transition = updateTransition(isOpen, label = "swipeDeleteIcon")
    // 桶盖最轻，弹开后有一次清楚的回落；桶身只轻弹，平移不回弹，避免整个图标晃动。
    // 回拖时用更强阻尼收拢；弹簧保留当前速度，快速反向也不会跳回起始帧。
    val lidAngle by transition.animateFloat(
        transitionSpec = {
            if (targetState) spring(dampingRatio = 0.55f, stiffness = 550f, visibilityThreshold = 0.1f)
            else spring(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 0.1f)
        },
        label = "lidAngle",
    ) { open -> if (open) -40f else 0f }
    val iconScale by transition.animateFloat(
        transitionSpec = {
            if (targetState) spring(dampingRatio = 0.66f, stiffness = 650f, visibilityThreshold = 0.001f)
            else spring(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 0.001f)
        },
        label = "iconScale",
    ) { open -> if (open) 1.2f else 1f }
    // 拖动时左移；松手保持开盖时仅向右补偿 1dp，让打开的桶盖视觉上更居中，垂直位置不变。
    val iconShiftDp by animateFloatAsState(
        targetValue = when {
            !isOpen -> 0f
            isDragging -> -2f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 1f, stiffness = 700f, visibilityThreshold = 0.01f),
        label = "iconShift",
    )

    Canvas(modifier.size(24.dp).semantics { contentDescription = description; role = Role.Image }) {
        val iconShiftPx = iconShiftDp.dp.toPx()
        // 在绘制阶段读取动画值，保持布局占位固定；平移量不受缩放影响。
        withTransform({
            translate(left = iconShiftPx)
            scale(
                scaleX = size.width / vector.viewportWidth,
                scaleY = size.height / vector.viewportHeight,
                pivot = Offset.Zero,
            )
            scale(
                scaleX = iconScale,
                scaleY = iconScale,
                pivot = Offset(vector.viewportWidth / 2f, vector.viewportHeight / 2f),
            )
        }) {
            drawPath(body, tint)
            // 以盖子左下角为支点向上掀起。
            rotate(degrees = lidAngle, pivot = Offset(6f, 6f)) {
                drawPath(lid, tint)
            }
        }
    }
}
