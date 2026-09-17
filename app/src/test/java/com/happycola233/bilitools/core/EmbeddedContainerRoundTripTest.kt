package com.happycola233.bilitools.core

import java.io.File
import java.util.concurrent.TimeUnit
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 可选主机集成测试：设置 BILITOOLS_TEST_FFMPEG 为 FFmpeg 所在目录，不使用 adb 或设备。 */
class EmbeddedContainerRoundTripTest {
    @Test fun fragmentedAudioBecomesPlayableM4aWithLyricsAndCovr() = verifyContainer("m4a", false)
    @Test fun videoMp4RetainsVideoAndAudioAndGetsCover() = verifyContainer("mp4", true)
    @Test fun dashVideoRetainsItsStreamAndGetsTags() = verifyContainer("m4s", true)
    @Test fun mkvCoverIsAnAttachmentAndAudioStaysLossless() = verifyContainer("mkv", true)
    @Test fun flvGetsTextTagsWithoutAnUnsupportedCoverStream() = verifyContainer("flv", true)
    @Test fun mp4SubtitleTracksBecomeSwitchableMovTextWithLanguages() = verifySubtitleTracks("mp4")
    @Test fun mkvSubtitleTracksStaySubRipWithBibliographicLanguages() = verifySubtitleTracks("mkv")

    /** 软字幕轨：两种语言各成一轨，播放器能按语言切换；第一条为默认轨，重试保存不会叠加轨道。 */
    private fun verifySubtitleTracks(extension: String) {
        val tools = System.getenv("BILITOOLS_TEST_FFMPEG")
        assumeTrue("Set BILITOOLS_TEST_FFMPEG to run container round trips", !tools.isNullOrBlank())
        val ffmpeg = File(tools!!, "ffmpeg.exe").absolutePath
        val ffprobe = File(tools, "ffprobe.exe").absolutePath
        val directory = File("../.tmp/metadata/container-tests/subtitles-$extension").apply { mkdirs() }
        val input = File(directory, "input.$extension")
        val output = File(directory, "output.$extension")
        val retry = File(directory, "retry.$extension")
        val cover = File(directory, "cover.jpg")
        javaClass.getResourceAsStream("/metadata/cover.jpg")!!.use { source ->
            cover.outputStream().use { source.copyTo(it) }
        }
        run(listOf(
            ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "color=c=red:s=128x96:r=10:d=2",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", input.absolutePath,
        ))
        val chinese = File(directory, "zh.srt").apply {
            writeText("1\n00:00:00,000 --> 00:00:01,000\n第一句，含逗号\n\n2\n00:00:01,000 --> 00:00:02,000\n第二句\n", Charsets.UTF_8)
        }
        val english = File(directory, "en.srt").apply {
            writeText("1\n00:00:00,000 --> 00:00:02,000\nFirst line\n", Charsets.UTF_8)
        }
        val tracks = listOf(
            EmbeddedSubtitleTrack(chinese, "zh-Hans", "中文（简体）"),
            EmbeddedSubtitleTrack(english, "ai-en", "英语（自动生成）"),
        )
        val format = if (extension == "mkv") "matroska" else "mp4"
        val values = mapOf("title" to "字幕测试", "comment" to "来源：测试")
        run(listOf(ffmpeg) + MediaProcessingEngine.buildMetadataArguments(
            input, output, format, values, EmbeddedCover(cover, 64, 48), tracks,
        ))

        fun subtitleStreams(file: File): List<String> =
            run(listOf(ffprobe, "-v", "error", "-show_streams", "-of", "default", file.absolutePath))
                .split("[STREAM]")
                .filter { it.contains("codec_type=subtitle") }
        val streams = subtitleStreams(output)
        assertEquals(streams.toString(), 2, streams.size)
        val (first, second) = streams
        if (extension == "mkv") {
            assertTrue(first, first.contains("codec_name=subrip"))
            assertTrue(first, first.contains("TAG:language=chi"))
            assertTrue(second, second.contains("TAG:language=eng"))
            assertTrue(first, first.contains("TAG:title=中文（简体）"))
            assertTrue(second, second.contains("TAG:title=英语（自动生成）"))
        } else {
            // MP4 没有独立的轨道标题字段，播放器展示的是 hdlr 里的处理器名称。
            assertTrue(first, first.contains("codec_name=mov_text"))
            assertTrue(first, first.contains("TAG:language=zho"))
            assertTrue(second, second.contains("TAG:language=eng"))
            assertTrue(first, first.contains("TAG:handler_name=中文（简体）"))
            assertTrue(second, second.contains("TAG:handler_name=英语（自动生成）"))
        }
        assertTrue(first, first.contains("DISPOSITION:default=1"))
        assertTrue(second, second.contains("DISPOSITION:default=0"))
        // 文本原样进入轨道，播放器读回的是完整的 UTF-8 字幕。
        val readBack = run(listOf(ffmpeg, "-v", "error", "-i", output.absolutePath, "-map", "0:s:0", "-f", "srt", "-"))
        assertTrue(readBack, readBack.contains("第一句，含逗号") && readBack.contains("第二句"))
        val tags = run(listOf(ffprobe, "-v", "error", "-show_entries", "format_tags", "-of", "default", output.absolutePath))
        assertTrue(tags, tags.contains("字幕测试"))
        // 音视频只改封装，不重编码。
        for (stream in listOf("a", "V")) {
            fun hashes(file: File) = run(listOf(
                ffprobe, "-v", "error", "-select_streams", stream,
                "-show_packets", "-show_entries", "packet=data_hash", "-show_data_hash", "sha256",
                "-of", "csv=p=0", file.absolutePath,
            )).lineSequence().filter { it.startsWith("SHA256:") }.toList()
            assertEquals("$extension $stream packets changed", hashes(input), hashes(output))
        }
        // 对已经带字幕轨的产物再写一次：轨道只来自本次选择，不会翻倍。
        run(listOf(ffmpeg) + MediaProcessingEngine.buildMetadataArguments(
            output, retry, format, values, EmbeddedCover(cover, 64, 48), tracks,
        ))
        assertEquals(2, subtitleStreams(retry).size)
        // 不嵌入字幕的普通元数据改写会保留原有字幕轨。
        val keep = File(directory, "keep.$extension")
        run(listOf(ffmpeg) + MediaProcessingEngine.buildMetadataArguments(output, keep, format, values, null))
        assertEquals(2, subtitleStreams(keep).size)
    }

