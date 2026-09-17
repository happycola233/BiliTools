package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.LyricsEmbedding
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding
import java.util.Locale
import kotlin.math.roundToLong

internal data class LyricsSubtitleLine(val from: Double, val to: Double, val content: String)

/** 指定了语言就只取这些语言（保持 B 站列表顺序）；空集合是用户选择「全部字幕」的快捷方式。 */
internal fun selectEmbeddedSubtitleTracks(
    subtitles: List<SubtitleInfo>,
    request: SubtitleTrackEmbedding,
): List<SubtitleInfo> {
    return subtitles.filter {
        it.url.isNotBlank() &&
            (request.languages.isEmpty() || it.lan in request.languages)
    }
}

/**
 * 单个歌词字段只放用户选择的一种语言。不匹配或尚未选择时留空，不能替用户选择其他来源。
 */
internal fun selectLyricsSubtitle(
    subtitles: List<SubtitleInfo>,
    request: LyricsEmbedding,
): SubtitleInfo? {
    val language = request.language ?: return null
    return subtitles.firstOrNull {
        it.url.isNotBlank() && it.lan == language
    }
}

/** 字幕是时间轴转写，不是音乐作品的填词；保留换行和停顿，不拼接不同语言。 */
internal fun subtitlesToLrc(lines: List<LyricsSubtitleLine>): String? {
    val cues = lines.filter {
        it.from.isFinite() && it.to.isFinite() && it.from >= 0 && it.to > it.from &&
            it.content.isNotBlank()
    }.sortedBy { it.from }
    if (cues.isEmpty()) return null
    return buildString {
        cues.forEachIndexed { index, cue ->
            val start = (cue.from * 100).roundToLong()
            val end = (cue.to * 100).roundToLong()
            cue.content.trim().lineSequence().filter(String::isNotBlank).forEach { line ->
                append(lrcTimestamp(start)).append(line.trim()).append('\n')
            }
            val nextStart = cues.getOrNull(index + 1)?.from?.let { (it * 100).roundToLong() }
            if (end > start && (nextStart == null || end < nextStart)) {
                append(lrcTimestamp(end)).append('\n')
            }
        }
    }.trimEnd()
}

private fun lrcTimestamp(centiseconds: Long): String = String.format(
    Locale.ROOT, "[%02d:%02d.%02d]", centiseconds / 6000, centiseconds / 100 % 60, centiseconds % 100,
)

/** CDN 错误页和 JSON 错误响应不能作为歌词保存；原始 LRC 的时间戳和 offset 保持原样。 */
internal fun normalizeOriginalLyrics(raw: String): String? {
    val text = raw.removePrefix("\uFEFF").trim().replace("\r\n", "\n").replace('\r', '\n')
    if (text.isBlank()) return null
    require(!text.startsWith("<") && !text.startsWith("{") && '\u0000' !in text) {
        "The lyrics response is not text lyrics"
    }
    val hasContent = text.lineSequence().any { line ->
        line.replace(Regex("\\[[^]\\r\\n]*]"), "").isNotBlank()
    }
    return text.takeIf { hasContent }
}
