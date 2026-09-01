package com.happycola233.bilitools.ui.parse

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.ui.copyTextToClipboard
import com.happycola233.bilitools.ui.copyTextWithFeedback
import kotlinx.coroutines.flow.collect

/**
 * 解析页的状态与平台副作用入口。主界面和外部下载入口共用这一层，视觉组件本身只接收状态与回调。
 */
@Composable
fun ParseRoute(
    viewModel: ParseViewModel,
    externalMode: Boolean = false,
    contentTopPadding: Dp = 0.dp,
    onExternalDownloadQueued: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.state.collectAsState()
    val settingsRepository = remember(context) {
        context.applicationContext.appContainer.settingsRepository
    }
    val currentOnExternalDownloadQueued by rememberUpdatedState(onExternalDownloadQueued)
    var showCellularConfirmation by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                resources.getString(R.string.notification_permission_denied_tip),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(viewModel, context, externalMode) {
        viewModel.events.collect { event ->
            when (event) {
                is ParseEvent.CopySingleSubtitle -> context.copySingleSubtitle(event.entry)
                is ParseEvent.CopySingleAiSummary -> context.copySingleAiSummary(event.entry)
                is ParseEvent.DownloadQueued -> {
                    if (externalMode) {
                        currentOnExternalDownloadQueued()
                    }
                }
            }
        }
    }

    LaunchedEffect(state.notice, context) {
        val notice = state.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        viewModel.clearNotice()
    }

    RefreshLoginStateOnStart(viewModel)

    val copyDialog = state.copyDialog
    ParseScreenContent(
        state = state,
        inputText = state.inputText,
        contentTopPadding = contentTopPadding,
        externalMode = externalMode,
        subtitleCopyDialogEntries =
            (copyDialog as? ParseCopyDialogState.Subtitles)?.entries,
        aiSummaryCopyDialogEntries =
            (copyDialog as? ParseCopyDialogState.AiSummaries)?.entries,
        onInputChange = viewModel::setInputText,
        onPaste = {
            context.readClipboardText()?.let(viewModel::setInputText)
        },
        onParse = viewModel::parse,
        onMediaTypeChange = viewModel::setMediaType,
        onDownload = {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (
                settingsRepository.shouldConfirmCellularDownload() &&
                context.isOnCellularNetwork()
            ) {
                showCellularConfirmation = true
            } else {
                viewModel.download()
            }
        },
        onSectionChange = viewModel::selectSection,
        onOpenUpper = context::openUpperSpace,
        onCopyResultContent = context::copyResultContent,
        onSelectAllItems = viewModel::selectAllItems,
        onClearSelectedItems = viewModel::clearSelectedItems,
        onLoadPrevPage = viewModel::loadPrevPage,
        onLoadNextPage = viewModel::loadNextPage,
        onLoadPage = viewModel::loadPage,
        onItemClick = viewModel::onItemRowClick,
        onItemSelectionChange = { index, _ -> viewModel.toggleItemSelection(index) },
        onFormatChange = viewModel::setFormat,
        onOutputTypeChange = viewModel::setOutputType,
        onCollectionModeChange = viewModel::setCollectionMode,
        onResolutionModeChange = viewModel::setResolutionMode,
        onResolutionChange = viewModel::setResolution,
        onCodecChange = viewModel::setCodec,
        onAudioBitrateModeChange = viewModel::setAudioBitrateMode,
        onAudioBitrateChange = viewModel::setAudioBitrate,
        onSubtitleEnabledChange = viewModel::setSubtitleEnabled,
        onSubtitleLanguageChange = viewModel::setSubtitleLanguageSelection,
        onCopySubtitles = viewModel::copySubtitlesNow,
        onAiSummaryEnabledChange = viewModel::setAiSummaryEnabled,
        onCopyAiSummaries = viewModel::copyAiSummariesNow,
        onNfoCollectionEnabledChange = viewModel::setNfoCollectionEnabled,
        onNfoSingleEnabledChange = viewModel::setNfoSingleEnabled,
        onDanmakuLiveEnabledChange = viewModel::setDanmakuLiveEnabled,
        onDanmakuHistoryEnabledChange = viewModel::setDanmakuHistoryEnabled,
        onDanmakuDateChange = viewModel::setDanmakuDate,
        onDanmakuHourChange = viewModel::setDanmakuHour,
        onImageSelectionChange = viewModel::setImageSelection,
        onOpusContentEnabledChange = viewModel::setOpusContentEnabled,
        onOpusImagesEnabledChange = viewModel::setOpusImagesEnabled,
        onDismissSubtitleCopyDialog = viewModel::dismissCopyDialog,
        onDismissAiSummaryCopyDialog = viewModel::dismissCopyDialog,
        onCopyCurrentSubtitle = context::copyCurrentSubtitle,
        onCopyAllSubtitles = context::copyAllSubtitles,
        onCopyCurrentAiSummary = context::copyCurrentAiSummary,
        onCopyAllAiSummaries = context::copyAllAiSummaries,
        onDismissError = viewModel::clearError,
    )

    if (showCellularConfirmation) {
        AlertDialog(
            onDismissRequest = { showCellularConfirmation = false },
            title = { Text(text = resources.getString(R.string.parse_mobile_confirm_title)) },
            text = { Text(text = resources.getString(R.string.parse_mobile_confirm_message)) },
            dismissButton = {
                TextButton(onClick = { showCellularConfirmation = false }) {
                    Text(text = resources.getString(android.R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCellularConfirmation = false
                        viewModel.download()
                    },
                ) {
                    Text(text = resources.getString(R.string.parse_mobile_confirm_action))
                }
            },
        )
    }
}

@Composable
private fun RefreshLoginStateOnStart(viewModel: ParseViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findActivity() as? LifecycleOwner }
    DisposableEffect(lifecycleOwner, viewModel) {
        viewModel.refreshLoginState()
        if (lifecycleOwner == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    viewModel.refreshLoginState()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.readClipboardText(): String? {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
    return clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.text
        ?.toString()
        ?.takeIf { it.isNotEmpty() }
}

private fun Context.isOnCellularNetwork(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
        return true
    }
    val hasWifiLikeTransport =
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    return !hasWifiLikeTransport && connectivityManager.isActiveNetworkMetered
}

private fun Context.copySingleSubtitle(entry: SubtitleCopyEntry) {
    val content = entry.content
    if (!content.isNullOrBlank()) {
        copyTextToClipboard(
            getString(R.string.parse_subtitle_copy_clip_label_single, entry.title),
            content,
        )
        Toast.makeText(
            this,
            getString(R.string.parse_subtitle_copy_single_done),
            Toast.LENGTH_SHORT,
        ).show()
    } else {
        Toast.makeText(
            this,
            entry.error ?: getString(R.string.parse_error_no_subtitle),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun Context.copySingleAiSummary(entry: AiSummaryCopyEntry) {
    val content = entry.content
    if (!content.isNullOrBlank()) {
        copyTextToClipboard(
            getString(R.string.parse_ai_summary_copy_clip_label_single, entry.title),
            content,
        )
        Toast.makeText(
            this,
            getString(R.string.parse_ai_summary_copy_single_done),
            Toast.LENGTH_SHORT,
        ).show()
    } else {
        Toast.makeText(
            this,
            entry.error ?: getString(R.string.parse_error_no_ai),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun Context.copyResultContent(target: ParseResultCopyTarget, content: String) {
    if (target == ParseResultCopyTarget.PublicId) {
        val identifierName = copiedIdentifierName(content)
        copyTextToClipboard(
            label = getString(R.string.parse_metadata_identifier_clip_label, identifierName),
            content = content,
        )
        Toast.makeText(
            this,
            getString(R.string.parse_metadata_identifier_copied, identifierName),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val (clipLabelRes, feedbackRes) = when (target) {
        ParseResultCopyTarget.Title ->
            R.string.common_title_clip_label to R.string.common_title_copied
        ParseResultCopyTarget.Description ->
            R.string.parse_description_clip_label to R.string.parse_description_copied
        ParseResultCopyTarget.CoverUrl ->
            R.string.parse_cover_url_clip_label to R.string.parse_cover_url_copied
        ParseResultCopyTarget.UpperName ->
            R.string.common_upper_name_clip_label to R.string.common_upper_name_copied
        ParseResultCopyTarget.PublicId -> return
        ParseResultCopyTarget.DetailValue ->
            R.string.parse_metadata_value_clip_label to R.string.parse_metadata_value_copied
    }
    copyTextWithFeedback(
        content = content,
        clipLabelRes = clipLabelRes,
        feedbackRes = feedbackRes,
    )
}

private fun copiedIdentifierName(content: String): String = when {
    content.startsWith("BV", ignoreCase = true) -> "BV 号"
    content.startsWith("ep", ignoreCase = true) -> "ep 号"
    content.startsWith("ss", ignoreCase = true) -> "ss 号"
    content.startsWith("au", ignoreCase = true) -> "au 号"
    content.startsWith("cv", ignoreCase = true) -> "cv 号"
    content.startsWith("am", ignoreCase = true) -> "am 号"
    content.startsWith("rl", ignoreCase = true) -> "rl 号"
    else -> "图文动态号"
}

private fun Context.openUpperSpace(mid: Long) {
    val intent = Intent(Intent.ACTION_VIEW, "https://space.bilibili.com/$mid".toUri())
    runCatching { startActivity(intent) }.onFailure {
        Toast.makeText(
            this,
            getString(R.string.common_open_link_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun Context.copyCurrentSubtitle(entry: SubtitleCopyEntry) {
    val content = entry.content
    if (content.isNullOrBlank()) {
        Toast.makeText(
            this,
            entry.error ?: getString(R.string.parse_subtitle_copy_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    copyTextToClipboard(
        getString(R.string.parse_subtitle_copy_clip_label_single, entry.title),
        content,
    )
    Toast.makeText(
        this,
        getString(R.string.parse_subtitle_copy_current_done),
        Toast.LENGTH_SHORT,
    ).show()
}

private fun Context.copyAllSubtitles(entries: List<SubtitleCopyEntry>) {
    val merged = entries.mapNotNull { entry ->
        val content = entry.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val subtitlePart = entry.subtitleName
            ?.takeIf { it.isNotBlank() }
            ?.let { " · $it" }
            .orEmpty()
        "【${entry.title}$subtitlePart】\n$content"
    }.joinToString("\n\n")
    if (merged.isBlank()) {
        Toast.makeText(
            this,
            getString(R.string.parse_subtitle_copy_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    copyTextToClipboard(getString(R.string.parse_subtitle_copy_clip_label_all), merged)
    Toast.makeText(
        this,
        getString(R.string.parse_subtitle_copy_all_done),
        Toast.LENGTH_SHORT,
    ).show()
}

private fun Context.copyCurrentAiSummary(entry: AiSummaryCopyEntry) {
    val content = entry.content
    if (content.isNullOrBlank()) {
        Toast.makeText(
            this,
            entry.error ?: getString(R.string.parse_ai_summary_copy_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    copyTextToClipboard(
        getString(R.string.parse_ai_summary_copy_clip_label_single, entry.title),
        content,
    )
    Toast.makeText(
        this,
        getString(R.string.parse_ai_summary_copy_current_done),
        Toast.LENGTH_SHORT,
    ).show()
}

private fun Context.copyAllAiSummaries(entries: List<AiSummaryCopyEntry>) {
    val merged = entries.mapNotNull { entry ->
        val content = entry.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        "【${entry.title}】\n$content"
    }.joinToString("\n\n")
    if (merged.isBlank()) {
        Toast.makeText(
            this,
            getString(R.string.parse_ai_summary_copy_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    copyTextToClipboard(getString(R.string.parse_ai_summary_copy_clip_label_all), merged)
    Toast.makeText(
        this,
        getString(R.string.parse_ai_summary_copy_all_done),
        Toast.LENGTH_SHORT,
    ).show()
}
