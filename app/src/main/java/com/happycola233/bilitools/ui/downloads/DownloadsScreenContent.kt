package com.happycola233.bilitools.ui.downloads

import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingActionButtonMenuScope
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.ui.AppAlertDialog
import com.happycola233.bilitools.ui.FloatingControlsDefaults
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.mainBottomBarBottomInset
import com.happycola233.bilitools.ui.mainBottomBarWindowInsets
import com.happycola233.bilitools.ui.theme.AppAccents
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.util.Locale

sealed interface DownloadsDialogState {
    val deleteFiles: Boolean

    data class DeleteTask(
        val itemId: Long,
        override val deleteFiles: Boolean,
    ) : DownloadsDialogState

    data class DeleteGroup(
        val groupId: Long,
        override val deleteFiles: Boolean,
    ) : DownloadsDialogState

    data class BatchDelete(
        val groupIds: Set<Long>,
        override val deleteFiles: Boolean,
    ) : DownloadsDialogState

}

enum class DownloadsTaskAction {
    Open,
    Share,
    Delete,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreenContent(
    groups: List<DownloadGroup>,
    selectionMode: Boolean,
    selectedGroupIds: Set<Long>,
    expandedGroupIds: Set<Long>,
    collapsedSections: Set<DownloadSectionType>,
    swipedGroupId: Long?,
    dialogState: DownloadsDialogState?,
    contentTopPadding: Dp,
    resumeAllCount: Int,
    pauseAllCount: Int,
    completedGroupCount: Int,
    liquidGlassPanelsEnabled: Boolean,
    glassDebugEnabled: Boolean,
    glassCornerRadiusDp: Float,
    glassBlurRadiusDp: Float,
    glassRefractionHeightDp: Float,
    glassRefractionAmountFrac: Float,
    glassChromaticAberration: Boolean,
    glassSurfaceAlpha: Float,
    barGlassBlurRadiusDp: Float,
    barGlassRefractionHeightDp: Float,
    barGlassRefractionAmountFrac: Float,
    barGlassChromaticAberration: Boolean,
    barGlassSurfaceAlpha: Float,
    onOpenParse: () -> Unit,
    onBatchManage: () -> Unit,
    onResumeAll: () -> Unit,
    onPauseAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearAll: () -> Unit,
    onClearRecords: () -> Unit,
    onDeleteFiles: () -> Unit,
    onDialogDismiss: () -> Unit,
    onDialogConfirm: (dontAskAgain: Boolean) -> Unit,
    onToggleSection: (DownloadSectionType) -> Unit,
    onToggleGroupExpanded: (Long) -> Unit,
    onSwipedGroupChange: (Long?) -> Unit,
    onGroupSelectionToggle: (Long) -> Unit,
    onGroupPause: (DownloadGroup) -> Unit,
    onGroupResume: (DownloadGroup) -> Unit,
    onGroupReparse: (DownloadGroup) -> Unit,
    onGroupShowDetails: (DownloadGroup) -> Unit,
    onGroupDelete: (group: DownloadGroup, deleteFiles: Boolean) -> Unit,
    onTaskPauseResume: (DownloadItem) -> Unit,
    onTaskRetry: (DownloadItem) -> Unit,
    onTaskClick: (DownloadItem, Rect) -> Unit,
    onDragSelectionStart: (anchorGroupId: Long) -> Unit,
    onDragSelectionRange: (Set<Long>) -> Unit,
    onDragSelectionEnd: () -> Unit,
    onGlassCornerRadiusChange: (Float) -> Unit,
    onGlassBlurRadiusChange: (Float) -> Unit,
    onGlassRefractionHeightChange: (Float) -> Unit,
    onGlassRefractionAmountChange: (Float) -> Unit,
    onGlassChromaticAberrationChange: (Boolean) -> Unit,
    onGlassSurfaceAlphaChange: (Float) -> Unit,
    onGlassReset: () -> Unit,
    onBarGlassBlurRadiusChange: (Float) -> Unit,
    onBarGlassRefractionHeightChange: (Float) -> Unit,
    onBarGlassRefractionAmountChange: (Float) -> Unit,
    onBarGlassChromaticAberrationChange: (Boolean) -> Unit,
    onBarGlassSurfaceAlphaChange: (Float) -> Unit,
    onBarGlassReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberLayerBackdrop()
    val selectionMotion = rememberDownloadsSelectionMotion(selectionMode)
    // 页面全出血绘制，内容从主界面底栏后方滚过，列表与底部悬浮控件均需预留底栏净空。
    // 多选时主导航栏让位给工具栏，工具栏沿用相同的系统安全边距。
    val mainBarBottomInset = mainBottomBarBottomInset()
    val controlsBottomPadding = FloatingControlsDefaults.MainScreenBottomPadding + mainBarBottomInset
    val toolbarBottomPadding = FloatingControlsDefaults.EdgePadding +
        mainBottomBarWindowInsets().asPaddingValues().calculateBottomPadding()
    val listBottomPadding = lerp(
        FloatingControlsDefaults.DownloadsListBottomPadding + mainBarBottomInset,
        toolbarBottomPadding + FloatingToolbarDefaults.ContainerSize + FloatingControlsDefaults.EdgePadding,
        selectionMotion.layoutProgress,
    )
    val motionScheme = MaterialTheme.motionScheme
    val downloadsGlassStyle = DownloadsGlassStyle(
        cornerRadiusDp = glassCornerRadiusDp,
        blurRadiusDp = glassBlurRadiusDp,
        refractionHeightDp = glassRefractionHeightDp,
        refractionAmountFrac = glassRefractionAmountFrac,
        chromaticAberration = glassChromaticAberration,
        surfaceAlpha = glassSurfaceAlpha,
    )
    var debugExpanded by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(AppSurfaces.pageContainerColor),
        ) {
            DownloadsListContent(
                groups = groups,
                selectionMode = selectionMode,
                selectionMotion = selectionMotion,
                selectedGroupIds = selectedGroupIds,
                expandedGroupIds = expandedGroupIds,
                collapsedSections = collapsedSections,
                swipedGroupId = swipedGroupId,
                contentTopPadding = contentTopPadding,
                listBottomPadding = listBottomPadding,
                onToggleSection = onToggleSection,
                onToggleGroupExpanded = onToggleGroupExpanded,
                onSwipedGroupChange = onSwipedGroupChange,
                onGroupSelectionToggle = onGroupSelectionToggle,
                onGroupDelete = onGroupDelete,
                onGroupPause = onGroupPause,
                onGroupResume = onGroupResume,
                onGroupReparse = onGroupReparse,
                onGroupShowDetails = onGroupShowDetails,
                onTaskPauseResume = onTaskPauseResume,
                onTaskRetry = onTaskRetry,
                onTaskClick = onTaskClick,
                onDragSelectionStart = onDragSelectionStart,
                onDragSelectionRange = onDragSelectionRange,
                onDragSelectionEnd = onDragSelectionEnd,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (groups.isEmpty()) {
            DownloadsEmptyState(
                onOpenParse = onOpenParse,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopPadding, bottom = listBottomPadding),
            )
        }

        selectionMotion.transition.AnimatedVisibility(
            visible = { it },
            modifier = Modifier.align(Alignment.BottomCenter),
            // 与让位的主导航栏反向运动：导航栏沉到屏幕外，工具栏从同一位置升起。
            enter =
                fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = downloadsSelectionControlsSpec(),
                    ),
            exit =
                fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = downloadsSelectionControlsSpec(),
                    ),
        ) {
            DownloadsSelectionToolbar(
                backdrop = backdrop,
                glassStyle = downloadsGlassStyle,
                liquidGlassEnabled = liquidGlassPanelsEnabled,
                actionsEnabled = selectedGroupIds.isNotEmpty(),
                onClearRecords = onClearRecords,
                onDeleteFiles = onDeleteFiles,
                modifier = Modifier
                    .padding(horizontal = FloatingControlsDefaults.EdgePadding)
                    .padding(bottom = toolbarBottomPadding),
            )
        }

        AnimatedVisibility(
            visible = !selectionMode && groups.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomEnd),
            enter =
                fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleIn(
                        initialScale = 0.84f,
                        animationSpec = downloadsSelectionControlsSpec(),
                    ),
            exit =
                fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleOut(
                        targetScale = 0.84f,
                        animationSpec = downloadsSelectionControlsSpec(),
                    ),
        ) {
            DownloadsManageFab(
                bottomPadding = controlsBottomPadding,
                resumeAllCount = resumeAllCount,
                pauseAllCount = pauseAllCount,
                completedGroupCount = completedGroupCount,
                onBatchManage = onBatchManage,
                onResumeAll = onResumeAll,
                onPauseAll = onPauseAll,
                onClearCompleted = onClearCompleted,
                onClearAll = onClearAll,
            )
        }

        DownloadsDeleteDialog(
            dialogState = dialogState,
            onDismiss = onDialogDismiss,
            onConfirm = onDialogConfirm,
        )
        if (glassDebugEnabled) {
            GlassDebugPanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = contentTopPadding + 12.dp, end = 12.dp),
                expanded = debugExpanded,
                onToggleExpand = { debugExpanded = !debugExpanded },
                cornerRadiusDp = glassCornerRadiusDp,
                onCornerRadiusChange = onGlassCornerRadiusChange,
                blurRadiusDp = glassBlurRadiusDp,
                onBlurRadiusChange = onGlassBlurRadiusChange,
                refractionHeightDp = glassRefractionHeightDp,
                onRefractionHeightChange = onGlassRefractionHeightChange,
                refractionAmountFrac = glassRefractionAmountFrac,
                onRefractionAmountChange = onGlassRefractionAmountChange,
                chromaticAberration = glassChromaticAberration,
                onChromaticAberrationChange = onGlassChromaticAberrationChange,
                surfaceAlpha = glassSurfaceAlpha,
                onSurfaceAlphaChange = onGlassSurfaceAlphaChange,
                onReset = onGlassReset,
                barBlurRadiusDp = barGlassBlurRadiusDp,
                onBarBlurRadiusChange = onBarGlassBlurRadiusChange,
                barRefractionHeightDp = barGlassRefractionHeightDp,
                onBarRefractionHeightChange = onBarGlassRefractionHeightChange,
                barRefractionAmountFrac = barGlassRefractionAmountFrac,
                onBarRefractionAmountChange = onBarGlassRefractionAmountChange,
                barChromaticAberration = barGlassChromaticAberration,
                onBarChromaticAberrationChange = onBarGlassChromaticAberrationChange,
                barSurfaceAlpha = barGlassSurfaceAlpha,
                onBarSurfaceAlphaChange = onBarGlassSurfaceAlphaChange,
                onBarReset = onBarGlassReset,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsEmptyState(
    onOpenParse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.empty),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
        )
        Text(
            text = stringResource(R.string.downloads_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        FilledTonalButton(
            onClick = { haptics.tap(); onOpenParse() },
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.padding(top = 24.dp).heightIn(min = ButtonDefaults.MinHeight),
        ) {
            Icon(painterResource(R.drawable.ic_home_rounded_24), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.downloads_empty_action))
        }
    }
}

