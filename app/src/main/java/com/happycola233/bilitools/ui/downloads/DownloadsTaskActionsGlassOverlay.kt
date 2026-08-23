package com.happycola233.bilitools.ui.downloads

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.liquidglass.rememberViewLayerBackdrop
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.rememberAndroidThemeColorScheme
import com.kyant.backdrop.backdrops.layerBackdrop
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

/**
 * 把任务操作弹窗挂到 Activity 内容之上，而不是放进独立的 Dialog Window。
 * 只有保持在同一窗口中，Backdrop 才能录制并折射弹窗背后的实时页面。
 */
internal object DownloadsTaskActionsGlassOverlay {
    private const val HOST_VIEW_TAG = "bilitools_downloads_task_actions_glass_host"

    fun show(
        activity: AppCompatActivity,
        backgroundView: View,
        state: DownloadsDialogState.TaskActions,
        anchorInWindow: Rect,
        onDismiss: () -> Unit,
        onActionSelected: (DownloadsTaskAction) -> Unit,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val container = activity.findViewById<ViewGroup>(android.R.id.content)
        dismiss(activity)

        // 宿主铺满内容区，把窗口坐标换算成内容区坐标后即可直接用于菜单定位。
        val containerLocation = IntArray(2)
        container.getLocationInWindow(containerLocation)
        val anchor = anchorInWindow.translate(
            -containerLocation[0].toFloat(),
            -containerLocation[1].toFloat(),
        )

        val composeView = ComposeView(activity).apply {
            tag = HOST_VIEW_TAG
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
        container.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        composeView.requestFocus()

        // 关闭动画播完再摘掉宿主并回调，避免退场画面被瞬间截断。
        val visibleState = mutableStateOf(true)
        var selectedAction: DownloadsTaskAction? = null
        var closing = false

        fun requestClose(action: DownloadsTaskAction?) {
            if (closing) return
            closing = true
            selectedAction = action
            visibleState.value = false
        }

        composeView.setContent {
            val colorScheme = rememberAndroidThemeColorScheme()
            val settings by activity.applicationContext.appContainer.settingsRepository
                .settings.collectAsState()
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            MaterialExpressiveTheme(colorScheme = colorScheme) {
                DownloadsTaskActionsOverlayContent(
                    backgroundView = backgroundView,
                    state = state,
                    anchor = anchor,
                    glassStyle = settings.toDownloadsGlassStyle(),
                    visible = visibleState.value,
                    onCloseRequested = ::requestClose,
                    onExitFinished = {
                        if (composeView.parent === container) {
                            container.removeView(composeView)
                        }
                        val action = selectedAction
                        if (action != null) {
                            onActionSelected(action)
                        } else {
                            onDismiss()
                        }
                    },
                )
            }
        }
    }

    fun dismiss(activity: AppCompatActivity) {
        val container = activity.findViewById<ViewGroup>(android.R.id.content)
        container.findViewWithTag<ComposeView>(HOST_VIEW_TAG)?.let(container::removeView)
    }
}

@Composable
private fun DownloadsTaskActionsOverlayContent(
    backgroundView: View,
    state: DownloadsDialogState.TaskActions,
    anchor: Rect,
    glassStyle: DownloadsGlassStyle,
    visible: Boolean,
    onCloseRequested: (DownloadsTaskAction?) -> Unit,
    onExitFinished: () -> Unit,
) {
    BackHandler { onCloseRequested(null) }

    val backdrop = rememberViewLayerBackdrop(
        backgroundView = backgroundView,
        baseColor = AppSurfaces.pageContainerColor,
    )
    val motionScheme = MaterialTheme.motionScheme
    val enterSpec = motionScheme.fastSpatialSpec<Float>()
    val exitSpec = motionScheme.fastEffectsSpec<Float>()
    // 单一进度驱动缩放与淡入淡出，保证玻璃、投影与内容始终同步。
    val transition = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            transition.animateTo(1f, enterSpec)
        } else {
            transition.animateTo(0f, exitSpec)
            onExitFinished()
        }
    }

    val scrimAlpha = if (isSystemInDarkTheme()) 0.48f else 0.32f
    Box(Modifier.fillMaxSize()) {
        // 与宿主 ComposeView 同尺寸的采样锚点，确保 View 与玻璃使用同一窗口坐标系。
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
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
                    onClick = { onCloseRequested(null) },
                )
                .clearAndSetSemantics {},
        )
        TaskActionsAnchoredLayout(anchor = anchor) {
            // 以左上角为缩放原点：Backdrop 的反向变换同样以左上角为基准，
            // 这样折射画面在整个缩放过程中都与真实页面严丝合缝，同时菜单看起来正是从任务行长出来的。
            val panelLayerBlock: GraphicsLayerScope.() -> Unit = remember {
                {
                    val progress = transition.value
                    val scale = lerp(TASK_ACTIONS_ENTER_SCALE, 1f, progress)
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
                        style = glassStyle,
                        shadow = modalGlassShadow,
                        layerBlock = panelLayerBlock,
                    )
                    .semantics {
                        dialog()
                        paneTitle = state.title
                    }
                    .padding(taskActionsPanelPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = state.title,
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
                    onClick = { onCloseRequested(DownloadsTaskAction.Open) },
                )
                TaskActionRow(
                    iconRes = R.drawable.ic_share_24,
                    text = stringResource(R.string.download_action_share),
                    onClick = { onCloseRequested(DownloadsTaskAction.Share) },
                )
            }
        }
    }
}

/**
 * 把菜单摆到被点击任务行的紧邻位置：优先贴在行下方，下方放不下就翻到上方，
 * 水平方向与任务行左边缘对齐，并始终留在安全区内。
 */
@Composable
private fun TaskActionsAnchoredLayout(
    anchor: Rect,
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
            val y = when {
                below <= maxY -> below
                above >= minY -> above
                else -> below.coerceIn(minY, maxY)
            }
            placeable.place(anchor.left.roundToInt().coerceIn(minX, maxX), y)
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
