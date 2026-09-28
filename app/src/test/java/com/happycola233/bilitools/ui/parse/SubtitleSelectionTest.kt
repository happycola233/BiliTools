package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.DownloadEmbedding
import com.happycola233.bilitools.data.model.LyricsEmbedding
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding
import org.junit.Assert.*
import org.junit.Test

class SubtitleSelectionTest {
    private val subtitles = listOf(
        SubtitleInfo("en-US", "英语", "https://example.com/en"),
        SubtitleInfo("zh-Hans", "中文（简体）", "https://example.com/zh"),
        SubtitleInfo("ai-en", "英语", "https://example.com/ai-en", isAi = true),
    )
    private val videoItem = MediaItem(
        title = "测试视频", coverUrl = "", description = "", url = "https://www.bilibili.com/video/BV1test",
        duration = 60, pubTime = 0L, type = MediaType.Video, isTarget = true, index = 0, aid = 1, bvid = "BV1test", cid = 2,
    )
    private val lyricsState = ParseUiState(
        items = listOf(videoItem), selectedItemIndices = listOf(0), outputType = OutputType.AudioOnly,
        embedLyricsEnabled = true, isLoggedIn = true,
    )

    @Test fun singleAndBatchKeepTheSameExplicitLanguages() {
        val single = ParseUiState(
            selectedItemIndices = listOf(0),
            outputType = OutputType.AudioVideo,
            embedSubtitlesEnabled = true,
            embedSubtitleSelection = SubtitleLanguageSelection.Languages(setOf("en-US", "ai-en")),
            embedLyricsEnabled = true,
            embedLyricsLanguage = "en-US",
        )
        val expected = DownloadEmbedding(subtitles = SubtitleTrackEmbedding(listOf("en-US", "ai-en")))
        assertEquals(expected, single.downloadEmbedding())
        assertEquals(expected, single.copy(selectedItemIndices = listOf(0, 1)).downloadEmbedding())
        assertEquals(
            DownloadEmbedding(lyrics = LyricsEmbedding("en-US")),
            single.copy(outputType = OutputType.AudioOnly, selectedItemIndices = listOf(0, 1)).downloadEmbedding(),
        )
        assertNull(single.copy(outputType = null).downloadEmbedding())
    }

    @Test fun allLanguagesIncludesEveryVisibleSource() {
        val selection = SubtitleLanguageSelection.All
        assertEquals(subtitles, selectSubtitles(subtitles, selection))
        assertEquals(SubtitleTrackEmbedding(), selection.toRequest())
    }

    @Test fun explicitMultiSelectionNeverFallsBackToAnotherLanguage() {
        val selection = SubtitleLanguageSelection.Languages(setOf("zh-Hans", "ai-en"))
        assertEquals(subtitles.drop(1), selectSubtitles(subtitles, selection))
        assertEquals(listOf(subtitles[2]), selectSubtitles(subtitles.takeLast(1), selection))
        assertTrue(selectSubtitles(subtitles, SubtitleLanguageSelection.Languages(setOf("ja"))).isEmpty())
    }

    @Test fun deselectingTheLastLanguageDoesNotMeanAll() {
        val selection = SubtitleLanguageSelection.Languages(setOf("en-US"))
            .toggle("en-US", subtitles)
        assertTrue(selection.isEmpty)
        assertTrue(selectSubtitles(subtitles, selection).isEmpty())
        val state = ParseUiState(subtitleEnabled = true, subtitleLanguageSelection = selection)
        assertTrue(state.hasIncompleteSubtitleSelection)
        assertFalse(state.copy(subtitleEnabled = false).hasIncompleteSubtitleSelection)
    }

    @Test fun lyricsWithoutAnySubtitleDoNotBlockTheDownload() {
        val noSubtitles = lyricsState.copy(subtitleLoadStatus = SubtitleLoadStatus.Ready)
        assertFalse(noSubtitles.awaitingSubtitleCatalog)
        assertFalse(noSubtitles.hasIncompleteSubtitleSelection)
        assertEquals(LyricsEmbedding(null), noSubtitles.downloadEmbedding()?.lyrics)
        assertFalse(noSubtitles.copy(isLoggedIn = false).hasIncompleteSubtitleSelection)
    }

    @Test fun availableLyricsLanguagesStillNeedAnExplicitChoice() {
        val choices = lyricsState.copy(subtitleList = subtitles, subtitleLoadStatus = SubtitleLoadStatus.Ready)
        assertFalse(choices.awaitingSubtitleCatalog)
        assertTrue(choices.hasIncompleteSubtitleSelection)
        assertFalse(choices.copy(embedLyricsLanguage = "en-US").hasIncompleteSubtitleSelection)
        assertFalse(choices.copy(embedLyricsEnabled = false).hasIncompleteSubtitleSelection)
    }

    @Test fun unknownCatalogIsNeitherSkippedNorTreatedAsEmpty() {
        val loading = lyricsState.copy(subtitleLoadStatus = SubtitleLoadStatus.Loading)
        assertTrue(loading.awaitingSubtitleCatalog)
        assertFalse(loading.copy(embedLyricsLanguage = "en-US").awaitingSubtitleCatalog)
        val failed = lyricsState.copy(subtitleLoadStatus = SubtitleLoadStatus.Failed, subtitleFailedCount = 1)
        assertFalse(failed.awaitingSubtitleCatalog)
        assertTrue(failed.hasIncompleteSubtitleSelection)
    }

    @Test fun emptySubtitleSelectionOnlyBlocksWhileLanguagesCanBeChosen() {
        val empty = SubtitleLanguageSelection.Languages(emptySet())
        val state = ParseUiState(
            items = listOf(videoItem), selectedItemIndices = listOf(0), outputType = OutputType.VideoOnly,
            subtitleEnabled = true, subtitleLanguageSelection = empty,
            embedSubtitlesEnabled = true, embedSubtitleSelection = empty,
            subtitleList = subtitles, subtitleLoadStatus = SubtitleLoadStatus.Ready,
        )
        assertTrue(state.hasIncompleteSubtitleSelection)
        assertFalse(state.copy(subtitleList = emptyList()).hasIncompleteSubtitleSelection)
    }

    @Test fun togglingAllChangesOnlyTheClickedSource() {
        assertEquals(
            SubtitleLanguageSelection.Languages(setOf("en-US", "ai-en")),
            SubtitleLanguageSelection.All.toggle("zh-Hans", subtitles),
        )
        assertEquals(
            SubtitleLanguageSelection.Languages(setOf("en-US", "zh-Hans")),
            SubtitleLanguageSelection.All.toggle("ai-en", subtitles),
        )
    }

    @Test fun containerDecisionsCannotEraseTheRequestedEmbedding() {
        val state = ParseUiState(
            outputType = OutputType.AudioOnly,
            selectedAudioId = com.happycola233.bilitools.core.AudioQualities.DOLBY_ATMOS,
            embedLyricsEnabled = true,
            embedLyricsLanguage = "en-US",
        )
        assertEquals(LyricsEmbedding("en-US"), state.downloadEmbedding()?.lyrics)
        assertNotNull(state.copy(
            outputType = OutputType.AudioVideo,
            format = com.happycola233.bilitools.data.model.StreamFormat.Flv,
            embedSubtitlesEnabled = true,
        ).downloadEmbedding()?.subtitles)
    }
}
