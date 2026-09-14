package com.happycola233.bilitools.ui.downloads

import android.content.Context
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        item.outputBytes?.let { return formatDownloadBytes(it) }
        // 老记录尚未读到成品大小时，把传输量明确标成「已下载」，不冒充转码后的大小。
        val transferred = maxOf(item.downloadedBytes, item.totalBytes)
        return if (transferred > 0L) {
            context.getString(R.string.downloads_size_transferred, formatDownloadBytes(transferred))
        } else {
            context.getString(R.string.downloads_size_unknown)
        }
    }
    return when {
        item.totalBytes > 0L -> context.getString(
            R.string.download_size_progress,
            formatDownloadBytes(item.downloadedBytes),
            formatDownloadBytes(item.totalBytes),
        )
        item.downloadedBytes > 0L -> context.getString(
            R.string.downloads_size_transferred,
            formatDownloadBytes(item.downloadedBytes),
        )
        else -> context.getString(R.string.downloads_size_unknown)
    }
}

internal fun formatDownloadCreatedAt(context: Context, createdAt: Long): String =
    if (createdAt <= 0L) {
        context.getString(R.string.download_time_unknown)
    } else {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(createdAt))
    }

internal fun formatDownloadBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}
