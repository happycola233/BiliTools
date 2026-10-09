package com.happycola233.bilitools.ui.markdown

import androidx.compose.runtime.Immutable

/**
 * 解析后的 GitHub 风格 Markdown 文档。
 *
 * 模型只描述结构与语义，不含颜色、字号等主题信息，可以在后台线程构建，也便于单元测试直接断言。
 */
@Immutable
data class MarkdownDocument(val blocks: List<MarkdownBlock>)

enum class MarkdownAlign { Start, Center, End }

@Immutable
sealed interface MarkdownBlock {
    data class Paragraph(val text: MarkdownText, val align: MarkdownAlign = MarkdownAlign.Start) : MarkdownBlock

    data class Heading(
        val level: Int,
        val text: MarkdownText,
        val align: MarkdownAlign = MarkdownAlign.Start,
    ) : MarkdownBlock

    /** 独占一行的一组图片（例如居中的截图、并排的徽章），按图片自身尺寸流式排列。 */
    data class Images(val images: List<MarkdownImage>, val align: MarkdownAlign = MarkdownAlign.Start) : MarkdownBlock

    data class ListBlock(
        val ordered: Boolean,
        val startNumber: Int,
        val items: List<MarkdownListItem>,
    ) : MarkdownBlock

    data class Quote(val blocks: List<MarkdownBlock>) : MarkdownBlock

    /** GitHub 提示块（`> [!NOTE]` 等）；[title] 为空时使用对应类型的本地化标题。 */
    data class Alert(
        val kind: MarkdownAlertKind,
        val title: String?,
        val blocks: List<MarkdownBlock>,
    ) : MarkdownBlock

    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock

    data class Table(val rows: List<MarkdownTableRow>) : MarkdownBlock

    /** `<details>` 折叠块；[summary] 为空时使用默认标题。 */
    data class Details(
        val summary: MarkdownText?,
        val blocks: List<MarkdownBlock>,
        val initiallyExpanded: Boolean,
    ) : MarkdownBlock

    data object Divider : MarkdownBlock
}

/** [taskChecked] 为 null 表示普通列表项，否则为任务列表项的勾选状态。 */
@Immutable
data class MarkdownListItem(val blocks: List<MarkdownBlock>, val taskChecked: Boolean? = null)

@Immutable
data class MarkdownTableRow(val cells: List<MarkdownTableCell>, val isHeader: Boolean)

@Immutable
data class MarkdownTableCell(val text: MarkdownText, val align: MarkdownAlign)

enum class MarkdownAlertKind { Note, Tip, Important, Warning, Caution }

/**
 * 一段行内富文本：纯文本加样式区间。
 *
 * 行内图片在 [text] 中占一个 [INLINE_IMAGE_PLACEHOLDER] 字符，位置记录在 [images] 里，
 * 渲染时替换为内联内容，因此样式区间的偏移量无需再做换算。
 */
@Immutable
data class MarkdownText(
    val text: String,
    val spans: List<MarkdownSpan> = emptyList(),
    val images: List<MarkdownInlineImage> = emptyList(),
) {
    fun isBlank(): Boolean = images.isEmpty() && text.isBlank()

    companion object {
        const val INLINE_IMAGE_PLACEHOLDER = '￼'
    }
}

@Immutable
data class MarkdownSpan(val start: Int, val end: Int, val style: MarkdownSpanStyle)

@Immutable
sealed interface MarkdownSpanStyle {
    data object Bold : MarkdownSpanStyle
    data object Italic : MarkdownSpanStyle
    data object Strikethrough : MarkdownSpanStyle
    data object Underline : MarkdownSpanStyle
    data object Code : MarkdownSpanStyle
    data object Keyboard : MarkdownSpanStyle
    data object Highlight : MarkdownSpanStyle
    data object Superscript : MarkdownSpanStyle
    data object Subscript : MarkdownSpanStyle
    data object Small : MarkdownSpanStyle
    data class Link(val url: String) : MarkdownSpanStyle
}

@Immutable
data class MarkdownInlineImage(val position: Int, val image: MarkdownImage)

/**
 * 图片及其候选来源。
 *
 * [sources] 来自 `<picture>` 的 `<source>`，按文档顺序排列，渲染时取第一个媒体查询命中的来源，
 * 都不命中时回退到 [url]；这样深浅色、窄屏专用的配图可以按当前主题与窗口宽度选择。
 */
@Immutable
data class MarkdownImage(
    val url: String,
    val alt: String? = null,
    val width: MarkdownLength? = null,
    val height: MarkdownLength? = null,
    val link: String? = null,
    val sources: List<MarkdownImageSource> = emptyList(),
)

@Immutable
data class MarkdownImageSource(val media: String?, val url: String)

/** HTML 尺寸属性：像素按 dp 理解（与浏览器的 CSS 像素一致），百分比相对容器宽度。 */
@Immutable
sealed interface MarkdownLength {
    data class Px(val value: Float) : MarkdownLength
    data class Percent(val value: Float) : MarkdownLength
}
