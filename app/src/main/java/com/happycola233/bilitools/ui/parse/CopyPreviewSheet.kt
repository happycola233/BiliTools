package com.happycola233.bilitools.ui.parse

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.LazyListScrollbar
import com.happycola233.bilitools.ui.rememberContainedScroll
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.markdown.GithubMarkdownParser
import com.happycola233.bilitools.ui.markdown.MarkdownBlock
import com.happycola233.bilitools.ui.markdown.MarkdownContent
import com.happycola233.bilitools.ui.markdown.MarkdownDocument
import com.happycola233.bilitools.ui.theme.AppAccents
import com.happycola233.bilitools.ui.theme.AppSurfaces
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val copyPreviewHorizontalPadding = 16.dp
private val copyPreviewTabMaxWidth = 220.dp
private val copyPreviewBodyPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp)
private const val copyPreviewSheetHeightFraction = 0.85f

/** 横屏、分屏等可用高度较小时，面板至少取这么高（不超过可用高度），给预览卡片留出阅读空间。 */
private val copyPreviewSheetMinHeight = 480.dp
private const val copiedFeedbackDurationMillis = 1_500L

/** 复制预览条目的标题按「 - 」分段，例如「稿件标题 - P2」「番剧名 - 第 3 话」。 */
private const val copyEntryTitleSeparator = " - "

/** 字幕与 AI 总结共用的预览条目；[content] 不为空即可复制。 */
private class CopyPreviewItem(
    val tabLabel: String,
    val title: String,
    val supportingText: String?,
    val content: String?,
    val unavailableMessage: String,
)

@Composable
internal fun SubtitleCopyPreviewSheet(
    entries: List<SubtitleCopyEntry>,
    onDismiss: () -> Unit,
    onCopyCurrent: (SubtitleCopyEntry) -> Unit,
    onCopyAll: (List<SubtitleCopyEntry>) -> Unit,
) {
    val unavailableMessage = stringResource(R.string.parse_subtitle_copy_unavailable)
    val items = remember(entries, unavailableMessage) {
        val titleParts = distinctTitleParts(entries.map { it.title })
        entries.mapIndexed { index, entry ->
            val language = entry.subtitleName?.takeIf { it.isNotBlank() }
            CopyPreviewItem(
                tabLabel = listOfNotNull(titleParts[index], language).joinToString(" · ")
                    .ifEmpty { entry.title },
                title = entry.title,
                supportingText = language,
                content = entry.content?.takeIf { it.isNotBlank() },
                unavailableMessage = entry.error ?: unavailableMessage,
            )
        }
    }
    CopyPreviewSheet(
        title = stringResource(R.string.parse_copy_subtitle_now),
        items = items,
        emptyIconRes = R.drawable.ic_subtitles_24,
        copyCurrentLabel = stringResource(R.string.parse_subtitle_copy_current),
        copyAllLabel = stringResource(R.string.parse_subtitle_copy_all),
        onDismiss = onDismiss,
        onCopyCurrent = { index -> onCopyCurrent(entries[index]) },
        onCopyAll = { onCopyAll(entries) },
    ) { content, modifier ->
        SubtitleCuePreview(srt = content, modifier = modifier)
    }
}

