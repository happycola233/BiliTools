package com.happycola233.bilitools.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.happycola233.bilitools.core.AppLog
import com.happycola233.bilitools.core.EmbeddedAudioTagWriter
import com.happycola233.bilitools.core.EmbeddedCover
import com.happycola233.bilitools.core.MediaProcessingEngine
import com.happycola233.bilitools.core.tagValues
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadTaskType
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

internal enum class MetadataWriteIssue { Cover, Lyrics, Tags, UnsupportedFormat }

/** 与下载队列分离的后处理：外部资源失败可单独省略，写文件失败绝不覆盖原件。 */
internal class DownloadMetadataWriter(
    private val httpClient: OkHttpClient,
    private val extrasRepository: ExtrasRepository,
) {
    suspend fun write(file: File, item: DownloadItem, settings: DownloadMetadataSettings): Set<MetadataWriteIssue> {
        val metadata = item.embeddedMetadata ?: return emptySet()
        val extension = item.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val format = when (extension) {
            "mp4", "m4a", "m4s" -> "mp4"
            "mkv" -> "matroska"
            "flv", "flac", "mp3" -> extension
            else -> return setOf(MetadataWriteIssue.UnsupportedFormat)
        }
        val issues = mutableSetOf<MetadataWriteIssue>()
        suspend fun <T> optional(issue: MetadataWriteIssue, block: suspend () -> T): T? = try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            issues += issue
            AppLog.w(TAG, "$issue unavailable for task ${item.id}", error)
            null
        }

        var cover: EmbeddedCover? = null
        var output: File? = null
        try {
            if (settings.embedCover && format != "flv" && metadata.coverUrl != null) {
                cover = optional(MetadataWriteIssue.Cover) { downloadCover(metadata.coverUrl, file.parentFile!!) }
            }
            var lyricsSource: String? = null
            val lyrics = if (item.taskType == DownloadTaskType.Audio && settings.embedLyrics) {
                optional(MetadataWriteIssue.Lyrics) {
                    if (metadata.lyricUrl != null) {
                        lyricsSource = "音频原始歌词"
                        normalizeOriginalLyrics(readTextAsset(metadata.lyricUrl))
                    } else if (metadata.musicSid != null) {
                        lyricsSource = "音频原始歌词"
                        extrasRepository.getMusicLyrics(metadata.musicSid)
                    } else if (metadata.subtitleAid != null && metadata.subtitleCid != null &&
                        settings.subtitleLyrics != SubtitleLyricsMode.Off
                    ) {
                        val subtitle = selectLyricsSubtitle(
                            extrasRepository.getSubtitles(metadata.subtitleAid, metadata.subtitleCid),
                            settings.subtitleLyrics,
                            metadata.preferredSubtitleLanguage,
                        )
                        subtitle?.let {
                            lyricsSource = "${it.name}（${if (it.isGenerated) "AI 字幕转写" else "字幕转写"}）"
                            extrasRepository.getSubtitleLyrics(it)
                        }
                    } else null
                }
            } else null
            val values = metadata.tagValues(settings, lyrics, lyricsSource.takeIf { lyrics != null })
            currentCoroutineContext().ensureActive()
            optional(MetadataWriteIssue.Tags) {
                val target = File.createTempFile("metadata-", ".$extension", file.parentFile)
                output = target
                when (format) {
                    "mp3" -> {
                        file.copyTo(target, overwrite = true)
                        EmbeddedAudioTagWriter.write(target, values, cover)
                    }
                    "flac" -> {
                        // DASH 的 FLAC 可能仍装在 MP4 中；扩展名不能作为容器的判断依据。
                        val nativeFlac = file.inputStream().use { input ->
                            val signature = ByteArray(4)
                            input.read(signature) == 4 && signature.contentEquals("fLaC".toByteArray())
                        }
                        if (nativeFlac) file.copyTo(target, overwrite = true)
                        else MediaProcessingEngine.writeMetadata(file, target, "flac", emptyMap())
                        EmbeddedAudioTagWriter.write(target, values, cover)
                    }
                    else -> MediaProcessingEngine.writeMetadata(file, target, format, values, cover)
                }
                currentCoroutineContext().ensureActive()
                Files.move(target.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            output?.delete()
            cover?.file?.delete()
        }
        return issues
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

    private companion object { const val TAG = "DownloadMetadata" }
}
