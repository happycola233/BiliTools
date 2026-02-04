package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.data.model.PlayUrlInfo
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoId
import com.happycola233.bilitools.data.model.VideoInfo
import com.happycola233.bilitools.data.model.VideoPage
import com.happycola233.bilitools.data.model.VideoStream
import com.squareup.moshi.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

class VideoRepository(
    private val httpClient: BiliHttpClient,
    private val wbiSigner: WbiSigner,
    private val cookieStore: CookieStore,
) {
    suspend fun getVideoInfo(input: String): VideoInfo {
        val id = parseVideoId(input)
        val baseUrl = "https://api.bilibili.com/x/web-interface/view"
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
        if (id.bvid != null) {
            urlBuilder.addQueryParameter("bvid", id.bvid)
        } else if (id.aid != null) {
            urlBuilder.addQueryParameter("aid", id.aid)
        } else {
            throw IllegalArgumentException("Unsupported video id")
        }
        val body = httpClient.get(urlBuilder.build())
        val adapter = httpClient.adapter(ViewResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty view response", -1)
        if (resp.code != 0 || resp.data == null) {
            throw BiliHttpException(resp.message, resp.code)
        }
        val tags = runCatching {
            val tagBody = httpClient.get(buildTagUrl(id).toHttpUrl())
            val tagAdapter = httpClient.adapter(TagResponse::class.java)
            val tagResp = tagAdapter.fromJson(tagBody)
            tagResp?.data?.mapNotNull { it.tagName } ?: emptyList()
        }.getOrDefault(emptyList())
        val pages = resp.data.pages?.map {
            VideoPage(it.cid, it.part, it.duration)
        } ?: listOf(VideoPage(resp.data.cid, resp.data.title, resp.data.duration))
        return VideoInfo(
            bvid = resp.data.bvid,
            aid = resp.data.aid,
            title = resp.data.title,
            description = resp.data.desc,
            coverUrl = normalizeCoverUrl(resp.data.pic),
            duration = resp.data.duration,
            pubTime = resp.data.pubdate,
            ownerName = resp.data.owner?.name,
            ownerMid = resp.data.owner?.mid,
            ownerAvatar = resp.data.owner?.face,
            tags = tags,
            collectionTitle = resp.data.ugcSeason?.title,
            collectionCoverUrl = resp.data.ugcSeason?.cover?.let { normalizeCoverUrl(it) },
            pages = pages,
        )
    }

    suspend fun getPlayUrlInfo(
        aid: Long,
        cid: Long,
        format: StreamFormat,
    ): PlayUrlInfo {
        val baseParams = mutableMapOf(
            "avid" to aid.toString(),
            "cid" to cid.toString(),
            "qn" to (if (cookieStore.isLoggedIn()) "127" else "64"),
            "fnver" to "0",
            "fnval" to "16",
            "fourk" to "1",
        )
        when (format) {
            StreamFormat.Flv -> baseParams["fnval"] = "0"
            StreamFormat.Mp4 -> baseParams["fnval"] = "1"
            StreamFormat.Dash -> baseParams["fnval"] = if (cookieStore.isLoggedIn()) "4048" else "16"
        }
        val baseUrl = "https://api.bilibili.com/x/player/wbi/playurl"
        val body = httpClient.get(wbiSigner.signedUrl(baseUrl, baseParams))
        val adapter = httpClient.adapter(VideoPlayUrlResponse::class.java)
        val resp =
            adapter.fromJson(body) ?: throw BiliHttpException("Empty playurl response", -1)
        if (resp.code != 0) {
            throw BiliHttpException(resp.message, resp.code)
        }
        var data = resp.data ?: resp.result
        if (data == null) {
            val altAdapter = httpClient.adapter(VideoPlayUrlVideoInfoResponse::class.java)
            val alt = altAdapter.fromJson(body)
            if (alt != null && alt.code != 0) {
                throw BiliHttpException(alt.message, alt.code)
            }
            data = alt?.result?.videoInfo
        }
        if (data == null) {
            throw BiliHttpException("Empty playurl response", -1)
        }
        val acceptQuality = data.acceptQuality ?: emptyList()
        val acceptDescription = data.acceptDescription ?: emptyList()

        if (data.dash != null) {
            val dash = data.dash
            val video = dash.video?.mapNotNull { it.toVideoStream(StreamFormat.Dash) } ?: emptyList()
            val audio = buildList {
                addAll(dash.audio?.mapNotNull { it.toAudioStream() }.orEmpty())
                dash.dolby?.audio?.firstOrNull()?.toAudioStream()?.let { add(it) }
                dash.flac?.audio?.toAudioStream()?.let { add(it) }
            }
            return PlayUrlInfo(
                format = StreamFormat.Dash,
                video = video,
                audio = audio,
                acceptQuality = acceptQuality,
                acceptDescription = acceptDescription,
            )
        }

        val durls = data.durls ?: emptyList()
        if (durls.isNotEmpty()) {
            val resolvedFormat = StreamFormat.Mp4
            val video = durls.mapNotNull { it.toVideoStream(resolvedFormat) }
            return PlayUrlInfo(
                format = resolvedFormat,
                video = video,
                audio = emptyList(),
                acceptQuality = acceptQuality,
                acceptDescription = acceptDescription,
            )
        }

        val durlList = data.durl ?: emptyList()
        if (durlList.isNotEmpty()) {
            val resolvedFormat = if (data.acceptFormat?.contains("flv") == true) {
                StreamFormat.Flv
            } else {
                StreamFormat.Mp4
            }
            val video = if (acceptQuality.isNotEmpty()) {
                acceptQuality.mapNotNull { qn ->
                    val info = if (data.quality == qn) {
                        data
                    } else {
                        val params = baseParams.toMutableMap()
                        params["qn"] = qn.toString()
                        val qnBody = httpClient.get(wbiSigner.signedUrl(baseUrl, params))
                        val qnResp =
                            adapter.fromJson(qnBody) ?: return@mapNotNull null
                        qnResp.data ?: qnResp.result
                    }
                    val durl = info?.durl?.firstOrNull()
                    durl?.toVideoStream(resolvedFormat, qn)
                }
            } else {
                durlList.mapNotNull { it.toVideoStream(resolvedFormat, data.quality ?: 0) }
            }
            return PlayUrlInfo(
                format = resolvedFormat,
                video = video,
                audio = emptyList(),
                acceptQuality = acceptQuality,
                acceptDescription = acceptDescription,
            )
        }

        throw BiliHttpException("No playable stream", -1)
    }

    private fun parseVideoId(input: String): VideoId {
        val trimmed = input.trim()
        val bvMatch = BV_REGEX.find(trimmed)?.value
        if (bvMatch != null) {
            return VideoId(bvid = bvMatch)
        }
        val avMatch = AV_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
        if (avMatch != null) {
            return VideoId(aid = avMatch)
        }
        if (trimmed.all { it.isDigit() }) {
            return VideoId(aid = trimmed)
        }
        throw IllegalArgumentException("Invalid input")
    }

    companion object {
    private val BV_REGEX = Regex("BV[0-9A-Za-z]{10}")
    private val AV_REGEX = Regex("av(\\d+)")
    }

    private fun normalizeCoverUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
            else -> url
        }
    }

    private fun buildTagUrl(id: VideoId): String {
        val baseUrl = "https://api.bilibili.com/x/tag/archive/tags"
        val builder = baseUrl.toHttpUrl().newBuilder()
        if (id.bvid != null) {
            builder.addQueryParameter("bvid", id.bvid)
        } else if (id.aid != null) {
            builder.addQueryParameter("aid", id.aid)
        }
        return builder.build().toString()
    }
}

