package com.happycola233.bilitools.ui.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.alerts.AlertsExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 按 GitHub 发布说明的规则解析 Markdown（GFM 与内嵌 HTML）。
 *
 * 先由 commonmark-java 按 CommonMark/GFM 规范生成 HTML，再交给 jsoup 按 HTML5 规则建树，最后转换为
 * [MarkdownDocument]。手写的 `<picture>`、`<div align>`、`<details>`、`<br>` 与 Markdown 语法因此走同一条路径，
 * 嵌套关系与容错行为和 GitHub 网页一致；未识别的标签只保留其中的内容，不会把源码原样显示出来。
 */
object GithubMarkdownParser {
    private val extensions = listOf(
        AutolinkExtension.create(),
        AlertsExtension.create(),
        FootnotesExtension.create(),
        StrikethroughExtension.create(),
        TablesExtension.create(),
        TaskListItemsExtension.create(),
    )
    private val parser = Parser.builder().extensions(extensions).build()

    // 发布说明与 GitHub 评论一样，段落内的单个换行显示为换行，而不是合并成空格。
    private val htmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .softbreak("<br />\n")
        .build()

    /**
     * @param pageUrl 文档所在页面地址，用于补全相对链接；若是 GitHub 仓库下的页面，
     * 正文里的 `@用户名` 与 `#123` 会像 GitHub 一样链接到个人主页与该仓库的议题，其他来源的文档保持原文。
     */
    fun parse(markdown: String, pageUrl: String? = null): MarkdownDocument {
        val html = htmlRenderer.render(parser.parse(markdown))
        val body = Jsoup.parseBodyFragment(html, pageUrl.orEmpty()).body()
        val converter = DomConverter(repositoryUrl = pageUrl?.let(::gitHubRepositoryUrl))
        return MarkdownDocument(converter.blocks(body, MarkdownAlign.Start))
    }

    private fun gitHubRepositoryUrl(pageUrl: String): String? {
        val match = GITHUB_REPOSITORY_URL.find(pageUrl) ?: return null
        return "https://github.com/${match.groupValues[1]}/${match.groupValues[2]}"
    }

    private val GITHUB_REPOSITORY_URL = Regex("""^https://github\.com/([A-Za-z0-9-]+)/([A-Za-z0-9._-]+)""")
}

/** 把 jsoup DOM 转为文档模型：块级元素逐个转换，夹在块之间的行内内容合并为匿名段落（与浏览器一致）。 */
private class DomConverter(private val repositoryUrl: String?) {

    fun blocks(
        container: Element,
        align: MarkdownAlign,
        skip: Element? = null,
    ): List<MarkdownBlock> {
        val result = mutableListOf<MarkdownBlock>()
        var pendingInline: InlineBuilder? = null
        for (node in container.childNodes()) {
            if (node === skip) continue
            if (node is Element && node.normalName() in IGNORED_TAGS) continue
            if (node is Element && node.normalName() in BLOCK_TAGS) {
                pendingInline?.let { result += it.buildBlocks(align) }
                pendingInline = null
                result += block(node, align)
            } else {
                (pendingInline ?: InlineBuilder(this).also { pendingInline = it }).append(node)
            }
        }
        pendingInline?.let { result += it.buildBlocks(align) }
        return result
    }

    private fun block(element: Element, inheritedAlign: MarkdownAlign): List<MarkdownBlock> {
        val align = element.alignment() ?: inheritedAlign
        return when (val tag = element.normalName()) {
            "p" -> InlineBuilder(this).appendChildren(element).buildBlocks(align)
            "h1", "h2", "h3", "h4", "h5", "h6" -> InlineBuilder(this).appendChildren(element)
                .buildBlocks(align) { text -> MarkdownBlock.Heading(tag[1].digitToInt(), text, align) }
            "ul", "ol" -> listOf(list(element))
            "blockquote" -> listOf(MarkdownBlock.Quote(blocks(element, MarkdownAlign.Start)))
            "pre" -> listOf(codeBlock(element))
            "hr" -> listOf(MarkdownBlock.Divider)
            "table" -> listOfNotNull(table(element))
            "details" -> listOf(details(element, align))
            "center" -> blocks(element, MarkdownAlign.Center)
            "div" -> alert(element)?.let(::listOf) ?: blocks(element, align)
            // 脚注区与正文之间用分隔线隔开，对应 GitHub 页面上的视觉分区。
            "section" -> if (element.hasClass("footnotes")) {
                listOf(MarkdownBlock.Divider) + blocks(element, align)
            } else {
                blocks(element, align)
            }
            else -> blocks(element, align)
        }
    }

