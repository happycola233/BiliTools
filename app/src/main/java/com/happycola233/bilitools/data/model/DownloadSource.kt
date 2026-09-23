package com.happycola233.bilitools.data.model

/** 同一条码流的完整地址集合；刷新或换源不能改变其分 P、画质和编码。 */
data class DownloadSource(
    val url: String,
    val backupUrls: List<String> = emptyList(),
)

fun VideoStream.downloadSource() = DownloadSource(url, backupUrls)

fun AudioStream.downloadSource() = DownloadSource(url, backupUrls)
