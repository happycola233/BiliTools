package com.happycola233.bilitools.ui.update

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.ReleaseInfo
import com.happycola233.bilitools.ui.theme.rememberAndroidThemeColorScheme

object UpdateDialog {
    private const val HOST_VIEW_TAG = "biltools_update_dialog_host"

    fun show(
        activity: AppCompatActivity,
        release: ReleaseInfo,
        currentVersion: String,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val container = activity.findViewById<ViewGroup>(android.R.id.content)
        container.findViewWithTag<ComposeView>(HOST_VIEW_TAG)?.let(container::removeView)

        val composeView = ComposeView(activity).apply {
            tag = HOST_VIEW_TAG
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
        container.addView(composeView)

        composeView.setContent {
            val colorScheme = rememberAndroidThemeColorScheme()
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            MaterialExpressiveTheme(colorScheme = colorScheme) {
                UpdateDialogHost(
                    release = release,
                    currentVersion = currentVersion,
                    onRemoveHost = {
                        if (composeView.parent === container) {
                            container.removeView(composeView)
                        }
                    },
                    onOpenRelease = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                        runCatching {
                            activity.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.update_dialog_open_release_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateDialogHost(
    release: ReleaseInfo,
    currentVersion: String,
    onRemoveHost: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    if (!isVisible && !sheetState.isVisible) {
        LaunchedEffect(Unit) {
            onRemoveHost()
        }
        return
    }

    if (isVisible || sheetState.isVisible) {
        UpdateBottomSheet(
            sheetState = sheetState,
            release = release,
            currentVersion = currentVersion,
            onDismiss = { isVisible = false },
            onOpenRelease = onOpenRelease,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateBottomSheet(
    sheetState: androidx.compose.material3.SheetState,
    release: ReleaseInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val showFab by remember {
        derivedStateOf {
            sheetState.currentValue != SheetValue.Hidden || sheetState.targetValue != SheetValue.Hidden
        }
    }
    val contentScrollEnabled by remember {
        derivedStateOf {
            sheetState.currentValue == SheetValue.Expanded &&
                sheetState.targetValue == SheetValue.Expanded &&
                !sheetState.isAnimationRunning
        }
    }
    val markdown = remember(release.bodyMarkdown) { release.bodyMarkdown.takeIf { it.isNotBlank() } }

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue != SheetValue.Expanded && scrollState.value != 0) {
            scrollState.scrollTo(0)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(),
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { BottomSheetDefaults.windowInsets },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UpdateSheetHeader(
                    release = release,
                    currentVersion = currentVersion,
                )

                if (markdown != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState, enabled = contentScrollEnabled)
                                .padding(horizontal = 18.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            MarkdownText(text = markdown)
                            Spacer(modifier = Modifier.height(96.dp))
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.update_dialog_notes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onOpenRelease,
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_github_invertocat_black),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    text = { Text(stringResource(R.string.update_dialog_open_release)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSheetHeader(
    release: ReleaseInfo,
    currentVersion: String,
) {
    val currentVersionLabel = buildVersionTag(currentVersion)
    val density = LocalDensity.current
    val stroke = remember(density) {
        Stroke(
            width = with(density) { 3.dp.toPx() },
            cap = StrokeCap.Round,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.update_dialog_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            LinearWavyProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .graphicsLayer(scaleX = -1f),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                stroke = stroke,
                trackStroke = stroke,
                amplitude = { 1f },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentVersionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "->",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = release.tagName,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun buildVersionTag(version: String): String {
    val normalizedVersion = version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .ifBlank { "0" }
    return "v$normalizedVersion"
}

@Composable
private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 20.sp,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEach { block ->
            MarkdownBlockView(
                block = block,
                bodyStyle = bodyStyle,
            )
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    bodyStyle: TextStyle,
) {
    when (block) {
        MarkdownBlock.Divider -> HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )

        is MarkdownBlock.Heading -> {
            val textStyle = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = block.text,
                style = textStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is MarkdownBlock.Paragraph -> MarkdownInlineText(
            inlines = block.inlines,
            style = bodyStyle,
        )

        is MarkdownBlock.ListBlock -> MarkdownListView(
            list = block.list,
            bodyStyle = bodyStyle,
        )

        is MarkdownBlock.Quote -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            MarkdownInlineText(
                inlines = block.inlines,
                style = bodyStyle.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        is MarkdownBlock.CodeFence -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = block.code,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                style = bodyStyle.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
    }
}

@Composable
private fun MarkdownListView(
    list: MarkdownList,
    bodyStyle: TextStyle,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (depth == 0) 10.dp else 8.dp),
    ) {
        list.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = list.markerText(index, item),
                    style = bodyStyle.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.padding(end = 10.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MarkdownInlineText(
                        inlines = item.inlines,
                        style = bodyStyle,
                    )
                    item.children.forEach { child ->
                        MarkdownListView(
                            list = child,
                            bodyStyle = bodyStyle,
                            modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                            depth = depth + 1,
                        )
                    }
                }
            }
        }
    }
}

private fun MarkdownList.markerText(index: Int, item: MarkdownListItem): String {
    return when (this) {
        is MarkdownList.Bullet -> "•"
        is MarkdownList.Ordered -> "${item.ordinal ?: index + 1}."
    }
}

@Composable
private fun MarkdownInlineText(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val annotatedText = remember(inlines, linkColor, inlineCodeBackground) {
        buildMarkdownAnnotatedString(
            inlines = inlines,
            linkColor = linkColor,
            inlineCodeBackground = inlineCodeBackground,
        )
    }

    Text(
        text = annotatedText,
        modifier = modifier,
        style = style,
    )
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.isBlank()) {
            index += 1
            continue
        }

        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim().ifBlank { null }
            index += 1
            val codeLines = mutableListOf<String>()
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                codeLines += lines[index]
                index += 1
            }
            if (index < lines.size) {
                index += 1
            }
            blocks += MarkdownBlock.CodeFence(
                language = language,
                code = codeLines.joinToString("\n").trimEnd(),
            )
            continue
        }

        if (isMarkdownDivider(trimmed)) {
            blocks += MarkdownBlock.Divider
            index += 1
            continue
        }

        val headingLevel = trimmed.takeWhile { it == '#' }.length
        if (headingLevel in 1..6 && trimmed.getOrNull(headingLevel) == ' ') {
            blocks += MarkdownBlock.Heading(
                level = headingLevel,
                text = trimmed.drop(headingLevel + 1).trim(),
            )
            index += 1
            continue
        }

        if (parseMarkdownListMarker(line) != null) {
            val (list, nextIndex) = parseMarkdownList(lines, index)
            blocks += MarkdownBlock.ListBlock(list)
            index = nextIndex
            continue
        }

        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size) {
                val quoteLine = lines[index].trim()
                if (!quoteLine.startsWith(">")) break
                quoteLines += quoteLine.removePrefix(">").trimStart()
                index += 1
            }
            blocks += MarkdownBlock.Quote(parseMarkdownInlines(quoteLines.joinToString("\n")))
            continue
        }

        val paragraphLines = mutableListOf<String>()
        while (index < lines.size) {
            val paragraphLine = lines[index]
            val paragraphTrimmed = paragraphLine.trim()
            val paragraphHeadingLevel = paragraphTrimmed.takeWhile { it == '#' }.length
            if (paragraphTrimmed.isBlank() ||
                paragraphTrimmed.startsWith("```") ||
                isMarkdownDivider(paragraphTrimmed) ||
                (paragraphHeadingLevel in 1..6 && paragraphTrimmed.getOrNull(paragraphHeadingLevel) == ' ') ||
                parseMarkdownListMarker(paragraphLine) != null ||
                paragraphTrimmed.startsWith(">")
            ) {
                break
            }
            paragraphLines += paragraphLine.trimEnd()
            index += 1
        }
        blocks += MarkdownBlock.Paragraph(parseMarkdownInlines(paragraphLines.joinToString("\n")))
    }

    return blocks
}

private fun buildMarkdownAnnotatedString(
    inlines: List<MarkdownInline>,
    linkColor: Color,
    inlineCodeBackground: Color,
): AnnotatedString {
    return buildAnnotatedString {
        appendMarkdownInlines(
            inlines = inlines,
            linkColor = linkColor,
            inlineCodeBackground = inlineCodeBackground,
        )
    }
}

private fun AnnotatedString.Builder.appendMarkdownInlines(
    inlines: List<MarkdownInline>,
    linkColor: Color,
    inlineCodeBackground: Color,
) {
    inlines.forEach { inline ->
        when (inline) {
            is MarkdownInline.Text -> append(inline.text)

            is MarkdownInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendMarkdownInlines(inline.children, linkColor, inlineCodeBackground)
            }

            is MarkdownInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendMarkdownInlines(inline.children, linkColor, inlineCodeBackground)
            }

            is MarkdownInline.Code -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = inlineCodeBackground,
                ),
            ) {
                append(inline.text)
            }

            is MarkdownInline.Link -> withLink(
                LinkAnnotation.Url(
                    url = inline.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            fontWeight = if (inline.isMention) FontWeight.Bold else null,
                        ),
                    ),
                ),
            ) {
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        fontWeight = if (inline.isMention) FontWeight.Bold else null,
                    ),
                ) {
                    appendMarkdownInlines(inline.children, linkColor, inlineCodeBackground)
                }
            }
        }
    }
}

