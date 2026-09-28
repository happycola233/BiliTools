package com.happycola233.bilitools.ui.diagnostics

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLog
import com.happycola233.bilitools.core.DiagnosticReport
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.ui.AppAlertDialog
import com.happycola233.bilitools.ui.theme.AppAccents
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 关于页、下载详情和崩溃提示共用的生成、分享、保存入口。 */
@Composable
fun DiagnosticReportHost(focusTaskId: Long? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = context.appContainer.diagnosticReportRepository
    val settings by context.appContainer.settingsRepository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var description by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var report by remember { mutableStateOf<DiagnosticReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingSavePath by rememberSaveable { mutableStateOf<String?>(null) }
    fun failed(error: Exception) {
        AppLog.w("DiagnosticReport", "[report] operation failed", error)
        Toast.makeText(context, R.string.diagnostic_failed, Toast.LENGTH_LONG).show()
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val path = pendingSavePath
        pendingSavePath = null
        if (uri != null && path != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output -> File(path).inputStream().use { it.copyTo(output) } }
                        ?: throw java.io.IOException("Unable to open report destination")
                }
                Toast.makeText(context, R.string.diagnostic_saved, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed(error)
            }
        }
    }
    fun prepare(action: String) {
        scope.launch {
            busy = true
            try {
                val prepared = repository.prepare(description, focusTaskId)
                report = prepared.report
                when (action) {
                    "share" -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "BiliTools diagnostic report")
                            putExtra(Intent.EXTRA_STREAM, prepared.uri)
                            clipData = ClipData.newRawUri("BiliTools diagnostic report", prepared.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.diagnostic_share)))
                    }
                    "save" -> {
                        pendingSavePath = prepared.file.absolutePath
                        save.launch(prepared.file.name)
                    }
                    "preview" -> expanded = true
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed(error)
            } finally { busy = false }
        }
    }
    BiliToolsTheme(settings) {
        DiagnosticReportSheet(
            description = description,
            onDescriptionChange = { description = it; expanded = false },
            report = report,
            expanded = expanded,
            busy = busy,
            onTogglePreview = { if (expanded) expanded = false else prepare("preview") },
            onShare = { prepare("share") },
            onSave = { prepare("save") },
            onGitHub = {
                try { uriHandler.openUri(repository.issueUrl()) }
                catch (error: IllegalArgumentException) {
                    AppLog.w("DiagnosticReport", "[report] open issue page failed", error)
                    Toast.makeText(context, R.string.common_open_link_failed, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DiagnosticReportSheet(
    description: String,
    onDescriptionChange: (String) -> Unit,
    report: DiagnosticReport?,
    expanded: Boolean,
    busy: Boolean,
    onTogglePreview: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onGitHub: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = AppSurfaces.pageContainerColor,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.diagnostic_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(
                onClick = { scope.launch { state.hide(); onDismiss() } },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(painterResource(R.drawable.ic_close_rounded_24), stringResource(R.string.diagnostic_close))
            }
        }
        // 操作区留在滚动区域外，窄屏、长翻译和展开预览时仍能直接分享或保存。
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.diagnostic_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.diagnostic_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = description, onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.diagnostic_problem)) },
                placeholder = { Text(stringResource(R.string.diagnostic_problem_hint)) },
                shape = MaterialTheme.shapes.large,
                // 保留原生透明底，让浮动标签的裁切缺口与输入框内部使用同一层面板底色。
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
            )
            DiagnosticReportPreview(report, expanded, busy, onTogglePreview)
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onShare, enabled = !busy,
                    shapes = ButtonDefaults.shapesFor(48.dp),
                    colors = AppAccents.filledButtonColors(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_share_24), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostic_share))
                }
                FilledTonalButton(
                    onClick = onSave, enabled = !busy,
                    shapes = ButtonDefaults.shapesFor(48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_file_save_24), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostic_save))
                }
            }
            TextButton(onClick = onGitHub, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.diagnostic_github))
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_open_in_new_24), null, Modifier.size(16.dp))
            }
            Text(
                stringResource(R.string.diagnostic_attach_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiagnosticReportPreview(report: DiagnosticReport?, expanded: Boolean, busy: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "diagnosticPreviewArrow",
    )
    Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        TextButton(
            onClick = onToggle, enabled = !busy,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(if (expanded) R.string.diagnostic_hide_preview else R.string.diagnostic_preview),
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_expand_more_24), null,
                Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
            )
        }
        if (expanded && report != null) {
            // 报告标题与 key 固定英文；局部 LTR 防止阿拉伯语环境颠倒标点、编号和路径。
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(shape = MaterialTheme.shapes.medium, color = AppSurfaces.insetContainerColor) {
                    SelectionContainer {
                        Text(
                            report.preview, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()).padding(12.dp),
                        )
                    }
                }
            }
            val timeRange = BidiFormatter.getInstance(LocalLayoutDirection.current == LayoutDirection.Rtl)
                .unicodeWrap(report.logTimeRange, TextDirectionHeuristicsCompat.LTR)
            Text(
                stringResource(R.string.diagnostic_log_summary, report.logLineCount, timeRange),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
internal fun ClearDiagnosticRecordsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_delete_sweep_24), null) },
        title = { Text(stringResource(R.string.diagnostic_clear)) },
        text = { Text(stringResource(R.string.diagnostic_clear_explanation)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.diagnostic_clear_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
internal fun DiagnosticCrashDialog(onExport: () -> Unit, onIgnore: () -> Unit) {
    AlertDialog(
        onDismissRequest = onIgnore,
        title = { Text(stringResource(R.string.diagnostic_crash_title)) },
        text = { Text(stringResource(R.string.diagnostic_crash_description)) },
        confirmButton = { TextButton(onClick = onExport) { Text(stringResource(R.string.diagnostic_export)) } },
        dismissButton = { TextButton(onClick = onIgnore) { Text(stringResource(R.string.diagnostic_ignore)) } },
    )
}

@Composable
fun DiagnosticStartupPrompt() {
    val history = LocalContext.current.appContainer.diagnosticExitHistory
    var exit by remember { mutableStateOf<com.happycola233.bilitools.core.DiagnosticExit?>(null) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(history) { exit = withContext(Dispatchers.IO) { history.pendingProblem() } }
    exit?.let { problem ->
        DiagnosticCrashDialog(
            onExport = { history.acknowledge(problem); exit = null; showReport = true },
            onIgnore = { history.acknowledge(problem); exit = null },
        )
    }
    if (showReport) DiagnosticReportHost(onDismiss = { showReport = false })
}
