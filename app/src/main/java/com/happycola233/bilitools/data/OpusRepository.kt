package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.OpusAssetPlanner
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaUpper
import com.happycola233.bilitools.data.model.OpusBlock
import com.happycola233.bilitools.data.model.OpusDocument
import com.happycola233.bilitools.data.model.OpusImage
import com.happycola233.bilitools.data.model.OpusListItem
import com.happycola233.bilitools.data.model.OpusNode
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl

class OpusRepository(
    private val httpClient: BiliHttpClient,
    private val cookieStore: CookieStore,
) {
    private val cacheMutex = Mutex()
    private val documentCache = object : LinkedHashMap<String, OpusDocument>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OpusDocument>?): Boolean {
            return size > CACHE_SIZE
        }
    }
    private val inFlight = mutableMapOf<String, CompletableDeferred<OpusDocument>>()

    suspend fun getDocument(item: MediaItem): OpusDocument {
        val opusId = item.opid?.trim().orEmpty()
        return when {
            opusId.isNotBlank() -> getDocument(opusId, item.cvid)
            item.cvid != null -> getDocument("cv${item.cvid}", item.cvid)
            else -> {
                val idFromUrl = OPUS_ID_REGEX.find(item.url)?.groupValues?.getOrNull(1)
                val cvFromUrl = CV_ID_REGEX.find(item.url)?.groupValues?.getOrNull(1)?.toLongOrNull()
                when {
                    !idFromUrl.isNullOrBlank() -> getDocument(idFromUrl, cvFromUrl)
                    cvFromUrl != null -> getDocument("cv$cvFromUrl", cvFromUrl)
                    else -> throw OpusException(OpusFailure.InvalidReference)
                }
            }
        }
    }

    suspend fun getDocument(reference: String, knownCvid: Long? = null): OpusDocument {
        val normalizedReference = normalizeReference(reference, knownCvid)
        var cached: OpusDocument? = null
        lateinit var request: CompletableDeferred<OpusDocument>
        var owner = false
        cacheMutex.withLock {
            cached = documentCache[normalizedReference]
            if (cached == null) {
                request = inFlight[normalizedReference] ?: CompletableDeferred<OpusDocument>().also {
                    inFlight[normalizedReference] = it
                    owner = true
                }
            }
        }
        cached?.let { return it }
        if (!owner) return request.await()

        try {
            val resolved = resolveReference(normalizedReference, knownCvid)
            cacheMutex.withLock {
                documentCache[resolved.cacheKey]?.let { resolvedDocument ->
                    val document = if (resolvedDocument.cvid == null && resolved.cvid != null) {
                        resolvedDocument.copy(cvid = resolved.cvid)
                    } else {
                        resolvedDocument
                    }
                    documentCache[normalizedReference] = document
                    inFlight.remove(normalizedReference)
                    request.complete(document)
                    return document
                }
            }
            val document = fetchDocument(resolved.opusId, resolved.cvid)
            cacheMutex.withLock {
                documentCache[normalizedReference] = document
                documentCache[resolved.cacheKey] = document
                inFlight.remove(normalizedReference)
                request.complete(document)
            }
            return document
        } catch (error: Throwable) {
            cacheMutex.withLock {
                inFlight.remove(normalizedReference)
                request.completeExceptionally(error)
            }
            throw error
        }
    }

    private suspend fun resolveReference(reference: String, knownCvid: Long?): ResolvedOpusReference {
        val cvid = knownCvid ?: reference.removePrefix("cv").toLongOrNull()
        if (reference.startsWith("cv") || (knownCvid != null && reference == "cv$knownCvid")) {
            val resolvedId = resolveCvid(cvid ?: throw OpusException(OpusFailure.InvalidReference))
            return ResolvedOpusReference(resolvedId, cvid)
        }
        val opusId = reference.filter(Char::isDigit)
        if (opusId.isBlank()) throw OpusException(OpusFailure.InvalidReference)
        return ResolvedOpusReference(opusId, cvid)
    }

    private suspend fun resolveCvid(cvid: Long): String {
        suspend fun requestArticle(): ArticleViewResponse {
            val body = httpClient.get(
                "https://api.bilibili.com/x/article/view".toHttpUrl().newBuilder()
                    .addQueryParameter("id", cvid.toString())
                    .addQueryParameter("gaia_source", "main_web")
                    .build(),
            )
            return httpClient.adapter(ArticleViewResponse::class.java).fromJson(body)
                ?: throw OpusException(OpusFailure.InvalidResponse)
        }

        var response = requestArticle()
        if (response.code == RISK_CONTROL_CODE) {
            bootstrapPublicPage("https://www.bilibili.com/read/cv$cvid")
            response = requestArticle()
        }
        if (response.code == 0) {
            response.data?.dynamicId?.takeIf(String::isNotBlank)?.let { return it }
        }

        val resolvedUrl = httpClient.resolveUrl("https://www.bilibili.com/read/cv$cvid".toHttpUrl())
        OPUS_ID_REGEX.find(resolvedUrl)?.groupValues?.getOrNull(1)?.let { return it }
        if (response.code != 0) throw response.toOpusException()
        throw OpusException(OpusFailure.InvalidResponse)
    }

    private suspend fun fetchDocument(opusId: String, cvid: Long?): OpusDocument {
        val publicUrl = "https://www.bilibili.com/opus/$opusId"
        if (cookieStore.getCookie("buvid3").isNullOrBlank()) {
            bootstrapPublicPage(publicUrl)
        }

        var response = requestDetail(opusId)
        if (response.code == RISK_CONTROL_CODE) {
            bootstrapPublicPage(publicUrl)
            response = requestDetail(opusId)
        }
        if (response.code != 0) throw response.toOpusException()
        val data = response.data ?: throw OpusException(OpusFailure.InvalidResponse)
        val item = data.item ?: throw OpusException(OpusFailure.NotFound)
        return OpusDocumentParser.fromItem(item, opusId, cvid, publicUrl)
    }

    private suspend fun requestDetail(opusId: String): OpusDetailResponse {
        val url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/detail"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("id", opusId)
            .addQueryParameter("timezone_offset", "-480")
            .addQueryParameter("features", OPUS_FEATURES)
            .build()
        val body = httpClient.get(url)
        return httpClient.adapter(OpusDetailResponse::class.java).fromJson(body)
            ?: throw OpusException(OpusFailure.InvalidResponse)
    }

    private suspend fun bootstrapPublicPage(url: String) {
        httpClient.resolveUrl(url.toHttpUrl())
    }

    private fun normalizeReference(reference: String, knownCvid: Long?): String {
        if (knownCvid != null && reference.isBlank()) return "cv$knownCvid"
        val trimmed = reference.trim().lowercase()
        if (trimmed.startsWith("cv")) {
            val digits = trimmed.drop(2)
            if (digits.isNotEmpty() && digits.all(Char::isDigit)) return "cv$digits"
        }
        val digits = trimmed.filter(Char::isDigit)
        if (digits.isBlank()) throw OpusException(OpusFailure.InvalidReference)
        return digits
    }

    private fun ApiResponse.toOpusException(): OpusException = when (code) {
        RISK_CONTROL_CODE,
        REQUEST_BLOCKED_CODE,
        -> OpusException(OpusFailure.RiskControl, code, message)
        LOGIN_REQUIRED_CODE -> OpusException(OpusFailure.LoginRequired, code, message)
        PERMISSION_DENIED_CODE -> OpusException(OpusFailure.PermissionDenied, code, message)
        NOT_FOUND_CODE,
        DYNAMIC_NOT_FOUND_CODE,
        -> OpusException(OpusFailure.NotFound, code, message)
        else -> OpusException(OpusFailure.ApiError, code, message)
    }

    private data class ResolvedOpusReference(val opusId: String, val cvid: Long?) {
        val cacheKey: String get() = opusId
    }

    companion object {
        private const val CACHE_SIZE = 64
        private const val RISK_CONTROL_CODE = -352
        private const val LOGIN_REQUIRED_CODE = -101
        private const val PERMISSION_DENIED_CODE = -403
        private const val NOT_FOUND_CODE = -404
        private const val REQUEST_BLOCKED_CODE = -412
        private const val DYNAMIC_NOT_FOUND_CODE = 4_101_139
        private const val OPUS_FEATURES =
            "onlyfansVote,onlyfansAssetsV2,decorationCard,htmlNewStyle,ugcDelete,editable," +
                "opusPrivateVisible,tribeeEdit,avatarAutoTheme,avatarTypeOpus"
        private val OPUS_ID_REGEX = Regex("/opus/(\\d+)", RegexOption.IGNORE_CASE)
        private val CV_ID_REGEX = Regex("/read/(?:cv)?(\\d+)", RegexOption.IGNORE_CASE)
    }
}

