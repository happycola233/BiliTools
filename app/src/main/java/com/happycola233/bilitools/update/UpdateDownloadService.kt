package com.happycola233.bilitools.update

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import com.happycola233.bilitools.BiliToolsApp
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class UpdateDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient by lazy { OkHttpClient() }
    private lateinit var notificationManager: UpdateNotificationManager

    private var downloadJob: Job? = null
    private var activeCall: Call? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = UpdateNotificationManager(this)
        notificationManager.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val request = intent.toDownloadRequest() ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (downloadJob?.isActive == true) {
            return START_NOT_STICKY
        }

        isDownloading = true
        startForeground(
            UpdateNotificationManager.NOTIFICATION_ID_PROGRESS,
            notificationManager.buildProgressNotification(
                versionLabel = request.displayVersion,
                downloadedBytes = 0L,
                totalBytes = request.assetSizeBytes,
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        downloadJob = serviceScope.launch {
            try {
                val targetFile = prepareTargetFile(request.versionName)
                downloadApk(request, targetFile)
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.showReadyToInstall(
                    versionLabel = request.displayVersion,
                    apkPath = targetFile.absolutePath,
                )
                if ((application as? BiliToolsApp)?.isAppInForeground == true) {
                    UpdateInstallActivity.launch(
                        context = this@UpdateDownloadService,
                        apkPath = targetFile.absolutePath,
                    )
                }
            } catch (error: Throwable) {
                runCatching {
                    File(filesDir, UPDATE_DIRECTORY_NAME)
                        .listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
                        ?.forEach { it.delete() }
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.showFailure(
                    versionLabel = request.displayVersion,
                    errorMessage = error.message ?: "Unknown error",
                    releasePageUrl = request.releasePageUrl,
                )
            } finally {
                activeCall = null
                isDownloading = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeCall?.cancel()
        downloadJob?.cancel()
        serviceScope.cancel()
        isDownloading = false
        super.onDestroy()
    }

    private fun prepareTargetFile(versionName: String): File {
        val directory = File(filesDir, UPDATE_DIRECTORY_NAME).apply { mkdirs() }
        val safeVersion = versionName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "latest" }
        directory.listFiles()
            ?.filter { it.isFile && it.name != "app-release-$safeVersion.apk" }
            ?.forEach { staleFile ->
                runCatching { staleFile.delete() }
            }
        return File(directory, "app-release-$safeVersion.apk").apply {
            parentFile?.mkdirs()
            if (exists()) {
                delete()
            }
        }
    }

    private suspend fun downloadApk(
        request: UpdateDownloadRequest,
        targetFile: File,
    ) {
        val httpRequest = Request.Builder()
            .url(request.downloadUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "BiliTools-Android")
            .get()
            .build()

        activeCall = httpClient.newCall(httpRequest)
        activeCall!!.execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty download body")
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: request.assetSizeBytes

            body.byteStream().use { input ->
                targetFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    var lastPublishMs = 0L

                    while (true) {
                        serviceScope.coroutineContext.ensureActive()
                        val readCount = input.read(buffer)
                        if (readCount < 0) break
                        output.write(buffer, 0, readCount)
                        downloadedBytes += readCount

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastPublishMs >= PROGRESS_UPDATE_INTERVAL_MS) {
                            publishProgress(
                                versionLabel = request.displayVersion,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            )
                            lastPublishMs = now
                        }
                    }
                    output.flush()
                    publishProgress(
                        versionLabel = request.displayVersion,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                    )
                }
            }
        }
    }

    private fun publishProgress(
        versionLabel: String,
        downloadedBytes: Long,
        totalBytes: Long,
    ) {
        notificationManager.notifyProgress(
            notificationManager.buildProgressNotification(
                versionLabel = versionLabel,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
            ),
        )
    }

    private fun Intent.toDownloadRequest(): UpdateDownloadRequest? {
        val versionName = getStringExtra(EXTRA_VERSION_NAME)?.trim().orEmpty()
        val tagName = getStringExtra(EXTRA_TAG_NAME)?.trim().orEmpty()
        val releaseTitle = getStringExtra(EXTRA_RELEASE_TITLE)?.trim().orEmpty()
        val releasePageUrl = getStringExtra(EXTRA_RELEASE_URL)?.trim().orEmpty()
        val downloadUrl = getStringExtra(EXTRA_APK_DOWNLOAD_URL)?.trim().orEmpty()
        if (versionName.isBlank() || releasePageUrl.isBlank() || downloadUrl.isBlank()) {
            return null
        }
        return UpdateDownloadRequest(
            versionName = versionName,
            tagName = tagName,
            releaseTitle = releaseTitle,
            releasePageUrl = releasePageUrl,
            downloadUrl = downloadUrl,
            assetSizeBytes = getLongExtra(EXTRA_APK_SIZE_BYTES, -1L),
        )
    }

    private data class UpdateDownloadRequest(
        val versionName: String,
        val tagName: String,
        val releaseTitle: String,
        val releasePageUrl: String,
        val downloadUrl: String,
        val assetSizeBytes: Long,
    ) {
        val displayVersion: String
            get() = tagName.ifBlank {
                releaseTitle.ifBlank {
                    "v$versionName"
                }
            }
    }

    companion object {
        const val ACTION_START = "com.happycola233.bilitools.update.action.START"

        const val EXTRA_VERSION_NAME = "extra_version_name"
        const val EXTRA_TAG_NAME = "extra_tag_name"
        const val EXTRA_RELEASE_TITLE = "extra_release_title"
        const val EXTRA_RELEASE_URL = "extra_release_url"
        const val EXTRA_APK_DOWNLOAD_URL = "extra_apk_download_url"
        const val EXTRA_APK_SIZE_BYTES = "extra_apk_size_bytes"

        @Volatile
        var isDownloading: Boolean = false
            private set

        private const val UPDATE_DIRECTORY_NAME = "updates"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 400L
    }
}
