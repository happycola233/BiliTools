package com.happycola233.bilitools.ui.parse

import android.content.Context
import android.icu.text.CompactDecimalFormat
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.MediaContributor
import com.happycola233.bilitools.data.model.MediaCopyrightType
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaCategory
import com.happycola233.bilitools.data.model.MediaAccess
import com.happycola233.bilitools.data.model.MediaContentKind
import com.happycola233.bilitools.data.model.MediaMetadata
import com.happycola233.bilitools.data.model.MediaRareAttribute
import com.happycola233.bilitools.data.model.MediaResolution
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.absoluteValue

internal data class ParseMetadataDisplay(
    val subjectKey: String,
    val publicIdText: String?,
    val publicIdCopyValue: String?,
    val publicIdCopyName: String?,
    val summarySlots: List<String>,
    val sections: List<ParseMetadataSection>,
)

internal sealed interface ParseMetadataSection {
    val title: String

    data class Values(
        override val title: String,
        val rows: List<ParseMetadataRow>,
    ) : ParseMetadataSection

    data class Groups(
        override val title: String,
        val groups: List<ParseMetadataGroup>,
    ) : ParseMetadataSection

    data class Contributors(
        override val title: String,
        val members: List<MediaContributor>,
    ) : ParseMetadataSection
}

internal data class ParseMetadataGroup(
    /** 分组编号标签，例如 `P1`。 */
    val title: String,
    /** 分组正标题（分 P 标题），随预览图一起放在分组头部而不是作为普通行。 */
    val subtitle: String? = null,
    val previewUrl: String? = null,
    val rows: List<ParseMetadataRow>,
)

internal data class ParseMetadataRow(
    val name: String,
    val value: String,
    /** 附在取值下方的补充说明，例如提示该字段可能不准确。 */
    val note: String? = null,
)

internal fun buildParseMetadataDisplay(
    context: Context,
    info: MediaInfo,
    subjectItem: MediaItem?,
    collectionOverview: Boolean,
): ParseMetadataDisplay = ParseMetadataFormatter(context).build(info, subjectItem, collectionOverview)

/** 每次从当前界面的配置取文案，避免语言切换后继续显示缓存的旧语言。 */
private class ParseMetadataFormatter(private val context: Context) {
    private val locale = context.resources.configuration.locales[0]
    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    /**
     * 卡片正文和元信息必须指向同一主体，因此主体选择由结果卡解析函数传入。
     * 这里只整理当前解析或既有预览补拉已经取得的字段，不会为了详情再请求接口。
     */
    fun build(
        info: MediaInfo,
        subjectItem: MediaItem?,
        collectionOverview: Boolean,
    ): ParseMetadataDisplay {
        if (collectionOverview) return buildCollectionOverviewMetadata(info)
        if (subjectItem == null) return buildContainerMetadata(info)

        val metadata = subjectItem.metadata
        val publicId = subjectItem.publicContentId()
        val publicIdText = when {
            subjectItem.type == MediaType.Opus && subjectItem.cvid == null && publicId != null ->
                text(R.string.runtime_dynamic_id, publicId)
            else -> publicId
        }
        val quantity = subjectQuantity(subjectItem, metadata)
        val characteristic = subjectCharacteristic(subjectItem.type, metadata)
        val videoStat = subjectItem.stat ?: info.nfo.stat.takeIf {
            subjectItem.type == MediaType.Video && metadata.videoParts.isNotEmpty()
        }

        val contentSections = buildList {
            when (subjectItem.type) {
                MediaType.Video -> addVideoSections(subjectItem, metadata, videoStat)
                MediaType.Bangumi -> addBangumiSections(subjectItem, metadata)
                MediaType.Lesson -> addLessonSections(subjectItem, metadata)
                MediaType.Music -> addMusicSections(subjectItem, metadata)
                MediaType.Opus -> addOpusSections(subjectItem, metadata)
                else -> Unit
            }
            if (metadata.invalid) {
                valueSection(text(R.string.runtime_status), rowsOf(text(R.string.runtime_content_status) to text(R.string.runtime_invalid)))?.let(::add)
            }
        }
        val sections = buildList {
            addAll(contentSections)
            // 容器归属不是当前内容本身的详情；只有存在内容字段时才作为补充来源展示。
            if (contentSections.isNotEmpty()) {
                buildOriginSection(info, subjectItem)?.let(::add)
            }
        }.filterNot { section ->
            when (section) {
                is ParseMetadataSection.Values -> section.rows.isEmpty()
                is ParseMetadataSection.Groups -> section.groups.isEmpty()
                is ParseMetadataSection.Contributors -> section.members.isEmpty()
            }
        }

        return ParseMetadataDisplay(
            subjectKey = subjectKey(subjectItem),
            publicIdText = publicIdText,
            publicIdCopyValue = publicId,
            publicIdCopyName = publicIdCopyName(subjectItem),
            summarySlots = listOfNotNull(publicIdText, quantity, characteristic).take(3),
            sections = sections,
        )
    }