@Composable
internal fun AiSummaryCopyPreviewSheet(
    entries: List<AiSummaryCopyEntry>,
    onDismiss: () -> Unit,
    onCopyCurrent: (AiSummaryCopyEntry) -> Unit,
    onCopyAll: (List<AiSummaryCopyEntry>) -> Unit,
) {
    val unavailableMessage = stringResource(R.string.parse_ai_summary_copy_unavailable)
    val items = remember(entries, unavailableMessage) {
        val titleParts = distinctTitleParts(entries.map { it.title })
        entries.mapIndexed { index, entry ->
            CopyPreviewItem(
                tabLabel = titleParts[index] ?: entry.title,
                title = entry.title,
                supportingText = null,
                content = entry.content?.takeIf { it.isNotBlank() },
                unavailableMessage = entry.error ?: unavailableMessage,
            )
        }
    }
    CopyPreviewSheet(
        title = stringResource(R.string.parse_copy_ai_summary_now),
        items = items,
        emptyIconRes = R.drawable.ic_article_24,
        copyCurrentLabel = stringResource(R.string.parse_ai_summary_copy_current),
        copyAllLabel = stringResource(R.string.parse_ai_summary_copy_all),
        onDismiss = onDismiss,
        onCopyCurrent = { index -> onCopyCurrent(entries[index]) },
        onCopyAll = { onCopyAll(entries) },
    ) { content, modifier ->
        AiSummaryMarkdownPreview(markdown = content, modifier = modifier)
    }
}

/**
 * 顶部标签与卡片联动：点标签或左右滑动卡片切换条目；卡片右上角复制当前条目，底部复制全部。
 *
 * @param preview 渲染可复制正文，需把传入的 modifier 用在根布局上以填满卡片剩余空间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyPreviewSheet(
    title: String,
    items: List<CopyPreviewItem>,
    @DrawableRes emptyIconRes: Int,
    copyCurrentLabel: String,
    copyAllLabel: String,
    onDismiss: () -> Unit,
    onCopyCurrent: (index: Int) -> Unit,
    onCopyAll: () -> Unit,
    preview: @Composable (content: String, modifier: Modifier) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val availableCount = items.count { it.content != null }
    // 优先打开第一个有内容的条目，避免一打开就是空白提示。
    val pagerState = rememberPagerState(
        initialPage = items.indexOfFirst { it.content != null }.coerceAtLeast(0),
    ) { items.size }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurfaces.pageContainerColor,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 各条目长短不一，面板固定高度，左右切换时不会忽高忽低。
            val sheetHeight = (maxHeight * copyPreviewSheetHeightFraction)
                .coerceAtLeast(minOf(maxHeight, copyPreviewSheetMinHeight))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
            ) {
                CopyPreviewHeader(
                    title = title,
                    availableCount = availableCount,
                    totalCount = items.size,
                )
                CopyPreviewTabs(items = items, pagerState = pagerState)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = copyPreviewHorizontalPadding),
                    // 页间距不小于两侧留白，相邻卡片才不会从屏幕边缘露出一条。
                    pageSpacing = copyPreviewHorizontalPadding,
                ) { page ->
                    CopyPreviewCard(
                        item = items[page],
                        emptyIconRes = emptyIconRes,
                        copyLabel = copyCurrentLabel,
                        onCopy = { onCopyCurrent(page) },
                        preview = preview,
                    )
                }
                CopyAllButton(
                    text = copyAllLabel,
                    enabled = availableCount > 0,
                    onClick = {
                        onCopyAll()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                )
            }
        }
    }
}

@Composable
private fun CopyPreviewHeader(
    title: String,
    availableCount: Int,
    totalCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.parse_copy_preview_available_count, availableCount, totalCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CopyPreviewTabs(
    items: List<CopyPreviewItem>,
    pagerState: PagerState,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = pagerState.currentPage)
    // 无论点标签还是滑卡片，都把当前标签移到中间，前后条目一眼可见；
    // 连续快速切换时放弃未播完的居中动画，直接追最新一页，标签栏不会落后排队。
    LaunchedEffect(pagerState, listState) {
        snapshotFlow { pagerState.targetPage }.collectLatest { page -> listState.animateScrollToCenter(page) }
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = copyPreviewHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items) { index, item ->
            val available = item.content != null
            ToggleButton(
                checked = index == pagerState.targetPage,
                onCheckedChange = {
                    haptics.select()
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                // 未选中的标签浮在页面底色上，取卡片色才分得开；没有内容的条目文字降一级。
                colors = AppAccents.toggleButtonColors(
                    containerColor = AppSurfaces.cardContainerColor,
                    contentColor = if (available) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
                modifier = Modifier
                    .widthIn(max = copyPreviewTabMaxWidth)
                    .semantics {
                        role = Role.Tab
                        if (!available) stateDescription = item.unavailableMessage
                    },
            ) {
                Text(
                    text = item.tabLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 把指定条目滚到可视区域中间；条目不在屏幕上时先跳过去再居中，尚未完成布局时不处理。 */
