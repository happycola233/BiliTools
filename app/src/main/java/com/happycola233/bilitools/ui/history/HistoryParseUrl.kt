package com.happycola233.bilitools.ui.history

import com.happycola233.bilitools.data.model.HistoryItem

private val SUPPORTED_HISTORY_BUSINESS = setOf("archive", "pgc", "cheese")
private val SUPPORTED_HISTORY_PATH_MARKERS = listOf(
    "/video/",
    "/bangumi/play/",
    "/cheese/play/",
    "/watchlater",
)

internal fun HistoryItem.toParseUrl(): String? {
    val resolvedBvid = bvid?.trim().orEmpty()
    if (resolvedBvid.isNotBlank()) {
        return "https://www.bilibili.com/video/$resolvedBvid"
    }

    val resolvedUri = uri?.trim().orEmpty()
    if (resolvedUri.isBlank()) return null

    val businessType = business?.trim()?.lowercase()
    if (businessType != null && businessType !in SUPPORTED_HISTORY_BUSINESS) {
        return null
    }

    val lowerUri = resolvedUri.lowercase()
    if (SUPPORTED_HISTORY_PATH_MARKERS.none { marker -> lowerUri.contains(marker) }) {
        return null
    }

    return resolvedUri
}
