package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.tagValues
import com.happycola233.bilitools.data.model.*
import org.junit.Assert.*
import org.junit.Test

class EmbeddedMetadataBuilderTest {
    private val uploader = MediaUpper("投稿人", 1)
    private fun item(type: MediaType = MediaType.Video) = MediaItem(
        title = "当前内容", coverUrl = "https://example.com/current.jpg", description = "当前简介",
        url = "https://www.bilibili.com/video/BV1current", duration = 60, pubTime = 1_700_000_000,
        type = type, upper = uploader, isTarget = true, index = 8, bvid = "BV1current", aid = 10, cid = 20,
    )
    private fun info(item: MediaItem, type: MediaType = item.type) = MediaInfo(
        type = type, id = "entry", list = listOf(item), nfo = MediaNfo(
            showTitle = "入口标题", intro = "入口简介", upper = MediaUpper("列表创建者", 2),
            tags = listOf("入口标签"), thumbs = listOf(MediaThumb("cover", "https://example.com/list.jpg")),
        ),
    )

    @Test fun musicUsesAuthorAndOwnLyricsInsteadOfUploaderOrAssociatedVideo() {
        val song = item(MediaType.Music).copy(artist = "实际作者", sid = 13598, lyricUrl = "https://example.com/song.lrc")
        val result = buildEmbeddedMetadata(info(song, MediaType.MusicList), song)
        assertEquals("实际作者", result.artist)
        assertFalse(result.artistIsUploader)
        assertNull(result.album)
        assertNull(result.trackNumber)
        assertEquals("https://www.bilibili.com/audio/au13598", result.originalUrl)
        assertEquals(song.lyricUrl, result.lyricUrl)
        assertNull(result.subtitleAid)
        assertNull(result.subtitleCid)
        assertEquals(13598L, result.musicSid)
    }

    @Test fun listContainersNeverSupplyAlbumArtistDescriptionCoverDateOrTrack() {
        for (type in listOf(MediaType.Favorite, MediaType.WatchLater, MediaType.UserVideo, MediaType.UserAudio, MediaType.MusicList)) {
            val entry = item().copy(upper = null, description = "", coverUrl = "", pubTime = 0)
            val result = buildEmbeddedMetadata(info(entry, type), entry)
            assertNull(type.name, result.album)
            assertNull(type.name, result.artist)
            assertNull(type.name, result.comment)
            assertNull(type.name, result.coverUrl)
            assertNull(type.name, result.publishedDate)
            assertNull(type.name, result.trackNumber)
            assertTrue(type.name, result.tags.isEmpty())
        }
    }

    @Test fun collectionUsesCurrentItemsOwnCollectionAndNeverAnotherVideosOwner() {
        val entry = item().copy(upper = null, metadata = MediaMetadata(collectionTitle = "真正的合集", collectionId = 3))
        val result = buildEmbeddedMetadata(info(entry).copy(collection = true), entry)
        assertEquals("真正的合集", result.album)
        assertTrue(result.albumIsCollection)
        assertNull(result.artist)
        assertNull(result.trackNumber)
        assertNull(result.trackTotal)
    }

    @Test fun multipartUsesPageInsteadOfListIndexAndLinksToThatPage() {
        val entry = item().copy(page = 3, pageCount = 5, workTitle = "完整作品", metadata = MediaMetadata(collectionTitle = "合集"))
        val result = buildEmbeddedMetadata(info(entry), entry)
        assertEquals("完整作品", result.album)
        assertFalse(result.albumIsCollection)
        assertEquals(3, result.trackNumber)
        assertEquals(5, result.trackTotal)
        assertEquals("https://www.bilibili.com/video/BV1current?p=3", result.originalUrl)
    }

    @Test fun singleVideoDoesNotInventAnAlbumOrTrack() {
        val entry = item().copy(page = 1, pageCount = 1, workTitle = "完整作品")
        val result = buildEmbeddedMetadata(info(entry), entry)
        assertEquals("完整作品", result.title)
        assertNull(result.album)
        assertNull(result.trackNumber)
        assertNull(result.trackTotal)
    }

    @Test fun listPreviewUsesTheDownloadedCidToRecoverPartMetadata() {
        val entry = item().copy(workTitle = "完整作品", metadata = MediaMetadata(
            presentationDetailsComplete = true, partCount = 3,
            videoParts = listOf(MediaVideoPart(page = 2, title = "第二部分", cid = 20)),
        ))
        val result = buildEmbeddedMetadata(info(entry, MediaType.Favorite), entry)
        assertEquals("第二部分", result.title)
        assertEquals("完整作品", result.album)
        assertEquals(2, result.trackNumber)
        assertEquals(3, result.trackTotal)
        assertTrue(result.originalUrl!!.endsWith("?p=2"))
    }

    @Test fun episodesUseDeclaredNumberAndSpecialsHaveNoInventedTrack() {
        for (type in listOf(MediaType.Bangumi, MediaType.Lesson)) {
            val entry = item(type).copy(episode = "12", workTitle = "系列作品", epid = 50)
            val result = buildEmbeddedMetadata(info(entry), entry)
            assertEquals(12, result.trackNumber)
            assertNull(result.trackTotal)
            assertEquals("系列作品", result.album)
            assertTrue(result.originalUrl!!.endsWith("/ep50"))
            val special = entry.copy(episode = "SP")
            assertNull(buildEmbeddedMetadata(info(special), special).trackNumber)
        }
    }

    @Test fun publicationAndArbitraryTagsKeepTheirOriginalMeaning() {
        val entry = item().copy(metadata = MediaMetadata(tags = listOf("知识", "年度推荐")))
        val result = buildEmbeddedMetadata(info(entry), entry)
        val values = result.tagValues(DownloadMetadataSettings())
        assertFalse(values.containsKey("genre"))
        assertFalse(values.containsKey("date"))
        assertTrue(values.getValue("comment").contains("B站发布时间："))
        assertTrue(values.getValue("comment").contains("知识、年度推荐"))
    }

    @Test fun repostUploaderIsNotPresentedAsTheArtist() {
        val entry = item().copy(metadata = MediaMetadata(copyrightType = MediaCopyrightType.Repost))
        val result = buildEmbeddedMetadata(info(entry), entry)
        assertNull(result.artist)
        assertEquals(uploader.name, result.uploader)
    }

    @Test fun disablingAttributionOptionsKeepsExplicitAuthorAndWorkMetadata() {
        val settings = DownloadMetadataSettings(useUploaderAsArtist = false, useCollectionAsAlbum = false)
        val video = item().copy(metadata = MediaMetadata(collectionTitle = "合集"))
        val videoTags = buildEmbeddedMetadata(info(video), video).tagValues(settings)
        assertFalse(videoTags.containsKey("artist"))
        assertFalse(videoTags.containsKey("album"))
        val music = item(MediaType.Music).copy(artist = "实际作者")
        assertEquals("实际作者", buildEmbeddedMetadata(info(music), music).tagValues(settings)["artist"])
        val part = video.copy(page = 2, pageCount = 3, workTitle = "多 P 作品")
        assertEquals("多 P 作品", buildEmbeddedMetadata(info(part), part).tagValues(settings)["album"])
    }
}
