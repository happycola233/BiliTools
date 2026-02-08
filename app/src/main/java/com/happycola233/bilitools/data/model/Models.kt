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
    Failed,
    Cancelled,
}

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
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long? = null,
    val reason: Int? = null,
    val localUri: String? = null,
    val outputMissing: Boolean = false,
    val userPaused: Boolean = false,
    val errorMessage: String? = null,
    val mediaParams: DownloadMediaParams? = null,
    val embeddedMetadata: DownloadEmbeddedMetadata? = null,
)

data class DownloadMediaParams(
    val resolution: String? = null,
    val codec: String? = null,
    val audioBitrate: String? = null,
)

data class DownloadEmbeddedMetadata(
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val comment: String? = null,
    val date: String? = null,
    val year: Int? = null,
    val tags: List<String> = emptyList(),
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val originalUrl: String? = null,
    val coverUrl: String? = null,
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
)
