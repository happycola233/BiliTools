package com.happycola233.bilitools.core

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

object MediaProcessingEngine {
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
