package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadTaskType
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
}
