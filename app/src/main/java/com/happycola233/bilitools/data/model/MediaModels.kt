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
    val isTarget: Boolean,
    val index: Int,
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long? = null,
    val epid: Long? = null,
    val ssid: Long? = null,
    val sid: Long? = null,
    val fid: Long? = null,
    val opid: String? = null,
    val rlid: Long? = null,
)

data class MediaInfo(
    val type: MediaType,
    val id: String,
    val nfo: MediaNfo,
    val list: List<MediaItem>,
    val sections: MediaSections? = null,
    val paged: Boolean = false,
    val offset: String? = null,
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
