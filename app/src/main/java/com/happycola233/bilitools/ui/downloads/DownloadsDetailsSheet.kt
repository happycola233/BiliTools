package com.happycola233.bilitools.ui.downloads

import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.happycola233.bilitools.core.localized
import com.happycola233.bilitools.core.localizedStatusDetail
import com.happycola233.bilitools.core.localizedErrorMessage
import com.happycola233.bilitools.core.localizedEmbedWarning
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.ui.copyTextWithFeedback
import com.happycola233.bilitools.ui.longPressAction
import com.happycola233.bilitools.ui.theme.AppSurfaces
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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
    val locale = LocalConfiguration.current.locales[0]
    val metadata = group.detailsMetadata
    val savedOutputs = group.tasks.filter { it.status == DownloadStatus.Success && !it.outputMissing }
        .mapNotNull { task -> task.localUri?.let { task.id to it } }
    val outputDetails by produceState<Map<Long, DownloadOutputDetails>>(
        initialValue = emptyMap(), group.id, savedOutputs,
    ) {
        value = withContext(Dispatchers.IO) {
            savedOutputs.mapNotNull { (id, uri) -> readDownloadOutput(context, uri)?.let { id to it } }.toMap()
        }
    }
    val savedTasks = group.tasks.filter { it.status == DownloadStatus.Success && !it.outputMissing }
    val knownSizes = savedTasks.mapNotNull { outputDetails[it.id]?.sizeBytes ?: it.outputBytes }
    val savedSize = when {
        savedTasks.isEmpty() -> stringResource(R.string.downloads_no_saved_files)
        knownSizes.isEmpty() -> stringResource(R.string.downloads_size_unknown)
        knownSizes.size < savedTasks.size -> stringResource(
            R.string.downloads_size_known, context.formatDownloadBytes(knownSizes.sum()),
        )
        else -> context.formatDownloadBytes(knownSizes.sum())
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = AppSurfaces.pageContainerColor,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "heading") {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        stringResource(R.string.downloads_details_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp).longPressAction(
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
                    listOf(
                        stringResource(R.string.downloads_saved_count, savedTasks.size, group.tasks.size),
                        savedSize,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item(key = "source") {
                DetailsSectionTitle(stringResource(R.string.downloads_details_source))
                group.subtitle?.takeIf { it.isNotBlank() && it != group.title }?.let { DetailValue(stringResource(R.string.runtime_episode_part), it, fullWidthValue = true) }
                metadata?.title?.takeIf { it != group.title && it != group.subtitle }?.let { DetailValue(stringResource(R.string.runtime_title), it, fullWidthValue = true) }
                metadata?.uploader?.let { DetailValue(stringResource(R.string.runtime_uploader), it) }
                metadata?.artist?.takeIf { it != metadata.uploader }?.let { DetailValue(stringResource(R.string.runtime_author), it) }
                metadata?.album?.let { DetailValue(if (metadata.albumIsCollection) stringResource(R.string.runtime_collection) else stringResource(R.string.runtime_album), it) }
                metadata?.trackNumber?.let { number ->
                    DetailValue(stringResource(R.string.runtime_track_number), metadata.trackTotal?.let { "$number / $it" } ?: number.toString())
                }
                metadata?.durationSeconds?.let { seconds ->
                    val duration = if (seconds >= 3600) {
                        String.format(locale, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
                    } else {
                        String.format(locale, "%d:%02d", seconds / 60, seconds % 60)
                    }
                    DetailValue(stringResource(R.string.runtime_duration), duration)
                }
                metadata?.publishedDate?.let { DetailValue(stringResource(R.string.runtime_published_at), it) }
                group.bvid?.let { DetailValue(stringResource(R.string.runtime_content_id), it) }
                DetailValue(stringResource(R.string.runtime_created_at), formatDownloadCreatedAt(context, group.createdAt))
                group.sourceUrl()?.let { DetailValue(stringResource(R.string.runtime_source_url), it, fullWidthValue = true) }
                metadata?.tags?.takeIf(List<String>::isNotEmpty)?.let { DetailValue(stringResource(R.string.runtime_tags), it.joinToString(" · "), fullWidthValue = true) }
            }
            item(key = "directory") {
                DetailsSectionTitle(stringResource(R.string.downloads_details_storage))
                group.relativePath.takeIf(String::isNotBlank)?.let { DetailValue(stringResource(R.string.runtime_download_directory), it.trimEnd('/'), fullWidthValue = true) }
            }
            itemsIndexed(group.tasks, key = { _, task -> "file:${task.id}" }) { index, task ->
                if (index > 0) DetailsDivider()
                DownloadFileDetails(task, group.relativePath, outputDetails[task.id])
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

@Composable
private fun DownloadFileDetails(item: DownloadItem, directory: String, output: DownloadOutputDetails?) {
    val context = LocalContext.current
    val mediaParams = item.mediaParams?.localized(context)
    val status = when (item.status) {
        DownloadStatus.Pending -> stringResource(R.string.download_status_pending)
        DownloadStatus.Running -> stringResource(R.string.download_status_running, item.progress)
        DownloadStatus.Paused -> stringResource(R.string.download_status_paused, item.progress)
        DownloadStatus.Merging -> item.localizedStatusDetail(context) ?: stringResource(R.string.download_detail_merging)
        DownloadStatus.Success -> stringResource(if (item.outputMissing) R.string.download_status_missing else R.string.download_status_success)
        DownloadStatus.Failed -> stringResource(R.string.download_status_failed)
        DownloadStatus.Unavailable -> stringResource(R.string.download_status_unavailable)
        DownloadStatus.Cancelled -> stringResource(R.string.download_status_cancelled)
    }
    Column(Modifier.fillMaxWidth()) {
        Text(item.title.ifBlank { item.fileName }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (item.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        DetailValue(stringResource(R.string.runtime_filename), item.fileName, fullWidthValue = true)
        val size = output?.sizeBytes ?: item.outputBytes.takeIf { item.status == DownloadStatus.Success }
        if (size != null) {
            DetailValue(stringResource(R.string.runtime_file_size), context.formatDownloadBytes(size))
        } else {
            buildDownloadSizeText(context, item)?.let { DetailValue(stringResource(R.string.runtime_download_size), it) }
        }
        mediaParams?.resolution?.let { DetailValue(stringResource(R.string.runtime_quality), it) }
        mediaParams?.codec?.let { DetailValue(stringResource(R.string.runtime_video_codec), it) }
        mediaParams?.audioBitrate?.let { DetailValue(stringResource(R.string.runtime_audio_spec), it) }
        item.fileName.substringAfterLast('.', "").takeIf(String::isNotBlank)?.let {
            DetailValue(stringResource(R.string.runtime_file_format), it.uppercase(Locale.ROOT))
        }
        val path = output?.path ?: directory.takeIf(String::isNotBlank)?.let { "${it.trimEnd('/')}/${item.fileName}" }
        path?.let { DetailValue(if (output?.path != null) stringResource(R.string.runtime_saved_location) else stringResource(R.string.runtime_target_location), it, fullWidthValue = true) }
        if (item.status == DownloadStatus.Failed) DetailValue(stringResource(R.string.runtime_failure_reason), resolveFailureReason(item.localizedErrorMessage(context)), fullWidthValue = true)
        item.localizedStatusDetail(context)?.takeIf { item.status == DownloadStatus.Unavailable }?.let { DetailValue(stringResource(R.string.runtime_explanation), it, fullWidthValue = true) }
        item.embeddedSubtitleTitles.takeIf { it.isNotEmpty() }?.let {
            DetailValue(stringResource(R.string.runtime_embedded_subtitles), it.joinToString(stringResource(R.string.runtime_list_separator)), fullWidthValue = true)
        }
        item.embeddedLyricsSource?.let { DetailValue(stringResource(R.string.runtime_embedded_lyrics), it, fullWidthValue = true) }
        item.localizedEmbedWarning(context)?.let { DetailValue(stringResource(R.string.runtime_incomplete_items), it, fullWidthValue = true) }
    }
}

@Composable
private fun DetailsSectionTitle(title: String) {
    DetailsDivider()
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun DetailsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(bottom = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** 短字段按列对齐，文件名、路径等长内容使用整行宽度；两种排布都支持长按复制。 */
@Composable
private fun DetailValue(label: String, value: String, fullWidthValue: Boolean = false) {
    val context = LocalContext.current
    val copyModifier = Modifier
        .longPressAction(
            interactionKey = value,
            actionLabel = stringResource(R.string.parse_metadata_copy_value),
            feedbackShape = MaterialTheme.shapes.medium,
            feedbackOutset = 4.dp,
            restrictToBounds = true,
            onLongPress = {
                context.copyTextWithFeedback(value, R.string.downloads_details_title, R.string.parse_metadata_value_copied)
            },
        )
    val modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    if (fullWidthValue || label.isEmpty()) {
        Column(modifier) {
            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = if (label.isEmpty()) 0.dp else 4.dp).then(copyModifier),
            )
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f).then(copyModifier))
        }
    }
}
