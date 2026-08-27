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
)

data class MediaRole(
    val role: String,
    val name: String,
)

data class MediaCredits(
    val actors: List<MediaRole> = emptyList(),
    val staff: List<MediaRole> = emptyList(),
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
) {
    /** 下载列表等处展示的公开内容号：视频为 BV，专栏为 cv。 */
    fun displayContentId(): String? {
        bvid?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return cvid?.let { "cv$it" }
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
)