private data class ViewResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: ViewData?,
)

private data class ViewData(
    @Json(name = "bvid") val bvid: String,
    @Json(name = "aid") val aid: Long,
    @Json(name = "title") val title: String,
    @Json(name = "desc") val desc: String,
    @Json(name = "pic") val pic: String,
    @Json(name = "cid") val cid: Long,
    @Json(name = "duration") val duration: Int,
    @Json(name = "pubdate") val pubdate: Long = 0,
    @Json(name = "owner") val owner: ViewOwner? = null,
    @Json(name = "ugc_season") val ugcSeason: UgcSeason? = null,
    @Json(name = "pages") val pages: List<ViewPage>? = null,
)

private data class ViewPage(
    @Json(name = "cid") val cid: Long,
    @Json(name = "part") val part: String,
    @Json(name = "duration") val duration: Int,
)

private data class VideoPlayUrlResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: VideoPlayUrlData?,
    @Json(name = "result") val result: VideoPlayUrlData?,
)

private data class VideoPlayUrlVideoInfoResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "result") val result: VideoPlayUrlVideoInfoResult?,
)

private data class VideoPlayUrlVideoInfoResult(
    @Json(name = "video_info") val videoInfo: VideoPlayUrlData?,
)

private data class VideoPlayUrlData(
    @Json(name = "quality") val quality: Int?,
    @Json(name = "accept_quality") val acceptQuality: List<Int>?,
    @Json(name = "accept_description") val acceptDescription: List<String>?,
    @Json(name = "accept_format") val acceptFormat: String?,
    @Json(name = "durls") val durls: List<VideoDurlGroup>?,
    @Json(name = "durl") val durl: List<VideoDurl>?,
    @Json(name = "dash") val dash: VideoDash?,
)

