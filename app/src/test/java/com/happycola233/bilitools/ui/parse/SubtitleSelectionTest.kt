package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.SubtitleInfo
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
