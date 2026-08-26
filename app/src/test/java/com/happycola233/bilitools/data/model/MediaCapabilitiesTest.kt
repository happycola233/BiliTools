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
    }

    @Test
    fun existingPlayableTypesKeepPlaybackCapability() {
        listOf(MediaType.Video, MediaType.Bangumi, MediaType.Lesson, MediaType.Music)
            .forEach { type -> assertTrue(type.capabilities.supportsPlaybackStream) }
    }

    @Test
    fun opusImagesUseResumableTransferWhileMarkdownRemainsGeneratedContent() {
        assertTrue(DownloadTaskType.OpusImage.isManagedTransfer)
        assertFalse(DownloadTaskType.OpusContent.isManagedTransfer)
        assertTrue(DownloadTaskType.Video.isManagedTransfer)
    }
}
