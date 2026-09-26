package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadSource
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Request

internal interface ResumableDownloadTarget {
    val transferMutex: Mutex
    var source: DownloadSource
    val tempFile: File
    var downloadedBytes: Long
    var totalBytes: Long
    var etag: String?
    var lastModified: String?
    /** 校验器只属于产生它的 URL，不能直接带到另一个 CDN 节点。 */
    var validatorUrl: String?
    var speedBytesPerSec: Long
}

internal class DownloadSourceFailure(
    val stage: String,
    cause: IOException,
) : IOException(cause.message, cause)

/** 负责文件传输和有限换源；任务调度、播放接口和 UI 状态仍由仓库管理。 */
internal class ResumableDownloader(
    private val callFactory: Call.Factory,
    private val onStateChanged: () -> Unit = {},
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    suspend fun download(
        target: ResumableDownloadTarget,
        refreshSource: suspend () -> DownloadSource? = { null },
        onFailure: (url: String, attempt: Int, failure: DownloadSourceFailure) -> Unit = { _, _, _ -> },
        onProgress: (downloaded: Long, total: Long, speed: Long, eta: Long?) -> Unit,
    ) = target.transferMutex.withLock {
        var refreshed = false
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            var candidates = target.source.orderedUrls()
            // 暂停后优先续传正在使用的节点，避免重复尝试本次下载已经淘汰的主地址。
            val currentUrl = target.validatorUrl
            if (target.tempFile.length() > 0 && currentUrl in candidates) {
                candidates = listOf(currentUrl!!) + candidates.filterNot { it == currentUrl }
            }
            var lastFailure: DownloadSourceFailure? = null
            for (url in candidates) {
                currentCoroutineContext().ensureActive()
                attempt++
                try {
                    downloadFromUrl(url, target, onProgress)
                    currentCoroutineContext().ensureActive()
                    return@withLock
                } catch (failure: DownloadSourceFailure) {
                    // cancel() 也会令 OkHttp 抛 IOException；必须先检查取消，不能因此换源。
                    currentCoroutineContext().ensureActive()
                    target.speedBytesPerSec = 0
                    // 下一节点的连接可能耗时，换源期间先清除上一段传输的估算。
                    onProgress(target.downloadedBytes, target.totalBytes, 0, null)
                    lastFailure = failure
                    onFailure(url, attempt, failure)
                }
            }
            if (!refreshed) {
                refreshed = true
                val source = refreshSource()
                currentCoroutineContext().ensureActive()
                if (source != null) {
                    target.source = source
                    onStateChanged()
                    continue
                }
            }
            throw lastFailure ?: IOException("No valid download URL")
        }
    }

    private suspend fun downloadFromUrl(
        url: String,
        target: ResumableDownloadTarget,
        onProgress: (Long, Long, Long, Long?) -> Unit,
    ) {
        var restarted = false
        while (true) {
            currentCoroutineContext().ensureActive()
            val existing = target.tempFile.length()
            target.downloadedBytes = existing
            val request = Request.Builder().url(url).header("Accept-Encoding", "identity").apply {
                if (existing > 0) {
                    header("Range", "bytes=$existing-")
                    if (target.validatorUrl == url) {
                        (target.etag ?: target.lastModified)?.let { header("If-Range", it) }
                    }
                }
            }.build()
            val call = callFactory.newCall(request)
            val retryFresh = coroutineScope {
                // execute/read 都是阻塞操作；独立子协程在暂停时主动关连接，退出前等待文件关闭。
                val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }
                try {
                    network("connect") { call.execute() }.use { response ->
                        ensureActive()
                        val range = ContentRange.parse(response.header("Content-Range"))
                        if (response.code == 416 && existing > 0) {
                            if (range?.total == existing) {
                                target.totalBytes = existing
                                target.speedBytesPerSec = 0
                                onProgress(existing, existing, 0, null)
                                return@use false
                            }
                            if (!restarted) {
                                resetFile(target)
                                return@use true
                            }
                        }
                        if (response.code != 200 && response.code != 206) {
                            val error = IOException("HTTP ${response.code}")
                            if (response.code in RETRYABLE_HTTP_CODES || response.code in 500..599) {
                                throw DownloadSourceFailure("http", error)
                            }
                            throw error
                        }
                        val length = response.body.contentLength()
                        if (response.code == 206) {
                            if (range?.start != existing || range.end == null ||
                                (length >= 0 && length != range.end - existing + 1)
                            ) {
                                throw DownloadSourceFailure("headers", IOException("Invalid Content-Range"))
                            }
                            if (existing > 0 && target.totalBytes > 0 && range.total != null &&
                                target.totalBytes != range.total && !restarted
                            ) {
                                resetFile(target)
                                return@use true
                            }
                        }
                        val append = existing > 0 && response.code == 206
                        // 服务器忽略 Range 返回 200 时，直接用本次完整响应覆盖，不能追加。
                        val total = if (response.code == 206) {
                            range?.total ?: if (length >= 0) existing + length else target.totalBytes
                        } else {
                            length.coerceAtLeast(0)
                        }
                        target.totalBytes = total
                        target.etag = response.header("ETag")?.takeUnless { it.startsWith("W/") }
                        target.lastModified = response.header("Last-Modified")
                        target.validatorUrl = url
                        FileOutputStream(target.tempFile, append).use { output ->
                            target.downloadedBytes = if (append) existing else 0
                            onStateChanged()
                            response.body.byteStream().use { input ->
                                target.speedBytesPerSec = 0
                                onProgress(target.downloadedBytes, total, 0, null)
                                val transferredBytes = AtomicLong(target.downloadedBytes)
                                val speedEstimator = DownloadSpeedEstimator(clockMillis(), target.downloadedBytes)
                                coroutineScope {
                                    // 独立于阻塞读取定时采样；没有新数据时也会衰减并清除过时的速度。
                                    val progressReporter = launch {
                                        while (true) {
                                            delay(PROGRESS_INTERVAL_MS)
                                            val downloaded = transferredBytes.get()
                                            val estimate = speedEstimator.update(clockMillis(), downloaded, total)
                                            target.speedBytesPerSec = estimate.speedBytesPerSec
                                            onProgress(downloaded, total, estimate.speedBytesPerSec, estimate.etaSeconds)
                                        }
                                    }
                                    try {
                                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                        while (true) {
                                            ensureActive()
                                            val read = network("body") { input.read(buffer) }
                                            if (read < 0) break
                                            // 磁盘写入失败必须原样终止，不能误认为 CDN 失败并反复重下。
                                            output.write(buffer, 0, read)
                                            target.downloadedBytes += read
                                            transferredBytes.set(target.downloadedBytes)
                                        }
                                    } finally {
                                        // 此作用域等待报告协程退出，再发布完成/换源状态，避免旧样本晚到。
                                        progressReporter.cancel()
                                    }
                                }
                            }
                        }
                        ensureActive()
                        if (total > 0 && target.downloadedBytes != total) {
                            throw DownloadSourceFailure("body", EOFException("unexpected end of stream"))
                        }
                        target.speedBytesPerSec = 0
                        onProgress(target.downloadedBytes, total, 0, null)
                        false
                    }
                } finally {
                    cancellation.cancel()
                }
            }
            if (!retryFresh) return
            restarted = true
        }
    }

    private fun resetFile(target: ResumableDownloadTarget) {
        FileOutputStream(target.tempFile).close()
        target.downloadedBytes = 0
        target.totalBytes = 0
        target.speedBytesPerSec = 0
        target.etag = null
        target.lastModified = null
        target.validatorUrl = null
        onStateChanged()
    }

    private inline fun <T> network(stage: String, block: () -> T): T = try {
        block()
    } catch (error: IOException) {
        throw DownloadSourceFailure(stage, error)
    }

    private data class ContentRange(val start: Long?, val end: Long?, val total: Long?) {
        companion object {
            private val pattern = Regex("bytes (?:(\\d+)-(\\d+)|\\*)/(\\d+|\\*)")

            fun parse(value: String?): ContentRange? {
                val match = value?.let(pattern::matchEntire) ?: return null
                val start = match.groupValues[1].toLongOrNull()
                val end = match.groupValues[2].toLongOrNull()
                val total = match.groupValues[3].toLongOrNull()
                if (start != null && (end == null || end < start || total != null && end >= total)) {
                    return null
                }
                return ContentRange(start, end, total)
            }
        }
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 250L
        private val RETRYABLE_HTTP_CODES = setOf(403, 404, 408, 410, 416, 429)
    }
}
