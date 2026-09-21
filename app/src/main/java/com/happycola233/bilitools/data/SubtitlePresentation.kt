package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.SubtitleInfo
import java.util.Locale

internal val SubtitleInfo.isGenerated: Boolean
    get() = isAi || lan.startsWith("ai-", ignoreCase = true)

/** 语言名称与来源分开展示，保留简繁体等语言区别。 */
internal val SubtitleInfo.languageName: String
    get() {
        val displayLocale = Locale.getDefault()
        // B 站名称通常为中文；其它界面语言根据标准语言代码显示本地化名称。
        if (displayLocale.language != "zh" || displayLocale.script == "Hant" ||
            displayLocale.country in setOf("TW", "HK", "MO")
        ) {
            val tag = if (lan.startsWith("ai-", ignoreCase = true)) lan.drop(3) else lan
            val locale = Locale.forLanguageTag(tag)
            if (locale.language in Locale.getISOLanguages()) return locale.getDisplayName(displayLocale)
        }
        if (!isGenerated) return name.ifBlank { subtitleLanguageDisplayName(lan) }
        return name.replace(generatedLabel, "").trim().trimEnd('·').trim()
            .ifBlank { subtitleLanguageName(lan) }
    }

/** 摘要、下载详情与播放器轨道标题保留完整来源；未标记 AI 的字幕不推断为人工制作。 */
internal val SubtitleInfo.displayName: String
    get() = languageName + if (isGenerated) " · AI" else ""

/** 字幕下载时已被移除，也能把请求里的语言代码展示成可读名称。 */
internal fun subtitleLanguageDisplayName(language: String): String =
    subtitleLanguageName(language) + if (language.startsWith("ai-", ignoreCase = true)) " · AI" else ""

private fun subtitleLanguageName(language: String): String {
    val tag = if (language.startsWith("ai-", ignoreCase = true)) language.drop(3) else language
    return Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault()).ifBlank { tag }
}

// 只去掉明确的 AI 来源后缀，保留「简体」「繁体」等真正的语言区别。
private val generatedLabel = Regex(
    "[（(]\\s*(?:自动生成|AI\\s*(?:字幕|生成|翻译)?|auto[- ]generated)\\s*[）)]|[·・]\\s*AI(?:\\s*字幕)?\\s*$|AI\\s*字幕\\s*$",
    RegexOption.IGNORE_CASE,
)
