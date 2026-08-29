package com.happycola233.bilitools.data.model

enum class MediaType {
    Video,
    Bangumi,
    Lesson,
    Music,
    MusicList,
    WatchLater,
    Favorite,
    Opus,
    OpusList,
    UserVideo,
    UserOpus,
    UserAudio,
}

data class MediaStat(
    val play: Long? = null,
    val danmaku: Long? = null,
    val reply: Long? = null,
    val like: Long? = null,
    val coin: Long? = null,
    val favorite: Long? = null,
    val share: Long? = null,
)

data class MediaThumb(
    val id: String,
    val url: String,
)

data class MediaUpper(
    val name: String,
    val mid: Long,
    val avatar: String? = null,
    val followerCount: Long? = null,
)

data class MediaRole(
    val role: String,
    val name: String,
)

data class MediaCredits(
    val actors: List<MediaRole> = emptyList(),
    val staff: List<MediaRole> = emptyList(),
)

enum class MediaCopyrightType {
    Original,
    Repost,
}

/** 只有会显著影响观看或下载的少见属性才进入摘要候选。 */
enum class MediaRareAttribute {
    Cooperation,
    Interactive,
    Panorama,
    ChargeExclusive,
    VipOnly,
    LimitedFree,
    PurchaseRequired,
    DynamicVideo,
}

data class MediaContributor(
    val name: String,
    val mid: Long,
    val avatar: String? = null,
    val role: String? = null,
)

data class MediaPaymentInfo(
    val description: String? = null,
    val price: String? = null,
)

data class MediaResolution(
    val width: Int,
    val height: Int,
    val rotate: Int = 0,
)

data class MediaVideoPart(
    val page: Int,
    val title: String? = null,
    val duration: Int? = null,
    val resolution: MediaResolution? = null,
    val cid: Long? = null,
    val submittedAt: Long? = null,
)

data class MediaHonor(
    val type: Int?,
    val description: String,
)

/**
 * 解析请求已经取得的展示元信息。这里保留原始语义，具体分组与产品文案由解析页决定，
 * 从而让直接解析与列表预览补拉可以复用同一份数据，而不会为详情再发旁路请求。
 */
data class MediaMetadata(
    /** 当前主体已由对应详情接口解析完成；列表摘要保持 false。 */
    val presentationDetailsComplete: Boolean = false,
    val totalDuration: Int? = null,
    val partCount: Int? = null,
    val itemCount: Int? = null,
    val imageCount: Int? = null,
    val legacyCategory: String? = null,
    val modernCategory: String? = null,
    val copyrightType: MediaCopyrightType? = null,
    val noReprint: Boolean = false,
    val rareAttributes: Set<MediaRareAttribute> = emptySet(),
    val badges: List<String> = emptyList(),
    val warning: String? = null,
    val collisionBvid: String? = null,
    /** view.state 原始状态码；展示层只输出对应的产品文案。 */
    val videoState: Int? = null,
    val honors: List<MediaHonor> = emptyList(),
    val currentRank: Int? = null,
    val historicalRank: Int? = null,
    val evaluation: String? = null,
    val dynamicText: String? = null,
    val videoParts: List<MediaVideoPart> = emptyList(),
    val resolution: MediaResolution? = null,
    val publishedAt: Long? = null,
    val submittedAt: Long? = null,
    val mediaId: Long? = null,
    val contentKind: String? = null,
    val area: String? = null,
    val rating: Double? = null,
    val copyrightLabel: String? = null,
    val isCompleted: Boolean? = null,
    val updateText: String? = null,
    val actors: String? = null,
    val productionStaff: String? = null,
    val contributors: List<MediaContributor> = emptyList(),
    val accessLabel: String? = null,
    val payment: MediaPaymentInfo? = null,
    val artist: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long? = null,
    val collectionId: Long? = null,
    val invalid: Boolean = false,
)

data class MediaNfo(
    val showTitle: String? = null,
    val intro: String? = null,
    val tags: List<String> = emptyList(),
    val url: String? = null,
    val stat: MediaStat = MediaStat(),
    val thumbs: List<MediaThumb> = emptyList(),
    val premiered: Long? = null,
    val upper: MediaUpper? = null,
    val credits: MediaCredits? = null,
)

