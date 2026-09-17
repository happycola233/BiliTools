package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.LyricsEmbedding
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding
import org.junit.Assert.*
import org.junit.Test

class EmbeddedLyricsTest {
    private val manualEnglish = SubtitleInfo("en", "英语", "https://example.com/en")
    private val aiChinese = SubtitleInfo("ai-zh", "中文（自动生成）", "https://example.com/ai", isAi = true)
    private val manualChinese = SubtitleInfo("zh-CN", "中文", "https://example.com/zh")
    private val traditionalChinese = SubtitleInfo("zh-Hant", "中文（繁体）", "https://example.com/hant")

    @Test fun automaticLyricsPreferManualThenSimplifiedThenTraditional() {
        val list = listOf(aiChinese, manualEnglish, traditionalChinese, manualChinese)
        assertEquals(manualChinese, selectLyricsSubtitle(list, LyricsEmbedding()))
        assertEquals(traditionalChinese, selectLyricsSubtitle(listOf(aiChinese, manualEnglish, traditionalChinese), LyricsEmbedding()))
        assertEquals(manualEnglish, selectLyricsSubtitle(listOf(aiChinese, manualEnglish), LyricsEmbedding()))
        assertEquals(aiChinese, selectLyricsSubtitle(listOf(aiChinese), LyricsEmbedding()))
        assertNull(selectLyricsSubtitle(listOf(aiChinese), LyricsEmbedding(includeGenerated = false)))
    }

    @Test fun explicitLyricsLanguageNeverFallsBackToADifferentLanguage() {
        val list = listOf(aiChinese, manualEnglish, manualChinese)
        assertEquals(manualEnglish, selectLyricsSubtitle(list, LyricsEmbedding(language = "en")))
        // 用户点名了 AI 字幕就用 AI 字幕，includeGenerated 只约束自动选择。
        assertEquals(aiChinese, selectLyricsSubtitle(list, LyricsEmbedding(language = "ai-zh", includeGenerated = false)))
        assertNull(selectLyricsSubtitle(list, LyricsEmbedding(language = "ja")))
    }

    @Test fun legacyAiLanguageStillCountsAsGenerated() {
        assertNull(selectLyricsSubtitle(listOf(aiChinese.copy(isAi = false)), LyricsEmbedding(includeGenerated = false)))
    }

    @Test fun subtitleTracksKeepBilibiliOrderAndIgnoreMissingUrls() {
        val list = listOf(aiChinese, manualEnglish.copy(url = ""), manualChinese, traditionalChinese)
        assertEquals(listOf(aiChinese, manualChinese, traditionalChinese), selectEmbeddedSubtitleTracks(list, SubtitleTrackEmbedding()))
        assertEquals(listOf(manualChinese, traditionalChinese), selectEmbeddedSubtitleTracks(list, SubtitleTrackEmbedding(includeGenerated = false)))
        assertEquals(
            listOf(aiChinese, traditionalChinese),
            selectEmbeddedSubtitleTracks(list, SubtitleTrackEmbedding(languages = listOf("zh-Hant", "ai-zh", "en"), includeGenerated = false)),
        )
    }

    @Test fun convertsTimesWithCarryAndPreservesSilenceWithoutClearingOverlappingCues() {
        val lyrics = subtitlesToLrc(listOf(
            LyricsSubtitleLine(62.0, 63.0, "后句"),
            LyricsSubtitleLine(59.999, 61.0, "第一行\n第二行"),
            LyricsSubtitleLine(60.5, 61.5, "重叠字幕"),
            LyricsSubtitleLine(Double.NaN, 4.0, "非法时间"),
            LyricsSubtitleLine(1.0, 0.0, "倒置时间"),
        ))!!
        assertTrue(lyrics.startsWith("[01:00.00]第一行\n[01:00.00]第二行"))
        assertFalse(lyrics.contains("[01:01.00]"))
        assertTrue(lyrics.contains("[01:01.50]\n[01:02.00]后句"))
        assertTrue(lyrics.endsWith("[01:03.00]"))
        assertFalse(lyrics.contains("非法"))
    }

    @Test fun nativeLrcKeepsOffsetAndMultipleTimestamps() {
        assertEquals("[offset:-200]\n[00:01.00][00:02.00]示例", normalizeOriginalLyrics("\uFEFF[offset:-200]\r\n[00:01.00][00:02.00]示例\r\n"))
        assertNull(normalizeOriginalLyrics("[ti:只有标题]\n[ar:只有作者]"))
        assertNull(normalizeOriginalLyrics("  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHtmlErrorsInsteadOfSavingThemAsLyrics() {
        normalizeOriginalLyrics("<!doctype html><title>Access denied</title>")
    }
}
