package com.happycola233.bilitools.ui.downloads

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import android.text.style.MetricAffectingSpan
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.ui.FloatingControlsDefaults
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.mainBottomBarBottomInset
import com.happycola233.bilitools.ui.theme.AppAccents
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.util.Locale

sealed interface DownloadsDialogState {
    data class DeleteTask(
        val itemId: Long,
        val canDeleteFile: Boolean,
    ) : DownloadsDialogState

    data class DeleteGroup(
        val groupId: Long,
        val canDeleteFile: Boolean,
    ) : DownloadsDialogState

    data class BatchDelete(
        val groupIds: Set<Long>,
        val deleteFile: Boolean,
    ) : DownloadsDialogState

}

enum class DownloadsTaskAction {
    Open,
    Share,
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
    emptyStateVisible: Boolean,
    batchStatusText: String,
    batchSelectAllText: String,
    batchHintHtml: String,
    batchClearEnabled: Boolean,
    batchDeleteEnabled: Boolean,
    dialogState: DownloadsDialogState?,
    contentTopPadding: Dp,
    resumeAllCount: Int,
    pauseAllCount: Int,
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
    onBatchManage: () -> Unit,
    onResumeAll: () -> Unit,
    onPauseAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearAll: () -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onClearRecords: () -> Unit,
    onDeleteFiles: () -> Unit,
    onDialogDismiss: () -> Unit,
    onDialogConfirm: (Boolean) -> Unit,
    onToggleSection: (DownloadSectionType) -> Unit,
    onToggleGroupExpanded: (Long) -> Unit,
    onSwipedGroupChange: (Long?) -> Unit,
    onGroupSelectionToggle: (Long) -> Unit,
    onGroupPause: (DownloadGroup) -> Unit,
    onGroupResume: (DownloadGroup) -> Unit,
    onGroupDelete: (DownloadGroup) -> Unit,
    onTaskPauseResume: (DownloadItem) -> Unit,
    onTaskRetry: (DownloadItem) -> Unit,
    onTaskDelete: (DownloadItem) -> Unit,
    onTaskClick: (DownloadItem, Rect) -> Unit,
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
    val density = LocalDensity.current
    // 页面全出血绘制，内容从主界面底栏后方滚过，列表与底部悬浮控件均需预留底栏净空
    val mainBarBottomInset = mainBottomBarBottomInset()
    val controlsBottomPadding = FloatingControlsDefaults.MainScreenBottomPadding + mainBarBottomInset
    val panelBottomPadding = controlsBottomPadding + 8.dp
    var panelHeightPx by remember { mutableStateOf(0) }
    val baseBottomPaddingPx =
        with(density) {
            (FloatingControlsDefaults.DownloadsListBottomPadding + mainBarBottomInset).roundToPx()
        }
    val extraBottomPaddingPx =
        if (selectionMode) panelHeightPx + with(density) { 20.dp.roundToPx() } else 0
    val targetListBottomPaddingDp = with(density) { (baseBottomPaddingPx + extraBottomPaddingPx).toDp() }
    val motionScheme = MaterialTheme.motionScheme
    val downloadsGlassStyle = DownloadsGlassStyle(
        cornerRadiusDp = glassCornerRadiusDp,
        blurRadiusDp = glassBlurRadiusDp,
        refractionHeightDp = glassRefractionHeightDp,
        refractionAmountFrac = glassRefractionAmountFrac,
        chromaticAberration = glassChromaticAberration,
        surfaceAlpha = glassSurfaceAlpha,
    )
    val listBottomPaddingDp by animateDpAsState(
        targetValue = targetListBottomPaddingDp,
        animationSpec = motionScheme.defaultSpatialSpec(),
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
                selectedGroupIds = selectedGroupIds,
                expandedGroupIds = expandedGroupIds,
                collapsedSections = collapsedSections,
                swipedGroupId = swipedGroupId,
                contentTopPadding = contentTopPadding,
                listBottomPadding = listBottomPaddingDp,
                onToggleSection = onToggleSection,
                onToggleGroupExpanded = onToggleGroupExpanded,
                onSwipedGroupChange = onSwipedGroupChange,
                onGroupSelectionToggle = onGroupSelectionToggle,
                onGroupDelete = onGroupDelete,
                onGroupPause = onGroupPause,
                onGroupResume = onGroupResume,
                onTaskPauseResume = onTaskPauseResume,
                onTaskRetry = onTaskRetry,
                onTaskDelete = onTaskDelete,
                onTaskClick = onTaskClick,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (emptyStateVisible) {
            DownloadsEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopPadding, bottom = listBottomPaddingDp),
            )
        }

        AnimatedVisibility(
            visible = selectionMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            // 面板内容会自行执行高度动画。这里若再使用从底部展开的尺寸动画，首次快速全选时
            // 两层裁剪边界会短暂不同步，横向截断刚变高的内容。
            enter =
                fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                    slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = motionScheme.defaultSpatialSpec(),
                    ),
            exit =
                fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    slideOutVertically(
                        targetOffsetY = { it / 2 },
                        animationSpec = motionScheme.fastSpatialSpec(),
                    ),
        ) {
            DownloadsBatchGlassPanel(
                modifier = Modifier,
                backdrop = backdrop,
                statusText = batchStatusText,
                selectAllText = batchSelectAllText,
                hintHtml = batchHintHtml,
                clearEnabled = batchClearEnabled,
                deleteEnabled = batchDeleteEnabled,
                bottomPadding = panelBottomPadding,
                glassStyle = downloadsGlassStyle,
                onExitSelection = onExitSelection,
                onSelectAll = onSelectAll,
                onClearRecords = onClearRecords,
                onDeleteFiles = onDeleteFiles,
                onHeightChanged = { panelHeightPx = it },
            )
        }

        AnimatedVisibility(
            visible = !selectionMode,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter =
                fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleIn(
                        initialScale = 0.84f,
                        animationSpec = motionScheme.defaultSpatialSpec(),
                    ),
            exit =
                fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleOut(
                        targetScale = 0.84f,
                        animationSpec = motionScheme.fastSpatialSpec(),
                    ),
        ) {
            DownloadsManageFab(
                modifier = Modifier,
                bottomPadding = controlsBottomPadding,
                resumeAllCount = resumeAllCount,
                pauseAllCount = pauseAllCount,
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

@Composable
private fun DownloadsEmptyState(modifier: Modifier = Modifier) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.empty),
            contentDescription = null,
            modifier = Modifier.size(280.dp),
        )
        BasicText(
            text = stringResource(R.string.downloads_empty),
            modifier = Modifier.padding(top = 16.dp),
            style = TextStyle(color = textColor, fontSize = 17.sp),
        )
    }
}

