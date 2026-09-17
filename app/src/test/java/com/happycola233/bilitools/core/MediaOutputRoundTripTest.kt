package com.happycola233.bilitools.core

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 使用主机 FFmpeg 验证最终文件，生成的样本均位于 .tmp，不需要连接设备。 */
class MediaOutputRoundTripTest {
    @Test
    fun dashVideoOnlyBecomesRegularMp4WithoutTagsOrReencoding() {
        val tools = tools()
        val directory = directory("dash-video-only")
        val source = File(directory, "source.m4s")
        val output = File(directory, "video.mp4")
        run(listOf(tools.ffmpeg, "-v", "error", "-y", "-f", "lavfi", "-i",
            "color=c=red:s=128x96:r=10:d=1", "-c:v", "libx264", "-pix_fmt", "yuv420p",
            "-an", "-movflags", "+frag_keyframe+empty_moov", "-f", "mp4", source.absolutePath))
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildMp4ConversionArguments(source, output))
        assertEquals(packetHashes(tools, source, "V"), packetHashes(tools, output, "V"))
        val outputProbe = probe(tools, output)
        assertTrue(outputProbe, outputProbe.contains("format_name=mov,mp4,m4a,3gp,3g2,mj2"))
        assertFalse(outputProbe, outputProbe.contains("codec_type=audio"))
    }

    @Test
    fun officialAtmosSampleRetainsJocPacketsLyricsCoverAndSubtitleTracks() {
        val tools = tools()
        val samplePath = System.getenv("BILITOOLS_TEST_ATMOS_SAMPLE")
        assumeTrue("Set BILITOOLS_TEST_ATMOS_SAMPLE to a Dolby JOC MP4 sample", !samplePath.isNullOrBlank())
        val source = File(samplePath!!)
        val sourceConfig = Mp4DolbyConfiguration.readConfigurations(source).single()
        assertTrue(Mp4DolbyConfiguration.hasAtmosExtension(sourceConfig))
        val directory = directory("official-atmos")
        val audio = File(directory, "audio.m4a")
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildAudioRemuxArguments(source, audio))
        Mp4DolbyConfiguration.preserve(source, audio)
        assertArrayEquals(sourceConfig, Mp4DolbyConfiguration.readConfigurations(audio).single())
        assertEquals(packetHashes(tools, source, "a"), packetHashes(tools, audio, "a"))

        val cover = File(directory, "cover.jpg")
        javaClass.getResourceAsStream("/metadata/cover.jpg")!!.use { input ->
            cover.outputStream().use { output -> input.copyTo(output) }
        }
        val lyrics = File(directory, "lyrics.m4a")
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildMetadataArguments(
            audio, lyrics, "mp4", mapOf("lyrics" to "[00:00.00]Atmos lyrics"), EmbeddedCover(cover, 64, 48),
        ))
        Mp4DolbyConfiguration.preserve(audio, lyrics)
        assertArrayEquals(sourceConfig, Mp4DolbyConfiguration.readConfigurations(lyrics).single())
        assertEquals(packetHashes(tools, source, "a"), packetHashes(tools, lyrics, "a"))
        assertTrue(probe(tools, lyrics).contains("TAG:lyrics=[00:00.00]Atmos lyrics"))
        assertTrue(probe(tools, lyrics).contains("DISPOSITION:attached_pic=1"))

        val merged = File(directory, "merged.mp4")
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildMergeArguments(source, audio, merged))
        Mp4DolbyConfiguration.preserve(audio, merged)
        val subtitle = File(directory, "subtitle.srt").apply {
            writeText("1\n00:00:00,000 --> 00:00:01,000\nAtmos subtitle\n")
        }
        val subtitled = File(directory, "subtitled.mp4")
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildMetadataArguments(
            merged, subtitled, "mp4", mapOf("title" to "Atmos video"), EmbeddedCover(cover, 64, 48),
            listOf(EmbeddedSubtitleTrack(subtitle, "en", "英语")),
        ))
        Mp4DolbyConfiguration.preserve(merged, subtitled)
        assertArrayEquals(sourceConfig, Mp4DolbyConfiguration.readConfigurations(subtitled).single())
        for (stream in listOf("a", "V")) {
            assertEquals(packetHashes(tools, merged, stream), packetHashes(tools, subtitled, stream))
        }
        val videoProbe = probe(tools, subtitled)
        assertTrue(videoProbe, videoProbe.contains("codec_name=mov_text"))
        assertTrue(videoProbe, videoProbe.contains("DISPOSITION:attached_pic=1"))
        assertTrue(videoProbe, videoProbe.contains("profile=Dolby Digital Plus + Dolby Atmos"))
    }

    @Test
    fun dolbyM4aSupportsLyricsWithoutReencodingTheEac3Packets() {
        val tools = tools()
        val directory = directory("dolby-lyrics")
        val source = File(directory, "dash.m4s")
        val normalized = File(directory, "audio.m4a")
        val tagged = File(directory, "lyrics.m4a")
        run(listOf(tools.ffmpeg, "-v", "error", "-y", "-f", "lavfi", "-i",
            "sine=frequency=440:duration=1", "-c:a", "eac3", "-b:a", "192k",
            "-movflags", "+frag_keyframe+delay_moov", "-f", "mp4", source.absolutePath))
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildAudioRemuxArguments(source, normalized))
        val lyrics = "[00:00.00]杜比音频歌词"
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildMetadataArguments(
            normalized, tagged, "mp4", mapOf("lyrics" to lyrics),
        ))

        val probe = probe(tools, tagged)
        assertTrue(probe, probe.contains("codec_name=eac3"))
        assertTrue(probe, probe.contains("format_name=mov,mp4,m4a,3gp,3g2,mj2"))
        assertTrue(probe, probe.contains(lyrics))
        assertEquals(packetHashes(tools, source, "a"), packetHashes(tools, normalized, "a"))
        assertEquals(packetHashes(tools, source, "a"), packetHashes(tools, tagged, "a"))
    }

    @Test
    fun dashFlacBecomesNativeFlacEvenWithoutTagsOrLyrics() {
        val tools = tools()
        val directory = directory("flac-without-tags")
        val source = File(directory, "dash.m4s")
        val output = File(directory, "audio.flac")
        run(listOf(tools.ffmpeg, "-v", "error", "-y", "-f", "lavfi", "-i",
            "sine=frequency=440:duration=1", "-c:a", "flac", "-strict", "unofficial", "-f", "mp4", source.absolutePath))
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildAudioRemuxArguments(source, output))

        assertTrue(output.inputStream().use { it.readNBytes(4).contentEquals("fLaC".toByteArray()) })
        assertEquals(packetHashes(tools, source, "a"), packetHashes(tools, output, "a"))
        assertTrue(probe(tools, output).contains("format_name=flac"))
    }

    @Test
    fun flvWithAacChangesContainerWithoutChangingAudioOrVideo() {
        val tools = tools()
        val directory = directory("flv-to-mp4")
        val source = File(directory, "input.flv")
        val output = File(directory, "output.mp4")
        generateVideo(tools, source, "aac")
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildMp4ConversionArguments(
            source, output, transcodeAudioToAac = MediaProcessingEngine.needsAacForMp4("aac"),
        ))

        for (stream in listOf("a", "V")) {
            assertEquals(packetHashes(tools, source, stream), packetHashes(tools, output, stream))
        }
        assertTrue(probe(tools, output).contains("codec_name=aac"))
    }

    @Test
    fun flacVideoConversionKeepsVideoAndUsesAacCompatibilityOutput() {
        val tools = tools()
        val directory = directory("flac-to-mp4")
        val source = File(directory, "input.mkv")
        val output = File(directory, "output.mp4")
        generateVideo(tools, source, "flac")
        run(listOf(tools.ffmpeg) + MediaProcessingEngine.buildMp4ConversionArguments(
            source, output, transcodeAudioToAac = MediaProcessingEngine.needsAacForMp4("flac"),
        ))

        assertEquals(packetHashes(tools, source, "V"), packetHashes(tools, output, "V"))
        val probe = probe(tools, output)
        assertTrue(probe, probe.contains("codec_name=aac"))
        assertFalse(probe, probe.contains("codec_name=flac"))
    }

    private fun generateVideo(tools: Tools, file: File, audioCodec: String) {
        run(listOf(tools.ffmpeg, "-v", "error", "-y", "-f", "lavfi", "-i",
            "color=c=red:s=128x96:r=10:d=1", "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", audioCodec, file.absolutePath))
    }

    private fun probe(tools: Tools, file: File): String =
        run(listOf(tools.ffprobe, "-v", "error", "-show_streams", "-show_format", file.absolutePath))

    private fun packetHashes(tools: Tools, file: File, stream: String): List<String> =
        run(listOf(tools.ffprobe, "-v", "error", "-select_streams", stream,
            "-show_packets", "-show_entries", "packet=data_hash", "-show_data_hash", "sha256",
            "-of", "csv=p=0", file.absolutePath))
            .lineSequence().filter { it.startsWith("SHA256:") }.toList()
            .also { assertTrue("No packets found: $file", it.isNotEmpty()) }

    private fun directory(name: String) = File("../.tmp/media-output-tests/$name").apply { mkdirs() }

    private data class Tools(val ffmpeg: String, val ffprobe: String)

    private fun tools(): Tools {
        val path = System.getenv("BILITOOLS_TEST_FFMPEG")
        assumeTrue("Set BILITOOLS_TEST_FFMPEG to run media round trips", !path.isNullOrBlank())
        return Tools(File(path!!, "ffmpeg.exe").absolutePath, File(path, "ffprobe.exe").absolutePath)
    }

    private fun run(command: List<String>): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertTrue("Media command timed out", process.waitFor(30, TimeUnit.SECONDS))
        assertEquals(output, 0, process.exitValue())
        return output
    }
}
