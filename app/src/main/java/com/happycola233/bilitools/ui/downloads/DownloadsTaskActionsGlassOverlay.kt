package com.happycola233.bilitools.ui.downloads

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.liquidglass.rememberViewLayerBackdrop
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.rememberAndroidThemeColorScheme
import com.kyant.backdrop.backdrops.layerBackdrop

private val taskActionsOuterHorizontalPadding = 12.dp
private val taskActionsContentHorizontalPadding = 24.dp
private val taskActionsIconSlotWidth = 24.dp
private val taskActionsIconTextSpacing = 12.dp

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
        onDismiss: () -> Unit,
        onActionSelected: (DownloadsTaskAction) -> Unit,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val container = activity.findViewById<ViewGroup>(android.R.id.content)
        dismiss(activity)

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

        fun removeHost() {
            if (composeView.parent === container) {
                container.removeView(composeView)
            }
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
                    glassStyle = settings.toDownloadsGlassStyle(),
                    onDismiss = {
                        removeHost()
                        onDismiss()
                    },
                    onActionSelected = { action ->
                        removeHost()
                        onActionSelected(action)
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
    glassStyle: DownloadsGlassStyle,
    onDismiss: () -> Unit,
    onActionSelected: (DownloadsTaskAction) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val backdrop = rememberViewLayerBackdrop(
        backgroundView = backgroundView,
        baseColor = AppSurfaces.pageContainerColor,
    )
    val scrimAlpha = if (isSystemInDarkTheme()) 0.48f else 0.32f
    Box(Modifier.fillMaxSize()) {
        // 与宿主 ComposeView 同尺寸的采样锚点，确保 View 与玻璃使用同一窗口坐标系。
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onDismiss,
                )
                .clearAndSetSemantics {},
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            val glassShape = RoundedCornerShape(glassStyle.cornerRadiusDp.dp)
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .fillMaxWidth(0.72f)
                    .shadow(elevation = 16.dp, shape = glassShape, clip = false)
                    .blockTouchThrough()
                    .downloadsGlassSurface(backdrop = backdrop, style = glassStyle)
                    .semantics {
                        dialog()
                        paneTitle = state.title
                    }
                    .padding(
                        start = taskActionsOuterHorizontalPadding,
                        end = taskActionsOuterHorizontalPadding,
                        top = 20.dp,
                        bottom = 12.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = taskActionsContentHorizontalPadding,
                        end = taskActionsContentHorizontalPadding,
                        top = 12.dp,
                        bottom = 8.dp,
                    ),
                )
                TaskActionRow(
                    iconRes = R.drawable.ic_open_in_new_24,
                    text = stringResource(R.string.download_action_open),
                    onClick = { onActionSelected(DownloadsTaskAction.Open) },
                )
                TaskActionRow(
                    iconRes = R.drawable.ic_share_24,
                    text = stringResource(R.string.download_action_share),
                    onClick = { onActionSelected(DownloadsTaskAction.Share) },
                )
            }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    haptics.tap()
                    onClick()
                }
                .padding(
                    start = taskActionsContentHorizontalPadding,
                    end = taskActionsContentHorizontalPadding,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(taskActionsIconSlotWidth),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = taskActionsIconTextSpacing)
                    .weight(1f),
            )
        }
    }
}
