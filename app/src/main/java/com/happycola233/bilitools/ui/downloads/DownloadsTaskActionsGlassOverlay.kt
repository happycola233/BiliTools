package com.happycola233.bilitools.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.usesDarkSurfaces
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.roundToInt

/** 菜单与屏幕安全区之间的最小留白。 */
private val taskActionsScreenMargin = 12.dp

/** 菜单与被点击任务行之间的间隙。 */
private val taskActionsAnchorGap = 6.dp

private val taskActionsMinWidth = 180.dp
private val taskActionsMaxWidth = 240.dp
private val taskActionsPanelPadding = 8.dp
private val taskActionsItemHorizontalPadding = 16.dp
private val taskActionsIconTextSpacing = 12.dp
private val taskActionsItemShape = RoundedCornerShape(14.dp)

/** 弹窗出现时的起始缩放：只做一点点收缩，避免玻璃折射出现夸张的呼吸感。 */
private const val TASK_ACTIONS_ENTER_SCALE = 0.92f

/** 让内容比缩放更早到达不透明，缩放收尾时画面已经稳定。 */
private const val TASK_ACTIONS_ALPHA_SPEEDUP = 1.6f

private fun taskActionsScale(progress: Float): Float =
    lerp(TASK_ACTIONS_ENTER_SCALE, 1f, progress)

internal data class DownloadsTaskActionsOverlayRequest(
    val itemId: Long,
    val title: String,
    val anchorInWindow: Rect,
    val glassStyle: DownloadsGlassStyle,
)

/** 主壳持有的任务菜单状态；退场动画结束后才派发所选操作。 */
@Stable
internal class DownloadsTaskActionsOverlayState {
    var request by mutableStateOf<DownloadsTaskActionsOverlayRequest?>(null)
        private set
    var visible by mutableStateOf(false)
        private set

    private var pendingAction: DownloadsTaskAction? = null
    private var onActionSelected: ((DownloadsTaskAction) -> Unit)? = null

    val activeItemId: Long?
        get() = request?.itemId

    fun show(
        request: DownloadsTaskActionsOverlayRequest,
        onActionSelected: (DownloadsTaskAction) -> Unit,
    ) {
        this.request = request
        this.onActionSelected = onActionSelected
        pendingAction = null
        visible = true
    }

    fun dismiss() {
        if (request != null) visible = false
    }

    fun dismissImmediately() {
        request = null
        visible = false
        pendingAction = null
        onActionSelected = null
    }

    internal fun requestClose(action: DownloadsTaskAction?) {
        if (!visible) return
        pendingAction = action
        visible = false
    }

    internal fun finishExit() {
        val action = pendingAction
        val callback = onActionSelected
        dismissImmediately()
        if (action != null) callback?.invoke(action)
    }
}

/**
 * 与主壳同树的最上层任务菜单。玻璃直接读取主内容层的 [backdrop]，不再创建 View 宿主，
 * 也不会把 scrim 或菜单自身重新录进采样层。
 */
