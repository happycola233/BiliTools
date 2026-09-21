package com.happycola233.bilitools.data.model

import androidx.annotation.StringRes
import com.happycola233.bilitools.R

/** 只持久化枚举名称和参数，资源整数 ID 会随构建变化，不能写入下载记录。 */
data class DownloadMessage(
    val code: DownloadMessageCode,
    val textArguments: List<String> = emptyList(),
    val countArguments: List<Int> = emptyList(),
)

enum class DownloadMessageCode(@StringRes val resourceId: Int, val hasArguments: Boolean = false) {
    DetailFetchingDanmakuSegment(R.string.download_detail_fetching_danmaku_segment, true),
    DetailConvertingDanmaku(R.string.download_detail_converting_danmaku),
    DetailSavingFile(R.string.download_detail_saving_file),
    DetailConvertingAudio(R.string.download_detail_converting_audio),
    DetailConvertingVideo(R.string.download_detail_converting_video),
    DetailPreparingMedia(R.string.download_detail_preparing_media),
    DetailMerging(R.string.download_detail_merging),
    DetailMetadata(R.string.download_detail_metadata),
    DetailMetadataSubtitles(R.string.download_detail_metadata_subtitles),
    DetailMetadataLyrics(R.string.download_detail_metadata_lyrics),
    DetailEmbedSubtitles(R.string.download_detail_embed_subtitles),
    DetailEmbedLyrics(R.string.download_detail_embed_lyrics),
    FailureSave(R.string.download_failure_save),
    FailureDownloadUnknown(R.string.download_failure_download_unknown),
    FailureMerge(R.string.download_failure_merge),
    FailureConvertAudio(R.string.download_failure_convert_audio),
    FailureConvertVideo(R.string.download_failure_convert_video),
    FailurePrepareMedia(R.string.download_failure_prepare_media),
    FailureResumeDataMissing(R.string.download_failure_resume_data_missing),
    FailureRetryDataMissing(R.string.download_failure_retry_data_missing),
    FailureExtra(R.string.download_failure_extra, true),
    ErrorUnsafeExit(R.string.download_error_unsafe_exit),
    UnavailableGeneric(R.string.download_unavailable_generic),
    UnavailableAudio(R.string.download_unavailable_audio),
    UnavailableVideo(R.string.download_unavailable_video),
    UnavailableAudioVideo(R.string.download_unavailable_audio_video),
    UnavailableImage(R.string.download_unavailable_image),
    UnavailableOpusImages(R.string.download_unavailable_opus_images),
    ErrorNoSubtitle(R.string.parse_error_no_subtitle),
    ErrorNoAi(R.string.parse_error_no_ai),
    ErrorNoNfo(R.string.parse_error_no_nfo),
    ErrorNoDanmaku(R.string.parse_error_no_danmaku),
    ErrorOpusInvalidResponse(R.string.parse_error_opus_invalid_response),
    MetadataCoverFailed(R.string.download_metadata_cover_failed),
    EmbedLyricsUnavailable(R.string.download_embed_lyrics_unavailable),
    EmbedLyricsFailed(R.string.download_embed_lyrics_failed),
    EmbedSubtitlesUnavailable(R.string.download_embed_subtitles_unavailable),
    EmbedSubtitlesFailed(R.string.download_embed_subtitles_failed),
    EmbedSubtitlesPartial(R.string.download_embed_subtitles_partial),
    EmbedSubtitlesContainer(R.string.download_embed_subtitles_container),
    EmbedWriteFailed(R.string.download_embed_write_failed),
    EmbedUnsupported(R.string.download_embed_unsupported),
    EmbedMissingLanguages(R.string.download_embed_missing_languages, true),
}
