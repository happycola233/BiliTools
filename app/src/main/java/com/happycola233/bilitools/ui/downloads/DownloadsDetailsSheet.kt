package com.happycola233.bilitools.ui.downloads

import android.content.Context
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.happycola233.bilitools.core.localized
import com.happycola233.bilitools.core.localizedStatusDetail
import com.happycola233.bilitools.core.localizedErrorMessage
import com.happycola233.bilitools.core.localizedEmbedWarning
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.ui.copyTextWithFeedback
import com.happycola233.bilitools.ui.longPressAction
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.AppSurfaces
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DownloadOutputDetails(val path: String?, val sizeBytes: Long?)

/** 仅在打开详情或成品发生变化时查询 MediaStore，不把磁盘查询放进列表重组。 */
private fun readDownloadOutput(context: Context, uri: String): DownloadOutputDetails? = runCatching {
    val outputUri = uri.toUri()
    val outputSize = context.contentResolver.openFileDescriptor(outputUri, "r")?.use {
        it.statSize.takeIf { size -> size >= 0L }
    }
    context.contentResolver.query(
        outputUri,
        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.SIZE),
        null, null, null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val directory = cursor.getString(1)
        val name = cursor.getString(0)
        DownloadOutputDetails(
            path = directory?.let { "${it.trimEnd('/')}/$name" },
            sizeBytes = outputSize ?: if (cursor.isNull(2)) null else cursor.getLong(2).takeIf { it >= 0L },
        )
    }
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsDetailsSheet(group: DownloadGroup, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val metadata = group.detailsMetadata
    val savedTasks = group.tasks.filter { it.status == DownloadStatus.Success && !it.outputMissing }
    val savedOutputs = savedTasks.mapNotNull { task -> task.localUri?.let { task.id to it } }
    val outputDetails by produceState<Map<Long, DownloadOutputDetails>>(
        initialValue = emptyMap(), group.id, savedOutputs,
    ) {
        value = withContext(Dispatchers.IO) {
            savedOutputs.mapNotNull { (id, uri) -> readDownloadOutput(context, uri)?.let { id to it } }.toMap()
        }
    }
    val knownSizes = savedTasks.mapNotNull { outputDetails[it.id]?.sizeBytes ?: it.outputBytes }
    val savedSize = when {
        savedTasks.isEmpty() -> null
        knownSizes.isEmpty() -> stringResource(R.string.downloads_size_unknown)
        knownSizes.size < savedTasks.size -> stringResource(
            R.string.downloads_size_known, context.formatDownloadBytes(knownSizes.sum()),
        )
        else -> context.formatDownloadBytes(knownSizes.sum())
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurfaces.pageContainerColor,
    ) {
        // 标题留在滚动区域外，浏览长列表时仍可直接关闭面板。
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.downloads_details_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) {
                Icon(painterResource(R.drawable.ic_close_rounded_24), stringResource(R.string.downloads_details_close))
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            item(key = "heading") {
                Column(Modifier.padding(bottom = 20.dp)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.longPressAction(
                            interactionKey = group.title,
                            actionLabel = stringResource(R.string.common_copy_title),
                            feedbackShape = MaterialTheme.shapes.medium,
                            feedbackOutset = 4.dp,
                            restrictToBounds = true,
                            onLongPress = {
                                context.copyTextWithFeedback(group.title, R.string.common_title_clip_label, R.string.common_title_copied)
                            },
                        ),
                    )
                    Text(
                        listOfNotNull(
                            stringResource(R.string.downloads_saved_count, savedTasks.size, group.tasks.size),
                            savedSize,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    val failedCount = group.tasks.count { it.status == DownloadStatus.Failed }
                    val missingCount = group.tasks.count { it.status == DownloadStatus.Success && it.outputMissing }
                    val issues = listOfNotNull(
                        failedCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.downloads_group_failed_count, it, it) },
                        missingCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.downloads_group_missing_count, it, it) },
                    )
                    if (issues.isNotEmpty()) {
                        DownloadDetailStatus(
                            issues.joinToString(" · "), R.drawable.ic_error_24, MaterialTheme.colorScheme.error,
                            Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            item(key = "files") {
                DownloadFilesHeading(group.relativePath.trimEnd('/'))
            }
            itemsIndexed(group.tasks, key = { _, task -> "file:${task.id}" }) { index, task ->
                if (index > 0) DetailsDivider()
                DownloadFileDetails(task, group.relativePath, outputDetails[task.id])
            }
            item(key = "source") {
                DetailsSectionTitle(stringResource(R.string.downloads_details_source))
                DownloadSourceDetails(group, metadata)
            }
            metadata?.comment?.takeIf(String::isNotBlank)?.let { description ->
                item(key = "description") {
                    DetailsSectionTitle(stringResource(R.string.runtime_description))
                    DetailValue("", description)
                }
            }
        }
    }
}

private data class DownloadDetailField(val label: String, val value: String)