private suspend fun LazyListState.animateScrollToCenter(index: Int) {
    val target = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: run {
            scrollToItem(index)
            layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }
        ?: return
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    animateScrollBy((target.offset + target.size / 2 - viewportCenter).toFloat())
}

@Composable
private fun CopyPreviewCard(
    item: CopyPreviewItem,
    @DrawableRes emptyIconRes: Int,
    copyLabel: String,
    onCopy: () -> Unit,
    preview: @Composable (content: String, modifier: Modifier) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = AppSurfaces.cardContainerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 4.dp)
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.supportingText?.let { supportingText ->
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.content != null) {
                CopyCurrentButton(label = copyLabel, onCopy = onCopy)
            }
        }
        val bodyModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        if (item.content != null) {
            preview(item.content, bodyModifier)
        } else {
            CopyPreviewEmptyState(
                message = item.unavailableMessage,
                iconRes = emptyIconRes,
                modifier = bodyModifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CopyCurrentButton(
    label: String,
    onCopy: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val motionScheme = MaterialTheme.motionScheme
    var copied by remember { mutableStateOf(false) }
    // 复制后短暂换成对勾，确认这一项已进入剪贴板。
    LaunchedEffect(copied) {
        if (copied) {
            delay(copiedFeedbackDurationMillis)
            copied = false
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        FilledTonalIconButton(
            onClick = {
                haptics.confirm()
                onCopy()
                copied = true
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            AnimatedContent(
                targetState = copied,
                transitionSpec = {
                    (fadeIn(motionScheme.fastEffectsSpec()) +
                        scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                        fadeOut(motionScheme.fastEffectsSpec())
                },
                label = "CopyCurrentFeedback",
            ) { done ->
                Icon(
                    painter = painterResource(
                        if (done) R.drawable.ic_check_rounded_24 else R.drawable.ic_content_copy_24,
                    ),
                    contentDescription = label,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CopyPreviewEmptyState(
    message: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = AppSurfaces.insetContainerColor,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CopyAllButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val buttonHeight = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = {
            haptics.confirm()
            onClick()
        },
        enabled = enabled,
        shapes = ButtonDefaults.shapesFor(buttonHeight),
        colors = AppAccents.filledButtonColors(),
        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .heightIn(min = buttonHeight),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_content_copy_24),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
        Text(
            text = text,
            style = ButtonDefaults.textStyleFor(buttonHeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 字幕逐句排成「开始时间 + 台词」；时间不参与文字选择，选中复制的只有台词。
 * 滚到顶或底后只回弹不带动面板，快速往回翻时不会把面板拉下来关掉；AI 总结同理。
 */
@Composable
private fun SubtitleCuePreview(
    srt: String,
    modifier: Modifier = Modifier,
) {
    val cues = remember(srt) { parseSubtitleCues(srt) }
    // 时长超过一小时才显示小时位，同一份字幕的时间宽度保持一致。
    val showHours = (cues.lastOrNull()?.startMillis ?: 0L) >= 3_600_000L
    val listState = rememberLazyListState()
    val containedScroll = rememberContainedScroll()
    val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    Box(modifier) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(containedScroll.connection),
                overscrollEffect = containedScroll.overscrollEffect,
                contentPadding = copyPreviewBodyPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(cues) { cue ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DisableSelection {
                            Text(
                                text = formatCueTime(cue.startMillis, showHours),
                                style = timeStyle,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                        Text(
                            text = cue.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .alignByBaseline(),
                        )
                    }
                }
            }
        }
        LazyListScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/** AI 总结按 Markdown 排版；章节时间点是可点按的跳转链接。 */
@Composable
private fun AiSummaryMarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    // 首次解析会加载 HTML 实体表等，放到后台线程。
    val document by produceState<MarkdownDocument?>(initialValue = null, markdown) {
        value = withContext(Dispatchers.Default) {
            GithubMarkdownParser.parse(markdown).asCardPreview()
        }
    }
    val containedScroll = rememberContainedScroll()
    Box(modifier) {
        document?.let { parsed ->
            SelectionContainer {
                MarkdownContent(
                    document = parsed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .nestedScroll(containedScroll.connection)
                        .verticalScroll(rememberScrollState(), containedScroll.overscrollEffect)
                        .padding(copyPreviewBodyPadding),
                )
            }
        }
    }
}

/**
 * 把 AI 总结排进卡片：Markdown 开头的「# 标题 - BV 号」与卡片标题重复，预览中省略；
 * 卡片标题用 titleMedium（相当于三级标题），章节标题至少降到四级（titleSmall），层级才不会倒挂。
 * 只影响预览，复制的仍是完整 Markdown。
 */
private fun MarkdownDocument.asCardPreview(): MarkdownDocument {
    val leadingHeading = blocks.firstOrNull() as? MarkdownBlock.Heading
    val body = if (leadingHeading?.level == 1) blocks.drop(1) else blocks
    return MarkdownDocument(
        body.map { block ->
            if (block is MarkdownBlock.Heading) block.copy(level = maxOf(block.level, 4)) else block
        },
    )
}

/**
 * 去掉所有标题共有的首尾分段，只留下能区分条目的部分用作切换标签：
 * 分 P 留下「P2」，收藏夹里的「视频 - 默认收藏夹」留下「视频」。
 * 与其他条目完全相同的标题返回 null，交由语言等信息区分。
 */
internal fun distinctTitleParts(titles: List<String>): List<String?> {
    val segments = titles.map { it.split(copyEntryTitleSeparator) }
    val prefixCount = sharedLeadingSegmentCount(segments)
    val remainders = segments.map { it.drop(prefixCount) }
    val suffixCount = sharedLeadingSegmentCount(remainders.map { it.asReversed() })
    return remainders.map { parts ->
        parts.dropLast(suffixCount).joinToString(copyEntryTitleSeparator).ifEmpty { null }
    }
}

private fun sharedLeadingSegmentCount(segmentLists: List<List<String>>): Int {
    val first = segmentLists.first()
    return first.indices
        .takeWhile { index -> segmentLists.all { it.getOrNull(index) == first[index] } }
        .size
}

internal data class SubtitleCue(val startMillis: Long, val text: String)

/** 解析 [com.happycola233.bilitools.data.ExtrasRepository.getSubtitleSrt] 生成的 SRT；台词可能为空或含换行。 */
internal fun parseSubtitleCues(srt: String): List<SubtitleCue> {
    val headers = srtCueHeader.findAll(srt).toList()
    return headers.mapIndexed { index, header ->
        val (hours, minutes, seconds, millis) = header.destructured
        val textEnd = headers.getOrNull(index + 1)?.range?.first ?: srt.length
        SubtitleCue(
            startMillis = ((hours.toLong() * 60 + minutes.toLong()) * 60 + seconds.toLong()) * 1000 +
                millis.toLong(),
            text = srt.substring(header.range.last + 1, textEnd).trimEnd('\n'),
        )
    }
}

internal fun formatCueTime(startMillis: Long, withHours: Boolean): String {
    val totalSeconds = startMillis / 1000
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    return if (withHours) {
        String.format(Locale.ROOT, "%d:%02d:%02d", totalSeconds / 3600, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** 序号行 + 时间轴行，捕获开始时间的时、分、秒、毫秒。 */
private val srtCueHeader = Regex("""(?m)^\d+\n(\d+):(\d{2}):(\d{2}),(\d{3}) --> .*\n""")
