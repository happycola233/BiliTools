package com.happycola233.bilitools.core

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/** 要挂进视频容器的一条软字幕轨：SRT 文件、B 站语言代码与展示给播放器的轨道名称。 */
internal data class EmbeddedSubtitleTrack(
    val file: File,
    val languageTag: String,
    val title: String,
)

object MediaProcessingEngine {
    /** 能承载软字幕轨的输出容器；FLV 没有字幕轨，MP3 / FLAC 是纯音频。 */
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
            // MP4 只认 tx3g（mov_text）；Matroska 直接存放 SubRip 文本。
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
    ): List<String> = listOf(
        "-hide_banner",
        "-nostats",
        "-loglevel",
        "warning",
        "-y",
        "-i",
        inputFile.absolutePath,
        "-map",
        "0:v:0",
        "-map",
        "0:a:0?",
        "-c:v",
        "copy",
        "-c:a",
        "aac",
        "-b:a",
        "192k",
        "-strict",
        "unofficial",
        "-movflags",
        "+faststart",
        "-map_metadata",
        "0",
        "-map_chapters",
        "0",
        outputFile.absolutePath,
    )

    suspend fun merge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        transcodeAudioToAac: Boolean = false,
    ) {
        execute(
            arguments = buildMergeArguments(
                videoFile = videoFile,
                audioFile = audioFile,
                outputFile = outputFile,
                transcodeAudioToAac = transcodeAudioToAac,
            ),
            operationName = "Media merge",
        )
    }

    suspend fun convertAudioToMp3(inputFile: File, outputFile: File) {
        execute(
            arguments = buildMp3ConversionArguments(inputFile, outputFile),
            operationName = "Audio conversion",
        )
    }

    suspend fun convertVideoToMp4(inputFile: File, outputFile: File) {
        execute(
            arguments = buildMp4ConversionArguments(inputFile, outputFile),
            operationName = "Video conversion",
        )
    }

    private suspend fun execute(arguments: List<String>, operationName: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(arguments.toTypedArray()) { completed ->
                if (!continuation.isActive) return@executeWithArgumentsAsync
                val returnCode = completed.returnCode
                when {
                    ReturnCode.isSuccess(returnCode) -> continuation.resume(Unit)
                    ReturnCode.isCancel(returnCode) ->
                        continuation.cancel(CancellationException("$operationName cancelled"))
                    else -> {
                        val details = returnCode?.toString().orEmpty()
                        continuation.resumeWithException(
                            IllegalStateException(
                                if (details.isBlank()) {
                                    "$operationName failed"
                                } else {
                                    "$operationName failed ($details)"
                                },
                            ),
                        )
                    }
                }
            }

            continuation.invokeOnCancellation {
                runCatching { FFmpegKit.cancel(session.sessionId) }
            }
        }
    }
}
