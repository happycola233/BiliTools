package com.happycola233.bilitools.data.model

/** 附加任务可持久化的执行方式；这里只保存从头重试所需的最小参数。 */
data class DownloadExtraTaskSpec(
    val operation: DownloadExtraTaskOperation,
    val mimeType: String? = null,
    val unavailableMessage: String,
    val textContent: String? = null,
    val sourceUrl: String? = null,
    val aid: Long? = null,
    val cid: Long? = null,
    val bvid: String? = null,
    val durationSeconds: Int? = null,
    val date: String? = null,
    val hour: Int? = null,
    val convertDanmakuToAss: Boolean = false,
    val summaryTitle: String? = null,
    val subtitle: SubtitleInfo? = null,
    val downloadAllSubtitles: Boolean = false,
    val selectedSubtitleLanguage: String? = null,
    val subtitleBaseFileName: String? = null,
    val subtitleTaskTitle: String? = null,
    val cleanFileNameSeparators: Boolean = true,
)

enum class DownloadExtraTaskOperation {
    StaticText,
    FetchBytes,
    SubtitleDiscovery,
    Subtitle,
    AiSummary,
    DanmakuLive,
    DanmakuHistory,
}

/** 字幕地址带时效参数，任务去重必须使用稳定的稿件、分 P 与语言标识。 */
internal data class SubtitleTaskKey(
    val aid: Long,
    val cid: Long,
    val languageCode: String,
)

internal fun DownloadExtraTaskSpec.subtitleTaskKey(): SubtitleTaskKey? {
    if (operation != DownloadExtraTaskOperation.Subtitle) return null
    val sourceAid = aid ?: return null
    val sourceCid = cid ?: return null
    val languageCode = subtitle?.lan ?: return null
    return SubtitleTaskKey(sourceAid, sourceCid, languageCode)
}

internal fun DownloadExtraTaskSpec.subtitleTaskKeyFor(
    subtitle: SubtitleInfo,
): SubtitleTaskKey? {
    val sourceAid = aid ?: return null
    val sourceCid = cid ?: return null
    return SubtitleTaskKey(sourceAid, sourceCid, subtitle.lan)
}
