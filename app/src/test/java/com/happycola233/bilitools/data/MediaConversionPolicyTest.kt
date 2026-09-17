package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.core.AudioQualities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaConversionPolicyTest {
    @Test
    fun targetFor_appliesMp3OnlyToAudioTasks() {
        assertEquals(
            MediaConversionTarget.MP3,
            MediaConversionPolicy.targetFor(
                DownloadTaskType.Audio,
                convertAudioToMp3 = true,
                convertVideoToMp4 = false,
            ),
        )
        assertNull(
            MediaConversionPolicy.targetFor(
                DownloadTaskType.AudioVideo,
                convertAudioToMp3 = true,
                convertVideoToMp4 = false,
            ),
        )
    }

    @Test
    fun targetFor_appliesMp4ToVideoAndAudioVideoTasks() {
        listOf(DownloadTaskType.Video, DownloadTaskType.AudioVideo).forEach { taskType ->
            assertEquals(
                MediaConversionTarget.MP4,
                MediaConversionPolicy.targetFor(
                    taskType,
                    convertAudioToMp3 = false,
                    convertVideoToMp4 = true,
                ),
            )
        }
    }

    @Test
    fun outputFileName_replacesOriginalExtension() {
        assertEquals(
            "sample.mp3",
            MediaConversionPolicy.outputFileName("sample.flac", MediaConversionTarget.MP3),
        )
        assertEquals(
            "sample.mp4",
            MediaConversionPolicy.outputFileName("sample.mkv", MediaConversionTarget.MP4),
        )
    }

    @Test
    fun subtitlesOnlyChangeFlvOutputToMp4WithoutChangingGlobalPreference() {
        fun target(extension: String, embedding: Boolean) = MediaConversionPolicy.targetFor(
            DownloadTaskType.AudioVideo,
            convertAudioToMp3 = false,
            convertVideoToMp4 = false,
            sourceExtension = extension,
            embedSubtitles = embedding,
        )
        assertEquals(MediaConversionTarget.MP4, target("flv", true))
        assertNull(target("flv", false))
        assertNull(target("mp4", true))
        assertNull(target("mkv", true))
    }

    @Test
    fun audioExtensionMatchesTheOutputContainerAndMediaStoreMimeType() {
        val dolbyExtension = AudioQualities.audioFileExtension(AudioQualities.DOLBY_ATMOS)
        assertEquals("m4a", dolbyExtension)
        assertEquals("audio/mp4", MediaConversionPolicy.mediaMimeType(dolbyExtension))
        listOf(AudioQualities.HI_RES_LOSSLESS, AudioQualities.LOSSLESS_FLAC).forEach { quality ->
            val extension = AudioQualities.audioFileExtension(quality)
            assertEquals("flac", extension)
            assertEquals("audio/flac", MediaConversionPolicy.mediaMimeType(extension))
        }
        assertEquals("audio/mpeg", MediaConversionPolicy.mediaMimeType(MediaConversionTarget.MP3.outputExtension))
        assertEquals("video/mp4", MediaConversionPolicy.mediaMimeType(MediaConversionTarget.MP4.outputExtension))
        assertNull(MediaConversionPolicy.mediaMimeType("m4s"))
    }
}
