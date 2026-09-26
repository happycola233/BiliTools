package com.happycola233.bilitools.core

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformationSession
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlinx.coroutines.CancellationException

/** 要挂进视频容器的一条软字幕轨：SRT 文件、B 站语言代码与展示给播放器的轨道名称。 */
internal data class EmbeddedSubtitleTrack(
    val file: File,
    val languageTag: String,
    val title: String,
)

object MediaProcessingEngine {
    /** 支持通用播放器切换、关闭多语言软字幕的输出容器。 */
    internal fun supportsSubtitleTracks(outputFormat: String): Boolean =
        outputFormat == "mp4" || outputFormat == "matroska"

    internal fun buildMetadataArguments(
        inputFile: File,
        outputFile: File,
        outputFormat: String,
        values: Map<String, String>,
        cover: EmbeddedCover? = null,
        subtitles: List<EmbeddedSubtitleTrack> = emptyList(),
    ): List<String> = buildList {
        require(subtitles.isEmpty() || supportsSubtitleTracks(outputFormat)) {
            "$outputFormat cannot carry subtitle tracks"
        }
        addAll(listOf("-hide_banner", "-nostats", "-loglevel", "warning", "-y", "-i", inputFile.absolutePath))
        // MP4 的封面是一条 attached_pic 视频轨，需要作为第二个输入；Matroska 的封面走 -attach。
        val coverAsVideoInput = cover != null && outputFormat == "mp4"
        if (coverAsVideoInput) {
            addAll(listOf("-i", cover.file.absolutePath))
        }
        // 字幕文件以独立输入接入；扩展名不可靠，显式指定 SRT 解复用器。
        val firstSubtitleInput = if (coverAsVideoInput) 2 else 1
        subtitles.forEach { track -> addAll(listOf("-f", "srt", "-i", track.file.absolutePath)) }
        // 嵌入字幕时输出里的字幕轨只来自本次选择，原有字幕轨不再映射，轨道序号才与下方的语言标注对应。
        val inputSubtitleMaps = if (subtitles.isEmpty()) listOf("-map", "0:s?") else emptyList()
        if (coverAsVideoInput) {
            // 封面是 attached_pic，不是普通视频轨；大写 V 只保留原始的动态视频。
            addAll(listOf("-map", "1:v:0", "-map", "0:V?", "-map", "0:a?"))
            addAll(inputSubtitleMaps)
            addAll(listOf("-disposition:v:0", "attached_pic"))
        } else if (cover != null && outputFormat == "matroska") {
            // 下载产物的旧封面不再映射，避免保存重试时不断追加相同附件。
            addAll(listOf("-map", "0:V?", "-map", "0:a?"))
            addAll(inputSubtitleMaps)
        } else if (subtitles.isNotEmpty()) {
            addAll(listOf("-map", "0:v?", "-map", "0:a?"))
        } else {
            addAll(listOf("-map", "0"))
        }
        subtitles.indices.forEach { index -> addAll(listOf("-map", "${firstSubtitleInput + index}:0")) }
        addAll(listOf("-c", "copy", "-map_metadata", "0", "-map_chapters", "0"))
        if (subtitles.isNotEmpty()) {
            // MP4 选用播放器广泛支持的 tx3g（mov_text）；Matroska 直接存放 SubRip 文本。
            if (outputFormat == "mp4") addAll(listOf("-c:s", "mov_text"))
            subtitles.forEachIndexed { index, track ->
                val language = if (outputFormat == "mp4") {
                    SubtitleLanguageCodes.iso639Terminology(track.languageTag)
                } else {
                    SubtitleLanguageCodes.iso639Bibliographic(track.languageTag)
                }
                addAll(listOf("-metadata:s:s:$index", "language=$language"))
                addAll(listOf("-metadata:s:s:$index", "title=${track.title}"))
                if (outputFormat == "mp4") addAll(listOf("-metadata:s:s:$index", "handler_name=${track.title}"))
                // 只把第一条标成默认轨，其余显式清零，不同播放器的初始字幕才一致。
                addAll(listOf("-disposition:s:$index", if (index == 0) "default" else "0"))
            }
        }
        values.forEach { (key, value) -> addAll(listOf("-metadata", "$key=$value")) }
        when (outputFormat) {
            "mp4" -> addAll(listOf("-strict", "unofficial", "-movflags", "+faststart"))
            "matroska" -> if (cover != null) {
                // Matroska 封面使用附件，不能添成一条静止视频轨。
                addAll(listOf("-attach", cover.file.absolutePath, "-metadata:s:t", "mimetype=image/jpeg"))
                val fileName = if (cover.width > cover.height) "cover_land.jpg" else "cover.jpg"
                addAll(listOf("-metadata:s:t", "filename=$fileName"))
            }
        }
        addAll(listOf("-f", outputFormat, outputFile.absolutePath))
    }

