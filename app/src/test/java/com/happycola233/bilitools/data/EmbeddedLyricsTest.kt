package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.SubtitleInfo
import org.junit.Assert.*
import org.junit.Test

class EmbeddedLyricsTest {
    private val manualEnglish = SubtitleInfo("en", "英语", "https://example.com/en")
    private val aiChinese = SubtitleInfo("ai-zh", "中文（自动生成）", "https://example.com/ai", isAi = true)
    private val manualChinese = SubtitleInfo("zh-CN", "中文", "https://example.com/zh")

    @Test fun subtitlesRequireOptInAndManualBeatsAi() {
        val list = listOf(aiChinese, manualEnglish, manualChinese)
        assertNull(selectLyricsSubtitle(list, SubtitleLyricsMode.Off, null))
        assertEquals(manualChinese, selectLyricsSubtitle(list, SubtitleLyricsMode.ManualOnly, null))
        assertEquals(manualEnglish, selectLyricsSubtitle(listOf(aiChinese, manualEnglish), SubtitleLyricsMode.PreferManual, null))
        assertNull(selectLyricsSubtitle(listOf(aiChinese), SubtitleLyricsMode.ManualOnly, null))
        assertEquals(aiChinese, selectLyricsSubtitle(listOf(aiChinese), SubtitleLyricsMode.PreferManual, null))
    }

    @Test fun explicitLanguageNeverFallsBackToADifferentLanguageOrForbiddenAi() {
        val list = listOf(aiChinese, manualEnglish, manualChinese)
        assertEquals(manualEnglish, selectLyricsSubtitle(list, SubtitleLyricsMode.PreferManual, "en"))
        assertNull(selectLyricsSubtitle(list, SubtitleLyricsMode.ManualOnly, "ai-zh"))
        assertNull(selectLyricsSubtitle(list, SubtitleLyricsMode.PreferManual, "ja"))
    }

    @Test fun legacyAiLanguageStillCountsAsGenerated() {
        assertNull(selectLyricsSubtitle(listOf(aiChinese.copy(isAi = false)), SubtitleLyricsMode.ManualOnly, null))
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
