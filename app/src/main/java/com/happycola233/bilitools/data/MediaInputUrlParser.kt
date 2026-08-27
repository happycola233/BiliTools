package com.happycola233.bilitools.data

import androidx.core.util.PatternsCompat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object MediaInputUrlParser {
    /**
     * 分享文案可能与链接直接相连，因此不能依赖空白分词。
     * 先遍历文本中的 URL，并选取首个受支持的 B 站地址；若没有匹配项，则保留把整段输入
     * 当作 URL 解析的旧行为，让调用方统一处理非法域名与手动指定类型的原始输入。
     */
    fun parse(raw: String): HttpUrl? {
        val matcher = PatternsCompat.WEB_URL.matcher(raw)
        while (matcher.find()) {
            parseCandidate(matcher.group())
                ?.takeIf(HttpUrl::isBiliMediaHost)
                ?.let { return it }
        }
        BILI_URL_WITHOUT_SCHEME_REGEX.find(raw)
            ?.value
            ?.let(::parseCandidate)
            ?.takeIf(HttpUrl::isBiliMediaHost)
            ?.let { return it }
        return raw.toHttpUrlOrNull()
    }

    private fun parseCandidate(candidate: String): HttpUrl? {
        // PatternsCompat 支持 IRI，会把紧随链接的中文文案也纳入路径；B 站分享链接本身使用 ASCII。
        val urlCandidate = ASCII_URL_PREFIX_REGEX.find(candidate)?.value ?: return null
        val normalized = if (urlCandidate.startsWith("http://", ignoreCase = true) ||
            urlCandidate.startsWith("https://", ignoreCase = true)
        ) {
            urlCandidate
        } else {
            "https://$urlCandidate"
        }
        return normalized.toHttpUrlOrNull()
    }

    /**
     * PatternsCompat 会把紧贴域名前的部分中文视作 IRI 域名标签，无法单独得到 B 站地址。
     * 此处仅补充无协议链接；前置边界可防止从其他域名或路径内部截出伪造的 B 站域名。
     */
    private val BILI_URL_WITHOUT_SCHEME_REGEX = Regex(
        "(?<![A-Za-z0-9._:/-])" +
            "(?:(?:[A-Za-z0-9-]+\\.)*bilibili\\.com|b23\\.tv)" +
            "/[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
        RegexOption.IGNORE_CASE,
    )
    private val ASCII_URL_PREFIX_REGEX =
        Regex("^[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
}

internal fun HttpUrl.isBiliMediaHost(): Boolean =
    host == "bilibili.com" || host.endsWith(".bilibili.com") || host == "b23.tv"
