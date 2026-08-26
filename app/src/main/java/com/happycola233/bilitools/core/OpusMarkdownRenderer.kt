package com.happycola233.bilitools.core

import com.happycola233.bilitools.data.model.OpusAssetPlan
import com.happycola233.bilitools.data.model.OpusBlock
import com.happycola233.bilitools.data.model.OpusDocument
import com.happycola233.bilitools.data.model.OpusImage
import com.happycola233.bilitools.data.model.OpusNode
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

object OpusAssetPlanner {
    private val supportedExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp")

    fun plan(document: OpusDocument, requestedBaseName: String): List<OpusAssetPlan> {
        if (document.images.isEmpty()) return emptyList()
        val sanitizedBaseName = DownloadNaming.sanitizeComponent(requestedBaseName)
        val safeBaseName = sanitizedBaseName
            .substringBeforeLast('.', sanitizedBaseName)
            .ifBlank { "图文图片" }
        val digits = max(2, document.images.size.toString().length)
        return document.images.mapIndexed { index, image ->
            val extension = extensionFromUrl(image.url)
            OpusAssetPlan(
                image = image,
                fileName = "$safeBaseName - ${(index + 1).toString().padStart(digits, '0')}.$extension",
                mimeType = mimeTypeFor(extension),
            )
        }
    }

    fun normalizeImageUrl(rawUrl: String): String {
        val withScheme = normalizeWebUrl(rawUrl)
        if (withScheme.isBlank()) return ""
        val withoutFragment = withScheme.substringBefore('#')
        val host = runCatching { URI(withoutFragment).host.orEmpty() }.getOrDefault("")
        return if (host.isBilibiliImageHost()) {
            withoutFragment.substringBefore('?').substringBefore('@')
        } else {
            withoutFragment
        }
    }

    fun normalizeWebUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://", ignoreCase = true) -> {
                val host = runCatching { URI(trimmed).host.orEmpty() }.getOrDefault("")
                if (host.isDomainOrSubdomainOf("bilibili.com") ||
                    host.isBilibiliImageHost()
                ) {
                    "https://${trimmed.substringAfter("://")}"
                } else {
                    trimmed
                }
            }
            else -> trimmed
        }
    }

    private fun extensionFromUrl(url: String): String {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url)
        val candidate = path.substringAfterLast('.', "").lowercase()
        return candidate.takeIf(supportedExtensions::contains) ?: "jpg"
    }

    private fun mimeTypeFor(extension: String): String = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }

    private fun String.isDomainOrSubdomainOf(domain: String): Boolean {
        return equals(domain, ignoreCase = true) || endsWith(".$domain", ignoreCase = true)
    }

    private fun String.isBilibiliImageHost(): Boolean {
        return isDomainOrSubdomainOf("hdslb.com") || isDomainOrSubdomainOf("biliimg.com")
    }
}

