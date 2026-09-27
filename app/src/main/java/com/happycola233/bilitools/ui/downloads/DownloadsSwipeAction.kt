package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.theme.AppDestructiveColors

private val TrashLidPivot = Offset(6f, 6f)
private const val TrashOpenAngle = -40f
private val SwipeIconSize = 23.dp
// 开盖垃圾桶窄且留白多；在最长边归一化后补偿 25%，让实际笔画分量接近列表图标。
private const val TrashOpticalScale = 1.25f
// 开盖后的上方留白会让桶身显得下坠，上移 3.45dp，使笔画重心与列表图标对齐。
private val TrashOpticalOffsetY = (-3.45).dp

/** 背景与图标共用裁剪边界，让深红色扫过时前景也同步换色，始终保持对比度。 */
@Composable
internal fun DownloadsSwipeAction(stage: DownloadsSwipeStage) {
    val trash = ImageVector.vectorResource(R.drawable.ic_delete_outline_rounded_24)
    val trashPaths = remember(trash) {
        trash.root.filterIsInstance<VectorPath>().associate {
            it.name to PathParser().addPathNodes(it.pathData).toPath()
        }
    }
    val playlist = ImageVector.vectorResource(R.drawable.ic_playlist_remove_rounded_24)
    val playlistPath = remember(playlist) {
        PathParser().addPathNodes((playlist.root.first() as VectorPath).pathData).toPath()
    }
    val playlistBounds = remember(playlistPath) { playlistPath.getBounds() }
    val openTrashBounds = remember(trashPaths) {
        Path().apply {
            addPath(trashPaths.getValue("lid"))
            transform(Matrix().apply {
                translate(TrashLidPivot.x, TrashLidPivot.y)
                rotateZ(TrashOpenAngle)
                translate(-TrashLidPivot.x, -TrashLidPivot.y)
            })
            addPath(trashPaths.getValue("body"))
        }.getBounds()
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val container = AppDestructiveColors.container
    val onContainer = AppDestructiveColors.onContainer
    val strongContainer = AppDestructiveColors.strongContainer
    val onStrongContainer = AppDestructiveColors.onStrongContainer
    val transition = updateTransition(stage, label = "downloadsSwipeAction")
    val wipe by transition.animateFloat(
        transitionSpec = { tween(260) }, label = "destructiveWipe",
    ) { if (it == DownloadsSwipeStage.DeleteFiles) 1f else 0f }
    val recordsAlpha by transition.animateFloat(
        transitionSpec = { tween(100) }, label = "recordsIcon",
    ) { if (it == DownloadsSwipeStage.DeleteFiles) 0f else 1f }
    val emphasisScale by transition.animateFloat(
        transitionSpec = {
            if (targetState == DownloadsSwipeStage.RevealRecords) spring(dampingRatio = 1f, stiffness = 900f)
            else spring(dampingRatio = 0.6f, stiffness = 650f)
        }, label = "actionScale",
    ) { if (it == DownloadsSwipeStage.RevealRecords) 1f else 1.2f }
    val lidAngle by transition.animateFloat(
        transitionSpec = {
            if (targetState == DownloadsSwipeStage.DeleteFiles) {
                spring(dampingRatio = 0.55f, stiffness = 550f, visibilityThreshold = 0.1f)
            } else spring(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 0.1f)
        }, label = "lidAngle",
    ) { if (it == DownloadsSwipeStage.DeleteFiles) TrashOpenAngle else 0f }

    val wipePath = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        fun DrawScope.drawIcon(tint: Color) {
            // 列表图标的可见最长边为初始 23dp、触发后 27.6dp；垃圾桶另做视觉补偿。
            // 先按完整轮廓居中，再修正垃圾桶的视觉重心；RTL 仅镜像水平方向。
            val iconSize = SwipeIconSize.toPx() * emphasisScale
            val trashUnit = iconSize * TrashOpticalScale / maxOf(openTrashBounds.width, openTrashBounds.height)
            val trashCenterY = center.y + TrashOpticalOffsetY.toPx()
            withTransform({
                translate(center.x, trashCenterY)
                scale(if (rtl) -trashUnit else trashUnit, trashUnit, Offset.Zero)
                translate(-openTrashBounds.center.x, -openTrashBounds.center.y)
            }) {
                drawPath(trashPaths.getValue("body"), tint, alpha = 1f - recordsAlpha)
                rotate(lidAngle, TrashLidPivot) {
                    drawPath(trashPaths.getValue("lid"), tint, alpha = 1f - recordsAlpha)
                }
            }
            val playlistUnit = iconSize / maxOf(playlistBounds.width, playlistBounds.height)
            withTransform({
                translate(center.x, center.y)
                scale(if (rtl) -playlistUnit else playlistUnit, playlistUnit, Offset.Zero)
                translate(-playlistBounds.center.x, -playlistBounds.center.y)
            }) {
                drawPath(playlistPath, tint, alpha = recordsAlpha)
            }
        }
        // 入场结束后直接画整面深红，只让 Surface 做外轮廓裁剪；叠加同一条
        // 抗锯齿圆角会把底层浅红混进边缘，深色页面尤其明显。
        if (wipe == 1f) {
            drawRect(strongContainer)
            drawIcon(onStrongContainer)
            return@Canvas
        }
        drawRect(container)
        drawIcon(onContainer)
        // 前缘沿操作区的 20dp 圆角铺开；矩形延伸到外侧，避免初始窄条挤小圆角。
        // 背景和前景共用此路径，回拖与 RTL 镜像也保持相同轮廓。
        val left = if (rtl) 0f else size.width * (1f - wipe)
        val right = if (rtl) size.width * wipe else size.width
        val radius = 20.dp.toPx().coerceAtMost(size.height / 2f)
        wipePath.reset()
        wipePath.addRoundRect(RoundRect(
            left = if (rtl) -radius else left,
            top = 0f,
            right = if (rtl) right else right + radius,
            bottom = size.height,
            topLeftCornerRadius = if (rtl) CornerRadius.Zero else CornerRadius(radius),
            bottomLeftCornerRadius = if (rtl) CornerRadius.Zero else CornerRadius(radius),
            topRightCornerRadius = if (rtl) CornerRadius(radius) else CornerRadius.Zero,
            bottomRightCornerRadius = if (rtl) CornerRadius(radius) else CornerRadius.Zero,
        ))
        clipPath(wipePath) {
            drawRect(strongContainer)
            drawIcon(onStrongContainer)
        }
    }
}
