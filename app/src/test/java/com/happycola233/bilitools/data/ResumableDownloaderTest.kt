package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadSource
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ResumableDownloaderTest {
    private lateinit var directory: File
    private val servers = mutableListOf<MockWebServer>()
    private val clients = mutableListOf<OkHttpClient>()

    @Before fun setUp() {
        val root = File("../.tmp/resumable-download-tests").apply { mkdirs() }
        directory = Files.createTempDirectory(root.toPath(), "case-").toFile()
    }

    @After fun tearDown() {
        clients.forEach { it.connectionPool.evictAll(); it.dispatcher.executorService.shutdown() }
        servers.forEach { it.close() }
        directory.deleteRecursively()
    }

    @Test fun tlsFailureSwitchesToBackupWithoutRefreshingTheApi() = runBlocking(Dispatchers.IO) {
        val backup = server().apply { enqueue(MockResponse(body = "video")) }
        val primaryUrl = "https://unreachable.example.com/video"
        val target = target(primaryUrl, backup.url("/video").toString())
        val client = client().newBuilder().addInterceptor { chain ->
            if (chain.request().url.host == "unreachable.example.com") throw SSLHandshakeException("connection closed")
            chain.proceed(chain.request())
        }.build().also(clients::add)
        var refreshCount = 0
        val failures = mutableListOf<String>()

        ResumableDownloader(client).download(
            target,
            refreshSource = { refreshCount++; null },
            onFailure = { _, _, failure -> failures += failure.stage },
        ) { _, _, _, _ -> }

        assertEquals("video", target.tempFile.readText())
        assertEquals(listOf("connect"), failures)
        assertEquals(0, refreshCount)
        assertEquals(1, backup.requestCount)
    }

    @Test fun truncatedBodyResumesFromWrittenBytesOnAnotherNode() = runBlocking(Dispatchers.IO) {
        val content = ByteArray(128 * 1024) { (it % 251).toByte() }
        val primary = server().apply {
            enqueue(MockResponse.Builder().body(Buffer().write(content)).addHeader("ETag", "\"primary\"")
                .onResponseBody(SocketEffect.CloseSocket()).build())
        }
        val backup = server().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val offset = request.headers["Range"]!!.removePrefix("bytes=").removeSuffix("-").toInt()
                    return MockResponse.Builder().code(206)
                        .addHeader("Content-Range", "bytes $offset-${content.lastIndex}/${content.size}")
                        .addHeader("ETag", "\"backup\"")
                        .body(Buffer().write(content, offset, content.size - offset)).build()
                }
            }
        }
        val target = target(primary.url("/video").toString(), backup.url("/video").toString())
        val failures = mutableListOf<String>()
        ResumableDownloader(client()).download(target, onFailure = { _, _, e -> failures += e.stage }) { _, _, _, _ -> }

        assertArrayEquals(content, target.tempFile.readBytes())
        assertEquals(listOf("body"), failures)
        val request = backup.takeRequest(2, TimeUnit.SECONDS)!!
        val offset = request.headers["Range"]!!.removePrefix("bytes=").removeSuffix("-").toLong()
        assertTrue(offset in 1 until content.size.toLong())
        assertNull(request.headers["If-Range"])
        assertEquals(backup.url("/video").toString(), target.validatorUrl)
        assertEquals("\"backup\"", target.etag)
    }

    @Test fun aFullResponseToRangeReplacesTheFileWithoutASecondRequest() = runBlocking(Dispatchers.IO) {
        val server = server().apply { enqueue(MockResponse(body = "replacement")) }
        val target = target(server.url("/video").toString()).apply {
            tempFile.writeText("old prefix")
            etag = "\"old\""
            validatorUrl = source.url
        }
        ResumableDownloader(client()).download(target) { _, _, _, _ -> }
        assertEquals("replacement", target.tempFile.readText())
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("bytes=10-", request.headers["Range"])
        assertEquals("\"old\"", request.headers["If-Range"])
        assertNull(target.etag)
    }

    @Test fun resumingAPausedTransferReusesItsWorkingBackupAndValidator() = runBlocking(Dispatchers.IO) {
        val primary = server()
        val backup = server().apply {
            enqueue(MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 2-5/6").body("cdef").build())
        }
        val target = target(primary.url("/video").toString(), backup.url("/video").toString()).apply {
            tempFile.writeText("ab")
            totalBytes = 6
            validatorUrl = backup.url("/video").toString()
            etag = "\"backup\""
        }
        ResumableDownloader(client()).download(target) { _, _, _, _ -> }
        assertEquals("abcdef", target.tempFile.readText())
        assertEquals(0, primary.requestCount)
        assertEquals("\"backup\"", backup.takeRequest().headers["If-Range"])
    }

    @Test fun incorrectRangeNeverGetsAppendedAndBackupCanResume() = runBlocking(Dispatchers.IO) {
        val primary = server().apply {
            enqueue(MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 0-2/6").body("bad").build())
        }
        val backup = server().apply {
            enqueue(MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 3-5/6").body("def").build())
        }
        val target = target(primary.url("/video").toString(), backup.url("/video").toString()).apply {
            tempFile.writeText("abc")
            totalBytes = 6
        }
        ResumableDownloader(client()).download(target) { _, _, _, _ -> }
        assertEquals("abcdef", target.tempFile.readText())
        assertEquals("bytes=3-", backup.takeRequest().headers["Range"])
    }

    @Test fun changedTotalRestartsInsteadOfCombiningDifferentRepresentations() = runBlocking(Dispatchers.IO) {
        val server = server().apply {
            enqueue(MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 3-7/8").body("defgh").build())
            enqueue(MockResponse(body = "abcdefgh"))
        }
        val target = target(server.url("/video").toString()).apply {
            tempFile.writeText("old")
            totalBytes = 6
        }
        ResumableDownloader(client()).download(target) { _, _, _, _ -> }
        assertEquals("abcdefgh", target.tempFile.readText())
        assertEquals("bytes=3-", server.takeRequest().headers["Range"])
        assertNull(server.takeRequest().headers["Range"])
    }

    @Test fun range416OnlyCompletesAnExactlySizedFile() = runBlocking(Dispatchers.IO) {
        val server = server().apply {
            enqueue(MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */5").build())
        }
        val target = target(server.url("/video").toString()).apply { tempFile.writeText("hello") }
        ResumableDownloader(client()).download(target) { _, _, _, _ -> }
        assertEquals("hello", target.tempFile.readText())
        assertEquals(5L, target.totalBytes)
        assertEquals(1, server.requestCount)
    }

    @Test fun staleRange416RestartsTheFile() = runBlocking(Dispatchers.IO) {
        val server = server().apply {
            enqueue(MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */3").build())
            enqueue(MockResponse(body = "new"))
        }
        val target = target(server.url("/video").toString()).apply { tempFile.writeText("obsolete") }
        ResumableDownloader(client()).download(target) { _, _, _, _ -> }
        assertEquals("new", target.tempFile.readText())
        assertEquals(2, server.requestCount)
    }

    @Test fun exhaustingCandidatesRefreshesTheWholeListOnce() = runBlocking(Dispatchers.IO) {
        val old = server().apply { enqueue(MockResponse(code = 403)); enqueue(MockResponse(code = 503)) }
        val fresh = server().apply { enqueue(MockResponse(code = 403)); enqueue(MockResponse(body = "fresh")) }
        val target = target(old.url("/primary").toString(), old.url("/backup").toString())
        var refreshCount = 0
        val updated = DownloadSource(fresh.url("/primary").toString(), listOf(fresh.url("/backup").toString()))
        ResumableDownloader(client()).download(target, refreshSource = { refreshCount++; updated }) { _, _, _, _ -> }
        assertEquals("fresh", target.tempFile.readText())
        assertEquals(updated, target.source)
        assertEquals(1, refreshCount)
        assertEquals(2, old.requestCount)
        assertEquals(2, fresh.requestCount)
    }

    @Test fun repeatedFailuresHaveABoundedAttemptCount() = runBlocking(Dispatchers.IO) {
        val server = server().apply { repeat(4) { enqueue(MockResponse(code = 503)) } }
        val target = target(server.url("/primary").toString(), server.url("/backup").toString())
        var refreshCount = 0
        val failure = expectIoFailure {
            ResumableDownloader(client()).download(target, refreshSource = { refreshCount++; target.source }) { _, _, _, _ -> }
        }
        assertEquals("HTTP 503", failure.message)
        assertEquals(4, server.requestCount)
        assertEquals(1, refreshCount)
    }

    @Test fun filesystemFailureDoesNotSwitchSourcesOrRefresh() = runBlocking(Dispatchers.IO) {
        val server = server().apply { enqueue(MockResponse(body = "data")); enqueue(MockResponse(body = "data")) }
        val target = target(server.url("/primary").toString(), server.url("/backup").toString())
        target.tempFile.mkdirs()
        var refreshCount = 0
        val failure = expectIoFailure {
            ResumableDownloader(client()).download(target, refreshSource = { refreshCount++; target.source }) { _, _, _, _ -> }
        }
        assertFalse(failure is DownloadSourceFailure)
        assertEquals(1, server.requestCount)
        assertEquals(0, refreshCount)
    }

    @Test fun cancellingABlockedRequestClosesItWithoutFailover() = runBlocking(Dispatchers.IO) {
        val server = server().apply {
            enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
            enqueue(MockResponse(body = "backup"))
        }
        val target = target(server.url("/primary").toString(), server.url("/backup").toString())
        var refreshCount = 0
        val job = launch {
            ResumableDownloader(client()).download(target, refreshSource = { refreshCount++; target.source }) { _, _, _, _ -> }
        }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        withTimeout(1500) { job.cancelAndJoin() }
        assertEquals(1, server.requestCount)
        assertEquals(0, refreshCount)
    }

    @Test fun cancellingABlockedBodyClosesTheFileWithoutFailover() = runBlocking(Dispatchers.IO) {
        val server = server().apply {
            enqueue(MockResponse.Builder().body("data".repeat(8192)).onResponseBody(SocketEffect.Stall).build())
            enqueue(MockResponse(body = "backup"))
        }
        val target = target(server.url("/primary").toString(), server.url("/backup").toString())
        val bodyStarted = CompletableDeferred<Unit>()
        val job = launch {
            ResumableDownloader(client()).download(target) { _, _, _, _ -> bodyStarted.complete(Unit) }
        }
        withTimeout(2000) { bodyStarted.await() }
        withTimeout(1500) { job.cancelAndJoin() }
        assertEquals(1, server.requestCount)
        // Windows 下打开的文件不能删除；确保取消返回时读取与写入资源均已释放。
        assertTrue(target.tempFile.delete())
    }

    private fun server() = MockWebServer().also { it.start(); servers += it }

    private fun client() = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build().also(clients::add)

    private fun target(primary: String, vararg backups: String) = Target(
        DownloadSource(primary, backups.toList()), File(directory, "media.part"),
    )

    private suspend fun expectIoFailure(block: suspend () -> Unit): IOException {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (error: IOException) {
            return error
        }
    }

    private data class Target(
        override var source: DownloadSource,
        override val tempFile: File,
        override var downloadedBytes: Long = 0,
        override var totalBytes: Long = 0,
        override var etag: String? = null,
        override var lastModified: String? = null,
        override var validatorUrl: String? = null,
        override var speedBytesPerSec: Long = 0,
        override val transferMutex: Mutex = Mutex(),
    ) : ResumableDownloadTarget
}
