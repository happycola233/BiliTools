package com.happycola233.bilitools.ui.parse

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.ui.FloatingControlsDefaults
import kotlinx.coroutines.launch

private val screenHorizontalPadding = 16.dp
private val cardCornerRadius = 24.dp
private val controlCornerRadius = 18.dp
private val inputCardHorizontalPadding = 20.dp
private val inputCardVerticalPadding = 20.dp
private val inputCardTitleBottomSpacing = 16.dp
private val inputCardControlSpacing = 14.dp
private val inputSearchBarHeight = 54.dp
private val inputActionButtonHeight = 52.dp
private val optionsCardHorizontalPadding = 16.dp
private val optionsCardVerticalPadding = 18.dp
private val optionsCardTitleBottomSpacing = 18.dp
private val optionsCardSectionSpacing = 12.dp
private val compactSelectionHeight = 58.dp
private val checkOptionMinHeight = 48.dp
private val actionButtonLoadingIndicatorSize = 26.dp
private val imageOptionChipHeight = 36.dp
private val pageSelectionHeaderActionHeight = 32.dp
private val pageSelectionItemSpacing = 10.dp
private val pageSelectionListMaxHeight = 286.dp
private val pageSelectionScrollbarContentInset = 10.dp
private val pageSelectionScrollbarTouchWidth = 18.dp
private val pageSelectionScrollbarVerticalInset = 6.dp
private val pageSelectionScrollbarTrackWidth = 2.dp
private val pageSelectionScrollbarTrackWidthActive = 6.dp
private val pageSelectionScrollbarThumbWidth = 4.dp
private val pageSelectionScrollbarThumbWidthActive = 8.dp
private val pageSelectionScrollbarMinThumbHeight = 48.dp
private const val pageSelectionNaturalItemLimit = 4
private val copyDialogCornerRadius = 32.dp
private val copyDialogHorizontalMargin = 28.dp
private val copyDialogContentPadding = 24.dp
private val copyDialogMaxHeight = 720.dp
private val copyDialogHeaderIconSize = 42.dp
private val copyDialogHeaderIconCornerRadius = 16.dp
private val copyDialogSectionSpacing = 16.dp
private val copyDialogPreviewHeight = 340.dp
private val copyDialogPreviewMinHeight = 220.dp
private val copyDialogPreviewCornerRadius = 24.dp
private val copyDialogPreviewScrollbarTouchWidth = 18.dp
private val copyDialogPreviewScrollbarTrackWidth = 2.dp
private val copyDialogPreviewScrollbarTrackWidthActive = 6.dp
private val copyDialogPreviewScrollbarThumbWidth = 4.dp
private val copyDialogPreviewScrollbarThumbWidthActive = 9.dp
private val copyDialogPreviewScrollbarMinThumbHeight = 52.dp
private const val optionsVisibilityAnimationDurationMillis = 180
private const val optionsValueAnimationDurationMillis = 120

