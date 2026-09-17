package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.StreamFormat

/** 解析页「下载与导出」卡片里可以跨次解析保留的选项分组，与卡片上的分区一一对应。 */
enum class DownloadPreferenceGroup(val value: String) {
    OutputType("output_type"),
    StreamFormat("stream_format"),
    VideoQuality("video_quality"),
    AudioQuality("audio_quality"),
    Embedding("embedding"),
    MiscExports("misc_exports"),
    Nfo("nfo"),
    Danmaku("danmaku"),
    Images("images"),
    Opus("opus"),
    ;

    companion object {
        fun fromValue(value: String?): DownloadPreferenceGroup? = entries.firstOrNull { it.value == value }
    }
}

data class DownloadPreferenceMemorySettings(
    val enabled: Boolean = true,
    val groups: Set<DownloadPreferenceGroup> = DEFAULT_GROUPS,
) {
    fun remembers(group: DownloadPreferenceGroup): Boolean = enabled && group in groups

    companion object {
        /**
         * 输出类型、流格式与画质音质各有自己的默认设置，默认每次解析回到默认值；
         * 内嵌与附加导出项没有别的默认来源，默认跨次保留。
         */
        val DEFAULT_GROUPS: Set<DownloadPreferenceGroup> = setOf(
            DownloadPreferenceGroup.Embedding,
            DownloadPreferenceGroup.MiscExports,
            DownloadPreferenceGroup.Nfo,
            DownloadPreferenceGroup.Danmaku,
            DownloadPreferenceGroup.Images,
            DownloadPreferenceGroup.Opus,
        )
    }
}

/**
 * 解析页上次使用的下载选项。字幕语言随条目变化，不跨次保留。
 */
data class RememberedDownloadPreferences(
    val outputType: OutputType? = OutputType.AudioVideo,
    val streamFormat: StreamFormat = StreamFormat.Dash,
    val quality: RememberedDownloadQuality = RememberedDownloadQuality(),
    val embedSubtitles: Boolean = false,
    val embedLyrics: Boolean = false,
    val subtitleExport: Boolean = false,
    val aiSummaryExport: Boolean = false,
    val nfoCollection: Boolean = false,
    val nfoSingle: Boolean = false,
    val danmakuLive: Boolean = false,
    val danmakuHistory: Boolean = false,
    val imageIds: Set<String> = emptySet(),
    val opusContent: Boolean = true,
    val opusImages: Boolean = true,
)

/**
 * 只保存用户明确改过的质量字段。未改过的字段沿用默认设置，资源暂不支持某画质或编码时
 * 产生的界面降级不会污染偏好，也不会改写用户在设置页指定的默认质量。
 */
data class RememberedDownloadQuality(
    val resolutionMode: DownloadQualityMode? = null,
    val fixedResolutionId: Int? = null,
    val codec: DefaultDownloadVideoCodec? = null,
    val audioBitrateMode: DownloadQualityMode? = null,
    val fixedAudioBitrateId: Int? = null,
) {
    fun resolve(
        defaults: DefaultDownloadQualitySettings,
        memory: DownloadPreferenceMemorySettings,
    ): DefaultDownloadQualitySettings {
        val rememberVideo = memory.remembers(DownloadPreferenceGroup.VideoQuality)
        val rememberAudio = memory.remembers(DownloadPreferenceGroup.AudioQuality)
        return defaults.copy(
            resolutionMode = resolutionMode.takeIf { rememberVideo } ?: defaults.resolutionMode,
            fixedResolutionId = fixedResolutionId.takeIf { rememberVideo } ?: defaults.fixedResolutionId,
            codec = codec.takeIf { rememberVideo } ?: defaults.codec,
            audioBitrateMode = audioBitrateMode.takeIf { rememberAudio } ?: defaults.audioBitrateMode,
            fixedAudioBitrateId = fixedAudioBitrateId.takeIf { rememberAudio } ?: defaults.fixedAudioBitrateId,
        )
    }
}
