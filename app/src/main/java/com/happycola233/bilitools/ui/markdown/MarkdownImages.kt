package com.happycola233.bilitools.ui.markdown

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Size
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.usesDarkSurfaces

/** 一组独占一行的图片：按各自的尺寸属性流式排列，宽度百分比相对当前容器计算。 */
@Composable
internal fun MarkdownRenderScope.MarkdownImageRow(block: MarkdownBlock.Images, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val containerWidth = maxWidth
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            // 与浏览器里行内图片之间的空白宽度相近，`width="49%"` 的两张图可以并排。
            horizontalArrangement = Arrangement.spacedBy(4.dp, block.align.horizontalAlignment),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.images.forEach { image -> BlockImage(image, containerWidth) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MarkdownRenderScope.BlockImage(image: MarkdownImage, containerWidth: Dp) {
    val density = LocalDensity.current
    val specifiedWidth = image.width?.toDp(containerWidth)?.coerceAtMost(containerWidth)
    val specifiedHeight = (image.height as? MarkdownLength.Px)?.value?.dp
    val decodeSize = with(density) {
        if (specifiedWidth != null) {
            ImageDecodeSize.Width(specifiedWidth.roundToPx())
        } else {
            ImageDecodeSize.Natural(maxWidthPx = containerWidth.roundToPx())
        }
    }
    val load = rememberRoutedImageLoad(image, decodeSize)
    val clickModifier = image.link?.let { link ->
        Modifier.clickable(role = Role.Image) { openLink(link) }
    } ?: Modifier

    val state = load.state
    when {
        state is AsyncImagePainter.State.Success -> {
            val decoded = state.result.image
            // SVG 按屏幕密度栅格化、超出容器的大图被缩小后，位图像素已是设备像素；
            // 未缩放的位图则与浏览器一致，把一个图片像素当作一个 CSS 像素（dp）显示。
            val naturalWidth = if (state.result.isSampled) {
                with(density) { decoded.width.toDp() }
            } else {
                decoded.width.toFloat().dp
            }
            val displaySize = displaySize(
                aspectRatio = decoded.width.toFloat() / decoded.height.coerceAtLeast(1),
                naturalWidth = naturalWidth,
                specifiedWidth = specifiedWidth,
                specifiedHeight = specifiedHeight,
                containerWidth = containerWidth,
            )
            Image(
                painter = load.painter,
                contentDescription = image.alt,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(displaySize)
                    .clip(MaterialTheme.shapes.small)
                    .then(clickModifier),
            )
        }
        state is AsyncImagePainter.State.Error && load.exhausted -> {
            ImageLoadFailure(image, specifiedWidth, onRetry = load.retry)
        }
        else -> {
            val placeholderWidth = specifiedWidth ?: containerWidth
            val placeholderHeight = specifiedHeight
                ?: (placeholderWidth * PLACEHOLDER_ASPECT_RATIO).coerceAtMost(MAX_PLACEHOLDER_HEIGHT)
            Box(
                modifier = Modifier
                    .size(placeholderWidth, placeholderHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(AppSurfaces.insetContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                if (placeholderHeight >= 48.dp) LoadingIndicator(Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun ImageLoadFailure(image: MarkdownImage, width: Dp?, onRetry: () -> Unit) {
    Surface(
        onClick = onRetry,
        color = AppSurfaces.insetContainerColor,
        shape = MaterialTheme.shapes.small,
        modifier = if (width != null) Modifier.widthIn(min = width) else Modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_hide_image_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                image.alt?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    text = stringResource(R.string.markdown_image_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 行内图片（文字中间的图标、徽章等）。文字排版时就要确定占位尺寸，因此只能使用 HTML 声明的像素尺寸，
 * 没有声明时按约一个字高的方块显示；图片按比例缩放进占位区域，加载失败时留空，不打断正文。
 */
@Composable
internal fun MarkdownRenderScope.markdownInlineImages(text: MarkdownText): Map<String, InlineTextContent> {
    if (text.images.isEmpty()) return emptyMap()
    val density = LocalDensity.current
    return text.images.withIndex().associate { (index, inline) ->
        val image = inline.image
        val width = (image.width as? MarkdownLength.Px)?.value
        val height = (image.height as? MarkdownLength.Px)?.value
        val placeholder = Placeholder(
            width = (width ?: height)?.toSp(density) ?: INLINE_IMAGE_FALLBACK_SIZE,
            height = (height ?: width)?.toSp(density) ?: INLINE_IMAGE_FALLBACK_SIZE,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        )
        inlineImageId(index) to InlineTextContent(placeholder) {
            val load = rememberRoutedImageLoad(image, ImageDecodeSize.Natural(maxWidthPx = null))
            if (load.state is AsyncImagePainter.State.Success) {
                Image(
                    painter = load.painter,
                    contentDescription = image.alt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** dp 尺寸换算为不随字体缩放变化的 sp，使行内图片与块级图片的像素尺寸含义一致。 */
private fun Float.toSp(density: Density): TextUnit = (this / density.fontScale).sp

/** 解码尺寸以普通值表示，作为 `remember` 的键时可按值比较，避免重组时反复重建请求。 */
private sealed interface ImageDecodeSize {
    /** 已知显示宽度时按该宽度解码，SVG 也能按实际尺寸清晰栅格化。 */
    data class Width(val px: Int) : ImageDecodeSize

    /** 未声明尺寸时按原图解码以得到自然尺寸；[maxWidthPx] 限制位图宽度，避免超大原图占用过多内存。 */
    data class Natural(val maxWidthPx: Int?) : ImageDecodeSize
}

private class RoutedImageLoad(
    val painter: AsyncImagePainter,
    val state: AsyncImagePainter.State,
    /** 所有候选地址都已失败。 */
    val exhausted: Boolean,
    val retry: () -> Unit,
)

/**
 * 按候选地址依次加载图片：当前地址失败就换下一个（例如镜像不支持该地址时回到 GitHub 直连），
 * 全部失败后由调用方展示重试入口，重试会从第一个地址重新开始。
 */
@Composable
private fun MarkdownRenderScope.rememberRoutedImageLoad(
    image: MarkdownImage,
    decodeSize: ImageDecodeSize,
): RoutedImageLoad {
    val source = rememberImageSource(image)
    val candidates = remember(source, imageUrlCandidates) { imageUrlCandidates(source) }
    var attempt by remember(candidates) { mutableIntStateOf(0) }
    var retryCount by remember(candidates) { mutableIntStateOf(0) }
    val context = LocalPlatformContext.current
    // 重试时更换组合键，确保即使地址相同也会重新发起请求。
    return key(retryCount) {
        val url = candidates[attempt.coerceAtMost(candidates.lastIndex)]
        val request = remember(url, decodeSize) {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .apply {
                    when (decodeSize) {
                        is ImageDecodeSize.Width -> size(Size(Dimension(decodeSize.px), Dimension.Undefined))
                        is ImageDecodeSize.Natural -> {
                            size(Size.ORIGINAL)
                            decodeSize.maxWidthPx?.let { maxBitmapSize(Size(it, it * MAX_IMAGE_ASPECT_RATIO)) }
                        }
                    }
                }
                .build()
        }
        val painter = rememberAsyncImagePainter(
            model = request,
            onError = { if (attempt < candidates.lastIndex) attempt++ },
        )
        val state by painter.state.collectAsState()
        RoutedImageLoad(
            painter = painter,
            state = state,
            exhausted = attempt >= candidates.lastIndex,
            retry = {
                attempt = 0
                retryCount++
            },
        )
    }
}

/** `<picture>` 按当前主题与窗口宽度挑选第一个媒体查询命中的 `<source>`，都不命中时使用 `<img>` 的地址。 */
@Composable
private fun rememberImageSource(image: MarkdownImage): String {
    if (image.sources.isEmpty()) return image.url
    val isDark = MaterialTheme.colorScheme.usesDarkSurfaces()
    val density = LocalDensity.current
    val viewportWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }.value
    return remember(image, isDark, viewportWidth) {
        image.sources.firstOrNull { source ->
            source.media == null || matchesMediaQuery(source.media, isDark, viewportWidth)
        }?.url ?: image.url
    }
}

/**
 * 支持发布说明里常见的媒体查询：`prefers-color-scheme`、`max-width`、`min-width`，
 * 以及 `and` 组合与逗号分隔的“或”关系；无法识别的条件视为不命中，回退到默认图片。
 */
internal fun matchesMediaQuery(media: String, isDark: Boolean, viewportWidthDp: Float): Boolean {
    return media.split(',').any { query ->
        query.trim().split(MEDIA_AND).all { condition ->
            matchesMediaCondition(condition.trim().lowercase(), isDark, viewportWidthDp)
        }
    }
}

private fun matchesMediaCondition(condition: String, isDark: Boolean, viewportWidthDp: Float): Boolean {
    if (condition in setOf("all", "screen", "only screen")) return true
    val feature = MEDIA_FEATURE.matchEntire(condition) ?: return false
    val (name, value) = feature.destructured
    return when (name) {
        "prefers-color-scheme" -> value == if (isDark) "dark" else "light"
        "max-width" -> cssLengthInDp(value)?.let { viewportWidthDp <= it } ?: false
        "min-width" -> cssLengthInDp(value)?.let { viewportWidthDp >= it } ?: false
        else -> false
    }
}

private fun cssLengthInDp(value: String): Float? = when {
    value.endsWith("px") -> value.removeSuffix("px").trim().toFloatOrNull()
    value.endsWith("em") -> value.removeSuffix("em").removeSuffix("r").trim().toFloatOrNull()?.times(16f)
    else -> value.toFloatOrNull()
}

private fun displaySize(
    aspectRatio: Float,
    naturalWidth: Dp,
    specifiedWidth: Dp?,
    specifiedHeight: Dp?,
    containerWidth: Dp,
): DpSize {
    if (specifiedWidth != null && specifiedHeight != null) return DpSize(specifiedWidth, specifiedHeight)
    val width = when {
        specifiedWidth != null -> specifiedWidth
        specifiedHeight != null -> specifiedHeight * aspectRatio
        else -> naturalWidth
    }.coerceAtMost(containerWidth)
    return DpSize(width, width / aspectRatio)
}

private fun MarkdownLength.toDp(containerWidth: Dp): Dp = when (this) {
    is MarkdownLength.Px -> value.dp
    is MarkdownLength.Percent -> containerWidth * (value / 100f)
}

private val MarkdownAlign.horizontalAlignment: Alignment.Horizontal
    get() = when (this) {
        MarkdownAlign.Start -> Alignment.Start
        MarkdownAlign.Center -> Alignment.CenterHorizontally
        MarkdownAlign.End -> Alignment.End
    }

private const val MAX_IMAGE_ASPECT_RATIO = 4
private val INLINE_IMAGE_FALLBACK_SIZE = 1.25.em
private const val PLACEHOLDER_ASPECT_RATIO = 9f / 16f
private val MAX_PLACEHOLDER_HEIGHT = 240.dp
private val MEDIA_AND = Regex("""\s+and\s+""", RegexOption.IGNORE_CASE)
private val MEDIA_FEATURE = Regex("""\(\s*([a-z-]+)\s*:\s*([^)]+?)\s*\)""")
