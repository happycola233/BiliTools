package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFormatAvailabilityTest {
    @Test
    fun retiredFlvIsUnavailableForOrdinaryVideoWithoutProbing() {
        val state = state(item(1, MediaType.Video)).copy(format = StreamFormat.Flv)
        assertEquals(setOf(StreamFormat.Flv), state.unavailableStreamFormats)
        assertEquals(StreamFormat.Dash, state.availableStreamFormat)
    }

    @Test
    fun differentVideoEndpointsRemainUnknownUntilAResponseConfirmsTheirCapabilities() {
        assertTrue(state(item(1, MediaType.Bangumi)).unavailableStreamFormats.isEmpty())
        assertTrue(state(item(1, MediaType.Lesson)).unavailableStreamFormats.isEmpty())
    }

    @Test
    fun actualFlvResponseTakesPrecedenceOverRetirementInformation() {
        val item = item(1, MediaType.Video)
        val state = state(item).copy(
            format = StreamFormat.Flv,
            streamFormatEvidence = mapOf(item.streamFormatKey() to StreamFormatEvidence().withResponse(StreamFormat.Flv, StreamFormat.Flv)),
        )
        assertFalse(StreamFormat.Flv in state.unavailableStreamFormats)
        assertEquals(StreamFormat.Flv, state.availableStreamFormat)
    }

    @Test
    fun successfulFallbackDisablesOnlyTheRequestedFormatForThatResource() {
        val first = item(1, MediaType.Bangumi)
        val second = item(2, MediaType.Bangumi)
        val state = state(first, second).copy(
            selectedItemIndices = listOf(0),
            format = StreamFormat.Flv,
            streamFormatEvidence = mapOf(first.streamFormatKey() to StreamFormatEvidence().withResponse(StreamFormat.Flv, StreamFormat.Mp4)),
        )
        assertEquals(setOf(StreamFormat.Flv), state.unavailableStreamFormats)
        assertEquals(StreamFormat.Dash, state.availableStreamFormat)
        assertTrue(state.copy(selectedItemIndex = 1, selectedItemIndices = listOf(1)).unavailableStreamFormats.isEmpty())
    }

    @Test
    fun allSelectedResourcesContributeKnownRestrictionsButUnselectedOnesDoNot() {
        val ordinary = item(1, MediaType.Video)
        val episode = item(2, MediaType.Bangumi)
        val batch = state(ordinary, episode)
        assertEquals(setOf(StreamFormat.Flv), batch.unavailableStreamFormats)
        assertTrue(batch.copy(selectedItemIndex = 1, selectedItemIndices = listOf(1)).unavailableStreamFormats.isEmpty())
    }

    @Test
    fun subtitleAndLyricsOrAudioOnlySelectionDoNotMakeDashUnavailable() {
        val audioOnly = state(item(1, MediaType.Video)).copy(outputType = OutputType.AudioOnly, embedLyricsEnabled = true)
        assertFalse(StreamFormat.Dash in audioOnly.unavailableStreamFormats)
        assertEquals(StreamFormat.Dash, audioOnly.availableStreamFormat)
    }

    @Test
    fun differentPartsOfOneWorkHaveIndependentResponseEvidence() {
        val first = item(1, MediaType.Lesson)
        val second = first.copy(cid = 12)
        val state = state(first, second).copy(
            selectedItemIndex = 1,
            selectedItemIndices = listOf(1),
            streamFormatEvidence = mapOf(first.streamFormatKey() to StreamFormatEvidence().withResponse(StreamFormat.Dash, StreamFormat.Mp4)),
        )
        assertTrue(state.unavailableStreamFormats.isEmpty())
    }

    @Test
    fun newestSuccessfulResponseReplacesEarlierContradictoryEvidence() {
        val item = item(1, MediaType.Bangumi)
        val initiallySupported = StreamFormatEvidence().withResponse(StreamFormat.Flv, StreamFormat.Flv)
        val noLongerSupported = initiallySupported.withResponse(StreamFormat.Flv, StreamFormat.Mp4)
        assertTrue(StreamFormat.Flv in item.unavailableStreamFormats(noLongerSupported))
        assertFalse(StreamFormat.Mp4 in item.unavailableStreamFormats(noLongerSupported))
    }

    @Test
    fun conflictingKnownCapabilitiesRequireSeparateBatches() {
        val first = item(1, MediaType.Video)
        val second = item(2, MediaType.Video)
        val batch = state(first, second).copy(
            streamFormatEvidence = mapOf(
                first.streamFormatKey() to StreamFormatEvidence().withResponse(StreamFormat.Dash, StreamFormat.Mp4),
                second.streamFormatKey() to StreamFormatEvidence().withResponse(StreamFormat.Mp4, StreamFormat.Dash),
            ),
        )
        assertFalse(batch.hasCommonStreamFormat)
        assertTrue(batch.copy(selectedItemIndices = listOf(0)).hasCommonStreamFormat)
        assertTrue(batch.copy(selectedItemIndex = 1, selectedItemIndices = listOf(1)).hasCommonStreamFormat)
    }

    @Test
    fun networkFailureAloneDoesNotDisableAnyPreviouslyUnknownFormat() {
        val state = state(item(1, MediaType.Bangumi)).copy(error = "网络连接失败")
        assertTrue(state.unavailableStreamFormats.isEmpty())
    }

    private fun item(id: Long, type: MediaType) = MediaItem(
        title = "视频 $id", coverUrl = "", description = "", url = "", duration = 0, pubTime = 0,
        type = type, isTarget = true, index = 0, aid = id, cid = id * 10,
    )

    private fun state(vararg items: MediaItem) = ParseUiState(items = items.toList(), selectedItemIndices = items.indices.toList())
}