    private fun buildCollectionOverviewMetadata(info: MediaInfo): ParseMetadataDisplay {
        val metadata = info.metadata
        val count = metadata.itemCount ?: info.list.size.takeIf { it > 0 }
        return ParseMetadataDisplay(
            subjectKey = "collection:${metadata.collectionId ?: info.id}",
            publicIdText = text(R.string.runtime_collection),
            publicIdCopyValue = null,
            publicIdCopyName = null,
            summarySlots = listOfNotNull(text(R.string.runtime_collection), count?.let { text(R.string.runtime_count_items, it) }),
            sections = listOfNotNull(
                valueSection(
                    text(R.string.runtime_identifiers),
                    rowsOf(text(R.string.runtime_collection_id) to metadata.collectionId?.toString()),
                ),
                valueSection(
                    text(R.string.runtime_content),
                    rowsOf(
                        text(R.string.runtime_item_count) to count?.let { text(R.string.runtime_count_items, it) },
                        text(R.string.runtime_created_at) to formatEpochSeconds(metadata.createdAt),
                    ),
                ),
            ),
        )
    }

    private fun buildContainerMetadata(info: MediaInfo): ParseMetadataDisplay {
        val metadata = info.metadata
        val publicId = when (info.type) {
            MediaType.MusicList -> normalizedPrefixedId(info.id, "am")
            MediaType.OpusList -> normalizedPrefixedId(info.id, "rl")
            MediaType.Bangumi -> normalizedPrefixedId(info.id, "ss")
            MediaType.Lesson -> normalizedPrefixedId(info.id, "ss")
            else -> null
        }
        val quantity = metadata.itemCount?.let { count ->
            when (info.type) {
                MediaType.MusicList -> text(R.string.runtime_count_songs, count)
                MediaType.OpusList -> text(R.string.runtime_count_articles, count)
                else -> text(R.string.runtime_count_items, count)
            }
        }
        val quantityName = when (info.type) {
            MediaType.MusicList -> text(R.string.runtime_song_count)
            MediaType.OpusList -> text(R.string.runtime_article_count)
            else -> text(R.string.runtime_item_count)
        }
        return ParseMetadataDisplay(
            subjectKey = "container:${info.type}:${info.id}",
            publicIdText = publicId,
            publicIdCopyValue = publicId,
            publicIdCopyName = publicId?.let(::publicIdCopyName),
            summarySlots = listOfNotNull(publicId, quantity),
            sections = listOfNotNull(
                valueSection(
                    text(R.string.runtime_identifiers_content),
                    rowsOf(
                        publicIdLabel(publicId) to publicId,
                        text(R.string.runtime_created_at) to formatEpochSeconds(metadata.createdAt),
                        quantityName to quantity,
                        text(R.string.runtime_tags) to metadata.tags.joinToString(text(R.string.runtime_list_separator)).takeIf(String::isNotBlank),
                    ),
                ),
            ),
        )
    }

