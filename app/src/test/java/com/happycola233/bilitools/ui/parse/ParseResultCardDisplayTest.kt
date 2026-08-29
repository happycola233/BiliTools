package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaMetadata
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.MediaUpper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseResultCardDisplayTest {
    @Test
    fun everyParseType_canDisplayNfoUpper() {
        MediaType.entries.forEach { type ->
            val upper = MediaUpper(
                name = "${type.name} UP",
                mid = 100L,
                avatar = "https://example.com/avatar.jpg",
            )
            val item = mediaItem(type = itemTypeFor(type))
            val info = mediaInfo(type = type, item = item, nfoUpper = upper)
            val state = ParseUiState(items = info.list)

            val display = resolveParseResultCardDisplay(state, info, item)

            assertEquals(type.name, upper, display.upper)
        }
    }

    @Test
    fun listPreview_prefersDisplayedItemUpperOverContainerOwner() {
        listOf(MediaType.Favorite, MediaType.WatchLater, MediaType.MusicList).forEach { type ->
            val containerOwner = MediaUpper("容器创建者", 100L, "https://example.com/container.jpg")
            val itemUpper = MediaUpper("当前内容 UP", 200L, "https://example.com/item.jpg")
            val item = mediaItem(type = itemTypeFor(type), upper = itemUpper)
            val info = mediaInfo(type = type, item = item, nfoUpper = containerOwner)
            val state = ParseUiState(items = info.list, previewItemIndex = 0)

            val display = resolveParseResultCardDisplay(state, info, item)

            assertEquals(type.name, itemUpper, display.upper)
        }
    }

    @Test
    fun collectionOverview_keepsCollectionUpper() {
        val collectionUpper = MediaUpper("合集 UP", 100L, "https://example.com/collection.jpg")
        val selectedItemUpper = MediaUpper("选集 UP", 200L, "https://example.com/item.jpg")
        val item = mediaItem(type = MediaType.Video, upper = selectedItemUpper)
        val info = mediaInfo(
            type = MediaType.Video,
            item = item,
            nfoUpper = collectionUpper,
            collection = true,
        )
        val state = ParseUiState(items = info.list, collectionMode = true)

        val display = resolveParseResultCardDisplay(state, info, item)

        assertEquals(collectionUpper, display.upper)
    }

    @Test
    fun collectionPreview_usesPreviewedItemUpper() {
        val collectionUpper = MediaUpper("合集 UP", 100L, "https://example.com/collection.jpg")
        val previewUpper = MediaUpper("预览选集 UP", 200L, "https://example.com/item.jpg")
        val item = mediaItem(type = MediaType.Video, upper = previewUpper).copy(
            bvid = "BV1PreviewedItem",
            metadata = MediaMetadata(modernCategory = "动画 > 短片"),
        )
        val info = mediaInfo(
            type = MediaType.Video,
            item = item,
            nfoUpper = collectionUpper,
            collection = true,
        )
        val state = ParseUiState(items = info.list, collectionMode = true, previewItemIndex = 0)

        val display = resolveParseResultCardDisplay(state, info, item)

        assertEquals(previewUpper, display.upper)
        assertEquals("BV1PreviewedItem", display.metadata.publicIdText)
        assertEquals(listOf("BV1PreviewedItem", "1:00", "动画 > 短片"), display.metadata.summarySlots)
    }

    @Test
    fun listPreview_metadataAndDescriptionFollowDisplayedItem() {
        val cases = listOf(
            PreviewCase(
                containerType = MediaType.Favorite,
                selected = mediaItem(MediaType.Video).copy(
                    title = "第一条视频",
                    description = "第一条简介",
                    bvid = "BV1SelectedVideo",
                ),
                preview = mediaItem(MediaType.Video).copy(
                    title = "预览视频",
                    description = "预览视频简介",
                    bvid = "BV1PreviewVideo",
                ),
                expectedPublicId = "BV1PreviewVideo",
            ),
            PreviewCase(
                containerType = MediaType.MusicList,
                selected = mediaItem(MediaType.Music).copy(
                    title = "第一首歌",
                    description = "第一首简介",
                    sid = 101L,
                    amid = 301L,
                ),
                preview = mediaItem(MediaType.Music).copy(
                    title = "预览歌曲",
                    description = "预览歌曲简介",
                    sid = 102L,
                    amid = 301L,
                ),
                expectedPublicId = "au102",
            ),
            PreviewCase(
                containerType = MediaType.OpusList,
                selected = mediaItem(MediaType.Opus).copy(
                    title = "第一篇文章",
                    description = "第一篇简介",
                    cvid = 201L,
                    rlid = 401L,
                ),
                preview = mediaItem(MediaType.Opus).copy(
                    title = "预览文章",
                    description = "预览文章简介",
                    cvid = 202L,
                    rlid = 401L,
                ),
                expectedPublicId = "cv202",
            ),
        )

        cases.forEach { case ->
            val info = MediaInfo(
                type = case.containerType,
                id = "container",
                nfo = MediaNfo(showTitle = "容器标题"),
                list = listOf(case.selected, case.preview),
            )
            val state = ParseUiState(items = info.list, selectedItemIndex = 0, previewItemIndex = 1)

            val display = resolveParseResultCardDisplay(state, info, case.selected)

            assertEquals(case.containerType.name, case.preview.title, display.title)
            assertEquals(case.containerType.name, case.preview.description, display.description)
            assertEquals(case.containerType.name, case.expectedPublicId, display.metadata.publicIdText)
        }
    }

    @Test
    fun missingUpper_remainsHidden() {
        val item = mediaItem(type = MediaType.Video)
        val info = mediaInfo(type = MediaType.Video, item = item, nfoUpper = null)

        val display = resolveParseResultCardDisplay(ParseUiState(items = info.list), info, item)

        assertNull(display.upper)
    }

    @Test
    fun displayedUpper_keepsFollowerCountAndFollowsPreviewSubject() {
        val containerUpper = MediaUpper("容器 UP", 100L, followerCount = 10)
        val previewUpper = MediaUpper("预览 UP", 200L, followerCount = 123_456)
        val item = mediaItem(type = MediaType.Music, upper = previewUpper)
        val info = mediaInfo(
            type = MediaType.MusicList,
            item = item,
            nfoUpper = containerUpper,
        )
        val state = ParseUiState(
            mediaInfo = info,
            items = info.list,
            previewItemIndex = 0,
        )

        val display = resolveParseResultCardDisplay(state, info, item)

        assertEquals(123_456L, display.upper?.followerCount)
        assertEquals(previewUpper, resolveDisplayedUpper(state))
    }

    private fun mediaInfo(
        type: MediaType,
        item: MediaItem,
        nfoUpper: MediaUpper?,
        collection: Boolean = false,
    ): MediaInfo {
        return MediaInfo(
            type = type,
            id = "test",
            nfo = MediaNfo(showTitle = "测试标题", upper = nfoUpper),
            list = listOf(item),
            collection = collection,
        )
    }

    private fun mediaItem(
        type: MediaType,
        upper: MediaUpper? = null,
    ): MediaItem {
        return MediaItem(
            title = "测试内容",
            coverUrl = "https://example.com/cover.jpg",
            description = "测试简介",
            url = "https://www.bilibili.com",
            duration = 60,
            pubTime = 1_700_000_000L,
            type = type,
            upper = upper,
            isTarget = true,
            index = 0,
        )
    }

    private fun itemTypeFor(containerType: MediaType): MediaType {
        return when (containerType) {
            MediaType.Favorite,
            MediaType.WatchLater,
            MediaType.UserVideo,
            -> MediaType.Video

            MediaType.MusicList,
            MediaType.UserAudio,
            -> MediaType.Music

            MediaType.OpusList,
            MediaType.UserOpus,
            -> MediaType.Opus

            else -> containerType
        }
    }

    private data class PreviewCase(
        val containerType: MediaType,
        val selected: MediaItem,
        val preview: MediaItem,
        val expectedPublicId: String,
    )
}
