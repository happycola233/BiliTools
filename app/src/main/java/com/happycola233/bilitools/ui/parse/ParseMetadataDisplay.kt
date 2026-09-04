package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaContributor
import com.happycola233.bilitools.data.model.MediaCopyrightType
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaMetadata
import com.happycola233.bilitools.data.model.MediaRareAttribute
import com.happycola233.bilitools.data.model.MediaResolution
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

/**
 * 卡片正文和元信息必须指向同一主体，因此主体选择由结果卡解析函数传入。
 * 这里只整理当前解析或既有预览补拉已经取得的字段，不会为了详情再请求接口。
 */
internal fun buildParseMetadataDisplay(
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
            "动态 $publicId"
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
            valueSection("状态", rowsOf("内容状态" to "已失效"))?.let(::add)
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
        publicIdText = "合集",
        publicIdCopyValue = null,
        publicIdCopyName = null,
        summarySlots = listOfNotNull("合集", count?.let { "$it 条" }),
        sections = listOfNotNull(
            valueSection(
                "标识",
                rowsOf("合集编号" to metadata.collectionId?.toString()),
            ),
            valueSection(
                "内容",
                rowsOf(
                    "条目数量" to count?.let { "$it 条" },
                    "创建时间" to formatEpochSeconds(metadata.createdAt),
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
            MediaType.MusicList -> "$count 首"
            MediaType.OpusList -> "$count 篇"
            else -> "$count 条"
        }
    }
    val quantityName = when (info.type) {
        MediaType.MusicList -> "歌曲数量"
        MediaType.OpusList -> "文章数量"
        else -> "条目数量"
    }
    return ParseMetadataDisplay(
        subjectKey = "container:${info.type}:${info.id}",
        publicIdText = publicId,
        publicIdCopyValue = publicId,
        publicIdCopyName = publicId?.let(::publicIdCopyName),
        summarySlots = listOfNotNull(publicId, quantity),
        sections = listOfNotNull(
            valueSection(
                "标识与内容",
                rowsOf(
                    publicIdLabel(publicId) to publicId,
                    "创建时间" to formatEpochSeconds(metadata.createdAt),
                    quantityName to quantity,
                    "标签" to metadata.tags.joinToString("、").takeIf(String::isNotBlank),
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
        "标识",
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
        MediaCopyrightType.Original -> "自制"
        MediaCopyrightType.Repost -> "转载"
        null -> null
    }
    val copyright = when {
        copyrightType != null && metadata.noReprint -> "$copyrightType（禁止转载）"
        copyrightType != null -> copyrightType
        metadata.noReprint -> "禁止转载"
        else -> null
    }
    valueSection(
        "属性",
        rowsOf(
            "类型" to copyright,
            "特殊属性" to metadata.rareAttributes.toAttributeLabels().joinToString("、")
                .takeIf(String::isNotBlank),
            "视频状态" to metadata.videoState.toVideoStateLabel(),
            "警告" to metadata.warning,
            "撞车跳转" to metadata.collisionBvid,
        ),
    )?.let(::add)

    valueSection(
        "时间与分区",
        rowsOf(
            "总时长" to formatDuration(metadata.totalDuration ?: item.duration),
            "分区（旧）" to metadata.legacyCategory,
            "分区（新）" to metadata.modernCategory,
            "分辨率" to if (isMultiPart) {
                null
            } else {
                formatResolution(metadata.resolution ?: singlePart?.resolution)
            },
            "发布时间" to formatEpochSeconds(
                metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
            ),
        ) + listOfNotNull(submittedAtRow(metadata.submittedAt)),
    )?.let(::add)

    val honorRows = buildList {
        metadata.honors.forEach { honor ->
            honor.description.trim().takeIf(String::isNotBlank)?.let {
                add(ParseMetadataRow("荣誉", it))
            }
        }
        metadata.currentRank?.takeIf { it > 0 }?.let {
            add(ParseMetadataRow("当前排名", "第 $it 名"))
        }
        val honorAlreadyContainsHistoricalRank = metadata.honors.any { it.type == 3 }
        if (!honorAlreadyContainsHistoricalRank) {
            metadata.historicalRank?.takeIf { it > 0 }?.let {
                add(ParseMetadataRow("历史最高排名", "第 $it 名"))
            }
        }
        metadata.evaluation?.trim()?.takeIf(String::isNotBlank)?.let {
            add(ParseMetadataRow("评分", it))
        }
    }
    valueSection("荣誉与排名", honorRows)?.let(::add)

    valueSection("播放数据", stat.toDetailRows())?.let(::add)

    valueSection(
        "内容",
        rowsOf(
            "同步动态" to metadata.dynamicText,
            "标签" to metadata.tags.joinToString("、").takeIf(String::isNotBlank),
        ),
    )?.let(::add)

    val partGroups = metadata.videoParts.takeIf { isMultiPart }.orEmpty().map { part ->
        ParseMetadataGroup(
            title = "P${part.page}",
            subtitle = part.title?.trim()?.takeIf(String::isNotBlank),
            previewUrl = part.firstFrameUrl,
            rows = rowsOf(
                "时长" to formatDuration(part.duration),
                "分辨率" to formatResolution(part.resolution),
                "cid" to part.cid?.toString(),
            ) + listOfNotNull(submittedAtRow(part.submittedAt)),
        )
    }.filter { it.rows.isNotEmpty() || it.subtitle != null }
    if (partGroups.isNotEmpty()) {
        add(ParseMetadataSection.Groups("分 P", partGroups))
    }

    if (metadata.contributors.isNotEmpty()) {
        add(ParseMetadataSection.Contributors("合作成员", metadata.contributors))
    }
}

private fun MutableList<ParseMetadataSection>.addBangumiSections(
    item: MediaItem,
    metadata: MediaMetadata,
) {
    valueSection(
        "标识",
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
        "属性",
        rowsOf(
            "观看权限" to metadata.badges.distinct().joinToString("、").takeIf(String::isNotBlank),
            "版权" to metadata.copyrightLabel,
            "更新状态" to metadata.isCompleted?.let { if (it) "已完结" else "连载中" },
            "更新说明" to metadata.updateText,
        ),
    )?.let(::add)
    valueSection(
        "时间与分类",
        rowsOf(
            "时长" to formatDuration(metadata.totalDuration ?: item.duration),
            "发布时间" to formatEpochSeconds(
                metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
            ),
            "分辨率" to formatResolution(metadata.resolution),
            "剧集类型" to metadata.contentKind,
            "地区" to metadata.area,
            "评分" to metadata.rating?.let(::formatRating),
            "标签" to metadata.tags.joinToString("、").takeIf(String::isNotBlank),
        ),
    )?.let(::add)
    valueSection(
        "制作",
        rowsOf(
            "所属内容" to item.sectionTitle,
            "声优" to metadata.actors,
            "制作人员" to metadata.productionStaff,
        ),
    )?.let(::add)
}

private fun MutableList<ParseMetadataSection>.addLessonSections(
    item: MediaItem,
    metadata: MediaMetadata,
) {
    valueSection(
        "标识",
        rowsOf(
            "课程 ep" to item.epid?.let { "ep$it" },
            "课程 ss" to item.ssid?.let { "ss$it" },
            "aid" to item.aid?.toString(),
            "cid" to item.cid?.toString(),
        ),
    )?.let(::add)
    valueSection(
        "属性",
        rowsOf(
            "观看权限" to metadata.accessLabel,
            "价格说明" to metadata.payment?.description,
            "价格" to metadata.payment?.price,
        ),
    )?.let(::add)
    valueSection(
        "时间",
        rowsOf(
            "时长" to formatDuration(metadata.totalDuration ?: item.duration),
            "发布时间" to formatEpochSeconds(
                metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
            ),
            "更新状态" to metadata.updateText,
        ),
    )?.let(::add)
}

private fun MutableList<ParseMetadataSection>.addMusicSections(
    item: MediaItem,
    metadata: MediaMetadata,
) {
    valueSection(
        "标识",
        rowsOf(
            "au" to item.sid?.let { "au$it" },
            "关联稿件" to item.bvid,
            "关联 AV" to item.aid?.takeIf { it > 0L }?.let { "AV$it" },
            "关联 cid" to item.cid?.takeIf { it > 0L }?.toString(),
            "所在歌单" to item.amid?.let { "am$it" },
        ),
    )?.let(::add)
    valueSection(
        "作者",
        rowsOf("演唱 / 作者" to (metadata.artist ?: item.artist)),
    )?.let(::add)
    valueSection(
        "时间与标签",
        rowsOf(
            "时长" to formatDuration(metadata.totalDuration ?: item.duration),
            "发布时间" to formatEpochSeconds(
                metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
            ),
            "标签" to metadata.tags.joinToString("、").takeIf(String::isNotBlank),
        ),
    )?.let(::add)
}

private fun MutableList<ParseMetadataSection>.addOpusSections(
    item: MediaItem,
    metadata: MediaMetadata,
) {
    valueSection(
        "标识",
        rowsOf(
            "cv" to item.cvid?.let { "cv$it" },
            "图文动态号" to item.opid,
            "所在文集" to item.rlid?.let { "rl$it" },
        ),
    )?.let(::add)
    valueSection(
        "内容",
        rowsOf(
            "发布时间" to formatEpochSeconds(
                metadata.publishedAt ?: item.pubTime.takeIf { it > 0L },
            ),
            "标签" to metadata.tags.joinToString("、").takeIf(String::isNotBlank),
            "图片数量" to metadata.imageCount?.let { "$it 张" },
        ),
    )?.let(::add)
}

private fun buildOriginSection(info: MediaInfo, item: MediaItem): ParseMetadataSection.Values? {
    val rows = when (info.type) {
        MediaType.Favorite -> rowsOf(
            "所在收藏夹" to item.fid?.let { fid ->
                listOfNotNull(info.nfo.showTitle?.trim()?.takeIf(String::isNotBlank), "fid$fid")
                    .joinToString(" · ")
            },
            "收藏夹创建时间" to formatEpochSeconds(info.metadata.createdAt),
            "收藏夹内容数量" to info.metadata.itemCount?.let { "$it 条" },
        )
        MediaType.WatchLater -> rowsOf(
            "内容来源" to "稍后再看",
            "列表内容数量" to info.metadata.itemCount?.let { "$it 条" },
        )
        MediaType.UserVideo,
        MediaType.UserOpus,
        MediaType.UserAudio,
        -> rowsOf(
            "来自空间" to item.sourceMid?.let { mid ->
                listOfNotNull(info.nfo.upper?.name?.trim()?.takeIf(String::isNotBlank), "mid$mid")
                    .joinToString(" · ")
            },
            "列表创建时间" to formatEpochSeconds(info.metadata.createdAt),
            "列表内容数量" to info.metadata.itemCount?.let { "$it 条" },
        )
        MediaType.Video -> rowsOf(
            "所属合集" to item.metadata.collectionId?.let { collectionId ->
                listOfNotNull(
                    info.nfo.showTitle?.trim()?.takeIf(String::isNotBlank),
                    collectionId.toString(),
                ).joinToString(" · ")
            },
            "合集分区" to item.sectionTitle,
            "合集条目数量" to info.metadata.itemCount?.let { "$it 条" },
        )
        MediaType.Bangumi -> rowsOf(
            "正片集数" to info.metadata.itemCount?.let { "$it 集" },
        )
        MediaType.Lesson -> rowsOf(
            "课程课时数" to info.metadata.itemCount?.let { "$it 节" },
        )
        MediaType.MusicList -> rowsOf(
            "歌单创建时间" to formatEpochSeconds(info.metadata.createdAt),
            "歌曲数量" to info.metadata.itemCount?.let { "$it 首" },
        )
        MediaType.OpusList -> rowsOf(
            "文集创建时间" to formatEpochSeconds(info.metadata.createdAt),
            "文章数量" to info.metadata.itemCount?.let { "$it 篇" },
        )
        else -> emptyList()
    }
    return valueSection("来源", rows)
}

private fun subjectQuantity(item: MediaItem, metadata: MediaMetadata): String? {
    return when (item.type) {
        MediaType.Video -> {
            val duration = formatDuration(metadata.totalDuration ?: item.duration)
            val partCount = metadata.partCount
                ?: metadata.videoParts.size.takeIf { it > 1 }
            partCount?.takeIf { it > 1 }?.let {
                listOfNotNull("${it}P", duration).joinToString(" · ")
            } ?: duration
        }
        MediaType.Bangumi,
        MediaType.Lesson,
        MediaType.Music,
        -> formatDuration(metadata.totalDuration ?: item.duration)
        MediaType.Opus -> metadata.imageCount?.takeIf { it > 0 }?.let { "$it 张" }
        else -> null
    }
}

private fun subjectCharacteristic(type: MediaType, metadata: MediaMetadata): String? {
    metadata.rareAttributes.firstSummaryLabel()?.let { return it }
    return when (type) {
        // 摘要只放新版分区；旧分区留在详情，避免同一行出现两套分类。
        MediaType.Video -> metadata.modernCategory
        MediaType.Bangumi -> metadata.contentKind ?: metadata.area
        MediaType.Lesson -> metadata.accessLabel?.takeUnless { it == "可观看" }
            ?: metadata.updateText
        MediaType.Music,
        MediaType.Opus,
        -> metadata.tags.firstOrNull()
        else -> null
    }?.trim()?.takeIf(String::isNotBlank)
}

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
    return MediaRareAttribute.entries.filter(::contains).map(MediaRareAttribute::label)
}

private fun MediaRareAttribute.label(): String = when (this) {
    MediaRareAttribute.Cooperation -> "合作"
    MediaRareAttribute.Interactive -> "互动"
    MediaRareAttribute.Panorama -> "全景"
    MediaRareAttribute.ChargeExclusive -> "充电专属"
    MediaRareAttribute.VipOnly -> "大会员"
    MediaRareAttribute.LimitedFree -> "限免"
    MediaRareAttribute.PurchaseRequired -> "需购买"
    MediaRareAttribute.DynamicVideo -> "动态视频"
}

private fun Int?.toVideoStateLabel(): String? = when (this) {
    null, 0 -> null
    1 -> "橙色通过"
    -1 -> "待审"
    -2 -> "已打回"
    -3 -> "已被网警锁定"
    -4 -> "已锁定"
    -5 -> "已被管理员锁定"
    -6 -> "修复待审"
    -7 -> "暂缓审核"
    -8 -> "补档待审"
    -9 -> "等待转码"
    -10 -> "延迟审核"
    -11 -> "视频源待修复"
    -12 -> "转储失败"
    -13 -> "评论权限待审"
    -14 -> "临时回收站"
    -15 -> "分发中"
    -16 -> "转码失败"
    -20 -> "尚未提交"
    -30 -> "已提交"
    -40 -> "定时发布"
    -50 -> "仅自己可见"
    -100 -> "已删除"
    else -> "暂不可公开浏览"
}

private fun MediaStat?.toDetailRows(): List<ParseMetadataRow> {
    val value = this ?: return emptyList()
    return rowsOf(
        "播放" to value.play?.takeIf { it > 0L }?.toString(),
        "弹幕" to value.danmaku?.takeIf { it > 0L }?.toString(),
        "评论" to value.reply?.takeIf { it > 0L }?.toString(),
        "点赞" to value.like?.takeIf { it > 0L }?.toString(),
        "投币" to value.coin?.takeIf { it > 0L }?.toString(),
        "收藏" to value.favorite?.takeIf { it > 0L }?.toString(),
        "分享" to value.share?.takeIf { it > 0L }?.toString(),
    )
}

internal fun formatMetadataDuration(seconds: Int?): String? = formatDuration(seconds)

private fun formatDuration(seconds: Int?): String? {
    val total = seconds?.takeIf { it > 0 } ?: return null
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val remainingSeconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, remainingSeconds)
    }
}

private fun formatEpochSeconds(epochSeconds: Long?): String? {
    val timestamp = epochSeconds?.takeIf { it > 0L } ?: return null
    return METADATA_TIME_FORMATTER.format(Instant.ofEpochSecond(timestamp))
}

/**
 * view 接口 `ctime` 的展示行。API 文档把 ctime 描述为「用户投稿时间」，但实测并不可靠
 * （常与 pubdate 完全一致、老稿件会落在 2017 年，官方页面也只展示 pubdate，详见
 * [MediaMetadata.submittedAt]），因此写成「投稿/过审时间」而非确切的「投稿时间」，并附上可能不准确的提示。
 */
private fun submittedAtRow(epochSeconds: Long?): ParseMetadataRow? {
    val value = formatEpochSeconds(epochSeconds) ?: return null
    return ParseMetadataRow(name = "投稿/过审时间", value = value, note = "可能不准确")
}

private fun formatResolution(resolution: MediaResolution?): String? {
    val value = resolution ?: return null
    val swapDimensions = value.rotate == 1 || value.rotate.absoluteValue % 180 == 90
    val width = if (swapDimensions) value.height else value.width
    val height = if (swapDimensions) value.width else value.height
    return "$width×$height"
}

internal fun formatMediaStatValue(value: Long): String = when {
    value >= 100_000_000L -> String.format(Locale.CHINA, "%.1f亿", value / 100_000_000.0)
    value >= 10_000L -> String.format(Locale.CHINA, "%.1f万", value / 10_000.0)
    else -> value.toString()
}

private fun formatRating(rating: Double): String {
    return if (rating % 1.0 == 0.0) {
        rating.toInt().toString()
    } else {
        String.format(Locale.CHINA, "%.1f", rating)
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
    else -> "内容编号"
}

private fun publicIdCopyName(item: MediaItem): String? = when (item.type) {
    MediaType.Video -> item.bvid?.let { "BV 号" }
    MediaType.Bangumi,
    MediaType.Lesson,
    -> when {
        item.epid != null -> "ep 号"
        item.ssid != null -> "ss 号"
        else -> null
    }
    MediaType.Music -> item.sid?.let { "au 号" }
    MediaType.Opus -> when {
        item.cvid != null -> "cv 号"
        !item.opid.isNullOrBlank() -> "图文动态号"
        else -> null
    }
    else -> null
}

private fun publicIdCopyName(publicId: String): String = when {
    publicId.startsWith("am", ignoreCase = true) -> "am 号"
    publicId.startsWith("rl", ignoreCase = true) -> "rl 号"
    publicId.startsWith("ep", ignoreCase = true) -> "ep 号"
    publicId.startsWith("ss", ignoreCase = true) -> "ss 号"
    publicId.startsWith("au", ignoreCase = true) -> "au 号"
    publicId.startsWith("cv", ignoreCase = true) -> "cv 号"
    publicId.startsWith("BV", ignoreCase = true) -> "BV 号"
    else -> "内容编号"
}

private fun subjectKey(item: MediaItem): String {
    return item.publicContentId()
        ?: "${item.type}:${item.url}:${item.index}"
}

private val METADATA_TIME_FORMATTER = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd HH:mm:ss",
    Locale.CHINA,
).withZone(ZoneId.of("Asia/Shanghai"))