/** 将接口 DTO 归一化为与下载层解耦的图文领域模型。 */
private object OpusDocumentParser {
    private val fixtureAdapter by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(OpusDetailResponse::class.java)
    }

    fun fromJson(
        json: String,
        requestedId: String,
        cvid: Long?,
        publicUrl: String,
    ): OpusDocument {
        val response = fixtureAdapter.fromJson(json)
            ?: throw OpusException(OpusFailure.InvalidResponse)
        if (response.code != 0) {
            throw OpusException(OpusFailure.ApiError, response.code, response.message)
        }
        val data = response.data ?: throw OpusException(OpusFailure.InvalidResponse)
        val item = data.item ?: throw OpusException(OpusFailure.NotFound)
        return fromItem(item, requestedId, cvid, publicUrl)
    }

    fun fromItem(
        item: OpusDetailItem,
        requestedId: String,
        cvid: Long?,
        publicUrl: String,
    ): OpusDocument = with(item) {
        val canonicalId = id?.takeIf(String::isNotBlank) ?: requestedId
        val titleModule = modules.orEmpty().firstNotNullOfOrNull { module ->
            module.title?.text?.trim()?.takeIf(String::isNotBlank)
        }
        val authorModule = modules.orEmpty().firstNotNullOfOrNull { module -> module.author }
        val statModule = modules.orEmpty().firstNotNullOfOrNull { module -> module.stat }
        val topImages = modules.orEmpty()
            .flatMap { module -> module.top?.display?.album?.pics.orEmpty() }
            .mapNotNull { picture -> picture.toImage() }
            .distinctBy(OpusImage::url)
        val blocks = modules.orEmpty()
            .flatMap { module -> module.content?.paragraphs.orEmpty() }
            .mapNotNull { paragraph -> paragraph.toBlock() }
        val allImages = linkedMapOf<String, OpusImage>()
        topImages.forEach { allImages.putIfAbsent(it.url, it) }
        blocks.forEach { block -> collectImages(block).forEach { allImages.putIfAbsent(it.url, it) } }

        val plainText = blocks.asSequence()
            .map(::plainText)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .trim()
        val title = titleModule
            ?: basic?.title?.trim()?.takeIf(String::isNotBlank)
            ?: plainText.lineSequence().firstOrNull(String::isNotBlank)?.trim()?.take(120)
            ?: "图文_$canonicalId"
        val tags = modules.orEmpty()
            .flatMap { module ->
                buildList {
                    addAll(module.extend?.items.orEmpty().mapNotNull { it.text })
                    module.topic?.name?.let(::add)
                }
            }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        val authorMid = authorModule?.mid ?: basic?.uid
        val author = authorMid?.let { mid ->
            MediaUpper(
                name = authorModule?.name.orEmpty(),
                mid = mid,
                avatar = authorModule?.face?.let(OpusAssetPlanner::normalizeImageUrl),
            )
        }
        OpusDocument(
            id = canonicalId,
            cvid = cvid,
            title = title,
            summary = plainText.replace(Regex("\\s+"), " ").take(280),
            sourceUrl = publicUrl.replace(requestedId, canonicalId),
            author = author,
            publishedAt = authorModule?.publishedAt.toEpochSecondsOrNull(),
            stat = MediaStat(
                reply = statModule?.comment?.count,
                like = statModule?.like?.count,
                coin = statModule?.coin?.count,
                favorite = statModule?.favorite?.count,
                share = statModule?.forward?.count,
            ),
            tags = tags,
            topImages = topImages,
            blocks = blocks,
            images = allImages.values.toList(),
        )
    }

    private fun OpusParagraphDto.toBlock(): OpusBlock? {
        return when (type) {
            1 -> {
                val sourceNodes = text?.nodes.orEmpty()
                val nodes = sourceNodes.mapNotNull { node -> node.toNode() }
                if (nodes.isEmpty()) null else OpusBlock.Paragraph(nodes, headingLevel(sourceNodes))
            }
            2 -> {
                val images = (pic ?: pics)?.pics.orEmpty().mapNotNull { picture -> picture.toImage() }
                images.takeIf(List<OpusImage>::isNotEmpty)?.let(OpusBlock::Pictures)
            }
            3 -> OpusBlock.Divider
            4 -> {
                val nodes = text?.nodes.orEmpty().mapNotNull { node -> node.toNode() }
                nodes.takeIf(List<OpusNode>::isNotEmpty)?.let(OpusBlock::Quote)
            }
            5 -> {
                val items = list?.items.orEmpty().map { listItem ->
                    OpusListItem(
                        level = listItem.level ?: 1,
                        order = listItem.order ?: 0,
                        nodes = listItem.nodes.orEmpty().mapNotNull { node -> node.toNode() },
                    )
                }.filter { listItem -> listItem.nodes.isNotEmpty() }
                items.takeIf(List<OpusListItem>::isNotEmpty)?.let { listItems ->
                    OpusBlock.ListBlock(ordered = list?.style == 1, items = listItems)
                }
            }
            6 -> linkCard?.card?.toLinkCard()
            7 -> code?.content?.takeIf(String::isNotBlank)?.let { content ->
                OpusBlock.Code(code.language, content)
            }
            else -> {
                val nodes = text?.nodes.orEmpty().mapNotNull { node -> node.toNode() }
                nodes.takeIf(List<OpusNode>::isNotEmpty)?.let { OpusBlock.Paragraph(it) }
            }
        }
    }

    private fun OpusTextNodeDto.toNode(): OpusNode? {
        word?.words?.takeIf(String::isNotEmpty)?.let { words ->
            return OpusNode.Text(
                text = words,
                bold = word.style?.bold == true,
                italic = word.style?.italic == true,
                strikethrough = word.style?.strikethrough == true,
            )
        }
        formula?.latex?.takeIf(String::isNotBlank)?.let { return OpusNode.Formula(it) }
        val richNode = rich ?: return null
        val text = richNode.text?.takeIf(String::isNotBlank)
            ?: richNode.originalText?.takeIf(String::isNotBlank)
            ?: richNode.rid.orEmpty()
        if (richNode.type == "RICH_TEXT_NODE_TYPE_VIEW_PICTURE") {
            val images = richNode.pics.orEmpty().mapNotNull { picture -> picture.toImage() }
            return OpusNode.Picture(text, images)
        }
        if (richNode.type == "RICH_TEXT_NODE_TYPE_EMOJI" || richNode.type == "RICH_TEXT_NODE_TYPE_TEXT") {
            return text.takeIf(String::isNotEmpty)?.let(OpusNode::Text)
        }
        val url = richNode.jumpUrl?.let(OpusAssetPlanner::normalizeWebUrl)
            ?.takeIf(String::isNotBlank)
            ?: richNode.defaultUrl()
        return if (!url.isNullOrBlank()) {
            OpusNode.Link(text.ifBlank { url }, url)
        } else {
            text.takeIf(String::isNotEmpty)?.let(OpusNode::Text)
        }
    }

    private fun OpusRichDto.defaultUrl(): String? = when (type) {
        "RICH_TEXT_NODE_TYPE_AT" -> rid?.let { "https://space.bilibili.com/$it" }
        "RICH_TEXT_NODE_TYPE_BV" -> rid?.let { "https://www.bilibili.com/video/$it" }
        "RICH_TEXT_NODE_TYPE_AV" -> rid?.let {
            val videoId = it.takeIf { value -> value.startsWith("av", true) } ?: "av$it"
            "https://www.bilibili.com/video/$videoId"
        }
        "RICH_TEXT_NODE_TYPE_CV" -> rid?.let {
            val articleId = it.takeIf { value -> value.startsWith("cv", true) } ?: "cv$it"
            "https://www.bilibili.com/read/$articleId"
        }
        else -> null
    }

    private fun OpusPictureDto.toImage(): OpusImage? {
        val source = url?.takeIf(String::isNotBlank) ?: src?.takeIf(String::isNotBlank) ?: return null
        val normalized = OpusAssetPlanner.normalizeImageUrl(source)
        if (normalized.isBlank()) return null
        return OpusImage(normalized, comment.orEmpty().trim())
    }

    private fun Map<String, Any?>.toLinkCard(): OpusBlock.LinkCard? {
        val title = findFirstString(
            setOf("title", "text", "name", "desc", "sub_title", "head_text"),
        ).orEmpty()
        val url = findFirstString(setOf("jump_url", "url"))
            ?.let(OpusAssetPlanner::normalizeWebUrl)
            ?.takeIf(String::isNotBlank)
        if (title.isBlank() && url.isNullOrBlank()) return null
        return OpusBlock.LinkCard(title.ifBlank { url.orEmpty() }, url)
    }

    private fun Map<String, Any?>.findFirstString(keys: Set<String>): String? {
        return findFirstStringInValue(this, keys)
    }

    private fun findFirstStringInValue(value: Any?, keys: Set<String>): String? = when (value) {
        is Map<*, *> -> {
            value.entries.firstNotNullOfOrNull { (key, entryValue) ->
                (entryValue as? String)?.takeIf { key in keys && it.isNotBlank() }
            } ?: value.values.firstNotNullOfOrNull { nested -> findFirstStringInValue(nested, keys) }
        }
        is Iterable<*> -> value.firstNotNullOfOrNull { nested -> findFirstStringInValue(nested, keys) }
        else -> null
    }

    private fun headingLevel(nodes: List<OpusTextNodeDto>): Int? {
        val sizes = nodes.mapNotNull { node -> node.word?.fontSize }.distinct()
        if (sizes.size != 1) return null
        return when {
            sizes.single() >= 24 -> 2
            sizes.single() >= 20 -> 3
            else -> null
        }
    }

    private fun Any?.toEpochSecondsOrNull(): Long? = when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }

    private fun collectImages(block: OpusBlock): List<OpusImage> = when (block) {
        is OpusBlock.Pictures -> block.images
        is OpusBlock.Paragraph -> block.nodes.filterIsInstance<OpusNode.Picture>().flatMap { it.images }
        is OpusBlock.Quote -> block.nodes.filterIsInstance<OpusNode.Picture>().flatMap { it.images }
        is OpusBlock.ListBlock -> block.items.flatMap { listItem ->
            listItem.nodes.filterIsInstance<OpusNode.Picture>().flatMap { it.images }
        }
        else -> emptyList()
    }

    private fun plainText(block: OpusBlock): String = when (block) {
        is OpusBlock.Paragraph -> plainText(block.nodes)
        is OpusBlock.Quote -> plainText(block.nodes)
        is OpusBlock.ListBlock -> block.items.joinToString("\n") { plainText(it.nodes) }
        else -> ""
    }

    private fun plainText(nodes: List<OpusNode>): String = buildString {
        nodes.forEach { node ->
            when (node) {
                is OpusNode.Text -> append(node.text)
                is OpusNode.Link -> append(node.text)
                is OpusNode.Formula -> append(node.latex)
                is OpusNode.Picture -> Unit
            }
        }
    }
}