@Composable
private fun DownloadSourceDetails(group: DownloadGroup, metadata: DownloadEmbeddedMetadata?) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.padding(bottom = 16.dp)) {
        group.subtitle?.takeIf { it.isNotBlank() && it != group.title }?.let {
            DetailValue(stringResource(R.string.runtime_episode_part), it)
        }
        metadata?.title?.takeIf { it != group.title && it != group.subtitle }?.let {
            DetailValue(stringResource(R.string.runtime_title), it)
        }
        DownloadDetailsGrid(buildList {
            metadata?.uploader?.let { add(DownloadDetailField(stringResource(R.string.runtime_uploader), it)) }
            metadata?.artist?.takeIf { it != metadata.uploader }?.let {
                add(DownloadDetailField(stringResource(R.string.runtime_author), it))
            }
            metadata?.album?.let {
                add(DownloadDetailField(stringResource(if (metadata.albumIsCollection) R.string.runtime_collection else R.string.runtime_album), it))
            }
            metadata?.trackNumber?.let { number ->
                add(DownloadDetailField(stringResource(R.string.runtime_track_number), metadata.trackTotal?.let { "$number / $it" } ?: number.toString()))
            }
            metadata?.durationSeconds?.let { seconds ->
                val duration = if (seconds >= 3600) {
                    String.format(locale, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
                } else {
                    String.format(locale, "%d:%02d", seconds / 60, seconds % 60)
                }
                add(DownloadDetailField(stringResource(R.string.runtime_duration), duration))
            }
            metadata?.publishedDate?.let { add(DownloadDetailField(stringResource(R.string.runtime_published_at), it)) }
            group.bvid?.let { add(DownloadDetailField(stringResource(R.string.runtime_content_id), it)) }
        })
        DetailValue(stringResource(R.string.runtime_created_at), formatDownloadCreatedAt(context, group.createdAt))
        group.sourceUrl()?.let { DetailValue(stringResource(R.string.runtime_source_url), it) }
        metadata?.tags?.takeIf(List<String>::isNotEmpty)?.let {
            DetailValue(stringResource(R.string.runtime_tags), it.joinToString(" · "))
        }
    }
}

@Composable
private fun DownloadFileDetails(item: DownloadItem, directory: String, output: DownloadOutputDetails?) {
    val context = LocalContext.current
    val mediaParams = item.mediaParams?.localized(context)
    val status = when (item.status) {
        DownloadStatus.Pending -> stringResource(R.string.download_status_pending)
        DownloadStatus.Running -> if (item.progressIndeterminate) {
            item.localizedStatusDetail(context) ?: stringResource(R.string.downloads_group_preparing)
        } else stringResource(R.string.download_status_running, item.progress)
        DownloadStatus.Paused -> stringResource(R.string.download_status_paused, item.progress)
        DownloadStatus.Merging -> item.localizedStatusDetail(context) ?: stringResource(R.string.download_detail_merging)
        DownloadStatus.Success -> stringResource(if (item.outputMissing) R.string.download_status_missing else R.string.download_status_success)
        DownloadStatus.Failed -> stringResource(R.string.download_status_failed)
        DownloadStatus.Unavailable -> stringResource(R.string.download_status_unavailable)
        DownloadStatus.Cancelled -> stringResource(R.string.download_status_cancelled)
    }
    val outputMissing = item.status == DownloadStatus.Success && item.outputMissing
    val statusColor = when {
        item.status == DownloadStatus.Failed || outputMissing -> MaterialTheme.colorScheme.error
        item.status == DownloadStatus.Unavailable -> MaterialTheme.colorScheme.tertiary
        item.status == DownloadStatus.Running || item.status == DownloadStatus.Merging -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusIcon = when {
        item.status == DownloadStatus.Failed || outputMissing -> R.drawable.ic_error_24
        item.status == DownloadStatus.Success -> null
        item.status == DownloadStatus.Paused -> R.drawable.ic_pause_24
        item.status == DownloadStatus.Cancelled -> R.drawable.ic_close_rounded_24
        item.status == DownloadStatus.Unavailable -> R.drawable.ic_info_24
        else -> R.drawable.ic_notification_download_24
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        // 状态与文件标题并列；窄屏或大字体时自然换行，不压缩状态文字。
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                item.title.ifBlank { item.fileName },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 16.dp).semantics { heading() },
            )
            DownloadDetailStatus(status, statusIcon, statusColor)
        }
        // 异常说明紧跟状态，始终展开；完整路径收起也不会把失败原因一起藏住。
        if (item.status == DownloadStatus.Failed) {
            DetailValue(
                stringResource(R.string.runtime_failure_reason), resolveFailureReason(item.localizedErrorMessage(context)),
                valueColor = MaterialTheme.colorScheme.error,
            )
        }
        item.localizedStatusDetail(context)?.takeIf { item.status == DownloadStatus.Unavailable }?.let {
            DetailValue(stringResource(R.string.runtime_explanation), it, valueColor = statusColor)
        }
        item.localizedEmbedWarning(context)?.let {
            DetailValue(
                stringResource(R.string.runtime_incomplete_items), it,
                valueColor = MaterialTheme.colorScheme.tertiary,
            )
        }
        val path = output?.path ?: directory.takeIf(String::isNotBlank)?.let { "${it.trimEnd('/')}/${item.fileName}" }
        // 系统可能为重名文件追加序号，文件名与展开的位置均以实际成品为准。
        val fileName = output?.path?.substringAfterLast('/') ?: item.fileName
        DownloadFileLocation(
            fileName = fileName,
            path = path,
            pathLabel = stringResource(if (output?.path != null) R.string.runtime_saved_location else R.string.runtime_target_location),
        )
        val size = output?.sizeBytes ?: item.outputBytes.takeIf { item.status == DownloadStatus.Success }
        DownloadDetailsGrid(buildList {
            if (size != null) {
                add(DownloadDetailField(stringResource(R.string.runtime_file_size), context.formatDownloadBytes(size)))
            } else {
                buildDownloadSizeText(context, item)?.let { add(DownloadDetailField(stringResource(R.string.runtime_download_size), it)) }
            }
            fileName.substringAfterLast('.', "").takeIf(String::isNotBlank)?.let {
                add(DownloadDetailField(stringResource(R.string.runtime_file_format), it.uppercase(Locale.ROOT)))
            }
            mediaParams?.resolution?.let { add(DownloadDetailField(stringResource(R.string.runtime_quality), it)) }
            mediaParams?.codec?.let { add(DownloadDetailField(stringResource(R.string.runtime_video_codec), it)) }
            mediaParams?.audioBitrate?.let { add(DownloadDetailField(stringResource(R.string.runtime_audio_spec), it)) }
        })
        item.embeddedSubtitleTitles.takeIf { it.isNotEmpty() }?.let {
            DetailValue(stringResource(R.string.runtime_embedded_subtitles), it.joinToString(stringResource(R.string.runtime_list_separator)))
        }
        item.embeddedLyricsSource?.let { DetailValue(stringResource(R.string.runtime_embedded_lyrics), it) }
    }
}