    @Test fun flacInsideMp4IsUnwrappedWithoutChangingSamples() {
        val tools = System.getenv("BILITOOLS_TEST_FFMPEG")
        assumeTrue("Set BILITOOLS_TEST_FFMPEG to run container round trips", !tools.isNullOrBlank())
        val ffmpeg = File(tools!!, "ffmpeg.exe").absolutePath
        val directory = File("../.tmp/metadata/container-tests/flac").apply { mkdirs() }
        val source = File(directory, "dash.flac")
        val output = File(directory, "tagged.flac")
        run(listOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
            "sine=frequency=440:duration=1", "-c:a", "flac", "-strict", "unofficial", "-f", "mp4", source.absolutePath))
        run(listOf(ffmpeg) + MediaProcessingEngine.buildMetadataArguments(source, output, "flac", emptyMap()))
        EmbeddedAudioTagWriter.write(output, mapOf("title" to "无损音频", "lyrics" to "[00:00.00]歌词"), null)
        assertTrue(output.inputStream().use { it.readNBytes(4).contentEquals("fLaC".toByteArray()) })
        val tag = AudioFileIO.read(output).tag
        assertEquals("无损音频", tag.getFirst(FieldKey.TITLE))
        assertEquals("[00:00.00]歌词", tag.getFirst(FieldKey.LYRICS))
        fun samples(file: File) = run(listOf(ffmpeg, "-v", "error", "-i", file.absolutePath,
            "-map", "0:a:0", "-f", "hash", "-hash", "sha256", "-"))
        assertEquals(samples(source), samples(output))
    }

    private fun verifyContainer(extension: String, hasVideo: Boolean) {
        val tools = System.getenv("BILITOOLS_TEST_FFMPEG")
        assumeTrue("Set BILITOOLS_TEST_FFMPEG to run container round trips", !tools.isNullOrBlank())
        val ffmpeg = File(tools!!, "ffmpeg.exe").absolutePath
        val ffprobe = File(tools, "ffprobe.exe").absolutePath
        val directory = File("../.tmp/metadata/container-tests/$extension").apply { mkdirs() }
        val input = File(directory, "input.${if (extension == "m4s") "mp4" else extension}")
        val output = File(directory, "output.${if (extension == "m4s") "mp4" else extension}")
        val cover = File(directory, "cover.jpg")
        javaClass.getResourceAsStream("/metadata/cover.jpg")!!.use { source ->
            cover.outputStream().use { source.copyTo(it) }
        }
        val generate = mutableListOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-y")
        if (hasVideo) generate += listOf("-f", "lavfi", "-i", "color=c=red:s=128x96:r=10:d=1")
        generate += listOf("-f", "lavfi", "-i", "sine=frequency=440:duration=1")
        if (hasVideo) generate += listOf("-c:v", "libx264", "-pix_fmt", "yuv420p")
        generate += listOf("-c:a", if (extension == "mkv") "flac" else "aac")
        if (extension == "m4a" || extension == "m4s") generate += listOf("-movflags", "+frag_keyframe+empty_moov")
        generate += input.absolutePath
        run(generate)
        val format = when (extension) { "mkv" -> "matroska"; "flv" -> "flv"; else -> "mp4" }
        val lyrics = "[00:00.00]示例歌词\n[00:00.50]下一句"
        val values = mapOf("title" to "标题=测试", "artist" to "作者", "album" to "作品", "track" to "2/5", "comment" to "来源：测试", "lyrics" to lyrics)
        run(listOf(ffmpeg) + MediaProcessingEngine.buildMetadataArguments(
            input, output, format, values, EmbeddedCover(cover, 64, 48).takeUnless { extension == "flv" },
        ))
        val tags = run(listOf(ffprobe, "-v", "error", "-show_entries", "format_tags", "-of", "default", output.absolutePath))
        assertTrue(tags, tags.contains("标题=测试"))
        assertTrue(tags, tags.contains("作者"))
        if (extension == "m4a") {
            assertEquals(lyrics, AudioFileIO.read(output).tag.getFirst(FieldKey.LYRICS))
        }
        val streams = run(listOf(ffprobe, "-v", "error", "-show_streams", "-of", "default", output.absolutePath))
        if (extension == "mkv") {
            assertTrue(streams, streams.contains("filename=cover_land.jpg"))
            assertTrue(streams, streams.contains("codec_name=flac"))
            val retry = File(directory, "retry.mkv")
            run(listOf(ffmpeg) + MediaProcessingEngine.buildMetadataArguments(
                output, retry, format, values, EmbeddedCover(cover, 64, 48),
            ))
            val retryStreams = run(listOf(ffprobe, "-v", "error", "-show_streams", retry.absolutePath))
            assertEquals(1, Regex("filename=cover_land.jpg").findAll(retryStreams).count())
        } else if (extension != "flv") {
            assertTrue(streams, streams.contains("attached_pic=1"))
        }
        // 比较所有音频包和动态视频包的哈希，验证只改封装和标签，没有重编码或丢帧。
        for (stream in listOfNotNull("a", "V".takeIf { hasVideo })) {
            fun hashes(file: File) = run(listOf(
                ffprobe, "-v", "error", "-select_streams", stream,
                "-show_packets", "-show_entries", "packet=data_hash", "-show_data_hash", "sha256",
                "-of", "csv=p=0", file.absolutePath,
            )).lineSequence().filter { it.startsWith("SHA256:") }.toList()
            val before = hashes(input)
            assertTrue(before.isNotEmpty())
            assertEquals("$extension $stream packets changed", before, hashes(output))
        }
    }

    private fun run(command: List<String>): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        assertTrue("Media command timed out", process.waitFor(30, TimeUnit.SECONDS))
        assertEquals(text, 0, process.exitValue())
        return text
    }
}
