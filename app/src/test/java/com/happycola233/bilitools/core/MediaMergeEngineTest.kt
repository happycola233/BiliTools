package com.happycola233.bilitools.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMergeEngineTest {
    @Test
    fun buildMergeArguments_includesStrictUnofficialForMp4() {
        val args = MediaMergeEngine.buildMergeArguments(
            videoFile = File("video.m4s"),
            audioFile = File("audio.m4s"),
            outputFile = File("output.mp4"),
        )

        assertTrue(args.containsAll(listOf("-strict", "unofficial")))
        assertTrue(args.containsAll(listOf("-movflags", "+faststart")))
    }

    @Test
    fun buildMergeArguments_skipsMp4SpecificFlagsForMkv() {
        val args = MediaMergeEngine.buildMergeArguments(
            videoFile = File("video.m4s"),
            audioFile = File("audio.flac"),
            outputFile = File("output.mkv"),
        )

        assertFalse(args.contains("-strict"))
        assertFalse(args.contains("-movflags"))
    }
}
