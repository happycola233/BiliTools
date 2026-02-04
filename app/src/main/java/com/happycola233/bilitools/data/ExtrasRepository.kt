package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.DanmakuParser
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.model.HistoryCursorInfo
import com.happycola233.bilitools.data.model.HistoryItem
import com.happycola233.bilitools.data.model.HistorySearchParams
import com.happycola233.bilitools.data.model.HistorySearchResult
import com.happycola233.bilitools.data.model.HistoryTab
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.squareup.moshi.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Duration

class ExtrasRepository(
    private val httpClient: BiliHttpClient,
    private val wbiSigner: WbiSigner,
) {
    suspend fun getSubtitles(aid: Long, cid: Long): List<SubtitleInfo> {
        val url = wbiSigner.signedUrl(
            "https://api.bilibili.com/x/player/wbi/v2",
            mapOf("aid" to aid.toString(), "cid" to cid.toString()),
        )
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(PlayerInfoResponse::class.java)
        val resp = adapter.fromJson(body) ?: return emptyList()
        val subtitles = resp.data?.subtitle?.subtitles.orEmpty()
        return subtitles.map {
            SubtitleInfo(
                lan = it.lan,
                name = it.lanDoc,
                url = normalizeUrl(it.subtitleUrl),
            )
        }
    }

    suspend fun getSubtitleSrt(subtitle: SubtitleInfo): ByteArray {
        val url = normalizeUrl(subtitle.url)
        val body = httpClient.get(url.toHttpUrl())
        val adapter = httpClient.adapter(SubtitleDetail::class.java)
        val detail = adapter.fromJson(body)
            ?: throw BiliHttpException("Empty subtitle", -1)
        val srt = buildString {
            detail.body.forEachIndexed { index, line ->
                append(index + 1)
                append('\n')
                append(formatSrtTime(line.from))
                append(" --> ")
                append(formatSrtTime(line.to))
                append('\n')
                append(line.content)
                append("\n\n")
            }
        }
        return srt.toByteArray(Charsets.UTF_8)
    }

    suspend fun hasAiSummary(aid: Long, cid: Long): Boolean {
        val url = wbiSigner.signedUrl(
            "https://api.bilibili.com/x/web-interface/view/conclusion/get",
            mapOf("aid" to aid.toString(), "cid" to cid.toString()),
        )
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(AiSummaryResponse::class.java)
        val resp = adapter.fromJson(body) ?: return false
        val result = resp.data?.modelResult ?: return false
        return result.resultType != 0
    }

    suspend fun getAiSummaryMarkdown(
        title: String,
        bvid: String,
        aid: Long,
        cid: Long,
    ): String? {
        val url = wbiSigner.signedUrl(
            "https://api.bilibili.com/x/web-interface/view/conclusion/get",
            mapOf("aid" to aid.toString(), "cid" to cid.toString()),
        )
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(AiSummaryResponse::class.java)
        val resp = adapter.fromJson(body) ?: return null
        val result = resp.data?.modelResult ?: return null
        if (result.resultType == 0) return null
        val sb = StringBuilder()
        sb.append("# ").append(title).append(" - ").append(bvid).append("\n\n")
        sb.append(result.summary).append("\n\n")
        if (result.resultType == 2) {
            result.outline.forEach { section ->
                sb.append("## ").append(section.title)
                sb.append(" - [")
                sb.append(formatDuration(section.timestamp))
                sb.append("](https://www.bilibili.com/video/")
                sb.append(bvid)
                sb.append("?t=")
                sb.append(section.timestamp)
                sb.append(")\n\n")
                section.partOutline.forEach { part ->
                    sb.append("- ")
                    sb.append(part.content)
                    sb.append(" - [")
                    sb.append(formatDuration(part.timestamp))
                    sb.append("](https://www.bilibili.com/video/")
                    sb.append(bvid)
                    sb.append("?t=")
                    sb.append(part.timestamp)
                    sb.append(")\n\n")
                }
            }
        }
        return sb.toString()
    }

    suspend fun getDanmakuLiveAss(aid: Long, cid: Long, durationSeconds: Int): ByteArray {
        val segments = ((durationSeconds + 359) / 360).coerceAtLeast(1)
        val elems = mutableListOf<com.happycola233.bilitools.core.DanmakuElem>()
        for (index in 1..segments) {
            val params = mapOf(
                "type" to "1",
                "oid" to cid.toString(),
                "pid" to aid.toString(),
                "segment_index" to index.toString(),
            )
            val url = wbiSigner.signedUrl(
                "https://api.bilibili.com/x/v2/dm/wbi/web/seg.so",
                params,
            )
            val bytes = httpClient.getBytes(url)
            elems.addAll(DanmakuParser.parse(bytes))
        }
        val ass = DanmakuParser.toAss(elems)
        return ass.toByteArray(Charsets.UTF_8)
    }

    suspend fun getDanmakuHistoryAss(
        cid: Long,
        date: String,
        hour: Int?,
    ): ByteArray {
        val params = mapOf(
            "type" to "1",
            "oid" to cid.toString(),
            "date" to date,
        )
        val url = "https://api.bilibili.com/x/v2/dm/web/history/seg.so"
            .toHttpUrl()
            .newBuilder()
            .apply {
                params.forEach { (key, value) -> addQueryParameter(key, value) }
            }
            .build()
        val bytes = httpClient.getBytes(url)
        val elems = DanmakuParser.parse(bytes)
        val zoneId = java.time.ZoneId.of("Asia/Shanghai")
        val targetDate = if (date.isNotBlank()) java.time.LocalDate.parse(date) else null
        val filtered = if (targetDate == null && hour == null) {
            elems
        } else {
            elems.filter { elem ->
                val instant = java.time.Instant.ofEpochSecond(elem.ctime)
                val time = instant.atZone(zoneId)
                if (targetDate != null && time.toLocalDate() != targetDate) return@filter false
                if (hour != null && time.hour != hour) return@filter false
                true
            }
        }
        val ass = DanmakuParser.toAss(filtered)
        return ass.toByteArray(Charsets.UTF_8)
    }

    suspend fun fetchBytes(url: String): ByteArray {
        return httpClient.getBytes(normalizeUrl(url).toHttpUrl())
    }

    suspend fun getHistoryCursor(): HistoryCursorInfo {
        val url = "https://api.bilibili.com/x/web-interface/history/cursor"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("ps", "1")
            .addQueryParameter("view_at", "0")
            .build()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(HistoryCursorResponse::class.java)
        val response = adapter.fromJson(body)
            ?: throw BiliHttpException("Empty history cursor response", -1)
        val data = response.data
        if (response.code != 0 || data == null) {
            throw BiliHttpException(response.message ?: "History cursor failed", response.code)
        }

        return HistoryCursorInfo(
            tabs = data.tab.orEmpty().mapNotNull { it.toModel() },
            defaultBusiness = data.cursor?.business?.trim().takeUnless { it.isNullOrBlank() },
            list = data.list.orEmpty().mapNotNull { it.toModel() },
        )
    }

    suspend fun getHistorySearch(params: HistorySearchParams): HistorySearchResult {
        val url = "https://api.bilibili.com/x/web-interface/history/search"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("pn", params.page.toString())
            .addQueryParameter("keyword", params.keyword)
            .addQueryParameter("business", params.business)
            .addQueryParameter("add_time_start", params.addTimeStart.toString())
            .addQueryParameter("add_time_end", params.addTimeEnd.toString())
            .addQueryParameter("arc_max_duration", params.arcMaxDuration.toString())
            .addQueryParameter("arc_min_duration", params.arcMinDuration.toString())
            .addQueryParameter("device_type", params.deviceType.toString())
            .build()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(HistorySearchResponse::class.java)
        val response = adapter.fromJson(body)
            ?: throw BiliHttpException("Empty history search response", -1)
        val data = response.data
        if (response.code != 0 || data == null) {
            throw BiliHttpException(response.message ?: "History search failed", response.code)
        }

        return HistorySearchResult(
            hasMore = data.hasMore ?: false,
            total = data.page?.total ?: 0,
            page = data.page?.pn ?: params.page,
            list = data.list.orEmpty().mapNotNull { it.toModel() },
        )
    }

    private fun normalizeUrl(url: String): String {
        return if (url.startsWith("//")) {
            "https:$url"
        } else if (url.startsWith("http://")) {
            "https://${url.removePrefix("http://")}"
        } else {
            url
        }
    }

    private fun formatSrtTime(seconds: Double): String {
        val duration = Duration.ofMillis((seconds * 1000).toLong())
        val hours = duration.toHours()
        val minutes = (duration.toMinutes() % 60)
        val secs = (duration.seconds % 60)
        val millis = (duration.toMillis() % 1000)
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis)
    }

    private fun formatDuration(seconds: Int): String {
        val duration = Duration.ofSeconds(seconds.toLong())
        val hours = duration.toHours()
        val minutes = (duration.toMinutes() % 60)
        val secs = (duration.seconds % 60)
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    private fun HistoryTabData.toModel(): HistoryTab? {
        val typeValue = type?.trim().orEmpty()
        val nameValue = name?.trim().orEmpty()
        if (typeValue.isBlank() || nameValue.isBlank()) return null
        return HistoryTab(
            type = typeValue,
            name = nameValue,
        )
    }

    private fun HistoryItemData.toModel(): HistoryItem? {
        val normalizedCover = cover?.takeIf { it.isNotBlank() }?.let(::normalizeUrl)
        val normalizedCovers = covers.orEmpty()
            .filter { it.isNotBlank() }
            .map(::normalizeUrl)
        return HistoryItem(
            title = title?.takeIf { it.isNotBlank() }
                ?: longTitle?.takeIf { it.isNotBlank() }
                ?: history?.part?.takeIf { it.isNotBlank() }
                ?: "",
            longTitle = longTitle?.takeIf { it.isNotBlank() },
            coverUrl = normalizedCover,
            coverUrls = normalizedCovers,
            uri = uri?.takeIf { it.isNotBlank() }?.let(::normalizeUrl),
            bvid = history?.bvid?.takeIf { it.isNotBlank() },
            cid = history?.cid,
            business = history?.business?.takeIf { it.isNotBlank() },
            page = history?.page,
            part = history?.part?.takeIf { it.isNotBlank() },
            videos = videos ?: 0,
            authorName = authorName.orEmpty(),
            authorMid = authorMid,
            authorAvatarUrl = authorFace?.takeIf { it.isNotBlank() }?.let(::normalizeUrl),
            viewAt = viewAt ?: 0,
            progress = progress ?: 0,
            duration = duration ?: 0,
        )
    }
}