    internal suspend fun writeMetadata(
        inputFile: File,
        outputFile: File,
        outputFormat: String,
        values: Map<String, String>,
        cover: EmbeddedCover? = null,
        subtitles: List<EmbeddedSubtitleTrack> = emptyList(),
    ) {
        execute(
            buildMetadataArguments(inputFile, outputFile, outputFormat, values, cover, subtitles),
            "Metadata writing",
        )
        if (outputFormat == "mp4") Mp4DolbyConfiguration.preserve(inputFile, outputFile)
    }

    internal fun buildMergeArguments(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        transcodeAudioToAac: Boolean = false,
    ): List<String> {
        val args = mutableListOf(
            "-hide_banner",
            "-nostats",
            "-loglevel",
            "warning",
            "-y",
            "-i",
            videoFile.absolutePath,
            "-i",
            audioFile.absolutePath,
            "-map",
            "0:v:0",
            "-map",
            "1:a:0",
        )
        args += if (transcodeAudioToAac) {
            listOf("-c:v", "copy", "-c:a", "aac", "-b:a", "192k")
        } else {
            listOf("-c", "copy")
        }
        args += "-shortest"
        if (outputFile.extension.equals("mp4", ignoreCase = true)) {
            // Preserve Dolby Vision dvcC/dvvC boxes when remuxing into MP4.
            args += listOf("-strict", "unofficial")
            args += listOf("-movflags", "+faststart")
        }
        args += outputFile.absolutePath
        return args
    }

    internal fun buildMp3ConversionArguments(
        inputFile: File,
        outputFile: File,
    ): List<String> = listOf(
        "-hide_banner",
        "-nostats",
        "-loglevel",
        "warning",
        "-y",
        "-i",
        inputFile.absolutePath,
        "-map",
        "0:a:0",
        "-vn",
        "-c:a",
        "libmp3lame",
        "-q:a",
        "2",
        "-id3v2_version",
        "4",
        "-map_metadata",
        "0",
        outputFile.absolutePath,
    )

    internal fun buildMp4ConversionArguments(
        inputFile: File,
        outputFile: File,
        transcodeAudioToAac: Boolean = false,
    ): List<String> = buildList {
        addAll(listOf("-hide_banner", "-nostats", "-loglevel", "warning", "-y", "-i", inputFile.absolutePath))
        addAll(listOf("-map", "0:v:0", "-map", "0:a:0?", "-c:v", "copy"))
        addAll(if (transcodeAudioToAac) listOf("-c:a", "aac", "-b:a", "192k") else listOf("-c:a", "copy"))
        addAll(listOf("-strict", "unofficial", "-movflags", "+faststart", "-map_metadata", "0", "-map_chapters", "0"))
        add(outputFile.absolutePath)
    }