private fun parseMarkdownInlines(text: String): List<MarkdownInline> {
    val result = mutableListOf<MarkdownInline>()
    val plainText = StringBuilder()
    var index = 0

    fun flushPlainText() {
        if (plainText.isNotEmpty()) {
            result += MarkdownInline.Text(plainText.toString())
            plainText.clear()
        }
    }

    while (index < text.length) {
        when {
            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val delimiter = text.substring(index, index + 2)
                val endIndex = text.indexOf(delimiter, startIndex = index + 2)
                if (endIndex > index + 2) {
                    flushPlainText()
                    result += MarkdownInline.Bold(
                        parseMarkdownInlines(text.substring(index + 2, endIndex)),
                    )
                    index = endIndex + 2
                } else {
                    plainText.append(delimiter)
                    index += 2
                }
            }

            text[index] == '*' || text[index] == '_' -> {
                val delimiter = text[index]
                val endIndex = text.indexOf(delimiter, startIndex = index + 1)
                if (endIndex > index + 1) {
                    flushPlainText()
                    result += MarkdownInline.Italic(
                        parseMarkdownInlines(text.substring(index + 1, endIndex)),
                    )
                    index = endIndex + 1
                } else {
                    plainText.append(delimiter)
                    index += 1
                }
            }

            text[index] == '`' -> {
                val endIndex = text.indexOf('`', startIndex = index + 1)
                if (endIndex > index + 1) {
                    flushPlainText()
                    result += MarkdownInline.Code(text.substring(index + 1, endIndex))
                    index = endIndex + 1
                } else {
                    plainText.append('`')
                    index += 1
                }
            }

            text[index] == '[' -> {
                val labelEnd = text.indexOf(']', startIndex = index + 1)
                val hasUrlStart = text.getOrNull(labelEnd + 1) == '('
                val urlEnd = if (hasUrlStart) text.indexOf(')', startIndex = labelEnd + 2) else -1
                if (labelEnd > index && urlEnd > labelEnd + 2) {
                    flushPlainText()
                    result += MarkdownInline.Link(
                        children = parseMarkdownInlines(text.substring(index + 1, labelEnd)),
                        url = text.substring(labelEnd + 2, urlEnd).trim(),
                    )
                    index = urlEnd + 1
                } else {
                    plainText.append('[')
                    index += 1
                }
            }

            text.startsWith("https://", index) || text.startsWith("http://", index) -> {
                val urlEnd = findUrlEnd(text, index)
                flushPlainText()
                val url = text.substring(index, urlEnd)
                result += MarkdownInline.Link(
                    children = listOf(MarkdownInline.Text(url)),
                    url = url,
                )
                index = urlEnd
            }

            text[index] == '@' -> {
                val mentionEnd = findGithubMentionEnd(text, index + 1)
                val standaloneMention = mentionEnd > index + 1 &&
                    !isMentionContinuation(text.getOrNull(index - 1))
                if (standaloneMention) {
                    flushPlainText()
                    val username = text.substring(index + 1, mentionEnd)
                    result += MarkdownInline.Link(
                        children = listOf(MarkdownInline.Text("@$username")),
                        url = "https://github.com/$username",
                        isMention = true,
                    )
                    index = mentionEnd
                } else {
                    plainText.append('@')
                    index += 1
                }
            }

            else -> {
                plainText.append(text[index])
                index += 1
            }
        }
    }

    flushPlainText()
    return result
}

