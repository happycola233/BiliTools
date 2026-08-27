package com.happycola233.bilitools.core.naming

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class NamingSegmentKind {
    /** 普通字面量。 */
    Literal,

    /** 可识别的变量。 */
    Token,

    /** 写错的变量名，原样保留并在预览里标红。 */
    Unknown,

    /** 可选片段的起止标记。 */
    OptionalStart,
    OptionalEnd,
}

data class NamingPreviewSegment(
    val raw: String,
    val kind: NamingSegmentKind,
    val token: NamingToken? = null,
    /** 处于可选片段内部。 */
    val optional: Boolean = false,
)

/**
 * 模板渲染。除了 `{变量}` 与 `{变量:格式}` 外，还支持可选片段 `{?…}`：
 * 片段内任意一个变量取不到值时，整段（含其中的括号、前缀、空格）一并消失。
 * 这样 `{?(P{p}) }` 在非分 P 资源上不会留下孤零零的 `(P)`。
 */
object NamingRenderer {
    private val trailingDotsRegex = Regex("\\.+$")

    // 变量缺失时，模板里相邻的 “ - ” 会拼成 “ -  - ”；至少两段连接符才收成一段。
    private val redundantDashSeparatorRegex = Regex("""(\s*-\s*){2,}""")
    private val redundantWhitespaceRegex = Regex("""\s{2,}""")
    private val illegalPathCharsRegex = Regex("""[\\/:*?"<>|]""")
    private val dayjsFormatterFallback = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd_HH-mm-ss",
        Locale.getDefault(),
    )

    fun render(template: String, context: NamingContext): String {
        if (template.isBlank()) return ""
        return buildString {
            parse(template).forEach { node -> append(renderNode(node, context)) }
        }
    }

    fun renderComponent(
        template: String,
        context: NamingContext,
        cleanSeparators: Boolean = true,
    ): String {
        return normalizeComponent(
            raw = render(template, context),
            cleanSeparators = cleanSeparators,
        )
    }

    fun normalizeComponent(raw: String, cleanSeparators: Boolean = true): String {
        val sanitized = sanitizeComponent(raw)
        return if (cleanSeparators) cleanRedundantSeparators(sanitized) else sanitized
    }

    fun sanitizeComponent(raw: String): String {
        return raw
            .replace(illegalPathCharsRegex, "_")
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

    /** 供设置页富预览使用：把模板拆成可逐段上色的片段。 */
    fun previewSegments(template: String): List<NamingPreviewSegment> {
        val result = mutableListOf<NamingPreviewSegment>()
        parse(template).forEach { node -> flatten(node, optional = false, into = result) }
        return result
    }

    /** 把光标处包成可选片段，或在光标处插入一个空的可选片段。 */
    fun wrapAsOptional(text: String): String = "{?$text}"

    private fun flatten(
        node: NamingNode,
        optional: Boolean,
        into: MutableList<NamingPreviewSegment>,
    ) {
        when (node) {
            is NamingNode.Literal -> if (node.text.isNotEmpty()) {
                into += NamingPreviewSegment(node.text, NamingSegmentKind.Literal, optional = optional)
            }

            is NamingNode.Variable -> into += NamingPreviewSegment(
                raw = node.raw,
                kind = if (node.token != null) NamingSegmentKind.Token else NamingSegmentKind.Unknown,
                token = node.token,
                optional = optional,
            )

            is NamingNode.Optional -> {
                into += NamingPreviewSegment("{?", NamingSegmentKind.OptionalStart, optional = true)
                node.children.forEach { flatten(it, optional = true, into = into) }
                into += NamingPreviewSegment("}", NamingSegmentKind.OptionalEnd, optional = true)
            }
        }
    }

    private fun renderNode(node: NamingNode, context: NamingContext): String {
        return when (node) {
            is NamingNode.Literal -> node.text
            is NamingNode.Variable -> {
                if (node.token == null) node.raw else valueFor(node.token, context, node.pattern).orEmpty()
            }

            is NamingNode.Optional -> {
                val missing = node.children.any { child ->
                    child is NamingNode.Variable &&
                        (child.token == null || valueFor(child.token, context, child.pattern) == null)
                }
                if (missing) "" else node.children.joinToString("") { renderNode(it, context) }
            }
        }
    }

    private fun parse(template: String): List<NamingNode> {
        val nodes = mutableListOf<NamingNode>()
        val literal = StringBuilder()
        var index = 0
        fun flushLiteral() {
            if (literal.isNotEmpty()) {
                nodes += NamingNode.Literal(literal.toString())
                literal.clear()
            }
        }
        while (index < template.length) {
            val char = template[index]
            if (char != '{') {
                literal.append(char)
                index += 1
                continue
            }
            val end = findClosingBrace(template, index)
            if (end < 0) {
                literal.append(char)
                index += 1
                continue
            }
            val inner = template.substring(index + 1, end)
            flushLiteral()
            nodes += if (inner.startsWith('?')) {
                NamingNode.Optional(parse(inner.substring(1)))
            } else {
                variableNode(template.substring(index, end + 1), inner)
            }
            index = end + 1
        }
        flushLiteral()
        return nodes
    }

    /** 返回与 [open] 处 `{` 配对的 `}` 下标；没有配对时返回 -1。 */
    private fun findClosingBrace(template: String, open: Int): Int {
        var depth = 0
        for (index in open until template.length) {
            when (template[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    private fun variableNode(raw: String, inner: String): NamingNode {
        val key = inner.substringBefore(':').trim()
        val pattern = inner.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }
        return NamingNode.Variable(
            raw = raw,
            token = NamingToken.fromKey(key),
            pattern = pattern,
        )
    }

    private fun cleanRedundantSeparators(raw: String): String {
        return raw
            .replace(redundantDashSeparatorRegex, " - ")
            .replace(redundantWhitespaceRegex, " ")
            .trim { it.isWhitespace() || it == '-' }
    }

    private fun valueFor(
        token: NamingToken,
        context: NamingContext,
        pattern: String?,
    ): String? {
        return when (token) {
            NamingToken.Title -> context.title
            NamingToken.Work -> context.work
            NamingToken.Collection -> context.collection
            NamingToken.P -> context.p
            NamingToken.Ep -> context.ep
            NamingToken.LongTitle -> context.longTitle
            NamingToken.Section -> context.section
            NamingToken.Img -> context.img
            NamingToken.Container -> context.container
            NamingToken.MediaType -> context.mediaType
            NamingToken.TaskType -> context.taskType
            NamingToken.Index -> context.index?.toString()
            NamingToken.PubTime -> context.pubTimeEpochSeconds?.let { formatEpochSeconds(it, pattern) }
            NamingToken.DownTime -> context.downTimeEpochSeconds?.let { formatEpochSeconds(it, pattern) }
            NamingToken.Id -> context.id
            NamingToken.Upper -> context.upper
            NamingToken.UpperId -> context.upperId
            NamingToken.Artist -> context.artist
            NamingToken.Aid -> context.aid
            NamingToken.Bvid -> context.bvid
            NamingToken.Cid -> context.cid
            NamingToken.Epid -> context.epid
            NamingToken.Ssid -> context.ssid
            NamingToken.Sid -> context.sid
            NamingToken.Amid -> context.amid
            NamingToken.Fid -> context.fid
            NamingToken.Opid -> context.opid
            NamingToken.Cvid -> context.cvid
            NamingToken.Res -> context.res
            NamingToken.Abr -> context.abr
            NamingToken.Enc -> context.enc
            NamingToken.Fmt -> context.fmt
        }?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun formatEpochSeconds(rawEpochSeconds: Long, pattern: String?): String {
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
        "ZZ" to "XX",
        "Z" to "XXX",
        "DD" to "dd",
        "A" to "a",
    )
}

private sealed interface NamingNode {
    data class Literal(val text: String) : NamingNode

    data class Variable(
        val raw: String,
        val token: NamingToken?,
        val pattern: String?,
    ) : NamingNode

    data class Optional(val children: List<NamingNode>) : NamingNode
}
