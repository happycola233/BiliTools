package com.happycola233.bilitools.core

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

object MediaProcessingEngine {
    internal fun buildMetadataArguments(
        inputFile: File,
        outputFile: File,
        outputFormat: String,
        values: Map<String, String>,
        cover: EmbeddedCover? = null,
    ): List<String> = buildList {
        addAll(listOf("-hide_banner", "-nostats", "-loglevel", "warning", "-y", "-i", inputFile.absolutePath))
        if (cover != null && outputFormat == "mp4") {
            addAll(listOf("-i", cover.file.absolutePath))
            // 封面是 attached_pic，不是普通视频轨；大写 V 只保留原始的动态视频。
            addAll(listOf("-map", "1:v:0", "-map", "0:V?", "-map", "0:a?", "-map", "0:s?"))
            addAll(listOf("-disposition:v:0", "attached_pic"))
        } else if (cover != null && outputFormat == "matroska") {
            // 下载产物的旧封面不再映射，避免保存重试时不断追加相同附件。
            addAll(listOf("-map", "0:V?", "-map", "0:a?", "-map", "0:s?"))
        } else {
            addAll(listOf("-map", "0"))
        }
        addAll(listOf("-c", "copy", "-map_metadata", "0", "-map_chapters", "0"))
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
    ) {
        execute(buildMetadataArguments(inputFile, outputFile, outputFormat, values, cover), "Metadata writing")
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