private fun parseMarkdownList(
    lines: List<String>,
    startIndex: Int,
): Pair<MarkdownList, Int> {
    val firstMarker = parseMarkdownListMarker(lines[startIndex])
        ?: error("Expected markdown list marker at line $startIndex")
    val items = mutableListOf<MarkdownListItem>()
    var index = startIndex

    while (index < lines.size) {
        val marker = parseMarkdownListMarker(lines[index]) ?: break
        if (marker.indent != firstMarker.indent || marker.type != firstMarker.type) break

        val contentLines = mutableListOf(marker.content)
        val children = mutableListOf<MarkdownList>()
        var itemIndex = index + 1
        var pendingBlankLine = false

        while (itemIndex < lines.size) {
            val currentLine = lines[itemIndex]
            val currentTrimmed = currentLine.trim()

            if (currentTrimmed.isBlank()) {
                pendingBlankLine = contentLines.isNotEmpty()
                itemIndex += 1
                continue
            }

            val currentMarker = parseMarkdownListMarker(currentLine)
            if (currentMarker != null) {
                when {
                    currentMarker.indent < firstMarker.indent -> break
                    currentMarker.indent == firstMarker.indent -> break
                    currentMarker.indent > firstMarker.indent -> {
                        val (childList, nextIndex) = parseMarkdownList(lines, itemIndex)
                        children += childList
                        itemIndex = nextIndex
                        pendingBlankLine = false
                        continue
                    }
                }
            }

            val lineIndent = countMarkdownIndent(currentLine)
            if (lineIndent > firstMarker.indent) {
                if (pendingBlankLine && contentLines.isNotEmpty()) {
                    contentLines += ""
                }
                contentLines += currentTrimmed
                itemIndex += 1
                pendingBlankLine = false
                continue
            }

            break
        }

        items += MarkdownListItem(
            inlines = parseMarkdownInlines(contentLines.joinToString("\n").trim()),
            ordinal = marker.ordinal,
            children = children,
        )
        index = itemIndex
    }

    val list = when (firstMarker.type) {
        MarkdownListType.Bullet -> MarkdownList.Bullet(items)
        MarkdownListType.Ordered -> MarkdownList.Ordered(items)
    }
    return list to index
}

