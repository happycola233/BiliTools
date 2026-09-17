package com.happycola233.bilitools.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadExtraTaskSpecTest {
    @Test
    fun `subtitle task key ignores expiring url`() {
        val discoverySpec = subtitleDiscoverySpec(aid = 100L, cid = 200L)
        val oldSubtitle = SubtitleInfo(
            lan = "zh-Hans",
            name = "中文（简体）",
            url = "https://example.com/subtitle.json?auth_key=old",
        )
        val refreshedSubtitle = oldSubtitle.copy(
            url = "https://example.com/subtitle.json?auth_key=new",
        )
        val persistedTaskSpec = discoverySpec.copy(
            subtitleSelection = SubtitleTrackEmbedding(listOf(oldSubtitle.lan)),
        )

        assertEquals(
            persistedTaskSpec.subtitleTaskKey(),
            discoverySpec.subtitleTaskKeyFor(refreshedSubtitle),
        )
    }

    @Test
    fun `subtitle task key keeps different episodes and languages separate`() {
        val source = subtitleDiscoverySpec(aid = 100L, cid = 200L)
        val chinese = SubtitleInfo("zh-Hans", "中文（简体）", "https://example.com/zh")
        val english = SubtitleInfo("en-US", "英语（美国）", "https://example.com/en")

        assertNotEquals(
            source.subtitleTaskKeyFor(chinese),
            source.copy(cid = 201L).subtitleTaskKeyFor(chinese),
        )
        assertNotEquals(
            source.subtitleTaskKeyFor(chinese),
            source.subtitleTaskKeyFor(english),
        )
    }

    @Test
    fun `single-language rediscovery has the same stable key as its requested source`() {
        val source = subtitleDiscoverySpec(aid = 100L, cid = 200L)
        val language = SubtitleInfo("ai-en", "英语", "https://example.com/en?auth_key=new", isAi = true)
        val retry = source.copy(subtitleSelection = SubtitleTrackEmbedding(listOf(language.lan)))

        assertEquals(source.subtitleTaskKeyFor(language), retry.subtitleTaskKey())
        assertNotEquals(
            retry.subtitleTaskKey(),
            source.subtitleTaskKeyFor(language.copy(lan = "en")),
        )
    }

    @Test
    fun `aggregate discovery does not masquerade as a single-language task`() {
        val source = subtitleDiscoverySpec(aid = 100L, cid = 200L)
        assertNull(source.subtitleTaskKey())
        assertNull(source.copy(subtitleSelection = SubtitleTrackEmbedding(listOf("zh-Hans", "ai-en"))).subtitleTaskKey())
        assertNull(source.copy(operation = DownloadExtraTaskOperation.StaticText).subtitleTaskKey())
    }

    @Test
    fun `persisted subtitle requests preserve both specific sources and all-language selection`() {
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(DownloadExtraTaskSpec::class.java)
        val source = subtitleDiscoverySpec(aid = 100L, cid = 200L).copy(
            subtitleBaseFileName = "视频标题",
            subtitleTaskTitle = "字幕",
            mimeType = "application/x-subrip",
        )
        listOf(
            source.copy(subtitleSelection = SubtitleTrackEmbedding(listOf("zh-Hans", "ai-en"))),
            source.copy(subtitleSelection = SubtitleTrackEmbedding()),
        ).forEach { spec ->
            assertEquals(spec, adapter.fromJson(adapter.toJson(spec)))
        }
    }

    private fun subtitleDiscoverySpec(aid: Long, cid: Long) = DownloadExtraTaskSpec(
        operation = DownloadExtraTaskOperation.SubtitleDiscovery,
        unavailableMessage = "无字幕",
        aid = aid,
        cid = cid,
    )
}
