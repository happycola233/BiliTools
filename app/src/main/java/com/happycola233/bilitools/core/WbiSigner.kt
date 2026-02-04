package com.happycola233.bilitools.core

import com.squareup.moshi.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class WbiSigner(private val httpClient: BiliHttpClient) {
    private var cachedMixinKey: String? = null
    private var lastUpdateMs: Long = 0L

    suspend fun signedUrl(baseUrl: String, params: Map<String, String>): HttpUrl {
        val mixinKey = getMixinKey()
        val wts = (System.currentTimeMillis() / 1000).toString()
        val merged = params.toMutableMap().apply {
            putIfAbsent("dm_img_str", DEFAULT_DM_IMG_STR)
            putIfAbsent("dm_cover_img_str", DEFAULT_DM_COVER_IMG_STR)
            putIfAbsent("dm_img_list", "[]")
            put("wts", wts)
        }
        val filtered = merged.mapValues { (_, v) -> v.replace(ILLEGAL_CHARS, "") }
        val sortedKeys = filtered.keys.sorted()
        val query = sortedKeys.joinToString("&") { key ->
            val value = filtered[key].orEmpty()
            "${encode(key)}=${encode(value)}"
        }
        val wRid = md5(query + mixinKey)
        val finalParams = filtered + mapOf("w_rid" to wRid)
        val builder = baseUrl.toHttpUrl().newBuilder()
        finalParams.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    private suspend fun getMixinKey(): String {
        val now = System.currentTimeMillis()
        val cached = cachedMixinKey
        if (cached != null && now - lastUpdateMs < TimeUnit.MINUTES.toMillis(10)) {
            return cached
        }
        val url = "https://api.bilibili.com/x/web-interface/nav".toHttpUrl()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(NavResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty nav response", -1)
        val data = resp.data ?: throw BiliHttpException(resp.message, resp.code)
        val imgKey = data.wbiImg.imgUrl.substringAfterLast("/").substringBefore(".")
        val subKey = data.wbiImg.subUrl.substringAfterLast("/").substringBefore(".")
        val mixinKey = mixinKeyEncTab
            .map { idx -> (imgKey + subKey)[idx] }
            .joinToString("")
            .substring(0, 32)
        cachedMixinKey = mixinKey
        lastUpdateMs = now
        return mixinKey
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    companion object {
        private val ILLEGAL_CHARS = "[!'()*]".toRegex()
        private const val DEFAULT_DM_IMG_STR = "bm8gd2ViZ2"
        private const val DEFAULT_DM_COVER_IMG_STR = "bm8gd2ViZ2wgZXh0ZW5zaW"
        private val mixinKeyEncTab = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
        )
    }
}

private data class NavResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: NavData?,
)

private data class NavData(
    @Json(name = "wbi_img") val wbiImg: WbiImg,
)

private data class WbiImg(
    @Json(name = "img_url") val imgUrl: String,
    @Json(name = "sub_url") val subUrl: String,
)