@Composable
internal fun DownloadsDeleteDialog(
    dialogState: DownloadsDialogState?,
    onDismiss: () -> Unit,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
) {
    val state = dialogState ?: return
    val title = stringResource(
        if (state.deleteFiles) R.string.downloads_multi_delete_files
        else R.string.downloads_multi_clear_records,
    )
    val message = when (state) {
        is DownloadsDialogState.DeleteTask,
        is DownloadsDialogState.DeleteGroup -> AnnotatedString(stringResource(
            if (state.deleteFiles) R.string.download_delete_files_message
            else R.string.download_remove_records_message,
        ))
        is DownloadsDialogState.BatchDelete -> htmlToAnnotatedString(
            stringResource(
                if (state.deleteFiles) R.string.downloads_multi_confirm_delete_message
                else R.string.downloads_multi_confirm_clear_message,
                state.groupIds.size,
            ),
        )
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = { Text(text = message) },
        confirmButton = {
            DownloadsDeleteDialogButtons(onDismiss, onConfirm)
        },
    )
}

@Composable
private fun DownloadsDeleteDialogButtons(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    val haptics = rememberAppHaptics()
    // 三个按钮作为一个整体交给 Material，避免内部 FlowRow 换行时改变视觉顺序。
    // 宽度不足时整组纵向排列；RTL 用 placeRelative 镜像，记忆按钮始终在中间。
    Layout(content = {
        TextButton(onClick = onDismiss) {
            Text(stringResource(android.R.string.cancel))
        }
        TextButton(onClick = { haptics.confirm(); onConfirm(true) }) {
            Text(stringResource(R.string.download_confirm_dont_ask_again), textAlign = TextAlign.Center)
        }
        TextButton(
            onClick = { haptics.confirm(); onConfirm(false) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(R.string.download_delete), fontWeight = FontWeight.Bold)
        }
    }) { measurables, constraints ->
        val buttons = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val spacing = 8.dp.roundToPx()
        val rowWidth = buttons.sumOf { it.width } + spacing * (buttons.size - 1)
        val horizontal = rowWidth <= constraints.maxWidth
        val width = constraints.constrainWidth(rowWidth)
        val height = if (horizontal) buttons.maxOf { it.height }
        else buttons.sumOf { it.height } + spacing * (buttons.size - 1)
        layout(width, height) {
            var x = width - rowWidth
            var y = 0
            buttons.forEach { button ->
                if (horizontal) {
                    button.placeRelative(x, (height - button.height) / 2)
                    x += button.width + spacing
                } else {
                    button.placeRelative(width - button.width, y)
                    y += button.height + spacing
                }
            }
        }
    }
}

