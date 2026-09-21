package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.LyricsEmbedding
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.util.Locale

class EmbeddedLyricsTest {
    private lateinit var previousLocale: Locale
    @Before fun useChineseDisplayLanguage() { previousLocale = Locale.getDefault(); Locale.setDefault(Locale.SIMPLIFIED_CHINESE) }
    @After fun restoreDisplayLanguage() { Locale.setDefault(previousLocale) }
    private val manualEnglish = SubtitleInfo("en", "英语", "https://example.com/en")
    private val aiChinese = SubtitleInfo("ai-zh", "中文（自动生成）", "https://example.com/ai", isAi = true)
    private val manualChinese = SubtitleInfo("zh-CN", "中文", "https://example.com/zh")
    private val traditionalChinese = SubtitleInfo("zh-Hant", "中文（繁体）", "https://example.com/hant")

    @Test fun lyricsRequireAnExplicitSubtitleSource() {
        val list = listOf(aiChinese, manualEnglish, traditionalChinese, manualChinese)
        assertNull(selectLyricsSubtitle(list, LyricsEmbedding()))
        assertNull(selectLyricsSubtitle(listOf(aiChinese), LyricsEmbedding()))
    }

    @Test fun explicitLyricsLanguageNeverFallsBackToADifferentLanguage() {
        val list = listOf(aiChinese, manualEnglish, manualChinese)
        assertEquals(manualEnglish, selectLyricsSubtitle(list, LyricsEmbedding(language = "en")))
        assertEquals(aiChinese, selectLyricsSubtitle(list, LyricsEmbedding(language = "ai-zh")))
        assertNull(selectLyricsSubtitle(list, LyricsEmbedding(language = "ja")))
    }

    @Test fun aiLanguageCodeCountsAsGeneratedEvenWithoutTheApiFlag() {
        assertTrue(aiChinese.copy(isAi = false).isGenerated)
        assertEquals("中文 · AI", aiChinese.copy(isAi = false).displayName)
    }

    @Test fun subtitleTracksKeepBilibiliOrderAndIgnoreMissingUrls() {
        val list = listOf(aiChinese, manualEnglish.copy(url = ""), manualChinese, traditionalChinese)
        assertEquals(listOf(aiChinese, manualChinese, traditionalChinese), selectEmbeddedSubtitleTracks(list, SubtitleTrackEmbedding()))
        assertEquals(
            listOf(aiChinese, traditionalChinese),
            selectEmbeddedSubtitleTracks(list, SubtitleTrackEmbedding(languages = listOf("zh-Hant", "ai-zh", "en"))),
        )
    }

    @Test fun generatedLabelsAreConsistentWithoutDuplicatingUpstreamNames() {
        assertEquals("中文", aiChinese.languageName)
        assertEquals("英语", manualEnglish.copy(name = "英语 · AI 字幕", isAi = true).languageName)
        assertEquals("中文（简体）", aiChinese.copy(name = "中文（简体）（自动生成）").languageName)
        assertEquals("中文（繁体）", traditionalChinese.languageName)
        assertEquals("中文 · AI", aiChinese.displayName)
        assertEquals("中文 · AI", aiChinese.copy(name = "中文", isAi = false).displayName)
        assertEquals("英语 · AI", manualEnglish.copy(isAi = true).displayName)
        assertEquals("英语 · AI", manualEnglish.copy(name = "英语 · AI 字幕", isAi = true).displayName)
        assertEquals("中文（简体） · AI", aiChinese.copy(name = "中文（简体）（自动生成）").displayName)
        assertEquals("中文（繁体）", traditionalChinese.displayName)
        assertEquals("中文 (简体) · AI", subtitleLanguageDisplayName("ai-zh-Hans"))
    }

    @Test fun displayLanguageChangesNamesWithoutChangingSubtitleSelection() {
        val generated = manualEnglish.copy(isAi = true)
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("English · AI", generated.displayName)
        Locale.setDefault(Locale.JAPANESE)
        assertEquals("英語 · AI", generated.displayName)
        assertEquals("en", generated.lan)
        assertEquals("英语", generated.name)
        assertEquals(manualEnglish, selectLyricsSubtitle(listOf(manualEnglish), LyricsEmbedding(language = "en")))
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