    private fun list(element: Element): MarkdownBlock.ListBlock {
        val items = element.children()
            .filter { it.normalName() == "li" }
            .map { item ->
                val checkbox = taskCheckbox(item)
                MarkdownListItem(
                    blocks = blocks(item, MarkdownAlign.Start, skip = checkbox),
                    taskChecked = checkbox?.hasAttr("checked"),
                )
            }
        return MarkdownBlock.ListBlock(
            ordered = element.normalName() == "ol",
            startNumber = element.attr("start").trim().toIntOrNull() ?: 1,
            items = items,
        )
    }

    /** 任务列表的复选框必须是列表项开头的第一个元素，夹在正文中间的表单控件不算。 */
    private fun taskCheckbox(item: Element): Element? {
        val first = item.firstElementChild() ?: return null
        if (first.normalName() != "input" || !first.attr("type").equals("checkbox", ignoreCase = true)) {
            return null
        }
        val hasLeadingText = generateSequence(first.previousSibling()) { it.previousSibling() }
            .any { it is TextNode && !it.isBlank }
        return first.takeUnless { hasLeadingText }
    }

    private fun codeBlock(element: Element): MarkdownBlock.CodeBlock {
        val code = element.children().firstOrNull { it.normalName() == "code" }
        val language = code?.classNames()
            ?.firstOrNull { it.startsWith("language-") }
            ?.removePrefix("language-")
            ?.takeIf { it.isNotBlank() }
        return MarkdownBlock.CodeBlock(
            code = (code ?: element).wholeText().removeSuffix("\n"),
            language = language,
        )
    }

    private fun table(element: Element): MarkdownBlock.Table? {
        val rows = element.select("tr")
            // 嵌套表格的行属于内层表格，不并入当前表格。
            .filter { it.closest("table") === element }
            .mapNotNull { row ->
                val cells = row.children()
                    .filter { it.normalName() == "th" || it.normalName() == "td" }
                    .map { cell ->
                        MarkdownTableCell(
                            text = InlineBuilder(this).appendChildren(cell).buildText(),
                            align = cell.alignment() ?: MarkdownAlign.Start,
                        )
                    }
                cells.takeIf { it.isNotEmpty() }?.let {
                    MarkdownTableRow(
                        cells = it,
                        isHeader = row.parent()?.normalName() == "thead" ||
                            row.children().all { cell -> cell.normalName() == "th" },
                    )
                }
            }
        return rows.takeIf { it.isNotEmpty() }?.let(MarkdownBlock::Table)
    }

    private fun details(element: Element, align: MarkdownAlign): MarkdownBlock.Details {
        val summary = element.children().firstOrNull { it.normalName() == "summary" }
        return MarkdownBlock.Details(
            summary = summary?.let { InlineBuilder(this).appendChildren(it).buildText() }?.takeUnless { it.isBlank() },
            blocks = blocks(element, align, skip = summary),
            initiallyExpanded = element.hasAttr("open"),
        )
    }

    /** commonmark 的提示块扩展输出 `div.markdown-alert[data-alert-type]`，结构与 GitHub 相同。 */
    private fun alert(element: Element): MarkdownBlock.Alert? {
        if (!element.hasClass("markdown-alert")) return null
        val kind = when (element.attr("data-alert-type").lowercase()) {
            "note" -> MarkdownAlertKind.Note
            "tip" -> MarkdownAlertKind.Tip
            "important" -> MarkdownAlertKind.Important
            "warning" -> MarkdownAlertKind.Warning
            "caution" -> MarkdownAlertKind.Caution
            else -> return null
        }
        val titleElement = element.children().firstOrNull { it.hasClass("markdown-alert-title") }
        // 默认标题是英文类型名，交给界面按当前语言显示；自定义标题原样保留。
        val title = titleElement?.text()?.trim()
            ?.takeUnless { it.isEmpty() || it.equals(kind.name, ignoreCase = true) }
        return MarkdownBlock.Alert(kind, title, blocks(element, MarkdownAlign.Start, skip = titleElement))
    }

    fun image(
        element: Element,
        link: String?,
        sources: List<MarkdownImageSource> = emptyList(),
    ): MarkdownImage? {
        val url = element.absUrl("src").takeIf(::isHttpUrl)
            ?: element.firstSrcsetUrl()
            ?: sources.lastOrNull()?.url
            ?: return null
        return MarkdownImage(
            url = url,
            alt = element.attr("alt").trim().ifEmpty { null },
            width = parseLength(element.attr("width")) ?: element.cssLength("width"),
            height = parseLength(element.attr("height")) ?: element.cssLength("height"),
            link = link,
            sources = sources,
        )
    }

