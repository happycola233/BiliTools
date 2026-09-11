package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadMetadataWriterTest {
    private var requests = 0
    private val directory = File("../.tmp/metadata/writer-tests").apply { mkdirs() }

    @Test fun writesCoverAndOriginalLyricsOnAndroid() = withAudio { file ->
        val issues = writer().write(file, item(file), DownloadMetadataSettings())
        assertTrue(issues.toString(), issues.isEmpty())
        val tag = AudioFileIO.read(file).tag
        assertEquals("当前歌曲", tag.getFirst(FieldKey.TITLE))
        assertEquals("[00:00.00]原始歌词", tag.getFirst(FieldKey.LYRICS))
        assertEquals("image/jpeg", tag.firstArtwork.mimeType)
        assertEquals(2, requests)
    }

    @Test fun disabledAssetsDoNotIssueNetworkRequests() = withAudio { file ->
        val issues = writer().write(file, item(file), DownloadMetadataSettings(embedCover = false, embedLyrics = false))
        assertTrue(issues.isEmpty())
        assertEquals(0, requests)
        val tag = AudioFileIO.read(file).tag
        assertEquals("当前歌曲", tag.getFirst(FieldKey.TITLE))
        assertTrue(tag.getFirst(FieldKey.LYRICS).isBlank())
        assertTrue(tag.artworkList.isEmpty())
    }

    @Test fun coverFailureDoesNotBlockLyricsOrTextMetadata() = withAudio { file ->
        val issues = writer(coverStatus = 404).write(file, item(file), DownloadMetadataSettings())
        assertEquals(setOf(MetadataWriteIssue.Cover), issues)
        val tag = AudioFileIO.read(file).tag
        assertEquals("当前歌曲", tag.getFirst(FieldKey.TITLE))
        assertEquals("[00:00.00]原始歌词", tag.getFirst(FieldKey.LYRICS))
        assertTrue(tag.artworkList.isEmpty())
    }

    @Test fun htmlLyricsAreOmittedAndReportedWithoutLosingCover() = withAudio { file ->
        val issues = writer(lyrics = "<html>Error</html>").write(file, item(file), DownloadMetadataSettings())
        assertEquals(setOf(MetadataWriteIssue.Lyrics), issues)
        val tag = AudioFileIO.read(file).tag
        assertEquals("当前歌曲", tag.getFirst(FieldKey.TITLE))
        assertTrue(tag.getFirst(FieldKey.LYRICS).isBlank())
        assertFalse(tag.artworkList.isEmpty())
    }

    @Test fun malformedTextEncodingIsReportedInsteadOfEmbeddingReplacementCharacters() = withAudio { file ->
        val issues = writer(lyricsBytes = byteArrayOf(0xc3.toByte(), 0x28)).write(file, item(file), DownloadMetadataSettings())
        assertEquals(setOf(MetadataWriteIssue.Lyrics), issues)
        assertTrue(AudioFileIO.read(file).tag.getFirst(FieldKey.LYRICS).isBlank())
    }

    @Test fun cancellationPropagatesAndLeavesSourceUntouched() = withAudio { file ->
        val before = file.readBytes()
        try {
            writer(onRequest = { throw CancellationException("Test cancellation") })
                .write(file, item(file), DownloadMetadataSettings())
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) {
            assertArrayEquals(before, file.readBytes())
        }
    }

    @Test fun failedTagWritePreservesSourceBytesAndCleansTemporaryFiles() = withAudio { file ->
        val incomplete = byteArrayOf(1, 2, 3, 4)
        file.writeBytes(incomplete)
        val before = directory.list()!!.toSet()
        val issues = writer().write(file, item(file), DownloadMetadataSettings())
        assertEquals(setOf(MetadataWriteIssue.Tags), issues)
        assertArrayEquals(incomplete, file.readBytes())
        assertEquals(before, directory.list()!!.toSet())
    }

    @Test fun rawAudioDoesNotReceiveForeignContainerTags() = withAudio { file ->
        val before = file.readBytes()
        val issues = writer().write(file, item(file).copy(fileName = "audio.eac3"), DownloadMetadataSettings())
        assertEquals(setOf(MetadataWriteIssue.UnsupportedFormat), issues)
        assertEquals(0, requests)
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun metadataOptionsSurviveRepositoryReload() {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsRepository(context)
        val options = DownloadMetadataSettings(false, false, SubtitleLyricsMode.PreferManual, false, false)
        settings.setDownloadMetadata(options)
        assertEquals(options, SettingsRepository(context).currentSettings().metadata)
    }

    private fun writer(
        coverStatus: Int = 200,
        lyrics: String = "[00:00.00]原始歌词",
        lyricsBytes: ByteArray? = null,
        onRequest: () -> Unit = {},
    ): DownloadMetadataWriter {
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
        return DownloadMetadataWriter(client, ExtrasRepository(bili, WbiSigner(bili)))
    }

    private fun item(file: File) = DownloadItem(
        id = 1, groupId = 1, taskType = DownloadTaskType.Audio, title = "音频", fileName = file.name,
        url = "https://example.com/audio", status = DownloadStatus.Running, progress = 100,
        embeddedMetadata = DownloadEmbeddedMetadata(
            title = "当前歌曲", artist = "作者", coverUrl = "https://example.com/cover.jpg",
            lyricUrl = "https://example.com/lyrics.lrc",
        ),
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
