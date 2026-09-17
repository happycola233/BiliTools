package com.happycola233.bilitools.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.happycola233.bilitools.core.AppLog
import com.happycola233.bilitools.core.EmbeddedAudioTagWriter
import com.happycola233.bilitools.core.EmbeddedCover
import com.happycola233.bilitools.core.EmbeddedSubtitleTrack
import com.happycola233.bilitools.core.MediaProcessingEngine
import com.happycola233.bilitools.core.tagValues
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.LyricsEmbedding
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request

/** 单次后处理里没能完整写入的部分；每一项对应一句面向用户的提示。 */
internal enum class EmbeddedContentIssue {
    Cover,
    LyricsUnavailable,
    LyricsFailed,
    SubtitlesUnavailable,
    SubtitlesFailed,
    SubtitlesPartiallyFailed,
    SubtitlesUnsupportedContainer,
    WriteFailed,
    UnsupportedFormat,
}

internal data class EmbeddedContentResult(
    val issues: Set<EmbeddedContentIssue> = emptySet(),
    /** 成功写入的软字幕轨名称，按轨道顺序。 */
    val subtitleTitles: List<String> = emptyList(),
    /** 成功写入的歌词来源说明。 */
    val lyricsSource: String? = null,
    /** 用户选择后被移除或未能获取的字幕，用于说明具体的未完成项。 */
    val incompleteSubtitleTitles: List<String> = emptyList(),
)

/**
 * 元数据标签、封面、软字幕轨与歌词共用一次容器改写。与下载队列分离的后处理：
 * 外部资源失败可单独省略，写文件失败绝不覆盖原件。
 */
