package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.DownloadEmbedding
import com.happycola233.bilitools.data.model.LyricsEmbedding
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSelectionTest {
    private val subtitles = listOf(
        SubtitleInfo(lan = "en-US", name = "英语（美国）", url = "https://example.com/en"),
        SubtitleInfo(lan = "zh-Hans", name = "中文（简体）", url = "https://example.com/zh"),
    )

    @Test
    fun embeddingFollowsOutputTypeAndContainerSupport() {
        val video = ParseUiState(
            selectedItemIndices = listOf(0),
            outputType = OutputType.AudioVideo,
            embedSubtitlesEnabled = true,
            embedSubtitleLanguages = setOf("zh-Hans"),
            embedLyricsEnabled = true,
            embedLyricsLanguage = "en-US",
            embedIncludeGeneratedSubtitles = false,
        )
        assertEquals(
            DownloadEmbedding(subtitles = SubtitleTrackEmbedding(listOf("zh-Hans"), includeGenerated = false)),
            video.downloadEmbedding(videoContainerSupportsSubtitles = true, audioContainerSupportsLyrics = true),
        )
        // FLV 等不能承载字幕轨的容器：没有可嵌入的内容就不生成请求。
        assertNull(video.downloadEmbedding(videoContainerSupportsSubtitles = false, audioContainerSupportsLyrics = true))
        assertNull(video.copy(embedSubtitlesEnabled = false).downloadEmbedding(true, true))

        val audio = video.copy(outputType = OutputType.AudioOnly)
        assertEquals(
            DownloadEmbedding(lyrics = LyricsEmbedding(language = "en-US", includeGenerated = false)),
            audio.downloadEmbedding(videoContainerSupportsSubtitles = true, audioContainerSupportsLyrics = true),
        )
        assertNull(audio.downloadEmbedding(videoContainerSupportsSubtitles = true, audioContainerSupportsLyrics = false))
        assertNull(video.copy(outputType = null).downloadEmbedding(true, true))
    }

    @Test
    fun batchDownloadsEmbedEveryLanguageInsteadOfHiddenSingleSelection() {
        val batch = ParseUiState(
            selectedItemIndices = listOf(0, 1),
            outputType = OutputType.VideoOnly,
            embedSubtitlesEnabled = true,
            embedSubtitleLanguages = setOf("zh-Hans"),
            embedLyricsEnabled = true,
            embedLyricsLanguage = "en-US",
        )
        assertEquals(
            SubtitleTrackEmbedding(languages = emptyList(), includeGenerated = true),
            batch.downloadEmbedding(true, true)?.subtitles,
        )
        assertEquals(
            LyricsEmbedding(language = null, includeGenerated = true),
            batch.copy(outputType = OutputType.AudioOnly).downloadEmbedding(true, true)?.lyrics,
        )
    }

    @Test
    fun embedLanguagePicksKeepValidChoicesAndFallBackSensibly() {
        assertEquals(setOf("en-US", "zh-Hans"), pickEmbedSubtitleLanguages(subtitles, emptySet()))
        assertEquals(setOf("zh-Hans"), pickEmbedSubtitleLanguages(subtitles, setOf("zh-Hans", "ja")))
        assertEquals(setOf("en-US", "zh-Hans"), pickEmbedSubtitleLanguages(subtitles, setOf("ja")))
        assertTrue(pickEmbedSubtitleLanguages(emptyList(), setOf("ja")).isEmpty())

        assertEquals("en-US", pickEmbedLyricsLanguage(subtitles, "en-US"))
        assertEquals("zh-Hans", pickEmbedLyricsLanguage(subtitles, "ja"))
        assertEquals("zh-Hans", pickEmbedLyricsLanguage(subtitles, null))
        assertNull(pickEmbedLyricsLanguage(emptyList(), "en-US"))
    }

    @Test
    fun selectedLanguage_onlyReturnsExactMatch() {
        val selected = selectSubtitles(
            subtitles = subtitles,
            languageSelection = SubtitleLanguageSelection.Language("zh-Hans"),
            policy = SubtitleSelectionPolicy.SelectedLanguage,
        )

        assertEquals(listOf(subtitles[1]), selected)
    }

    @Test
    fun selectedLanguage_doesNotMatchSimilarLanguage() {
        val similarLanguage = selectSubtitles(
            subtitles = subtitles,
            languageSelection = SubtitleLanguageSelection.Language("zh-CN"),
            policy = SubtitleSelectionPolicy.SelectedLanguage,
        )

        assertTrue(similarLanguage.isEmpty())
    }

    @Test
    fun allAvailable_returnsEverySubtitleAndIgnoresSpecificLanguage() {
        val selected = selectSubtitles(
            subtitles = subtitles,
            languageSelection = SubtitleLanguageSelection.Language("missing-language"),
            policy = SubtitleSelectionPolicy.AllAvailable,
        )

        assertEquals(subtitles, selected)
        assertTrue(
            selectSubtitles(
                subtitles = emptyList(),
                languageSelection = SubtitleLanguageSelection.All,
                policy = SubtitleSelectionPolicy.AllAvailable,
            ).isEmpty(),
        )
    }

    @Test
    fun singleLanguage_doesNotOfferAnAllSelection() {
        assertEquals(
            SubtitleLanguageSelection.Language("en-US"),
            pickSubtitleLanguageSelection(
                subtitles = listOf(subtitles.first()),
                currentSelection = SubtitleLanguageSelection.All,
            ),
        )
    }

    @Test
    fun multipleLanguages_defaultToAllAndPreserveValidSelection() {
        assertEquals(
            SubtitleLanguageSelection.All,
            pickSubtitleLanguageSelection(subtitles, currentSelection = null),
        )
        assertEquals(
            SubtitleLanguageSelection.All,
            pickSubtitleLanguageSelection(subtitles, currentSelection = SubtitleLanguageSelection.All),
        )
        assertEquals(
            SubtitleLanguageSelection.Language("en-US"),
            pickSubtitleLanguageSelection(
                subtitles,
                currentSelection = SubtitleLanguageSelection.Language("en-US"),
            ),
        )
        assertEquals(
            SubtitleLanguageSelection.All,
            pickSubtitleLanguageSelection(
                subtitles,
                currentSelection = SubtitleLanguageSelection.Language("missing-language"),
            ),
        )
    }

    @Test
    fun emptyLanguages_haveNoSelectionAndProduceNoDownload() {
        assertNull(pickSubtitleLanguageSelection(emptyList(), SubtitleLanguageSelection.All))
        assertTrue(
            selectSubtitles(
                subtitles = emptyList(),
                languageSelection = SubtitleLanguageSelection.All,
                policy = SubtitleSelectionPolicy.AllAvailable,
            ).isEmpty(),
        )
        assertTrue(
            selectSubtitles(
                subtitles = emptyList(),
                languageSelection = SubtitleLanguageSelection.Language("en-US"),
                policy = SubtitleSelectionPolicy.SelectedLanguage,
            ).isEmpty(),
        )
    }

    @Test
    fun policy_selectsAllForAllOptionOrMultipleItems() {
        assertEquals(
            SubtitleSelectionPolicy.SelectedLanguage,
            subtitleSelectionPolicy(1, SubtitleLanguageSelection.Language("en-US")),
        )
        assertEquals(
            SubtitleSelectionPolicy.AllAvailable,
            subtitleSelectionPolicy(1, SubtitleLanguageSelection.All),
        )
        assertEquals(
            SubtitleSelectionPolicy.AllAvailable,
            subtitleSelectionPolicy(1, languageSelection = null),
        )
        assertEquals(
            SubtitleSelectionPolicy.AllAvailable,
            subtitleSelectionPolicy(2, SubtitleLanguageSelection.Language("en-US")),
        )
    }
}