    fun picture(element: Element, link: String?): MarkdownImage? {
        val sources = element.children()
            .filter { it.normalName() == "source" }
            .mapNotNull { source ->
                source.firstSrcsetUrl()?.let { url ->
                    MarkdownImageSource(media = source.attr("media").trim().ifEmpty { null }, url = url)
                }
            }
        val fallback = element.children().firstOrNull { it.normalName() == "img" }
        return if (fallback != null) {
            image(fallback, link, sources)
        } else {
            sources.lastOrNull()?.let { MarkdownImage(url = it.url, link = link, sources = sources) }
        }
    }

    /**
     * GitHub 会把正文里的 `@用户名` 链接到个人主页，把 `#123` 链接到当前仓库的议题或拉取请求；
     * 不是来自 GitHub 仓库页面的文档（如 B 站 AI 总结）不做这种转换。
     */
    fun referenceUrl(reference: String): String? {
        val repository = repositoryUrl ?: return null
        return when (reference.first()) {
            '@' -> "https://github.com/${reference.drop(1)}"
            else -> "$repository/issues/${reference.drop(1)}"
        }
    }
}

/**
 * 收集一段行内内容。空白按 HTML 规则折叠，`<br>` 转为换行；
 * 前后都是换行的图片会被拆成独立的图片块，其余图片作为行内内容保留在文字中。
 */
private class InlineBuilder(private val converter: DomConverter) {
    private val text = StringBuilder()
    private val spans = mutableListOf<MarkdownSpan>()
    private val images = mutableListOf<MarkdownInlineImage>()
    private var pendingSpace = false
    private var linkUrl: String? = null
    private var codeDepth = 0

    fun appendChildren(element: Element): InlineBuilder = apply {
        element.childNodes().forEach(::append)
    }

    fun append(node: Node) {
        when (node) {
            is TextNode -> appendText(node.wholeText)
            is Element -> appendElement(node)
        }
    }

    fun buildText(): MarkdownText = MarkdownText(
        text = text.toString(),
        // 行尾空格在换行时被去掉，可能让少数区间越过文本末尾，这里统一收口。
        spans = spans.mapNotNull { span ->
            val end = minOf(span.end, text.length)
            if (end > span.start) span.copy(end = end) else null
        },
        images = images.toList(),
    ).trimmed()

    fun buildBlocks(
        align: MarkdownAlign,
        textBlock: (MarkdownText) -> MarkdownBlock = { MarkdownBlock.Paragraph(it, align) },
    ): List<MarkdownBlock> = splitStandaloneImages(buildText(), align, textBlock)

    private fun appendElement(element: Element) {
        when (val tag = element.normalName()) {
            in IGNORED_TAGS, "input", "source" -> Unit
            "br" -> lineBreak()
            "img" -> converter.image(element, linkUrl)?.let(::appendImage)
            "picture" -> converter.picture(element, linkUrl)?.let(::appendImage)
            "a" -> appendLink(element)
            "strong", "b" -> styled(MarkdownSpanStyle.Bold) { appendChildren(element) }
            "em", "i", "cite", "dfn", "var" -> styled(MarkdownSpanStyle.Italic) { appendChildren(element) }
            "del", "s", "strike" -> styled(MarkdownSpanStyle.Strikethrough) { appendChildren(element) }
            "ins", "u" -> styled(MarkdownSpanStyle.Underline) { appendChildren(element) }
            "mark" -> styled(MarkdownSpanStyle.Highlight) { appendChildren(element) }
            "sup" -> styled(MarkdownSpanStyle.Superscript) { appendChildren(element) }
            "sub" -> styled(MarkdownSpanStyle.Subscript) { appendChildren(element) }
            "small" -> styled(MarkdownSpanStyle.Small) { appendChildren(element) }
            "code", "tt", "samp" -> inCode(MarkdownSpanStyle.Code, element)
            "kbd" -> inCode(MarkdownSpanStyle.Keyboard, element)
            else -> if (tag in BLOCK_TAGS) {
                // 表格单元格、标题等只能容纳行内内容的位置遇到块级元素时，按独立的一行展开。
                lineBreak()
                appendChildren(element)
                lineBreak()
            } else {
                appendChildren(element)
            }
        }
    }

    private fun appendLink(element: Element) {
        if (element.hasAttr("data-footnote-backref")) return
        val href = element.attr("href").trim()
        // 页内锚点（如脚注编号）在应用内没有可跳转的目标，只保留文字。
        val url = element.absUrl("href").takeIf { !href.startsWith("#") && isLinkUrl(it) }
        if (url == null || linkUrl != null) {
            appendChildren(element)
            return
        }
        if (appendAutolinkedEmail(element, url)) return
        linkUrl = url
        styled(MarkdownSpanStyle.Link(url)) { appendChildren(element) }
        linkUrl = null
    }

