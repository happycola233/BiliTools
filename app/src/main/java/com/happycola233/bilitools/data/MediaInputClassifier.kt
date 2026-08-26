package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.ParsedInput
import okhttp3.HttpUrl

class InvalidMediaInputException : IllegalArgumentException("Invalid input")

internal object MediaInputClassifier {
    fun parseDirectId(raw: String): ParsedInput? {
        if (raw.startsWith("uid", ignoreCase = true)) {
            val digits = raw.substring(3)
            if (!digits.isAsciiDigits()) return null
            return ParsedInput("uid$digits", MediaType.UserVideo)
        }

        // BV 号体本身区分大小写，只把前缀规范成 BV。
        if (raw.length == BV_ID_LENGTH && raw.startsWith("bv", ignoreCase = true)) {
            val body = raw.substring(2)
            if (body.all { it.isAsciiLetterOrDigit() }) {
                return ParsedInput("BV$body", MediaType.Video)
            }
        }

        if (raw.length <= 2) return null
        val digits = raw.substring(2)
        if (!digits.isAsciiDigits()) return null
        val type = TWO_CHAR_PREFIX_TYPES[raw.substring(0, 2).lowercase()] ?: return null
        return ParsedInput(raw.substring(0, 2).lowercase() + digits, type)
    }

    /**
     * 桌面空间页将 UID 放在根路径，移动空间页则额外带有 `/space` 前缀；
     * 归一化后共用同一套路由规则，避免两种域名的解析行为逐渐分叉。
     */
    fun parseSpaceUrl(url: HttpUrl): ParsedInput? {
        val pathSegments = url.pathSegments.filter(String::isNotBlank)
        val spaceSegments = when {
            url.host.equals("space.bilibili.com", ignoreCase = true) -> pathSegments
            url.host.equals("m.bilibili.com", ignoreCase = true) &&
                pathSegments.firstOrNull() == "space" -> pathSegments.drop(1)
            else -> return null
        }

        val mid = spaceSegments.getOrNull(0) ?: throw InvalidMediaInputException()
        if (mid.isEmpty() || mid.any { it !in '0'..'9' }) throw InvalidMediaInputException()
        val type = spaceSegments.getOrNull(1)
        if (type == "favlist") {
            val fid = url.queryParameter("fid")?.toLongOrNull()
            return ParsedInput(mid, MediaType.Favorite, fid)
        }
        // 兼容 /{mid}/video 与 /{mid}/upload/video。
        if ((spaceSegments.getOrNull(2) ?: type) == "video" ||
            type == "lists" ||
            spaceSegments.size == 1
        ) {
            val listId = spaceSegments
                .zipWithNext()
                .firstOrNull { (segment, _) -> segment == "lists" }
                ?.second
                ?.toLongOrNull()
            return ParsedInput(mid, MediaType.UserVideo, listId)
        }
        if (spaceSegments.getOrNull(2) == "opus" || type == "article") {
            return ParsedInput(mid, MediaType.UserOpus)
        }
        if (spaceSegments.getOrNull(2) == "audio" || type == "audio") {
            return ParsedInput(mid, MediaType.UserAudio)
        }
        throw InvalidMediaInputException()
    }

    private const val BV_ID_LENGTH = 12

    private val TWO_CHAR_PREFIX_TYPES = mapOf(
        "av" to MediaType.Video,
        "ep" to MediaType.Bangumi,
        "ss" to MediaType.Bangumi,
        "md" to MediaType.Bangumi,
        "au" to MediaType.Music,
        "am" to MediaType.MusicList,
        "cv" to MediaType.Opus,
        "rl" to MediaType.OpusList,
    )

    private fun String.isAsciiDigits(): Boolean = isNotEmpty() && all { it in '0'..'9' }

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in '0'..'9' || this in 'A'..'Z' || this in 'a'..'z'
}
