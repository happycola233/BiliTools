package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadEmbedding
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.LyricsEmbedding
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmbeddedContentWriterTest {
    private var requests = 0
    private val directory = File("../.tmp/metadata/writer-tests").apply { mkdirs() }
    private val metadata = DownloadEmbeddedMetadata(
        title = "当前歌曲", artist = "作者", coverUrl = "https://example.com/cover.jpg",
        lyricUrl = "https://example.com/lyrics.lrc",
    )
    private val lyricsEmbedding = DownloadEmbedding(lyrics = LyricsEmbedding())

    @Test fun writesCoverAndOriginalLyricsOnAndroid() = withAudio { file ->
        val result = writer().write(file, item(file, lyricsEmbedding), metadata, DownloadMetadataSettings())
        assertTrue(result.toString(), result.issues.isEmpty())
        assertEquals("音频原始歌词", result.lyricsSource)
        val tag = AudioFileIO.read(file).tag
        assertEquals("当前歌曲", tag.getFirst(FieldKey.TITLE))
        assertEquals("[00:00.00]原始歌词", tag.getFirst(FieldKey.LYRICS))
        assertEquals("image/jpeg", tag.firstArtwork.mimeType)
        assertEquals(2, requests)
    }

    @Test fun lyricsAreEmbeddedEvenWhenMetadataIsDisabled() = withAudio { file ->
        val result = writer().write(file, item(file, lyricsEmbedding), null, DownloadMetadataSettings())
        assertTrue(result.issues.isEmpty())
        assertEquals(1, requests)
        val tag = AudioFileIO.read(file).tag
        assertEquals("[00:00.00]原始歌词", tag.getFirst(FieldKey.LYRICS))
        assertTrue(tag.getFirst(FieldKey.TITLE).isBlank())
        assertTrue(tag.artworkList.isEmpty())
    }

    @Test fun unrequestedAssetsDoNotIssueNetworkRequests() = withAudio { file ->
        val result = writer().write(file, item(file, embedding = null), metadata, DownloadMetadataSettings(embedCover = false))
        assertTrue(result.issues.isEmpty())
        assertNull(result.lyricsSource)
        assertEquals(0, requests)
        val tag = AudioFileIO.read(file).tag
        assertEquals("当前歌曲", tag.getFirst(FieldKey.TITLE))
        assertTrue(tag.getFirst(FieldKey.LYRICS).isBlank())
        assertTrue(tag.artworkList.isEmpty())
    }

    @Test fun nothingToWriteLeavesTheFileAlone() = withAudio { file ->
        val before = file.readBytes()
        val result = writer().write(file, item(file, embedding = null), null, DownloadMetadataSettings())
        assertEquals(EmbeddedContentResult(), result)
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun coverFailureDoesNotBlockLyricsOrTextMetadata() = withAudio { file ->
        val result = writer(coverStatus = 404).write(file, item(file, lyricsEmbedding), metadata, DownloadMetadataSettings())
        assertEquals(setOf(EmbeddedContentIssue.Cover), result.issues)
        val tag = AudioFileIO.read(file).tag
        assertEquals("当前歌曲", tag.getFirst(FieldKey.TITLE))
        assertEquals("[00:00.00]原始歌词", tag.getFirst(FieldKey.LYRICS))
        assertTrue(tag.artworkList.isEmpty())
    }

    @Test fun htmlLyricsAreOmittedAndReportedWithoutLosingCover() = withAudio { file ->
        val result = writer(lyrics = "<html>Error</html>").write(file, item(file, lyricsEmbedding), metadata, DownloadMetadataSettings())
        assertEquals(setOf(EmbeddedContentIssue.LyricsFailed), result.issues)
        assertNull(result.lyricsSource)
        val tag = AudioFileIO.read(file).tag
        assertEquals("当前歌曲", tag.getFirst(FieldKey.TITLE))
        assertTrue(tag.getFirst(FieldKey.LYRICS).isBlank())
        assertFalse(tag.artworkList.isEmpty())
    }

    @Test fun malformedTextEncodingIsReportedInsteadOfEmbeddingReplacementCharacters() = withAudio { file ->
        val result = writer(lyricsBytes = byteArrayOf(0xc3.toByte(), 0x28))
            .write(file, item(file, lyricsEmbedding), metadata, DownloadMetadataSettings())
        assertEquals(setOf(EmbeddedContentIssue.LyricsFailed), result.issues)
        assertTrue(AudioFileIO.read(file).tag.getFirst(FieldKey.LYRICS).isBlank())
    }

    @Test fun lyricsWithoutAnySourceAreReportedAsUnavailable() = withAudio { file ->
        val sourceless = metadata.copy(lyricUrl = null, musicSid = null, subtitleAid = null, subtitleCid = null)
        val result = writer().write(
            file, item(file, lyricsEmbedding).copy(embeddedMetadata = sourceless), sourceless, DownloadMetadataSettings(embedCover = false),
        )
        assertEquals(setOf(EmbeddedContentIssue.LyricsUnavailable), result.issues)
        assertEquals(0, requests)
        assertEquals("当前歌曲", AudioFileIO.read(file).tag.getFirst(FieldKey.TITLE))
    }

    @Test fun subtitleTracksAreNeverForcedIntoAudioFiles() = withAudio { file ->
        val embedding = DownloadEmbedding(subtitles = SubtitleTrackEmbedding(listOf("zh-Hans")))
        val result = writer().write(file, item(file, embedding), null, DownloadMetadataSettings())
        assertEquals(EmbeddedContentResult(), result)
        assertEquals(0, requests)
    }

    @Test fun subtitleTracksAreRejectedForFlvContainers() = withAudio { file ->
        val embedding = DownloadEmbedding(subtitles = SubtitleTrackEmbedding(listOf("zh-Hans")))
        val video = item(file, embedding).copy(taskType = DownloadTaskType.Video, fileName = "video.flv")
        val result = writer().write(file, video, null, DownloadMetadataSettings())
        assertEquals(setOf(EmbeddedContentIssue.SubtitlesUnsupportedContainer), result.issues)
        assertEquals(0, requests)
    }

    @Test fun cancellationPropagatesAndLeavesSourceUntouched() = withAudio { file ->
        val before = file.readBytes()
        try {
            writer(onRequest = { throw CancellationException("Test cancellation") })
                .write(file, item(file, lyricsEmbedding), metadata, DownloadMetadataSettings())
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) {
            assertArrayEquals(before, file.readBytes())
        }
    }

    @Test fun selectedLanguagesRemovedBeforeDownloadAreReportedEvenWhenAnotherTrackSucceeds() = runBlocking {
        val issues = mutableSetOf<EmbeddedContentIssue>()
        val incomplete = mutableListOf<String>()
        val temporaryFiles = mutableListOf<File>()
        try {
            val tracks = subtitleWriter().resolveSubtitleTracks(
                metadata.copy(subtitleAid = 1, subtitleCid = 2),
                SubtitleTrackEmbedding(listOf("zh-Hans", "ai-zh")), directory, issues, incomplete,
                onTemporaryFile = { temporaryFiles += it },
            )
            assertEquals(setOf(EmbeddedContentIssue.SubtitlesPartiallyFailed), issues)
            assertEquals(listOf("中文 · AI 字幕"), incomplete)
            assertEquals(listOf("中文（简体）"), tracks.map { it.title })
            assertEquals(tracks.map { it.file }, temporaryFiles)
            assertTrue(temporaryFiles.single().readText().contains("字幕示例"))
        } finally {
            temporaryFiles.forEach { it.delete() }
        }
    }

    @Test fun allSubtitleTracksIncludeAiWithAnExplicitSourceLabel() = runBlocking {
        val issues = mutableSetOf<EmbeddedContentIssue>()
        val incomplete = mutableListOf<String>()
        val temporaryFiles = mutableListOf<File>()
        try {
            val tracks = subtitleWriter().resolveSubtitleTracks(
                metadata.copy(subtitleAid = 1, subtitleCid = 2),
                SubtitleTrackEmbedding(),
                directory, issues, incomplete, onTemporaryFile = { temporaryFiles += it },
            )
            assertEquals(listOf("中文（简体）", "英语 · AI 字幕"), tracks.map { it.title })
            assertTrue(issues.isEmpty())
            assertTrue(incomplete.isEmpty())
        } finally {
            temporaryFiles.forEach { it.delete() }
        }
    }

    @Test fun cancellingSecondSubtitleDownloadCleansFirstTrackAndPreservesOriginalVideo() = withAudio { file ->
        val original = file.readBytes()
        val existingFiles = directory.list()!!.toSet()
        val embedding = DownloadEmbedding(subtitles = SubtitleTrackEmbedding(listOf("zh-Hans", "ai-en")))
        val video = item(file, embedding).copy(
            taskType = DownloadTaskType.Video, fileName = "video.mp4",
            embeddedMetadata = metadata.copy(lyricUrl = null, subtitleAid = 1, subtitleCid = 2),
        )
        try {
            subtitleWriter { request ->
                if (request.url.encodedPath == "/ai-en.json") throw CancellationException("Paused during second subtitle")
            }.write(file, video, null, DownloadMetadataSettings())
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) {
            assertArrayEquals(original, file.readBytes())
            assertEquals(existingFiles, directory.list()!!.toSet())
        }
    }

    @Test fun missingLyricsSelectionNeverChoosesAnotherSubtitle() = withAudio { file ->
        val sources = metadata.copy(lyricUrl = null, subtitleAid = 1, subtitleCid = 2)
        val result = writer().write(
            file, item(file, lyricsEmbedding).copy(embeddedMetadata = sources), null, DownloadMetadataSettings(),
        )
        assertEquals(setOf(EmbeddedContentIssue.LyricsUnavailable), result.issues)
        assertEquals(0, requests)
    }

    @Test fun failedTagWritePreservesSourceBytesAndCleansTemporaryFiles() = withAudio { file ->
        val incomplete = byteArrayOf(1, 2, 3, 4)
        file.writeBytes(incomplete)
        val before = directory.list()!!.toSet()
        val result = writer().write(file, item(file, lyricsEmbedding), metadata, DownloadMetadataSettings())
        assertEquals(setOf(EmbeddedContentIssue.WriteFailed), result.issues)
        assertArrayEquals(incomplete, file.readBytes())
        assertEquals(before, directory.list()!!.toSet())
    }

    @Test fun rawAudioDoesNotReceiveForeignContainerTags() = withAudio { file ->
        val before = file.readBytes()
        val result = writer().write(file, item(file, lyricsEmbedding).copy(fileName = "audio.eac3"), metadata, DownloadMetadataSettings())
        assertEquals(setOf(EmbeddedContentIssue.UnsupportedFormat), result.issues)
        assertEquals(0, requests)
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun metadataOptionsSurviveRepositoryReload() {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsRepository(context)
        val options = DownloadMetadataSettings(embedCover = false, useUploaderAsArtist = false, useCollectionAsAlbum = false)
        settings.setDownloadMetadata(options)
        assertEquals(options, SettingsRepository(context).currentSettings().metadata)
    }

    private fun writer(
        coverStatus: Int = 200,
        lyrics: String = "[00:00.00]原始歌词",
        lyricsBytes: ByteArray? = null,
        onRequest: () -> Unit = {},
    ): EmbeddedContentWriter {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            onRequest()
            val isCover = chain.request().url.encodedPath.endsWith(".jpg")
            val bytes = if (isCover) javaClass.getResourceAsStream("/metadata/cover.jpg")!!.use { it.readBytes() }
                else lyricsBytes ?: lyrics.toByteArray(Charsets.UTF_8)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (isCover) coverStatus else 200).message("Fixture")
                .body(bytes.toResponseBody((if (isCover) "image/jpeg" else "text/plain; charset=utf-8").toMediaType()))
                .build()
        }.build()
        val context = RuntimeEnvironment.getApplication()
        val bili = BiliHttpClient(CookieStore(context), SettingsRepository(context))
        return EmbeddedContentWriter(client, ExtrasRepository(bili, WbiSigner(bili)))
    }

    private fun subtitleWriter(onRequest: (Request) -> Unit = {}): EmbeddedContentWriter {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            onRequest(request)
            val content = if (request.url.encodedPath == "/x/player/wbi/v2") {
                """{"code":0,"data":{"subtitle":{"subtitles":[
                    {"lan":"zh-Hans","lan_doc":"中文（简体）","subtitle_url":"https://example.com/zh.json"},
                    {"lan":"ai-en","lan_doc":"英语（自动生成）","subtitle_url":"https://example.com/ai-en.json","ai_type":1}
                ]}}}"""
            } else {
                """{"body":[{"from":0,"to":1,"content":"字幕示例"}]}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(content.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
        }.build()
        val context = RuntimeEnvironment.getApplication()
        val bili = BiliHttpClient(CookieStore(context), SettingsRepository(context))
        ReflectionHelpers.setField(bili, "client\$delegate", lazyOf(client))
        val signer = WbiSigner(bili)
        ReflectionHelpers.setField(signer, "cachedMixinKey", "fixture")
        ReflectionHelpers.setField(signer, "lastUpdateMs", System.currentTimeMillis())
        return EmbeddedContentWriter(client, ExtrasRepository(bili, signer))
    }

    private fun item(file: File, embedding: DownloadEmbedding?) = DownloadItem(
        id = 1, groupId = 1, taskType = DownloadTaskType.Audio, title = "音频", fileName = file.name,
        url = "https://example.com/audio", status = DownloadStatus.Running, progress = 100,
        embeddedMetadata = metadata,
        embedding = embedding,
    )

    private fun withAudio(block: suspend (File) -> Unit) = runBlocking {
        val file = File.createTempFile("audio-", ".mp3", directory)
        try {
            javaClass.getResourceAsStream("/metadata/audio.mp3")!!.use { source ->
                file.outputStream().use { source.copyTo(it) }
            }
            block(file)
        } finally { file.delete() }
    }
}
