package com.happycola233.bilitools.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.ui.theme.AppSurfaces
import kotlin.math.roundToInt

/** 顶栏折叠后的高度，不含状态栏。 */
internal val MainTopBarCollapsedHeight = 56.dp

/** 顶栏完全展开时的高度，不含状态栏。 */
internal val MainTopBarExpandedHeight = 96.dp

/** 标题左边距；展开态与折叠态取值相同，折叠过程中标题不横向移动。 */
private val MainTopBarTitleStartPadding = 16.dp

/** 展开态标题基线到顶栏底边的距离。 */
private val MainTopBarExpandedTitleBaselineMargin = 28.dp

/**
 * 主界面折叠顶栏。
 *
 * Material 3 的两行顶栏折叠时是「大标题被裁切、小标题淡入」的交叉淡变；这里只保留一个标题，
 * 位置线性插值、字号按减速曲线从 headlineMedium 收到 titleLarge，让标题连续上移并缩小。
 *
 * 折叠进度只在测量与放置阶段读取，滚动时顶栏不重组。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainCollapsingTopBar(
    title: String,
    state: TopAppBarState,
    modifier: Modifier = Modifier,
    titleFontFamily: FontFamily? = null,
) {
    val typography = MaterialTheme.typography
    val containerColor = AppSurfaces.pageContainerColor
    val titleColor = MaterialTheme.colorScheme.onSurface
    val insetTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    val expandedHeightPx: Int
    val collapsedHeightPx: Int
    val titleStartPaddingPx: Int
    val expandedBaselineMarginPx: Float
    val collapsedTitleScale: Float
    with(LocalDensity.current) {
        expandedHeightPx = (insetTop + MainTopBarExpandedHeight).roundToPx()
        collapsedHeightPx = (insetTop + MainTopBarCollapsedHeight).roundToPx()
        titleStartPaddingPx = MainTopBarTitleStartPadding.roundToPx()
        expandedBaselineMarginPx = MainTopBarExpandedTitleBaselineMargin.toPx()
        collapsedTitleScale =
            typography.titleLarge.fontSize.toPx() / typography.headlineMedium.fontSize.toPx()
    }

    SideEffect {
        // 折叠区间由顶栏自身高度决定，滚动行为据此换算折叠进度
        state.heightOffsetLimit = (collapsedHeightPx - expandedHeightPx).toFloat()
    }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawRect(containerColor) },
        content = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = typography.headlineMedium.copy(
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = titleFontFamily ?: typography.headlineMedium.fontFamily,
                    // 裁掉行高留白，让文本盒正好是字形的 ascent~descent，基线与视觉居中才对得准
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        },
    ) { measurables, constraints ->
        val titlePlaceable = measurables.first().measure(
            Constraints(maxWidth = (constraints.maxWidth - titleStartPaddingPx).coerceAtLeast(0)),
        )
        val height = (expandedHeightPx + state.heightOffset)
            .roundToInt()
            .coerceIn(collapsedHeightPx, expandedHeightPx)

        layout(constraints.maxWidth, height) {
            val fraction = state.collapsedFraction.coerceIn(0f, 1f)
            val baseline = titlePlaceable[FirstBaseline]
            // 展开态基线距顶栏底边固定 28dp；折叠态字形盒在 56dp 工具栏内垂直居中。
            val expandedY = height - expandedBaselineMarginPx - baseline
            val collapsedShift = baseline - collapsedTitleScale * titlePlaceable.height / 2f
            val scale = 1f + (collapsedTitleScale - 1f) * decelerate(fraction)
            titlePlaceable.placeWithLayer(
                x = titleStartPaddingPx,
                y = (expandedY + fraction * collapsedShift).roundToInt(),
            ) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

/** 与旧折叠栏字号插值一致的减速曲线（Android DecelerateInterpolator）。 */
private fun decelerate(fraction: Float): Float = 1f - (1f - fraction) * (1f - fraction)
