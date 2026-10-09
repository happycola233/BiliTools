package com.happycola233.bilitools.ui.markdown

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLog
import com.happycola233.bilitools.ui.theme.AppSurfaces

/**
 * 渲染 [MarkdownDocument]：块之间留出统一间距，长代码与宽表格在各自区域内横向滚动，不会撑破容器。
 *
 * @param imageUrlCandidates 把图片地址展开为按优先级排列的候选地址，加载失败时依次尝试。
 */
@Composable
fun MarkdownContent(
    document: MarkdownDocument,
    modifier: Modifier = Modifier,
    imageUrlCandidates: (String) -> List<String> = { listOf(it) },
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val inlineCodeBackground = AppSurfaces.insetContainerColor
    val scope = remember(colors, typography, inlineCodeBackground, imageUrlCandidates, uriHandler) {
        MarkdownRenderScope(
            bodyStyle = typography.bodyMedium,
            linkStyles = TextLinkStyles(
                style = SpanStyle(color = colors.primary),
                pressedStyle = SpanStyle(color = colors.primary, background = colors.primary.copy(alpha = 0.12f)),
            ),
            codeStyle = SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 0.9.em,
                background = inlineCodeBackground,
            ),
            highlightStyle = SpanStyle(
                color = colors.onTertiaryContainer,
                background = colors.tertiaryContainer,
            ),
            imageUrlCandidates = imageUrlCandidates,
            openLink = { url ->
                try {
                    uriHandler.openUri(url)
                } catch (error: IllegalArgumentException) {
                    AppLog.w("Markdown", "[link] open failed", error)
                    Toast.makeText(context, R.string.common_open_link_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    scope.Blocks(document.blocks, modifier, spacing = 12.dp)
}

@Immutable
internal class MarkdownRenderScope(
    val bodyStyle: TextStyle,
    val linkStyles: TextLinkStyles,
    val codeStyle: SpanStyle,
    val highlightStyle: SpanStyle,
    val imageUrlCandidates: (String) -> List<String>,
    val openLink: (String) -> Unit,
)

@Composable
private fun MarkdownRenderScope.Blocks(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    listDepth: Int = 0,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        blocks.forEachIndexed { index, block ->
            // 章节标题与上一段拉开距离，便于快速扫读分区。
            val sectionGap = index > 0 && block is MarkdownBlock.Heading && block.level <= 3
            Block(block, if (sectionGap) Modifier.padding(top = 8.dp) else Modifier, listDepth)
        }
    }
}

@Composable
private fun MarkdownRenderScope.Block(block: MarkdownBlock, modifier: Modifier, listDepth: Int) {
    when (block) {
        is MarkdownBlock.Paragraph -> RichText(block.text, bodyStyle, block.align, modifier)
        is MarkdownBlock.Heading -> RichText(
            text = block.text,
            style = headingStyle(block.level),
            align = block.align,
            modifier = modifier.semantics { heading() },
        )
        is MarkdownBlock.Images -> MarkdownImageRow(block, modifier)
        is MarkdownBlock.ListBlock -> ListView(block, listDepth, modifier)
        is MarkdownBlock.Quote -> AccentBarColumn(MaterialTheme.colorScheme.outlineVariant, modifier) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Blocks(block.blocks, listDepth = listDepth)
            }
        }
        is MarkdownBlock.Alert -> AlertView(block, listDepth, modifier)
        is MarkdownBlock.CodeBlock -> CodeBlockView(block, modifier)
        is MarkdownBlock.Table -> TableView(block, modifier)
        is MarkdownBlock.Details -> DetailsView(block, listDepth, modifier)
        MarkdownBlock.Divider -> HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = with(MaterialTheme.typography) {
    when (level) {
        1 -> headlineSmall
        2 -> titleLarge
        3 -> titleMedium
        4 -> titleSmall
        5 -> labelLarge
        else -> labelMedium
    }
}.copy(fontWeight = FontWeight.Bold)

@Composable
internal fun MarkdownRenderScope.RichText(
    text: MarkdownText,
    style: TextStyle,
    align: MarkdownAlign,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text, this) { annotate(text) }
    Text(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = style,
        textAlign = align.textAlign,
        inlineContent = markdownInlineImages(text),
    )
}

private fun MarkdownRenderScope.annotate(content: MarkdownText): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    content.images.forEachIndexed { index, image ->
        append(content.text, cursor, image.position)
        // 占位文字与模型里的占位符同为一个字符，样式区间的偏移量因此保持不变。
        appendInlineContent(inlineImageId(index), MarkdownText.INLINE_IMAGE_PLACEHOLDER.toString())
        cursor = image.position + 1
    }
    append(content.text, cursor, content.text.length)
    content.spans.forEach { span ->
        when (val style = span.style) {
            is MarkdownSpanStyle.Link -> addLink(
                LinkAnnotation.Url(style.url, linkStyles) { openLink(style.url) },
                span.start,
                span.end,
            )
            else -> addStyle(spanStyleOf(style), span.start, span.end)
        }
    }
}

private fun MarkdownRenderScope.spanStyleOf(style: MarkdownSpanStyle): SpanStyle = when (style) {
    MarkdownSpanStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    MarkdownSpanStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    MarkdownSpanStyle.Strikethrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    MarkdownSpanStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    MarkdownSpanStyle.Code, MarkdownSpanStyle.Keyboard -> codeStyle
    MarkdownSpanStyle.Highlight -> highlightStyle
    MarkdownSpanStyle.Superscript -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em)
    MarkdownSpanStyle.Subscript -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)
    MarkdownSpanStyle.Small -> SpanStyle(fontSize = 0.85.em)
    is MarkdownSpanStyle.Link -> SpanStyle()
}

