package com.happycola233.bilitools.core

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.VideoInfo
import com.happycola233.bilitools.data.model.VideoPage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NfoGenerator {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun buildCollectionNfo(info: VideoInfo): String? {
        val title = info.collectionTitle ?: return null
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<album>")
        appendNode(sb, "title", title)
        appendNode(sb, "plot", info.description)
        appendNode(sb, "premiered", formatDate(info.pubTime))
        appendNode(sb, "runtime", (info.duration / 60).coerceAtLeast(1))
        info.tags.forEach { tag ->
            appendNode(sb, "genre", tag)
            appendNode(sb, "tag", tag)
        }
        info.collectionCoverUrl?.let {
            appendNode(sb, "thumb", it, mapOf("preview" to it))
        }
        appendBiliNodes(sb, info, null)
        sb.append("</album>")
        return sb.toString()
    }

    fun buildCollectionNfo(info: MediaInfo): String? {
        val title = info.nfo.showTitle?.takeIf { it.isNotBlank() } ?: return null
        val runtime = info.list.firstOrNull()?.duration
            ?.takeIf { it > 0 }
            ?.let { (it / 60).coerceAtLeast(1) }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<album>")
        appendNode(sb, "title", title)
        appendNode(sb, "plot", info.nfo.intro)
        appendNode(sb, "premiered", formatDate(info.nfo.premiered ?: 0))
        runtime?.let { appendNode(sb, "runtime", it) }
        info.nfo.tags.forEach { tag ->
            appendNode(sb, "genre", tag)
            appendNode(sb, "tag", tag)
        }
        val thumb = info.nfo.thumbs.firstOrNull()?.url
        if (!thumb.isNullOrBlank()) {
            appendNode(sb, "thumb", thumb, mapOf("preview" to thumb))
        }
        sb.append("</album>")
        return sb.toString()
    }

    fun buildSingleNfo(info: VideoInfo, page: VideoPage): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<movie>")
        appendNode(sb, "title", info.title)
        appendNode(sb, "originaltitle", page.title)
        appendNode(sb, "plot", info.description)
        appendNode(sb, "premiered", formatDate(info.pubTime))
        appendNode(sb, "runtime", (page.duration / 60).coerceAtLeast(1))
        if (!info.ownerName.isNullOrBlank()) {
            appendNode(sb, "director", info.ownerName)
        }
        info.tags.forEach { tag ->
            appendNode(sb, "genre", tag)
            appendNode(sb, "tag", tag)
        }
        appendNode(sb, "thumb", info.coverUrl, mapOf("preview" to info.coverUrl))
        appendBiliNodes(sb, info, page)
        sb.append("</movie>")
        return sb.toString()
    }

    fun buildSingleNfo(info: MediaInfo, item: MediaItem): String {
        val title = info.nfo.showTitle?.takeIf { it.isNotBlank() } ?: item.title
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<movie>")
        appendNode(sb, "title", title)
        appendNode(sb, "originaltitle", item.title)
        val plot = item.description.takeIf { it.isNotBlank() } ?: info.nfo.intro
        appendNode(sb, "plot", plot)
        appendNode(sb, "premiered", formatDate(item.pubTime))
        appendNode(sb, "runtime", (item.duration / 60).coerceAtLeast(1))
        info.nfo.upper?.name?.let { appendNode(sb, "director", it) }
        info.nfo.tags.forEach { tag ->
            appendNode(sb, "genre", tag)
            appendNode(sb, "tag", tag)
        }
        val thumb = item.coverUrl.ifBlank { info.nfo.thumbs.firstOrNull()?.url.orEmpty() }
        if (thumb.isNotBlank()) {
            appendNode(sb, "thumb", thumb, mapOf("preview" to thumb))
        }
        appendBiliNodes(sb, item)
        sb.append("</movie>")
        return sb.toString()
    }

    private fun appendBiliNodes(sb: StringBuilder, info: VideoInfo, page: VideoPage?) {
        appendNode(sb, "bili:aid", info.aid)
        appendNode(sb, "bili:bvid", info.bvid)
        page?.let {
            appendNode(sb, "bili:cid", it.cid)
        }
    }

    private fun appendBiliNodes(sb: StringBuilder, item: MediaItem) {
        appendNode(sb, "bili:aid", item.aid)
        appendNode(sb, "bili:bvid", item.bvid)
        appendNode(sb, "bili:cid", item.cid)
        appendNode(sb, "bili:sid", item.sid)
        appendNode(sb, "bili:fid", item.fid)
        appendNode(sb, "bili:epid", item.epid)
        appendNode(sb, "bili:ssid", item.ssid)
        appendNode(sb, "bili:opid", item.opid)
    }

    private fun appendNode(sb: StringBuilder, name: String, value: Any?) {
        appendNode(sb, name, value, emptyMap())
    }

    private fun appendNode(
        sb: StringBuilder,
        name: String,
        value: Any?,
        attrs: Map<String, String>,
    ) {
        if (value == null) return
        sb.append('<').append(name)
        attrs.forEach { (key, attr) ->
            sb.append(' ').append(key).append("=\"").append(escapeXml(attr)).append('"')
        }
        sb.append('>')
        sb.append(escapeXml(value.toString()))
        sb.append("</").append(name).append('>')
    }

    private fun formatDate(epochSeconds: Long): String {
        if (epochSeconds <= 0) return ""
        val normalized = if (epochSeconds > 10_000_000_000L) {
            epochSeconds / 1000
        } else {
            epochSeconds
        }
        return Instant.ofEpochSecond(normalized).atZone(zoneId).toLocalDate()
            .format(dateFormatter)
    }

    private fun escapeXml(text: String): String {
        return buildString(text.length) {
            for (c in text) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }
    }
}
