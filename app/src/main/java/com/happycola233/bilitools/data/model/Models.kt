package com.happycola233.bilitools.data.model


data class VideoId(
    val bvid: String? = null,
    val aid: String? = null,
)

data class VideoPage(
    val cid: Long,
    val title: String,
    val duration: Int,
)

data class VideoInfo(
    val bvid: String,
    val aid: Long,
    val title: String,
    val description: String,
    val coverUrl: String,
    val duration: Int,
    val pubTime: Long,
    val ownerName: String?,
    val ownerMid: Long?,
    val ownerAvatar: String?,
    val tags: List<String>,
    val collectionTitle: String?,
    val collectionCoverUrl: String?,
    val pages: List<VideoPage>,
)

enum class StreamFormat {
    Mp4,
    Dash,
    Flv,
}

enum class OutputType {
    AudioVideo,
    VideoOnly,
    AudioOnly,
}

enum class VideoCodec {
    Avc,
    Hevc,
    Av1,
}

data class VideoStream(
    val id: Int,
    val format: StreamFormat,
    val width: Int? = null,
    val height: Int? = null,
    val bandwidth: Long? = null,
    val frameRate: String? = null,
    val codec: VideoCodec? = null,
    val url: String,
    val backupUrls: List<String> = emptyList(),
    val size: Long? = null,
)

data class AudioStream(
    val id: Int,
    val bandwidth: Long? = null,
    val url: String,
    val backupUrls: List<String> = emptyList(),
)

data class PlayUrlInfo(
    val format: StreamFormat,
    val video: List<VideoStream> = emptyList(),
    val audio: List<AudioStream> = emptyList(),
    val acceptQuality: List<Int> = emptyList(),
    val acceptDescription: List<String> = emptyList(),
)

data class SubtitleInfo(
    val lan: String,
    val name: String,
    val url: String,
    val isAi: Boolean = false,
)

data class UserInfo(
    val name: String,
    val mid: Long,
    val avatarUrl: String?,
    val level: Int?,
    val isSeniorMember: Boolean = false,
    val sign: String?,
    val vipLabel: String?,
    val vipLabelImageUrl: String? = null,
    val vipStatus: Int? = null,
    val vipType: Int? = null,
    val vipAvatarSubscript: Int? = null,
    val topPhotoUrl: String? = null,
    val coins: Double? = null,
    val following: Int? = null,
    val follower: Int? = null,
    val dynamic: Int? = null,
)

data class QrLoginInfo(
    val qrUrl: String,
    val qrKey: String,
)

enum class QrLoginStatus {
    Waiting,
    Scanned,
    Success,
    Expired,
    Error,
}

data class QrLoginResult(
    val status: QrLoginStatus,
    val message: String,
)

enum class DownloadStatus {
    Pending,
    Running,
    Paused,
    Merging,
    Success,
    Unavailable,
    Failed,
    Cancelled,
}

/** 已正常处理完毕的结果：资源成功保存，或确认该条目没有对应资源后跳过。 */
val DownloadStatus.isResolvedWithoutFailure: Boolean
    get() = this == DownloadStatus.Success || this == DownloadStatus.Unavailable

enum class DownloadTaskType {
    Video,
    Audio,
    AudioVideo,
    Subtitle,
    AiSummary,
    NfoCollection,
    NfoSingle,
    DanmakuLive,
    DanmakuHistory,
    Cover,
    CollectionCover,
    OpusContent,
    OpusImage,
}

val DownloadTaskType.isManagedTransfer: Boolean
    get() = when (this) {
        DownloadTaskType.Video,
        DownloadTaskType.Audio,
        DownloadTaskType.AudioVideo,
        DownloadTaskType.OpusImage,
        -> true

        else -> false
    }

data class DownloadItem(
    val id: Long,
    val groupId: Long,
    val taskType: DownloadTaskType,
    val title: String,
    val fileName: String,
    val url: String,
    val createdAt: Long = 0,
    val status: DownloadStatus,
    val progress: Int,
    val progressIndeterminate: Boolean = false,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long? = null,
    val reason: Int? = null,
    val localUri: String? = null,
    val outputMissing: Boolean = false,
    val userPaused: Boolean = false,
    val errorMessage: String? = null,
    val statusDetail: String? = null,
    /** 稳定消息代码用于重新渲染历史状态；旧版保存的原文仍保留作兼容。 */
    val statusMessage: DownloadMessage? = null,
    val failureMessage: DownloadMessage? = null,
    val mediaParams: DownloadMediaParams? = null,
    val embeddedMetadata: DownloadEmbeddedMetadata? = null,
    /** 解析页为这次下载单独选择的内嵌字幕 / 歌词；与元数据开关无关。 */
    val embedding: DownloadEmbedding? = null,
    /** 元数据、封面、字幕或歌词未能全部写入时的提示。 */
    val embedWarning: String? = null,
    val embeddingMessages: List<DownloadMessage> = emptyList(),
    /** 实际写入文件的软字幕轨名称与歌词来源，供详情页展示。 */
    val embeddedSubtitleTitles: List<String> = emptyList(),
    val embeddedLyricsSource: String? = null,
    /** 保存后的实际字节数；合并、转码与元数据写入后可能不同于传输大小。 */
    val outputBytes: Long? = null,
)

data class DownloadMediaParams(
    val resolution: String? = null,
    val codec: String? = null,
    val audioBitrate: String? = null,
    val resolutionId: Int? = null,
    val resolutionHeight: Int? = null,
    val codecType: VideoCodec? = null,
    val audioQualityId: Int? = null,
)

/**
 * 下载完成后写进媒体文件内部的字幕轨与歌词。字幕以软字幕轨挂在视频容器里，播放器可切换或关闭；
 * 歌词使用音频标签存储，但产品上独立于作品信息，因此不受「添加元数据」设置影响。
 */
data class DownloadEmbedding(
    val subtitles: SubtitleTrackEmbedding? = null,
    val lyrics: LyricsEmbedding? = null,
)

data class SubtitleTrackEmbedding(
    /** 要嵌入的字幕语言代码（B 站 lan 值）；为空表示嵌入该条目的全部可用字幕。 */
    val languages: List<String> = emptyList(),
)

data class LyricsEmbedding(
    /** 视频转音频时明确选定的字幕语言；null 表示未选择。音乐条目使用原始歌词。 */
    val language: String? = null,
)

data class DownloadEmbeddedMetadata(
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val comment: String? = null,
    val tags: List<String> = emptyList(),
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val originalUrl: String? = null,
    val coverUrl: String? = null,
    val artistIsUploader: Boolean = false,
    val albumIsCollection: Boolean = false,
    val uploader: String? = null,
    val publishedDate: String? = null,
    val lyricUrl: String? = null,
    val musicSid: Long? = null,
    val subtitleAid: Long? = null,
    val subtitleCid: Long? = null,
    val durationSeconds: Int? = null,
)

data class DownloadGroup(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val bvid: String? = null,
    val coverUrl: String? = null,
    val createdAt: Long,
    val relativePath: String = "",
    val tasks: List<DownloadItem>,
    /** 独立于下载类型保存来源，只有字幕或封面时也能查看详情和重新解析。 */
    val sourceMetadata: DownloadEmbeddedMetadata? = null,
)
