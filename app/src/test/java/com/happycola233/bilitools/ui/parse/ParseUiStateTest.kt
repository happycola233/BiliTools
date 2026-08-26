package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseUiStateTest {
    @Test
    fun hasSelectedDownloadContent_isFalseWhenMediaAndExtrasAreDisabled() {
        val state = ParseUiState(outputType = null)

        assertFalse(state.hasSelectedDownloadContent)
    }

    @Test
    fun hasSelectedDownloadContent_isTrueForEverySupportedContentSelection() {
        val emptyState = ParseUiState(outputType = null)
        val statesWithContent = listOf(
            emptyState.copy(outputType = OutputType.AudioVideo),
            emptyState.copy(outputType = OutputType.VideoOnly),
            emptyState.copy(outputType = OutputType.AudioOnly),
            emptyState.copy(subtitleEnabled = true),
            emptyState.copy(aiSummaryEnabled = true),
            emptyState.copy(nfoCollectionEnabled = true),
            emptyState.copy(nfoSingleEnabled = true),
            emptyState.copy(danmakuLiveEnabled = true),
            emptyState.copy(danmakuHistoryEnabled = true),
            emptyState.copy(selectedImageIds = setOf("cover")),
            emptyState.copy(opusContentEnabled = true),
            emptyState.copy(opusImagesEnabled = true),
        )

        statesWithContent.forEach { state ->
            assertTrue(state.hasSelectedDownloadContent)
        }
    }

    @Test
    fun isMultiSelect_requiresMoreThanOneSelectedItem() {
        assertFalse(ParseUiState(selectedItemIndices = emptyList()).isMultiSelect)
        assertFalse(ParseUiState(selectedItemIndices = listOf(0)).isMultiSelect)
        assertTrue(ParseUiState(selectedItemIndices = listOf(0, 1)).isMultiSelect)
    }

    @Test
    fun defaultContentSelection_opusSkipsStreamAndSelectsBothExports() {
        val selection = defaultParseContentSelection(MediaType.Opus)

        assertNull(selection.outputType)
        assertTrue(selection.opusContentEnabled)
        assertTrue(selection.opusImagesEnabled)
    }

    @Test
    fun defaultContentSelection_playableMediaKeepsAudioVideoBehavior() {
        val selection = defaultParseContentSelection(MediaType.Video)

        assertEquals(OutputType.AudioVideo, selection.outputType)
        assertFalse(selection.opusContentEnabled)
        assertFalse(selection.opusImagesEnabled)
    }
}
