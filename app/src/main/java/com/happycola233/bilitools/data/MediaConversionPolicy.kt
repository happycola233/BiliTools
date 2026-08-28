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
    ): MediaConversionTarget? {
        return when (taskType) {
            DownloadTaskType.Audio -> MediaConversionTarget.MP3.takeIf { convertAudioToMp3 }
            DownloadTaskType.Video,
            DownloadTaskType.AudioVideo,
            -> MediaConversionTarget.MP4.takeIf { convertVideoToMp4 }

            else -> null
        }
    }

    fun outputFileName(fileName: String, target: MediaConversionTarget?): String {
        if (target == null) return fileName
        val nameWithoutExtension = fileName.substringBeforeLast('.', fileName)
        return "$nameWithoutExtension.${target.outputExtension}"
    }
}
