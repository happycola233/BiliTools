package com.happycola233.bilitools.data.model

data class HistoryTab(
    val type: String,
    val name: String,
)

data class HistoryItem(
    val title: String,
    val longTitle: String? = null,
    val coverUrl: String? = null,
    val coverUrls: List<String> = emptyList(),
    val uri: String? = null,
    val bvid: String? = null,
    val cid: Long? = null,
    val business: String? = null,
    val page: Int? = null,
    val part: String? = null,
    val videos: Int = 0,
    val authorName: String = "",
    val authorMid: Long? = null,
    val authorAvatarUrl: String? = null,
    val viewAt: Long = 0,
    val progress: Int = 0,
    val duration: Int = 0,
) {
    val displayCoverUrl: String?
        get() = coverUrls.firstOrNull() ?: coverUrl
}

data class HistoryCursorInfo(
    val tabs: List<HistoryTab>,
    val defaultBusiness: String?,
    val list: List<HistoryItem>,
)

data class HistorySearchParams(
    val page: Int = 1,
    val keyword: String = "",
    val business: String = "archive",
    val addTimeStart: Long = 0,
    val addTimeEnd: Long = 0,
    val arcMaxDuration: Int = 0,
    val arcMinDuration: Int = 0,
    val deviceType: Int = 0,
)

data class HistorySearchResult(
    val hasMore: Boolean,
    val total: Int,
    val page: Int,
    val list: List<HistoryItem>,
)