private object ParseTextStyles {
    val cardTitle: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )

    val mediaTitle: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
        )

    val mediaDescription: TextStyle
        @Composable
        get() = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

    val body: TextStyle
        @Composable
        get() = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

    val compactBody: TextStyle
        @Composable
        get() = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )

    val supporting: TextStyle
        @Composable
        get() = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

    val sectionLabel: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleSmall.copy(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

    val controlLabel: TextStyle
        @Composable
        get() = MaterialTheme.typography.labelSmall.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

    val controlValue: TextStyle
        @Composable
        get() = MaterialTheme.typography.labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        )

    val buttonLabel: TextStyle
        @Composable
        get() = MaterialTheme.typography.labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun ParseScreenContent(
    state: ParseUiState,
    inputText: String,
    controlsOffsetPx: Float,
    externalMode: Boolean,
    subtitleCopyDialogEntries: List<SubtitleCopyEntry>?,
    aiSummaryCopyDialogEntries: List<AiSummaryCopyEntry>?,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onParse: (String) -> Unit,
    onMediaTypeChange: (MediaType?) -> Unit,
    onDownload: () -> Unit,
    onSectionChange: (Long) -> Unit,
    onSelectAllItems: () -> Unit,
    onClearSelectedItems: () -> Unit,
    onLoadPrevPage: () -> Unit,
    onLoadNextPage: () -> Unit,
    onLoadPage: (Int) -> Unit,
    onItemClick: (Int) -> Unit,
    onItemSelectionChange: (Int, Boolean) -> Unit,
    onFormatChange: (StreamFormat) -> Unit,
    onOutputTypeChange: (OutputType?) -> Unit,
    onCollectionModeChange: (Boolean) -> Unit,
    onResolutionModeChange: (QualityMode) -> Unit,
    onResolutionChange: (Int) -> Unit,
    onCodecChange: (VideoCodec) -> Unit,
    onAudioBitrateModeChange: (QualityMode) -> Unit,
    onAudioBitrateChange: (Int) -> Unit,
    onSubtitleEnabledChange: (Boolean) -> Unit,
    onSubtitleLanguageChange: (String) -> Unit,
    onCopySubtitles: () -> Unit,
    onAiSummaryEnabledChange: (Boolean) -> Unit,
    onCopyAiSummaries: () -> Unit,
    onNfoCollectionEnabledChange: (Boolean) -> Unit,
    onNfoSingleEnabledChange: (Boolean) -> Unit,
    onDanmakuLiveEnabledChange: (Boolean) -> Unit,
    onDanmakuHistoryEnabledChange: (Boolean) -> Unit,
    onDanmakuDateChange: (String) -> Unit,
    onDanmakuHourChange: (String) -> Unit,
    onImageSelectionChange: (String, Boolean) -> Unit,
    onDismissSubtitleCopyDialog: () -> Unit,
    onDismissAiSummaryCopyDialog: () -> Unit,
    onCopyCurrentSubtitle: (SubtitleCopyEntry) -> Unit,
    onCopyAllSubtitles: (List<SubtitleCopyEntry>) -> Unit,
    onCopyCurrentAiSummary: (AiSummaryCopyEntry) -> Unit,
    onCopyAllAiSummaries: (List<AiSummaryCopyEntry>) -> Unit,
) {
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val info = state.mediaInfo
    val item = state.items.getOrNull(state.selectedItemIndex)
    val totalItems = state.items.size
    val showPageModule = totalItems > 1 || info?.paged == true
    val showOptions = info != null && state.playUrlInfo != null

    val hasSelection = state.selectedItemIndices.isNotEmpty()
    val streamReady = state.outputType == null || state.playUrlInfo != null
    val downloadEnabled = !state.loading &&
        !state.downloadStarting &&
        hasSelection &&
        streamReady
    val quickActionVisible = info != null && hasSelection

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollInterop),
            contentPadding = PaddingValues(
                start = screenHorizontalPadding,
                top = 12.dp,
                end = screenHorizontalPadding,
                bottom = if (!externalMode || info != null) 96.dp else 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "input") {
                ParseInputCard(
                    inputText = inputText,
                    loading = state.loading,
                    selectedMediaType = state.selectedMediaType,
                    onInputChange = onInputChange,
                    onPaste = onPaste,
                    onParse = onParse,
                    onMediaTypeChange = onMediaTypeChange,
                )
            }

            if (!state.error.isNullOrBlank()) {
                item(key = "error") {
                    MessageCard(
                        message = state.error.orEmpty(),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            if (info != null) {
                item(key = "video") {
                    ParseResultCard(
                        state = state,
                        info = info,
                        selectedItem = item,
                        onSectionChange = onSectionChange,
                        onCollectionModeChange = onCollectionModeChange,
                    ) {
                        if (showPageModule) {
                            SectionDivider()
                            PageSelectionSection(
                                state = state,
                                info = info,
                                selectedCount = state.selectedItemIndices.size,
                                totalItems = totalItems,
                                onSelectAllItems = onSelectAllItems,
                                onClearSelectedItems = onClearSelectedItems,
                                onLoadPrevPage = onLoadPrevPage,
                                onLoadNextPage = onLoadNextPage,
                                onLoadPage = onLoadPage,
                                onItemClick = onItemClick,
                                onItemSelectionChange = onItemSelectionChange,
                            )
                        }
                    }
                }

                if (!state.isLoggedIn) {
                    item(key = "login-hint") {
                        MessageCard(
                            message = stringResource(R.string.parse_login_limited),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (showOptions) {
                    item(key = "options") {
                        ParseOptionsCard(
                            state = state,
                            info = info,
                            selectedItem = item,
                            onFormatChange = onFormatChange,
                            onOutputTypeChange = onOutputTypeChange,
                            onResolutionModeChange = onResolutionModeChange,
                            onResolutionChange = onResolutionChange,
                            onCodecChange = onCodecChange,
                            onAudioBitrateModeChange = onAudioBitrateModeChange,
                            onAudioBitrateChange = onAudioBitrateChange,
                            onSubtitleEnabledChange = onSubtitleEnabledChange,
                            onSubtitleLanguageChange = onSubtitleLanguageChange,
                            onCopySubtitles = onCopySubtitles,
                            onAiSummaryEnabledChange = onAiSummaryEnabledChange,
                            onCopyAiSummaries = onCopyAiSummaries,
                            onNfoCollectionEnabledChange = onNfoCollectionEnabledChange,
                            onNfoSingleEnabledChange = onNfoSingleEnabledChange,
                            onDanmakuLiveEnabledChange = onDanmakuLiveEnabledChange,
                            onDanmakuHistoryEnabledChange = onDanmakuHistoryEnabledChange,
                            onDanmakuDateChange = onDanmakuDateChange,
                            onDanmakuHourChange = onDanmakuHourChange,
                            onImageSelectionChange = onImageSelectionChange,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = quickActionVisible,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            QuickActionFab(
                enabled = downloadEnabled,
                controlsOffsetPx = controlsOffsetPx,
                onDownload = onDownload,
            )
        }

        subtitleCopyDialogEntries?.let { entries ->
            SubtitleCopyPreviewDialog(
                entries = entries,
                onDismiss = onDismissSubtitleCopyDialog,
                onCopyCurrent = onCopyCurrentSubtitle,
                onCopyAll = onCopyAllSubtitles,
            )
        }

        aiSummaryCopyDialogEntries?.let { entries ->
            AiSummaryCopyPreviewDialog(
                entries = entries,
                onDismiss = onDismissAiSummaryCopyDialog,
                onCopyCurrent = onCopyCurrentAiSummary,
                onCopyAll = onCopyAllAiSummaries,
            )
        }
    }
}

@Composable
private fun SubtitleCopyPreviewDialog(
    entries: List<SubtitleCopyEntry>,
    onDismiss: () -> Unit,
    onCopyCurrent: (SubtitleCopyEntry) -> Unit,
    onCopyAll: (List<SubtitleCopyEntry>) -> Unit,
) {
    val context = LocalContext.current
    CopyPreviewDialog(
        title = stringResource(R.string.parse_subtitle_copy_dialog_title),
        hint = stringResource(R.string.parse_subtitle_copy_dialog_hint),
        itemLabel = stringResource(R.string.parse_subtitle_copy_item_label),
        currentActionText = stringResource(R.string.parse_subtitle_copy_current),
        allActionText = stringResource(R.string.parse_subtitle_copy_all),
        headerIconRes = R.drawable.ic_checklist_24,
        entries = entries,
        previewUsesMonospace = true,
        hasContent = { entry -> !entry.content.isNullOrBlank() },
        labelForEntry = { entry ->
            val subtitlePart = entry.subtitleName
                ?.takeIf { it.isNotBlank() }
                ?.let { " · $it" }
                .orEmpty()
            val base = "${entry.title}$subtitlePart"
            if (entry.content.isNullOrBlank()) {
                context.getString(R.string.parse_subtitle_copy_item_unavailable, base)
            } else {
                base
            }
        },
        statusForEntry = { entry ->
            if (!entry.content.isNullOrBlank()) {
                val subtitleName = entry.subtitleName
                    ?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.parse_subtitle_label)
                context.getString(R.string.parse_subtitle_copy_status_ready, subtitleName)
            } else {
                entry.error ?: context.getString(R.string.parse_subtitle_copy_unavailable)
            }
        },
        previewForEntry = { entry ->
            entry.content?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.parse_subtitle_copy_preview_empty)
        },
        onDismiss = onDismiss,
        onCopyCurrent = onCopyCurrent,
        onCopyAll = onCopyAll,
    )
}

@Composable
private fun AiSummaryCopyPreviewDialog(
    entries: List<AiSummaryCopyEntry>,
    onDismiss: () -> Unit,
    onCopyCurrent: (AiSummaryCopyEntry) -> Unit,
    onCopyAll: (List<AiSummaryCopyEntry>) -> Unit,
) {
    val context = LocalContext.current
    CopyPreviewDialog(
        title = stringResource(R.string.parse_ai_summary_copy_dialog_title),
        hint = stringResource(R.string.parse_ai_summary_copy_dialog_hint),
        itemLabel = stringResource(R.string.parse_ai_summary_copy_item_label),
        currentActionText = stringResource(R.string.parse_ai_summary_copy_current),
        allActionText = stringResource(R.string.parse_ai_summary_copy_all),
        headerIconRes = R.drawable.ic_wand_shine_24,
        entries = entries,
        previewUsesMonospace = false,
        hasContent = { entry -> !entry.content.isNullOrBlank() },
        labelForEntry = { entry ->
            if (entry.content.isNullOrBlank()) {
                context.getString(R.string.parse_ai_summary_copy_item_unavailable, entry.title)
            } else {
                entry.title
            }
        },
        statusForEntry = { entry ->
            if (!entry.content.isNullOrBlank()) {
                context.getString(R.string.parse_ai_summary_copy_status_ready)
            } else {
                entry.error ?: context.getString(R.string.parse_ai_summary_copy_unavailable)
            }
        },
        previewForEntry = { entry ->
            entry.content?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.parse_ai_summary_copy_preview_empty)
        },
        onDismiss = onDismiss,
        onCopyCurrent = onCopyCurrent,
        onCopyAll = onCopyAll,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> CopyPreviewDialog(
    title: String,
    hint: String,
    itemLabel: String,
    currentActionText: String,
    allActionText: String,
    headerIconRes: Int,
    entries: List<T>,
    previewUsesMonospace: Boolean,
    hasContent: (T) -> Boolean,
    labelForEntry: (T) -> String,
    statusForEntry: (T) -> String,
    previewForEntry: (T) -> String,
    onDismiss: () -> Unit,
    onCopyCurrent: (T) -> Unit,
    onCopyAll: (List<T>) -> Unit,
) {
    if (entries.isEmpty()) return

    val initialIndex = remember(entries) { entries.indexOfFirst(hasContent).takeIf { it >= 0 } ?: 0 }
    var selectedIndex by remember(entries) { mutableStateOf(initialIndex) }
    val safeSelectedIndex = selectedIndex.coerceIn(entries.indices)
    val selectedEntry = entries[safeSelectedIndex]
    val selectedHasContent = hasContent(selectedEntry)
    val labels = entries.map(labelForEntry)
    val hasAnyContent = entries.any(hasContent)
    val motionScheme = MaterialTheme.motionScheme
    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    LaunchedEffect(entries, selectedIndex) {
        if (selectedIndex !in entries.indices) {
            selectedIndex = initialIndex
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter =
                fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = motionScheme.defaultSpatialSpec(),
                    ),
            exit =
                fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleOut(
                        targetScale = 0.92f,
                        animationSpec = motionScheme.fastSpatialSpec(),
                    ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = copyDialogHorizontalMargin),
                shape = RoundedCornerShape(copyDialogCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = copyDialogMaxHeight)
                        .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec())
                        .padding(copyDialogContentPadding),
                    verticalArrangement = Arrangement.spacedBy(copyDialogSectionSpacing),
                ) {
                    CopyDialogHeader(
                        title = title,
                        hint = hint,
                        iconRes = headerIconRes,
                    )
                    CompactSelectionField(
                        label = itemLabel,
                        value = labels.getOrElse(safeSelectedIndex) { labels.firstOrNull().orEmpty() },
                        enabled = labels.isNotEmpty(),
                        options = labels.mapIndexed { index, label -> DropdownOption(label, index) },
                        onOptionSelected = { option -> selectedIndex = option.value },
                    )
                    CopyDialogStatusPill(
                        text = statusForEntry(selectedEntry),
                        available = selectedHasContent,
                    )
                    CopyDialogPreviewPanel(
                        entries = entries,
                        selectedIndex = safeSelectedIndex,
                        hasContent = hasContent,
                        previewForEntry = previewForEntry,
                        usesMonospace = previewUsesMonospace,
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .heightIn(
                                min = copyDialogPreviewMinHeight,
                                max = copyDialogPreviewHeight,
                            ),
                    )
                    ExpressiveActionButton(
                        text = currentActionText,
                        iconRes = R.drawable.ic_content_copy_24,
                        loading = false,
                        enabled = selectedHasContent,
                        onClick = { onCopyCurrent(selectedEntry) },
                        modifier = Modifier.fillMaxWidth(),
                        tonal = true,
                        height = 52.dp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(android.R.string.cancel),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        ExpressiveActionButton(
                            text = allActionText,
                            iconRes = R.drawable.ic_content_copy_24,
                            loading = false,
                            enabled = hasAnyContent,
                            onClick = {
                                onCopyAll(entries)
                                onDismiss()
                            },
                            tonal = true,
                            height = 48.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyDialogHeader(
    title: String,
    hint: String,
    iconRes: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(copyDialogHeaderIconSize),
                shape = RoundedCornerShape(copyDialogHeaderIconCornerRadius),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = hint,
            style = ParseTextStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CopyDialogStatusPill(
    text: String,
    available: Boolean,
) {
    val motionScheme = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = if (available) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = motionScheme.fastEffectsSpec(),
    )
    val contentColor by animateColorAsState(
        targetValue = if (available) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = motionScheme.fastEffectsSpec(),
    )

    AnimatedContent(
        targetState = text to available,
        transitionSpec = {
            fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                fadeOut(animationSpec = motionScheme.fastEffectsSpec())
        },
        label = "CopyDialogStatus",
    ) { (statusText, statusAvailable) ->
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (statusAvailable) R.drawable.ic_check_24 else R.drawable.ic_info_24,
                ),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = statusText,
                style = ParseTextStyles.supporting,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> CopyDialogPreviewPanel(
    entries: List<T>,
    selectedIndex: Int,
    hasContent: (T) -> Boolean,
    previewForEntry: (T) -> String,
    usesMonospace: Boolean,
    modifier: Modifier = Modifier.height(copyDialogPreviewHeight),
) {
    val scrollState = rememberScrollState()
    val motionScheme = MaterialTheme.motionScheme

    LaunchedEffect(entries, selectedIndex) {
        scrollState.scrollTo(0)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(copyDialogPreviewCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = copyDialogPreviewScrollbarTouchWidth)
                    .verticalScroll(scrollState),
            ) {
                AnimatedContent(
                    targetState = selectedIndex,
                    transitionSpec = {
                        fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                    },
                    label = "CopyDialogPreview",
                ) { index ->
                    val entry = entries[index.coerceIn(entries.indices)]
                    val entryHasContent = hasContent(entry)
                    SelectionContainer {
                        Text(
                            text = previewForEntry(entry),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
                            style = ParseTextStyles.body.copy(
                                fontFamily = if (usesMonospace) FontFamily.Monospace else null,
                                lineHeight = 22.sp,
                            ),
                            color = if (entryHasContent) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            CopyPreviewScrollbar(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CopyPreviewScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val minThumbHeightPx = with(density) { copyDialogPreviewScrollbarMinThumbHeight.toPx() }
    var trackHeightPx by remember { mutableStateOf(0) }
    var dragActive by remember { mutableStateOf(false) }
    val metrics = calculateCopyPreviewScrollbarMetrics(
        scrollValuePx = scrollState.value,
        maxScrollPx = scrollState.maxValue,
        trackHeightPx = trackHeightPx,
        minThumbHeightPx = minThumbHeightPx,
    )
    val trackWidth by animateDpAsState(
        targetValue = if (dragActive) {
            copyDialogPreviewScrollbarTrackWidthActive
        } else {
            copyDialogPreviewScrollbarTrackWidth
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (dragActive) {
            copyDialogPreviewScrollbarThumbWidthActive
        } else {
            copyDialogPreviewScrollbarThumbWidth
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbTopTarget = with(density) { metrics.thumbOffsetPx.toDp() }
    val thumbTopAnimated by animateDpAsState(
        targetValue = thumbTopTarget,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbTop = if (dragActive) thumbTopTarget else thumbTopAnimated
    val thumbHeight = with(density) { metrics.thumbHeightPx.toDp() }
    val trackColor by animateColorAsState(
        targetValue = if (dragActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val thumbColor by animateColorAsState(
        targetValue = if (dragActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(copyDialogPreviewScrollbarTouchWidth)
            .pointerInput(scrollState, trackHeightPx, minThumbHeightPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        val currentMetrics = calculateCopyPreviewScrollbarMetrics(
                            scrollValuePx = scrollState.value,
                            maxScrollPx = scrollState.maxValue,
                            trackHeightPx = trackHeightPx,
                            minThumbHeightPx = minThumbHeightPx,
                        )
                        if (currentMetrics.scrollable) {
                            dragActive = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = { dragActive = false },
                    onDragCancel = { dragActive = false },
                    onDrag = { change, dragAmount ->
                        val currentMetrics = calculateCopyPreviewScrollbarMetrics(
                            scrollValuePx = scrollState.value,
                            maxScrollPx = scrollState.maxValue,
                            trackHeightPx = trackHeightPx,
                            minThumbHeightPx = minThumbHeightPx,
                        )
                        if (currentMetrics.scrollable && currentMetrics.maxThumbOffsetPx > 0f) {
                            change.consume()
                            val scrollDelta = dragAmount.y *
                                (currentMetrics.maxScrollPx / currentMetrics.maxThumbOffsetPx)
                            coroutineScope.launch {
                                scrollState.scrollBy(scrollDelta)
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 10.dp)
                .onSizeChanged { size -> trackHeightPx = size.height },
            contentAlignment = Alignment.Center,
        ) {
            if (metrics.scrollable || dragActive) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(trackWidth),
                    shape = RoundedCornerShape(percent = 50),
                    color = trackColor,
                ) {}
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = thumbTop)
                        .width(thumbWidth)
                        .height(thumbHeight),
                    shape = RoundedCornerShape(percent = 50),
                    color = thumbColor,
                ) {}
            }
        }
    }
}

private data class CopyPreviewScrollbarMetrics(
    val scrollable: Boolean,
    val thumbHeightPx: Float,
    val thumbOffsetPx: Float,
    val maxThumbOffsetPx: Float,
    val maxScrollPx: Float,
) {
    companion object
}

private fun calculateCopyPreviewScrollbarMetrics(
    scrollValuePx: Int,
    maxScrollPx: Int,
    trackHeightPx: Int,
    minThumbHeightPx: Float,
): CopyPreviewScrollbarMetrics {
    if (trackHeightPx <= 0 || maxScrollPx <= 0) {
        return CopyPreviewScrollbarMetrics.empty(trackHeightPx.toFloat())
    }

    val trackHeight = trackHeightPx.toFloat()
    val maxScroll = maxScrollPx.toFloat()
    val contentHeight = trackHeight + maxScroll
    val thumbHeight = ((trackHeight * trackHeight) / contentHeight)
        .coerceIn(minThumbHeightPx.coerceAtMost(trackHeight), trackHeight)
    val maxThumbOffset = (trackHeight - thumbHeight).coerceAtLeast(0f)
    val scrollFraction = (scrollValuePx.toFloat() / maxScroll).coerceIn(0f, 1f)

    return CopyPreviewScrollbarMetrics(
        scrollable = maxThumbOffset > 0f,
        thumbHeightPx = thumbHeight,
        thumbOffsetPx = scrollFraction * maxThumbOffset,
        maxThumbOffsetPx = maxThumbOffset,
        maxScrollPx = maxScroll,
    )
}

private fun CopyPreviewScrollbarMetrics.Companion.empty(
    trackHeightPx: Float = 0f,
): CopyPreviewScrollbarMetrics {
    return CopyPreviewScrollbarMetrics(
        scrollable = false,
        thumbHeightPx = trackHeightPx,
        thumbOffsetPx = 0f,
        maxThumbOffsetPx = 0f,
        maxScrollPx = 0f,
    )
}

@Composable
private fun ParseInputCard(
    inputText: String,
    loading: Boolean,
    selectedMediaType: MediaType?,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onParse: (String) -> Unit,
    onMediaTypeChange: (MediaType?) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cardCornerRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = inputCardHorizontalPadding,
                vertical = inputCardVerticalPadding,
            ),
        ) {
            Text(
                text = stringResource(R.string.parse_input_card_title),
                style = ParseTextStyles.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(inputCardTitleBottomSpacing))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(inputCardControlSpacing),
            ) {
                SearchInputBar(
                    value = inputText,
                    onValueChange = onInputChange,
                    onPaste = onPaste,
                    onDone = {
                        focusManager.clearFocus()
                        onParse(inputText)
                    },
                )

                CompactSelectionField(
                    label = stringResource(R.string.parse_media_type_label),
                    value = mediaTypeOptions().firstOrNull { it.value == selectedMediaType }?.label.orEmpty(),
                    enabled = !loading,
                    options = mediaTypeOptions(),
                    onOptionSelected = { option -> onMediaTypeChange(option.value) },
                )

                ExpressiveActionButton(
                    text = stringResource(R.string.parse_action),
                    iconRes = R.drawable.ic_movie_filter_24,
                    loading = loading,
                    enabled = !loading,
                    onClick = {
                        focusManager.clearFocus()
                        onParse(inputText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = inputActionButtonHeight,
                )
            }
        }
    }
}

@Composable
private fun SearchInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onDone: () -> Unit,
) {
    val textStyle = ParseTextStyles.body.copy(color = MaterialTheme.colorScheme.onSurface)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(inputSearchBarHeight),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_search_24),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(22.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                singleLine = true,
                textStyle = textStyle,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = stringResource(R.string.parse_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = ParseTextStyles.body,
                        )
                    }
                    innerTextField()
                },
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            TextButton(
                onClick = onPaste,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Text(
                    text = stringResource(R.string.parse_paste),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ParseResultCard(
    state: ParseUiState,
    info: MediaInfo,
    selectedItem: MediaItem?,
    onSectionChange: (Long) -> Unit,
    onCollectionModeChange: (Boolean) -> Unit,
    contentBelowSectionControls: @Composable ColumnScope.() -> Unit,
) {
    val display = resolveVideoCardDisplay(state, info, selectedItem)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cardCornerRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            CoverImage(display.coverUrl)
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = display.title.ifBlank { stringResource(R.string.parse_section_result) },
                    style = ParseTextStyles.mediaTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (display.description.isNotBlank()) {
                    Text(
                        text = display.description,
                        style = ParseTextStyles.mediaDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatRow(display.stat)
                SectionControls(
                    state = state,
                    info = info,
                    onSectionChange = onSectionChange,
                    onCollectionModeChange = onCollectionModeChange,
                )
                contentBelowSectionControls()
            }
        }
    }
}

@Composable
private fun CoverImage(coverUrl: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(204.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = stringResource(R.string.parse_cover_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_hide_image_24),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun StatRow(stat: MediaStat) {
    val stats = listOfNotNull(
        stat.play?.let { StatDisplay(R.string.icon_stat_play, it) },
        stat.danmaku?.let { StatDisplay(R.string.icon_stat_danmaku, it) },
        stat.reply?.let { StatDisplay(R.string.icon_stat_reply, it) },
        stat.like?.let { StatDisplay(R.string.icon_stat_like, it) },
        stat.coin?.let { StatDisplay(R.string.icon_stat_coin, it) },
        stat.favorite?.let { StatDisplay(R.string.icon_stat_favorite, it) },
        stat.share?.let { StatDisplay(R.string.icon_stat_share, it) },
    )
    if (stats.isEmpty()) return

    val iconFont = remember { FontFamily(Font(R.font.bcc_iconfont)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(item.iconRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = iconFont,
                    maxLines = 1,
                    style = ParseTextStyles.supporting,
                )
                Text(
                    text = formatStat(item.value),
                    modifier = Modifier.padding(start = 5.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    style = ParseTextStyles.supporting,
                )
            }
        }
    }
}

@Composable
private fun SectionControls(
    state: ParseUiState,
    info: MediaInfo,
    onSectionChange: (Long) -> Unit,
    onCollectionModeChange: (Boolean) -> Unit,
) {
    val sections = state.sections
    val showSections = sections != null && sections.tabs.isNotEmpty() && !state.collectionMode
    val sectionLabel = when (info.type) {
        MediaType.Favorite -> stringResource(R.string.parse_section_label_favorite)
        MediaType.Bangumi -> stringResource(R.string.parse_section_label_bangumi)
        else -> stringResource(R.string.parse_section_label)
    }
    if (showSections) {
        DropdownField(
            label = sectionLabel,
            value = sections?.tabs
                ?.firstOrNull { it.id == state.selectedSectionId }
                ?.name
                .orEmpty(),
            enabled = !state.loading,
            options = sections?.tabs.orEmpty().map { DropdownOption(it.name, it) },
            onOptionSelected = { option -> onSectionChange(option.value.id) },
        )
    }

    val collectionModeAvailable = info.type == MediaType.Video && info.collection
    if (collectionModeAvailable) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.parse_collection_toggle),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = ParseTextStyles.sectionLabel,
                )
                Text(
                    text = stringResource(R.string.parse_collection_toggle_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = ParseTextStyles.supporting,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = state.collectionMode,
                onCheckedChange = onCollectionModeChange,
                enabled = !state.loading,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun PageSelectionSection(
    state: ParseUiState,
    info: MediaInfo,
    selectedCount: Int,
    totalItems: Int,
    onSelectAllItems: () -> Unit,
    onClearSelectedItems: () -> Unit,
    onLoadPrevPage: () -> Unit,
    onLoadNextPage: () -> Unit,
    onLoadPage: (Int) -> Unit,
    onItemClick: (Int) -> Unit,
    onItemSelectionChange: (Int, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(pageSelectionItemSpacing),
    ) {
        PageSelectionHeader(
            state = state,
            info = info,
            selectedCount = selectedCount,
            totalItems = totalItems,
            onSelectAllItems = onSelectAllItems,
            onClearSelectedItems = onClearSelectedItems,
            onLoadPrevPage = onLoadPrevPage,
            onLoadNextPage = onLoadNextPage,
            onLoadPage = onLoadPage,
        )
        PageSelectionList(
            state = state,
            info = info,
            onItemClick = onItemClick,
            onItemSelectionChange = onItemSelectionChange,
        )
    }
}

@Composable
private fun PageSelectionList(
    state: ParseUiState,
    info: MediaInfo,
    onItemClick: (Int) -> Unit,
    onItemSelectionChange: (Int, Boolean) -> Unit,
) {
    if (state.items.size <= pageSelectionNaturalItemLimit) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(pageSelectionItemSpacing),
        ) {
            state.items.forEachIndexed { index, mediaItem ->
                PageSelectionRow(
                    state = state,
                    info = info,
                    index = index,
                    mediaItem = mediaItem,
                    onItemClick = onItemClick,
                    onItemSelectionChange = onItemSelectionChange,
                )
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val boundaryConsumption = remember { ScrollBoundaryConsumption() }
    val boundaryConnection = rememberScrollBoundaryNestedScrollConnection(boundaryConsumption)
    val overscrollEffect = rememberBoundaryAwareOverscrollEffect(boundaryConsumption)

    LaunchedEffect(state.items) {
        listState.scrollToItem(0)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(pageSelectionListMaxHeight),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = pageSelectionScrollbarContentInset)
                .nestedScroll(boundaryConnection),
            state = listState,
            overscrollEffect = overscrollEffect,
            verticalArrangement = Arrangement.spacedBy(pageSelectionItemSpacing),
        ) {
            itemsIndexed(
                items = state.items,
                key = { index, mediaItem -> pageSelectionItemKey(index, mediaItem) },
            ) { index, mediaItem ->
                PageSelectionRow(
                    state = state,
                    info = info,
                    index = index,
                    mediaItem = mediaItem,
                    onItemClick = onItemClick,
                    onItemSelectionChange = onItemSelectionChange,
                )
            }
        }
        PageSelectionScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageSelectionScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val minThumbHeightPx = with(density) { pageSelectionScrollbarMinThumbHeight.toPx() }
    var trackHeightPx by remember { mutableStateOf(0) }
    var dragActive by remember { mutableStateOf(false) }
    val metrics = calculatePageSelectionScrollbarMetrics(
        layoutInfo = listState.layoutInfo,
        trackHeightPx = trackHeightPx,
        minThumbHeightPx = minThumbHeightPx,
    )

    val trackWidth by animateDpAsState(
        targetValue = if (dragActive) {
            pageSelectionScrollbarTrackWidthActive
        } else {
            pageSelectionScrollbarTrackWidth
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (dragActive) {
            pageSelectionScrollbarThumbWidthActive
        } else {
            pageSelectionScrollbarThumbWidth
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbTopTarget = with(density) { metrics.thumbOffsetPx.toDp() }
    val thumbTopAnimated by animateDpAsState(
        targetValue = thumbTopTarget,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbTop = if (dragActive) thumbTopTarget else thumbTopAnimated
    val thumbHeight = with(density) { metrics.thumbHeightPx.toDp() }
    val trackColor by animateColorAsState(
        targetValue = if (dragActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val thumbColor by animateColorAsState(
        targetValue = if (dragActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.84f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val thumbScaleX by animateFloatAsState(
        targetValue = if (dragActive) 1f else 0.82f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val thumbScaleY by animateFloatAsState(
        targetValue = if (dragActive) 1f else 0.96f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(pageSelectionScrollbarTouchWidth)
            .pointerInput(listState, trackHeightPx, minThumbHeightPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        val currentMetrics = calculatePageSelectionScrollbarMetrics(
                            layoutInfo = listState.layoutInfo,
                            trackHeightPx = trackHeightPx,
                            minThumbHeightPx = minThumbHeightPx,
                        )
                        if (currentMetrics.scrollable) {
                            dragActive = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = { dragActive = false },
                    onDragCancel = { dragActive = false },
                    onDrag = { change, dragAmount ->
                        val currentMetrics = calculatePageSelectionScrollbarMetrics(
                            layoutInfo = listState.layoutInfo,
                            trackHeightPx = trackHeightPx,
                            minThumbHeightPx = minThumbHeightPx,
                        )
                        if (currentMetrics.scrollable && currentMetrics.maxThumbOffsetPx > 0f) {
                            change.consume()
                            val scrollDelta = dragAmount.y *
                                (currentMetrics.maxScrollPx / currentMetrics.maxThumbOffsetPx)
                            coroutineScope.launch {
                                listState.scrollBy(scrollDelta)
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = pageSelectionScrollbarVerticalInset)
                .onSizeChanged { size -> trackHeightPx = size.height },
            contentAlignment = Alignment.Center,
        ) {
            if (metrics.scrollable || dragActive) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(trackWidth),
                    shape = RoundedCornerShape(percent = 50),
                    color = trackColor,
                ) {}
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = thumbTop)
                        .width(thumbWidth)
                        .height(thumbHeight)
                        .graphicsLayer {
                            scaleX = thumbScaleX
                            scaleY = thumbScaleY
                        },
                    shape = RoundedCornerShape(percent = 50),
                    color = thumbColor,
                ) {}
            }
        }
    }
}

private data class PageSelectionScrollbarMetrics(
    val scrollable: Boolean,
    val thumbHeightPx: Float,
    val thumbOffsetPx: Float,
    val maxThumbOffsetPx: Float,
    val maxScrollPx: Float,
) {
    companion object
}

private fun calculatePageSelectionScrollbarMetrics(
    layoutInfo: LazyListLayoutInfo,
    trackHeightPx: Int,
    minThumbHeightPx: Float,
): PageSelectionScrollbarMetrics {
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewportHeightPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .coerceAtLeast(0)

    if (trackHeightPx <= 0 || viewportHeightPx <= 0 || visibleItems.isEmpty()) {
        return PageSelectionScrollbarMetrics.empty()
    }

    val totalItems = layoutInfo.totalItemsCount
    val averageItemSizePx = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
    val averageItemStepPx = estimatePageSelectionAverageItemStep(visibleItems)
        ?: averageItemSizePx
    val averageSpacingPx = (averageItemStepPx - averageItemSizePx).coerceAtLeast(0f)
    val contentHeightPx = averageItemSizePx * totalItems +
        averageSpacingPx * (totalItems - 1).coerceAtLeast(0)
    val maxScrollPx = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)

    if (totalItems <= 0 || maxScrollPx <= 0f) {
        return PageSelectionScrollbarMetrics.empty(trackHeightPx.toFloat())
    }

    val firstVisibleItem = visibleItems.first()
    val scrolledWithinFirstItem = (layoutInfo.viewportStartOffset - firstVisibleItem.offset)
        .coerceAtLeast(0)
    val scrollOffsetPx = firstVisibleItem.index * averageItemStepPx + scrolledWithinFirstItem
    val scrollFraction = (scrollOffsetPx / maxScrollPx).coerceIn(0f, 1f)
    val thumbHeightPx = ((viewportHeightPx / contentHeightPx) * trackHeightPx)
        .coerceIn(minThumbHeightPx.coerceAtMost(trackHeightPx.toFloat()), trackHeightPx.toFloat())
    val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    return PageSelectionScrollbarMetrics(
        scrollable = maxThumbOffsetPx > 0f,
        thumbHeightPx = thumbHeightPx,
        thumbOffsetPx = scrollFraction * maxThumbOffsetPx,
        maxThumbOffsetPx = maxThumbOffsetPx,
        maxScrollPx = maxScrollPx,
    )
}

private fun estimatePageSelectionAverageItemStep(
    visibleItems: List<androidx.compose.foundation.lazy.LazyListItemInfo>,
): Float? {
    if (visibleItems.size < 2) {
        return null
    }

    val steps = visibleItems.zipWithNext().mapNotNull { (current, next) ->
        val indexDelta = next.index - current.index
        if (indexDelta > 0) {
            (next.offset - current.offset).toFloat() / indexDelta
        } else {
            null
        }
    }

    return steps.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

private fun PageSelectionScrollbarMetrics.Companion.empty(
    trackHeightPx: Float = 0f,
): PageSelectionScrollbarMetrics {
    return PageSelectionScrollbarMetrics(
        scrollable = false,
        thumbHeightPx = trackHeightPx,
        thumbOffsetPx = 0f,
        maxThumbOffsetPx = 0f,
        maxScrollPx = 0f,
    )
}

@Composable
private fun PageSelectionRow(
    state: ParseUiState,
    info: MediaInfo,
    index: Int,
    mediaItem: MediaItem,
    onItemClick: (Int) -> Unit,
    onItemSelectionChange: (Int, Boolean) -> Unit,
) {
    PageItemRow(
        item = mediaItem,
        index = index,
        checked = index in state.selectedItemIndices,
        highlighted = index == resolveHighlightedIndex(state, info),
        rowClickEnabled = isRowClickEnabled(state, info),
        onRowClick = { onItemClick(index) },
        onCheckedChange = { checked -> onItemSelectionChange(index, checked) },
    )
}

@Composable
private fun rememberBoundaryAwareOverscrollEffect(
    boundaryConsumption: ScrollBoundaryConsumption,
): OverscrollEffect? {
    val overscrollEffect = rememberOverscrollEffect() ?: return null
    return remember(overscrollEffect, boundaryConsumption) {
        BoundaryAwareOverscrollEffect(
            delegate = overscrollEffect,
            boundaryConsumption = boundaryConsumption,
        )
    }
}

@Composable
private fun rememberScrollBoundaryNestedScrollConnection(
    boundaryConsumption: ScrollBoundaryConsumption,
): NestedScrollConnection {
    return remember(boundaryConsumption) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val blocked = Offset(x = 0f, y = available.y)
                boundaryConsumption.recordScroll(blocked)
                return blocked
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val blocked = Velocity(x = 0f, y = available.y)
                boundaryConsumption.recordFling(blocked)
                return blocked
            }
        }
    }
}

private class BoundaryAwareOverscrollEffect(
    private val delegate: OverscrollEffect,
    private val boundaryConsumption: ScrollBoundaryConsumption,
) : OverscrollEffect {
    override val isInProgress: Boolean
        get() = delegate.isInProgress

    override val node: DelegatableNode
        get() = delegate.node

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        boundaryConsumption.resetScroll()
        return delegate.applyToScroll(delta, source) { scrollDelta ->
            boundaryConsumption.resetScroll()
            val consumed = performScroll(scrollDelta)
            // The boundary connection blocks parent scrolling by consuming leftover deltas. Hide that
            // artificial consumption from the real overscroll effect so edge stretch still renders.
            consumed - boundaryConsumption.scroll
        }
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        boundaryConsumption.resetFling()
        delegate.applyToFling(velocity) { flingVelocity ->
            boundaryConsumption.resetFling()
            val consumed = performFling(flingVelocity)
            // Same accounting fix for velocity so a fling into the edge can be absorbed by overscroll.
            consumed - boundaryConsumption.fling
        }
    }
}

private class ScrollBoundaryConsumption {
    var scroll: Offset = Offset.Zero
        private set

    var fling: Velocity = Velocity.Zero
        private set

    fun recordScroll(value: Offset) {
        scroll += value
    }

    fun recordFling(value: Velocity) {
        fling += value
    }

    fun resetScroll() {
        scroll = Offset.Zero
    }

    fun resetFling() {
        fling = Velocity.Zero
    }
}

private fun pageSelectionItemKey(index: Int, mediaItem: MediaItem): String {
    return "${mediaItem.type}-${mediaItem.aid}-${mediaItem.cid}-${mediaItem.index}-$index"
}

@Composable
private fun PageSelectionHeader(
    state: ParseUiState,
    info: MediaInfo,
    selectedCount: Int,
    totalItems: Int,
    onSelectAllItems: () -> Unit,
    onClearSelectedItems: () -> Unit,
    onLoadPrevPage: () -> Unit,
    onLoadNextPage: () -> Unit,
    onLoadPage: (Int) -> Unit,
) {
    val label = if (state.collectionMode) {
        when (info.type) {
            MediaType.Favorite -> stringResource(R.string.parse_section_label_favorite)
            MediaType.Bangumi -> stringResource(R.string.parse_section_label_bangumi)
            else -> stringResource(R.string.parse_section_label)
        }
    } else {
        when (info.type) {
            MediaType.Favorite -> stringResource(R.string.parse_page_label_video)
            MediaType.Bangumi,
            MediaType.Lesson,
            MediaType.WatchLater,
            -> stringResource(R.string.parse_page_label_episode)
            else -> stringResource(R.string.parse_page_label)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
                style = ParseTextStyles.buttonLabel,
            )
            Text(
                text = stringResource(R.string.parse_selected_count, selectedCount, totalItems),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ParseTextStyles.supporting,
            )
            PageSelectionHeaderAction(
                text = stringResource(R.string.parse_select_all),
                onClick = onSelectAllItems,
                enabled = selectedCount < totalItems,
            )
            PageSelectionHeaderAction(
                text = stringResource(R.string.parse_clear_all),
                onClick = onClearSelectedItems,
                enabled = selectedCount > 0,
            )
        }

        if (info.paged) {
            PageNavigator(
                pageIndex = state.pageIndex,
                loading = state.loading,
                onLoadPrevPage = onLoadPrevPage,
                onLoadNextPage = onLoadNextPage,
                onLoadPage = onLoadPage,
            )
        }
    }
}

@Composable
private fun PageSelectionHeaderAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val containerColor by animateColorAsState(
        targetValue = if (pressed && enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )

    Box(
        modifier = Modifier
            .height(pageSelectionHeaderActionHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ParseTextStyles.buttonLabel,
        )
    }
}

@Composable
private fun PageNavigator(
    pageIndex: Int,
    loading: Boolean,
    onLoadPrevPage: () -> Unit,
    onLoadNextPage: () -> Unit,
    onLoadPage: (Int) -> Unit,
) {
    var pageText by rememberSaveable { mutableStateOf(pageIndex.toString()) }
    LaunchedEffect(pageIndex) {
        pageText = pageIndex.toString()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = onLoadPrevPage,
            enabled = pageIndex > 1 && !loading,
        ) {
            Text(stringResource(R.string.parse_page_prev))
        }
        OutlinedTextField(
            value = pageText,
            onValueChange = { pageText = it.filter(Char::isDigit) },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.parse_page_nav_label)) },
            enabled = !loading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    pageText.toIntOrNull()?.let(onLoadPage)
                },
            ),
            shape = RoundedCornerShape(controlCornerRadius),
        )
        TextButton(
            onClick = onLoadNextPage,
            enabled = !loading,
        ) {
            Text(stringResource(R.string.parse_page_next))
        }
    }
}

@Composable
private fun PageItemRow(
    item: MediaItem,
    index: Int,
    checked: Boolean,
    highlighted: Boolean,
    rowClickEnabled: Boolean,
    onRowClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val rowShape = RoundedCornerShape(16.dp)
    val targetContainerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val targetTitleColor by animateColorAsState(
        targetValue = when {
            checked -> MaterialTheme.colorScheme.onSecondaryContainer
            highlighted -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val targetMetaColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val targetBorderColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(rowShape)
            .clickable(enabled = rowClickEnabled, onClick = onRowClick),
        color = targetContainerColor,
        shape = rowShape,
        border = if (highlighted) BorderStroke(1.dp, targetBorderColor) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(
                text = (index + 1).toString(),
                modifier = Modifier.width(36.dp),
                color = targetMetaColor,
                style = ParseTextStyles.controlValue,
            )
            Text(
                text = item.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                color = targetTitleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = ParseTextStyles.compactBody,
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
private fun ParseOptionsCard(
    state: ParseUiState,
    info: MediaInfo,
    selectedItem: MediaItem?,
    onFormatChange: (StreamFormat) -> Unit,
    onOutputTypeChange: (OutputType?) -> Unit,
    onResolutionModeChange: (QualityMode) -> Unit,
    onResolutionChange: (Int) -> Unit,
    onCodecChange: (VideoCodec) -> Unit,
    onAudioBitrateModeChange: (QualityMode) -> Unit,
    onAudioBitrateChange: (Int) -> Unit,
    onSubtitleEnabledChange: (Boolean) -> Unit,
    onSubtitleLanguageChange: (String) -> Unit,
    onCopySubtitles: () -> Unit,
    onAiSummaryEnabledChange: (Boolean) -> Unit,
    onCopyAiSummaries: () -> Unit,
    onNfoCollectionEnabledChange: (Boolean) -> Unit,
    onNfoSingleEnabledChange: (Boolean) -> Unit,
    onDanmakuLiveEnabledChange: (Boolean) -> Unit,
    onDanmakuHistoryEnabledChange: (Boolean) -> Unit,
    onDanmakuDateChange: (String) -> Unit,
    onDanmakuHourChange: (String) -> Unit,
    onImageSelectionChange: (String, Boolean) -> Unit,
) {
    val selectedCount = state.selectedItemIndices.size
    val isMultiSelect = selectedCount > 1
    val allowAnyExtras = isMultiSelect
    val formatEnabled = state.outputType != null
    val hasVideo = state.videoStreams.isNotEmpty()
    val hasAudio = state.audioStreams.isNotEmpty()
    val activeFormat = if (state.streamLoading) {
        state.format
    } else {
        state.playUrlInfo?.format ?: state.format
    }
    val isDash = activeFormat == StreamFormat.Dash
    val allowAv = if (isDash) hasVideo && hasAudio else hasVideo
    val resolutionModeEnabled = isMultiSelect && formatEnabled && hasVideo
    val bitrateModeEnabled = isMultiSelect && formatEnabled && hasAudio
    val resolutionEnabled =
        formatEnabled && hasVideo && (!isMultiSelect || state.resolutionMode == QualityMode.Fixed)
    val bitrateEnabled =
        formatEnabled && hasAudio && (!isMultiSelect || state.audioBitrateMode == QualityMode.Fixed)
    val codecEnabled = formatEnabled && hasVideo
    val copyEnabledBase = state.selectedItemIndices.isNotEmpty() &&
        !state.loading &&
        !state.downloadStarting &&
        !state.subtitleCopying &&
        !state.aiSummaryCopying
    val collectionAvailable = !info.nfo.showTitle.isNullOrBlank() &&
        (info.collection || info.type == MediaType.Bangumi || info.type == MediaType.Lesson)
    val danmakuLiveEnabled = (selectedItem?.aid != null && selectedItem.cid != null) || allowAnyExtras
    val danmakuHistoryEnabled = selectedItem?.cid != null || allowAnyExtras
    val subtitleLanguageOptions = state.subtitleList.map { DropdownOption(it.name, it) }
    val subtitleLanguageValue = state.subtitleList
        .firstOrNull { it.lan == state.selectedSubtitleLan }
        ?.name
        .orEmpty()
    val subtitleLanguageEnabled = state.subtitleEnabled &&
        (state.subtitleList.isNotEmpty() || allowAnyExtras)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cardCornerRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(
                    horizontal = optionsCardHorizontalPadding,
                    vertical = optionsCardVerticalPadding,
                ),
        ) {
            Text(
                text = stringResource(R.string.parse_section_options),
                style = ParseTextStyles.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(optionsCardTitleBottomSpacing))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(optionsCardSectionSpacing),
            ) {
                OptionsSection(title = stringResource(R.string.parse_output_type)) {
                    ConnectedOutputButtons(
                        selected = state.outputType,
                        audioVideoEnabled = allowAv,
                        videoEnabled = isDash && hasVideo,
                        audioEnabled = isDash && hasAudio,
                        onOutputTypeChange = onOutputTypeChange,
                    )
                }

                OptionsSection(title = stringResource(R.string.parse_stream_format)) {
                    ConnectedFormatButtons(
                        selected = state.format,
                        enabled = formatEnabled,
                        onFormatChange = onFormatChange,
                    )
                    StreamFormatHint()
                }

                QualityControls(
                    state = state,
                    isMultiSelect = isMultiSelect,
                    resolutionModeEnabled = resolutionModeEnabled,
                    bitrateModeEnabled = bitrateModeEnabled,
                    resolutionEnabled = resolutionEnabled,
                    codecEnabled = codecEnabled,
                    bitrateEnabled = bitrateEnabled,
                    onResolutionModeChange = onResolutionModeChange,
                    onAudioBitrateModeChange = onAudioBitrateModeChange,
                    onResolutionChange = onResolutionChange,
                    onCodecChange = onCodecChange,
                    onAudioBitrateChange = onAudioBitrateChange,
                )

                SectionDivider()
                OptionsSection(title = stringResource(R.string.parse_misc_label)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckOption(
                            text = stringResource(R.string.parse_subtitle_label),
                            checked = state.subtitleEnabled,
                            enabled = state.subtitleList.isNotEmpty() || allowAnyExtras,
                            onCheckedChange = onSubtitleEnabledChange,
                            minHeight = compactSelectionHeight,
                            modifier = Modifier.weight(0.9f),
                        )
                        CompactSelectionField(
                            label = stringResource(R.string.parse_subtitle_language),
                            value = subtitleLanguageValue,
                            enabled = subtitleLanguageEnabled,
                            options = subtitleLanguageOptions,
                            onOptionSelected = { onSubtitleLanguageChange(it.value.lan) },
                            modifier = Modifier.weight(1.1f),
                        )
                        CheckOption(
                            text = stringResource(R.string.parse_ai_summary_label),
                            checked = state.aiSummaryEnabled,
                            enabled = state.aiSummaryAvailable || allowAnyExtras,
                            onCheckedChange = onAiSummaryEnabledChange,
                            minHeight = compactSelectionHeight,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ExpressiveActionButton(
                            text = stringResource(R.string.parse_copy_subtitle_now),
                            iconRes = R.drawable.ic_content_copy_24,
                            loading = state.subtitleCopying,
                            enabled = copyEnabledBase && (state.subtitleList.isNotEmpty() || allowAnyExtras),
                            tonal = true,
                            onClick = onCopySubtitles,
                            modifier = Modifier.weight(1f),
                        )
                        ExpressiveActionButton(
                            text = stringResource(R.string.parse_copy_ai_summary_now),
                            iconRes = R.drawable.ic_content_copy_24,
                            loading = state.aiSummaryCopying,
                            enabled = copyEnabledBase && (state.aiSummaryAvailable || allowAnyExtras),
                            tonal = true,
                            onClick = onCopyAiSummaries,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                SectionDivider()
                OptionsSection(title = stringResource(R.string.parse_nfo_label)) {
                    TwoColumnChecks {
                        CheckOption(
                            text = stringResource(R.string.parse_nfo_collection),
                            checked = state.nfoCollectionEnabled,
                            enabled = collectionAvailable || allowAnyExtras,
                            onCheckedChange = onNfoCollectionEnabledChange,
                            modifier = Modifier.weight(1f),
                        )
                        CheckOption(
                            text = stringResource(R.string.parse_nfo_single),
                            checked = state.nfoSingleEnabled,
                            enabled = info.list.isNotEmpty() || allowAnyExtras,
                            onCheckedChange = onNfoSingleEnabledChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                SectionDivider()
                OptionsSection(title = stringResource(R.string.parse_danmaku_label)) {
                    TwoColumnChecks {
                        CheckOption(
                            text = stringResource(R.string.parse_danmaku_live),
                            checked = state.danmakuLiveEnabled,
                            enabled = danmakuLiveEnabled,
                            onCheckedChange = onDanmakuLiveEnabledChange,
                            modifier = Modifier.weight(1f),
                        )
                        CheckOption(
                            text = stringResource(R.string.parse_danmaku_history),
                            checked = state.danmakuHistoryEnabled,
                            enabled = danmakuHistoryEnabled,
                            onCheckedChange = onDanmakuHistoryEnabledChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = state.danmakuDate,
                            onValueChange = onDanmakuDateChange,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.parse_danmaku_date)) },
                            enabled = state.danmakuHistoryEnabled,
                            singleLine = true,
                            shape = RoundedCornerShape(controlCornerRadius),
                        )
                        OutlinedTextField(
                            value = state.danmakuHour,
                            onValueChange = { value ->
                                onDanmakuHourChange(value.filter(Char::isDigit).take(2))
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.parse_danmaku_hour)) },
                            enabled = state.danmakuHistoryEnabled,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(controlCornerRadius),
                        )
                    }
                }

                if (state.imageOptions.isNotEmpty()) {
                    SectionDivider()
                    OptionsSection(title = stringResource(R.string.parse_image_label)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.imageOptions.forEach { option ->
                                ExpressiveFilterChip(
                                    selected = option.id in state.selectedImageIds,
                                    onClick = {
                                        onImageSelectionChange(
                                            option.id,
                                            option.id !in state.selectedImageIds,
                                        )
                                    },
                                    text = option.label,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QualityControls(
    state: ParseUiState,
    isMultiSelect: Boolean,
    resolutionModeEnabled: Boolean,
    bitrateModeEnabled: Boolean,
    resolutionEnabled: Boolean,
    codecEnabled: Boolean,
    bitrateEnabled: Boolean,
    onResolutionModeChange: (QualityMode) -> Unit,
    onAudioBitrateModeChange: (QualityMode) -> Unit,
    onResolutionChange: (Int) -> Unit,
    onCodecChange: (VideoCodec) -> Unit,
    onAudioBitrateChange: (Int) -> Unit,
) {
    val resolutionModes = resolutionModeOptions()
    val bitrateModes = bitrateModeOptions()

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedOptionsVisibility(visible = isMultiSelect) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactSelectionField(
                        label = stringResource(R.string.parse_resolution_mode_label),
                        value = resolutionModes.first { it.value == state.resolutionMode }.label,
                        enabled = resolutionModeEnabled,
                        options = resolutionModes,
                        onOptionSelected = { onResolutionModeChange(it.value) },
                        modifier = Modifier.weight(1f),
                    )
                    CompactSelectionField(
                        label = stringResource(R.string.parse_bitrate_mode_label),
                        value = bitrateModes.first { it.value == state.audioBitrateMode }.label,
                        enabled = bitrateModeEnabled,
                        options = bitrateModes,
                        onOptionSelected = { onAudioBitrateModeChange(it.value) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactSelectionField(
                label = stringResource(R.string.parse_resolution_label),
                value = state.resolutions.firstOrNull { it.id == state.selectedResolutionId }?.label.orEmpty(),
                enabled = resolutionEnabled,
                options = state.resolutions.map { DropdownOption(it.label, it) },
                onOptionSelected = { onResolutionChange(it.value.id) },
                modifier = Modifier.weight(1f),
            )
            CompactSelectionField(
                label = stringResource(R.string.parse_codec_label),
                value = state.codecs.firstOrNull { it.codec == state.selectedCodec }?.label.orEmpty(),
                enabled = codecEnabled,
                options = state.codecs.map { DropdownOption(it.label, it) },
                onOptionSelected = { onCodecChange(it.value.codec) },
                modifier = Modifier.weight(1f),
            )
            CompactSelectionField(
                label = stringResource(R.string.parse_bitrate_label),
                value = state.audioBitrates.firstOrNull { it.id == state.selectedAudioId }?.label.orEmpty(),
                enabled = bitrateEnabled,
                options = state.audioBitrates.map { DropdownOption(it.label, it) },
                onOptionSelected = { onAudioBitrateChange(it.value.id) },
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedOptionsVisibility(visible = isMultiSelect) {
            Column {
                Spacer(Modifier.height(8.dp))
                HelperText(text = stringResource(R.string.parse_quality_multi_hint))
            }
        }

        AnimatedOptionsVisibility(visible = !state.warning.isNullOrBlank()) {
            Column {
                Spacer(Modifier.height(8.dp))
                MessageCard(
                    message = state.warning.orEmpty(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedOptionsVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val sizeSpec = tween<IntSize>(
        durationMillis = optionsVisibilityAnimationDurationMillis,
        easing = FastOutSlowInEasing,
    )
    val fadeSpec = tween<Float>(
        durationMillis = optionsVisibilityAnimationDurationMillis,
        easing = FastOutSlowInEasing,
    )
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(animationSpec = fadeSpec) +
                expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = sizeSpec,
                ),
        exit =
            fadeOut(animationSpec = fadeSpec) +
                shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = sizeSpec,
                ),
    ) {
        content()
    }
}

@Composable
private fun ConnectedOutputButtons(
    selected: OutputType?,
    audioVideoEnabled: Boolean,
    videoEnabled: Boolean,
    audioEnabled: Boolean,
    onOutputTypeChange: (OutputType?) -> Unit,
) {
    val options = listOf(
        SegmentedOption(
            label = stringResource(R.string.output_audio_video),
            iconRes = R.drawable.ic_output_movie_24,
            value = OutputType.AudioVideo,
            enabled = audioVideoEnabled,
        ),
        SegmentedOption(
            label = stringResource(R.string.output_video),
            iconRes = R.drawable.ic_output_video_file_24,
            value = OutputType.VideoOnly,
            enabled = videoEnabled,
        ),
        SegmentedOption(
            label = stringResource(R.string.output_audio),
            iconRes = R.drawable.ic_output_audio_file_24,
            value = OutputType.AudioOnly,
            enabled = audioEnabled,
        ),
    )
    ConnectedToggleRow(
        options = options,
        selected = selected,
        selectionRequired = false,
        onSelected = onOutputTypeChange,
    )
}

@Composable
private fun ConnectedFormatButtons(
    selected: StreamFormat,
    enabled: Boolean,
    onFormatChange: (StreamFormat) -> Unit,
) {
    val options = listOf(
        SegmentedOption(
            stringResource(R.string.format_dash),
            null,
            StreamFormat.Dash,
            enabled,
        ),
        SegmentedOption(
            stringResource(R.string.format_mp4),
            null,
            StreamFormat.Mp4,
            enabled,
        ),
        SegmentedOption(
            stringResource(R.string.format_flv),
            null,
            StreamFormat.Flv,
            enabled,
        ),
    )
    ConnectedToggleRow(
        options = options,
        selected = selected,
        selectionRequired = true,
        onSelected = { next -> if (next != null) onFormatChange(next) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("DEPRECATION")
@Composable
private fun <T> ConnectedToggleRow(
    options: List<SegmentedOption<T>>,
    selected: T?,
    selectionRequired: Boolean,
    onSelected: (T?) -> Unit,
) {
    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        expandedRatio = 0.16f,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val interactionSource = remember { MutableInteractionSource() }
            ToggleButton(
                checked = selected == option.value,
                onCheckedChange = { checked ->
                    when {
                        checked -> onSelected(option.value)
                        !selectionRequired && selected == option.value -> onSelected(null)
                    }
                },
                enabled = option.enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .animateWidth(interactionSource)
                    .semanticsRoleRadio(),
                interactionSource = interactionSource,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                option.iconRes?.let { iconRes ->
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = option.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ParseTextStyles.buttonLabel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveActionButton(
    text: String,
    iconRes: Int,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    height: Dp = 48.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 16.dp else 24.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val scaleX by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val scaleY by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val buttonModifier = modifier
        .height(height)
        .graphicsLayer {
            this.scaleX = scaleX
            this.scaleY = scaleY
        }
    val loadingContainerColor = if (tonal) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val loadingContentColor = if (tonal) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val content: @Composable () -> Unit = {
        if (loading) {
            LoadingIndicator(
                modifier = Modifier.size(actionButtonLoadingIndicatorSize),
                color = loadingContentColor,
            )
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ParseTextStyles.buttonLabel,
            )
        }
    }

    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = buttonModifier,
            shape = RoundedCornerShape(corner),
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = 14.dp),
            colors = if (loading) {
                ButtonDefaults.filledTonalButtonColors(
                    disabledContainerColor = loadingContainerColor,
                    disabledContentColor = loadingContentColor,
                )
            } else {
                ButtonDefaults.filledTonalButtonColors()
            },
        ) {
            content()
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = buttonModifier,
            shape = RoundedCornerShape(corner),
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = 14.dp),
            colors = if (loading) {
                ButtonDefaults.buttonColors(
                    disabledContainerColor = loadingContainerColor,
                    disabledContentColor = loadingContentColor,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            content()
        }
    }
}

@Composable
private fun QuickActionFab(
    enabled: Boolean,
    controlsOffsetPx: Float,
    onDownload: () -> Unit,
) {
    val density = LocalDensity.current
    val offsetDp = with(density) { controlsOffsetPx.toDp() }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    FloatingActionButton(
        onClick = {
            if (!enabled) return@FloatingActionButton
            onDownload()
        },
        modifier = Modifier
            .padding(
                end = FloatingControlsDefaults.EdgePadding,
                bottom = FloatingControlsDefaults.BottomPadding,
            )
            .offset(y = offsetDp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = FloatingActionButtonDefaults.shape,
        interactionSource = interactionSource,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_save_alt_24),
            contentDescription = stringResource(R.string.parse_download),
            modifier = Modifier.graphicsLayer(alpha = if (enabled) 1f else 0.42f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    enabled: Boolean,
    options: List<DropdownOption<T>>,
    onOptionSelected: (DropdownOption<T>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled && options.isNotEmpty()) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ParseTextStyles.controlLabel,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(controlCornerRadius),
            textStyle = ParseTextStyles.body,
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = ParseTextStyles.body) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CompactSelectionField(
    label: String,
    value: String,
    enabled: Boolean,
    options: List<DropdownOption<T>>,
    onOptionSelected: (DropdownOption<T>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.42f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val borderColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val valueTransitionSpec = tween<Float>(
        durationMillis = optionsValueAnimationDurationMillis,
        easing = FastOutSlowInEasing,
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled && options.isNotEmpty()) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(compactSelectionHeight)
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            border = if (expanded) BorderStroke(1.dp, borderColor) else null,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = contentAlpha)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = ParseTextStyles.controlLabel,
                    )
                    AnimatedContent(
                        targetState = value,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            fadeIn(animationSpec = valueTransitionSpec) togetherWith
                                fadeOut(animationSpec = valueTransitionSpec)
                        },
                        label = "CompactSelectionFieldValue",
                    ) { animatedValue ->
                        Text(
                            text = animatedValue,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = ParseTextStyles.controlValue,
                        )
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = ParseTextStyles.body,
                        )
                    },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info_24),
                contentDescription = null,
                tint = contentColor,
            )
            Text(
                text = message,
                modifier = Modifier.padding(start = 12.dp),
                color = contentColor,
                style = ParseTextStyles.body,
            )
        }
    }
}

@Composable
private fun ExpressiveFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val chipColors = MaterialTheme.colorScheme
    val containerColor = lerp(
        chipColors.primaryContainer.copy(alpha = 0f),
        chipColors.primaryContainer,
        selectedProgress,
    )
    val contentColor = lerp(
        chipColors.onSurfaceVariant,
        chipColors.onPrimaryContainer,
        selectedProgress,
    )
    val borderColor = lerp(
        chipColors.outlineVariant,
        chipColors.primary.copy(alpha = 0.48f),
        selectedProgress,
    )
    val leadingSlotWidth by animateDpAsState(
        targetValue = if (selected) 24.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.72f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )

    Surface(
        modifier = Modifier
            .height(imageOptionChipHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(leadingSlotWidth)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_24),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            alpha = checkAlpha
                            scaleX = checkScale
                            scaleY = checkScale
                        },
                )
            }
            Text(
                text = text,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ParseTextStyles.buttonLabel,
            )
        }
    }
}

@Composable
private fun OptionsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = ParseTextStyles.sectionLabel,
        )
        content()
    }
}

@Composable
private fun TwoColumnChecks(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = 2,
        content = content,
    )
}

@Composable
private fun CheckOption(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = checkOptionMinHeight,
) {
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ParseTextStyles.compactBody,
        )
    }
}

@Composable
private fun StreamFormatHint() {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgbCompat()
    val linkColor = MaterialTheme.colorScheme.primary.toArgbCompat()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = {
            TextView(it).apply {
                movementMethod = LinkMovementMethod.getInstance()
                textSize = 13f
            }
        },
        update = { textView ->
            textView.text = HtmlCompat.fromHtml(
                context.getString(R.string.parse_stream_format_hint),
                HtmlCompat.FROM_HTML_MODE_COMPACT,
            )
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
        },
    )
}

@Composable
private fun HelperText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = ParseTextStyles.supporting,
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .height(1.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
    )
}

@Composable
private fun mediaTypeOptions(): List<DropdownOption<MediaType?>> {
    return listOf(
        DropdownOption(stringResource(R.string.parse_media_type_auto), null),
        DropdownOption(stringResource(R.string.parse_media_type_video), MediaType.Video),
        DropdownOption(stringResource(R.string.parse_media_type_bangumi), MediaType.Bangumi),
        DropdownOption(stringResource(R.string.parse_media_type_lesson), MediaType.Lesson),
        DropdownOption(stringResource(R.string.parse_media_type_music), MediaType.Music),
        DropdownOption(stringResource(R.string.parse_media_type_music_list), MediaType.MusicList),
        DropdownOption(stringResource(R.string.parse_media_type_watch_later), MediaType.WatchLater),
        DropdownOption(stringResource(R.string.parse_media_type_favorite), MediaType.Favorite),
        DropdownOption(stringResource(R.string.parse_media_type_opus), MediaType.Opus),
        DropdownOption(stringResource(R.string.parse_media_type_opus_list), MediaType.OpusList),
        DropdownOption(stringResource(R.string.parse_media_type_user_video), MediaType.UserVideo),
        DropdownOption(stringResource(R.string.parse_media_type_user_opus), MediaType.UserOpus),
        DropdownOption(stringResource(R.string.parse_media_type_user_audio), MediaType.UserAudio),
    )
}

@Composable
private fun resolutionModeOptions(): List<DropdownOption<QualityMode>> {
    return listOf(
        DropdownOption(stringResource(R.string.parse_resolution_mode_highest), QualityMode.Highest),
        DropdownOption(stringResource(R.string.parse_resolution_mode_lowest), QualityMode.Lowest),
        DropdownOption(stringResource(R.string.parse_resolution_mode_fixed), QualityMode.Fixed),
    )
}

@Composable
private fun bitrateModeOptions(): List<DropdownOption<QualityMode>> {
    return listOf(
        DropdownOption(stringResource(R.string.parse_bitrate_mode_highest), QualityMode.Highest),
        DropdownOption(stringResource(R.string.parse_bitrate_mode_lowest), QualityMode.Lowest),
        DropdownOption(stringResource(R.string.parse_bitrate_mode_fixed), QualityMode.Fixed),
    )
}

private fun resolveHighlightedIndex(state: ParseUiState, info: MediaInfo): Int {
    return if (isRowClickEnabled(state, info)) {
        state.collectionPreviewIndex ?: state.selectedItemIndex
    } else {
        -1
    }
}

private fun isRowClickEnabled(state: ParseUiState, info: MediaInfo): Boolean {
    return info.type != MediaType.Video || (state.collectionMode && info.collection)
}

private data class StatDisplay(
    val iconRes: Int,
    val value: Long,
)

private data class VideoCardDisplay(
    val title: String,
    val description: String,
    val coverUrl: String,
    val stat: MediaStat,
)

private data class DropdownOption<T>(
    val label: String,
    val value: T,
)

private data class SegmentedOption<T>(
    val label: String,
    val iconRes: Int?,
    val value: T,
    val enabled: Boolean,
)

private fun resolveVideoCardDisplay(
    state: ParseUiState,
    info: MediaInfo,
    selectedItem: MediaItem?,
): VideoCardDisplay {
    val isCollectionVideo = state.collectionMode && info.type == MediaType.Video && info.collection
    val previewItem = if (isCollectionVideo) {
        state.collectionPreviewIndex?.let { index -> state.items.getOrNull(index) }
    } else {
        null
    }

    if (previewItem != null) {
        val title = previewItem.title.trim().ifBlank {
            info.nfo.showTitle?.trim().orEmpty()
        }
        val description = previewItem.description.trim().ifBlank {
            info.nfo.intro?.trim().orEmpty()
        }
        val coverUrl = previewItem.coverUrl.ifBlank {
            info.nfo.thumbs.firstOrNull { it.id == "cover" }?.url
                ?: info.nfo.thumbs.firstOrNull { it.id == "ugc" }?.url
                ?: info.nfo.thumbs.firstOrNull()?.url
                ?: ""
        }
        val stat = state.collectionPreviewStat ?: info.nfo.stat
        return VideoCardDisplay(title, description, coverUrl, stat)
    }

    if (isCollectionVideo) {
        val title = info.nfo.showTitle?.trim().ifNullOrBlank {
            selectedItem?.title?.trim().orEmpty()
        }
        val description = info.nfo.intro?.trim().ifNullOrBlank {
            selectedItem?.description?.trim().orEmpty()
        }
        val coverUrl = info.nfo.thumbs.firstOrNull { it.id == "ugc" }?.url
            ?: info.nfo.thumbs.firstOrNull()?.url
            ?: selectedItem?.coverUrl.orEmpty()
        return VideoCardDisplay(
            title = title,
            description = description,
            coverUrl = coverUrl,
            stat = selectedItem?.stat ?: state.selectedItemStat ?: info.nfo.stat,
        )
    }

    if (info.type == MediaType.Video) {
        val title = resolveNonCollectionVideoTitle(state, info, selectedItem)
        val description = selectedItem?.description?.trim().ifNullOrBlank {
            info.nfo.intro?.trim().orEmpty()
        }
        val coverUrl = selectedItem?.coverUrl?.ifBlank { null }
            ?: info.nfo.thumbs.firstOrNull()?.url
            ?: ""
        return VideoCardDisplay(title, description, coverUrl, info.nfo.stat)
    }

    val title = selectedItem?.title?.trim().ifNullOrBlank {
        info.nfo.showTitle?.trim().orEmpty()
    }
    val fallbackDescription = when (info.type) {
        MediaType.Favorite,
        MediaType.WatchLater,
        -> ""
        else -> info.nfo.intro?.trim().orEmpty()
    }
    val description = selectedItem?.description?.trim().ifNullOrBlank {
        fallbackDescription
    }
    val coverUrl = selectedItem?.coverUrl?.ifBlank { null }
        ?: info.nfo.thumbs.firstOrNull()?.url
        ?: ""
    val stat = when (info.type) {
        MediaType.Favorite,
        MediaType.WatchLater,
        -> selectedItem?.stat ?: state.selectedItemStat ?: MediaStat()
        else -> selectedItem?.stat ?: state.selectedItemStat ?: info.nfo.stat
    }
    return VideoCardDisplay(title, description, coverUrl, stat)
}

private fun resolveNonCollectionVideoTitle(
    state: ParseUiState,
    info: MediaInfo,
    selectedItem: MediaItem?,
): String {
    val sectionTitle = state.sections
        ?.tabs
        ?.firstOrNull { it.id == state.selectedSectionId }
        ?.name
        ?.trim()
        .orEmpty()
    if (sectionTitle.isNotBlank()) return sectionTitle

    val baseTitle = info.nfo.showTitle?.trim().orEmpty()
    if (!info.collection && baseTitle.isNotBlank()) return baseTitle

    if (info.collection && state.items.size == 1) {
        val singleTitle = selectedItem?.title?.trim().orEmpty()
        if (singleTitle.isNotBlank()) return singleTitle
    }

    return if (baseTitle.isNotBlank()) {
        baseTitle
    } else {
        selectedItem?.title?.trim().orEmpty()
    }
}

private fun String?.ifNullOrBlank(fallback: () -> String): String {
    return this?.takeIf { it.isNotBlank() } ?: fallback()
}

private fun formatStat(value: Long): String {
    return when {
        value >= 100_000_000L -> String.format("%.1f亿", value / 100_000_000.0)
        value >= 10_000L -> String.format("%.1f万", value / 10_000.0)
        else -> value.toString()
    }
}

private fun Modifier.semanticsRoleRadio(): Modifier {
    return semantics { role = Role.RadioButton }
}

private fun Color.toArgbCompat(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
}