data class MediaTab(
    val id: Long,
    val name: String,
)

data class MediaSections(
    val target: Long,
    val tabs: List<MediaTab> = emptyList(),
)

data class MediaItem(
    val title: String,
    val coverUrl: String,
    val description: String,
    val stat: MediaStat? = null,
    val url: String,
    val duration: Int,
    val pubTime: Long,
    val type: MediaType,
    // 稿件自身的 UP；收藏夹等容器里每条可以不同，缺省时回退到 nfo.upper
    val upper: MediaUpper? = null,
    val isTarget: Boolean,
    val index: Int,
    // 官方分 P 号与分 P 总数，只有稿件视频有；列表里的稿件不展开分 P，因此为空。
    val page: Int? = null,
    val pageCount: Int? = null,
    /** 所属作品标题：稿件标题、番剧季名、课程名、歌名、图文标题。 */
    val workTitle: String? = null,
    /** 集号：番剧 title（"1"、"SP"），课程 index。 */
    val episode: String? = null,
    /** 番剧单集完整标题 long_title。 */
    val longTitle: String? = null,
    /** 分区：番剧的正片/PV/特典，UGC 合集的分部。 */
    val sectionTitle: String? = null,
    /** 音频作者，常与 UP 主不同。 */
    val artist: String? = null,
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long? = null,
    val epid: Long? = null,
    val ssid: Long? = null,
    val sid: Long? = null,
    /** 歌单号 menuId。 */
    val amid: Long? = null,
    val fid: Long? = null,
    val opid: String? = null,
    val cvid: Long? = null,
    val rlid: Long? = null,
    /** 番剧/影视 media id。 */
    val mdid: Long? = null,
    /** 用户投稿、用户图文、用户音频等列表的来源空间。 */
    val sourceMid: Long? = null,
    val metadata: MediaMetadata = MediaMetadata(),
) {
    /** 下载列表等处展示的公开内容号：视频为 BV，专栏为 cv。 */
    fun displayContentId(): String? {
        bvid?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return cvid?.let { "cv$it" }
    }

    /** 当前卡片主体在站内最常复制的公开号。 */
    fun publicContentId(): String? = when (type) {
        MediaType.Video -> bvid?.trim()?.takeIf(String::isNotBlank)
        MediaType.Bangumi -> epid?.let { "ep$it" } ?: ssid?.let { "ss$it" }
        MediaType.Lesson -> epid?.let { "ep$it" } ?: ssid?.let { "ss$it" }
        MediaType.Music -> sid?.let { "au$it" }
        MediaType.Opus -> cvid?.let { "cv$it" }
            ?: opid?.trim()?.takeIf(String::isNotBlank)
        else -> null
    }

    fun resolvedUpper(info: MediaInfo): MediaUpper? = upper ?: info.nfo.upper
}

data class MediaInfo(
    val type: MediaType,
    val id: String,
    val nfo: MediaNfo,
    val list: List<MediaItem>,
    val sections: MediaSections? = null,
    val paged: Boolean = false,
    // 按请求页容量从接口总数换算出的总页数（不是当前页实际条数）。
    // 游标分页（如用户动态）拿不到总数时为 null。
    val totalPages: Int? = null,
    val offset: String? = null,
    val hasMore: Boolean? = null,
    val collection: Boolean = false,
    val metadata: MediaMetadata = MediaMetadata(),
)

data class ParsedInput(
    val id: String,
    val type: MediaType? = null,
    val target: Long? = null,
)

data class MediaQueryOptions(
    val page: Int = 1,
    val offset: String? = null,
    val target: Long? = null,
    val collection: Boolean = false,
    /** 列表预览补拉 view 时关闭，避免为了详情再次请求独立标签接口。 */
    val includeOptionalVideoTags: Boolean = true,
    /** 直接解析 au 时获取标签和 UP 资料；列表预览只请求歌曲主体，避免两次旁路请求。 */
    val includeOptionalMusicExtras: Boolean = true,
)
