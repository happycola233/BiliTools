package com.happycola233.bilitools.core.naming

import com.happycola233.bilitools.data.model.MediaType

/**
 * 命名形态：按「可下载单元」的真实形状划分，而不是和 [MediaType] 一一对应。
 *
 * 收藏夹、稍后再看、用户投稿里的条目仍然是 [Video]，因为它们的命名诉求和普通稿件完全一致；
 * 只有真正结构不同的资源（番剧单集、音频单曲、图文）才各自成形态。
 *
 * 每个形态各自带一套内置默认模板，形态之间不共享、不继承：改一个不会波及其它形态。
 */
enum class NamingShape(val value: String) {
    Video("video"),
    Episode("episode"),
    Track("track"),
    Opus("opus"),
    Listing("list"),
    ;

    /** [Listing] 只描述入口，条目本身总是落在其它形态上，因此只保留顶层文件夹一层。 */
    val supportedScopes: Set<NamingTemplateScope>
        get() = if (this == Listing) {
            setOf(NamingTemplateScope.TopFolder)
        } else {
            NamingTemplateScope.entries.toSet()
        }

    companion object {
        fun fromValue(value: String?): NamingShape? = entries.firstOrNull { it.value == value }

        /** 条目形态：决定项目文件夹与文件名。 */
        fun ofItem(type: MediaType): NamingShape = when (type) {
            MediaType.Video, MediaType.WatchLater, MediaType.Favorite, MediaType.UserVideo -> Video
            MediaType.Bangumi, MediaType.Lesson -> Episode
            MediaType.Music, MediaType.MusicList, MediaType.UserAudio -> Track
            MediaType.Opus, MediaType.OpusList, MediaType.UserOpus -> Opus
        }

        /** 入口形态：决定顶层文件夹。 */
        fun ofEntry(type: MediaType): NamingShape = when (type) {
            MediaType.WatchLater, MediaType.Favorite,
            MediaType.UserVideo, MediaType.UserOpus, MediaType.UserAudio,
            -> Listing
            else -> ofItem(type)
        }
    }
}

enum class NamingTemplateScope(val value: String) {
    TopFolder("top"),
    ItemFolder("item"),
    File("file"),
    ;

    companion object {
        fun fromValue(value: String?): NamingTemplateScope? =
            entries.firstOrNull { it.value == value }
    }
}
