package com.happycola233.bilitools.ui.downloads

import android.content.Context
import com.happycola233.bilitools.core.formatByteCount
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import java.text.DateFormat
import java.util.Date

internal val DownloadGroup.detailsMetadata: DownloadEmbeddedMetadata?
    get() = sourceMetadata ?: tasks.firstNotNullOfOrNull { it.embeddedMetadata }

internal fun DownloadGroup.sourceUrl(): String? =
    detailsMetadata?.originalUrl?.takeIf(String::isNotBlank) ?: bvid?.let { contentId ->
        // 兼容尚未保存来源信息的旧记录；此字段历史上同时存放 BV 号与专栏 cv 号。
        when {
            contentId.startsWith("BV") -> "https://www.bilibili.com/video/$contentId"
            contentId.startsWith("cv") -> "https://www.bilibili.com/read/$contentId"
            else -> null
        }
    }

internal val DownloadItem.isAudioVideoFile: Boolean
    get() = taskType == DownloadTaskType.AudioVideo ||
        taskType == DownloadTaskType.Video || taskType == DownloadTaskType.Audio

internal fun buildDownloadSizeText(context: Context, item: DownloadItem): String? {
    if (!item.isAudioVideoFile) return null
    if (item.status == DownloadStatus.Success) {
        item.outputBytes?.let { return context.formatDownloadBytes(it) }
        // 老记录尚未读到成品大小时，把传输量明确标成「已下载」，不冒充转码后的大小。
        val transferred = maxOf(item.downloadedBytes, item.totalBytes)
        return if (transferred > 0L) {
            context.getString(R.string.downloads_size_transferred, context.formatDownloadBytes(transferred))
        } else {
            context.getString(R.string.downloads_size_unknown)
        }
    }
    return when {
        item.totalBytes > 0L -> context.getString(
            R.string.download_size_progress,
            context.formatDownloadBytes(item.downloadedBytes),
            context.formatDownloadBytes(item.totalBytes),
        )
        item.downloadedBytes > 0L -> context.getString(
            R.string.downloads_size_transferred,
            context.formatDownloadBytes(item.downloadedBytes),
        )
        else -> context.getString(R.string.downloads_size_unknown)
    }
}

internal fun formatDownloadCreatedAt(context: Context, createdAt: Long): String =
    if (createdAt <= 0L) {
        context.getString(R.string.download_time_unknown)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, context.resources.configuration.locales[0])
            .format(Date(createdAt))
    }

internal fun Context.formatDownloadBytes(bytes: Long): String = formatByteCount(bytes)
