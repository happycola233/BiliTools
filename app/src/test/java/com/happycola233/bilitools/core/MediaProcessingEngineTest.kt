package com.happycola233.bilitools.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaProcessingEngineTest {
    private val subtitleTracks = listOf(
        EmbeddedSubtitleTrack(File("zh.srt"), "zh-Hans", "中文（简体）"),
        EmbeddedSubtitleTrack(File("en.srt"), "ai-en", "英语（自动生成）"),
    )

    @Test
    fun buildMetadataArguments_mp4SubtitleTracksFollowTheCoverInputAndUseMovText() {
        val args = MediaProcessingEngine.buildMetadataArguments(
            inputFile = File("input.mp4"),
            outputFile = File("output.mp4"),
            outputFormat = "mp4",
            values = mapOf("title" to "标题"),
            cover = EmbeddedCover(File("cover.jpg"), 64, 48),
            subtitles = subtitleTracks,
        )

        // 输入顺序：媒体、封面、两条 SRT；封面占用 1 号输入，字幕从 2 号开始映射。
        val cover = File("cover.jpg").absolutePath
        val (chinese, english) = subtitleTracks.map { it.file.absolutePath }
        assertEquals(
            listOf("-i", cover, "-f", "srt", "-i", chinese, "-f", "srt", "-i", english),
            args.subList(args.indexOf(cover) - 1, args.indexOf(english) + 1),
        )
        assertTrue(args.containsAll(listOf("-map", "2:0", "-map", "3:0")))
        assertTrue(args.containsAll(listOf("-c:s", "mov_text")))
        assertTrue(args.containsAll(listOf("-metadata:s:s:0", "language=zho")))
        assertTrue(args.containsAll(listOf("-metadata:s:s:1", "language=eng")))
        assertTrue(args.containsAll(listOf("-metadata:s:s:0", "title=中文（简体）")))
        assertTrue(args.containsAll(listOf("-metadata:s:s:1", "handler_name=英语（自动生成）")))
        assertTrue(args.containsAll(listOf("-disposition:s:0", "default", "-disposition:s:1", "0")))
        // 嵌入字幕时不再映射输入里的旧字幕轨，否则重试保存会不断叠加轨道。
        assertFalse(args.contains("0:s?"))
        assertTrue(args.containsAll(listOf("-map", "0:V?", "-map", "0:a?")))
    }

    @Test
    fun buildMetadataArguments_matroskaKeepsSubRipAndBibliographicCodes() {
        val args = MediaProcessingEngine.buildMetadataArguments(
            inputFile = File("input.mkv"),
            outputFile = File("output.mkv"),
            outputFormat = "matroska",
            values = emptyMap(),
            subtitles = subtitleTracks,
        )

        assertFalse(args.contains("mov_text"))
        assertTrue(args.containsAll(listOf("-map", "1:0", "-map", "2:0")))
        assertTrue(args.containsAll(listOf("-metadata:s:s:0", "language=chi")))
        assertTrue(args.containsAll(listOf("-metadata:s:s:1", "language=eng")))
        assertFalse(args.contains("handler_name=中文（简体）"))
        assertTrue(args.containsAll(listOf("-map", "0:v?", "-map", "0:a?")))
    }

    @Test
    fun buildMetadataArguments_withoutSubtitlesStillCopiesExistingSubtitleStreams() {
        val args = MediaProcessingEngine.buildMetadataArguments(
            inputFile = File("input.mp4"),
            outputFile = File("output.mp4"),
            outputFormat = "mp4",
            values = mapOf("title" to "标题"),
            cover = EmbeddedCover(File("cover.jpg"), 64, 48),
        )

        assertTrue(args.containsAll(listOf("-map", "0:s?")))
        assertFalse(args.contains("-c:s"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildMetadataArguments_rejectsSubtitleTracksForFlv() {
        MediaProcessingEngine.buildMetadataArguments(
            inputFile = File("input.flv"),
            outputFile = File("output.flv"),
            outputFormat = "flv",
            values = emptyMap(),
            subtitles = subtitleTracks,
        )
    }

    @Test
    fun supportsSubtitleTracks_onlyForMp4AndMatroska() {
        assertTrue(MediaProcessingEngine.supportsSubtitleTracks("mp4"))
        assertTrue(MediaProcessingEngine.supportsSubtitleTracks("matroska"))
        listOf("flv", "mp3", "flac").forEach { assertFalse(it, MediaProcessingEngine.supportsSubtitleTracks(it)) }
    }

    @Test
    fun subtitleLanguageCodes_mapBilibiliTagsToBothIso6392Forms() {
        assertEquals("zho", SubtitleLanguageCodes.iso639Terminology("zh-Hans"))
        assertEquals("zho", SubtitleLanguageCodes.iso639Terminology("ai-zh"))
        assertEquals("chi", SubtitleLanguageCodes.iso639Bibliographic("zh-Hant"))
        assertEquals("eng", SubtitleLanguageCodes.iso639Terminology("en-US"))
        assertEquals("eng", SubtitleLanguageCodes.iso639Bibliographic("ai-en"))
        assertEquals("jpn", SubtitleLanguageCodes.iso639Terminology("ja"))
        assertEquals("fre", SubtitleLanguageCodes.iso639Bibliographic("fr"))
        assertEquals("fra", SubtitleLanguageCodes.iso639Terminology("fr"))
        assertEquals("und", SubtitleLanguageCodes.iso639Terminology(""))
        assertEquals("und", SubtitleLanguageCodes.iso639Bibliographic("xx-unknown"))
    }

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
    fun buildMp4ConversionArguments_copiesCompatibleAudioWithoutBitrateChange() {
        val args = MediaProcessingEngine.buildMp4ConversionArguments(
            inputFile = File("video.mkv"),
            outputFile = File("video.mp4"),
        )

        assertTrue(args.containsAll(listOf("-c:v", "copy")))
        assertTrue(args.containsAll(listOf("-c:a", "copy")))
        assertFalse(args.contains("-b:a"))
        assertTrue(args.containsAll(listOf("-movflags", "+faststart")))
    }

    @Test
    fun buildMp4ConversionArguments_transcodesFlacForPlayerCompatibility() {
        val args = MediaProcessingEngine.buildMp4ConversionArguments(
            File("video.mkv"), File("video.mp4"), transcodeAudioToAac = true,
        )
        assertTrue(args.containsAll(listOf("-c:v", "copy")))
        assertTrue(args.containsAll(listOf("-c:a", "aac", "-b:a", "192k")))
    }

    @Test
    fun needsAacForMp4_preservesDolbyAndOtherCompatibleAudio() {
        listOf(null, "aac", "ac3", "eac3", "mp3", "alac").forEach { codec ->
            assertFalse("$codec should not be transcoded", MediaProcessingEngine.needsAacForMp4(codec))
        }
        assertTrue(MediaProcessingEngine.needsAacForMp4("flac"))
        assertTrue(MediaProcessingEngine.needsAacForMp4("pcm_s16le"))
    }

    @Test
    fun buildAudioRemuxArguments_usesMp4MuxerForDolbyM4aAndCopiesPackets() {
        val args = MediaProcessingEngine.buildAudioRemuxArguments(File("dash.m4s"), File("audio.m4a"))
        assertTrue(args.containsAll(listOf("-f", "mp4", "-c:a", "copy")))
        assertFalse(args.contains("-b:a"))
    }

    @Test
    fun buildAudioRemuxArguments_extractsNativeFlacWithoutMetadataOptions() {
        val args = MediaProcessingEngine.buildAudioRemuxArguments(File("dash.m4s"), File("audio.flac"))
        assertTrue(args.containsAll(listOf("-f", "flac", "-c:a", "copy")))
        assertFalse(args.contains("-metadata"))
        assertFalse(args.contains("-movflags"))
    }
}
