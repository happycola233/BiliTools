package com.happycola233.bilitools.ui.downloads

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import android.text.style.MetricAffectingSpan
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import com.google.android.material.color.MaterialColors
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import java.util.Locale
import kotlin.math.sign

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

    data class TaskActions(
        val itemId: Long,
        val title: String,
    ) : DownloadsDialogState
}

enum class DownloadsTaskAction {
    Open,
    Share,
}

private val downloadsTaskActionsOuterHorizontalPadding = 12.dp
private val downloadsTaskActionsContentHorizontalPadding = 24.dp
private val downloadsTaskActionsIconSlotWidth = 24.dp
private val downloadsTaskActionsIconTextSpacing = 12.dp

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
    controlsOffsetPx: Float,
    resumeAllCount: Int,
    pauseAllCount: Int,
    glassDebugEnabled: Boolean,
    glassCornerRadiusDp: Float,
    glassBlurRadiusDp: Float,
    glassRefractionHeightDp: Float,
    glassRefractionAmountFrac: Float,
    glassChromaticAberration: Boolean,
    glassSurfaceAlpha: Float,
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
    onTaskActionSelected: (DownloadsTaskAction) -> Unit,
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
    onTaskClick: (DownloadItem) -> Unit,
    onGlassCornerRadiusChange: (Float) -> Unit,
    onGlassBlurRadiusChange: (Float) -> Unit,
    onGlassRefractionHeightChange: (Float) -> Unit,
    onGlassRefractionAmountChange: (Float) -> Unit,
    onGlassChromaticAberrationChange: (Boolean) -> Unit,
    onGlassSurfaceAlphaChange: (Float) -> Unit,
    onGlassReset: () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val controlsBottomPadding = 56.dp
    val panelBottomPadding = controlsBottomPadding + 8.dp
    val controlsOffsetDp = with(density) { controlsOffsetPx.toDp() }
    var panelHeightPx by remember { mutableStateOf(0) }
    val baseBottomPaddingPx = with(density) { 88.dp.roundToPx() }
    val extraBottomPaddingPx =
        if (selectionMode) panelHeightPx + with(density) { 20.dp.roundToPx() } else 0
    val targetListBottomPaddingDp = with(density) { (baseBottomPaddingPx + extraBottomPaddingPx).toDp() }
    val motionScheme = MaterialTheme.motionScheme
    val listBottomPaddingDp by animateDpAsState(
        targetValue = targetListBottomPaddingDp,
        animationSpec = motionScheme.defaultSpatialSpec(),
    )
    var debugExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            DownloadsListContent(
                groups = groups,
                selectionMode = selectionMode,
                selectedGroupIds = selectedGroupIds,
                expandedGroupIds = expandedGroupIds,
                collapsedSections = collapsedSections,
                swipedGroupId = swipedGroupId,
                listBottomPadding = PaddingValues(bottom = listBottomPaddingDp),
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
                    .padding(bottom = listBottomPaddingDp),
            )
        }

        AnimatedVisibility(
            visible = selectionMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter =
                fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                    slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = motionScheme.defaultSpatialSpec(),
                    ) +
                    expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = motionScheme.defaultSpatialSpec(),
                    ),
            exit =
                fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    slideOutVertically(
                        targetOffsetY = { it / 2 },
                        animationSpec = motionScheme.fastSpatialSpec(),
                    ) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
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
                controlsOffset = controlsOffsetDp,
                bottomPadding = panelBottomPadding,
                debugOverrideEnabled = true,
                cornerRadiusDp = glassCornerRadiusDp,
                blurRadiusDp = glassBlurRadiusDp,
                refractionHeightDp = glassRefractionHeightDp,
                refractionAmountFrac = glassRefractionAmountFrac,
                chromaticAberration = glassChromaticAberration,
                surfaceAlpha = glassSurfaceAlpha,
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
                controlsOffset = controlsOffsetDp,
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
        DownloadsTaskActionsDialog(
            dialogState = dialogState,
            onDismiss = onDialogDismiss,
            onActionSelected = onTaskActionSelected,
        )

        if (glassDebugEnabled) {
            GlassDebugPanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp),
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
            )
        }
    }
}

@Composable
private fun DownloadsEmptyState(modifier: Modifier = Modifier) {
    val textColor = materialColor(
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        0xFF6B6F76.toInt(),
    )
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

        is DownloadsDialogState.TaskActions,
        null,
        -> return
    }
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
        is DownloadsDialogState.TaskActions -> return
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
        is DownloadsDialogState.TaskActions -> return
    }
    val showCheckbox = when (state) {
        is DownloadsDialogState.DeleteTask -> state.canDeleteFile
        is DownloadsDialogState.DeleteGroup -> state.canDeleteFile
        is DownloadsDialogState.BatchDelete -> false
        is DownloadsDialogState.TaskActions -> return
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
                            colors = CheckboxDefaults.colors(),
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

@Composable
private fun DownloadsTaskActionsDialog(
    dialogState: DownloadsDialogState?,
    onDismiss: () -> Unit,
    onActionSelected: (DownloadsTaskAction) -> Unit,
) {
    val state = dialogState as? DownloadsDialogState.TaskActions ?: return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = downloadsTaskActionsOuterHorizontalPadding,
                    end = downloadsTaskActionsOuterHorizontalPadding,
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
                        start = downloadsTaskActionsContentHorizontalPadding,
                        end = downloadsTaskActionsContentHorizontalPadding,
                        top = 12.dp,
                        bottom = 8.dp,
                    ),
                )
                DownloadsTaskActionRow(
                    iconRes = R.drawable.ic_open_in_new_24,
                    text = stringResource(R.string.download_action_open),
                    onClick = { onActionSelected(DownloadsTaskAction.Open) },
                )
                DownloadsTaskActionRow(
                    iconRes = R.drawable.ic_share_24,
                    text = stringResource(R.string.download_action_share),
                    onClick = { onActionSelected(DownloadsTaskAction.Share) },
                )
            }
        }
    }
}

