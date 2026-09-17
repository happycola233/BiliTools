package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.SubtitleInfo
import java.util.Locale

internal val SubtitleInfo.isGenerated: Boolean
    get() = isAi || lan.startsWith("ai-", ignoreCase = true)

/** 语言名称与来源分开展示，保留简繁体等语言区别。 */
internal val SubtitleInfo.languageName: String
    get() {
        if (!isGenerated) return name.ifBlank { subtitleLanguageDisplayName(lan) }
        return name.replace(generatedLabel, "").trim().trimEnd('·').trim()
            .ifBlank { subtitleLanguageName(lan) }
    }

/** 摘要、下载详情与播放器轨道标题保留完整来源；未标记 AI 的字幕不推断为人工制作。 */
internal val SubtitleInfo.displayName: String
    get() = languageName + if (isGenerated) " · AI 字幕" else ""

/** 字幕下载时已被移除，也能把请求里的语言代码展示成可读名称。 */
internal fun subtitleLanguageDisplayName(language: String): String =
    subtitleLanguageName(language) + if (language.startsWith("ai-", ignoreCase = true)) " · AI 字幕" else ""

private fun subtitleLanguageName(language: String): String {
    val tag = if (language.startsWith("ai-", ignoreCase = true)) language.drop(3) else language
    return when (tag.lowercase(Locale.ROOT)) {
        "zh", "zh-cn" -> "中文"
        "zh-hans" -> "中文（简体）"
        "zh-tw", "zh-hant" -> "中文（繁体）"
        "zh-hk" -> "中文（香港）"
        else -> Locale.forLanguageTag(tag).getDisplayName(Locale.SIMPLIFIED_CHINESE).ifBlank { tag }
    }
}

// 只去掉明确的 AI 来源后缀，保留「简体」「繁体」等真正的语言区别。
private val generatedLabel = Regex(
    "[（(]\\s*(?:自动生成|AI\\s*(?:字幕|生成|翻译)?|auto[- ]generated)\\s*[）)]|(?:[·・]\\s*)?AI\\s*字幕\\s*$",
    RegexOption.IGNORE_CASE,
)
