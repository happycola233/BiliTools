package com.happycola233.bilitools.data

import android.content.pm.ProviderInfo
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.LogRedirectionStrategy
import com.arthenica.ffmpegkit.MediaInformationJsonParser
import com.arthenica.ffmpegkit.MediaInformationSession
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.MediaProcessingEngine
import com.happycola233.bilitools.core.WbiSigner
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers

/** 用主机 FFmpeg 执行真实的检查、重试和合并保存链路，不加载 Android 原生库或连接设备。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [DownloadMergeCompatibilityTest.HostMediaConfig::class])
class DownloadMergeCompatibilityTest {
    private lateinit var directory: File
    private lateinit var settings: SettingsRepository
    private lateinit var repository: DownloadRepository
    private lateinit var provider: DownloadTestMediaProvider
    private lateinit var task: Any
    private lateinit var video: ResumableDownloadTarget
    private lateinit var audio: ResumableDownloadTarget

    @Before fun setUp() {
        assumeTrue("Set BILITOOLS_TEST_FFMPEG to run media round trips",
            !System.getenv("BILITOOLS_TEST_FFMPEG").isNullOrBlank())
        val root = File("../.tmp/merge-compatibility-tests").apply { mkdirs() }
        directory = Files.createTempDirectory(root.toPath(), "case-").toFile()
        HostMediaConfig.cancelNextProbe = false
        val context = RuntimeEnvironment.getApplication()
        provider = DownloadTestMediaProvider(directory)
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        settings = SettingsRepository(context).apply { setAddMetadata(false) }
        val cookies = CookieStore(context)
        val bili = BiliHttpClient(cookies)
        val signer = WbiSigner(bili)
        repository = DownloadRepository(context, cookies, settings,
            MediaRepository(bili, signer, cookies, OpusRepository(bili, cookies)),
            ExtrasRepository(bili, signer), ExportRepository(context, settings))
        // 调度暂停，仅手动调用真实的重试准备与合并保存，避免任何网络请求。
        ReflectionHelpers.getField<CoroutineScope>(repository, "scope").cancel()
        ReflectionHelpers.setField(repository, "storeFile", File(directory, "downloads.json"))
        ReflectionHelpers.setField(repository, "tempDir\$delegate", lazyOf(directory))
        val group = repository.createGroup("合并回归", null)
        val item = repository.enqueueDashMerge(group, "视频", "中文 空格 (Hi-Res) ;.mkv",
            "https://example.invalid/video", "https://example.invalid/audio")
        task = ReflectionHelpers.getField<Map<Long, Any>>(repository, "mergeTasks").getValue(item.id)
        video = ReflectionHelpers.getField(task, "video")
        audio = ReflectionHelpers.getField(task, "audio")
    }

    @After fun tearDown() {
        if (::settings.isInitialized) ReflectionHelpers.getField<CoroutineScope>(settings, "settingsScope").cancel()
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    @Test fun hiResFlacSurvivesRetryAndMergesWithoutReencoding() = runBlocking { verifyMerge("flac") }

    @Test fun aacStillSurvivesRetryAndMergesWithoutReencoding() = runBlocking { verifyMerge("aac") }

    @Test fun dolbyStillSurvivesRetryAndMergesWithoutReencoding() = runBlocking { verifyMerge("eac3") }

    private suspend fun verifyMerge(codec: String) {
        generateParts(codec)
        val videoHashes = packetHashes(video.tempFile, "v")
        val audioHashes = packetHashes(audio.tempFile, "a")
        invokeTaskOperation<Unit>("prepareMergedTaskForRun")
        for (part in listOf(video, audio)) {
            assertTrue(ReflectionHelpers.getField(part, "completed"))
            assertEquals(part.tempFile.length(), part.downloadedBytes)
            assertTrue(part.tempFile.length() > 0)
        }
        val uri = requireNotNull(invokeTaskOperation<String?>("performMerge"))
        val output = provider.row(uri).file
        assertPacketsCopied(videoHashes, packetHashes(output, "v"))
        assertPacketsCopied(audioHashes, packetHashes(output, "a"))
        val information = MediaInformationJsonParser.fromWithError(
            runTool("ffprobe", listOf("-v", "error", "-show_streams", "-of", "json", output.path)).second)
        assertEquals(codec, information.streams.single { it.type == "audio" }.codec)
        assertFalse(video.tempFile.exists())
        assertFalse(audio.tempFile.exists())
    }

    @Test fun invalidDownloadedAudioIsRejectedAndOnlyThatPartIsReset() = runBlocking {
        generateParts("flac")
        audio.tempFile.writeText("<html>CDN error</html>")
        assertNull(invokeTaskOperation<String?>("performMerge"))
        assertTrue(provider.rows.isEmpty())
        assertTrue(audio.tempFile.exists())
        invokeTaskOperation<Unit>("prepareMergedTaskForRun")
        assertTrue(video.tempFile.exists())
        assertTrue(ReflectionHelpers.getField(video, "completed"))
        assertFalse(audio.tempFile.exists())
        assertFalse(ReflectionHelpers.getField(audio, "completed"))
        assertEquals(0L, audio.downloadedBytes)
    }

    @Test fun videoDisguisedAsAudioIsRejectedByItsActualStreamType() = runBlocking {
        generateParts("flac")
        video.tempFile.copyTo(audio.tempFile, overwrite = true)
        assertFalse(MediaProcessingEngine.hasMediaStream(audio.tempFile, "audio"))
        assertTrue(MediaProcessingEngine.hasMediaStream(audio.tempFile, "video"))
    }

    @Test fun incompletePartsAreKeptForRangeResumeWithoutMediaInspection() = runBlocking {
        listOf(video, audio).forEach { part ->
            part.tempFile.writeBytes(byteArrayOf(0, 0, 0, 24))
            part.totalBytes = 100
        }
        invokeTaskOperation<Unit>("prepareMergedTaskForRun")
        listOf(video, audio).forEach { part ->
            assertTrue(part.tempFile.exists())
            assertEquals(4L, part.downloadedBytes)
            assertEquals(100L, part.totalBytes)
            assertFalse(ReflectionHelpers.getField(part, "completed"))
        }
    }

    @Test fun cancelledInspectionDoesNotDeleteCompletedParts() = runBlocking {
        generateParts("flac")
        HostMediaConfig.cancelNextProbe = true
        try {
            invokeTaskOperation<Unit>("prepareMergedTaskForRun")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            listOf(video, audio).forEach { part ->
                assertTrue(part.tempFile.exists())
                assertTrue(ReflectionHelpers.getField(part, "completed"))
            }
        }
    }

    @Test fun missingAndEmptyPartsAreRejected() = runBlocking {
        assertFalse(MediaProcessingEngine.hasMediaStream(audio.tempFile, "audio"))
        audio.tempFile.writeBytes(byteArrayOf())
        assertFalse(MediaProcessingEngine.hasMediaStream(audio.tempFile, "audio"))
    }

    private fun generateParts(codec: String) {
        runTool("ffmpeg", listOf("-v", "error", "-y", "-f", "lavfi", "-i",
            "color=c=red:s=128x96:r=10:d=1", "-c:v", "libx264", "-pix_fmt", "yuv420p",
            "-an", "-movflags", "+frag_keyframe+empty_moov", "-f", "mp4", video.tempFile.path))
        val sampleRate = if (codec == "flac") 96000 else 48000
        runTool("ffmpeg", listOf("-v", "error", "-y", "-f", "lavfi", "-i",
            "sine=frequency=440:sample_rate=$sampleRate:duration=1", "-ac", "2", "-c:a", codec) +
            (if (codec == "flac") listOf("-sample_fmt", "s32") else emptyList()) +
            listOf("-strict", "unofficial", "-movflags", "+frag_keyframe+delay_moov", "-f", "mp4", audio.tempFile.path))
        listOf(video, audio).forEach { part ->
            part.downloadedBytes = part.tempFile.length()
            part.totalBytes = part.tempFile.length()
            ReflectionHelpers.setField(part, "completed", true)
        }
    }

    private fun packetHashes(file: File, stream: String): List<String> {
        val output = runTool("ffprobe", listOf("-v", "error", "-select_streams", stream,
            "-show_packets", "-show_entries", "packet=data_hash", "-show_data_hash", "sha256",
            "-of", "json", file.path)).second
        // 只比较压缩数据哈希，不把 AAC 的 Skip Samples 等容器附加信息计入。
        val packets = JSONObject(output).getJSONArray("packets")
        return (0 until packets.length()).map { packets.getJSONObject(it).getString("data_hash") }
            .also { assertTrue(it.isNotEmpty()) }
    }

    private fun assertPacketsCopied(source: List<String>, output: List<String>) {
        // 合并沿用 -shortest，可能裁掉超出较短流的尾包；保留下来的包必须逐包一致。
        assertEquals(source.take(output.size), output)
    }

    private suspend fun <T> invokeTaskOperation(name: String): T = suspendCoroutineUninterceptedOrReturn { continuation ->
        val method = DownloadRepository::class.java.getDeclaredMethod(name, task.javaClass, Continuation::class.java)
            .apply { isAccessible = true }
        try {
            method.invoke(repository, task, continuation)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    /** 仅替换原生执行边界；参数、媒体探测结果解析与取消传播都走应用及依赖库的原有代码。 */
    @Implements(FFmpegKitConfig::class, isInAndroidSdk = false)
    class HostMediaConfig {
        companion object {
            var cancelNextProbe = false

            @JvmStatic @Implementation fun __staticInitializer__() = Unit
            @JvmStatic @Implementation fun addSession(session: Session) = Unit
            @JvmStatic @Implementation fun getLogRedirectionStrategy() = LogRedirectionStrategy.NEVER_PRINT_LOGS

            @JvmStatic @Implementation
            fun asyncGetMediaInformationExecute(session: MediaInformationSession, waitTimeout: Int) {
                val (code, output) = if (cancelNextProbe) {
                    cancelNextProbe = false
                    255 to ""
                } else runTool("ffprobe", session.arguments.toList(), allowFailure = true)
                if (code == 0) session.mediaInformation = MediaInformationJsonParser.fromWithError(output)
                ReflectionHelpers.setField(session, "returnCode", ReturnCode(code))
                session.completeCallback.apply(session)
            }

            @JvmStatic @Implementation fun asyncFFmpegExecute(session: FFmpegSession) {
                val (code, _) = runTool("ffmpeg", session.arguments.toList(), allowFailure = true)
                ReflectionHelpers.setField(session, "returnCode", ReturnCode(code))
                session.completeCallback.apply(session)
            }
        }
    }

    companion object {
        private fun runTool(tool: String, arguments: List<String>, allowFailure: Boolean = false): Pair<Int, String> {
            val executable = File(requireNotNull(System.getenv("BILITOOLS_TEST_FFMPEG")), "$tool.exe")
            val process = ProcessBuilder(listOf(executable.path) + arguments).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertTrue("Media command timed out", process.waitFor(30, TimeUnit.SECONDS))
            val code = process.exitValue()
            if (!allowFailure) assertEquals(output, 0, code)
            return code to output
        }
    }
}
