package com.happycola233.bilitools.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * B 站图床（`*.hdslb.com`、`*.biliimg.com` 的 `/bfs/` 路径）支持在文件名后追加 `@` 格式化参数，
 * 由服务端缩放转码，参见 bilibili-API-collect `docs/misc/picture.md`。
 */
object BiliImageUrls {
    /**
     * 返回仅供界面展示的缩略图地址：等比缩放到恰好覆盖 [widthPx]×[heightPx]（`1e` 即「保留比例取其大」，
     * 与 `ContentScale.Crop` 的裁切语义一致），并转为体积更小的 WebP。
     * 非 B 站图床或已带格式化参数的地址原样返回。
     */
    fun thumbnail(url: String, widthPx: Int, heightPx: Int): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url
        val isBfsImage = (httpUrl.host.endsWith(".hdslb.com") || httpUrl.host.endsWith(".biliimg.com")) &&
            httpUrl.encodedPath.startsWith("/bfs/") &&
            '@' !in httpUrl.encodedPath
        if (!isBfsImage) return url
        return httpUrl.newBuilder()
            .encodedPath("${httpUrl.encodedPath}@${widthPx}w_${heightPx}h_1e.webp")
            .build()
            .toString()
    }
}
