package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun DownloadSource.orderedUrls(): List<String> =
    (listOf(url) + backupUrls)
        .distinct()
        .mapNotNull { value -> value.toHttpUrlOrNull()?.let { value to it } }
        .sortedBy { (_, url) -> url.downloadPriority() }
        .map { (value, _) -> value }

private fun HttpUrl.downloadPriority(): Int {
    val biliCdn = host.endsWith(".bilivideo.com") || host.endsWith(".bilivideo.cn")
    if (!biliCdn) return 3
    val storage = queryParameter("os").orEmpty()
    return when {
        host.contains("mirror") && storage.endsWith("bv") -> 0
        storage == "upos" -> 1
        host.startsWith("cn-") && storage == "bcache" -> 2
        else -> 3
    }
}
