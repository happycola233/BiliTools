package com.happycola233.bilitools.ui.downloads

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.flow.StateFlow

/** 顶栏两端的多选控件随进入多选展开出现；水平伸展让标题同步让位，而不是被瞬间推开。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionSlot(
    visible: Boolean,
    expandFrom: Alignment.Horizontal,
    content: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val enter: EnterTransition = fadeIn(motionScheme.defaultEffectsSpec()) +
        scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.6f) +
        expandHorizontally(motionScheme.defaultSpatialSpec(), expandFrom = expandFrom)
    val exit: ExitTransition = fadeOut(motionScheme.fastEffectsSpec()) +
        scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.6f) +
        shrinkHorizontally(motionScheme.defaultSpatialSpec(), shrinkTowards = expandFrom)
    AnimatedVisibility(visible = visible, enter = enter, exit = exit) { content() }
}

/** 多选时顶栏起始侧的关闭按钮，等同系统返回键退出多选。 */
@Composable
internal fun DownloadsSelectionCloseButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    SelectionSlot(visible = visible, expandFrom = Alignment.Start) {
        IconButton(onClick = { haptics.tap(); onClick() }) {
            Icon(
                painter = painterResource(R.drawable.ic_close_rounded_24),
                contentDescription = stringResource(R.string.downloads_multi_exit),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 多选时顶栏末端的全选按钮。全部选中后切换为取消全选；用文字而不是复选框，
 * 避免顶栏右上角出现一块实心色块抢走标题的注意力。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadsSelectAllButton(
    visible: Boolean,
    selectedGroupIds: Set<Long>,
    groups: StateFlow<List<DownloadGroup>>,
    onToggleSelectAll: (List<DownloadGroup>) -> Unit,
) {
    val haptics = rememberAppHaptics()
    SelectionSlot(visible = visible, expandFrom = Alignment.End) {
        val currentGroups = groups.collectAsState()
        // 下载进度会频繁刷新列表，这里只关心总数，避免按钮跟着每次进度更新重组。
        val totalCount by remember { derivedStateOf { currentGroups.value.size } }
        val allSelected = totalCount > 0 && selectedGroupIds.size >= totalCount
        val motionScheme = MaterialTheme.motionScheme
        TextButton(
            onClick = {
                haptics.tap()
                onToggleSelectAll(currentGroups.value)
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            AnimatedContent(
                targetState = allSelected,
                transitionSpec = {
                    fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
                },
                label = "downloadsSelectAllLabel",
            ) { all ->
                Text(
                    stringResource(if (all) R.string.downloads_multi_unselect_all else R.string.downloads_multi_select_all),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * 多选时替代主导航栏的悬浮工具栏，承载对所选项目的两种批量处理。
 * 处理后果在确认弹窗中说明，这里只保留操作本身。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadsSelectionToolbar(
    backdrop: LayerBackdrop,
    glassStyle: DownloadsGlassStyle,
    liquidGlassEnabled: Boolean,
    actionsEnabled: Boolean,
    onClearRecords: () -> Unit,
    onDeleteFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        // 容器由玻璃或不透明浮层表面绘制，与下载页其他浮窗保持同一套材质。
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = Color.Transparent),
        expandedShadowElevation = 0.dp,
        modifier = modifier
            .blockTouchThrough()
            .downloadsPanelSurface(
                backdrop = backdrop,
                style = glassStyle,
                liquidGlassEnabled = liquidGlassEnabled,
                shape = CircleShape,
            ),
    ) {
        SelectionToolbarButton(
            iconRes = R.drawable.ic_playlist_remove_rounded_24,
            text = stringResource(R.string.downloads_multi_clear_records),
            enabled = actionsEnabled,
            destructive = false,
            onClick = onClearRecords,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        SelectionToolbarButton(
            iconRes = R.drawable.ic_delete_outline_rounded_24,
            text = stringResource(R.string.downloads_multi_delete_files),
            enabled = actionsEnabled,
            destructive = true,
            onClick = onDeleteFiles,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionToolbarButton(
    @DrawableRes iconRes: Int,
    text: String,
    enabled: Boolean,
    destructive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val content: @Composable () -> Unit = {
        Icon(painterResource(iconRes), null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val onButtonClick = {
        haptics.tap()
        onClick()
    }
    if (destructive) {
        Button(
            onClick = onButtonClick,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = modifier,
        ) { content() }
    } else {
        FilledTonalButton(
            onClick = onButtonClick,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = modifier,
        ) { content() }
    }
}