object OpusMarkdownRenderer {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.of("Asia/Shanghai"))

    fun render(
        document: OpusDocument,
        localAssets: List<OpusAssetPlan> = emptyList(),
    ): String {
        val localTargets = localAssets.associate { it.image.url to encodeRelativePath(it.fileName) }
        val imageOrder = document.images.mapIndexed { index, image -> image.url to index + 1 }.toMap()
        val renderedImages = linkedSetOf<String>()
        val output = StringBuilder()

        output.append("# ").append(escapeMarkdown(document.title)).append("\n\n")
        document.author?.name?.takeIf(String::isNotBlank)?.let { author ->
            output.append("- 作者：").append(escapeMarkdown(author)).append('\n')
        }
        document.publishedAt?.takeIf { it > 0L }?.let { publishedAt ->
            output.append("- 发布时间：").append(dateFormatter.format(Instant.ofEpochSecond(publishedAt))).append('\n')
        }
        buildStatText(document)?.let { output.append("- 数据：").append(it).append('\n') }
        output.append("- 原文：[")
            .append(escapeMarkdown(document.sourceUrl))
            .append("](")
            .append(escapeLinkDestination(document.sourceUrl))
            .append(")\n\n")

        fun appendImage(image: OpusImage) {
            if (!renderedImages.add(image.url)) return
            val order = imageOrder[image.url] ?: renderedImages.size
            val alt = image.alt.ifBlank { "图片 $order" }
            val target = escapeLinkDestination(localTargets[image.url] ?: image.url)
            output.append("![")
                .append(escapeAltText(alt))
                .append("](")
                .append(target)
                .append(")\n\n")
        }

        document.topImages.forEach(::appendImage)
        document.blocks.forEach { block ->
            when (block) {
                is OpusBlock.Paragraph -> {
                    val text = renderNodes(block.nodes)
                    if (text.isNotBlank()) {
                        block.headingLevel?.let { level ->
                            output.append("#".repeat(level.coerceIn(2, 6))).append(' ')
                        }
                        output.append(text).append("\n\n")
                    }
                    block.nodes.filterIsInstance<OpusNode.Picture>()
                        .flatMap(OpusNode.Picture::images)
                        .forEach(::appendImage)
                }
                is OpusBlock.Pictures -> block.images.forEach(::appendImage)
                OpusBlock.Divider -> output.append("---\n\n")
                is OpusBlock.Quote -> {
                    val quote = renderNodes(block.nodes).trim()
                    if (quote.isNotBlank()) {
                        quote.lineSequence().forEach { line ->
                            output.append("> ").append(line).append('\n')
                        }
                        output.append('\n')
                    }
                    block.nodes.filterIsInstance<OpusNode.Picture>()
                        .flatMap(OpusNode.Picture::images)
                        .forEach(::appendImage)
                }
                is OpusBlock.ListBlock -> {
                    block.items.forEachIndexed { index, item ->
                        val indent = "    ".repeat((item.level - 1).coerceAtLeast(0))
                        val marker = if (block.ordered) "${item.order.takeIf { it > 0 } ?: index + 1}." else "-"
                        output.append(indent).append(marker).append(' ')
                            .append(renderNodes(item.nodes).trim())
                            .append('\n')
                    }
                    output.append('\n')
                    block.items.flatMap { it.nodes }
                        .filterIsInstance<OpusNode.Picture>()
                        .flatMap(OpusNode.Picture::images)
                        .forEach(::appendImage)
                }
                is OpusBlock.LinkCard -> {
                    val title = escapeMarkdown(block.title.ifBlank { block.url.orEmpty() })
                    if (!block.url.isNullOrBlank()) {
                        output.append('[').append(title).append("](")
                            .append(escapeLinkDestination(block.url))
                            .append(")\n\n")
                    } else if (title.isNotBlank()) {
                        output.append(title).append("\n\n")
                    }
                }
                is OpusBlock.Code -> {
                    val content = decodeHtmlEntities(block.content).trimEnd()
                    val fence = "`".repeat(max(3, longestBacktickRun(content) + 1))
                    val language = block.language.orEmpty().removePrefix("language-").trim()
                    output.append(fence).append(language).append('\n')
                        .append(content).append('\n')
                        .append(fence).append("\n\n")
                }
            }
        }

        return output.toString().trimEnd() + "\n"
    }

    private fun renderNodes(nodes: List<OpusNode>): String = buildString {
        nodes.forEach { node ->
            when (node) {
                is OpusNode.Text -> {
                    var value = escapeMarkdown(node.text)
                    if (node.bold && value.isNotBlank()) value = "**$value**"
                    if (node.italic && value.isNotBlank()) value = "*$value*"
                    if (node.strikethrough && value.isNotBlank()) value = "~~$value~~"
                    append(value)
                }
                is OpusNode.Link -> append('[')
                    .append(escapeMarkdown(node.text.ifBlank { node.url }))
                    .append("](")
                    .append(escapeLinkDestination(node.url))
                    .append(')')
                is OpusNode.Formula -> append('$')
                    .append(node.latex.trim().replace("$", "\\$"))
                    .append('$')
                is OpusNode.Picture -> if (node.text.isNotBlank()) append(escapeMarkdown(node.text))
            }
        }
    }

    private fun buildStatText(document: OpusDocument): String? {
        val values = buildList {
            document.stat.play?.let { add("阅读 $it") }
            document.stat.like?.let { add("点赞 $it") }
            document.stat.coin?.let { add("投币 $it") }
            document.stat.favorite?.let { add("收藏 $it") }
            document.stat.reply?.let { add("评论 $it") }
            document.stat.share?.let { add("转发 $it") }
        }
        return values.takeIf(List<String>::isNotEmpty)?.joinToString(" · ")
    }

    private fun escapeMarkdown(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (character in MARKDOWN_SPECIAL_CHARACTERS) append('\\')
            append(character)
        }
    }

    private fun escapeAltText(value: String): String = value
        .replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")

    private fun encodeRelativePath(fileName: String): String = runCatching {
        URI(null, null, fileName, null).rawPath
    }.getOrDefault(fileName.replace(" ", "%20"))

    private fun escapeLinkDestination(value: String): String = value
        .replace("\\", "%5C")
        .replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")

    private fun longestBacktickRun(value: String): Int = Regex("`+")
        .findAll(value)
        .maxOfOrNull { it.value.length }
        ?: 0

    internal fun decodeHtmlEntities(value: String): String {
        var decoded = value
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", "\u00a0")
            .replace("&amp;", "&")
        decoded = Regex("&#(\\d+);").replace(decoded) { match ->
            match.groupValues[1].toIntOrNull()?.let(::codePointToString) ?: match.value
        }
        return Regex("&#x([0-9a-fA-F]+);").replace(decoded) { match ->
            match.groupValues[1].toIntOrNull(16)?.let(::codePointToString) ?: match.value
        }
    }

    private fun codePointToString(codePoint: Int): String = runCatching {
        String(Character.toChars(codePoint))
    }.getOrDefault("")

    private const val MARKDOWN_SPECIAL_CHARACTERS = "\\`*_[]{}()#+-!|>~$"
}