internal fun inlineImageId(index: Int): String = "markdown-image-$index"

private val MarkdownAlign.textAlign: TextAlign
    get() = when (this) {
        MarkdownAlign.Start -> TextAlign.Start
        MarkdownAlign.Center -> TextAlign.Center
        MarkdownAlign.End -> TextAlign.End
    }

@Composable
private fun MarkdownRenderScope.ListView(block: MarkdownBlock.ListBlock, depth: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        block.items.forEachIndexed { index, item ->
            Row {
                ListMarker(block, index, item.taskChecked, depth)
                Blocks(item.blocks, Modifier.weight(1f), spacing = 6.dp, listDepth = depth + 1)
            }
        }
    }
}

@Composable
private fun MarkdownRenderScope.ListMarker(
    block: MarkdownBlock.ListBlock,
    index: Int,
    taskChecked: Boolean?,
    depth: Int,
) {
    if (taskChecked != null) {
        Icon(
            painter = painterResource(
                if (taskChecked) R.drawable.ic_check_box_24 else R.drawable.ic_check_box_outline_blank_24,
            ),
            contentDescription = stringResource(
                if (taskChecked) R.string.markdown_task_done else R.string.markdown_task_todo,
            ),
            tint = if (taskChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp, end = 8.dp).size(18.dp),
        )
        return
    }
    val marker = when {
        block.ordered -> "${block.startNumber + index}."
        depth == 0 -> "•"
        depth == 1 -> "◦"
        else -> "▪"
    }
    Text(
        text = marker,
        style = bodyStyle,
        textAlign = if (block.ordered) TextAlign.End else TextAlign.Center,
        modifier = Modifier.widthIn(min = 16.dp).padding(end = 8.dp),
    )
}

/** 在内容起始侧画一条竖向强调线；不用固有尺寸测量，内容里有图片等子组合布局时也能安全使用。 */
@Composable
private fun AccentBarColumn(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val barWidth = 3.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - barWidth else 0f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
            .padding(start = 15.dp),
    ) {
        content()
    }
}

@Composable
private fun MarkdownRenderScope.AlertView(block: MarkdownBlock.Alert, listDepth: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    // Material 3 没有“成功/警示”色角色，警告与危险共用错误色，靠图标区分。
    val (accent, icon, defaultTitle) = when (block.kind) {
        MarkdownAlertKind.Note -> Triple(colors.primary, R.drawable.ic_info_24, R.string.markdown_alert_note)
        MarkdownAlertKind.Tip -> Triple(colors.tertiary, R.drawable.ic_lightbulb_24, R.string.markdown_alert_tip)
        MarkdownAlertKind.Important -> Triple(colors.secondary, R.drawable.ic_feedback_24, R.string.markdown_alert_important)
        MarkdownAlertKind.Warning -> Triple(colors.error, R.drawable.ic_warning_24, R.string.markdown_alert_warning)
        MarkdownAlertKind.Caution -> Triple(colors.error, R.drawable.ic_report_24, R.string.markdown_alert_caution)
    }
    AccentBarColumn(accent, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = block.title ?: stringResource(defaultTitle),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Blocks(block.blocks, listDepth = listDepth)
        }
    }
}