@Composable
internal fun DownloadsTaskActionsGlassOverlay(
    state: DownloadsTaskActionsOverlayState,
    backdrop: LayerBackdrop,
) {
    val request = state.request ?: return
    BackHandler(enabled = state.visible) { state.requestClose(null) }

    var overlayBoundsInWindow by remember { mutableStateOf(Rect.Zero) }
    val anchor = request.anchorInWindow.translate(
        -overlayBoundsInWindow.left,
        -overlayBoundsInWindow.top,
    )
    val motionScheme = MaterialTheme.motionScheme
    val enterSpec = motionScheme.fastSpatialSpec<Float>()
    val exitSpec = motionScheme.fastEffectsSpec<Float>()
    // 单一进度驱动缩放与淡入淡出，保证玻璃、投影与内容始终同步。
    val transition = remember { Animatable(0f) }
    LaunchedEffect(state.visible, request) {
        if (state.visible) {
            transition.animateTo(1f, enterSpec)
        } else {
            transition.animateTo(0f, exitSpec)
            state.finishExit()
        }
    }

    val scrimAlpha = if (MaterialTheme.colorScheme.usesDarkSurfaces()) 0.48f else 0.32f
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayBoundsInWindow = it.boundsInWindow() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        color = Color.Black,
                        alpha = scrimAlpha * transition.value.coerceIn(0f, 1f),
                    )
                }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { state.requestClose(null) },
                )
                .clearAndSetSemantics {},
        )
        TaskActionsAnchoredLayout(
            anchor = anchor,
            animationProgress = { transition.value },
        ) {
            // Backdrop 2.0 的反向采样补偿固定以左上角为基准，因此玻璃层保持左上缩放；
            // 菜单位于任务行上方时，由外层布局同步移动面板，使视觉缩放原点落在面板底边。
            val panelLayerBlock: GraphicsLayerScope.() -> Unit = remember {
                {
                    val progress = transition.value
                    val scale = taskActionsScale(progress)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    alpha = (progress * TASK_ACTIONS_ALPHA_SPEEDUP).coerceIn(0f, 1f)
                }
            }
            Column(
                modifier = Modifier
                    .widthIn(min = taskActionsMinWidth, max = taskActionsMaxWidth)
                    .width(IntrinsicSize.Max)
                    .blockTouchThrough()
                    .downloadsGlassSurface(
                        backdrop = backdrop,
                        style = request.glassStyle,
                        shadow = modalGlassShadow,
                        layerBlock = panelLayerBlock,
                    )
                    .semantics {
                        dialog()
                        paneTitle = request.title
                    }
                    .padding(taskActionsPanelPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = taskActionsItemHorizontalPadding,
                        end = taskActionsItemHorizontalPadding,
                        top = 10.dp,
                        bottom = 12.dp,
                    ),
                )
                TaskActionRow(
                    iconRes = R.drawable.ic_open_in_new_24,
                    text = stringResource(R.string.download_action_open),
                    onClick = { state.requestClose(DownloadsTaskAction.Open) },
                )
                TaskActionRow(
                    iconRes = R.drawable.ic_share_24,
                    text = stringResource(R.string.download_action_share),
                    onClick = { state.requestClose(DownloadsTaskAction.Share) },
                )
            }
        }
    }
}

/**
 * 把菜单摆到被点击任务行的紧邻位置：优先贴在行下方，下方放不下就翻到上方，
 * 水平方向与任务行左边缘对齐，并始终留在安全区内。动画期间固定靠近任务行的那条边，
 * 让下方菜单向下展开、上方菜单向上展开。
 */
@Composable
private fun TaskActionsAnchoredLayout(
    anchor: Rect,
    animationProgress: () -> Float,
    content: @Composable () -> Unit,
) {
    val safeInsets = WindowInsets.safeDrawing
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = taskActionsScreenMargin.roundToPx()
        val gap = taskActionsAnchorGap.roundToPx()
        val minX = safeInsets.getLeft(this, layoutDirection) + margin
        val minY = safeInsets.getTop(this) + margin
        val limitX = constraints.maxWidth - safeInsets.getRight(this, layoutDirection) - margin
        val limitY = constraints.maxHeight - safeInsets.getBottom(this) - margin

        val placeable = measurables.first().measure(
            Constraints(
                maxWidth = (limitX - minX).coerceAtLeast(0),
                maxHeight = (limitY - minY).coerceAtLeast(0),
            ),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val maxX = (limitX - placeable.width).coerceAtLeast(minX)
            val maxY = (limitY - placeable.height).coerceAtLeast(minY)
            val below = anchor.bottom.roundToInt() + gap
            val above = anchor.top.roundToInt() - gap - placeable.height
            val placeAbove = below > maxY && above >= minY
            val settledY = when {
                below <= maxY -> below
                placeAbove -> above
                else -> below.coerceIn(minY, maxY)
            }
            val animatedY = if (placeAbove) {
                val scale = taskActionsScale(animationProgress())
                settledY + ((1f - scale) * placeable.height).roundToInt()
            } else {
                settledY
            }
            placeable.place(anchor.left.roundToInt().coerceIn(minX, maxX), animatedY)
        }
    }
}

@Composable
private fun TaskActionRow(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(taskActionsItemShape)
            .clickable {
                haptics.tap()
                onClick()
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = taskActionsItemHorizontalPadding, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = taskActionsIconTextSpacing),
        )
    }
}