private data class PlayerInfoResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: PlayerInfoData?,
)

private data class PlayerInfoData(
    @Json(name = "subtitle") val subtitle: PlayerInfoSubtitle?,
)

private data class PlayerInfoSubtitle(
    @Json(name = "subtitles") val subtitles: List<PlayerSubtitle>?,
)

private data class PlayerSubtitle(
    @Json(name = "lan") val lan: String,
    @Json(name = "lan_doc") val lanDoc: String,
    @Json(name = "subtitle_url") val subtitleUrl: String,
)

private data class AiSummaryResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: AiSummaryData?,
)

private data class AiSummaryData(
    @Json(name = "model_result") val modelResult: AiSummaryResult?,
)

private data class AiSummaryResult(
    @Json(name = "result_type") val resultType: Int,
    @Json(name = "summary") val summary: String,
    @Json(name = "outline") val outline: List<AiSummarySection>,
)

private data class AiSummarySection(
    @Json(name = "title") val title: String,
    @Json(name = "timestamp") val timestamp: Int,
    @Json(name = "part_outline") val partOutline: List<AiSummaryPart>,
)

private data class AiSummaryPart(
    @Json(name = "timestamp") val timestamp: Int,
    @Json(name = "content") val content: String,
)

private data class SubtitleDetail(
    @Json(name = "body") val body: List<SubtitleLine>,
)

