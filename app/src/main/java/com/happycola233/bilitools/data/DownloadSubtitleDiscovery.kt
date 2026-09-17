package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadExtraTaskOperation
import com.happycola233.bilitools.data.model.DownloadExtraTaskSpec
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding

internal data class SubtitleDiscoveryTask(
    val subtitle: SubtitleInfo,
    /** 每个实际文件只负责一个语言，重试刷新地址时不能重新解释原来的多语言选择。 */
    val retrySpec: DownloadExtraTaskSpec,
) {
    val available: Boolean get() = subtitle.url.isNotBlank()
}

/** 把用户的字幕请求分成独立文件；缺失来源同样保留为任务，避免部分成功掩盖未完成项。 */
internal fun planSubtitleDiscovery(
    subtitles: List<SubtitleInfo>,
    spec: DownloadExtraTaskSpec,
): List<SubtitleDiscoveryTask> {
    val selected = selectEmbeddedSubtitleTracks(subtitles, spec.subtitleSelection).distinctBy { it.lan }
    val selectedLanguages = selected.map { it.lan }.toSet()
    val missing = spec.subtitleSelection.languages.distinct().filter { language ->
        language !in selectedLanguages
    }.map { language ->
        subtitles.firstOrNull { it.lan == language }?.copy(url = "")
            ?: SubtitleInfo(language, subtitleLanguageDisplayName(language), "")
    }
    return (selected + missing).map { subtitle ->
        SubtitleDiscoveryTask(
            subtitle = subtitle,
            retrySpec = spec.copy(
                operation = DownloadExtraTaskOperation.SubtitleDiscovery,
                subtitleSelection = SubtitleTrackEmbedding(listOf(subtitle.lan)),
            ),
        )
    }
}
