package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.SubtitleInfo
import java.util.Locale
import kotlin.math.roundToLong

internal data class LyricsSubtitleLine(val from: Double, val to: Double, val content: String)

internal fun selectLyricsSubtitle(
    subtitles: List<SubtitleInfo>,
    mode: SubtitleLyricsMode,
    preferredLanguage: String?,
): SubtitleInfo? {
    if (mode == SubtitleLyricsMode.Off) return null
    val eligible = subtitles.filter { subtitle ->
        subtitle.url.isNotBlank() &&
            (mode == SubtitleLyricsMode.PreferManual || !subtitle.isGenerated) &&
            (preferredLanguage == null || subtitle.lan == preferredLanguage)
    }
    // 单个歌词字段只放一种语言。显式语言不匹配时留空，不悄悄换成另一种语言。
    return eligible.minWithOrNull(
        compareBy<SubtitleInfo> { it.isGenerated }
            .thenBy {
                when (it.lan.removePrefix("ai-").lowercase(Locale.ROOT)) {
                    "zh", "zh-cn", "zh-hans" -> 0
                    "zh-tw", "zh-hant", "zh-hk" -> 1
                    else -> 2
                }
            },
    )
}

internal val SubtitleInfo.isGenerated: Boolean
    get() = isAi || lan.startsWith("ai-", ignoreCase = true)

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
