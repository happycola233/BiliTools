package com.happycola233.bilitools.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCapabilitiesTest {
    @Test
    fun opusExportsDocumentsWithoutPlaybackStream() {
        val capabilities = MediaType.Opus.capabilities

        assertTrue(capabilities.supportsOpusExport)
        assertFalse(capabilities.supportsPlaybackStream)
        assertFalse(capabilities.supportsSubtitleExport)
        assertFalse(capabilities.supportsAiSummaryExport)
        assertFalse(capabilities.supportsNfoExport)
        assertFalse(capabilities.supportsDanmakuExport)
        assertFalse(capabilities.supportsAuxiliaryImageExport)
    }

    @Test
    fun opusCollectionsKeepDocumentExportWithoutPlayerExtras() {
        listOf(MediaType.OpusList, MediaType.UserOpus).forEach { type ->
            val capabilities = type.capabilities

            assertTrue(capabilities.supportsOpusExport)
            assertFalse(capabilities.supportsPlaybackStream)
            assertFalse(capabilities.supportsSubtitleExport)
            assertFalse(capabilities.supportsAiSummaryExport)
            assertFalse(capabilities.supportsNfoExport)
            assertFalse(capabilities.supportsDanmakuExport)
            assertFalse(capabilities.supportsAuxiliaryImageExport)
        }
    }

    @Test
    fun existingPlayableTypesKeepPlaybackAndExtraCapabilities() {
        listOf(MediaType.Video, MediaType.Bangumi, MediaType.Lesson, MediaType.Music)
            .forEach { type ->
                val capabilities = type.capabilities

                assertTrue(capabilities.supportsPlaybackStream)
                assertTrue(capabilities.supportsSubtitleExport)
                assertTrue(capabilities.supportsAiSummaryExport)
                assertTrue(capabilities.supportsNfoExport)
                assertTrue(capabilities.supportsDanmakuExport)
                assertTrue(capabilities.supportsAuxiliaryImageExport)
            }
    }

    @Test
    fun opusImagesUseResumableTransferWhileMarkdownRemainsGeneratedContent() {
        assertTrue(DownloadTaskType.OpusImage.isManagedTransfer)
        assertFalse(DownloadTaskType.OpusContent.isManagedTransfer)
        assertTrue(DownloadTaskType.Video.isManagedTransfer)
    }
}