/**
 * 页面级批量操作菜单。只列出当前可执行的项；展开时按钮按 Expressive 规范由容器色过渡到 `primary`，
 * 图标同步变色、变小并切换为关闭。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsManageFab(
    bottomPadding: Dp,
    resumeAllCount: Int,
    pauseAllCount: Int,
    completedGroupCount: Int,
    onBatchManage: () -> Unit,
    onResumeAll: () -> Unit,
    onPauseAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    var expanded by remember { mutableStateOf(false) }
    // 展开的操作项与收起态的主按钮同色，见 AppAccents.floatingActionContainer。
    val menuItemContainerColor = AppAccents.floatingActionContainer
    val menuItemContentColor = AppAccents.onFloatingActionContainer
    val buttonContainerColor = ToggleFloatingActionButtonDefaults.containerColor(
        initialColor = menuItemContainerColor,
        finalColor = MaterialTheme.colorScheme.primary,
    )
    val iconColor = ToggleFloatingActionButtonDefaults.iconColor(
        initialColor = menuItemContentColor,
        finalColor = MaterialTheme.colorScheme.onPrimary,
    )

    @Composable
    fun FloatingActionButtonMenuScope.MenuItem(iconRes: Int, text: String, onClick: () -> Unit) {
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; haptics.confirm(); onClick() },
            icon = { Icon(painter = painterResource(iconRes), contentDescription = null) },
            text = { Text(text = text) },
            containerColor = menuItemContainerColor,
            contentColor = menuItemContentColor,
        )
    }

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { next ->
                    haptics.toggle(next)
                    expanded = next
                },
                containerColor = buttonContainerColor,
            ) {
                Icon(
                    painter = painterResource(
                        if (checkedProgress > 0.5f) R.drawable.ic_close_rounded_24 else R.drawable.ic_more_horiz_24,
                    ),
                    contentDescription = stringResource(R.string.downloads_actions_menu),
                    // 颜色与尺寸由 animateIcon 的着色层随展开进度统一决定，覆盖图标自身的 tint。
                    modifier = Modifier.animateIcon({ checkedProgress }, color = iconColor),
                )
            }
        },
        modifier = modifier
            .padding(bottom = FloatingControlsDefaults.menuFabBottomPadding(bottomPadding)),
    ) {
        MenuItem(R.drawable.ic_delete_outline_rounded_24, stringResource(R.string.downloads_clear_all), onClearAll)
        if (completedGroupCount > 0) {
            MenuItem(R.drawable.ic_playlist_remove_rounded_24, stringResource(R.string.downloads_clear_completed), onClearCompleted)
        }
        if (pauseAllCount > 0) {
            MenuItem(R.drawable.ic_pause_24, stringResource(R.string.downloads_pause_all_with_count, pauseAllCount), onPauseAll)
        }
        if (resumeAllCount > 0) {
            MenuItem(R.drawable.ic_play_arrow_24, stringResource(R.string.downloads_resume_all_with_count, resumeAllCount), onResumeAll)
        }
        MenuItem(R.drawable.ic_checklist_rounded_24, stringResource(R.string.downloads_multi_manage), onBatchManage)
    }
}

@Composable
private fun GlassDebugPanel(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    cornerRadiusDp: Float,
    onCornerRadiusChange: (Float) -> Unit,
    blurRadiusDp: Float,
    onBlurRadiusChange: (Float) -> Unit,
    refractionHeightDp: Float,
    onRefractionHeightChange: (Float) -> Unit,
    refractionAmountFrac: Float,
    onRefractionAmountChange: (Float) -> Unit,
    chromaticAberration: Boolean,
    onChromaticAberrationChange: (Boolean) -> Unit,
    surfaceAlpha: Float,
    onSurfaceAlphaChange: (Float) -> Unit,
    onReset: () -> Unit,
    barBlurRadiusDp: Float,
    onBarBlurRadiusChange: (Float) -> Unit,
    barRefractionHeightDp: Float,
    onBarRefractionHeightChange: (Float) -> Unit,
    barRefractionAmountFrac: Float,
    onBarRefractionAmountChange: (Float) -> Unit,
    barChromaticAberration: Boolean,
    onBarChromaticAberrationChange: (Boolean) -> Unit,
    barSurfaceAlpha: Float,
    onBarSurfaceAlphaChange: (Float) -> Unit,
    onBarReset: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    if (!expanded) {
        Box(
            modifier = modifier
                .blockTouchThrough()
                .size(44.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.9f))
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "DBG",
                style = TextStyle(color = colorScheme.onSurface, fontSize = 11.sp),
            )
        }
        return
    }

    Column(
        modifier = modifier
            .blockTouchThrough()
            .width(268.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = stringResource(R.string.runtime_glass_debug),
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    color = colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            DebugSmallButton(text = stringResource(R.string.runtime_collapse), onClick = onToggleExpand)
        }

        Column(
            modifier = Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DebugSectionHeader(title = stringResource(R.string.runtime_downloads_glass_panel), onReset = onReset)
            DebugStepperRow(
                label = stringResource(R.string.runtime_corner_radius),
                value = "${formatFloat(cornerRadiusDp)} dp",
                onMinus = { onCornerRadiusChange((cornerRadiusDp - 1f).coerceIn(0f, 64f)) },
                onPlus = { onCornerRadiusChange((cornerRadiusDp + 1f).coerceIn(0f, 64f)) },
            )
            DebugStepperRow(
                label = stringResource(R.string.runtime_blur_radius),
                value = "${formatFloat(blurRadiusDp)} dp",
                onMinus = { onBlurRadiusChange((blurRadiusDp - 1f).coerceIn(0f, 48f)) },
                onPlus = { onBlurRadiusChange((blurRadiusDp + 1f).coerceIn(0f, 48f)) },
            )
            DebugStepperRow(
                label = stringResource(R.string.runtime_refraction_height),
                value = "${formatFloat(refractionHeightDp)} dp",
                onMinus = { onRefractionHeightChange((refractionHeightDp - 1f).coerceIn(0f, 72f)) },
                onPlus = { onRefractionHeightChange((refractionHeightDp + 1f).coerceIn(0f, 72f)) },
            )
            DebugStepperRow(
                label = stringResource(R.string.runtime_refraction_amount),
                value = formatFloat(refractionAmountFrac),
                onMinus = { onRefractionAmountChange((refractionAmountFrac - 0.05f).coerceIn(0f, 1f)) },
                onPlus = { onRefractionAmountChange((refractionAmountFrac + 0.05f).coerceIn(0f, 1f)) },
            )
            DebugStepperRow(
                label = stringResource(R.string.runtime_surface_opacity),
                value = formatFloat(surfaceAlpha),
                onMinus = { onSurfaceAlphaChange((surfaceAlpha - 0.05f).coerceIn(0f, 1f)) },
                onPlus = { onSurfaceAlphaChange((surfaceAlpha + 0.05f).coerceIn(0f, 1f)) },
            )
            DebugToggleRow(
                label = stringResource(R.string.runtime_chromatic_aberration),
                checked = chromaticAberration,
                onToggle = onChromaticAberrationChange,
            )

            DebugSectionHeader(title = stringResource(R.string.runtime_bottom_navigation), onReset = onBarReset)
            DebugStepperRow(
                label = stringResource(R.string.runtime_blur_radius),
                value = "${formatFloat(barBlurRadiusDp)} dp",
                onMinus = { onBarBlurRadiusChange((barBlurRadiusDp - 1f).coerceIn(0f, 48f)) },
                onPlus = { onBarBlurRadiusChange((barBlurRadiusDp + 1f).coerceIn(0f, 48f)) },
            )
            DebugStepperRow(
                label = stringResource(R.string.runtime_refraction_height),
                value = "${formatFloat(barRefractionHeightDp)} dp",
                onMinus = { onBarRefractionHeightChange((barRefractionHeightDp - 1f).coerceIn(0f, 72f)) },
                onPlus = { onBarRefractionHeightChange((barRefractionHeightDp + 1f).coerceIn(0f, 72f)) },
            )
            DebugStepperRow(
                label = stringResource(R.string.runtime_refraction_amount),
                value = formatFloat(barRefractionAmountFrac),
                onMinus = { onBarRefractionAmountChange((barRefractionAmountFrac - 0.05f).coerceIn(0f, 1f)) },
                onPlus = { onBarRefractionAmountChange((barRefractionAmountFrac + 0.05f).coerceIn(0f, 1f)) },
            )
            DebugStepperRow(
                label = stringResource(R.string.runtime_surface_opacity),
                value = formatFloat(barSurfaceAlpha),
                onMinus = { onBarSurfaceAlphaChange((barSurfaceAlpha - 0.05f).coerceIn(0f, 1f)) },
                onPlus = { onBarSurfaceAlphaChange((barSurfaceAlpha + 0.05f).coerceIn(0f, 1f)) },
            )
            DebugToggleRow(
                label = stringResource(R.string.runtime_chromatic_aberration),
                checked = barChromaticAberration,
                onToggle = onBarChromaticAberrationChange,
            )
        }
    }
}

@Composable
private fun DebugSectionHeader(
    title: String,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        DebugSmallButton(text = stringResource(R.string.runtime_reset), onClick = onReset)
    }
}

@Composable
private fun DebugToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = colorScheme.onSurfaceVariant, fontSize = 12.sp),
        )
        BasicText(
            text = if (checked) stringResource(R.string.runtime_on) else stringResource(R.string.runtime_off),
            style = TextStyle(
                color = if (checked) colorScheme.primary else colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun DebugStepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = colorScheme.onSurfaceVariant, fontSize = 12.sp),
        )
        DebugSmallButton(text = "-", onClick = onMinus)
        BasicText(
            text = value,
            modifier = Modifier.padding(horizontal = 6.dp),
            style = TextStyle(color = colorScheme.onSurface, fontSize = 12.sp),
        )
        DebugSmallButton(text = "+", onClick = onPlus)
    }
}

@Composable
private fun DebugSmallButton(
    text: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = colorScheme.onSurface, fontSize = 12.sp),
        )
    }
}

private fun htmlToAnnotatedString(rawHtml: String): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val builder = AnnotatedString.Builder(spanned.toString())
    spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        if (start < 0 || end <= start) {
            return@forEach
        }
        when (span) {
            is StyleSpan -> {
                val style = when (span.style) {
                    Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Typeface.BOLD_ITALIC -> SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                    )
                    else -> null
                }
                if (style != null) {
                    builder.addStyle(style, start, end)
                }
            }

            is UnderlineSpan -> {
                builder.addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    start,
                    end,
                )
            }

            is ForegroundColorSpan -> {
                builder.addStyle(
                    SpanStyle(color = Color(span.foregroundColor)),
                    start,
                    end,
                )
            }
        }
    }
    return builder.toAnnotatedString()
}

private fun formatFloat(value: Float): String {
    return String.format(Locale.getDefault(), "%.2f", value)
}
