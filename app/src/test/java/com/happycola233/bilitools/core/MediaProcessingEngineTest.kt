package com.happycola233.bilitools.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaProcessingEngineTest {
    @Test
    fun buildMergeArguments_includesStrictUnofficialForMp4() {
        val args = MediaProcessingEngine.buildMergeArguments(
            videoFile = File("video.m4s"),
            audioFile = File("audio.m4s"),
            outputFile = File("output.mp4"),
        )

        assertTrue(args.containsAll(listOf("-strict", "unofficial")))
        assertTrue(args.containsAll(listOf("-movflags", "+faststart")))
    }

    @Test
    fun buildMergeArguments_transcodesOnlyAudioForForcedMp4() {
        val args = MediaProcessingEngine.buildMergeArguments(
            videoFile = File("video.m4s"),
            audioFile = File("audio.flac"),
            outputFile = File("output.mp4"),
            transcodeAudioToAac = true,
        )

        assertTrue(args.containsAll(listOf("-c:v", "copy")))
        assertTrue(args.containsAll(listOf("-c:a", "aac")))
        assertTrue(args.containsAll(listOf("-b:a", "192k")))
        assertFalse(args.containsAll(listOf("-c", "copy")))
    }

    @Test
    fun buildMergeArguments_skipsMp4SpecificFlagsForMkv() {
        val args = MediaProcessingEngine.buildMergeArguments(
            videoFile = File("video.m4s"),
            audioFile = File("audio.flac"),
            outputFile = File("output.mkv"),
        )

        assertFalse(args.contains("-strict"))
        assertFalse(args.contains("-movflags"))
    }

    @Test
    fun buildMp3ConversionArguments_usesDesktopQualitySettings() {
        val args = MediaProcessingEngine.buildMp3ConversionArguments(
            inputFile = File("audio.flac"),
            outputFile = File("audio.mp3"),
        )

        assertTrue(args.containsAll(listOf("-c:a", "libmp3lame")))
        assertTrue(args.containsAll(listOf("-q:a", "2")))
        assertTrue(args.containsAll(listOf("-id3v2_version", "4")))
    }

    @Test
    fun buildMp4ConversionArguments_copiesVideoAndTranscodesAudio() {
        val args = MediaProcessingEngine.buildMp4ConversionArguments(
            inputFile = File("video.mkv"),
            outputFile = File("video.mp4"),
        )

        assertTrue(args.containsAll(listOf("-c:v", "copy")))
        assertTrue(args.containsAll(listOf("-c:a", "aac")))
        assertTrue(args.containsAll(listOf("-b:a", "192k")))
        assertTrue(args.containsAll(listOf("-movflags", "+faststart")))
    }
}
