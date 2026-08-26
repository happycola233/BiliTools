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
    val businessType = business?.trim()?.lowercase()
    // 直播没有可落盘的媒体流，即使接口偶发带上 uri 也不跳转解析。
    if (businessType == "live") return null

    val resolvedBvid = bvid?.trim().orEmpty()
    if (resolvedBvid.isNotBlank()) {
        return "https://www.bilibili.com/video/$resolvedBvid"
    }

    articleParseUrl(businessType)?.let { return it }

    val resolvedUri = uri?.trim().orEmpty()
    if (resolvedUri.isBlank()) return null

    if (businessType != null && businessType !in SUPPORTED_HISTORY_BUSINESS) {
        return null
    }

    val lowerUri = resolvedUri.lowercase()
    if (SUPPORTED_HISTORY_PATH_MARKERS.none { marker -> lowerUri.contains(marker) }) {
        return null
    }

    return resolvedUri
}

/**
 * 专栏历史通常不返回 uri / bvid，只能用 oid、cid 还原公开链接。
 * 文集条目的标题是当时阅读的那一篇，优先跳到该文而不是整本文集。
 */
private fun HistoryItem.articleParseUrl(businessType: String?): String? {
    if (businessType != "article" && businessType != "article-list") {
        return null
    }

    val resolvedUri = uri?.trim().orEmpty()
    if (resolvedUri.isNotBlank()) {
        val lowerUri = resolvedUri.lowercase()
        if (lowerUri.contains("/read/") || lowerUri.contains("/opus/")) {
            return resolvedUri
        }
    }

    if (businessType == "article-list") {
        cid?.takeIf { it > 0 }?.let { return articleUrl(it) }
        oid?.takeIf { it > 0 }?.let { return articleListUrl(it) }
        return null
    }

    return oid?.takeIf { it > 0 }?.let { articleUrl(it) }
}

private fun articleUrl(cvid: Long): String = "https://www.bilibili.com/read/cv$cvid"

private fun articleListUrl(rlid: Long): String =
    "https://www.bilibili.com/read/readlist/rl$rlid"