@Composable
private fun DownloadsTaskActionRow(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
) {
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
                .clickable(onClick = onClick)
                .padding(
                    start = downloadsTaskActionsContentHorizontalPadding,
                    end = downloadsTaskActionsContentHorizontalPadding,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(downloadsTaskActionsIconSlotWidth),
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
                    .padding(start = downloadsTaskActionsIconTextSpacing)
                    .weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsManageFab(
    modifier: Modifier = Modifier,
    bottomPadding: Dp,
    controlsOffset: Dp,
    resumeAllCount: Int,
    pauseAllCount: Int,
    onBatchManage: () -> Unit,
    onResumeAll: () -> Unit,
    onPauseAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
            ) {
                val imageVector = if (checkedProgress > 0.5f) R.drawable.ic_close_24 else R.drawable.ic_menu_24
                Icon(
                    painter = painterResource(imageVector),
                    contentDescription = stringResource(R.string.downloads_actions_menu),
                )
            }
        },
        modifier = modifier
            .padding(bottom = (bottomPadding - 16.dp).coerceAtLeast(0.dp))
            .offset(y = controlsOffset),
    ) {
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onClearAll() },
            icon = { Icon(painter = painterResource(R.drawable.ic_delete_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_clear_all)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onClearCompleted() },
            icon = { Icon(painter = painterResource(R.drawable.ic_delete_sweep_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_clear_completed)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onPauseAll() },
            icon = { Icon(painter = painterResource(R.drawable.ic_pause_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_pause_all_with_count, pauseAllCount)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onResumeAll() },
            icon = { Icon(painter = painterResource(R.drawable.ic_play_arrow_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_resume_all_with_count, resumeAllCount)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onBatchManage() },
            icon = { Icon(painter = painterResource(R.drawable.ic_checklist_24), contentDescription = null) },
            text = { Text(text = stringResource(R.string.downloads_multi_manage)) },
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
    controlsOffset: Dp,
    bottomPadding: Dp,
    debugOverrideEnabled: Boolean,
    cornerRadiusDp: Float,
    blurRadiusDp: Float,
    refractionHeightDp: Float,
    refractionAmountFrac: Float,
    chromaticAberration: Boolean,
    surfaceAlpha: Float,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onClearRecords: () -> Unit,
    onDeleteFiles: () -> Unit,
    onHeightChanged: (Int) -> Unit,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val luminance = if (isLightTheme) 0.58f else 0.42f
    val effectiveSurfaceAlpha = surfaceAlpha.coerceIn(0f, 1f)
    val surfaceOverlayColor = if (isLightTheme) Color.White else Color.Black

    val panelTextColor = materialColor(
        com.google.android.material.R.attr.colorOnSurface,
        0xFF101418.toInt(),
    )
    val actionTextColor = Color(0xFF6A688F)
    val panelSubTextColor = materialColor(
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        0xFF58616B.toInt(),
    )

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomPadding)
            .offset(y = controlsOffset)
            .blockTouchThrough()
            .onSizeChanged { onHeightChanged(it.height) }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(cornerRadiusDp.dp) },
                effects = {
                    val l = (luminance * 2f - 1f).let { sign(it) * it * it }
                    colorControls(
                        brightness =
                            if (l > 0f) lerp(0.1f, 0.5f, l)
                            else lerp(0.1f, -0.2f, -l),
                        contrast =
                            if (l > 0f) lerp(1f, 0f, l)
                            else 1f,
                        saturation = 1.5f,
                    )
                    val adaptiveBlurPx =
                        if (l > 0f) lerp(8.dp.toPx(), 16.dp.toPx(), l)
                        else lerp(8.dp.toPx(), 2.dp.toPx(), -l)
                    val activeBlurPx =
                        if (debugOverrideEnabled) blurRadiusDp.dp.toPx()
                        else adaptiveBlurPx
                    blur(activeBlurPx)
                    lens(
                        refractionHeightDp.dp.toPx(),
                        size.minDimension * refractionAmountFrac.coerceIn(0f, 1f),
                        depthEffect = true,
                        chromaticAberration = chromaticAberration,
                    )
                },
                highlight = { Highlight.Plain },
                onDrawSurface = {
                    drawRect(surfaceOverlayColor.copy(alpha = effectiveSurfaceAlpha))
                },
            )
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
                containerColor = Color(0xFFE8E8F8),
                contentColor = Color(0xFF4A4C5C),
                onClick = onClearRecords,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            BatchActionButton(
                iconRes = R.drawable.ic_delete_24,
                text = stringResource(R.string.downloads_multi_delete_files),
                enabled = deleteEnabled,
                containerColor = Color(0xFFF2D8D8),
                contentColor = Color(0xFF8C2727),
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
) {
    val surfaceAlphaLabel = "\u8868\u5c42\u900f\u660e\u5ea6"

    if (!expanded) {
        Box(
            modifier = modifier
                .blockTouchThrough()
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xD9FFFFFF))
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "DBG",
                style = TextStyle(color = Color(0xFF303548), fontSize = 11.sp),
            )
        }
        return
    }

    Column(
        modifier = modifier
            .blockTouchThrough()
            .width(260.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE6FFFFFF))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "液态玻璃调试窗口",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = Color(0xFF1E2433), fontSize = 12.sp),
            )
            DebugSmallButton(text = "\u6536\u8d77", onClick = onToggleExpand)
        }

        DebugStepperRow(
            label = "\u5706\u89d2\u534a\u5f84",
            value = "${formatFloat(cornerRadiusDp)} dp",
            onMinus = { onCornerRadiusChange((cornerRadiusDp - 1f).coerceIn(0f, 64f)) },
            onPlus = { onCornerRadiusChange((cornerRadiusDp + 1f).coerceIn(0f, 64f)) },
        )
        DebugStepperRow(
            label = "\u6a21\u7cca\u534a\u5f84",
            value = "${formatFloat(blurRadiusDp)} dp",
            onMinus = { onBlurRadiusChange((blurRadiusDp - 1f).coerceIn(0f, 48f)) },
            onPlus = { onBlurRadiusChange((blurRadiusDp + 1f).coerceIn(0f, 48f)) },
        )
        DebugStepperRow(
            label = "\u6298\u5c04\u9ad8\u5ea6",
            value = "${formatFloat(refractionHeightDp)} dp",
            onMinus = { onRefractionHeightChange((refractionHeightDp - 1f).coerceIn(0f, 72f)) },
            onPlus = { onRefractionHeightChange((refractionHeightDp + 1f).coerceIn(0f, 72f)) },
        )
        DebugStepperRow(
            label = "\u6298\u5c04\u5f3a\u5ea6",
            value = formatFloat(refractionAmountFrac),
            onMinus = { onRefractionAmountChange((refractionAmountFrac - 0.05f).coerceIn(0f, 1f)) },
            onPlus = { onRefractionAmountChange((refractionAmountFrac + 0.05f).coerceIn(0f, 1f)) },
        )
        DebugStepperRow(
            label = surfaceAlphaLabel,
            value = formatFloat(surfaceAlpha),
            onMinus = { onSurfaceAlphaChange((surfaceAlpha - 0.05f).coerceIn(0f, 1f)) },
            onPlus = { onSurfaceAlphaChange((surfaceAlpha + 0.05f).coerceIn(0f, 1f)) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x0D000000))
                .clickable { onChromaticAberrationChange(!chromaticAberration) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "\u8272\u5dee",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = Color(0xFF3A4050), fontSize = 12.sp),
            )
            BasicText(
                text = if (chromaticAberration) "\u5f00" else "\u5173",
                style = TextStyle(
                    color = if (chromaticAberration) Color(0xFF7E1D1D) else Color(0xFF4C5366),
                    fontSize = 12.sp,
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DebugActionButton(
                text = "\u91cd\u7f6e",
                modifier = Modifier.weight(1f),
                onClick = onReset,
            )
            DebugActionButton(
                text = "\u5173\u95ed",
                modifier = Modifier.weight(1f),
                onClick = onToggleExpand,
            )
        }
    }
}

@Composable
private fun DebugStepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x0D000000))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = Color(0xFF3A4050), fontSize = 12.sp),
        )
        DebugSmallButton(text = "-", onClick = onMinus)
        BasicText(
            text = value,
            modifier = Modifier.padding(horizontal = 6.dp),
            style = TextStyle(color = Color(0xFF1F2433), fontSize = 12.sp),
        )
        DebugSmallButton(text = "+", onClick = onPlus)
    }
}

@Composable
private fun DebugSmallButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x14000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = Color(0xFF2F3545), fontSize = 12.sp),
        )
    }
}

@Composable
private fun DebugActionButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x16000000))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = Color(0xFF2F3545), fontSize = 12.sp),
        )
    }
}

@Composable
private fun BatchTextAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
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
            .clickable(enabled = enabled, onClick = onClick),
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

private fun Modifier.blockTouchThrough(): Modifier {
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                event.changes.forEach { it.consume() }
            }
        }
    }
}

@Composable
private fun materialColor(
    @AttrRes attr: Int,
    @ColorInt fallback: Int,
): Color {
    val view = LocalView.current
    return remember(view, attr, fallback) {
        Color(MaterialColors.getColor(view, attr, fallback))
    }
}