private fun findUrlEnd(text: String, startIndex: Int): Int {
    var endIndex = startIndex
    while (endIndex < text.length && !text[endIndex].isWhitespace()) {
        endIndex += 1
    }
    while (endIndex > startIndex && text[endIndex - 1] in MarkdownTrailingUrlPunctuation) {
        endIndex -= 1
    }
    return endIndex
}

private fun findGithubMentionEnd(text: String, startIndex: Int): Int {
    var endIndex = startIndex
    while (endIndex < text.length && isGithubMentionChar(text[endIndex])) {
        endIndex += 1
    }
    return endIndex
}

private fun isGithubMentionChar(char: Char): Boolean {
    return char.isLetterOrDigit() || char == '_' || char == '-'
}

private fun isMentionContinuation(char: Char?): Boolean {
    return char?.let {
        it.isLetterOrDigit() || it == '_' || it == '`' || it == '['
    } == true
}

private fun isMarkdownDivider(line: String): Boolean {
    if (line.length < 3) return false
    val dividerChar = line.first()
    if (dividerChar != '-' && dividerChar != '*' && dividerChar != '_') return false
    return line.all { it == dividerChar }
}

private fun countMarkdownIndent(line: String): Int {
    var indent = 0
    line.forEach { char ->
        when (char) {
            ' ' -> indent += 1
            '\t' -> indent += 4
            else -> return indent
        }
    }
    return indent
}

private fun parseMarkdownListMarker(line: String): MarkdownListMarker? {
    val indent = countMarkdownIndent(line)
    var index = 0
    while (index < line.length && (line[index] == ' ' || line[index] == '\t')) {
        index += 1
    }

    when (line.getOrNull(index)) {
        '-', '*', '+' -> {
            if (line.getOrNull(index + 1) != ' ') return null
            return MarkdownListMarker(
                indent = indent,
                type = MarkdownListType.Bullet,
                content = line.substring(index + 2).trim(),
            )
        }
    }

    var numberEnd = index
    while (numberEnd < line.length && line[numberEnd].isDigit()) {
        numberEnd += 1
    }
    if (numberEnd == index || line.getOrNull(numberEnd) != '.' || line.getOrNull(numberEnd + 1) != ' ') {
        return null
    }

    return MarkdownListMarker(
        indent = indent,
        type = MarkdownListType.Ordered,
        content = line.substring(numberEnd + 2).trim(),
        ordinal = line.substring(index, numberEnd).toIntOrNull(),
    )
}

private sealed interface MarkdownBlock {
    data object Divider : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock
    data class ListBlock(val list: MarkdownList) : MarkdownBlock
    data class Quote(val inlines: List<MarkdownInline>) : MarkdownBlock
    data class CodeFence(val language: String?, val code: String) : MarkdownBlock
}

private enum class MarkdownListType {
    Bullet,
    Ordered,
}

private data class MarkdownListMarker(
    val indent: Int,
    val type: MarkdownListType,
    val content: String,
    val ordinal: Int? = null,
)

private data class MarkdownListItem(
    val inlines: List<MarkdownInline>,
    val ordinal: Int? = null,
    val children: List<MarkdownList> = emptyList(),
)

private sealed interface MarkdownList {
    val items: List<MarkdownListItem>

    data class Bullet(override val items: List<MarkdownListItem>) : MarkdownList

    data class Ordered(override val items: List<MarkdownListItem>) : MarkdownList
}

private sealed interface MarkdownInline {
    data class Text(val text: String) : MarkdownInline
    data class Bold(val children: List<MarkdownInline>) : MarkdownInline
    data class Italic(val children: List<MarkdownInline>) : MarkdownInline
    data class Code(val text: String) : MarkdownInline
    data class Link(
        val children: List<MarkdownInline>,
        val url: String,
        val isMention: Boolean = false,
    ) : MarkdownInline
}

private val MarkdownTrailingUrlPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')')