    private fun MutableList<ParseMetadataSection>.addVideoSections(
        item: MediaItem,
        metadata: MediaMetadata,
        stat: MediaStat?,
    ) {
        val isMultiPart = (metadata.partCount ?: metadata.videoParts.size) > 1
        val singlePart = metadata.videoParts.singleOrNull()
        valueSection(
            text(R.string.runtime_identifiers),
            rowsOf(
                "BV" to item.bvid,
                "AV" to item.aid?.let { "AV$it" },
                "cid" to if (isMultiPart) {
                    null
                } else {
                    (item.cid ?: singlePart?.cid)?.toString()
                },
            ),
        )?.let(::add)

        val copyrightType = when (metadata.copyrightType) {
            MediaCopyrightType.Original -> text(R.string.runtime_original)
            MediaCopyrightType.Repost -> text(R.string.runtime_repost)
            null -> null
        }
        val copyright = when {
            copyrightType != null && metadata.noReprint -> text(R.string.runtime_copyright_no_reprint, copyrightType)
            copyrightType != null -> copyrightType
            metadata.noReprint -> text(R.string.runtime_no_reprint)
            else -> null
        }
        valueSection(
            text(R.string.runtime_attributes),
            rowsOf(
                text(R.string.runtime_type) to copyright,
                text(R.string.runtime_special_attributes) to metadata.rareAttributes.toAttributeLabels().joinToString(text(R.string.runtime_list_separator))
                    .takeIf(String::isNotBlank),
                text(R.string.runtime_video_status) to metadata.videoState.toVideoStateLabel(),
                text(R.string.runtime_warning) to metadata.warning,
                text(R.string.runtime_redirect_video) to metadata.collisionBvid,
            ),
        )?.let(::add)

        valueSection(
            text(R.string.runtime_time_categories),
            rowsOf(
                text(R.string.runtime_total_duration) to formatMetadataDuration(metadata.totalDuration ?: item.duration, locale),
                text(R.string.runtime_legacy_category) to metadata.legacyCategory?.displayName(),
                text(R.string.runtime_modern_category) to metadata.modernCategory?.displayName(),
                text(R.string.runtime_resolution) to if (isMultiPart) {
                    null
                } else {
                    formatResolution(metadata.resolution ?: singlePart?.resolution)
                },
                text(R.string.runtime_published_at) to formatEpochSeconds(
                    metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
                ),
            ) + listOfNotNull(submittedAtRow(metadata.submittedAt)),
        )?.let(::add)

        val honorRows = buildList {
            metadata.honors.forEach { honor ->
                honor.description.trim().takeIf(String::isNotBlank)?.let {
                    add(ParseMetadataRow(text(R.string.runtime_honors), it))
                }
            }
            metadata.currentRank?.takeIf { it > 0 }?.let {
                add(ParseMetadataRow(text(R.string.runtime_current_rank), text(R.string.runtime_rank_position, it)))
            }
            val honorAlreadyContainsHistoricalRank = metadata.honors.any { it.type == 3 }
            if (!honorAlreadyContainsHistoricalRank) {
                metadata.historicalRank?.takeIf { it > 0 }?.let {
                    add(ParseMetadataRow(text(R.string.runtime_highest_rank), text(R.string.runtime_rank_position, it)))
                }
            }
            metadata.evaluation?.trim()?.takeIf(String::isNotBlank)?.let {
                add(ParseMetadataRow(text(R.string.runtime_rating), it))
            }
        }
        valueSection(text(R.string.runtime_honors_rank), honorRows)?.let(::add)

        valueSection(text(R.string.runtime_statistics), stat.toDetailRows())?.let(::add)

        valueSection(
            text(R.string.runtime_content),
            rowsOf(
                text(R.string.runtime_dynamic_text) to metadata.dynamicText,
                text(R.string.runtime_tags) to metadata.tags.joinToString(text(R.string.runtime_list_separator)).takeIf(String::isNotBlank),
            ),
        )?.let(::add)

        val partGroups = metadata.videoParts.takeIf { isMultiPart }.orEmpty().map { part ->
            ParseMetadataGroup(
                title = "P${part.page}",
                subtitle = part.title?.trim()?.takeIf(String::isNotBlank),
                previewUrl = part.firstFrameUrl,
                rows = rowsOf(
                    text(R.string.runtime_duration) to formatMetadataDuration(part.duration, locale),
                    text(R.string.runtime_resolution) to formatResolution(part.resolution),
                    "cid" to part.cid?.toString(),
                ) + listOfNotNull(submittedAtRow(part.submittedAt)),
            )
        }.filter { it.rows.isNotEmpty() || it.subtitle != null }
        if (partGroups.isNotEmpty()) {
            add(ParseMetadataSection.Groups(text(R.string.runtime_parts), partGroups))
        }

        if (metadata.contributors.isNotEmpty()) {
            add(ParseMetadataSection.Contributors(text(R.string.runtime_contributors), metadata.contributors))
        }
    }

