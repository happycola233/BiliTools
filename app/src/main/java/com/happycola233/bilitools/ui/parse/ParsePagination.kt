package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem

/** 页边界保存实际返回的条目身份；详情补全可能改变 cid 等字段，列表身份仍保持初次入页时的值。 */
data class ParseLoadedPage(val page: Int, val itemKeys: List<String>)

data class ParseScrollRequest(val page: Int, val id: Int)

data class ParsePagination(
    val pages: List<ParseLoadedPage> = emptyList(),
    val hasMore: Boolean = false,
    val appending: Boolean = false,
    val appendError: String? = null,
    val scrollRequest: ParseScrollRequest? = null,
    /** 整个列表被替换时递增；自然滚动与追加不改变选中条目的取流身份。 */
    val generation: Int = 0,
) {
    val lastLoadedPage: Int? get() = pages.lastOrNull()?.page

    fun pageAt(index: Int): Int? {
        var remaining = index
        for (page in pages) {
            if (remaining in page.itemKeys.indices) return page.page
            remaining -= page.itemKeys.size
        }
        return null
    }

    fun keyAt(index: Int): String? {
        var remaining = index
        for (page in pages) {
            if (remaining in page.itemKeys.indices) return page.itemKeys[remaining]
            remaining -= page.itemKeys.size
        }
        return null
    }

    /** 缺项或空页不会改变后续页面的页码；跳过空页，定位到之后实际存在的第一条。 */
    fun firstIndexAtOrAfter(pageNumber: Int): Int? {
        var index = 0
        for (page in pages) {
            if (page.page >= pageNumber && page.itemKeys.isNotEmpty()) return index
            index += page.itemKeys.size
        }
        return null
    }
}

internal val ParseUiState.canGoToNextPage: Boolean
    get() = pagination.pages.any { it.page > pageIndex && it.itemKeys.isNotEmpty() } || pagination.hasMore

internal fun MediaInfo.hasNextPage(page: Int): Boolean = paged &&
    (hasMore ?: totalPages?.let { page < it } ?: false)

private fun MediaItem.pageItemKey(): String {
    val identity = when {
        // 音频也可能带关联视频的 aid/cid；应按歌曲自身的 sid 去重。
        sid != null -> "au:$sid"
        opid != null -> "opus:$opid"
        cvid != null -> "cv:$cvid"
        epid != null -> "ep:$epid"
        ssid != null -> "ss:$ssid"
        aid != null -> "av:$aid"
        bvid != null -> "bv:$bvid"
        else -> "url:$url"
    }
    return "$type:$identity:part:$page"
}

/** 新解析、切换收藏夹或跳到不相邻的未加载页时，建立新的连续页面窗口。 */
internal fun ParseUiState.withNewList(
    info: MediaInfo,
    page: Int,
    scrollRequest: ParseScrollRequest,
): ParseUiState {
    val newItems = if (info.paged) info.list.distinctBy { it.pageItemKey() } else info.list
    val defaultIndex = newItems.indexOfFirst { it.isTarget }.takeIf { it >= 0 } ?: 0
    return copy(
        mediaInfo = info.copy(list = newItems),
        items = newItems,
        selectedItemIndex = defaultIndex,
        selectedItemIndices = if (newItems.isEmpty()) emptyList() else listOf(defaultIndex),
        selectedItemStat = newItems.getOrNull(defaultIndex)?.stat,
        previewItemIndex = null,
        sections = info.sections,
        selectedSectionId = info.sections?.target,
        pageIndex = page,
        pagination = ParsePagination(
            pages = listOf(ParseLoadedPage(page, newItems.map { it.pageItemKey() })),
            hasMore = info.hasNextPage(page),
            generation = pagination.generation + 1,
            scrollRequest = scrollRequest,
        ),
    )
}

/** 只追加真正新增的条目；旧条目的补全详情、勾选下标、预览下标与流信息都原样保留。 */
internal fun ParseUiState.withAppendedPage(info: MediaInfo, page: Int): ParseUiState {
    val knownKeys = pagination.pages.flatMapTo(HashSet()) { it.itemKeys }
    val newItems = info.list.filter { knownKeys.add(it.pageItemKey()) }
    val combined = items + newItems
    val selectFirst = items.isEmpty() && newItems.isNotEmpty()
    return copy(
        // 容器封面可能取自该页首项，追加时保留已有封面，避免随翻页改变。
        mediaInfo = info.copy(list = combined, nfo = if (items.isEmpty()) info.nfo else mediaInfo!!.nfo),
        items = combined,
        selectedItemIndex = if (selectFirst) 0 else selectedItemIndex,
        selectedItemIndices = if (selectFirst) listOf(0) else selectedItemIndices,
        selectedItemStat = if (selectFirst) newItems.first().stat else selectedItemStat,
        pagination = pagination.copy(
            pages = pagination.pages + ParseLoadedPage(page, newItems.map { it.pageItemKey() }),
            hasMore = info.hasNextPage(page),
        ),
    )
}