/** 供固定接口夹具与只读冒烟复用生产解析器，不绕过任何归一化逻辑。 */
internal fun parseOpusDocumentResponse(
    json: String,
    requestedId: String,
    cvid: Long? = null,
    publicUrl: String = "https://www.bilibili.com/opus/$requestedId",
): OpusDocument = OpusDocumentParser.fromJson(json, requestedId, cvid, publicUrl)

enum class OpusFailure {
    InvalidReference,
    RiskControl,
    LoginRequired,
    PermissionDenied,
    NotFound,
    InvalidResponse,
    ApiError,
}

class OpusException(
    val failure: OpusFailure,
    val apiCode: Int? = null,
    detail: String? = null,
) : RuntimeException(detail ?: failure.name)

private interface ApiResponse {
    val code: Int
    val message: String?
}

private data class OpusDetailResponse(
    @Json(name = "code") override val code: Int,
    @Json(name = "message") override val message: String? = null,
    @Json(name = "data") val data: OpusDetailData? = null,
) : ApiResponse

private data class OpusDetailData(
    @Json(name = "item") val item: OpusDetailItem? = null,
)

private data class OpusDetailItem(
    @Json(name = "id_str") val id: String? = null,
    @Json(name = "basic") val basic: OpusBasicDto? = null,
    @Json(name = "modules") val modules: List<OpusModule>? = null,
)