    /**
     * 自动识别的邮箱会把紧跟的中文标点也算进地址（如“a@b.com，谢谢”），
     * GitHub 只链接符合邮箱字符规则的部分，其余文字照常显示。
     */
    private fun appendAutolinkedEmail(element: Element, url: String): Boolean {
        val label = element.text()
        if (!url.startsWith("mailto:", ignoreCase = true) || label != url.substring("mailto:".length)) return false
        val address = GITHUB_EMAIL.find(label)?.value ?: return false
        linkUrl = "mailto:$address"
        styled(MarkdownSpanStyle.Link("mailto:$address")) { appendCollapsed(address) }
        linkUrl = null
        appendText(label.substring(address.length))
        return true
    }

    private fun inCode(style: MarkdownSpanStyle, element: Element) {
        codeDepth++
        styled(style) { appendChildren(element) }
        codeDepth--
    }

    private inline fun styled(style: MarkdownSpanStyle, content: () -> Unit) {
        materializePendingSpace()
        val start = text.length
        content()
        if (text.length > start) spans += MarkdownSpan(start, text.length, style)
    }

    private fun appendText(raw: String) {
        if (linkUrl != null || codeDepth > 0) {
            appendCollapsed(raw)
            return
        }
        var cursor = 0
        for (match in GITHUB_REFERENCE.findAll(raw)) {
            val start = match.range.first
            // 正则的后顾只能看到当前文本节点，节点开头还要检查前一个节点留下的字符。
            if (start == 0 && !pendingSpace && text.lastOrNull()?.isReferenceBoundary() == false) continue
            val url = converter.referenceUrl(match.value) ?: continue
            appendCollapsed(raw.substring(cursor, start))
            styled(MarkdownSpanStyle.Link(url)) { appendCollapsed(match.value) }
            cursor = match.range.last + 1
        }
        appendCollapsed(raw.substring(cursor))
    }

    private fun appendCollapsed(raw: String) {
        for (char in raw) {
            when (char) {
                ' ', '\n', '\t', '\r', '\u000C' -> pendingSpace = true
                MarkdownText.INLINE_IMAGE_PLACEHOLDER -> Unit
                else -> {
                    materializePendingSpace()
                    text.append(char)
                }
            }
        }
    }

    private fun materializePendingSpace() {
        if (pendingSpace && text.isNotEmpty() && text.last() != ' ' && text.last() != '\n') {
            text.append(' ')
        }
        pendingSpace = false
    }

    private fun lineBreak() {
        pendingSpace = false
        while (text.lastOrNull() == ' ') text.setLength(text.length - 1)
        // 段首的换行没有可见效果，与浏览器一样忽略。
        if (text.isNotEmpty()) text.append('\n')
    }

    private fun appendImage(image: MarkdownImage) {
        materializePendingSpace()
        images += MarkdownInlineImage(text.length, image)
        text.append(MarkdownText.INLINE_IMAGE_PLACEHOLDER)
    }
}

/**
 * 把独占一行的图片拆成 [MarkdownBlock.Images]：只隔着空格的相邻图片归为一组，
 * 前面是段首或换行、后面是段尾或换行的组才拆出，与浏览器里图片单独成行的效果一致。
 */
private fun splitStandaloneImages(
    content: MarkdownText,
    align: MarkdownAlign,
    textBlock: (MarkdownText) -> MarkdownBlock,
): List<MarkdownBlock> {
    val runs = mutableListOf<MutableList<MarkdownInlineImage>>()
    content.images.forEach { image ->
        val previous = runs.lastOrNull()?.last()
        val onlySpacesBetween = previous != null &&
            content.text.substring(previous.position + 1, image.position).all { it == ' ' }
        if (onlySpacesBetween) runs.last() += image else runs += mutableListOf(image)
    }

    val blocks = mutableListOf<MarkdownBlock>()
    fun emitText(start: Int, end: Int) {
        content.slice(start, end).trimmed().takeUnless { it.isBlank() }?.let { blocks += textBlock(it) }
    }

    var cursor = 0
    for (run in runs) {
        val runStart = run.first().position
        val runEnd = run.last().position + 1
        val before = content.text.substring(cursor, runStart).trimEnd(' ')
        val after = content.text.substring(runEnd).trimStart(' ')
        val standalone = (before.isEmpty() || before.endsWith('\n')) && (after.isEmpty() || after.startsWith('\n'))
        if (!standalone) continue
        emitText(cursor, runStart)
        blocks += MarkdownBlock.Images(run.map { it.image }, align)
        cursor = runEnd
    }
    emitText(cursor, content.text.length)
    return blocks
}

