package com.happycola233.bilitools.core

import java.util.Locale
import java.util.MissingResourceException

/**
 * 把 B 站字幕的语言代码（BCP 47 风格，如 `zh-Hans`、`en-US`，AI 字幕带 `ai-` 前缀）换成容器要求的
 * ISO 639-2 三字母码。简繁等脚本差异三字母码表达不了，交给轨道标题（B 站给的语言名称）保留。
 */
internal object SubtitleLanguageCodes {
    private const val UNDETERMINED = "und"

    /** ISO 639-2/B 与 /T 不同的语言；MP4 与 Matroska 各认一套。 */
    private val BIBLIOGRAPHIC_BY_TERMINOLOGY = mapOf(
        "zho" to "chi",
        "fra" to "fre",
        "deu" to "ger",
        "nld" to "dut",
        "msa" to "may",
        "ron" to "rum",
        "ell" to "gre",
        "ces" to "cze",
        "fas" to "per",
        "isl" to "ice",
        "mkd" to "mac",
        "sqi" to "alb",
        "hye" to "arm",
        "mya" to "bur",
        "kat" to "geo",
        "slk" to "slo",
        "bod" to "tib",
        "cym" to "wel",
        "eus" to "baq",
    )

    /** MP4 的 mdhd 语言字段使用 ISO 639-2/T（中文为 `zho`）。 */
    fun iso639Terminology(lan: String): String {
        val primary = lan.trim().lowercase(Locale.ROOT).removePrefix("ai-").split('-', '_').first()
        if (primary.isBlank()) return UNDETERMINED
        return try {
            Locale.forLanguageTag(primary).isO3Language.takeIf { it.isNotBlank() } ?: UNDETERMINED
        } catch (_: MissingResourceException) {
            UNDETERMINED
        }
    }

    /** Matroska 的 Language 元素使用 ISO 639-2/B（中文为 `chi`）。 */
    fun iso639Bibliographic(lan: String): String {
        val terminology = iso639Terminology(lan)
        return BIBLIOGRAPHIC_BY_TERMINOLOGY[terminology] ?: terminology
    }
}