private data class VideoDurlGroup(
    @Json(name = "quality") val quality: Int,
    @Json(name = "durl") val durl: List<VideoDurl>?,
)

private data class VideoDurl(
    @Json(name = "url") val url: String,
    @Json(name = "backup_url") val backupUrl: List<String>?,
    @Json(name = "size") val size: Long?,
)

private data class VideoDash(
    @Json(name = "video") val video: List<VideoDashItem>?,
    @Json(name = "audio") val audio: List<VideoDashItem>?,
    @Json(name = "dolby") val dolby: VideoDashDolby?,
    @Json(name = "flac") val flac: VideoDashFlac?,
)

private data class VideoDashItem(
    @Json(name = "id") val id: Int,
    @Json(name = "baseUrl") val baseUrl: String?,
    @Json(name = "base_url") val baseUrlAlt: String?,
    @Json(name = "backupUrl") val backupUrl: List<String>?,
    @Json(name = "backup_url") val backupUrlAlt: List<String>?,
    @Json(name = "bandwidth") val bandwidth: Long?,
    @Json(name = "codecid") val codecid: Int?,
    @Json(name = "width") val width: Int?,
    @Json(name = "height") val height: Int?,
    @Json(name = "frameRate") val frameRate: String?,
)

private data class VideoDashDolby(
    @Json(name = "audio") val audio: List<VideoDashItem>?,
)

private data class VideoDashFlac(
    @Json(name = "audio") val audio: VideoDashItem?,
)

private data class ViewOwner(
    @Json(name = "name") val name: String?,
    @Json(name = "mid") val mid: Long?,
    @Json(name = "face") val face: String?,
)

private data class UgcSeason(
    @Json(name = "id") val id: Long?,
    @Json(name = "title") val title: String?,
    @Json(name = "cover") val cover: String?,
)

private data class TagResponse(
    @Json(name = "data") val data: List<TagItem>?,
)

private data class TagItem(
    @Json(name = "tag_name") val tagName: String?,
)

private fun VideoDashItem.toVideoStream(format: StreamFormat): VideoStream? {
    val url = baseUrl ?: baseUrlAlt ?: return null
    return VideoStream(
        id = id,
        format = format,
        width = width,
        height = height,
        bandwidth = bandwidth,
        frameRate = frameRate,
        codec = when (codecid) {
            7 -> VideoCodec.Avc
            12 -> VideoCodec.Hevc
            13 -> VideoCodec.Av1
            else -> null
        },
        url = url,
        backupUrls = backupUrl ?: backupUrlAlt.orEmpty(),
    )
}

private fun VideoDashItem.toAudioStream(): AudioStream? {
    val url = baseUrl ?: baseUrlAlt ?: return null
    return AudioStream(
        id = id,
        bandwidth = bandwidth,
        url = url,
        backupUrls = backupUrl ?: backupUrlAlt.orEmpty(),
    )
}

private fun VideoDurl.toVideoStream(format: StreamFormat, quality: Int): VideoStream {
    return VideoStream(
        id = quality,
        format = format,
        url = url,
        backupUrls = backupUrl.orEmpty(),
        size = size,
    )
}

private fun VideoDurlGroup.toVideoStream(format: StreamFormat): VideoStream? {
    val primary = durl?.firstOrNull() ?: return null
    return VideoStream(
        id = quality,
        format = format,
        url = primary.url,
        backupUrls = primary.backupUrl.orEmpty(),
        size = primary.size,
    )
}

