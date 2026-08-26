package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.ParsedInput
import okhttp3.HttpUrl

internal object MediaInputClassifier {
    fun parseDirectId(raw: String): ParsedInput? {
        if (!DIRECT_ID_REGEX.matches(raw)) return null

        val prefix = if (raw.startsWith("uid", ignoreCase = true)) {
            "uid"
        } else {
            raw.substring(0, 2).lowercase()
        }
        val type = when (prefix) {
            "av", "bv" -> MediaType.Video
            "ep", "ss", "md" -> MediaType.Bangumi
            "au" -> MediaType.Music
            "am" -> MediaType.MusicList
            "cv" -> MediaType.Opus
            "rl" -> MediaType.OpusList
            "uid" -> MediaType.UserVideo
            else -> null
        }
        return ParsedInput(raw, type)
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

        val mid = spaceSegments.getOrNull(0) ?: throw IllegalArgumentException("Invalid input")
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
        throw IllegalArgumentException("Invalid input")
    }

    private val DIRECT_ID_REGEX = Regex(
        "^(av\\d+|BV[0-9A-Za-z]{10}|ep\\d+|ss\\d+|md\\d+|au\\d+|am\\d+|cv\\d+|rl\\d+|uid\\d+)$",
        RegexOption.IGNORE_CASE,
    )
}