    private fun MutableList<ParseMetadataSection>.addBangumiSections(
        item: MediaItem,
        metadata: MediaMetadata,
    ) {
        valueSection(
            text(R.string.runtime_identifiers),
            rowsOf(
                "ep" to item.epid?.let { "ep$it" },
                "ss" to item.ssid?.let { "ss$it" },
                "md" to (item.mdid?.let { "md$it" } ?: metadata.mediaId?.let { "md$it" }),
                "BV" to item.bvid,
                "AV" to item.aid?.let { "AV$it" },
                "cid" to item.cid?.toString(),
            ),
        )?.let(::add)
        valueSection(
            text(R.string.runtime_attributes),
            rowsOf(
                text(R.string.runtime_access) to metadata.badges.distinct().joinToString(text(R.string.runtime_list_separator)).takeIf(String::isNotBlank),
                text(R.string.runtime_copyright) to metadata.copyrightCode?.let { code ->
                    when (code.lowercase(Locale.ROOT)) {
                        "bilibili" -> text(R.string.media_copyright_licensed)
                        "dujia" -> text(R.string.media_copyright_exclusive)
                        else -> code
                    }
                },
                text(R.string.runtime_release_status) to metadata.isCompleted?.let { if (it) text(R.string.runtime_completed) else text(R.string.runtime_ongoing) },
                text(R.string.runtime_release_notes) to metadata.updateText,
            ),
        )?.let(::add)
        valueSection(
            text(R.string.runtime_time_classification),
            rowsOf(
                text(R.string.runtime_duration) to formatMetadataDuration(metadata.totalDuration ?: item.duration, locale),
                text(R.string.runtime_published_at) to formatEpochSeconds(
                    metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
                ),
                text(R.string.runtime_resolution) to formatResolution(metadata.resolution),
                text(R.string.runtime_series_type) to metadata.contentKind?.displayName(),
                text(R.string.runtime_region) to metadata.area,
                text(R.string.runtime_rating) to metadata.rating?.let(::formatRating),
                text(R.string.runtime_tags) to metadata.tags.joinToString(text(R.string.runtime_list_separator)).takeIf(String::isNotBlank),
            ),
        )?.let(::add)
        valueSection(
            text(R.string.runtime_production),
            rowsOf(
                text(R.string.runtime_parent_content) to item.sectionTitle,
                text(R.string.runtime_cast) to metadata.actors,
                text(R.string.runtime_staff) to metadata.productionStaff,
            ),
        )?.let(::add)
    }

    private fun MutableList<ParseMetadataSection>.addLessonSections(
        item: MediaItem,
        metadata: MediaMetadata,
    ) {
        valueSection(
            text(R.string.runtime_identifiers),
            rowsOf(
                text(R.string.runtime_lesson_ep) to item.epid?.let { "ep$it" },
                text(R.string.runtime_lesson_ss) to item.ssid?.let { "ss$it" },
                "aid" to item.aid?.toString(),
                "cid" to item.cid?.toString(),
            ),
        )?.let(::add)
        valueSection(
            text(R.string.runtime_attributes),
            rowsOf(
                text(R.string.runtime_access) to metadata.access?.displayName(),
                text(R.string.runtime_price_description) to metadata.payment?.description,
                text(R.string.runtime_price) to metadata.payment?.priceBCoins?.let { text(R.string.media_price_bcoin, it) },
            ),
        )?.let(::add)
        valueSection(
            text(R.string.runtime_time),
            rowsOf(
                text(R.string.runtime_duration) to formatMetadataDuration(metadata.totalDuration ?: item.duration, locale),
                text(R.string.runtime_published_at) to formatEpochSeconds(
                    metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
                ),
                text(R.string.runtime_release_status) to metadata.updateText,
            ),
        )?.let(::add)
    }

