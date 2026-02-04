package com.happycola233.bilitools.ui

import android.content.Intent
import android.net.Uri

internal object ExternalDownloadContract {
    const val EXTRA_URL = "com.happycola233.bilitools.extra.EXTERNAL_URL"
    const val RESULT_KEY = "com.happycola233.bilitools.result.EXTERNAL_DOWNLOAD"
    const val RESULT_URL = "url"
}

private val HTTP_URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

internal fun Intent.extractExternalDownloadUrl(): String? {
    val raw = when (action) {
        Intent.ACTION_VIEW -> dataString
        Intent.ACTION_SEND -> {
            getStringExtra(Intent.EXTRA_TEXT)
                ?: clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
        }
        else -> null
    }
    if (raw.isNullOrBlank()) return null

    val candidate = HTTP_URL_REGEX.find(raw)?.value ?: raw
    return normalizeHttpUrl(candidate)
}

internal fun normalizeHttpUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw
        .trim()
        .trimEnd(
            '.',
            ',',
            ';',
            ':',
            '!',
            '?',
            ')',
            ']',
            '}',
            '>',
            '。',
            '，',
            '；',
            '：',
            '！',
            '？',
            '）',
            '】',
            '》',
        )
    if (trimmed.isBlank()) return null

    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val host = uri.host
    if (scheme != "http" && scheme != "https") return null
    if (host.isNullOrBlank()) return null

    return trimmed
}