private data class OpusBasicDto(
    @Json(name = "title") val title: String? = null,
    @Json(name = "uid") val uid: Long? = null,
)

private data class OpusModule(
    @Json(name = "module_top") val top: OpusTopDto? = null,
    @Json(name = "module_title") val title: OpusTitleDto? = null,
    @Json(name = "module_author") val author: OpusAuthorDto? = null,
    @Json(name = "module_content") val content: OpusContentDto? = null,
    @Json(name = "module_extend") val extend: OpusExtendDto? = null,
    @Json(name = "module_topic") val topic: OpusTopicDto? = null,
    @Json(name = "module_stat") val stat: OpusStatDto? = null,
)

private data class OpusTopDto(
    @Json(name = "display") val display: OpusTopDisplayDto? = null,
)

private data class OpusTopDisplayDto(
    @Json(name = "album") val album: OpusPictureContainerDto? = null,
)

private data class OpusTitleDto(@Json(name = "text") val text: String? = null)

private data class OpusAuthorDto(
    @Json(name = "mid") val mid: Long? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "face") val face: String? = null,
    @Json(name = "pub_ts") val publishedAt: Any? = null,
)

private data class OpusContentDto(
    @Json(name = "paragraphs") val paragraphs: List<OpusParagraphDto>? = null,
)

