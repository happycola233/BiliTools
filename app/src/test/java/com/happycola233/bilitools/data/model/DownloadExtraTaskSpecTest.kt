package com.happycola233.bilitools.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
            operation = DownloadExtraTaskOperation.Subtitle,
            subtitle = oldSubtitle,
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

    private fun subtitleDiscoverySpec(aid: Long, cid: Long) = DownloadExtraTaskSpec(
        operation = DownloadExtraTaskOperation.SubtitleDiscovery,
        unavailableMessage = "无字幕",
        aid = aid,
        cid = cid,
    )
}