    private fun MutableList<ParseMetadataSection>.addMusicSections(
        item: MediaItem,
        metadata: MediaMetadata,
    ) {
        valueSection(
            text(R.string.runtime_identifiers),
            rowsOf(
                "au" to item.sid?.let { "au$it" },
                text(R.string.runtime_related_video) to item.bvid,
                text(R.string.runtime_related_av) to item.aid?.takeIf { it > 0L }?.let { "AV$it" },
                text(R.string.runtime_related_cid) to item.cid?.takeIf { it > 0L }?.toString(),
                text(R.string.runtime_playlist) to item.amid?.let { "am$it" },
            ),
        )?.let(::add)
        valueSection(
            text(R.string.runtime_author),
            rowsOf(text(R.string.runtime_artist_author) to (metadata.artist ?: item.artist)),
        )?.let(::add)
        valueSection(
            text(R.string.runtime_time_tags),
            rowsOf(
                text(R.string.runtime_duration) to formatMetadataDuration(metadata.totalDuration ?: item.duration, locale),
                text(R.string.runtime_published_at) to formatEpochSeconds(
                    metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
                ),
                text(R.string.runtime_tags) to metadata.tags.joinToString(text(R.string.runtime_list_separator)).takeIf(String::isNotBlank),
            ),
        )?.let(::add)
    }

    private fun MutableList<ParseMetadataSection>.addOpusSections(
        item: MediaItem,
        metadata: MediaMetadata,
    ) {
        valueSection(
            text(R.string.runtime_identifiers),
            rowsOf(
                "cv" to item.cvid?.let { "cv$it" },
                text(R.string.runtime_opus_id) to item.opid,
                text(R.string.runtime_article_collection) to item.rlid?.let { "rl$it" },
            ),
        )?.let(::add)
        valueSection(
            text(R.string.runtime_content),
            rowsOf(
                text(R.string.runtime_published_at) to formatEpochSeconds(
                    metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
                ),
                text(R.string.runtime_tags) to metadata.tags.joinToString(text(R.string.runtime_list_separator)).takeIf(String::isNotBlank),
                text(R.string.runtime_image_count) to metadata.imageCount?.let { text(R.string.runtime_count_images, it) },
            ),
        )?.let(::add)
    }

    private fun buildOriginSection(info: MediaInfo, item: MediaItem): ParseMetadataSection.Values? {
        val rows = when (info.type) {
            MediaType.Favorite -> rowsOf(
                text(R.string.runtime_favorite_folder) to item.fid?.let { fid ->
                    listOfNotNull(info.nfo.showTitle?.trim()?.takeIf(String::isNotBlank), "fid$fid")
                        .joinToString(" · ")
                },
                text(R.string.runtime_favorite_created_at) to formatEpochSeconds(info.metadata.createdAt),
                text(R.string.runtime_favorite_count) to info.metadata.itemCount?.let { text(R.string.runtime_count_items, it) },
            )
            MediaType.WatchLater -> rowsOf(
                text(R.string.runtime_content_source) to text(R.string.runtime_watch_later),
                text(R.string.runtime_list_count) to info.metadata.itemCount?.let { text(R.string.runtime_count_items, it) },
            )
            MediaType.UserVideo,
            MediaType.UserOpus,
            MediaType.UserAudio,
            -> rowsOf(
                text(R.string.runtime_uploader_space) to item.sourceMid?.let { mid ->
                    listOfNotNull(info.nfo.upper?.name?.trim()?.takeIf(String::isNotBlank), "mid$mid")
                        .joinToString(" · ")
                },
                text(R.string.runtime_list_created_at) to formatEpochSeconds(info.metadata.createdAt),
                text(R.string.runtime_list_count) to info.metadata.itemCount?.let { text(R.string.runtime_count_items, it) },
            )
            MediaType.Video -> rowsOf(
                text(R.string.runtime_parent_collection) to item.metadata.collectionId?.let { collectionId ->
                    listOfNotNull(
                        info.nfo.showTitle?.trim()?.takeIf(String::isNotBlank),
                        collectionId.toString(),
                    ).joinToString(" · ")
                },
                text(R.string.runtime_collection_category) to item.sectionTitle,
                text(R.string.runtime_collection_count) to info.metadata.itemCount?.let { text(R.string.runtime_count_items, it) },
            )
            MediaType.Bangumi -> rowsOf(
                text(R.string.runtime_episode_count) to info.metadata.itemCount?.let { text(R.string.runtime_count_episodes, it) },
            )
            MediaType.Lesson -> rowsOf(
                text(R.string.runtime_lesson_count) to info.metadata.itemCount?.let { text(R.string.runtime_count_lessons, it) },
            )
            MediaType.MusicList -> rowsOf(
                text(R.string.runtime_playlist_created_at) to formatEpochSeconds(info.metadata.createdAt),
                text(R.string.runtime_song_count) to info.metadata.itemCount?.let { text(R.string.runtime_count_songs, it) },
            )
            MediaType.OpusList -> rowsOf(
                text(R.string.runtime_article_collection_created_at) to formatEpochSeconds(info.metadata.createdAt),
                text(R.string.runtime_article_count) to info.metadata.itemCount?.let { text(R.string.runtime_count_articles, it) },
            )
            else -> emptyList()
        }
        return valueSection(text(R.string.runtime_source), rows)
    }

