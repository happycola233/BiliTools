package com.happycola233.bilitools.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class NamingTemplateScope {
    TopFolder,
    ItemFolder,
    File,
}

enum class NamingTokenGroup {
    General,
    Time,
    Ids,
    Stream,
}

enum class NamingToken(
    val key: String,
    val scopes: Set<NamingTemplateScope>,
    val group: NamingTokenGroup,
    val supportsPattern: Boolean = false,
) {
    VideoTitle(
        key = "videotitle",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.General,
    ),
    CollectionTitle(
        key = "collectiontitle",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.General,
    ),
    Title(
        key = "title",
        scopes = setOf(NamingTemplateScope.ItemFolder, NamingTemplateScope.File),
        group = NamingTokenGroup.General,
    ),
    P(
        key = "p",
        scopes = setOf(NamingTemplateScope.ItemFolder, NamingTemplateScope.File),
        group = NamingTokenGroup.General,
    ),
    Container(
        key = "container",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.General,
    ),
    MediaType(
        key = "mediaType",
        scopes = setOf(NamingTemplateScope.ItemFolder, NamingTemplateScope.File),
        group = NamingTokenGroup.General,
    ),
    TaskType(
        key = "taskType",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.General,
    ),
    Index(
        key = "index",
        scopes = setOf(NamingTemplateScope.ItemFolder, NamingTemplateScope.File),
        group = NamingTokenGroup.Time,
    ),
    PubTime(
        key = "pubtime",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Time,
        supportsPattern = true,
    ),
    DownTime(
        key = "downtime",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Time,
        supportsPattern = true,
    ),
    Upper(
        key = "upper",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    UpperId(
        key = "upperid",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    Aid(
        key = "aid",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    Sid(
        key = "sid",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    Fid(
        key = "fid",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    Cid(
        key = "cid",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    Bvid(
        key = "bvid",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    Epid(
        key = "epid",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    Ssid(
        key = "ssid",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    Opid(
        key = "opid",
        scopes = setOf(
            NamingTemplateScope.TopFolder,
            NamingTemplateScope.ItemFolder,
            NamingTemplateScope.File,
        ),
        group = NamingTokenGroup.Ids,
    ),
    Res(
        key = "res",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.Stream,
    ),
    Abr(
        key = "abr",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.Stream,
    ),
    Enc(
        key = "enc",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.Stream,
    ),
    Fmt(
        key = "fmt",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.Stream,
    ),
    ;

    companion object {
        fun fromKey(key: String): NamingToken? = entries.firstOrNull { it.key == key }
    }
}

data class NamingRenderContext(
    val videoTitle: String? = null,
    val collectionTitle: String? = null,
    val title: String? = null,
    val p: String? = null,
    val container: String? = null,
    val mediaType: String? = null,
    val taskType: String? = null,
    val index: Int? = null,
    val pubTimeEpochSeconds: Long? = null,
    val downTimeEpochSeconds: Long? = null,
    val upper: String? = null,
    val upperId: String? = null,
    val aid: String? = null,
    val sid: String? = null,
    val fid: String? = null,
    val cid: String? = null,
    val bvid: String? = null,
    val epid: String? = null,
    val ssid: String? = null,
    val opid: String? = null,
    val res: String? = null,
    val abr: String? = null,
    val enc: String? = null,
    val fmt: String? = null,
)

data class NamingPreviewSegment(
    val raw: String,
    val token: NamingToken? = null,
)

object DownloadNaming {
    const val DEFAULT_TOP_FOLDER_TEMPLATE = "{container} - {collectiontitle} ({downtime:YYYY-MM-DD_HH-mm-ss})"
    const val DEFAULT_ITEM_FOLDER_TEMPLATE = "{mediaType} - {bvid} - {videotitle}"
    const val DEFAULT_FILE_TEMPLATE = "{taskType} - (P{p}) {title} - {res}"

    private val tokenRegex = Regex("\\{([^{}]+)\\}")
    private val trailingDotsRegex = Regex("\\.+$")
    private val dayjsFormatterFallback = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd_HH-mm-ss",
        Locale.getDefault(),
    )

    fun tokensForScope(scope: NamingTemplateScope): List<NamingToken> {
        return NamingToken.entries.filter { scope in it.scopes }
    }

    fun previewSegments(template: String): List<NamingPreviewSegment> {
        if (template.isEmpty()) return emptyList()
        val result = mutableListOf<NamingPreviewSegment>()
        var lastEnd = 0
        tokenRegex.findAll(template).forEach { match ->
            val start = match.range.first
            if (start > lastEnd) {
                result += NamingPreviewSegment(
                    raw = template.substring(lastEnd, start),
                )
            }
            val inner = match.groupValues.getOrNull(1).orEmpty()
            val tokenKey = inner.substringBefore(':').trim()
            result += NamingPreviewSegment(
                raw = match.value,
                token = NamingToken.fromKey(tokenKey),
            )
            lastEnd = match.range.last + 1
        }
        if (lastEnd < template.length) {
            result += NamingPreviewSegment(
                raw = template.substring(lastEnd),
            )
        }
        return result
    }

    fun renderTemplate(
        template: String,
        context: NamingRenderContext,
    ): String {
        if (template.isBlank()) return ""
        return tokenRegex.replace(template) { match ->
            val inner = match.groupValues.getOrNull(1).orEmpty()
            val tokenKey = inner.substringBefore(':').trim()
            val formatPattern = inner.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }
            val token = NamingToken.fromKey(tokenKey) ?: return@replace match.value
            valueFor(token, context, formatPattern).orEmpty()
        }
    }

    fun renderComponent(
        template: String,
        context: NamingRenderContext,
        cleanSeparators: Boolean = true,
    ): String {
        return normalizeComponent(
            raw = renderTemplate(template, context),
            cleanSeparators = cleanSeparators,
        )
    }

    fun normalizeComponent(
        raw: String,
        cleanSeparators: Boolean = true,
    ): String {
        val sanitized = sanitizeComponent(raw)
        return if (cleanSeparators) {
            trimOuterSeparators(sanitized)
        } else {
            sanitized
        }
    }

    fun sanitizeComponent(raw: String): String {
        return raw
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(trailingDotsRegex, "")
            .trim()
    }

    fun appendExtension(
        baseName: String,
        extension: String,
        cleanSeparators: Boolean = true,
    ): String {
        val normalizedBase = normalizeComponent(baseName, cleanSeparators)
        val normalizedExt = extension.trim().trimStart('.')
        return when {
            normalizedExt.isBlank() -> normalizedBase
            normalizedBase.isBlank() -> ".$normalizedExt"
            else -> sanitizeComponent("$normalizedBase.$normalizedExt")
        }
    }

    private fun trimOuterSeparators(raw: String): String {
        return raw.trim { it.isWhitespace() || it == '-' }
    }

    private fun valueFor(
        token: NamingToken,
        context: NamingRenderContext,
        formatPattern: String?,
    ): String? {
        return when (token) {
            NamingToken.VideoTitle -> context.videoTitle
            NamingToken.CollectionTitle -> context.collectionTitle
            NamingToken.Title -> context.title
            NamingToken.P -> context.p
            NamingToken.Container -> context.container
            NamingToken.MediaType -> context.mediaType
            NamingToken.TaskType -> context.taskType
            NamingToken.Index -> context.index?.toString()
            NamingToken.PubTime -> context.pubTimeEpochSeconds?.let {
                formatEpochSeconds(it, formatPattern)
            }
            NamingToken.DownTime -> context.downTimeEpochSeconds?.let {
                formatEpochSeconds(it, formatPattern)
            }
            NamingToken.Upper -> context.upper
            NamingToken.UpperId -> context.upperId
            NamingToken.Aid -> context.aid
            NamingToken.Sid -> context.sid
            NamingToken.Fid -> context.fid
            NamingToken.Cid -> context.cid
            NamingToken.Bvid -> context.bvid
            NamingToken.Epid -> context.epid
            NamingToken.Ssid -> context.ssid
            NamingToken.Opid -> context.opid
            NamingToken.Res -> context.res
            NamingToken.Abr -> context.abr
            NamingToken.Enc -> context.enc
            NamingToken.Fmt -> context.fmt
        }?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun formatEpochSeconds(
        rawEpochSeconds: Long,
        pattern: String?,
    ): String {
        val epochSeconds = if (rawEpochSeconds > 10_000_000_000L) {
            rawEpochSeconds / 1000L
        } else {
            rawEpochSeconds
        }
        if (pattern.equals("ts", ignoreCase = true)) {
            return epochSeconds.toString()
        }
        val translated = translateDayjsPattern(
            pattern?.takeIf { it.isNotBlank() } ?: "YYYY-MM-DD_HH-mm-ss",
        )
        val formatter = runCatching {
            DateTimeFormatter.ofPattern(translated, Locale.getDefault())
        }.getOrDefault(dayjsFormatterFallback)
        return formatter.format(
            Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()),
        )
    }

    private fun translateDayjsPattern(pattern: String): String {
        var result = pattern
        DAYJS_PATTERN_REPLACEMENTS.forEach { (source, target) ->
            result = result.replace(source, target)
        }
        return result
    }

    private val DAYJS_PATTERN_REPLACEMENTS = listOf(
        "dddd" to "EEEE",
        "ddd" to "EEE",
        "YYYY" to "yyyy",
        "YY" to "yy",
        "SSS" to "SSS",
        "ZZ" to "XX",
        "Z" to "XXX",
        "DD" to "dd",
        "HH" to "HH",
        "hh" to "hh",
        "MM" to "MM",
        "ss" to "ss",
        "mm" to "mm",
        "A" to "a",
    )
}