    /**
     * 统一整理下载音轨，与是否写入标签无关。DASH 的 FLAC 需解封装；AAC / E-AC-3
     * 则输出常规音频 MP4。显式使用 MP4 muxer，避免 .m4a 默认的 iPod muxer 拒绝 E-AC-3。
     */
    internal fun buildAudioRemuxArguments(inputFile: File, outputFile: File): List<String> = buildList {
        addAll(listOf("-hide_banner", "-nostats", "-loglevel", "warning", "-y", "-i", inputFile.absolutePath))
        addAll(listOf("-map", "0:a:0", "-c:a", "copy", "-map_metadata", "0"))
        if (outputFile.extension.equals("flac", ignoreCase = true)) {
            addAll(listOf("-f", "flac"))
        } else {
            addAll(listOf("-strict", "unofficial", "-movflags", "+faststart", "-f", "mp4"))
        }
        add(outputFile.absolutePath)
    }

    /** 常见 MP4 音轨直接复制；FLAC 等保持「转 MP4」原有的 AAC 兼容输出。null 表示没有音轨。 */
    internal fun needsAacForMp4(audioCodec: String?): Boolean =
        audioCodec != null && audioCodec !in setOf("aac", "ac3", "eac3", "mp3", "alac")

    suspend fun merge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
    ) {
        val transcodeAudioToAac = outputFile.extension.equals("mp4", ignoreCase = true) &&
            needsAacForMp4(firstAudioCodec(audioFile))
        execute(
            arguments = buildMergeArguments(
                videoFile = videoFile,
                audioFile = audioFile,
                outputFile = outputFile,
                transcodeAudioToAac = transcodeAudioToAac,
            ),
            operationName = "Media merge",
        )
        if (outputFile.extension.equals("mp4", ignoreCase = true) && !transcodeAudioToAac) {
            Mp4DolbyConfiguration.preserve(audioFile, outputFile)
        }
    }

    suspend fun convertAudioToMp3(inputFile: File, outputFile: File) {
        execute(
            arguments = buildMp3ConversionArguments(inputFile, outputFile),
            operationName = "Audio conversion",
        )
    }

    suspend fun convertVideoToMp4(inputFile: File, outputFile: File) {
        execute(
            arguments = buildMp4ConversionArguments(
                inputFile, outputFile, transcodeAudioToAac = needsAacForMp4(firstAudioCodec(inputFile)),
            ),
            operationName = "Video conversion",
        )
        Mp4DolbyConfiguration.preserve(inputFile, outputFile)
    }

    suspend fun remuxAudio(inputFile: File, outputFile: File) {
        execute(buildAudioRemuxArguments(inputFile, outputFile), "Audio preparation")
        if (!outputFile.extension.equals("flac", ignoreCase = true)) {
            Mp4DolbyConfiguration.preserve(inputFile, outputFile)
        }
    }

    /** 输入来自下载服务器，实际编码在这里探测，不能由文件后缀或音质名称猜测。 */
    private suspend fun firstAudioCodec(inputFile: File): String? {
        val completed = awaitNativeOperation<MediaInformationSession> { finish ->
            val session = FFprobeKit.getMediaInformationAsync(inputFile.absolutePath) { finish(it) }
            val cancel: () -> Unit = { FFmpegKit.cancel(session.sessionId) }
            cancel
        }
        if (ReturnCode.isCancel(completed.returnCode)) throw CancellationException("Media inspection cancelled")
        check(ReturnCode.isSuccess(completed.returnCode) && completed.mediaInformation != null) {
            "Media inspection failed"
        }
        return completed.mediaInformation.streams.firstOrNull { it.type == "audio" }?.codec
    }

    private suspend fun execute(arguments: List<String>, operationName: String) {
        val completed = awaitNativeOperation<FFmpegSession> { finish ->
            val session = FFmpegKit.executeWithArgumentsAsync(arguments.toTypedArray()) { finish(it) }
            val cancel: () -> Unit = { FFmpegKit.cancel(session.sessionId) }
            cancel
        }
        val returnCode = completed.returnCode
        if (ReturnCode.isCancel(returnCode)) throw CancellationException("$operationName cancelled")
        check(ReturnCode.isSuccess(returnCode)) {
            val details = returnCode?.toString().orEmpty()
            if (details.isBlank()) "$operationName failed" else "$operationName failed ($details)"
        }
    }
}
