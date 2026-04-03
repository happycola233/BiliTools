package com.happycola233.bilitools.core

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object MediaMergeEngine {
    suspend fun merge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
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
                "-c",
                "copy",
                "-shortest",
            )
            if (outputFile.extension.equals("mp4", ignoreCase = true)) {
                args += listOf("-movflags", "+faststart")
            }
            args += outputFile.absolutePath

            val session = FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { completed ->
                if (!continuation.isActive) return@executeWithArgumentsAsync
                val returnCode = completed.returnCode
                when {
                    ReturnCode.isSuccess(returnCode) -> continuation.resume(Unit)
                    ReturnCode.isCancel(returnCode) ->
                        continuation.cancel(CancellationException("Media merge cancelled"))
                    else -> {
                        val details = returnCode?.toString().orEmpty()
                        continuation.resumeWithException(
                            IllegalStateException(
                                if (details.isBlank()) {
                                    "Media merge failed"
                                } else {
                                    "Media merge failed ($details)"
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