private data class OpusParagraphDto(
    @Json(name = "para_type") val type: Int? = null,
    @Json(name = "text") val text: OpusTextDto? = null,
    @Json(name = "pic") val pic: OpusPictureContainerDto? = null,
    @Json(name = "pics") val pics: OpusPictureContainerDto? = null,
    @Json(name = "list") val list: OpusListDto? = null,
    @Json(name = "link_card") val linkCard: OpusLinkCardDto? = null,
    @Json(name = "code") val code: OpusCodeDto? = null,
)

private data class OpusTextDto(
    @Json(name = "nodes") val nodes: List<OpusTextNodeDto>? = null,
)

private data class OpusTextNodeDto(
    @Json(name = "word") val word: OpusWordDto? = null,
    @Json(name = "rich") val rich: OpusRichDto? = null,
    @Json(name = "formula") val formula: OpusFormulaDto? = null,
)

private data class OpusWordDto(
    @Json(name = "words") val words: String? = null,
    @Json(name = "font_size") val fontSize: Int? = null,
    @Json(name = "style") val style: OpusWordStyleDto? = null,
)

private data class OpusWordStyleDto(
    @Json(name = "bold") val bold: Boolean? = null,
    @Json(name = "italic") val italic: Boolean? = null,
    @Json(name = "strikethrough") val strikethrough: Boolean? = null,
)

