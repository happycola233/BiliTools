package com.happycola233.bilitools.ui.parse

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.PaginationTestEnvironment
import com.happycola233.bilitools.awaitPagination
import com.happycola233.bilitools.data.buildEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.ui.downloads.sourceUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ParseVideoPartTest {
    private lateinit var env: PaginationTestEnvironment
    private lateinit var model: ParseViewModel
    private val bvid = "BV17x411w7KC"

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        env = PaginationTestEnvironment()
        model = env.parse()
    }

    @After fun tearDown() {
        model.viewModelScope.cancel()
        env.cookies.clear()
        Dispatchers.resetMain()
    }

    @Test fun reparseDownloadedVideoSelectsTheSavedPart() = runTest {
        verifyDownloadedPart(inCollection = false)
    }

    @Test fun reparseDownloadedCollectionVideoSelectsItsPartInsteadOfTheCollectionEpisode() = runTest {
        verifyDownloadedPart(inCollection = true)
    }

    private suspend fun TestScope.verifyDownloadedPart(inCollection: Boolean) {
        respondWithVideo(inCollection)
        val info = env.media.getMediaInfo(bvid, MediaType.Video)
        val downloadedPart = info.list[8]
        val group = DownloadGroup(
            id = 1, title = downloadedPart.title, subtitle = null, bvid = bvid, createdAt = 1,
            tasks = emptyList(), sourceMetadata = buildEmbeddedMetadata(info, downloadedPart),
        )
        val sourceUrl = group.sourceUrl()!!
        assertEquals("https://www.bilibili.com/video/$bvid?p=9", sourceUrl)
        model.submitExternalUrl(sourceUrl)
        awaitPagination { !model.state.value.loading && !model.state.value.streamLoading }
        val state = model.state.value
        assertNull(state.error)
        assertEquals(8, state.selectedItemIndex)
        assertEquals(listOf(8), state.selectedItemIndices)
        assertEquals(109L, state.items[state.selectedItemIndex].cid)
        assertEquals(9, state.items.single { it.isTarget }.page)
        assertEquals(8, state.pagination.scrollRequest?.itemIndex)
        assertEquals(1, state.pageIndex)
        assertEquals(12, state.items.size)
        assertEquals(setOf("109"), env.requests.filter { it.encodedPath.endsWith("/playurl") }.map { it.queryParameter("cid") }.toSet())
        assertEquals("https://example.com/109.mp4", state.videoStreams.single().url)
    }

    @Test fun missingInvalidAndOutOfRangePartNumbersKeepTheFirstPartSelected() = runTest {
        respondWithVideo(inCollection = false)
        for (query in listOf("", "?p=0", "?p=-1", "?p=abc", "?p=999")) {
            model.submitExternalUrl("https://www.bilibili.com/video/$bvid$query")
            awaitPagination { !model.state.value.loading && !model.state.value.streamLoading }
            assertNull(model.state.value.error)
            assertEquals(query, 0, model.state.value.selectedItemIndex)
            assertEquals(query, 1, model.state.value.items.single { it.isTarget }.page)
        }
    }

    private fun respondWithVideo(inCollection: Boolean) {
        val pages = (1..12).joinToString { part ->
            """{"cid":${100 + part},"page":$part,"part":"分P $part","duration":60}"""
        }
        val collection = if (inCollection) """,
            "ugc_season":{"id":20,"title":"合集","cover":"","intro":"","sections":[
                {"id":30,"title":"分区","episodes":[{"section_id":30,"id":40,"aid":1,
                    "cid":101,"title":"多P视频","bvid":"$bvid",
                    "arc":{"pic":"","desc":"","pubdate":1},
                    "page":{"cid":101,"page":1,"part":"分P 1","duration":60},"pages":[$pages]}]}
            ]}
        """ else ""
        val response = """{"code":0,"data":{"bvid":"$bvid","aid":1,"title":"多P视频",
            "desc":"","pic":"","cid":101,"duration":720,"pubdate":1,"pages":[$pages]$collection}}
        """
        env.respond = { url ->
            when (url.encodedPath) {
                "/x/web-interface/view" -> response
                "/x/tag/archive/tags" -> """{"code":0,"data":[]}"""
                "/x/web-interface/nav" -> """{"code":0,"message":"0","data":{"wbi_img":{
                    "img_url":"https://example.com/00000000000000000000000000000000.png",
                    "sub_url":"https://example.com/00000000000000000000000000000000.png"}}}"""
                "/x/player/wbi/playurl" -> """{"code":0,"data":{"dash":{
                    "video":[{"id":80,"codecid":7,"width":1920,"height":1080,"baseUrl":"https://example.com/${url.queryParameter("cid")}.mp4"}],
                    "audio":[{"id":30280,"baseUrl":"https://example.com/audio.m4a"}]
                }}}"""
                else -> """{"code":-404,"message":"测试资源不存在"}"""
            }
        }
    }
}