    private fun subjectQuantity(item: MediaItem, metadata: MediaMetadata): String? {
        return when (item.type) {
            MediaType.Video -> {
                val duration = formatMetadataDuration(metadata.totalDuration ?: item.duration, locale)
                val partCount = metadata.partCount
                    ?: metadata.videoParts.size.takeIf { it > 1 }
                partCount?.takeIf { it > 1 }?.let {
                    listOfNotNull("${it}P", duration).joinToString(" · ")
                } ?: duration
            }
            MediaType.Bangumi,
            MediaType.Lesson,
            MediaType.Music,
            -> formatMetadataDuration(metadata.totalDuration ?: item.duration, locale)
            MediaType.Opus -> metadata.imageCount?.takeIf { it > 0 }?.let { text(R.string.runtime_count_images, it) }
            else -> null
        }
    }

    private fun subjectCharacteristic(type: MediaType, metadata: MediaMetadata): String? {
        metadata.rareAttributes.firstSummaryLabel()?.let { return it }
        return when (type) {
            // 摘要只放新版分区；旧分区留在详情，避免同一行出现两套分类。
            MediaType.Video -> metadata.modernCategory?.displayName()
            MediaType.Bangumi -> metadata.contentKind?.displayName() ?: metadata.area
            MediaType.Lesson -> metadata.access?.takeUnless { it == MediaAccess.Available }?.displayName()
                ?: metadata.updateText
            MediaType.Music,
            MediaType.Opus,
            -> metadata.tags.firstOrNull()
            else -> null
        }?.trim()?.takeIf(String::isNotBlank)
    }


    private fun MediaCategory.displayName(): String =
        if (offline) text(R.string.runtime_category_offline, name) else name

    private fun MediaContentKind.displayName(): String = text(when (this) {
        MediaContentKind.Anime -> R.string.media_kind_anime
        MediaContentKind.Movie -> R.string.media_kind_movie
        MediaContentKind.Documentary -> R.string.media_kind_documentary
        MediaContentKind.ChineseAnimation -> R.string.media_kind_chinese_animation
        MediaContentKind.Series -> R.string.media_kind_series
        MediaContentKind.Variety -> R.string.media_kind_variety
    })

    private fun MediaAccess.displayName(): String = text(when (this) {
        MediaAccess.Available -> R.string.media_access_available
        MediaAccess.PurchaseRequired -> R.string.media_access_purchase
        MediaAccess.Unavailable -> R.string.media_access_unavailable
    })

