package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaMetadata
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationDetailQueryTest {
    @Test
    fun listSubjectsUseTheirRealDetailType() {
        val video = item(MediaType.Video, bvid = "BV17x411w7KC")
        val episode = item(MediaType.Bangumi, epid = 606591, ssid = 42290)
        val season = item(MediaType.Bangumi, ssid = 42290)
        val music = item(MediaType.Music, sid = 13598)
        val opus = item(MediaType.Opus, cvid = 123, opid = "456")
        val dynamicOpus = item(MediaType.Opus, opid = "456")

        assertEquals(MediaType.Video, buildPresentationDetailQuery(video)?.type)
        assertEquals("BV17x411w7KC", buildPresentationDetailQuery(video)?.id)
        assertFalse(
            buildPresentationDetailQuery(video)?.options?.includeOptionalVideoTags ?: true,
        )
        assertEquals("ep606591", buildPresentationDetailQuery(episode)?.id)
        assertEquals("ss42290", buildPresentationDetailQuery(season)?.id)
        assertEquals("au13598", buildPresentationDetailQuery(music)?.id)
        assertFalse(
            buildPresentationDetailQuery(music)?.options?.includeOptionalMusicExtras ?: true,
        )
        assertEquals("cv123", buildPresentationDetailQuery(opus)?.id)
        assertEquals("456", buildPresentationDetailQuery(dynamicOpus)?.id)
        assertNull(buildPresentationDetailQuery(item(MediaType.Opus)))
    }

    @Test
    fun allSupportedListContainersRequestDetailsUntilSubjectIsComplete() {
        val cases = listOf(
            MediaType.Favorite to item(MediaType.Bangumi, ssid = 42290),
            MediaType.WatchLater to item(MediaType.Video, bvid = "BV17x411w7KC"),
            MediaType.UserVideo to item(MediaType.Video, bvid = "BV17x411w7KC"),
            MediaType.UserAudio to item(MediaType.Music, sid = 13598),
            MediaType.UserOpus to item(MediaType.Opus, opid = "456"),
            MediaType.MusicList to item(MediaType.Music, sid = 13598),
            MediaType.OpusList to item(MediaType.Opus, cvid = 123),
        )

        cases.forEach { (containerType, subject) ->
            assertTrue(
                containerType.name,
                shouldSupplementPresentation(info(containerType, subject), subject),
            )
            val complete = subject.copy(
                metadata = subject.metadata.copy(presentationDetailsComplete = true),
            )
            assertFalse(
                containerType.name,
                shouldSupplementPresentation(info(containerType, complete), complete),
            )
        }
    }

    @Test
    fun collectionOnlySupplementsEpisodesWithoutCompleteViewMetadata() {
        val incomplete = item(MediaType.Video, bvid = "BV1Incomplete")
        val complete = incomplete.copy(
            metadata = MediaMetadata(presentationDetailsComplete = true),
        )
        val collection = info(MediaType.Video, incomplete).copy(collection = true)

        assertTrue(shouldSupplementPresentation(collection, incomplete))
        assertFalse(shouldSupplementPresentation(collection, complete))
    }

    private fun info(type: MediaType, item: MediaItem): MediaInfo = MediaInfo(
        type = type,
        id = "container",
        nfo = MediaNfo(),
        list = listOf(item),
    )

    private fun item(
        type: MediaType,
        bvid: String? = null,
        epid: Long? = null,
        ssid: Long? = null,
        sid: Long? = null,
        opid: String? = null,
        cvid: Long? = null,
    ): MediaItem = MediaItem(
        title = "测试内容",
        coverUrl = "",
        description = "",
        url = "https://www.bilibili.com",
        duration = 0,
        pubTime = 0,
        type = type,
        isTarget = true,
        index = 0,
        bvid = bvid,
        epid = epid,
        ssid = ssid,
        sid = sid,
        opid = opid,
        cvid = cvid,
    )
}