@Composable
private fun CodeBlockView(block: MarkdownBlock.CodeBlock, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppSurfaces.insetContainerColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        // 代码固定从左到右排版，阿拉伯语界面下也不会颠倒缩进与符号。
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun MarkdownRenderScope.TableView(block: MarkdownBlock.Table, modifier: Modifier) {
    val columnCount = block.rows.maxOf { it.cells.size }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = AppSurfaces.insetContainerColor
    Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            border = BorderStroke(1.dp, lineColor),
        ) {
            TableLayout(columnCount) {
                block.rows.forEachIndexed { rowIndex, row ->
                    repeat(columnCount) { columnIndex ->
                        val cell = row.cells.getOrNull(columnIndex)
                        Box(
                            Modifier
                                .then(if (row.isHeader) Modifier.background(headerBackground) else Modifier)
                                .drawBehind {
                                    val stroke = 1.dp.toPx()
                                    if (rowIndex > 0) drawRect(lineColor, size = Size(size.width, stroke))
                                    if (columnIndex > 0) {
                                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - stroke else 0f
                                        drawRect(lineColor, topLeft = Offset(x, 0f), size = Size(stroke, size.height))
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            if (cell != null) {
                                RichText(
                                    text = cell.text,
                                    style = if (row.isHeader) bodyStyle.copy(fontWeight = FontWeight.Bold) else bodyStyle,
                                    align = cell.align,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 按内容测量列宽（限制在合理范围内，过长的文字换行），同一行的单元格等高，以便网格线与表头底色连贯。 */
@Composable
private fun TableLayout(columnCount: Int, content: @Composable () -> Unit) {
    Layout(content) { measurables, _ ->
        val rows = measurables.chunked(columnCount)
        val minColumnWidth = 48.dp.roundToPx()
        val maxColumnWidth = 280.dp.roundToPx()
        val columnWidths = IntArray(columnCount) { column ->
            rows.maxOf { it[column].maxIntrinsicWidth(Constraints.Infinity) }
                .coerceIn(minColumnWidth, maxColumnWidth)
        }
        val rowHeights = rows.map { row ->
            row.withIndex().maxOf { (column, cell) -> cell.minIntrinsicHeight(columnWidths[column]) }
        }
        val placeables = rows.mapIndexed { rowIndex, row ->
            row.mapIndexed { column, cell ->
                cell.measure(Constraints.fixed(columnWidths[column], rowHeights[rowIndex]))
            }
        }
        layout(columnWidths.sum(), rowHeights.sum()) {
            var y = 0
            placeables.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEachIndexed { column, placeable ->
                    placeable.placeRelative(x, y)
                    x += columnWidths[column]
                }
                y += rowHeights[rowIndex]
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MarkdownRenderScope.DetailsView(block: MarkdownBlock.Details, listDepth: Int, modifier: Modifier) {
    var expanded by rememberSaveable(block) { mutableStateOf(block.initiallyExpanded) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spatialSpec,
        label = "markdownDetailsArrow",
    )
    val stateText = stringResource(
        if (expanded) R.string.markdown_details_expanded else R.string.markdown_details_collapsed,
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppSurfaces.insetContainerColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = stateText }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    val summaryStyle = bodyStyle.copy(fontWeight = FontWeight.Bold)
                    if (block.summary != null) {
                        RichText(block.summary, summaryStyle, MarkdownAlign.Start)
                    } else {
                        Text(stringResource(R.string.markdown_details_summary), style = summaryStyle)
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
            if (expanded) {
                Blocks(
                    blocks = block.blocks,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    listDepth = listDepth,
                )
            }
        }
    }
}
