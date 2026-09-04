package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaCopyrightType
import com.happycola233.bilitools.data.model.MediaHonor
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaMetadata
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaRareAttribute
import com.happycola233.bilitools.data.model.MediaResolution
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.MediaVideoPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseMetadataDisplayTest {
    @Test
    fun video_usesBvMultiPartDurationAndRareAttribute() {
        val item = item(
            type = MediaType.Video,
            duration = 120,
            aid = 170001,
            bvid = "BV17x411w7KC",
            cid = 279786,
            page = 2,
            pageCount = 3,
            metadata = MediaMetadata(
                totalDuration = 3_723,
                partCount = 3,
                legacyCategory = "音乐 > 原创音乐",
                modernCategory = "鬼畜 > 人力VOCALOID",
                rareAttributes = setOf(MediaRareAttribute.Interactive),
                warning = "内容可能引发不适",
                collisionBvid = "BV1L9Uoa9EUx",
                videoState = -40,
                honors = listOf(
                    MediaHonor(type = 3, description = "全站排行榜最高第 13 名"),
                    MediaHonor(type = 4, description = "热门收录"),
                ),
                historicalRank = 13,
                evaluation = "9.8",
                dynamicText = "同步动态正文",
                tags = listOf("音乐", "鬼畜"),
                publishedAt = 1_700_000_000L,
                submittedAt = 1_700_003_600L,
                videoParts = listOf(
                    MediaVideoPart(
                        page = 1,
                        title = "第一段",
                        duration = 1_200,
                        resolution = MediaResolution(1_920, 1_080),
                        cid = 1001,
                        firstFrameUrl = "https://i0.hdslb.com/bfs/storyff/p1_firsti.jpg",
                        submittedAt = 1_700_000_001L,
                    ),
                    MediaVideoPart(
                        page = 2,
                        title = "第二段",
                        duration = 1_300,
                        resolution = MediaResolution(1_080, 1_920, rotate = 1),
                        cid = 279786,
                        submittedAt = 1_700_000_002L,
                    ),
                    MediaVideoPart(page = 3, title = "第三段", duration = 1_223, cid = 1003),
                ),
            ),
            stat = MediaStat(
                play = 1_234_567,
                danmaku = 2,
                reply = 3,
                like = 4,
                coin = 5,
                favorite = 6,
                share = 7,
            ),
        )

        val display = buildParseMetadataDisplay(info(MediaType.Video, item), item, false)

        assertEquals(
            listOf("BV17x411w7KC", "3P · 1:02:03", "互动"),
            display.summarySlots,
        )
        assertEquals("BV17x411w7KC", display.publicIdCopyValue)
        assertEquals("BV 号", display.publicIdCopyName)
        assertFalse(display.summarySlots.any { it.contains("内容可能引发不适") })
        assertTrue(display.allRows().any { it.name == "警告" && it.value == "内容可能引发不适" })
        assertTrue(display.allRows().any { it.name == "撞车跳转" && it.value == "BV1L9Uoa9EUx" })
        assertTrue(display.allRows().any { it.name == "AV" && it.value == "AV170001" })
        assertTrue(display.allRows().any { it.name == "cid" && it.value == "279786" })
        assertTrue(display.allRows().any { it.name == "分区（旧）" && "原创音乐" in it.value })
        assertTrue(display.allRows().any { it.name == "分区（新）" && "人力VOCALOID" in it.value })
        assertTrue(display.allRows().any { it.name == "视频状态" && it.value == "定时发布" })
        assertTrue(display.allRows().any { it.name == "播放" && it.value == "1234567" })
        assertTrue(display.allRows().any { it.name == "发布时间" && it.value == "2023-11-15 06:13:20" })
        assertTrue(display.allRows().any {
            it.name == "投稿/过审时间" && it.value == "2023-11-15 07:13:20" && it.note == "可能不准确"
        })
        assertTrue(display.allRows().any { it.name == "分辨率" && it.value == "1920×1080" })
        val partSection = display.sections.filterIsInstance<ParseMetadataSection.Groups>().single()
        assertEquals(listOf("P1", "P2", "P3"), partSection.groups.map(ParseMetadataGroup::title))
        assertEquals(
            listOf("第一段", "第二段", "第三段"),
            partSection.groups.map(ParseMetadataGroup::subtitle),
        )
        assertEquals(
            listOf("https://i0.hdslb.com/bfs/storyff/p1_firsti.jpg", null, null),
            partSection.groups.map(ParseMetadataGroup::previewUrl),
        )
        assertFalse(display.allRows().any { it.name == "标题" })
        assertEquals(
            1,
            display.allRows().count { it.value.contains("全站排行榜最高第 13 名") },
        )
        assertFalse(display.allRows().any { it.name == "历史最高排名" })
    }

    @Test
    fun bangumiEpisode_usesEpAndKeepsSeasonIdentifiersInDetails() {
        val item = item(
            type = MediaType.Bangumi,
            duration = 1_420,
            epid = 9001,
            ssid = 8001,
            mdid = 7001,
            metadata = MediaMetadata(
                totalDuration = 1_420,
                contentKind = "纪录片",
                area = "中国大陆",
                badges = listOf("限免"),
                rareAttributes = setOf(MediaRareAttribute.LimitedFree),
            ),
        )

        val display = buildParseMetadataDisplay(info(MediaType.Bangumi, item), item, false)

        assertEquals(listOf("ep9001", "23:40", "限免"), display.summarySlots)
        assertEquals("ep 号", display.publicIdCopyName)
        assertTrue(display.allRows().any { it.name == "ss" && it.value == "ss8001" })
        assertTrue(display.allRows().any { it.name == "md" && it.value == "md7001" })
    }

    @Test
    fun bangumiEpisode_keepsEpisodeCountOnlyInOriginSection() {
        val item = item(
            type = MediaType.Bangumi,
            epid = 9001,
            ssid = 8001,
            metadata = MediaMetadata(itemCount = 12),
        )
        val info = info(MediaType.Bangumi, item).copy(
            metadata = MediaMetadata(itemCount = 12),
        )

        val display = buildParseMetadataDisplay(info, item, false)

        assertFalse(display.allRows().any { it.name == "剧集数量" })
        assertEquals(
            listOf(ParseMetadataRow("正片集数", "12 集")),
            display.sections
                .filterIsInstance<ParseMetadataSection.Values>()
                .single { it.title == "来源" }
                .rows,
        )
    }

    @Test
    fun lessonEpisode_keepsCourseIdsSeparate() {
        val item = item(
            type = MediaType.Lesson,
            duration = 600,
            aid = 123,
            cid = 456,
            epid = 789,
            ssid = 321,
            metadata = MediaMetadata(
                totalDuration = 600,
                accessLabel = "需购买",
                rareAttributes = setOf(MediaRareAttribute.PurchaseRequired),
            ),
        )

        val display = buildParseMetadataDisplay(info(MediaType.Lesson, item), item, false)

        assertEquals(listOf("ep789", "10:00", "需购买"), display.summarySlots)
        assertEquals("ep 号", display.publicIdCopyName)
        assertTrue(display.allRows().any { it.name == "课程 ep" && it.value == "ep789" })
        assertTrue(display.allRows().any { it.name == "课程 ss" && it.value == "ss321" })
    }

    @Test
    fun lessonEpisode_doesNotInventCourseAsThirdSummarySlot() {
        val item = item(
            type = MediaType.Lesson,
            duration = 600,
            epid = 789,
            metadata = MediaMetadata(
                totalDuration = 600,
                accessLabel = "可观看",
            ),
        )

        val display = buildParseMetadataDisplay(info(MediaType.Lesson, item), item, false)

        assertEquals(listOf("ep789", "10:00"), display.summarySlots)
    }

    @Test
    fun songAndArticle_keepTheirContainerIdsInDetails() {
        val song = item(
            type = MediaType.Music,
            duration = 245,
            sid = 100,
            amid = 200,
            metadata = MediaMetadata(totalDuration = 245, tags = listOf("原创音乐")),
        )
        val songDisplay = buildParseMetadataDisplay(info(MediaType.MusicList, song), song, false)
        assertEquals("au100", songDisplay.publicIdCopyValue)
        assertEquals("au 号", songDisplay.publicIdCopyName)
        assertTrue(songDisplay.allRows().any { it.name == "所在歌单" && it.value == "am200" })

        val article = item(
            type = MediaType.Opus,
            cvid = 300,
            opid = "400",
            rlid = 500,
            metadata = MediaMetadata(imageCount = 6, tags = listOf("摄影")),
        )
        val articleDisplay = buildParseMetadataDisplay(info(MediaType.OpusList, article), article, false)
        assertEquals(listOf("cv300", "6 张", "摄影"), articleDisplay.summarySlots)
        assertEquals("cv 号", articleDisplay.publicIdCopyName)
        assertTrue(articleDisplay.allRows().any { it.name == "所在文集" && it.value == "rl500" })
    }

    @Test
    fun collectionOverview_hasNoCopiedVideoId() {
        val item = item(type = MediaType.Video, bvid = "BV17x411w7KC")
        val info = info(MediaType.Video, item).copy(
            collection = true,
            metadata = MediaMetadata(collectionId = 88, itemCount = 12),
        )

        val display = buildParseMetadataDisplay(info, null, true)

        assertEquals(listOf("合集", "12 条"), display.summarySlots)
        assertEquals(null, display.publicIdCopyValue)
        assertTrue(display.allRows().any { it.name == "合集编号" && it.value == "88" })
    }

    @Test
    fun zeroDuration_isOmittedInsteadOfDisplayedAsZero() {
        val opus = item(type = MediaType.Opus, opid = "123456", duration = 0)

        val display = buildParseMetadataDisplay(info(MediaType.Opus, opus), opus, false)

        assertFalse(display.summarySlots.any { it == "0:00" })
        assertEquals(null, formatMetadataDuration(0))
    }

    @Test
    fun legacyVideoCategory_staysInDetailsInsteadOfSummary() {
        val video = item(
            type = MediaType.Video,
            bvid = "BV1LegacyOnly",
            metadata = MediaMetadata(legacyCategory = "知识 > 演讲·公开课（已下线）"),
        )

        val display = buildParseMetadataDisplay(info(MediaType.Video, video), video, false)

        assertEquals(listOf("BV1LegacyOnly"), display.summarySlots)
        assertTrue(display.allRows().any {
            it.name == "分区（旧）" && it.value.endsWith("（已下线）")
        })
    }

    @Test
    fun noReprint_staysInDetailsAndDoesNotDisplaceModernCategory() {
        val video = item(
            type = MediaType.Video,
            duration = 60,
            bvid = "BV1NoReprint",
            metadata = MediaMetadata(
                copyrightType = MediaCopyrightType.Original,
                noReprint = true,
                modernCategory = "影视 > AI影视",
            ),
        )

        val display = buildParseMetadataDisplay(info(MediaType.Video, video), video, false)

        assertEquals(listOf("BV1NoReprint", "1:00", "影视 > AI影视"), display.summarySlots)
        assertTrue(display.allRows().any {
            it.name == "类型" && it.value == "自制（禁止转载）"
        })
    }

    @Test
    fun singlePart_keepsCidAndResolutionInFlatSections() {
        val video = item(
            type = MediaType.Video,
            bvid = "BV1SinglePart",
            cid = 1234,
            metadata = MediaMetadata(
                partCount = 1,
                videoParts = listOf(
                    MediaVideoPart(
                        page = 1,
                        title = "唯一分 P",
                        duration = 60,
                        resolution = MediaResolution(1920, 1080),
                        cid = 1234,
                    ),
                ),
            ),
        )

        val display = buildParseMetadataDisplay(info(MediaType.Video, video), video, false)

        assertFalse(display.sections.any { it is ParseMetadataSection.Groups })
        assertTrue(display.allRows().any { it.name == "cid" && it.value == "1234" })
        assertTrue(display.allRows().any { it.name == "分辨率" && it.value == "1920×1080" })
    }

    @Test
    fun zeroStatsAreOmittedAndHonorRankDedupUsesHonorType() {
        val video = item(
            type = MediaType.Video,
            bvid = "BV1StructuredHonor",
            metadata = MediaMetadata(
                videoState = 1,
                honors = listOf(MediaHonor(type = 3, description = "排行榜记录")),
                historicalRank = 7,
            ),
            stat = MediaStat(play = 0, danmaku = 0, like = 12),
        )

        val display = buildParseMetadataDisplay(info(MediaType.Video, video), video, false)

        assertTrue(display.allRows().any { it.name == "视频状态" && it.value == "橙色通过" })
        assertFalse(display.allRows().any { it.name == "播放" || it.name == "弹幕" })
        assertTrue(display.allRows().any { it.name == "点赞" && it.value == "12" })
        assertFalse(display.allRows().any { it.name == "历史最高排名" })
    }

    @Test
    fun seasonAndDynamicItems_useAvailablePublicIdWithoutInventingOne() {
        val seasonOnly = item(type = MediaType.Bangumi, ssid = 8001)
        val seasonDisplay = buildParseMetadataDisplay(
            info(MediaType.Favorite, seasonOnly),
            seasonOnly,
            false,
        )
        assertEquals("ss8001", seasonDisplay.publicIdCopyValue)
        assertEquals("ss 号", seasonDisplay.publicIdCopyName)

        val dynamicOnly = item(type = MediaType.Opus, opid = "123456789")
        val dynamicDisplay = buildParseMetadataDisplay(
            info(MediaType.UserOpus, dynamicOnly),
            dynamicOnly,
            false,
        )
        assertEquals("动态 123456789", dynamicDisplay.publicIdText)
        assertEquals("123456789", dynamicDisplay.publicIdCopyValue)
        assertEquals("图文动态号", dynamicDisplay.publicIdCopyName)
    }

    @Test
    fun originOnlyMetadata_doesNotExposeEmptyDetailsPanel() {
        val sourceOnly = item(
            type = MediaType.Opus,
            pubTime = 0,
            sourceMid = 123,
        )

        val display = buildParseMetadataDisplay(
            info(MediaType.UserOpus, sourceOnly),
            sourceOnly,
            false,
        )

        assertTrue(display.sections.isEmpty())
    }

    private fun ParseMetadataDisplay.allRows(): List<ParseMetadataRow> = sections.flatMap { section ->
        when (section) {
            is ParseMetadataSection.Values -> section.rows
            is ParseMetadataSection.Groups -> section.groups.flatMap(ParseMetadataGroup::rows)
            is ParseMetadataSection.Contributors -> emptyList()
        }
    }

    private fun info(type: MediaType, item: MediaItem): MediaInfo = MediaInfo(
        type = type,
        id = "test",
        nfo = MediaNfo(showTitle = "测试容器"),
        list = listOf(item),
    )

    private fun item(
        type: MediaType,
        duration: Int = 0,
        aid: Long? = null,
        bvid: String? = null,
        cid: Long? = null,
        epid: Long? = null,
        ssid: Long? = null,
        sid: Long? = null,
        amid: Long? = null,
        opid: String? = null,
        cvid: Long? = null,
        rlid: Long? = null,
        mdid: Long? = null,
        page: Int? = null,
        pageCount: Int? = null,
        pubTime: Long = 1_700_000_000L,
        sourceMid: Long? = null,
        stat: MediaStat? = null,
        metadata: MediaMetadata = MediaMetadata(),
    ): MediaItem = MediaItem(
        title = "测试内容",
        coverUrl = "",
        description = "测试简介",
        stat = stat,
        url = "https://www.bilibili.com",
        duration = duration,
        pubTime = pubTime,
        type = type,
        isTarget = true,
        index = 0,
        aid = aid,
        bvid = bvid,
        cid = cid,
        epid = epid,
        ssid = ssid,
        sid = sid,
        amid = amid,
        opid = opid,
        cvid = cvid,
        rlid = rlid,
        mdid = mdid,
        page = page,
        pageCount = pageCount,
        sourceMid = sourceMid,
        metadata = metadata,
    )
}
