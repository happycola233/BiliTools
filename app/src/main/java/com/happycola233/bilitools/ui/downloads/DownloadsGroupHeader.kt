package com.happycola233.bilitools.ui.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.AppAccents

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadsGroupHeader(
    group: DownloadGroup,
    presentation: DownloadsGroupPresentation,
    selectionMode: Boolean,
    selectionMotion: DownloadsSelectionMotion,
    selected: Boolean,
    expanded: Boolean,
    allMissing: Boolean,
    headlineColor: Color,
    supportingColor: Color,
    coverPlaceholderColor: Color,
    onToggleSelection: () -> Unit,
    onToggleExpanded: () -> Unit,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val titleMeasurer = rememberTextMeasurer(cacheSize = 2)
    val titleStyle = MaterialTheme.typography.titleMedium
    val haptics = rememberAppHaptics()
    val coverModel = remember(group.coverUrl, context) {
        group.coverUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(context).data(it).crossfade(true).build()
        }
    }
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "downloadsGroupArrow",
    )
    val expandAction = stringResource(when {
        presentation.completed && expanded -> R.string.downloads_group_collapse
        presentation.completed -> R.string.downloads_group_view_files
        expanded -> R.string.downloads_group_collapse_tasks
        else -> R.string.downloads_group_view_tasks
    })
    val supportingText = presentation.headerSupportingText(context, group)
    val transferEstimate = rememberDownloadTransferEstimate(group.tasks, presentation.speedBytesPerSec, presentation.etaSeconds)
    val footerStatus = presentation.footerStatus(context, group, transferEstimate)
    val checkboxSize = 40.dp
    val checkboxEndSpacing = 6.dp
    // 底栏与封面共用起始位置，多选时为复选框整列留空。
    val footerStartPadding = (checkboxSize + checkboxEndSpacing) * selectionMotion.layoutProgress
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 用普通模式的可用宽度判断窄屏，避免动画经过断点时突然切换封面和文字间距。
        val compact = maxWidth - (16.dp - selectionMotion.cardInset) * 2 - 32.dp < 300.dp
        val normalCoverWidth = if (compact) 72.dp else 80.dp
        val titleSpacing = if (compact) 12.dp else 16.dp
        val actionWidth = if (presentation.completed) 32.dp else 56.dp
        // 预先测量两端宽度，标题高度从模式切换时就参与同一 Transition，
        // 不等途中实际换行才启动尺寸动画；展开标题和中途反向也从当前高度自然接续。
        val normalTitleWidth = maxWidth - (16.dp - selectionMotion.cardInset) * 2 -
            32.dp - normalCoverWidth - titleSpacing - 8.dp - actionWidth
        val selectionTitleWidth = maxWidth + (selectionMotion.cardInset - 10.dp) * 2 -
            24.dp - checkboxSize - checkboxEndSpacing - 72.dp - titleSpacing
        fun measureTitleHeight(selecting: Boolean): Float {
            val width = if (selecting) selectionTitleWidth else normalTitleWidth
            val measured = titleMeasurer.measure(
                text = group.title,
                style = titleStyle,
                maxLines = if (expanded && !selecting) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = with(density) { width.roundToPx().coerceAtLeast(0) }),
            )
            return with(density) { measured.size.height.toDp().value }
        }
        val normalTitleHeight = measureTitleHeight(false)
        val selectionTitleHeight = measureTitleHeight(true)
        val titleHeight by selectionMotion.transition.animateFloat(
            transitionSpec = { downloadsSelectionLayoutSpec(visibilityThreshold = 0.1f) },
            label = "groupTitleHeight-${group.id}",
        ) { if (it) selectionTitleHeight else normalTitleHeight }
        val targetTitleHeight = if (selectionMode) selectionTitleHeight else normalTitleHeight
        // 布局尺寸与共用进度一样不越过终点，避免多个组的高度过冲累积成列表晃动。
        // 记录本次目标变化时的高度，展开标题折叠和中途反向不会被新端点截断。
        val titleHeightRange = remember(targetTitleHeight) {
            minOf(titleHeight, targetTitleHeight)..maxOf(titleHeight, targetTitleHeight)
        }
        val collapsedBottomPadding = if (presentation.completed) 16.dp else 4.dp
        val bottomPadding = when {
            expanded -> lerp(6.dp, collapsedBottomPadding, selectionMotion.layoutProgress)
            presentation.completed -> 16.dp
            else -> 4.dp
        }
        Column(Modifier.padding(horizontal = selectionMotion.headerInset).padding(top = 16.dp, bottom = bottomPadding)) {
            // 完成后恢复「标题 + 时间」，与封面、展开箭头共用中心线，不保留操作按钮的空位。
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                DownloadsGroupSelectionSlot(
                    layoutFraction = selectionMotion.layoutProgress,
                    alpha = selectionMotion.selectionAlpha,
                    scale = selectionMotion.selectionScale,
                    interactive = selectionMode,
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { if (selectionMode) { haptics.toggle(it); onToggleSelection() } },
                        colors = AppAccents.checkboxColors(),
                        modifier = Modifier.padding(end = checkboxEndSpacing).size(checkboxSize),
                    )
                }
                AsyncImage(
                    model = coverModel,
                    contentDescription = stringResource(R.string.downloads_group_cover_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(
                            lerp(normalCoverWidth, 72.dp, selectionMotion.layoutProgress),
                            lerp(if (compact) 50.dp else 56.dp, 50.dp, selectionMotion.layoutProgress),
                        )
                        .clip(RoundedCornerShape(12.dp)).background(coverPlaceholderColor),
                )
                Column(Modifier.weight(1f).padding(start = titleSpacing, end = 8.dp * selectionMotion.actionFraction)) {
                    Text(
                        text = group.title,
                        style = titleStyle,
                        color = headlineColor,
                        maxLines = if (expanded && !selectionMode) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (allMissing) TextDecoration.LineThrough else TextDecoration.None,
                        // 动画只控制可见高度，不限制文字排版；否则第二行展开途中会被临时省略到第一行。
                        modifier = Modifier.fillMaxWidth().height(titleHeight.coerceIn(titleHeightRange).dp).clipToBounds()
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .alpha(if (allMissing) 0.6f else 1f),
                    )
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = supportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                DownloadsGroupSelectionSlot(
                    layoutFraction = selectionMotion.actionFraction,
                    alpha = selectionMotion.actionAlpha,
                    scale = selectionMotion.actionScale,
                    interactive = !selectionMode,
                ) {
                    if (presentation.completed) {
                        DownloadsGroupExpandButton(expandAction, arrowRotation, supportingColor, Modifier.size(32.dp)) {
                            if (!selectionMode) { haptics.tap(); onToggleExpanded() }
                        }
                    } else {
                        DownloadsGroupProgressAction(presentation, expandAction, supportingText) {
                            if (!selectionMode) { haptics.tap(); onAction() }
                        }
                    }
                }
            }
            if (presentation.completed) {
                // 「已完成」分区已表达结果，只补充跳过或文件丢失信息。
                if (footerStatus != null) {
                    Text(
                        text = footerStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (presentation.missingCount > 0) MaterialTheme.colorScheme.error else supportingColor,
                        modifier = Modifier.padding(start = footerStartPadding, top = 8.dp),
                    )
                }
            } else Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = footerStartPadding, top = 4.dp),
            ) {
                Text(
                    text = footerStatus.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (presentation.failedCount > 0 || presentation.missingCount > 0) MaterialTheme.colorScheme.error else supportingColor,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                DownloadsGroupSelectionSlot(
                    layoutFraction = selectionMotion.actionFraction,
                    alpha = selectionMotion.actionAlpha,
                    scale = selectionMotion.actionScale,
                    interactive = !selectionMode,
                ) {
                    TextButton(
                        onClick = { if (!selectionMode) { haptics.tap(); onToggleExpanded() } },
                        contentPadding = PaddingValues(start = 8.dp, end = 0.dp),
                        modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = expandAction },
                    ) {
                        Text(
                            pluralStringResource(R.plurals.downloads_group_task_count, group.tasks.size, group.tasks.size),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                        Icon(
                            painterResource(R.drawable.ic_expand_more_24), null,
                            modifier = Modifier.padding(start = 2.dp).size(18.dp).graphicsLayer { rotationZ = arrowRotation },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsGroupExpandButton(
    label: String,
    rotation: Float,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painterResource(R.drawable.ic_expand_more_24), label, tint = tint,
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsGroupProgressAction(
    presentation: DownloadsGroupPresentation,
    expandAction: String,
    completionDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "downloadsGroupActionScale",
    )
    val animatedCompletion by animateFloatAsState(
        presentation.completionFraction,
        WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "downloadsGroupCompletion",
    )
    val actionColor = WavyProgressIndicatorDefaults.indicatorColor
    val hasFailure = presentation.failedCount > 0
    val indicatorColor = if (hasFailure) {
        MaterialTheme.colorScheme.error
    } else {
        actionColor
    }
    // 零进度没有已完成弧，仍用淡错误色底轨表达失败；深浅两种色阶不会把失败画成完成。
    val trackColor = if (hasFailure) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        else WavyProgressIndicatorDefaults.trackColor
    val actionLabel = when (presentation.action) {
        DownloadsGroupAction.Pause -> stringResource(R.string.downloads_group_pause)
        DownloadsGroupAction.Resume -> stringResource(R.string.downloads_group_resume)
        DownloadsGroupAction.Retry -> stringResource(R.string.downloads_group_retry)
        DownloadsGroupAction.Expand -> expandAction
    }
    IconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.iconButtonColors(contentColor = actionColor),
        interactionSource = interactionSource,
        // 外圈与中心共用一个圆形触控面和涟漪裁切，不叠加实心内按钮或另一种按压形状。
        modifier = Modifier.size(56.dp).semantics {
            contentDescription = actionLabel
            stateDescription = completionDescription
            progressBarRangeInfo = if (presentation.awaitingFirstResult) ProgressBarRangeInfo.Indeterminate
                else ProgressBarRangeInfo(presentation.completionFraction, 0f..1f)
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 使用库的标准 48dp 波浪圆环及默认线宽、间距、波长、速度和随进度变化的振幅。
            // 暂停、排队和失败时将振幅/速度归零，表达工作已停止。
            val ringModifier = Modifier.size(WavyProgressIndicatorDefaults.CircularContainerSize).clearAndSetSemantics { }
            if (presentation.awaitingFirstResult) {
                CircularWavyProgressIndicator(modifier = ringModifier, color = indicatorColor, trackColor = trackColor)
            } else {
                CircularWavyProgressIndicator(
                    progress = { animatedCompletion }, modifier = ringModifier, color = indicatorColor, trackColor = trackColor,
                    amplitude = { if (presentation.executing) WavyProgressIndicatorDefaults.indicatorAmplitude(it) else 0f },
                    waveSpeed = if (presentation.executing) WavyProgressIndicatorDefaults.CircularWavelength else 0.dp,
                )
            }
            // 错误由外环提示；中心沿用主题色的继续图标，点击仍重试失败任务。
            Icon(
                painterResource(when (presentation.action) {
                    DownloadsGroupAction.Pause -> R.drawable.ic_pause_24
                    DownloadsGroupAction.Resume, DownloadsGroupAction.Retry -> R.drawable.ic_play_arrow_24
                    DownloadsGroupAction.Expand -> R.drawable.ic_expand_more_24
                }),
                contentDescription = null,
                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            )
        }
    }
}