internal class EmbeddedContentWriter(
    private val httpClient: OkHttpClient,
    private val extrasRepository: ExtrasRepository,
) {
    /**
     * @param metadata 要写入的元数据；「添加元数据」关闭时传 null，此时只处理 [DownloadItem.embedding]。
     */
    suspend fun write(
        file: File,
        item: DownloadItem,
        metadata: DownloadEmbeddedMetadata?,
        settings: DownloadMetadataSettings,
    ): EmbeddedContentResult {
        val embedding = item.embedding
        if (metadata == null && embedding == null) return EmbeddedContentResult()
        // 歌词与字幕的来源标识随元数据一起生成，元数据开关关闭时仍可用。
        val sources = item.embeddedMetadata
        val extension = item.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val format = when (extension) {
            "mp4", "m4a" -> "mp4"
            "mkv" -> "matroska"
            "flv", "flac", "mp3" -> extension
            else -> return EmbeddedContentResult(issues = setOf(EmbeddedContentIssue.UnsupportedFormat))
        }
        val issues = mutableSetOf<EmbeddedContentIssue>()
        suspend fun <T> optional(issue: EmbeddedContentIssue, block: suspend () -> T): T? = try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            issues += issue
            AppLog.w(TAG, "$issue for task ${item.id}", error)
            null
        }

        var cover: EmbeddedCover? = null
        val subtitleFiles = mutableListOf<File>()
        val incompleteSubtitleTitles = mutableListOf<String>()
        var output: File? = null
        try {
            if (metadata != null && settings.embedCover && format != "flv" && metadata.coverUrl != null) {
                cover = optional(EmbeddedContentIssue.Cover) { downloadCover(metadata.coverUrl, file.parentFile!!) }
            }

            val lyricsRequest = embedding?.lyrics?.takeIf { item.taskType == DownloadTaskType.Audio }
            var lyricsSource: String? = null
            var lyrics: String? = null
            if (lyricsRequest != null) {
                val resolved = sources?.let { lyricSources ->
                    optional(EmbeddedContentIssue.LyricsFailed) {
                        resolveLyrics(lyricSources, lyricsRequest) { source -> lyricsSource = source }
                    }
                }
                when {
                    resolved != null -> lyrics = resolved
                    // 请求失败已单独记录；这里只剩「来源里确实没有歌词」的情况。
                    EmbeddedContentIssue.LyricsFailed !in issues -> issues += EmbeddedContentIssue.LyricsUnavailable
                }
            }

            val subtitleRequest = embedding?.subtitles?.takeIf { item.taskType != DownloadTaskType.Audio }
            val subtitleTracks = when {
                subtitleRequest == null -> emptyList()
                !MediaProcessingEngine.supportsSubtitleTracks(format) -> {
                    issues += EmbeddedContentIssue.SubtitlesUnsupportedContainer
                    emptyList()
                }
                else -> resolveSubtitleTracks(
                    sources, subtitleRequest, file.parentFile!!, issues, incompleteSubtitleTitles,
                    onTemporaryFile = { subtitleFiles += it },
                )
            }

            val values = metadata?.tagValues(settings, lyrics, lyricsSource.takeIf { lyrics != null })
                ?: buildMap { lyrics?.let { put("lyrics", it) } }
            if (values.isEmpty() && cover == null && subtitleTracks.isEmpty()) {
                return EmbeddedContentResult(issues = issues, incompleteSubtitleTitles = incompleteSubtitleTitles)
            }
            currentCoroutineContext().ensureActive()
            val written = optional(EmbeddedContentIssue.WriteFailed) {
                val target = File.createTempFile("metadata-", ".$extension", file.parentFile)
                output = target
                when (format) {
                    "mp3", "flac" -> {
                        // 下载后处理已确保容器正确；标签选项不能决定是否需要解封装。
                        file.copyTo(target, overwrite = true)
                        EmbeddedAudioTagWriter.write(target, values, cover)
                    }
                    else -> MediaProcessingEngine.writeMetadata(file, target, format, values, cover, subtitleTracks)
                }
                currentCoroutineContext().ensureActive()
                Files.move(target.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                true
            } ?: false
            return EmbeddedContentResult(
                issues = issues,
                subtitleTitles = if (written) subtitleTracks.map(EmbeddedSubtitleTrack::title) else emptyList(),
                lyricsSource = lyricsSource.takeIf { written && lyrics != null },
                incompleteSubtitleTitles = incompleteSubtitleTitles,
            )
        } finally {
            output?.delete()
            cover?.file?.delete()
            subtitleFiles.forEach { it.delete() }
        }
    }

    /** 音乐条目用自身的原始歌词；视频转出的音频按请求挑一种字幕转成 LRC。 */
    private suspend fun resolveLyrics(
        sources: DownloadEmbeddedMetadata,
        request: LyricsEmbedding,
        onSource: (String) -> Unit,
    ): String? {
        if (sources.lyricUrl != null) {
            onSource("音频原始歌词")
            return normalizeOriginalLyrics(readTextAsset(sources.lyricUrl))
        }
        if (sources.musicSid != null) {
            onSource("音频原始歌词")
            return extrasRepository.getMusicLyrics(sources.musicSid)
        }
        if (request.language == null) return null
        val aid = sources.subtitleAid ?: return null
        val cid = sources.subtitleCid ?: return null
        val subtitle = selectLyricsSubtitle(extrasRepository.getSubtitles(aid, cid), request) ?: return null
        onSource("${subtitle.displayName}（字幕转写）")
        return extrasRepository.getSubtitleLyrics(subtitle)
    }

    /** 每条字幕单独获取：个别语言失败只影响那一条轨道，其余照常嵌入。 */
    internal suspend fun resolveSubtitleTracks(
        sources: DownloadEmbeddedMetadata?,
        request: SubtitleTrackEmbedding,
        directory: File,
        issues: MutableSet<EmbeddedContentIssue>,
        incompleteSubtitleTitles: MutableList<String>,
        onTemporaryFile: (File) -> Unit,
    ): List<EmbeddedSubtitleTrack> {
        val aid = sources?.subtitleAid
        val cid = sources?.subtitleCid
        val available = try {
            if (aid == null || cid == null) emptyList()
            else extrasRepository.getSubtitles(aid, cid)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.w(TAG, "subtitle list unavailable for aid=$aid cid=$cid", error)
            issues += EmbeddedContentIssue.SubtitlesFailed
            return emptyList()
        }
        val selected = selectEmbeddedSubtitleTracks(available, request)
        val missingLanguages = request.languages.distinct().filter { language ->
            selected.none { it.lan == language }
        }
        incompleteSubtitleTitles += missingLanguages.map { language ->
            available.firstOrNull { it.lan == language }?.displayName ?: subtitleLanguageDisplayName(language)
        }
        if (selected.isEmpty()) {
            issues += EmbeddedContentIssue.SubtitlesUnavailable
            return emptyList()
        }
        var failed = 0
        val tracks = selected.mapNotNull { subtitle ->
            currentCoroutineContext().ensureActive()
            try {
                val srt = extrasRepository.getSubtitleSrt(subtitle)
                // 没有任何字幕行的空文件会让整次改写失败，按不可用处理。
                if (srt.isEmpty()) {
                    failed += 1
                    incompleteSubtitleTitles += subtitle.displayName
                    null
                } else {
                    val trackFile = File.createTempFile("subtitle-", ".srt", directory)
                    // 注册必须早于写文件和下一次挂起，否则中途取消会留下已下载的字幕。
                    onTemporaryFile(trackFile)
                    trackFile.writeBytes(srt)
                    EmbeddedSubtitleTrack(file = trackFile, languageTag = subtitle.lan, title = subtitle.displayName)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w(TAG, "subtitle ${subtitle.lan} unavailable", error)
                failed += 1
                incompleteSubtitleTitles += subtitle.displayName
                null
            }
        }
        when {
            failed == 0 && missingLanguages.isEmpty() -> Unit
            tracks.isEmpty() -> issues += EmbeddedContentIssue.SubtitlesFailed
            else -> issues += EmbeddedContentIssue.SubtitlesPartiallyFailed
        }
        return tracks
    }

    private suspend fun readAsset(rawUrl: String, limit: Long): Pair<ByteArray, java.nio.charset.Charset> {
        currentCoroutineContext().ensureActive()
        val url = when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("http://") -> "https:${rawUrl.removePrefix("http:")}"
            else -> rawUrl
        }
        return httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Metadata resource HTTP ${response.code}" }
            val body = response.body
            check(body.contentLength() <= limit) { "Metadata resource is too large" }
            val source = body.source()
            source.request(limit + 1)
            check(source.buffer.size <= limit) { "Metadata resource is too large" }
            currentCoroutineContext().ensureActive()
            source.readByteArray() to (body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
        }
    }

    private suspend fun readTextAsset(url: String): String {
        val (bytes, charset) = readAsset(url, 1024 * 1024)
        // 声明的编码与内容不符时省略并提示，不能把替换字符组成的乱码写入歌词。
        return charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    private suspend fun downloadCover(url: String, directory: File): EmbeddedCover {
        val bytes = readAsset(url, 8 * 1024 * 1024).first
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid cover image" }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 1600) sampleSize *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )) { "Cannot decode cover image" }
        val file = File.createTempFile("metadata-cover-", ".jpg", directory)
        return try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            EmbeddedCover(file, bitmap.width, bitmap.height)
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    private companion object { const val TAG = "EmbeddedContent" }
}
