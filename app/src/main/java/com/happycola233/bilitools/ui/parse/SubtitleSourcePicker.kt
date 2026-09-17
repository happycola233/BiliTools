package com.happycola233.bilitools.ui.parse

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.displayName
import com.happycola233.bilitools.data.isGenerated
import com.happycola233.bilitools.data.languageName
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.subtitleLanguageDisplayName
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.AppAccents
import kotlinx.coroutines.launch

/** 页面只呈现已选摘要；语言选择集中在面板中，确认前不改变下载选项。 */
@Composable
internal fun SubtitleSourcePicker(
    state: ParseUiState,
    selection: SubtitleLanguageSelection,
    enabled: Boolean,
    onSelectionChange: (SubtitleLanguageSelection) -> Unit,
    onRetry: () -> Unit,
    singleSelection: Boolean = false,
    selectedLanguage: String? = null,
    onLanguageSelected: (String) -> Unit = {},
    sheetTitle: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val languages = selectedLanguages(selection, state.subtitleList, singleSelection, selectedLanguage)
    val choices = state.subtitleChoices(languages)
    val label = stringResource(if (singleSelection) R.string.subtitle_source_lyrics_language else R.string.subtitle_source_language)
    val summary = when {
        languages.isEmpty() -> stringResource(R.string.subtitle_source_select)
        singleSelection -> choices.firstOrNull { it.lan == selectedLanguage }?.displayName.orEmpty()
        selection == SubtitleLanguageSelection.All -> stringResource(R.string.subtitle_source_all_count, languages.size)
        languages.size <= 2 -> choices.filter { it.lan in languages }.joinToString("、") { it.displayName }
        else -> stringResource(R.string.subtitle_source_selected_count, languages.size)
    }
    val haptics = rememberAppHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // clickable Surface 在形状内绘制涟漪，不能把 toggleable 放在 Surface 的裁剪之外。
        Surface(
            onClick = { haptics.select(); open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.ic_chevron_right_24), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SubtitleSelectionStatus(state, languages, singleSelection, onRetry, enabled,
            showPartialCoverage = singleSelection || selection is SubtitleLanguageSelection.Languages)
    }
    if (open) {
        SubtitleSelectionSheet(
            state = state,
            selection = selection,
            singleSelection = singleSelection,
            selectedLanguage = selectedLanguage,
            title = sheetTitle ?: label,
            enabled = enabled,
            onRetry = onRetry,
            onDismiss = { open = false },
            onConfirm = { chosen, language ->
                if (singleSelection) language?.let(onLanguageSelected) else onSelectionChange(chosen)
            },
        )
    }
}

private val selectionSaver = listSaver<SubtitleLanguageSelection, String>(
    save = { when (it) {
        SubtitleLanguageSelection.All -> listOf("all")
        is SubtitleLanguageSelection.Languages -> listOf("languages") + it.languages
    } },
    restore = { if (it.first() == "all") SubtitleLanguageSelection.All else SubtitleLanguageSelection.Languages(it.drop(1).toSet()) },
)

