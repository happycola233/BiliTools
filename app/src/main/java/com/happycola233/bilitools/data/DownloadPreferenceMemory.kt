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
 * 解析页上次使用的下载选项。画质与音质直接写回「默认下载质量」，不在这里重复保存；
 * 字幕语言随条目变化，也不保留。
 */
data class RememberedDownloadPreferences(
    val outputType: OutputType? = OutputType.AudioVideo,
    val streamFormat: StreamFormat = StreamFormat.Dash,
    val embedSubtitles: Boolean = false,
    val embedLyrics: Boolean = false,
    val embedIncludeGeneratedSubtitles: Boolean = true,
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
