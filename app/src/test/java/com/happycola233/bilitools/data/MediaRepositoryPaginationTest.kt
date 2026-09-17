package com.happycola233.bilitools.data

import android.app.Application
import com.happycola233.bilitools.PaginationTestEnvironment
import com.happycola233.bilitools.favoriteFolders
import com.happycola233.bilitools.favoritePage
import com.happycola233.bilitools.data.model.MediaQueryOptions
import com.happycola233.bilitools.data.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MediaRepositoryPaginationTest {
    private lateinit var env: PaginationTestEnvironment
    @Before fun setUp() { env = PaginationTestEnvironment() }
    @After fun tearDown() { env.cookies.clear() }

    @Test fun nullFavoritePageKeepsMoreFlagAndUsesRequestedCapacityForTotalPages() = runBlocking {
        env.respond = { url ->
            if (url.encodedPath.endsWith("/created/list-all")) favoriteFolders else favoritePage(nullItems = true)
        }
        val page = env.media.getMediaInfo("42", MediaType.Favorite, MediaQueryOptions(page = 2))
        assertTrue(page.list.isEmpty())
        assertEquals(true, page.hasMore)
        assertEquals(4, page.totalPages)
        assertEquals("36", env.favoritePages().single().queryParameter("ps"))
        assertEquals("2", env.favoritePages().single().queryParameter("pn"))
    }

    @Test fun findsSeasonBeyondFirstDirectoryPageIndependentlyOfContentPage() = runBlocking {
        env.respond = { url ->
            when (url.encodedPath) {
                "/x/web-interface/nav" -> wbiNav()
                "/x/polymer/web-space/seasons_series_list" -> {
                    val entries = if (url.queryParameter("page_num") == "1") {
                        (1..20).joinToString { metadata(it, season = true) }
                    } else metadata(99, season = true)
                    """{"code":0,"data":{"items_lists":{"page":{"total":21},"seasons_list":[$entries],"series_list":[]}}}"""
                }
                "/x/polymer/web-space/seasons_archives_list" -> archives()
                else -> """{"code":-404}"""
            }
        }
        val result = env.media.getMediaInfo("123", MediaType.UserVideo, MediaQueryOptions(target = 99, page = 3))
        val directory = env.requests.filter { it.encodedPath.endsWith("/seasons_series_list") }
        assertEquals(listOf("1", "2"), directory.map { it.queryParameter("page_num") })
        val content = env.requests.single { it.encodedPath.endsWith("/seasons_archives_list") }
        assertEquals("3", content.queryParameter("page_num"))
        assertEquals("30", content.queryParameter("page_size"))
        assertEquals(3, result.totalPages)
        assertEquals(21, result.sections!!.tabs.size)
        assertEquals(60, result.list.single().index)
        assertEquals(123L, result.nfo.premiered)
    }

    @Test fun seriesMetadataDoesNotRequireSeasonFieldsAndUsesCorrectParameters() = runBlocking {
        env.respond = { url ->
            when (url.encodedPath) {
                "/x/web-interface/nav" -> wbiNav()
                "/x/polymer/web-space/seasons_series_list" ->
                    """{"code":0,"data":{"items_lists":{"page":{"total":1},"seasons_list":[],"series_list":[${metadata(99, season = false)}]}}}"""
                "/x/series/archives" -> archives()
                else -> """{"code":-404}"""
            }
        }
        val result = env.media.getMediaInfo("123", MediaType.UserVideo, MediaQueryOptions(target = 99, page = 2))
        val request = env.requests.single { it.encodedPath == "/x/series/archives" }
        assertEquals("2", request.queryParameter("pn"))
        assertEquals("30", request.queryParameter("ps"))
        assertNull(request.queryParameter("page_num"))
        assertEquals(123L, result.nfo.premiered)
        assertEquals(30, result.list.single().index)
    }

    private fun metadata(id: Int, season: Boolean): String {
        val identity = if (season) "\"season_id\":$id,\"ptime\":123" else "\"series_id\":$id,\"ctime\":123"
        return """{"meta":{$identity,"cover":"","name":"列表$id","description":""}}"""
    }

    private fun archives() = """{"code":0,"data":{"page":{"total":75},"archives":[{"aid":1,"bvid":"BV1test","title":"视频","pic":"","duration":60,"pubdate":1}]}}"""

    private fun wbiNav() = """{"code":0,"message":"0","data":{"wbi_img":{"img_url":"https://example.com/00000000000000000000000000000000.png","sub_url":"https://example.com/00000000000000000000000000000000.png"}}}"""
}