    private fun Set<MediaRareAttribute>.firstSummaryLabel(): String? {
        val priority = listOf(
            MediaRareAttribute.Interactive,
            MediaRareAttribute.Panorama,
            MediaRareAttribute.ChargeExclusive,
            MediaRareAttribute.VipOnly,
            MediaRareAttribute.LimitedFree,
            MediaRareAttribute.PurchaseRequired,
            MediaRareAttribute.Cooperation,
            MediaRareAttribute.DynamicVideo,
        )
        return priority.firstOrNull(::contains)?.label()
    }

    private fun Set<MediaRareAttribute>.toAttributeLabels(): List<String> {
        return MediaRareAttribute.entries.filter(::contains).map { it.label() }
    }

    private fun MediaRareAttribute.label(): String = when (this) {
        MediaRareAttribute.Cooperation -> text(R.string.runtime_cooperation)
        MediaRareAttribute.Interactive -> text(R.string.runtime_interactive)
        MediaRareAttribute.Panorama -> text(R.string.runtime_panorama)
        MediaRareAttribute.ChargeExclusive -> text(R.string.runtime_supporter_only)
        MediaRareAttribute.VipOnly -> text(R.string.runtime_premium)
        MediaRareAttribute.LimitedFree -> text(R.string.runtime_limited_free)
        MediaRareAttribute.PurchaseRequired -> text(R.string.runtime_purchase_required)
        MediaRareAttribute.DynamicVideo -> text(R.string.runtime_dynamic_video)
    }

    private fun Int?.toVideoStateLabel(): String? = when (this) {
        null, 0 -> null
        1 -> text(R.string.runtime_approved_limited)
        -1 -> text(R.string.runtime_pending_review)
        -2 -> text(R.string.runtime_rejected)
        -3 -> text(R.string.runtime_locked_police)
        -4 -> text(R.string.runtime_locked)
        -5 -> text(R.string.runtime_locked_admin)
        -6 -> text(R.string.runtime_repair_review)
        -7 -> text(R.string.runtime_review_suspended)
        -8 -> text(R.string.runtime_replacement_review)
        -9 -> text(R.string.runtime_transcode_pending)
        -10 -> text(R.string.runtime_review_delayed)
        -11 -> text(R.string.runtime_source_repair)
        -12 -> text(R.string.runtime_dump_failed)
        -13 -> text(R.string.runtime_comments_review)
        -14 -> text(R.string.runtime_temporary_trash)
        -15 -> text(R.string.runtime_distributing)
        -16 -> text(R.string.runtime_transcode_failed)
        -20 -> text(R.string.runtime_not_submitted)
        -30 -> text(R.string.runtime_submitted)
        -40 -> text(R.string.runtime_scheduled)
        -50 -> text(R.string.runtime_private)
        -100 -> text(R.string.runtime_deleted)
        else -> text(R.string.runtime_not_public)
    }

