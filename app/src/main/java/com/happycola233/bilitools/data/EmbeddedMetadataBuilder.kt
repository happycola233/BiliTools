package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.MediaCopyrightType
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaType
import java.time.Instant
import java.time.ZoneOffset

/** 只映射当前下载单元的信息。收藏夹、歌单、空间等入口不是作品，不能充当专辑或作者。 */
internal fun buildEmbeddedMetadata(
    info: MediaInfo,
    item: MediaItem,
    preferredSubtitleLanguage: String? = null,
): DownloadEmbeddedMetadata {
    val isVideo = item.type == MediaType.Video
    val isMusic = item.type == MediaType.Music
    val isEpisode = item.type == MediaType.Bangumi || item.type == MediaType.Lesson
    val currentPart = item.metadata.videoParts.firstOrNull { it.cid == item.cid }
    val page = item.page ?: currentPart?.page
    val pageCount = item.pageCount ?: item.metadata.partCount
    val isMultipart = isVideo && (pageCount ?: 0) > 1
    // 只有作品本身的详情允许回退到 nfo；合集里另一条视频也不能继承当前视频的 UP。
    val sameWork = info.type == item.type && !info.collection &&
        (isEpisode || info.list.any { it.url == item.url })
    val uploader = item.upper?.name.clean()
        ?: info.nfo.upper?.name.clean().takeIf { sameWork }
    val author = if (isMusic) item.artist.clean() ?: item.metadata.artist.clean() else null
    val uploaderArtist = uploader.takeUnless {
        item.metadata.copyrightType == MediaCopyrightType.Repost || item.type == MediaType.Bangumi
    }
    val collectionTitle = item.metadata.collectionTitle.clean().takeIf { isVideo }
    val album = when {
        isMultipart || isEpisode -> item.workTitle.clean()
        collectionTitle != null -> collectionTitle
        else -> null
    }
    val track = when {
        isMultipart -> page
        isEpisode -> item.episode?.toIntOrNull()
        else -> null
    }?.takeIf { it > 0 }
    val total = when {
        isMultipart -> pageCount
        isEpisode && !info.paged && info.type == item.type -> {
            // PV、特典和有缺号的剧集不按当前列表长度伪造总轨数。
            val numbers = info.list.mapNotNull { it.episode?.toIntOrNull() }.sorted()
            info.list.size.takeIf { numbers == (1..info.list.size).toList() }
        }
        else -> null
    }?.takeIf { track != null && it >= track }
    val sourceUrl = when (item.type) {
        MediaType.Video -> item.bvid.clean()?.let { bvid ->
            "https://www.bilibili.com/video/$bvid" +
                (page?.takeIf { it > 1 }?.let { "?p=$it" } ?: "")
        } ?: item.url.clean()
        MediaType.Music -> item.sid?.let { "https://www.bilibili.com/audio/au$it" }
        MediaType.Bangumi -> item.epid?.let { "https://www.bilibili.com/bangumi/play/ep$it" }
        MediaType.Lesson -> item.epid?.let { "https://www.bilibili.com/cheese/play/ep$it" }
        else -> item.url.clean()
    }
    val publishedAt = item.metadata.publishedAt ?: item.pubTime.takeIf { it > 0 }
    return DownloadEmbeddedMetadata(
        title = when {
            isVideo && !isMultipart -> item.workTitle.clean() ?: item.title.clean()
            isMultipart -> currentPart?.title.clean() ?: item.title.clean()
            item.type == MediaType.Bangumi && !item.longTitle.isNullOrBlank() && !item.title.contains(item.longTitle) ->
                "${item.title} ${item.longTitle}".trim()
            else -> item.title.clean()
        },
        album = album,
        artist = author ?: uploaderArtist,
        artistIsUploader = author == null && uploaderArtist != null,
        albumIsCollection = !isMultipart && !isEpisode && collectionTitle != null,
        comment = item.description.clean(),
        // B 站发布时间不是音乐发行日期，也不是录制时间，不写 YEAR / DATE。
        publishedDate = publishedAt?.let {
            Instant.ofEpochSecond(it).atOffset(ZoneOffset.ofHours(8)).toLocalDate().toString()
        },
        uploader = uploader,
        tags = item.metadata.tags.mapNotNull { it.clean() }.distinct(),
        trackNumber = track.takeIf { album != null },
        trackTotal = total.takeIf { album != null },
        originalUrl = sourceUrl,
        coverUrl = item.coverUrl.clean(),
        lyricUrl = item.lyricUrl.clean().takeIf { isMusic },
        musicSid = item.sid.takeIf { isMusic },
        // au 关联视频可能使用不同剪辑，绝不能用它的字幕充当该歌曲的歌词。
        subtitleAid = item.aid?.takeIf { it > 0 && (isVideo || isEpisode) },
        subtitleCid = item.cid?.takeIf { it > 0 && (isVideo || isEpisode) },
        preferredSubtitleLanguage = preferredSubtitleLanguage,
    )
}

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
