package com.happycola233.bilitools.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal const val FAVORITE_PAGE_SIZE = 36
internal const val UPLOADS_LIST_PAGE_SIZE = 30
internal const val UPLOADS_DIRECTORY_PAGE_SIZE = 20

/** 新合集和旧系列使用不同的分页参数，不能复用同一份查询参数。 */
internal fun buildUploadsListUrl(mid: String, listId: Long, isSeason: Boolean, page: Int): HttpUrl {
    val path = if (isSeason) "x/polymer/web-space/seasons_archives_list" else "x/series/archives"
    return "https://api.bilibili.com/$path".toHttpUrl().newBuilder()
        .addQueryParameter("mid", mid)
        .addQueryParameter(if (isSeason) "season_id" else "series_id", listId.toString())
        .addQueryParameter(if (isSeason) "page_num" else "pn", page.toString())
        .addQueryParameter(if (isSeason) "page_size" else "ps", UPLOADS_LIST_PAGE_SIZE.toString())
        .build()
}