    private fun MediaStat?.toDetailRows(): List<ParseMetadataRow> {
        val value = this ?: return emptyList()
        return rowsOf(
            text(R.string.runtime_plays) to value.play?.takeIf { it > 0L }?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) },
            text(R.string.runtime_danmaku) to value.danmaku?.takeIf { it > 0L }?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) },
            text(R.string.runtime_comments) to value.reply?.takeIf { it > 0L }?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) },
            text(R.string.runtime_likes) to value.like?.takeIf { it > 0L }?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) },
            text(R.string.runtime_coins) to value.coin?.takeIf { it > 0L }?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) },
            text(R.string.runtime_favorites) to value.favorite?.takeIf { it > 0L }?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) },
            text(R.string.runtime_shares) to value.share?.takeIf { it > 0L }?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) },
        )
    }


    private fun formatEpochSeconds(epochSeconds: Long?): String? {
        val timestamp = epochSeconds?.takeIf { it > 0L } ?: return null
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochSecond(timestamp))
    }

    /**
     * view 接口 `ctime` 的展示行。API 文档把 ctime 描述为「用户投稿时间」，但实测并不可靠
     * （常与 pubdate 完全一致、老稿件会落在 2017 年，官方页面也只展示 pubdate，详见
     * [MediaMetadata.submittedAt]），因此写成「投稿/过审时间」而非确切的「投稿时间」，并附上可能不准确的提示。
     */
    private fun submittedAtRow(epochSeconds: Long?): ParseMetadataRow? {
        val value = formatEpochSeconds(epochSeconds) ?: return null
        return ParseMetadataRow(name = text(R.string.runtime_submitted_at), value = value, note = text(R.string.runtime_time_uncertain))
    }

    private fun formatResolution(resolution: MediaResolution?): String? {
        val value = resolution ?: return null
        val swapDimensions = value.rotate == 1 || value.rotate.absoluteValue % 180 == 90
        val width = if (swapDimensions) value.height else value.width
        val height = if (swapDimensions) value.width else value.height
        return "$width×$height"
    }


    private fun formatRating(rating: Double): String {
        return if (rating % 1.0 == 0.0) {
            java.text.NumberFormat.getIntegerInstance(locale).format(rating.toInt())
        } else {
            String.format(locale, "%.1f", rating)
        }
    }

    private fun rowsOf(vararg pairs: Pair<String, String?>): List<ParseMetadataRow> {
        return pairs.mapNotNull { (name, rawValue) ->
            val value = rawValue?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            ParseMetadataRow(name, value)
        }
    }

    private fun valueSection(
        title: String,
        rows: List<ParseMetadataRow>,
    ): ParseMetadataSection.Values? = if (rows.isEmpty()) {
        null
    } else {
        ParseMetadataSection.Values(title, rows)
    }

    private fun normalizedPrefixedId(rawId: String, prefix: String): String? {
        val digits = rawId.filter(Char::isDigit)
        return digits.takeIf(String::isNotBlank)?.let { "$prefix$it" }
    }

    private fun publicIdLabel(publicId: String?): String = when {
        publicId?.startsWith("am") == true -> "am"
        publicId?.startsWith("rl") == true -> "rl"
        publicId?.startsWith("ss") == true -> "ss"
        publicId?.startsWith("ep") == true -> "ep"
        else -> text(R.string.runtime_content_id)
    }

    private fun publicIdCopyName(item: MediaItem): String? = when (item.type) {
        MediaType.Video -> item.bvid?.let { text(R.string.runtime_bv_id) }
        MediaType.Bangumi,
        MediaType.Lesson,
        -> when {
            item.epid != null -> text(R.string.runtime_ep_id)
            item.ssid != null -> text(R.string.runtime_ss_id)
            else -> null
        }
        MediaType.Music -> item.sid?.let { text(R.string.runtime_au_id) }
        MediaType.Opus -> when {
            item.cvid != null -> text(R.string.runtime_cv_id)
            !item.opid.isNullOrBlank() -> text(R.string.runtime_opus_id)
            else -> null
        }
        else -> null
    }

    private fun publicIdCopyName(publicId: String): String = when {
        publicId.startsWith("am", ignoreCase = true) -> text(R.string.runtime_am_id)
        publicId.startsWith("rl", ignoreCase = true) -> text(R.string.runtime_rl_id)
        publicId.startsWith("ep", ignoreCase = true) -> text(R.string.runtime_ep_id)
        publicId.startsWith("ss", ignoreCase = true) -> text(R.string.runtime_ss_id)
        publicId.startsWith("au", ignoreCase = true) -> text(R.string.runtime_au_id)
        publicId.startsWith("cv", ignoreCase = true) -> text(R.string.runtime_cv_id)
        publicId.startsWith("BV", ignoreCase = true) -> text(R.string.runtime_bv_id)
        else -> text(R.string.runtime_content_id)
    }

    private fun subjectKey(item: MediaItem): String {
        return item.publicContentId()
            ?: "${item.type}:${item.url}:${item.index}"
    }
}

internal fun formatMetadataDuration(seconds: Int?, locale: Locale = Locale.getDefault()): String? {
    val total = seconds?.takeIf { it > 0 } ?: return null
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val remainingSeconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(locale, hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(locale, minutes, remainingSeconds)
    }
}

internal fun formatMediaStatValue(value: Long, locale: Locale = Locale.getDefault()): String =
    CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(value)
