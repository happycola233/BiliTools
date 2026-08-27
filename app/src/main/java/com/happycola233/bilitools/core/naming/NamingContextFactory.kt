package com.happycola233.bilitools.core.naming

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaType

/** 需要本地化的文案由调用方解析好再传进来，命名模块本身不碰 Android 资源。 */
data class NamingLabels(
    val container: String? = null,
    val mediaType: String? = null,
    val taskType: String? = null,
    val format: String? = null,
)

/** 流参数，只有音视频任务才有；字幕、NFO 等附属文件一律留空。 */
data class NamingStreamInfo(
    val resolution: String? = null,
    val audioBitrate: String? = null,
    val codec: String? = null,
)

/** 把 [MediaInfo] + [MediaItem] 翻译成命名变量取值。 */
object NamingContextFactory {

    fun forItem(
        info: MediaInfo,
        item: MediaItem,
        shape: NamingShape,
        labels: NamingLabels,
        downTimeEpochSeconds: Long,
        batchIndex: Int? = null,
        showSinglePageNumber: Boolean = false,
        stream: NamingStreamInfo? = null,
        imageOrdinal: String? = null,
    ): NamingContext {
        val upper = item.resolvedUpper(info)
        return NamingContext(
            title = leafTitle(item, shape),
            work = workTitle(item),
            collection = collectionTitle(info),
            p = pageNumber(item, shape, showSinglePageNumber),
            ep = episodeNumber(item),
            longTitle = item.longTitle.normalized(),
            section = item.sectionTitle.normalized(),
            img = imageOrdinal.normalized(),
            container = labels.container.normalized(),
            mediaType = labels.mediaType.normalized(),
            taskType = labels.taskType.normalized(),
            index = batchIndex,
            pubTimeEpochSeconds = item.pubTime.takeIf { it > 0L } ?: info.nfo.premiered,
            downTimeEpochSeconds = downTimeEpochSeconds,
            upper = upper?.name.normalized(),
            upperId = upper?.mid?.toString(),
            artist = item.artist.normalized(),
            id = contentId(item, shape),
            aid = item.aid?.toString(),
            bvid = item.bvid.normalized(),
            cid = item.cid?.toString(),
            epid = item.epid?.toString(),
            ssid = item.ssid?.toString(),
            sid = item.sid?.toString(),
            amid = item.amid?.toString(),
            fid = item.fid?.toString(),
            opid = item.opid.normalized(),
            cvid = item.cvid?.toString(),
            res = stream?.resolution.normalized(),
            abr = stream?.audioBitrate.normalized(),
            enc = stream?.codec.normalized(),
            fmt = labels.format.normalized(),
        )
    }

    /**
     * 顶层文件夹描述的是「这一批下载」，因此只取入口级信息，
     * 条目级变量（分 P、集号、流参数）在这里一律为空。
     */
    fun forEntry(
        info: MediaInfo,
        representative: MediaItem?,
        shape: NamingShape,
        labels: NamingLabels,
        downTimeEpochSeconds: Long,
    ): NamingContext {
        val upper = representative?.resolvedUpper(info) ?: info.nfo.upper
        return NamingContext(
            work = representative?.let(::workTitle),
            collection = collectionTitle(info),
            section = representative?.sectionTitle.normalized(),
            container = labels.container.normalized(),
            pubTimeEpochSeconds = representative?.pubTime?.takeIf { it > 0L } ?: info.nfo.premiered,
            downTimeEpochSeconds = downTimeEpochSeconds,
            upper = upper?.name.normalized(),
            upperId = upper?.mid?.toString(),
            artist = representative?.artist.normalized(),
            id = representative?.let { contentId(it, shape) },
            aid = representative?.aid?.toString(),
            bvid = representative?.bvid.normalized(),
            cid = representative?.cid?.toString(),
            epid = representative?.epid?.toString(),
            ssid = representative?.ssid?.toString(),
            sid = representative?.sid?.toString(),
            amid = representative?.amid?.toString(),
            fid = representative?.fid?.toString(),
            opid = representative?.opid.normalized(),
            cvid = representative?.cvid?.toString(),
        )
    }

    /** 番剧的展示标题往往只是「1」这样的集号，真正的单集名在 long_title 里。 */
    private fun leafTitle(item: MediaItem, shape: NamingShape): String? {
        if (shape == NamingShape.Episode) {
            item.longTitle.normalized()?.let { return it }
        }
        return item.title.normalized()
    }

    private fun workTitle(item: MediaItem): String? =
        item.workTitle.normalized() ?: item.title.normalized()

    private fun collectionTitle(info: MediaInfo): String? {
        val parentTitle = info.nfo.showTitle.normalized() ?: return null
        return when (info.type) {
            // 单个稿件的 showTitle 就是稿件本身，只有合集才算上层集合。
            MediaType.Video -> parentTitle.takeIf { info.collection }
            MediaType.Music, MediaType.Opus -> null
            else -> parentTitle
        }
    }

    /** 只有稿件分 P 才有编号；单 P 默认不写，避免所有文件都挂一个 P1。 */
    private fun pageNumber(
        item: MediaItem,
        shape: NamingShape,
        showSinglePageNumber: Boolean,
    ): String? {
        if (shape != NamingShape.Video) return null
        val page = item.page?.takeIf { it > 0 } ?: return null
        val pageCount = item.pageCount ?: 1
        return if (pageCount > 1 || showSinglePageNumber) page.toString() else null
    }

    /**
     * 集号取官方的分集标识：番剧是 `title`（"1"、"12.5"），课程是 `index`。
     * 「PV」「特典」这类非数字标识不是集号，留空让模板走可选片段。
     */
    private fun episodeNumber(item: MediaItem): String? {
        val raw = item.episode.normalized() ?: return null
        val integerPart = raw.substringBefore('.')
        if (integerPart.isEmpty() || !integerPart.all(Char::isDigit)) return null
        val fraction = raw.substringAfter('.', "")
        if (fraction.isNotEmpty() && !fraction.all(Char::isDigit)) return null
        val padded = integerPart.padStart(2, '0')
        return if (fraction.isEmpty()) padded else "$padded.$fraction"
    }

    private fun contentId(item: MediaItem, shape: NamingShape): String? = when (shape) {
        NamingShape.Episode -> item.epid?.let { "ep$it" }
        // 音频的关联稿件不能当主键：没有关联稿件时 aid/bvid 会是 0 / 空。
        NamingShape.Track -> item.sid?.let { "au$it" }
        NamingShape.Opus -> item.cvid?.let { "cv$it" } ?: item.opid.normalized()
        else -> item.bvid.normalized() ?: item.aid?.let { "av$it" }
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
