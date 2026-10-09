package com.happycola233.bilitools.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.ui.theme.AppSurfaces
import kotlin.math.roundToInt

/** 顶栏折叠后的高度，不含状态栏。 */
internal val MainTopBarCollapsedHeight = 56.dp

/** 顶栏完全展开时的高度，不含状态栏。 */
internal val MainTopBarExpandedHeight = 96.dp

/** 标题起始侧边距；展开态与折叠态取值相同，折叠过程中标题不横向移动。 */
private val MainTopBarTitleStartPadding = 16.dp

/** 两端图标按钮与屏幕边缘的距离，使 24dp 图标落在 16dp 边距上。 */
private val MainTopBarIconEdgePadding = 4.dp

/** 有导航图标时标题起点为 56dp（16dp 边距 + 24dp 图标 + 16dp 间距），即 48dp 按钮宽度再加 8dp。 */
private val MainTopBarNavigationTitleOffset = 8.dp

/** 展开态标题基线到顶栏底边的距离。 */
private val MainTopBarExpandedTitleBaselineMargin = 28.dp

private data class MainTopBarTitle(val text: String, val fontFamily: FontFamily?, val key: Any)

/**
 * 主界面折叠顶栏。
 *
 * Material 3 的两行顶栏折叠时是「大标题被裁切、小标题淡入」的交叉淡变；这里只保留一个标题，
 * 位置线性插值、字号按减速曲线从 headlineMedium 收到 titleLarge，让标题连续上移并缩小。
 *
 * [navigationIcon] 与 [actions] 用于多选等上下文模式：两者与标题的视觉中线对齐并随之折叠，
 * 导航图标出现时标题随其宽度让位。[titleKey] 标识标题所属的模式，模式切换时新旧标题交叉淡变，
 * 同一模式内的文字更新（如选中数量）直接替换。
 *
 * 折叠进度只在测量与放置阶段读取，滚动时顶栏不重组。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MainCollapsingTopBar(
    title: String,
    state: TopAppBarState,
    modifier: Modifier = Modifier,
    titleFontFamily: FontFamily? = null,
    titleKey: Any = Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    val typography = MaterialTheme.typography
    val motionScheme = MaterialTheme.motionScheme
    val containerColor = AppSurfaces.pageContainerColor
    val titleColor = MaterialTheme.colorScheme.onSurface

    val expandedHeightPx: Int
    val collapsedHeightPx: Int
    val titleStartPaddingPx: Int
    val iconEdgePaddingPx: Int
    val navigationTitleOffsetPx: Int
    val expandedBaselineMarginPx: Float
    val collapsedTitleScale: Float
    with(LocalDensity.current) {
        expandedHeightPx = MainTopBarExpandedHeight.roundToPx()
        collapsedHeightPx = MainTopBarCollapsedHeight.roundToPx()
        titleStartPaddingPx = MainTopBarTitleStartPadding.roundToPx()
        iconEdgePaddingPx = MainTopBarIconEdgePadding.roundToPx()
        navigationTitleOffsetPx = MainTopBarNavigationTitleOffset.roundToPx()
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
            .drawBehind { drawRect(containerColor) }
            // Insets 在组合之后、布局之前更新；交给布局修饰符处理，避免启动首帧标题位置跳动。
            // 背景放在 padding 外侧，让状态栏区域继续使用顶栏底色。
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contents = listOf(
            {
                AnimatedContent(
                    targetState = MainTopBarTitle(title, titleFontFamily, titleKey),
                    contentKey = { it.key },
                    contentAlignment = Alignment.CenterStart,
                    transitionSpec = {
                        fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                            fadeOut(motionScheme.fastEffectsSpec()) using SizeTransform(clip = false)
                    },
                    label = "mainTopBarTitle",
                ) { current ->
                    Text(
                        text = current.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = typography.headlineMedium.copy(
                            color = titleColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = current.fontFamily ?: typography.headlineMedium.fontFamily,
                            // 裁掉行高留白，让文本盒正好是字形的 ascent~descent，基线与视觉居中才对得准
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                    )
                }
            },
            navigationIcon,
            actions,
        ),
    ) { (titleMeasurables, navigationMeasurables, actionMeasurables), constraints ->
        val iconConstraints = Constraints()
        val navigationPlaceable = navigationMeasurables.firstOrNull()?.measure(iconConstraints)
        val actionsPlaceable = actionMeasurables.firstOrNull()?.measure(iconConstraints)
        val titleStart = titleStartPaddingPx +
            ((navigationPlaceable?.width ?: 0) - navigationTitleOffsetPx).coerceAtLeast(0)
        val titleEndReserve = actionsPlaceable?.let { it.width + iconEdgePaddingPx } ?: 0
        val titlePlaceable = titleMeasurables.first().measure(
            Constraints(maxWidth = (constraints.maxWidth - titleStart - titleEndReserve).coerceAtLeast(0)),
        )
        val height = (expandedHeightPx + state.heightOffset)
            .roundToInt()
            .coerceIn(collapsedHeightPx, expandedHeightPx)
        val titleTransformOrigin = TransformOrigin(
            pivotFractionX = if (layoutDirection == LayoutDirection.Rtl) 1f else 0f,
            pivotFractionY = 0f,
        )

        layout(constraints.maxWidth, height) {
            val fraction = state.collapsedFraction.coerceIn(0f, 1f)
            val baseline = titlePlaceable[FirstBaseline]
            // 展开态基线距顶栏底边固定 28dp；折叠态字形盒在 56dp 工具栏内垂直居中。
            val expandedY = height - expandedBaselineMarginPx - baseline
            val collapsedShift = baseline - collapsedTitleScale * titlePlaceable.height / 2f
            val scale = 1f + (collapsedTitleScale - 1f) * decelerate(fraction)
            val titleY = expandedY + fraction * collapsedShift
            titlePlaceable.placeRelativeWithLayer(
                x = titleStart,
                y = titleY.roundToInt(),
            ) {
                // 定位与缩放都锚定起始侧，避免 RTL 标题折叠时偏离右边距。
                transformOrigin = titleTransformOrigin
                scaleX = scale
                scaleY = scale
            }
            // 缩放以标题顶边为锚点，视觉中线 = 顶边 + 缩放后高度的一半。
            val titleCenterY = titleY + titlePlaceable.height * scale / 2f
            navigationPlaceable?.placeRelative(
                x = iconEdgePaddingPx,
                y = (titleCenterY - navigationPlaceable.height / 2f).roundToInt(),
            )
            actionsPlaceable?.placeRelative(
                x = constraints.maxWidth - iconEdgePaddingPx - actionsPlaceable.width,
                y = (titleCenterY - actionsPlaceable.height / 2f).roundToInt(),
            )
        }
    }
}

/** 与旧折叠栏字号插值一致的减速曲线（Android DecelerateInterpolator）。 */
private fun decelerate(fraction: Float): Float = 1f - (1f - fraction) * (1f - fraction)
