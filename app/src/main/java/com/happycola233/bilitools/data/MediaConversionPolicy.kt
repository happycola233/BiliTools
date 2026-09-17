package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadTaskType

internal enum class MediaConversionTarget(val outputExtension: String) {
    MP3("mp3"),
    MP4("mp4"),
}

internal object MediaConversionPolicy {
    fun targetFor(
        taskType: DownloadTaskType,
        convertAudioToMp3: Boolean,
        convertVideoToMp4: Boolean,
        sourceExtension: String = "",
        embedSubtitles: Boolean = false,
    ): MediaConversionTarget? {
        return when (taskType) {
            DownloadTaskType.Audio -> MediaConversionTarget.MP3.takeIf { convertAudioToMp3 }
            DownloadTaskType.Video,
            DownloadTaskType.AudioVideo,
            -> MediaConversionTarget.MP4.takeIf {
                convertVideoToMp4 || (embedSubtitles && sourceExtension.equals("flv", ignoreCase = true))
            }

            else -> null
        }
    }

    fun outputFileName(fileName: String, target: MediaConversionTarget?): String {
        if (target == null) return fileName
        val nameWithoutExtension = fileName.substringBeforeLast('.', fileName)
        return "$nameWithoutExtension.${target.outputExtension}"
    }

    /** 下载产物的后缀、实际封装与 MediaStore 类型保持一致，避免系统再次追加扩展名。 */
    fun mediaMimeType(extension: String): String? = when (extension) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "flv" -> "video/x-flv"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "aac" -> "audio/aac"
        else -> null
    }
}
