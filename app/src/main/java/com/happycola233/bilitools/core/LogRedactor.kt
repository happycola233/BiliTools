package com.happycola233.bilitools.core

import java.net.URI
import java.net.URLDecoder

/** 所有落盘内容的最后一道边界；不推测手机号，避免误伤 aid / cid。 */
class LogRedactor(private val selfUid: () -> String? = { null }) {
    fun describeUrl(value: String): String {
        val uri = try { URI(value.replace("\\u0026", "&")) } catch (_: Exception) { return "<invalid-url>" }
        val host = uri.host?.lowercase() ?: return "<invalid-url>"
        val path = uri.rawPath.orEmpty()
        val cdn = CDN_DOMAINS.any { host == it || host.endsWith(".$it") }
        val description = when {
            cdn -> "$host/${path.substringAfterLast('/')}"
            host == "bilibili.com" || host.endsWith(".bilibili.com") -> buildString {
                append(host)
                append(path)
                uri.rawQuery?.let { query ->
                    append('?')
                    append(query.split('&').joinToString("&") { parameter ->
                        val name = parameter.substringBefore('=')
                        val decodedName = decode(name)
                        val raw = parameter.substringAfter('=', "")
                        // 登录接口没有需要保留的参数值，cid 在这里是国家区号。
                        val safeValue = host != "passport.bilibili.com" && decodedName in QUERY_ALLOWLIST &&
                            SAFE_ID.matches(decode(raw))
                        "$name=${if (safeValue) raw else "…"}"
                    })
                }
            }
            else -> "$host$path"
        }
        return hideIdentity(description)
    }

    fun redact(text: String): String {
        var result = URL.replace(text) { describeUrl(it.value) }
        result = HEADER.replace(result) { "${it.groupValues[1]}: …" }
        result = SECRET.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}…" }
        return hideIdentity(result)
    }

    private fun hideIdentity(text: String): String {
        val uid = selfUid()?.takeIf { it.isNotBlank() }
        val hidden = if (uid == null) text else text.replace(uid, "<self-uid>")
        return IPV6.replace(IPV4.replace(hidden, "<ip>")) { if (it.value.count { c -> c == ':' } >= 4 || "::" in it.value) "<ip>" else it.value }
    }

    private fun decode(text: String): String = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (_: IllegalArgumentException) { "" }

    companion object {
        // 对应 video/playurl、bangumi、收藏夹、合集、历史和音频接口；sid 是 Cookie 名，保守隐藏。
        private val QUERY_ALLOWLIST = setOf(
            "bvid", "aid", "avid", "cid", "ep_id", "season_id", "media_id", "fid", "pn", "ps",
            "qn", "fnval", "fnver", "fourk", "type", "oid", "page_num", "page_size", "p",
        )
        private val CDN_DOMAINS = setOf("bilivideo.com", "bilivideo.cn", "bilivideo.net", "akamaized.net", "mcdn.bilivideo.cn", "acgvideo.com")
        private val SAFE_ID = Regex("[a-zA-Z0-9_-]+")
        private val URL = Regex("https?://[^\\s<>\\\"'\\\\]+", RegexOption.IGNORE_CASE)
        private val HEADER = Regex("(?im)\\b(Cookie|Set-Cookie|Authorization)[\"']?\\s*[:=]\\s*[^\\r\\n]*")
        private val SECRET = Regex(
            "(?i)\\b(SESSDATA|bili_jct|DedeUserID__ckMd5|DedeUserID|sid|buvid3|buvid4|refresh_token|access_key|access_token|csrf|csrf_token|qrcode_key|captcha_key|recaptcha_token|tmp_token|tmp_code|token|validate|seccode|challenge|password|username|tel)([\\\"']?\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s;,&}\\]\\\"']+)",
        )
        private val IPV4 = Regex("(?<![\\w.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w.])")
        private val IPV6 = Regex("(?i)(?<![\\w:])(?:[0-9a-f]{0,4}:){2,}[0-9a-f]{0,4}(?:%[\\w]+)?(?![\\w:])")
    }
}