private data class SubtitleLine(
    @Json(name = "from") val from: Double,
    @Json(name = "to") val to: Double,
    @Json(name = "content") val content: String,
)

private data class HistoryCursorResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: HistoryCursorData?,
)

private data class HistoryCursorData(
    @Json(name = "cursor") val cursor: HistoryCursorMeta?,
    @Json(name = "list") val list: List<HistoryItemData>?,
    @Json(name = "tab") val tab: List<HistoryTabData>?,
)

private data class HistoryCursorMeta(
    @Json(name = "business") val business: String?,
)

private data class HistorySearchResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: HistorySearchData?,
)

private data class HistorySearchData(
    @Json(name = "has_more") val hasMore: Boolean?,
    @Json(name = "page") val page: HistorySearchPage?,
    @Json(name = "list") val list: List<HistoryItemData>?,
)

private data class HistorySearchPage(
    @Json(name = "pn") val pn: Int?,
    @Json(name = "total") val total: Int?,
)

private data class HistoryTabData(
    @Json(name = "type") val type: String?,
    @Json(name = "name") val name: String?,
)

private data class HistoryItemData(
    @Json(name = "title") val title: String?,
    @Json(name = "long_title") val longTitle: String?,
    @Json(name = "cover") val cover: String?,
    @Json(name = "covers") val covers: List<String>?,
    @Json(name = "uri") val uri: String?,
    @Json(name = "history") val history: HistoryMetaData?,
    @Json(name = "videos") val videos: Int?,
    @Json(name = "author_name") val authorName: String?,
    @Json(name = "author_mid") val authorMid: Long?,
    @Json(name = "author_face") val authorFace: String?,
    @Json(name = "view_at") val viewAt: Long?,
    @Json(name = "progress") val progress: Int?,
    @Json(name = "duration") val duration: Int?,
)

private data class HistoryMetaData(
    @Json(name = "bvid") val bvid: String?,
    @Json(name = "cid") val cid: Long?,
    @Json(name = "business") val business: String?,
    @Json(name = "page") val page: Int?,
    @Json(name = "part") val part: String?,
)
