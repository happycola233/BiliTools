package com.happycola233.bilitools.data.model

/** 统一图文详情。API DTO 会先转换为该模型，再交给展示与下载层使用。 */
data class OpusDocument(
    val id: String,
    val cvid: Long? = null,
    val title: String,
    val summary: String,
    val sourceUrl: String,
    val author: MediaUpper? = null,
    val publishedAt: Long? = null,
    val stat: MediaStat = MediaStat(),
    val tags: List<String> = emptyList(),
    val topImages: List<OpusImage> = emptyList(),
    val blocks: List<OpusBlock> = emptyList(),
    /** 按正文出现顺序去重后的全部原图，包含顶部相册与“查看图片”节点。 */
    val images: List<OpusImage> = emptyList(),
)

data class OpusImage(
    val url: String,
    val alt: String = "",
)

sealed interface OpusBlock {
    data class Paragraph(
        val nodes: List<OpusNode>,
        val headingLevel: Int? = null,
    ) : OpusBlock

    data class Pictures(val images: List<OpusImage>) : OpusBlock

    data object Divider : OpusBlock

    data class Quote(val nodes: List<OpusNode>) : OpusBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<OpusListItem>,
    ) : OpusBlock

    data class LinkCard(
        val title: String,
        val url: String? = null,
    ) : OpusBlock

    data class Code(
        val language: String? = null,
        val content: String,
    ) : OpusBlock
}

data class OpusListItem(
    val level: Int,
    val order: Int,
    val nodes: List<OpusNode>,
)

sealed interface OpusNode {
    data class Text(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strikethrough: Boolean = false,
    ) : OpusNode

    data class Link(
        val text: String,
        val url: String,
    ) : OpusNode

    data class Formula(val latex: String) : OpusNode

    data class Picture(
        val text: String,
        val images: List<OpusImage>,
    ) : OpusNode
}

data class OpusAssetPlan(
    val image: OpusImage,
    val fileName: String,
    val mimeType: String,
)

data class MediaCapabilities(
    val supportsPlaybackStream: Boolean = false,
    val supportsOpusExport: Boolean = false,
    val supportsSubtitleExport: Boolean = false,
    val supportsAiSummaryExport: Boolean = false,
    val supportsNfoExport: Boolean = false,
    val supportsDanmakuExport: Boolean = false,
    val supportsAuxiliaryImageExport: Boolean = false,
)

val MediaType.capabilities: MediaCapabilities
    get() = when (this) {
        MediaType.Video,
        MediaType.Bangumi,
        MediaType.Lesson,
        MediaType.Music,
        -> MediaCapabilities(
            supportsPlaybackStream = true,
            supportsSubtitleExport = true,
            supportsAiSummaryExport = true,
            supportsNfoExport = true,
            supportsDanmakuExport = true,
            supportsAuxiliaryImageExport = true,
        )

        MediaType.Opus,
        MediaType.OpusList,
        MediaType.UserOpus,
        -> MediaCapabilities(supportsOpusExport = true)

        else -> MediaCapabilities()
    }
