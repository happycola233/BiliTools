package com.happycola233.bilitools.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive 分段列表的形状约定，设置页、「我」页与下载页共用：
 * 同组条目以 [Gap] 隔开，组的首尾用 [OuterCorner] 大圆角、组内相邻处用 [InnerCorner] 小圆角；
 * 按下时四角一起变圆，以形变代替涟漪作为按压反馈。
 */
internal object SegmentedListShapes {
    val Gap: Dp = 2.dp
    /** 与 `MaterialTheme.shapes.largeIncreased` 相同。 */
    val OuterCorner: Dp = 20.dp
    /** 与 `MaterialTheme.shapes.extraSmall` 相同。 */
    val InnerCorner: Dp = 4.dp
    /** 单行设置项按下后收成胶囊。 */
    val PressedCorner: Dp = 40.dp

    fun edgeCorner(isGroupEdge: Boolean): Dp = if (isGroupEdge) OuterCorner else InnerCorner
}

/**
 * 按压时四角变为 [pressedCorner]、松手后回到 [topCorner] / [bottomCorner] 的分段形状，
 * 过渡使用快速空间弹簧。较高的卡片应传入更小的 [pressedCorner]，否则会从圆角矩形变成一团。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun animateSegmentShape(
    topCorner: Dp,
    bottomCorner: Dp,
    pressed: Boolean,
    pressedCorner: Dp = SegmentedListShapes.PressedCorner,
): RoundedCornerShape {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val top by animateDpAsState(
        targetValue = if (pressed) pressedCorner else topCorner,
        animationSpec = spec,
        label = "segmentTopCorner",
    )
    val bottom by animateDpAsState(
        targetValue = if (pressed) pressedCorner else bottomCorner,
        animationSpec = spec,
        label = "segmentBottomCorner",
    )
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