private data class OpusFormulaDto(
    @Json(name = "latex_content") val latex: String? = null,
)

private data class OpusRichDto(
    @Json(name = "type") val type: String? = null,
    @Json(name = "text") val text: String? = null,
    @Json(name = "orig_text") val originalText: String? = null,
    @Json(name = "jump_url") val jumpUrl: String? = null,
    @Json(name = "rid") val rid: String? = null,
    @Json(name = "pics") val pics: List<OpusPictureDto>? = null,
)

private data class OpusPictureContainerDto(
    @Json(name = "pics") val pics: List<OpusPictureDto>? = null,
)

private data class OpusPictureDto(
    @Json(name = "url") val url: String? = null,
    @Json(name = "src") val src: String? = null,
    @Json(name = "comment") val comment: String? = null,
)

private data class OpusListDto(
    @Json(name = "style") val style: Int? = null,
    @Json(name = "items") val items: List<OpusListItemDto>? = null,
)

private data class OpusListItemDto(
    @Json(name = "level") val level: Int? = null,
    @Json(name = "order") val order: Int? = null,
    @Json(name = "nodes") val nodes: List<OpusTextNodeDto>? = null,
)

private data class OpusLinkCardDto(
    @Json(name = "card") val card: Map<String, Any?>? = null,
)

private data class OpusCodeDto(
    @Json(name = "lang") val language: String? = null,
    @Json(name = "content") val content: String? = null,
)

private data class OpusExtendDto(
    @Json(name = "items") val items: List<OpusExtendItemDto>? = null,
)

private data class OpusExtendItemDto(@Json(name = "text") val text: String? = null)

private data class OpusTopicDto(@Json(name = "name") val name: String? = null)

private data class OpusStatDto(
    @Json(name = "coin") val coin: OpusStatCountDto? = null,
    @Json(name = "comment") val comment: OpusStatCountDto? = null,
    @Json(name = "favorite") val favorite: OpusStatCountDto? = null,
    @Json(name = "forward") val forward: OpusStatCountDto? = null,
    @Json(name = "like") val like: OpusStatCountDto? = null,
)

private data class OpusStatCountDto(@Json(name = "count") val count: Long? = null)

private data class ArticleViewResponse(
    @Json(name = "code") override val code: Int,
    @Json(name = "message") override val message: String? = null,
    @Json(name = "data") val data: ArticleViewData? = null,
) : ApiResponse

private data class ArticleViewData(
    @Json(name = "dyn_id_str") val dynamicId: String? = null,
)