@Composable
private fun DownloadDetailStatus(text: String, icon: Int?, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = color)
    }
}

/** 整组目录始终展开，作为文件区的公共信息展示。 */
@Composable
private fun DownloadFilesHeading(directory: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        DetailsSectionTitle(stringResource(R.string.downloads_details_storage))
        if (directory.isNotBlank()) {
            DetailValue(
                stringResource(R.string.runtime_download_directory), directory,
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 位置入口跟随文件名左对齐，与右上方的状态分开；共用文件名触摸区域，不额外撑高按钮行。 */
@Composable
private fun DownloadFileLocation(fileName: String, path: String?, pathLabel: String) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    var expanded by rememberSaveable(path) { mutableStateOf(false) }
    val interaction = if (path == null) Modifier.copyDownloadDetail(fileName) else Modifier.combinedClickable(
        role = Role.Button,
        onClickLabel = downloadPathActionLabel(expanded),
        onLongClickLabel = stringResource(R.string.parse_metadata_copy_value),
        hapticFeedbackEnabled = false,
        onLongClick = {
            haptics.longPress()
            context.copyTextWithFeedback(fileName, R.string.downloads_details_title, R.string.parse_metadata_value_copied)
        },
        onClick = { expanded = !expanded },
    )
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).then(interaction)
                .heightIn(min = 48.dp).padding(vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.runtime_filename),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                fileName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (path != null) DownloadPathLabel(pathLabel, expanded, Modifier.padding(top = 6.dp))
        }
        if (path != null) {
            AnimatedVisibility(expanded) {
                DetailValue("", path, valueColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DownloadPathLabel(label: String, expanded: Boolean, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "downloadPathArrow")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            painterResource(R.drawable.ic_expand_more_24), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).rotate(rotation),
        )
    }
}

@Composable
private fun downloadPathActionLabel(expanded: Boolean): String = stringResource(
    if (expanded) R.string.downloads_details_collapse_path else R.string.downloads_details_expand_path,
)

/** 常规宽度并排呈现两项参数；大字体或窄屏改为单列，保持取值完整。 */
@Composable
private fun DownloadDetailsGrid(fields: List<DownloadDetailField>) {
    if (fields.isEmpty()) return
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val columnCount = if (maxWidth >= 300.dp && fontScale < 1.3f) 2 else 1
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = columnCount,
        ) {
            fields.forEach { field ->
                DetailValue(field.label, field.value, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailsSectionTitle(title: String) {
    DetailsDivider()
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun DetailsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** 字段统一上下排列，短字段交给网格并排，长内容直接占满行宽。 */
@Composable
private fun DetailValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier.padding(vertical = 4.dp)) {
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.padding(top = if (label.isEmpty()) 0.dp else 4.dp).copyDownloadDetail(value),
        )
    }
}

@Composable
private fun Modifier.copyDownloadDetail(value: String): Modifier {
    val context = LocalContext.current
    return longPressAction(
        interactionKey = value,
        actionLabel = stringResource(R.string.parse_metadata_copy_value),
        feedbackShape = MaterialTheme.shapes.medium,
        feedbackOutset = 4.dp,
        restrictToBounds = true,
        onLongPress = {
            context.copyTextWithFeedback(value, R.string.downloads_details_title, R.string.parse_metadata_value_copied)
        },
    )
}