internal fun MarkdownText.slice(start: Int, end: Int): MarkdownText {
    if (start == 0 && end == text.length) return this
    return MarkdownText(
        text = text.substring(start, end),
        spans = spans.mapNotNull { span ->
            val spanStart = maxOf(span.start, start)
            val spanEnd = minOf(span.end, end)
            if (spanEnd > spanStart) span.copy(start = spanStart - start, end = spanEnd - start) else null
        },
        images = images
            .filter { it.position in start until end }
            .map { it.copy(position = it.position - start) },
    )
}

internal fun MarkdownText.trimmed(): MarkdownText {
    var start = 0
    var end = text.length
    while (start < end && (text[start] == ' ' || text[start] == '\n')) start++
    while (end > start && (text[end - 1] == ' ' || text[end - 1] == '\n')) end--
    return slice(start, end)
}

private fun Element.alignment(): MarkdownAlign? {
    val value = attr("align").ifBlank {
        CSS_TEXT_ALIGN.find(attr("style"))?.groupValues?.get(1).orEmpty()
    }
    return when (value.trim().lowercase()) {
        "center" -> MarkdownAlign.Center
        "right", "end" -> MarkdownAlign.End
        "left", "start", "justify" -> MarkdownAlign.Start
        else -> null
    }
}

private fun Element.cssLength(property: String): MarkdownLength? {
    val pattern = Regex("""(?:^|;)\s*$property\s*:\s*([0-9.]+\s*(?:px|%)?)""", RegexOption.IGNORE_CASE)
    return pattern.find(attr("style"))?.groupValues?.get(1)?.let(::parseLength)
}

/** `srcset` 里可能有多张不同密度的图，取第一张即可满足显示需要。 */
private fun Element.firstSrcsetUrl(): String? {
    val candidate = attr("srcset").split(',').firstOrNull()?.trim()?.substringBefore(' ')
    if (candidate.isNullOrEmpty()) return null
    return StringUtil.resolve(baseUri(), candidate).takeIf(::isHttpUrl)
}

private fun parseLength(value: String): MarkdownLength? {
    val normalized = value.trim().lowercase()
    if (normalized.endsWith("%")) {
        return normalized.dropLast(1).trim().toFloatOrNull()
            ?.takeIf { it > 0f }
            ?.let { MarkdownLength.Percent(it.coerceAtMost(100f)) }
    }
    return normalized.removeSuffix("px").trim().toFloatOrNull()
        ?.takeIf { it > 0f }
        ?.let(MarkdownLength::Px)
}

private fun isHttpUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

private fun isLinkUrl(url: String): Boolean = isHttpUrl(url) || url.startsWith("mailto:", ignoreCase = true)

private fun Char.isReferenceBoundary(): Boolean =
    !(this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in "_`/\\@#&")

/** GitHub 用户名由字母、数字和单个连字符组成，最长 39 位；议题编号为纯数字。 */
private val GITHUB_REFERENCE =
    Regex("""(?<![A-Za-z0-9_`/\\@#&])(?:@[A-Za-z0-9](?:-?[A-Za-z0-9]){0,38}|#[0-9]+)(?![A-Za-z0-9_-])""")

/** GitHub 自动链接邮箱时只接受这些字符，域名至少含一个点且不以点结尾。 */
private val GITHUB_EMAIL = Regex("""^[A-Za-z0-9.+_-]+@[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)+""")

private val CSS_TEXT_ALIGN = Regex("""text-align\s*:\s*([a-z-]+)""", RegexOption.IGNORE_CASE)

private val BLOCK_TAGS = setOf(
    "address", "article", "aside", "blockquote", "body", "caption", "center", "dd", "details", "dir",
    "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4",
    "h5", "h6", "header", "hgroup", "hr", "html", "legend", "li", "main", "menu", "nav", "ol", "p",
    "pre", "search", "section", "summary", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "ul",
)

/** 不展示内容的元素：脚本、样式、媒体与表单控件等。 */
private val IGNORED_TAGS = setOf(
    "audio", "base", "button", "canvas", "datalist", "dialog", "embed", "frame", "frameset", "head",
    "iframe", "link", "map", "math", "meta", "noframes", "noscript", "object", "optgroup", "option",
    "script", "select", "style", "svg", "template", "textarea", "title", "track", "video",
)