/** 全选是列表的聚合状态：选完为勾选、选了一部分为半选、没有选择为空。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleSelectionSheet(
    state: ParseUiState,
    selection: SubtitleLanguageSelection,
    singleSelection: Boolean,
    selectedLanguage: String?,
    title: String,
    enabled: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (SubtitleLanguageSelection, String?) -> Unit,
) {
    var draft by rememberSaveable(stateSaver = selectionSaver) { mutableStateOf(selection) }
    var draftLanguage by rememberSaveable { mutableStateOf(selectedLanguage) }
    val languages = selectedLanguages(draft, state.subtitleList, singleSelection, draftLanguage)
    val originalLanguages = selectedLanguages(selection, state.subtitleList, singleSelection, selectedLanguage)
    // 保留本次打开时选中的缺失语言，取消勾选后不让行立即消失或跳位。
    val choices = state.subtitleChoices(originalLanguages + languages)
    val allState = when {
        languages.isEmpty() -> ToggleableState.Off
        choices.isNotEmpty() && choices.all { it.lan in languages } -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val close = { scope.launch { sheetState.hide(); onDismiss() }; Unit }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (singleSelection) stringResource(R.string.subtitle_source_choose_lyrics)
                    else stringResource(R.string.subtitle_source_selected_count, languages.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                    .then(if (singleSelection) Modifier.selectableGroup() else Modifier),
            ) {
                if (!singleSelection && choices.isNotEmpty()) {
                    item(key = "all") {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .triStateToggleable(state = allState, enabled = enabled, role = Role.Checkbox, onClick = {
                                    haptics.select()
                                    draft = if (allState == ToggleableState.On) SubtitleLanguageSelection.Languages(emptySet())
                                    else if (choices.size == state.subtitleList.size) SubtitleLanguageSelection.All
                                    else SubtitleLanguageSelection.Languages(choices.map { it.lan }.toSet())
                                }).heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TriStateCheckbox(state = allState, onClick = null, enabled = enabled, colors = AppAccents.checkboxColors())
                            Text(stringResource(R.string.subtitle_source_select_all), style = MaterialTheme.typography.labelLarge)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                items(choices, key = { it.lan }) { subtitle ->
                    SubtitleSourceOption(
                        subtitle = subtitle,
                        supportingText = subtitleAvailability(state, subtitle),
                        selected = subtitle.lan in languages,
                        enabled = enabled,
                        singleSelection = singleSelection,
                        onClick = {
                            if (singleSelection) draftLanguage = subtitle.lan
                            else draft = draft.toggle(subtitle.lan, state.subtitleList)
                        },
                    )
                }
                item(key = "status") {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        // 每行已显示覆盖数量，面板不重复解释部分条目缺失。
                        SubtitleSelectionStatus(state, languages, singleSelection, onRetry, enabled, showPartialCoverage = false)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = close, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.subtitle_source_cancel))
                }
                Button(
                    onClick = { onConfirm(draft, draftLanguage); close() },
                    enabled = enabled && (!singleSelection || draftLanguage != null),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.subtitle_source_done)) }
            }
        }
    }
}

private fun selectedLanguages(selection: SubtitleLanguageSelection, subtitles: List<SubtitleInfo>, single: Boolean, language: String?): Set<String> =
    if (single) setOfNotNull(language) else when (selection) {
        SubtitleLanguageSelection.All -> subtitles.map { it.lan }.toSet()
        is SubtitleLanguageSelection.Languages -> selection.languages
    }

private fun ParseUiState.subtitleChoices(selected: Set<String>): List<SubtitleInfo> {
    val available = subtitleList.map { it.lan }.toSet()
    return subtitleList + (selected - available).map { language ->
        subtitleKnownLanguages.firstOrNull { it.lan == language }
            ?: SubtitleInfo(language, subtitleLanguageDisplayName(language), "")
    }
}

@Composable
private fun subtitleAvailability(state: ParseUiState, subtitle: SubtitleInfo): String? {
    val uncertain = state.subtitleLoadStatus != SubtitleLoadStatus.Ready || state.subtitleFailedCount > 0 ||
        !state.isLoggedIn && state.subtitleList.isEmpty()
    return when {
        state.subtitleTargetCount > 1 -> stringResource(
            if (uncertain) R.string.subtitle_source_coverage_pending else R.string.subtitle_source_coverage,
            state.subtitleAvailableCounts[subtitle.lan] ?: 0, state.subtitleTargetCount,
        )
        state.subtitleList.none { it.lan == subtitle.lan } -> stringResource(
            if (uncertain) R.string.subtitle_source_unconfirmed else R.string.subtitle_source_unavailable,
        )
        else -> null
    }
}

@Composable
private fun SubtitleSelectionStatus(
    state: ParseUiState,
    selected: Set<String>,
    single: Boolean,
    onRetry: () -> Unit,
    enabled: Boolean,
    showPartialCoverage: Boolean,
) {
    val loading = state.subtitleLoadStatus == SubtitleLoadStatus.Loading
    val failed = state.subtitleLoadStatus == SubtitleLoadStatus.Failed || state.subtitleFailedCount > 0
    val batch = state.subtitleTargetCount > 1
    val loginNeeded = !state.isLoggedIn && state.subtitleList.isEmpty()
    val message = when {
        loading -> if (batch) stringResource(R.string.subtitle_source_loading_batch, state.subtitleCompletedCount, state.subtitleTargetCount)
            else stringResource(R.string.subtitle_source_loading)
        failed -> if (batch) stringResource(R.string.subtitle_source_partial_failure, state.subtitleFailedCount)
            else stringResource(R.string.subtitle_source_failure)
        state.subtitleList.isEmpty() -> stringResource(when {
            loginNeeded && single && selected.isEmpty() -> R.string.subtitle_source_login_lyrics
            loginNeeded -> R.string.subtitle_source_login
            single && selected.isEmpty() -> R.string.subtitle_source_no_lyrics
            selected.isNotEmpty() -> R.string.subtitle_source_missing_selection
            batch -> R.string.subtitle_source_empty_batch
            else -> R.string.subtitle_source_empty
        })
        selected.isEmpty() -> stringResource(if (single) R.string.subtitle_source_choose_lyrics else R.string.subtitle_source_choose_subtitles)
        showPartialCoverage && selected.any { language -> state.subtitleList.none { it.lan == language } || batch && (state.subtitleAvailableCounts[language] ?: 0) < state.subtitleTargetCount } ->
            stringResource(R.string.subtitle_source_missing_selection)
        else -> null
    }
    if (message != null) {
        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    if (failed) TextButton(onClick = onRetry, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.subtitle_source_retry))
    }
}

/** 无边框的选择行；先裁剪再添加交互，使按压状态始终遵守同一圆角。 */
@Composable
private fun SubtitleSourceOption(subtitle: SubtitleInfo, selected: Boolean, enabled: Boolean, onClick: () -> Unit, supportingText: String?, singleSelection: Boolean) {
    val haptics = rememberAppHaptics()
    val select = { haptics.select(); onClick() }
    val selectionModifier = if (singleSelection) Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = select)
    else Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { select() })
    Row(
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(MaterialTheme.shapes.small)
            .then(selectionModifier).heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (singleSelection) RadioButton(selected = selected, onClick = null, enabled = enabled)
        else Checkbox(checked = selected, onCheckedChange = null, enabled = enabled, colors = AppAccents.checkboxColors())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    subtitle.languageName,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                )
                if (subtitle.isGenerated) {
                    val sourceDescription = stringResource(R.string.subtitle_source_ai_description)
                    // 标签只说明来源，整行仍是唯一选择控件；读屏读出完整含义。
                    Text(
                        stringResource(R.string.subtitle_source_ai_badge),
                        modifier = Modifier.align(Alignment.CenterVertically)
                            .clearAndSetSemantics { text = AnnotatedString(sourceDescription) }
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.38f), RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                    )
                }
            }
            if (supportingText != null) Text(supportingText, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
