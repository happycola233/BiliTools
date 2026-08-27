package com.happycola233.bilitools.data.model

import com.happycola233.bilitools.core.NfoGenerator
import com.happycola233.bilitools.core.naming.NamingContext
import com.happycola233.bilitools.core.naming.NamingRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemResolvedUpperTest {
    @Test
    fun resolvedUpper_prefersItemUploaderOverFavoriteOwner() {
        val folderOwner = MediaUpper("登录用户", 10001L)
        val videoUp = MediaUpper("稿件UP", 20002L)
        val info = favoriteInfo(folderOwner)
        val item = videoItem(upper = videoUp)

        assertEquals(videoUp, item.resolvedUpper(info))
    }

    @Test
    fun resolvedUpper_fallsBackToContainerUpper() {
        val folderOwner = MediaUpper("登录用户", 10001L)
        val info = favoriteInfo(folderOwner)
        val item = videoItem(upper = null)

        assertEquals(folderOwner, item.resolvedUpper(info))
    }

    @Test
    fun resolvedUpper_isNullWhenNeitherSideHasUpper() {
        val info = favoriteInfo(upper = null)
        val item = videoItem(upper = null)

        assertNull(item.resolvedUpper(info))
    }

    @Test
    fun namingTokens_useItemUploaderInsteadOfFavoriteOwner() {
        val info = favoriteInfo(MediaUpper("登录用户", 10001L))
        val item = videoItem(upper = MediaUpper("稿件UP", 20002L))
        val upper = item.resolvedUpper(info)
        val rendered = NamingRenderer.renderComponent(
            template = "{upper}-{upperid}",
            context = NamingContext(
                upper = upper?.name,
                upperId = upper?.mid?.toString(),
            ),
        )

        assertEquals("稿件UP-20002", rendered)
    }

    @Test
    fun singleNfo_writesItemUploaderAsDirector() {
        val info = favoriteInfo(MediaUpper("登录用户", 10001L))
        val item = videoItem(upper = MediaUpper("稿件UP", 20002L))
        val nfo = NfoGenerator.buildSingleNfo(info, item)

        assertTrue(nfo.contains("<director>稿件UP</director>"))
    }

    private fun favoriteInfo(upper: MediaUpper?): MediaInfo {
        return MediaInfo(
            type = MediaType.Favorite,
            id = "10001",
            nfo = MediaNfo(showTitle = "默认收藏夹", upper = upper),
            list = emptyList(),
        )
    }

    private fun videoItem(upper: MediaUpper?): MediaItem {
        return MediaItem(
            title = "示例视频",
            coverUrl = "",
            description = "",
            url = "https://www.bilibili.com/video/BV1xx411c7mD",
            duration = 60,
            pubTime = 1_700_000_000L,
            type = MediaType.Video,
            upper = upper,
            isTarget = true,
            index = 0,
            bvid = "BV1xx411c7mD",
        )
    }
}