@Composable
private fun DownloadsDeleteDialog(
    dialogState: DownloadsDialogState?,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    val state = when (dialogState) {
        is DownloadsDialogState.DeleteTask,
        is DownloadsDialogState.DeleteGroup,
        is DownloadsDialogState.BatchDelete,
        -> dialogState

        null,
        -> return
    }
    val haptics = rememberAppHaptics()
    var deleteFileChecked by remember(state) { mutableStateOf(true) }
    val title = when (state) {
        is DownloadsDialogState.DeleteTask -> stringResource(R.string.download_delete)
        is DownloadsDialogState.DeleteGroup -> stringResource(R.string.downloads_group_delete)
        is DownloadsDialogState.BatchDelete -> stringResource(
            if (state.deleteFile) {
                R.string.downloads_multi_confirm_delete_title
            } else {
                R.string.downloads_multi_confirm_clear_title
            },
        )
    }
    val message = when (state) {
        is DownloadsDialogState.DeleteTask -> AnnotatedString(
            stringResource(R.string.download_delete_confirm_task),
        )
        is DownloadsDialogState.DeleteGroup -> AnnotatedString(
            stringResource(R.string.download_delete_confirm_group),
        )
        is DownloadsDialogState.BatchDelete -> htmlToAnnotatedString(
            stringResource(
                if (state.deleteFile) {
                    R.string.downloads_multi_confirm_delete_message
                } else {
                    R.string.downloads_multi_confirm_clear_message
                },
                state.groupIds.size,
            ),
        )
    }
    val showCheckbox = when (state) {
        is DownloadsDialogState.DeleteTask -> state.canDeleteFile
        is DownloadsDialogState.DeleteGroup -> state.canDeleteFile
        is DownloadsDialogState.BatchDelete -> false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = message)
                if (showCheckbox) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { deleteFileChecked = !deleteFileChecked }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteFileChecked,
                            onCheckedChange = { checked -> deleteFileChecked = checked },
                            colors = AppAccents.checkboxColors(),
                        )
                        Text(
                            text = stringResource(R.string.download_delete_with_file),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.confirm()
                    val confirmDeleteFile = if (showCheckbox) deleteFileChecked else true
                    onConfirm(confirmDeleteFile)
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    text = stringResource(R.string.download_delete),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsManageFab(
    modifier: Modifier = Modifier,
    bottomPadding: Dp,
    resumeAllCount: Int,
    pauseAllCount: Int,
    onBatchManage: () -> Unit,
    onResumeAll: () -> Unit,
    onPauseAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearAll: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    var expanded by remember { mutableStateOf(false) }
    // 图标的 tint 必须显式给：ToggleFloatingActionButton 只做了加阴影、画容器、调 content()
    // 三件事，既不提供 LocalContentColor，也不会自动套 animateIcon，不写 tint 的 Icon 会
    // 回落到 Compose 库默认的纯黑，与配色方案彻底脱钩。
    val menuButtonContainerColor = AppAccents.floatingActionContainer
    val menuButtonContentColor = AppAccents.onFloatingActionContainer
    // 展开的操作项浅色模式使用更轻的 fixed 容器，深色模式保持原样；
    // 右下角主菜单按钮继续保留独立的 FAB 配色。
    val menuItemContainerColor = AppAccents.floatingActionMenuItemContainer
    val menuItemContentColor = AppAccents.onFloatingActionMenuItemContainer

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { next ->
                    haptics.toggle(next)
                    expanded = next
                },
                containerColor = { menuButtonContainerColor },
            ) {
                val imageVector = if (checkedProgress > 0.5f) {
                    R.drawable.ic_close_rounded_24
                } else {
                    R.drawable.ic_menu_24
                }
                Icon(
                    painter = painterResource(imageVector),
                    contentDescription = stringResource(R.string.downloads_actions_menu),
                    tint = menuButtonContentColor,
                )
            }
        },
        modifier = modifier
            .padding(bottom = FloatingControlsDefaults.menuFabBottomPadding(bottomPadding)),
    ) {
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; haptics.confirm(); onClearAll() },
            icon = { Icon(painter = painterResource(R.drawable.ic_delete_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_clear_all)) },
            containerColor = menuItemContainerColor,
            contentColor = menuItemContentColor,
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; haptics.confirm(); onClearCompleted() },
            icon = { Icon(painter = painterResource(R.drawable.ic_delete_sweep_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_clear_completed)) },
            containerColor = menuItemContainerColor,
            contentColor = menuItemContentColor,
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; haptics.confirm(); onPauseAll() },
            icon = { Icon(painter = painterResource(R.drawable.ic_pause_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_pause_all_with_count, pauseAllCount)) },
            containerColor = menuItemContainerColor,
            contentColor = menuItemContentColor,
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; haptics.confirm(); onResumeAll() },
            icon = { Icon(painter = painterResource(R.drawable.ic_play_arrow_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_resume_all_with_count, resumeAllCount)) },
            containerColor = menuItemContainerColor,
            contentColor = menuItemContentColor,
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; haptics.confirm(); onBatchManage() },
            icon = { Icon(painter = painterResource(R.drawable.ic_checklist_rounded_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_multi_manage)) },
            containerColor = menuItemContainerColor,
            contentColor = menuItemContentColor,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsBatchGlassPanel(
    modifier: Modifier = Modifier,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    statusText: String,
    selectAllText: String,
    hintHtml: String,
    clearEnabled: Boolean,
    deleteEnabled: Boolean,
    bottomPadding: Dp,
    glassStyle: DownloadsGlassStyle,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onClearRecords: () -> Unit,
    onDeleteFiles: () -> Unit,
    onHeightChanged: (Int) -> Unit,
) {
    val panelTextColor = MaterialTheme.colorScheme.onSurface
    val actionTextColor = MaterialTheme.colorScheme.primary
    val panelSubTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomPadding)
            .blockTouchThrough()
            .onSizeChanged { onHeightChanged(it.height) }
            .downloadsGlassSurface(backdrop = backdrop, style = glassStyle)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = statusText,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    color = panelTextColor,
                    fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ),
            )
            BatchTextAction(text = selectAllText, color = actionTextColor, onClick = onSelectAll)
            BatchTextAction(
                text = stringResource(R.string.downloads_multi_exit),
                color = actionTextColor,
                onClick = onExitSelection,
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                TextView(context).apply {
                    textSize = 12.5f
                    setLineSpacing(0f, 1.2f)
                }
            },
            update = { textView ->
                textView.text = refineHintText(
                    HtmlCompat.fromHtml(hintHtml, HtmlCompat.FROM_HTML_MODE_LEGACY),
                )
                textView.setTextColor(panelSubTextColor.toArgb())
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BatchActionButton(
                iconRes = R.drawable.ic_delete_sweep_24,
                text = stringResource(R.string.downloads_multi_clear_records),
                enabled = clearEnabled,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onClearRecords,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            BatchActionButton(
                iconRes = R.drawable.ic_delete_24,
                text = stringResource(R.string.downloads_multi_delete_files),
                enabled = deleteEnabled,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = onDeleteFiles,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
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
                text = "液态玻璃调试",
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    color = colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            DebugSmallButton(text = "收起", onClick = onToggleExpand)
        }

        Column(
            modifier = Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DebugSectionHeader(title = "下载页玻璃浮窗", onReset = onReset)
            DebugStepperRow(
                label = "圆角半径",
                value = "${formatFloat(cornerRadiusDp)} dp",
                onMinus = { onCornerRadiusChange((cornerRadiusDp - 1f).coerceIn(0f, 64f)) },
                onPlus = { onCornerRadiusChange((cornerRadiusDp + 1f).coerceIn(0f, 64f)) },
            )
            DebugStepperRow(
                label = "模糊半径",
                value = "${formatFloat(blurRadiusDp)} dp",
                onMinus = { onBlurRadiusChange((blurRadiusDp - 1f).coerceIn(0f, 48f)) },
                onPlus = { onBlurRadiusChange((blurRadiusDp + 1f).coerceIn(0f, 48f)) },
            )
            DebugStepperRow(
                label = "折射高度",
                value = "${formatFloat(refractionHeightDp)} dp",
                onMinus = { onRefractionHeightChange((refractionHeightDp - 1f).coerceIn(0f, 72f)) },
                onPlus = { onRefractionHeightChange((refractionHeightDp + 1f).coerceIn(0f, 72f)) },
            )
            DebugStepperRow(
                label = "折射强度",
                value = formatFloat(refractionAmountFrac),
                onMinus = { onRefractionAmountChange((refractionAmountFrac - 0.05f).coerceIn(0f, 1f)) },
                onPlus = { onRefractionAmountChange((refractionAmountFrac + 0.05f).coerceIn(0f, 1f)) },
            )
            DebugStepperRow(
                label = "表层透明度",
                value = formatFloat(surfaceAlpha),
                onMinus = { onSurfaceAlphaChange((surfaceAlpha - 0.05f).coerceIn(0f, 1f)) },
                onPlus = { onSurfaceAlphaChange((surfaceAlpha + 0.05f).coerceIn(0f, 1f)) },
            )
            DebugToggleRow(
                label = "色差",
                checked = chromaticAberration,
                onToggle = onChromaticAberrationChange,
            )

            DebugSectionHeader(title = "底部导航栏", onReset = onBarReset)
            DebugStepperRow(
                label = "模糊半径",
                value = "${formatFloat(barBlurRadiusDp)} dp",
                onMinus = { onBarBlurRadiusChange((barBlurRadiusDp - 1f).coerceIn(0f, 48f)) },
                onPlus = { onBarBlurRadiusChange((barBlurRadiusDp + 1f).coerceIn(0f, 48f)) },
            )
            DebugStepperRow(
                label = "折射高度",
                value = "${formatFloat(barRefractionHeightDp)} dp",
                onMinus = { onBarRefractionHeightChange((barRefractionHeightDp - 1f).coerceIn(0f, 72f)) },
                onPlus = { onBarRefractionHeightChange((barRefractionHeightDp + 1f).coerceIn(0f, 72f)) },
            )
            DebugStepperRow(
                label = "折射强度",
                value = formatFloat(barRefractionAmountFrac),
                onMinus = { onBarRefractionAmountChange((barRefractionAmountFrac - 0.05f).coerceIn(0f, 1f)) },
                onPlus = { onBarRefractionAmountChange((barRefractionAmountFrac + 0.05f).coerceIn(0f, 1f)) },
            )
            DebugStepperRow(
                label = "表层透明度",
                value = formatFloat(barSurfaceAlpha),
                onMinus = { onBarSurfaceAlphaChange((barSurfaceAlpha - 0.05f).coerceIn(0f, 1f)) },
                onPlus = { onBarSurfaceAlphaChange((barSurfaceAlpha + 0.05f).coerceIn(0f, 1f)) },
            )
            DebugToggleRow(
                label = "色差",
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
        DebugSmallButton(text = "重置", onClick = onReset)
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
            text = if (checked) "开" else "关",
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

@Composable
private fun BatchTextAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = color,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BatchActionButton(
    iconRes: Int,
    text: String,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val resolvedContainerColor by animateColorAsState(
        targetValue = if (enabled) containerColor else containerColor.copy(alpha = 0.88f),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val resolvedContentColor by animateColorAsState(
        targetValue = if (enabled) contentColor else contentColor.copy(alpha = 0.62f),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(resolvedContainerColor)
            .clickable(enabled = enabled) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(resolvedContentColor),
            )
            BasicText(
                text = text,
                style = TextStyle(
                    color = resolvedContentColor,
                    fontSize = 13.5f.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                ),
            )
        }
    }
}

private fun refineHintText(spanned: CharSequence): CharSequence {
    val raw = spanned.toString()
    if (raw.isBlank()) return raw

    val normalized =
        raw.replace("\r\n", "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    return SpannableStringBuilder(normalized).apply {
        applyBoldToken("\u6e05\u9664\u8bb0\u5f55")
        applyBoldToken("\u5220\u9664\u6587\u4ef6")
    }
}

private fun SpannableStringBuilder.applyBoldToken(token: String) {
    var start = indexOf(token)
    while (start >= 0) {
        setSpan(
            MediumBoldSpan(),
            start,
            start + token.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        start = indexOf(token, start + token.length)
    }
}

private class MediumBoldSpan : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) {
        textPaint.isFakeBoldText = true
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        textPaint.isFakeBoldText = true
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
    return String.format(Locale.US, "%.2f", value)
}
