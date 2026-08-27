package com.happycola233.bilitools.core.naming

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.MediaUpper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NamingContextFactoryTest {

    @Test
    fun `多P稿件写编号，单P稿件默认不写`() {
        val multiPage = contextFor(videoItem(page = 2, pageCount = 3))
        assertEquals("2", multiPage.p)

        val singlePage = contextFor(videoItem(page = 1, pageCount = 1))
        assertNull(singlePage.p)

        val forced = contextFor(videoItem(page = 1, pageCount = 1), showSinglePageNumber = true)
        assertEquals("1", forced.p)
    }

    @Test
    fun `列表里的稿件没有分P信息时编号为空`() {
        val fromFavorite = contextFor(videoItem(page = null, pageCount = null))
        assertNull(fromFavorite.p)
    }

    @Test
    fun `番剧集号补零，非数字标识不当集号`() {
        assertEquals("01", episodeContext("1").ep)
        assertEquals("12.5", episodeContext("12.5").ep)
        assertEquals("100", episodeContext("100").ep)
        assertNull(episodeContext("PV").ep)
        assertNull(episodeContext("特典 01").ep)
    }

    @Test
    fun `番剧标题优先用单集完整标题`() {
        val info = infoOf(MediaType.Bangumi, showTitle = "幕末Rock")
        val item = MediaItem(
            title = "1",
            coverUrl = "",
            description = "",
            url = "",
            duration = 0,
            pubTime = 0,
            type = MediaType.Bangumi,
            isTarget = true,
            index = 0,
            workTitle = "幕末Rock",
            episode = "1",
            longTitle = "瞒天过海！罪犯新选组",
            epid = 606591,
            ssid = 44227,
        )
        val context = NamingContextFactory.forItem(
            info = info,
            item = item,
            shape = NamingShape.Episode,
            labels = NamingLabels(),
            downTimeEpochSeconds = 0,
        )
        assertEquals("瞒天过海！罪犯新选组", context.title)
        assertEquals("幕末Rock", context.work)
        assertEquals("幕末Rock", context.collection)
        assertEquals("ep606591", context.id)
    }

    @Test
    fun `内容号按形态取各自的主键`() {
        assertEquals("BV1xx411c7mD", contextFor(videoItem(page = 1, pageCount = 1)).id)

        val trackInfo = infoOf(MediaType.MusicList, showTitle = "深夜歌单")
        val trackItem = MediaItem(
            title = "起风了",
            coverUrl = "",
            description = "",
            url = "",
            duration = 0,
            pubTime = 0,
            type = MediaType.Music,
            isTarget = true,
            index = 0,
            workTitle = "起风了",
            artist = "吴青峰",
            // 关联稿件存在也不能当主键
            aid = 0,
            bvid = "",
            sid = 10001,
            amid = 20002,
        )
        val trackContext = NamingContextFactory.forItem(
            info = trackInfo,
            item = trackItem,
            shape = NamingShape.Track,
            labels = NamingLabels(),
            downTimeEpochSeconds = 0,
        )
        assertEquals("au10001", trackContext.id)
        assertEquals("吴青峰", trackContext.artist)
        assertEquals("深夜歌单", trackContext.collection)
    }

    @Test
    fun `单个稿件与单曲没有上层合集`() {
        assertNull(
            NamingContextFactory.forItem(
                info = infoOf(MediaType.Video, showTitle = "稿件标题"),
                item = videoItem(page = 1, pageCount = 1),
                shape = NamingShape.Video,
                labels = NamingLabels(),
                downTimeEpochSeconds = 0,
            ).collection,
        )
        assertEquals(
            "合集标题",
            NamingContextFactory.forItem(
                info = infoOf(MediaType.Video, showTitle = "合集标题", collection = true),
                item = videoItem(page = 1, pageCount = 1),
                shape = NamingShape.Video,
                labels = NamingLabels(),
                downTimeEpochSeconds = 0,
            ).collection,
        )
    }

    private fun contextFor(
        item: MediaItem,
        showSinglePageNumber: Boolean = false,
    ): NamingContext = NamingContextFactory.forItem(
        info = infoOf(MediaType.Video, showTitle = "稿件标题"),
        item = item,
        shape = NamingShape.Video,
        labels = NamingLabels(),
        downTimeEpochSeconds = 0,
        showSinglePageNumber = showSinglePageNumber,
    )

    private fun episodeContext(episode: String): NamingContext = NamingContextFactory.forItem(
        info = infoOf(MediaType.Bangumi, showTitle = "幕末Rock"),
        item = MediaItem(
            title = episode,
            coverUrl = "",
            description = "",
            url = "",
            duration = 0,
            pubTime = 0,
            type = MediaType.Bangumi,
            isTarget = true,
            index = 0,
            episode = episode,
        ),
        shape = NamingShape.Episode,
        labels = NamingLabels(),
        downTimeEpochSeconds = 0,
    )

    private fun videoItem(page: Int?, pageCount: Int?) = MediaItem(
        title = "分P标题",
        coverUrl = "",
        description = "",
        url = "",
        duration = 0,
        pubTime = 0,
        type = MediaType.Video,
        isTarget = true,
        index = 0,
        page = page,
        pageCount = pageCount,
        workTitle = "稿件标题",
        aid = 123456789,
        bvid = "BV1xx411c7mD",
    )

    private fun infoOf(
        type: MediaType,
        showTitle: String?,
        collection: Boolean = false,
    ) = MediaInfo(
        type = type,
        id = "",
        nfo = MediaNfo(showTitle = showTitle, upper = MediaUpper(name = "影视飓风", mid = 946974)),
        list = emptyList(),
        collection = collection,
    )
}
