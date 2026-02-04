package com.happycola233.bilitools.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp4.Mp4TagReader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DownloadRepository(
    private val context: Context,
    private val cookieStore: CookieStore,
    private val settingsRepository: SettingsRepository,
) {
    private val resolver = context.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _groups = MutableStateFlow<List<DownloadGroup>>(emptyList())
    val groups: StateFlow<List<DownloadGroup>> = _groups.asStateFlow()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val cookie = cookieStore.cookieHeader()
                val request = chain.request().newBuilder()
                    .header("User-Agent", BiliHttpClient.USER_AGENT)
                    .header("Referer", "https://www.bilibili.com/")
                    .header("Origin", "https://www.bilibili.com")
                    .apply {
                        if (cookie.isNotBlank()) {
                            header("Cookie", cookie)
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                cookieStore.updateFromHeaders(response.headers)
                response
            }
            .build()
    }

    private val tempDir by lazy {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        File(base, "BiliTools/tmp").apply { mkdirs() }
    }
    private val storeFile = File(context.filesDir, "downloads_state.json")
    private val storeAdapter by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(DownloadStore::class.java)
    }
    private val persistLock = Any()
    private var persistJob: Job? = null
    @Volatile
    private var loaded = false
    private val loadLock = Any()

    private val downloadJobs = ConcurrentHashMap<Long, Job>()
    private val downloadStates = ConcurrentHashMap<Long, ResumableState>()
    private val mergeTasks = ConcurrentHashMap<Long, MergedDownload>()
    private val mergeJobs = ConcurrentHashMap<Long, Job>()
    private val mergeIds = AtomicLong(-1L)
    private val downloadIds = AtomicLong(0L)
    private val groupIds = AtomicLong(0L)
    private val extraTaskIds = AtomicLong(EXTRA_TASK_ID_START)
    private val groupInfo = ConcurrentHashMap<Long, GroupInfo>()
    private val groupTaskIds = ConcurrentHashMap<Long, MutableList<Long>>()
    private val tasks = ConcurrentHashMap<Long, DownloadItem>()
    private val lock = Any()

    fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            loadState()
            loaded = true
        }
    }

    fun refreshOutputAvailability() {
        scope.launch {
            ensureLoaded()
            val snapshot = synchronized(lock) { tasks.values.toList() }
            snapshot.forEach { item ->
                val missing = when (item.status) {
                    DownloadStatus.Success -> !isLocalUriAccessible(item.localUri)
                    else -> false
                }
                if (item.outputMissing != missing) {
                    updateTask(item.copy(outputMissing = missing))
                }
            }
        }
    }

    fun createGroup(
        title: String,
        subtitle: String?,
        bvid: String? = null,
        coverUrl: String? = null,
    ): Long {
        val id = groupIds.incrementAndGet()
        val folderName = buildGroupFolderName(
            title = title,
            bvid = bvid,
            existingNames = existingGroupFolderNames(),
        )
        val relativePath = buildGroupRelativePath(folderName)
        synchronized(lock) {
            groupInfo[id] = GroupInfo(
                title,
                subtitle,
                bvid,
                coverUrl,
                System.currentTimeMillis(),
                relativePath,
            )
            groupTaskIds[id] = mutableListOf()
        }
        updateGroups()
        schedulePersist()
        return id
    }

    fun groupRelativePath(groupId: Long): String {
        val info = groupInfo[groupId]
        val stored = info?.relativePath?.takeIf { it.isNotBlank() }
        if (stored != null) return stored
        val folderName = buildGroupFolderName(
            title = info?.title,
            bvid = info?.bvid,
            existingNames = existingGroupFolderNames(),
        )
        val relativePath = buildGroupRelativePath(folderName)
        if (info != null) {
            groupInfo[groupId] = info.copy(relativePath = relativePath)
            updateGroups()
            schedulePersist()
        }
        return relativePath
    }

    fun enqueue(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        url: String,
        mediaParams: com.happycola233.bilitools.data.model.DownloadMediaParams? = null,
        embeddedMetadata: DownloadEmbeddedMetadata? = null,
    ): DownloadItem {
        val id = downloadIds.incrementAndGet()
        val item = buildItem(
            id,
            groupId,
            type,
            taskTitle,
            fileName,
            url,
            mediaParams = mediaParams,
            embeddedMetadata = embeddedMetadata,
        )
        val state = ResumableState(
            id = id,
            url = url,
            fileName = fileName,
            tempFile = tempFileFor(id, fileName),
        )
        downloadStates[id] = state
        schedulePersist()
        addTask(item)
        startDownload(id)
        return item
    }

    fun enqueueDashMerge(
        groupId: Long,
        taskTitle: String,
        outputFileName: String,
        videoUrl: String,
        audioUrl: String,
        mediaParams: com.happycola233.bilitools.data.model.DownloadMediaParams? = null,
        embeddedMetadata: DownloadEmbeddedMetadata? = null,
    ): DownloadItem {
        val baseName = outputFileName.substringBeforeLast('.')
        val videoName = "$baseName-video.m4s"
        val audioName = "$baseName-audio.m4s"
        val requestTitle = groupInfo[groupId]?.title ?: taskTitle
        val mergeId = mergeIds.getAndDecrement()
        val task = MergedDownload(
            id = mergeId,
            title = requestTitle,
            outputName = outputFileName,
            video = ResumablePart(
                url = videoUrl,
                fileName = videoName,
                tempFile = tempFileFor(mergeId, videoName),
            ),
            audio = ResumablePart(
                url = audioUrl,
                fileName = audioName,
                tempFile = tempFileFor(mergeId, audioName),
            ),
        )
        mergeTasks[mergeId] = task
        schedulePersist()
        val item = buildItem(
            mergeId,
            groupId,
            DownloadTaskType.AudioVideo,
            taskTitle,
            outputFileName,
            videoUrl,
            mediaParams = mediaParams,
            embeddedMetadata = embeddedMetadata,
        )
        addTask(item)
        startMergedDownloads(task)
        return item
    }

    fun addExtraTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        status: DownloadStatus,
        errorMessage: String? = null,
        localUri: String? = null,
    ): DownloadItem {
        val id = extraTaskIds.getAndDecrement()
        val progress = if (status == DownloadStatus.Success) 100 else 0
        val item = buildItem(
            id,
            groupId,
            type,
            taskTitle,
            fileName,
            "",
            status = status,
            progress = progress,
            errorMessage = errorMessage,
            localUri = localUri,
        )
        addTask(item)
        return item
    }

    fun pause(id: Long) {
        val target = tasks[id] ?: return
        if (!isManagedTask(target)) return
        val mergeTask = mergeTasks[id]
        if (mergeTask != null) {
            pauseMerged(mergeTask)
            return
        }
        downloadJobs.remove(id)?.cancel()
        updateTask(
            target.copy(
                status = DownloadStatus.Paused,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = true,
            ),
        )
    }

    fun resume(id: Long) {
        val target = tasks[id] ?: return
        if (!isManagedTask(target)) return
        val mergeTask = mergeTasks[id]
        if (mergeTask != null) {
            resumeMerged(mergeTask)
            return
        }
        if (target.status != DownloadStatus.Paused || !target.userPaused) return
        updateTask(
            target.copy(
                status = DownloadStatus.Pending,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = false,
                errorMessage = null,
            ),
        )
        startDownload(id)
    }

    fun retry(id: Long) {
        val target = tasks[id] ?: return
        if (!isManagedTask(target)) return
        val mergeTask = mergeTasks[id]
        if (mergeTask != null) {
            retryMerged(mergeTask)
            return
        }
        if (target.status != DownloadStatus.Failed) return
        val tempFile = tempFileFor(id, target.fileName)
        val existing = if (tempFile.exists()) tempFile.length() else 0L
        val state = downloadStates[id] ?: ResumableState(
            id = id,
            url = target.url,
            fileName = target.fileName,
            tempFile = tempFile,
            downloadedBytes = existing,
            totalBytes = target.totalBytes,
        )
        state.downloadedBytes = existing
        if (state.totalBytes <= 0 && target.totalBytes > 0) {
            state.totalBytes = target.totalBytes
        }
        downloadStates[id] = state
        updateTask(
            target.copy(
                status = DownloadStatus.Pending,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = false,
                errorMessage = null,
            ),
        )
        startDownload(id)
    }

    fun cancel(id: Long) {
        val target = tasks[id] ?: return
        if (!isManagedTask(target)) return
        val mergeTask = mergeTasks[id]
        if (mergeTask != null) {
            cancelMerged(mergeTask)
            return
        }
        downloadJobs.remove(id)?.cancel()
        downloadStates.remove(id)?.tempFile?.delete()
        schedulePersist()
        updateTask(
            target.copy(
                status = DownloadStatus.Cancelled,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = false,
            ),
        )
    }

    fun pauseGroup(groupId: Long) {
        val ids = synchronized(lock) { groupTaskIds[groupId]?.toList().orEmpty() }
        ids.mapNotNull { tasks[it] }
            .filter { item ->
                isManagedTask(item) && when (item.status) {
                    DownloadStatus.Pending,
                    DownloadStatus.Running,
                    DownloadStatus.Merging,
                    DownloadStatus.Paused -> true
                    else -> false
                }
            }
            .forEach { pause(it.id) }
    }

    fun resumeGroup(groupId: Long) {
        val ids = synchronized(lock) { groupTaskIds[groupId]?.toList().orEmpty() }
        ids.mapNotNull { tasks[it] }
            .filter { item ->
                isManagedTask(item) &&
                    item.status == DownloadStatus.Paused &&
                    item.userPaused
            }
            .forEach { resume(it.id) }
    }

    fun deleteTask(id: Long, deleteFile: Boolean) {
        val item = tasks[id] ?: return
        deleteTasks(listOf(item), deleteFile)
    }

    fun deleteGroup(groupId: Long, deleteFile: Boolean) {
        val relativePath = groupInfo[groupId]?.relativePath
        val ids = synchronized(lock) { groupTaskIds[groupId]?.toList().orEmpty() }
        val items = ids.mapNotNull { tasks[it] }
        
        if (items.isNotEmpty()) {
            deleteTasks(items, deleteFile)
        } else {
             // Force remove empty group
             synchronized(lock) {
                 groupInfo.remove(groupId)
                 groupTaskIds.remove(groupId)
             }
             updateGroups()
             schedulePersist()
        }
        
        if (deleteFile && !relativePath.isNullOrBlank()) {
             deleteGroupFolder(relativePath)
        }
    }

    private fun deleteGroupFolder(relativePath: String) {
        runCatching {
             val dir = File(Environment.getExternalStorageDirectory(), relativePath)
             if (dir.exists()) {
                 dir.deleteRecursively()
             }
        }
    }

    private fun startDownload(id: Long) {
        val state = downloadStates[id] ?: return
        downloadJobs.remove(id)?.cancel()
        val job = scope.launch {
            try {
                runResumableDownload(id, state)
            } catch (err: CancellationException) {
                // user pause/cancel
            } catch (err: Exception) {
                handleDownloadFailure(id, err)
            }
        }
        downloadJobs[id] = job
    }

    private suspend fun runResumableDownload(id: Long, state: ResumableState) {
        val startItem = tasks[id] ?: return
        updateTask(
            startItem.copy(
                status = DownloadStatus.Pending,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = false,
                errorMessage = null,
            ),
        )
        downloadToTemp(state.url, state) { downloaded, total, speed, eta ->
            val current = tasks[id] ?: return@downloadToTemp
            if (current.userPaused) return@downloadToTemp
            val progress = calculateProgress(downloaded, total, current.progress)
            updateTask(
                current.copy(
                    status = DownloadStatus.Running,
                    progress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    speedBytesPerSec = speed,
                    etaSeconds = eta,
                    userPaused = false,
                    errorMessage = null,
                ),
            )
        }
        val finalizedTemp = prepareFinalTempFile(state.id, state.fileName, state.tempFile)
        tasks[id]?.let { item ->
            applyEmbeddedMetadataIfPossible(item, finalizedTemp)
        }
        val relativePath = groupRelativePath(startItem.groupId)
        val uri = saveToDownloads(finalizedTemp, state.fileName, relativePath)
        if (uri != null && finalizedTemp != state.tempFile) {
            runCatching { state.tempFile.delete() }
        } else if (uri == null && finalizedTemp != state.tempFile) {
            runCatching { finalizedTemp.delete() }
        }
        val current = tasks[id] ?: return
        if (uri != null) {
            updateTask(
                current.copy(
                    status = DownloadStatus.Success,
                    progress = 100,
                    downloadedBytes = state.downloadedBytes,
                    totalBytes = state.totalBytes,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    localUri = uri,
                    userPaused = false,
                    errorMessage = null,
                ),
            )
            downloadStates.remove(id)
            persistState()
        } else {
            updateTask(
                current.copy(
                    status = DownloadStatus.Failed,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = false,
                    errorMessage = "Save failed",
                ),
            )
            schedulePersist()
        }
        downloadJobs.remove(id)
    }

    private fun handleDownloadFailure(id: Long, err: Throwable) {
        val state = downloadStates[id]
        val current = tasks[id] ?: return
        val downloaded = state?.downloadedBytes ?: current.downloadedBytes
        val total = state?.totalBytes ?: current.totalBytes
        val progress = calculateProgress(downloaded, total, current.progress)
        updateTask(
            current.copy(
                status = DownloadStatus.Failed,
                progress = progress,
                downloadedBytes = downloaded,
                totalBytes = total,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = false,
                errorMessage = err.message ?: "Download failed",
            ),
        )
        downloadJobs.remove(id)
        schedulePersist()
    }

    private suspend fun downloadToTemp(
        url: String,
        target: ResumableTarget,
        onProgress: (downloaded: Long, total: Long, speed: Long, eta: Long?) -> Unit,
    ) {
        var existing = if (target.tempFile.exists()) target.tempFile.length() else 0L
        if (existing < 0) existing = 0
        target.downloadedBytes = existing
        var resumed = existing > 0
        var restarted = false
        while (true) {
            val request = buildRequest(url, resumed, existing, target)
            val response = httpClient.newCall(request).execute()
            try {
                if (resumed && response.code == 200) {
                    if (!restarted) {
                        target.tempFile.delete()
                        existing = 0
                        target.downloadedBytes = 0
                        resumed = false
                        restarted = true
                        continue
                    }
                }
                if (resumed && response.code == 416) {
                    return
                }
                if (!response.isSuccessful && response.code != 206) {
                    throw RuntimeException("HTTP ${response.code}")
                }
                val totalBytes = resolveTotalBytes(response, existing)
                if (totalBytes > 0) {
                    val previousTotal = target.totalBytes
                    target.totalBytes = totalBytes
                    if (previousTotal != totalBytes) {
                        schedulePersist()
                    }
                }
                val newEtag = response.header("ETag") ?: target.etag
                val newLastModified = response.header("Last-Modified") ?: target.lastModified
                val metaChanged = newEtag != target.etag || newLastModified != target.lastModified
                target.etag = newEtag
                target.lastModified = newLastModified
                if (metaChanged) {
                    schedulePersist()
                }
                val append = resumed && existing > 0 && response.code == 206
                val body = response.body ?: throw RuntimeException("Empty body")
                FileOutputStream(target.tempFile, append).use { output ->
                    body.byteStream().use { input ->
                        var lastUpdateTime = SystemClock.elapsedRealtime()
                        var lastUpdateBytes = target.downloadedBytes
                        onProgress(target.downloadedBytes, target.totalBytes, 0, null)
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            target.downloadedBytes += read
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL_MS) {
                                val deltaBytes = target.downloadedBytes - lastUpdateBytes
                                val deltaTime = now - lastUpdateTime
                                val speed = if (deltaBytes > 0 && deltaTime > 0) {
                                    (deltaBytes * 1000) / deltaTime
                                } else {
                                    0
                                }
                                target.speedBytesPerSec = speed
                                val eta = if (speed > 0 && target.totalBytes > 0 &&
                                    target.downloadedBytes < target.totalBytes) {
                                    (target.totalBytes - target.downloadedBytes) / speed
                                } else {
                                    null
                                }
                                onProgress(target.downloadedBytes, target.totalBytes, speed, eta)
                                lastUpdateTime = now
                                lastUpdateBytes = target.downloadedBytes
                            }
                        }
                        target.speedBytesPerSec = 0
                        onProgress(target.downloadedBytes, target.totalBytes, 0, null)
                    }
                }
                return
            } finally {
                response.close()
            }
        }
    }

    private fun buildRequest(
        url: String,
        resume: Boolean,
        existing: Long,
        target: ResumableTarget,
    ): Request {
        val builder = Request.Builder().url(url).get()
        if (resume && existing > 0) {
            builder.header("Range", "bytes=$existing-")
            val ifRange = target.etag ?: target.lastModified
            if (!ifRange.isNullOrBlank()) {
                builder.header("If-Range", ifRange)
            }
        }
        return builder.build()
    }

    private fun resolveTotalBytes(response: Response, existing: Long): Long {
        val contentRange = response.header("Content-Range")
        val total = parseContentRangeTotal(contentRange)
        if (total != null) {
            return total
        }
        val length = response.body?.contentLength() ?: -1L
        return if (length > 0) {
            if (response.code == 206) existing + length else length
        } else {
            0L
        }
    }

    private fun parseContentRangeTotal(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val slash = value.lastIndexOf('/')
        if (slash < 0 || slash == value.length - 1) return null
        val total = value.substring(slash + 1).trim()
        if (total == "*") return null
        return total.toLongOrNull()
    }

    private fun startMergedDownloads(task: MergedDownload) {
        if (task.userPaused || task.failed || task.completed) return
        if (task.video.completed && task.audio.completed) {
            startMerge(task)
            return
        }
        startPartDownload(task, task.video)
        startPartDownload(task, task.audio)
        updateMergedProgress(task, true, null)
    }

    private fun startPartDownload(task: MergedDownload, part: ResumablePart) {
        if (part.completed || part.job?.isActive == true) return
        part.job = scope.launch {
            try {
                downloadToTemp(part.url, part) { _, _, _, _ ->
                    updateMergedProgress(task, false, null)
                }
                part.completed = true
                part.speedBytesPerSec = 0
                schedulePersist()
                updateMergedProgress(task, true, null)
                if (task.video.completed && task.audio.completed) {
                    startMerge(task)
                }
            } catch (err: CancellationException) {
                // user pause/cancel
            } catch (err: Exception) {
                part.failed = true
                part.speedBytesPerSec = 0
                task.failed = true
                val other = if (part === task.video) task.audio else task.video
                other.job?.cancel()
                other.job = null
                other.speedBytesPerSec = 0
                schedulePersist()
                updateMergedProgress(task, true, err.message ?: "Download failed")
            }
        }
    }

    private fun pauseMerged(task: MergedDownload) {
        task.userPaused = true
        task.video.job?.cancel()
        task.audio.job?.cancel()
        task.video.job = null
        task.audio.job = null
        // Also cancel the actual merge process if it's running
        mergeJobs.remove(task.id)?.cancel()
        task.isMerging = false
        updateMergedProgress(task, true, null)
    }

    private fun resumeMerged(task: MergedDownload) {
        if (!task.userPaused || task.failed || task.completed) return
        task.userPaused = false
        updateMergedProgress(task, true, null)
        startMergedDownloads(task)
    }

    private fun retryMerged(task: MergedDownload) {
        if (task.completed) return
        task.failed = false
        task.userPaused = false
        task.video.failed = false
        task.audio.failed = false
        if (!task.video.tempFile.exists()) {
            task.video.completed = false
            task.video.downloadedBytes = 0
            task.video.totalBytes = 0
        }
        if (!task.audio.tempFile.exists()) {
            task.audio.completed = false
            task.audio.downloadedBytes = 0
            task.audio.totalBytes = 0
        }
        updateMergedProgress(task, true, null)
        startMergedDownloads(task)
    }

    private fun cancelMerged(task: MergedDownload) {
        task.userPaused = false
        task.video.job?.cancel()
        task.audio.job?.cancel()
        task.video.job = null
        task.audio.job = null
        mergeJobs.remove(task.id)?.cancel()
        task.video.tempFile.delete()
        task.audio.tempFile.delete()
        mergeTasks.remove(task.id)
        schedulePersist()
        val target = tasks[task.id] ?: return
        updateTask(
            target.copy(
                status = DownloadStatus.Cancelled,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = false,
            ),
        )
    }

    private fun updateMergedProgress(
        task: MergedDownload,
        force: Boolean,
        errorMessage: String?,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - task.lastProgressTimeMs < PROGRESS_UPDATE_INTERVAL_MS) return
        task.lastProgressTimeMs = now
        val total = task.video.totalBytes + task.audio.totalBytes
        val downloaded = task.video.downloadedBytes + task.audio.downloadedBytes
        val speed = task.video.speedBytesPerSec + task.audio.speedBytesPerSec
        val eta = if (speed > 0 && total > 0 && downloaded < total) {
            (total - downloaded) / speed
        } else {
            null
        }
        val current = tasks[task.id] ?: return
        val progress = calculateProgress(downloaded, total, current.progress)
        val status = when {
            task.userPaused -> DownloadStatus.Paused
            task.failed -> DownloadStatus.Failed
            task.isMerging -> DownloadStatus.Merging
            task.completed -> DownloadStatus.Success
            task.video.completed && task.audio.completed -> DownloadStatus.Merging
            task.video.job?.isActive == true || task.audio.job?.isActive == true ->
                DownloadStatus.Running
            else -> DownloadStatus.Pending
        }
        val finalProgress = if (status == DownloadStatus.Merging || status == DownloadStatus.Success) {
            100
        } else {
            progress
        }
        updateTask(
            current.copy(
                status = status,
                progress = finalProgress,
                downloadedBytes = downloaded,
                totalBytes = total,
                speedBytesPerSec = if (status == DownloadStatus.Running) speed else 0,
                etaSeconds = if (status == DownloadStatus.Running) eta else null,
                userPaused = task.userPaused,
                errorMessage = if (status == DownloadStatus.Failed) {
                    errorMessage ?: current.errorMessage
                } else {
                    null
                },
            ),
        )
    }

    private fun calculateProgress(downloaded: Long, total: Long, fallback: Int): Int {
        return if (total > 0) {
            ((downloaded * 100) / total).toInt().coerceIn(0, 100)
        } else {
            fallback
        }
    }

    fun clearAllGroups() {
        val downloadJobSnapshot = downloadJobs.values.toList()
        val mergeJobSnapshot = mergeJobs.values.toList()
        downloadJobs.clear()
        mergeJobs.clear()
        downloadJobSnapshot.forEach { it.cancel() }
        mergeJobSnapshot.forEach { it.cancel() }

        val downloadStateSnapshot = downloadStates.values.toList()
        downloadStates.clear()
        downloadStateSnapshot.forEach { it.tempFile.delete() }

        val mergeTaskSnapshot = mergeTasks.values.toList()
        mergeTasks.clear()
        mergeTaskSnapshot.forEach { task ->
            task.video.job?.cancel()
            task.audio.job?.cancel()
            task.video.job = null
            task.audio.job = null
            task.video.tempFile.delete()
            task.audio.tempFile.delete()
        }

        synchronized(lock) {
            groupInfo.clear()
            groupTaskIds.clear()
            tasks.clear()
        }
        updateGroups()
        schedulePersist()
    }

    fun clearCompletedGroups() {
        val completedGroups = mutableListOf<Long>()
        val taskIds = mutableListOf<Long>()
        synchronized(lock) {
            groupInfo.forEach { (groupId, _) ->
                val ids = groupTaskIds[groupId].orEmpty()
                if (ids.isEmpty()) return@forEach
                val groupTasks = ids.mapNotNull { tasks[it] }
                if (groupTasks.isNotEmpty() &&
                    groupTasks.all { it.status == DownloadStatus.Success }) {
                    completedGroups.add(groupId)
                    taskIds.addAll(ids)
                }
            }
            taskIds.forEach { id ->
                tasks.remove(id)
                downloadJobs.remove(id)?.cancel()
                downloadStates.remove(id)?.tempFile?.delete()
                mergeJobs.remove(id)?.cancel()
                mergeTasks.remove(id)?.let {
                    it.video.tempFile.delete()
                    it.audio.tempFile.delete()
                }
            }
            completedGroups.forEach { groupId ->
                groupInfo.remove(groupId)
                groupTaskIds.remove(groupId)
            }
        }
        updateGroups()
        schedulePersist()
    }

    private fun deleteTasks(items: List<DownloadItem>, deleteFile: Boolean) {
        if (items.isEmpty()) return
        val groupIds = items.map { it.groupId }.distinct()
        items.forEach { cleanupTaskResources(it, deleteFile) }
        synchronized(lock) {
            items.forEach { item ->
                tasks.remove(item.id)
                groupTaskIds[item.groupId]?.remove(item.id)
            }
            groupIds.forEach { groupId ->
                if (groupTaskIds[groupId].isNullOrEmpty()) {
                    groupTaskIds.remove(groupId)
                    groupInfo.remove(groupId)
                }
            }
        }
        updateGroups()
        schedulePersist()
    }

    private fun cleanupTaskResources(item: DownloadItem, deleteFile: Boolean) {
        downloadJobs.remove(item.id)?.cancel()
        downloadStates.remove(item.id)?.tempFile?.delete()
        mergeJobs.remove(item.id)?.cancel()
        mergeTasks.remove(item.id)?.let { merge ->
            merge.video.job?.cancel()
            merge.audio.job?.cancel()
            merge.video.job = null
            merge.audio.job = null
            merge.video.tempFile.delete()
            merge.audio.tempFile.delete()
        }
        if (deleteFile) {
            deleteLocalUri(item.localUri)
        }
    }

    private fun deleteLocalUri(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        runCatching {
            resolver.delete(Uri.parse(uriString), null, null)
        }
    }

    private fun isLocalUriAccessible(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return runCatching {
            resolver.openFileDescriptor(Uri.parse(uriString), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun addTask(item: DownloadItem) {
        synchronized(lock) {
            tasks[item.id] = item
            val list = groupTaskIds.getOrPut(item.groupId) { mutableListOf() }
            list.add(item.id)
        }
        updateGroups()
        schedulePersist()
    }

    private fun updateTask(item: DownloadItem) {
        val shouldPersist = synchronized(lock) {
            val previous = tasks[item.id]
            tasks[item.id] = item
            shouldPersistTaskChange(previous, item)
        }
        updateGroups()
        if (shouldPersist) {
            schedulePersist()
        }
    }

    private fun replaceTask(oldId: Long, newItem: DownloadItem) {
        val shouldPersist = synchronized(lock) {
            tasks.remove(oldId)
            tasks[newItem.id] = newItem
            val list = groupTaskIds[newItem.groupId] ?: mutableListOf()
            val idx = list.indexOf(oldId)
            if (idx >= 0) {
                list[idx] = newItem.id
            } else {
                list.add(newItem.id)
            }
            groupTaskIds[newItem.groupId] = list
            true
        }
        updateGroups()
        if (shouldPersist) {
            schedulePersist()
        }
    }

    private fun shouldPersistTaskChange(old: DownloadItem?, new: DownloadItem): Boolean {
        if (old == null) return true
        if (old.status != new.status) return true
        if (old.userPaused != new.userPaused) return true
        if (old.errorMessage != new.errorMessage) return true
        if (old.localUri != new.localUri) return true
        if (old.outputMissing != new.outputMissing) return true
        if (old.totalBytes != new.totalBytes && new.totalBytes > 0) return true
        return false
    }

    private fun updateGroups() {
        _groups.value = snapshotGroups()
    }

    private fun snapshotGroups(): List<DownloadGroup> {
        return synchronized(lock) {
            groupInfo.mapNotNull { (groupId, info) ->
                val ids = groupTaskIds[groupId].orEmpty()
                if (ids.isEmpty()) return@mapNotNull null
                val taskList = ids.mapNotNull { tasks[it] }
                if (taskList.isEmpty()) return@mapNotNull null
                DownloadGroup(
                    id = groupId,
                    title = info.title,
                    subtitle = info.subtitle,
                    bvid = info.bvid,
                    coverUrl = info.coverUrl,
                    createdAt = info.createdAt,
                    relativePath = info.relativePath,
                    tasks = taskList,
                )
            }
        }.sortedByDescending { it.createdAt }
    }

    private fun schedulePersist() {
        synchronized(persistLock) {
            if (persistJob?.isActive == true) return
            persistJob = scope.launch {
                delay(PERSIST_DELAY_MS)
                persistState()
            }
        }
    }

    private fun persistState() {
        val snapshot = buildStoreSnapshot()
        runCatching {
            storeFile.writeText(storeAdapter.toJson(snapshot), Charsets.UTF_8)
        }
    }

    private fun readStore(): DownloadStore? {
        if (!storeFile.exists()) return null
        val json = runCatching { storeFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val store = runCatching { storeAdapter.fromJson(json) }.getOrNull()
        if (store == null) {
            runCatching { storeFile.delete() }
        }
        return store
    }

    private fun buildStoreSnapshot(): DownloadStore {
        return DownloadStore(
            version = STORE_VERSION,
            groups = snapshotGroups(),
            resumableStates = downloadStates.values.map { state ->
                ResumableStateSnapshot(
                    id = state.id,
                    url = state.url,
                    fileName = state.fileName,
                    totalBytes = state.totalBytes,
                    etag = state.etag,
                    lastModified = state.lastModified,
                )
            },
            mergeStates = mergeTasks.values.map { task ->
                MergedTaskSnapshot(
                    id = task.id,
                    outputName = task.outputName,
                    video = ResumablePartSnapshot(
                        url = task.video.url,
                        fileName = task.video.fileName,
                        totalBytes = task.video.totalBytes,
                        etag = task.video.etag,
                        lastModified = task.video.lastModified,
                        completed = task.video.completed,
                    ),
                    audio = ResumablePartSnapshot(
                        url = task.audio.url,
                        fileName = task.audio.fileName,
                        totalBytes = task.audio.totalBytes,
                        etag = task.audio.etag,
                        lastModified = task.audio.lastModified,
                        completed = task.audio.completed,
                    ),
                )
            },
        )
    }

    private fun loadState() {
        val store = readStore() ?: return
        val resumableById = store.resumableStates.associateBy { it.id }
        val mergeById = store.mergeStates.associateBy { it.id }
        val restoredGroupInfo = mutableMapOf<Long, GroupInfo>()
        val restoredGroupTaskIds = mutableMapOf<Long, MutableList<Long>>()
        val restoredTasks = mutableMapOf<Long, DownloadItem>()
        val restoredStates = mutableMapOf<Long, ResumableState>()
        val restoredMerges = mutableMapOf<Long, MergedDownload>()
        val autoResumeIds = mutableListOf<Long>()
        val autoResumeMerges = mutableListOf<MergedDownload>()
        val autoMerge = mutableListOf<MergedDownload>()
        var maxGroupId = 0L
        var maxDownloadId = 0L
        var minMergeId: Long? = null
        var minExtraId: Long? = null

        val usedFolderNames = mutableSetOf<String>()
        for (group in store.groups) {
            maxGroupId = maxOf(maxGroupId, group.id)
            val storedPath = group.relativePath.takeIf { it.isNotBlank() }
            val storedFolder = storedPath?.let { extractFolderName(it) }
            val folderName = if (!storedFolder.isNullOrBlank()) {
                usedFolderNames.add(storedFolder)
                storedFolder
            } else {
                val newName = buildGroupFolderName(
                    title = group.title,
                    bvid = group.bvid,
                    existingNames = usedFolderNames,
                )
                usedFolderNames.add(newName)
                newName
            }
            val relativePath = storedPath ?: buildGroupRelativePath(folderName)
            restoredGroupInfo[group.id] = GroupInfo(
                group.title,
                group.subtitle,
                group.bvid,
                group.coverUrl,
                group.createdAt,
                relativePath,
            )
            val ids = mutableListOf<Long>()
            for (task in group.tasks) {
                ids.add(task.id)
                when {
                    task.id > 0 -> maxDownloadId = maxOf(maxDownloadId, task.id)
                    task.id <= EXTRA_TASK_ID_START -> {
                        minExtraId = minOf(minExtraId ?: task.id, task.id)
                    }
                    else -> minMergeId = minOf(minMergeId ?: task.id, task.id)
                }

                if (task.taskType == DownloadTaskType.AudioVideo) {
                    val restored = restoreMergedTask(task, mergeById[task.id])
                    restoredTasks[task.id] = restored.item
                    val mergeTask = restored.mergeTask
                    if (mergeTask != null) {
                        restoredMerges[task.id] = mergeTask
                        if (restored.autoMerge) {
                            autoMerge.add(mergeTask)
                        } else if (restored.autoResume) {
                            autoResumeMerges.add(mergeTask)
                        }
                    }
                    continue
                }

                if (isManagedTask(task)) {
                    val restored = restoreManagedTask(task, resumableById[task.id])
                    restoredTasks[task.id] = restored.item
                    if (restored.state != null) {
                        restoredStates[task.id] = restored.state
                    }
                    if (restored.autoResume) {
                        autoResumeIds.add(task.id)
                    }
                    continue
                }

                restoredTasks[task.id] = task.copy(
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                )
            }
            restoredGroupTaskIds[group.id] = ids
        }

        synchronized(lock) {
            groupInfo.clear()
            groupInfo.putAll(restoredGroupInfo)
            groupTaskIds.clear()
            groupTaskIds.putAll(restoredGroupTaskIds)
            tasks.clear()
            tasks.putAll(restoredTasks)
        }
        downloadStates.clear()
        downloadStates.putAll(restoredStates)
        mergeTasks.clear()
        mergeTasks.putAll(restoredMerges)

        groupIds.set(maxGroupId)
        downloadIds.set(maxDownloadId)
        mergeIds.set(if (minMergeId != null) minMergeId - 1L else -1L)
        extraTaskIds.set(if (minExtraId != null) minExtraId - 1L else EXTRA_TASK_ID_START)

        updateGroups()

        autoResumeIds.forEach { startDownload(it) }
        autoMerge.forEach { startMerge(it) }
        autoResumeMerges.forEach { startMergedDownloads(it) }
    }

    private fun restoreManagedTask(
        item: DownloadItem,
        snapshot: ResumableStateSnapshot?,
    ): ManagedRestoreResult {
        val tempFile = tempFileFor(item.id, item.fileName)
        val downloaded = if (tempFile.exists()) tempFile.length() else 0L
        val total = if (snapshot != null && snapshot.totalBytes > 0) {
            snapshot.totalBytes
        } else {
            item.totalBytes
        }
        var status = item.status
        var userPaused = item.userPaused
        var autoResume = false
        var errorMessage = item.errorMessage
        val shouldCheckExisting = status == DownloadStatus.Pending ||
            status == DownloadStatus.Running ||
            (status == DownloadStatus.Paused && !item.userPaused)
        if (shouldCheckExisting) {
            val existingUri = findExistingDownload(item.fileName, item.groupId)
            if (existingUri != null) {
                return ManagedRestoreResult(
                    item = item.copy(
                        status = DownloadStatus.Success,
                        progress = 100,
                        localUri = existingUri,
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                        userPaused = false,
                        errorMessage = null,
                    ),
                    state = null,
                    autoResume = false,
                )
            }
        }

        if (status == DownloadStatus.Pending) {
            status = DownloadStatus.Pending
            userPaused = false
            autoResume = true
            errorMessage = null
        } else if (status == DownloadStatus.Running ||
            (status == DownloadStatus.Paused && !item.userPaused)) {
            // Unsafe exit detected
            status = DownloadStatus.Failed
            userPaused = false
            autoResume = false
            errorMessage = context.getString(R.string.download_error_unsafe_exit)
            // Force delete potentially corrupted file
            tempFile.delete()
        }

        val progress = calculateProgress(downloaded, total, item.progress)
        val finalItem = item.copy(
            status = status,
            progress = progress,
            downloadedBytes = downloaded,
            totalBytes = total,
            speedBytesPerSec = 0,
            etaSeconds = null,
            userPaused = userPaused,
            errorMessage = errorMessage,
        )
        val state = if (finalItem.status != DownloadStatus.Success &&
            finalItem.status != DownloadStatus.Cancelled &&
            finalItem.status != DownloadStatus.Failed) {
            ResumableState(
                id = item.id,
                url = snapshot?.url ?: item.url,
                fileName = item.fileName,
                tempFile = tempFile,
                downloadedBytes = downloaded,
                totalBytes = total,
                etag = snapshot?.etag,
                lastModified = snapshot?.lastModified,
            )
        } else {
            null
        }
        return ManagedRestoreResult(finalItem, state, autoResume)
    }

    private fun restoreMergedTask(
        item: DownloadItem,
        snapshot: MergedTaskSnapshot?,
    ): MergedRestoreResult {
        val shouldCheckExisting = item.status == DownloadStatus.Pending ||
            item.status == DownloadStatus.Running ||
            item.status == DownloadStatus.Merging ||
            (item.status == DownloadStatus.Paused && !item.userPaused)
        if (shouldCheckExisting) {
            val existingUri = findExistingDownload(item.fileName, item.groupId)
            if (existingUri != null) {
                return MergedRestoreResult(
                    item = item.copy(
                        status = DownloadStatus.Success,
                        progress = 100,
                        localUri = existingUri,
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                        errorMessage = null,
                    ),
                    mergeTask = null,
                    autoResume = false,
                    autoMerge = false,
                )
            }
        }
        if (snapshot == null) {
            if (item.status == DownloadStatus.Success) {
                return MergedRestoreResult(
                    item = item.copy(
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                    ),
                    mergeTask = null,
                    autoResume = false,
                    autoMerge = false,
                )
            }
            val failedItem = item.copy(
                status = DownloadStatus.Failed,
                speedBytesPerSec = 0,
                etaSeconds = null,
                errorMessage = item.errorMessage ?: "Resume data missing",
            )
            return MergedRestoreResult(
                item = failedItem,
                mergeTask = null,
                autoResume = false,
                autoMerge = false,
            )
        }

        val videoTemp = tempFileFor(item.id, snapshot.video.fileName)
        val audioTemp = tempFileFor(item.id, snapshot.audio.fileName)
        val videoDownloaded = if (videoTemp.exists()) videoTemp.length() else 0L
        val audioDownloaded = if (audioTemp.exists()) audioTemp.length() else 0L
        val videoTotal = snapshot.video.totalBytes
        val audioTotal = snapshot.audio.totalBytes
        val total = if (videoTotal > 0 || audioTotal > 0) {
            videoTotal + audioTotal
        } else {
            item.totalBytes
        }
        val downloaded = videoDownloaded + audioDownloaded
        val videoCompleted =
            snapshot.video.completed || (videoTotal > 0 && videoDownloaded >= videoTotal)
        val audioCompleted =
            snapshot.audio.completed || (audioTotal > 0 && audioDownloaded >= audioTotal)

        if (item.status == DownloadStatus.Success ||
            item.status == DownloadStatus.Cancelled) {
            val finalItem = item.copy(
                progress = if (item.status == DownloadStatus.Success) 100 else item.progress,
                downloadedBytes = downloaded,
                totalBytes = total,
                speedBytesPerSec = 0,
                etaSeconds = null,
            )
            return MergedRestoreResult(
                item = finalItem,
                mergeTask = null,
                autoResume = false,
                autoMerge = false,
            )
        }

        var status = item.status
        var userPaused = item.userPaused
        var autoResume = false
        var autoMerge = false
        var errorMessage = item.errorMessage

        if (status == DownloadStatus.Pending) {
            status = DownloadStatus.Pending
            userPaused = false
            autoResume = true
            errorMessage = null
        } else if (status == DownloadStatus.Running ||
            (status == DownloadStatus.Paused && !item.userPaused)) {
            // Unsafe exit during download - potentially corrupted files
            status = DownloadStatus.Failed
            userPaused = false
            autoResume = false
            autoMerge = false
            errorMessage = context.getString(R.string.download_error_unsafe_exit)
            // Force delete potentially corrupted files
            videoTemp.delete()
            audioTemp.delete()
        } else if (status == DownloadStatus.Merging) {
            // Unsafe exit during merge - source files are likely safe
            status = DownloadStatus.Failed
            userPaused = false
            autoResume = false
            autoMerge = false
            errorMessage = context.getString(R.string.download_error_unsafe_exit)
            // Do NOT delete videoTemp and audioTemp, allow retry to re-merge
        }

        if (status != DownloadStatus.Failed && !userPaused && videoCompleted && audioCompleted) {
            autoMerge = true
            autoResume = false
        }
        val progress = calculateProgress(downloaded, total, item.progress)
        val finalItem = item.copy(
            status = status,
            progress = progress,
            downloadedBytes = downloaded,
            totalBytes = total,
            speedBytesPerSec = 0,
            etaSeconds = null,
            userPaused = userPaused,
            errorMessage = errorMessage,
        )
        val mergeTask = MergedDownload(
            id = item.id,
            title = item.title,
            outputName = item.fileName,
            video = ResumablePart(
                url = snapshot.video.url,
                fileName = snapshot.video.fileName,
                tempFile = videoTemp,
                downloadedBytes = videoDownloaded,
                totalBytes = videoTotal,
                etag = snapshot.video.etag,
                lastModified = snapshot.video.lastModified,
                speedBytesPerSec = 0,
                completed = videoCompleted,
                failed = item.status == DownloadStatus.Failed,
            ),
            audio = ResumablePart(
                url = snapshot.audio.url,
                fileName = snapshot.audio.fileName,
                tempFile = audioTemp,
                downloadedBytes = audioDownloaded,
                totalBytes = audioTotal,
                etag = snapshot.audio.etag,
                lastModified = snapshot.audio.lastModified,
                speedBytesPerSec = 0,
                completed = audioCompleted,
                failed = item.status == DownloadStatus.Failed,
            ),
            userPaused = userPaused,
            isMerging = false,
            completed = false,
            failed = item.status == DownloadStatus.Failed,
            outputUri = item.localUri,
        )
        return MergedRestoreResult(
            item = finalItem,
            mergeTask = mergeTask,
            autoResume = autoResume,
            autoMerge = autoMerge,
        )
    }

    private fun isManagedTask(item: DownloadItem): Boolean {
        return when (item.taskType) {
            DownloadTaskType.Video,
            DownloadTaskType.Audio,
            DownloadTaskType.AudioVideo -> true
            else -> false
        }
    }

    private fun startMerge(task: MergedDownload) {
        if (task.isMerging || mergeJobs[task.id]?.isActive == true) return
        task.isMerging = true
        updateMergedProgress(task, true, null)
        mergeJobs[task.id] = scope.launch {
            val result = runCatching { performMerge(task) }
            val uri = result.getOrNull()
            val target = tasks[task.id]
            if (result.isSuccess && uri != null) {
                task.completed = true
                task.outputUri = uri
                if (target != null) {
                    updateTask(
                        target.copy(
                            status = DownloadStatus.Success,
                            progress = 100,
                            localUri = uri,
                            speedBytesPerSec = 0,
                            etaSeconds = null,
                        ),
                    )
                    persistState()
                }
            } else {
                task.failed = true
                if (target != null) {
                    updateTask(
                        target.copy(
                            status = DownloadStatus.Failed,
                            speedBytesPerSec = 0,
                            etaSeconds = null,
                            errorMessage = result.exceptionOrNull()?.message ?: "Merge failed",
                        ),
                    )
                }
            }
            task.isMerging = false
            mergeJobs.remove(task.id)
        }
    }

    private fun performMerge(task: MergedDownload): String? {
        val groupId = tasks[task.id]?.groupId
        val relativePath = if (groupId != null) {
            groupRelativePath(groupId)
        } else {
            settingsRepository.downloadRootRelativePath()
        }
        val videoFile = task.video.tempFile
        val audioFile = task.audio.tempFile
        if (!videoFile.exists() || !audioFile.exists()) {
            return null
        }
        val outputTemp = tempFileFor(task.id, task.outputName)
        runCatching { outputTemp.delete() }
        val muxer = MediaMuxer(outputTemp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        val videoInput = FileInputStream(videoFile)
        val audioInput = FileInputStream(audioFile)
        var merged = false
        try {
            videoExtractor.setDataSource(videoInput.fd)
            val videoTrack = selectTrack(videoExtractor, "video/")
            audioExtractor.setDataSource(audioInput.fd)
            val audioTrack = selectTrack(audioExtractor, "audio/")
            val videoTrackIndex = if (videoTrack >= 0) {
                muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            } else {
                -1
            }
            val audioTrackIndex = if (audioTrack >= 0) {
                muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            } else {
                -1
            }
            if (videoTrackIndex < 0 && audioTrackIndex < 0) {
                return null
            }
            muxer.start()
            if (videoTrack >= 0) {
                videoExtractor.selectTrack(videoTrack)
                copySamples(
                    videoExtractor,
                    muxer,
                    videoTrackIndex,
                    videoExtractor.getTrackFormat(videoTrack),
                )
            }
            if (audioTrack >= 0) {
                audioExtractor.selectTrack(audioTrack)
                copySamples(
                    audioExtractor,
                    muxer,
                    audioTrackIndex,
                    audioExtractor.getTrackFormat(audioTrack),
                )
            }
            muxer.stop()
            merged = true
        } finally {
            runCatching { muxer.release() }
            videoExtractor.release()
            audioExtractor.release()
            videoInput.close()
            audioInput.close()
            videoFile.delete()
            audioFile.delete()
            if (!merged) {
                runCatching { outputTemp.delete() }
            }
        }

        if (!merged) return null

        tasks[task.id]?.let { item ->
            applyEmbeddedMetadataIfPossible(item, outputTemp)
        }

        val uri = saveToDownloads(outputTemp, task.outputName, relativePath)
        if (uri == null) {
            runCatching { outputTemp.delete() }
        }
        return uri
    }

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) {
                return i
            }
        }
        return -1
    }

    private fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        format: MediaFormat,
    ) {
        val maxSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(512 * 1024)
        } else {
            2 * 1024 * 1024
        }
        val buffer = java.nio.ByteBuffer.allocate(maxSize)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
                info.size = 0
                break
            }
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            buffer.position(0)
            buffer.limit(size)
            muxer.writeSampleData(trackIndex, buffer, info)
            extractor.advance()
        }
    }

    private fun applyEmbeddedMetadataIfPossible(item: DownloadItem, tempFile: File) {
        if (!settingsRepository.shouldAddMetadata()) return
        val metadata = item.embeddedMetadata ?: return
        if (!tempFile.exists()) return

        val ext = item.fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val supported = ext == "mp3" || ext == "m4a" || ext == "mp4"
        if (!supported) return

        runCatching {
            val audioFile = readTagWritableAudioFile(tempFile, ext)
            val tag = audioFile.tagOrCreateAndSetDefault

            setTagField(tag, FieldKey.TITLE, metadata.title)
            setTagField(tag, FieldKey.ALBUM, metadata.album)
            setTagField(tag, FieldKey.ARTIST, metadata.artist)
            setTagField(tag, FieldKey.ALBUM_ARTIST, metadata.albumArtist)
            setTagField(tag, FieldKey.COMMENT, metadata.comment)

            val year = metadata.year ?: metadata.date?.take(4)?.toIntOrNull()
            if (year != null) {
                setTagField(tag, FieldKey.YEAR, year.toString())
            }

            val genre = metadata.tags
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("; ")
                .takeIf { it.isNotBlank() }
            setTagField(tag, FieldKey.GENRE, genre)

            metadata.trackNumber?.let { setTagField(tag, FieldKey.TRACK, it.toString()) }
            metadata.trackTotal?.let { setTagField(tag, FieldKey.TRACK_TOTAL, it.toString()) }

            setOptionalTagField(tag, "DATE", metadata.date)
            setFirstAvailableTagField(
                tag,
                listOf(
                    "URL_OFFICIAL_RELEASE_SITE",
                    "URL_OFFICIAL_WEBSITE",
                    "WEBSITE_URL",
                    "URL",
                    "ORIGINAL_URL",
                ),
                metadata.originalUrl,
            )

            val coverUrl = metadata.coverUrl?.takeIf { it.isNotBlank() }
            val coverFile = coverUrl?.let { downloadCoverToTempFile(it) }
            if (coverFile != null) {
                runCatching {
                    val artwork = ArtworkFactory.createArtworkFromFile(coverFile)
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                }
                runCatching { coverFile.delete() }
            }

            audioFile.commit()
        }
    }

    private fun readTagWritableAudioFile(file: File, ext: String): AudioFile {
        val parsed = runCatching { AudioFileIO.read(file) }.getOrElse { readErr ->
            if (ext != "mp4") {
                throw readErr
            }
            // jaudiotagger rejects MP4 video in audio-header parsing; read tag directly.
            val tag = Mp4TagReader().read(file.toPath())
            AudioFile(file, null, tag)
        }
        parsed.setExt(ext)
        return parsed
    }

    private fun setTagField(tag: org.jaudiotagger.tag.Tag, key: FieldKey, value: String?) {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        runCatching { tag.setField(key, text) }
    }

    private fun setOptionalTagField(tag: org.jaudiotagger.tag.Tag, keyName: String, value: String?) {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        val key = runCatching { FieldKey.valueOf(keyName) }.getOrNull() ?: return
        runCatching { tag.setField(key, text) }
    }

    private fun setFirstAvailableTagField(
        tag: org.jaudiotagger.tag.Tag,
        keyNames: List<String>,
        value: String?,
    ) {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        for (name in keyNames) {
            val key = runCatching { FieldKey.valueOf(name) }.getOrNull() ?: continue
            if (runCatching { tag.setField(key, text) }.isSuccess) return
        }
    }

    private fun downloadCoverToTempFile(rawUrl: String): File? {
        val url = normalizeCoverUrl(rawUrl)
        if (url.isBlank()) return null
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                val file = File(tempDir, "cover-${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)
                file
            }
        }.getOrNull()
    }

    private fun normalizeCoverUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        val normalized = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
            else -> trimmed
        }
        if (normalized.contains("@")) return normalized
        val q = normalized.indexOf('?')
        val h = normalized.indexOf('#')
        val cut = when {
            q >= 0 && h >= 0 -> minOf(q, h)
            q >= 0 -> q
            h >= 0 -> h
            else -> -1
        }
        return if (cut >= 0) {
            val prefix = normalized.substring(0, cut)
            val suffix = normalized.substring(cut)
            "$prefix@.jpg$suffix"
        } else {
            "$normalized@.jpg"
        }
    }

    private fun saveToDownloads(
        tempFile: File,
        fileName: String,
        relativePath: String,
    ): String? {
        val output = createOutputFile(fileName, guessMimeType(fileName), relativePath) ?: return null
        val uri = output.uri
        return runCatching {
            output.pfd.use { pfd ->
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
            }
            finalizeOutputFile(uri)
            tempFile.delete()
            uri.toString()
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "mp4" -> "video/mp4"
            "m4s" -> "video/mp4"
            "m4a" -> "audio/mp4"
            "flv" -> "video/x-flv"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            else -> "application/octet-stream"
        }
    }

    private fun tempFileFor(id: Long, fileName: String): File {
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val legacy = File(tempDir, "$id-$safeName.part")
        val target = tempFileForNewSchema(id, fileName)
        return if (legacy.exists() && !target.exists()) legacy else target
    }

    private fun tempFileForNewSchema(id: Long, fileName: String): File {
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val ext = safeName.substringAfterLast('.', "")
        val base = if (ext.isNotBlank()) safeName.removeSuffix(".$ext") else safeName
        val newName = if (ext.isNotBlank()) {
            "$id-$base.part.$ext"
        } else {
            "$id-$safeName.part"
        }
        return File(tempDir, newName)
    }

    private fun prepareFinalTempFile(id: Long, fileName: String, tempFile: File): File {
        val expectedExt = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (expectedExt.isBlank()) return tempFile

        val tempName = tempFile.name.lowercase(Locale.US)
        if (tempName.endsWith(".$expectedExt")) return tempFile

        val target = runCatching { tempFileForNewSchema(id, fileName) }.getOrNull() ?: return tempFile
        if (target.absolutePath == tempFile.absolutePath) return tempFile

        runCatching { target.delete() }
        return runCatching {
            FileInputStream(tempFile).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target
        }.getOrElse { tempFile }
    }

    private fun buildGroupFolderName(
        title: String?,
        bvid: String?,
        existingNames: Set<String>,
    ): String {
        val safeTitle = title?.trim()?.takeIf { it.isNotBlank() }
        val safeBvid = bvid?.trim()?.takeIf { it.isNotBlank() }
        val base = when {
            !safeBvid.isNullOrBlank() && !safeTitle.isNullOrBlank() -> "$safeBvid-$safeTitle"
            !safeBvid.isNullOrBlank() -> safeBvid
            !safeTitle.isNullOrBlank() -> safeTitle
            else -> "BiliTools"
        }
        val safe = sanitizeFolderName(base).trim()
        val trimmed = trimFolderName(safe, 40)
        val candidate = if (trimmed.isBlank()) "BiliTools" else trimmed
        return ensureUniqueFolderName(candidate, existingNames)
    }

    private fun trimFolderName(name: String, maxLength: Int): String {
        if (name.length <= maxLength) return name
        val suffixMatch = Regex("\\s-\\sP\\d+$").find(name)
        if (suffixMatch != null) {
            val suffix = suffixMatch.value
            if (suffix.length < maxLength) {
                val prefixMax = maxLength - suffix.length
                val prefix = name.substring(0, prefixMax).trimEnd()
                val combined = (prefix + suffix).trim()
                if (combined.isNotBlank()) return combined
            }
        }
        return name.substring(0, maxLength)
    }

    private fun ensureUniqueFolderName(base: String, existingNames: Set<String>): String {
        if (!existingNames.contains(base)) return base
        var index = 1
        while (true) {
            val candidate = "$base($index)"
            if (!existingNames.contains(candidate)) return candidate
            index++
        }
    }

    private fun existingGroupFolderNames(): Set<String> {
        return synchronized(lock) {
            groupInfo.values
                .mapNotNull { extractFolderName(it.relativePath) }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }

    private fun extractFolderName(relativePath: String): String? {
        val trimmed = relativePath.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        return trimmed.substringAfterLast('/')
    }

    private fun buildGroupRelativePath(folderName: String): String {
        val root = settingsRepository.downloadRootRelativePath()
            .replace('\\', '/')
            .trim()
            .trim('/')
        val normalizedRoot = if (root.isBlank()) {
            SettingsRepository.DEFAULT_DOWNLOAD_ROOT
        } else {
            root
        }
        return "$normalizedRoot/$folderName"
    }

    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun createOutputFile(
        fileName: String,
        mimeType: String,
        relativePath: String,
    ): OutputTarget? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val pfd = resolver.openFileDescriptor(uri, "w") ?: return null
        return OutputTarget(uri, pfd)
    }

    private fun finalizeOutputFile(uri: Uri) {
        val update = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        resolver.update(uri, update, null, null)
    }

    private fun findExistingDownload(fileName: String, groupId: Long): String? {
        if (fileName.isBlank()) return null
        val relativePath = groupRelativePath(groupId).trim()
        if (relativePath.isBlank()) return null
        val normalizedPath = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(fileName)
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Downloads._ID)
            val pathIndex = cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)
            while (idIndex >= 0 && cursor.moveToNext()) {
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                if (path == normalizedPath || path == relativePath) {
                    val id = cursor.getLong(idIndex)
                    return Uri.withAppendedPath(collection, id.toString()).toString()
                }
            }
        }
        return null
    }

    private fun buildItem(
        id: Long,
        groupId: Long,
        taskType: DownloadTaskType,
        title: String,
        fileName: String,
        url: String,
        createdAt: Long = System.currentTimeMillis(),
        status: DownloadStatus = DownloadStatus.Pending,
        progress: Int = 0,
        downloadedBytes: Long = 0,
        totalBytes: Long = 0,
        speedBytesPerSec: Long = 0,
        etaSeconds: Long? = null,
        reason: Int? = null,
        localUri: String? = null,
        userPaused: Boolean = false,
        errorMessage: String? = null,
        mediaParams: com.happycola233.bilitools.data.model.DownloadMediaParams? = null,
        embeddedMetadata: DownloadEmbeddedMetadata? = null,
    ): DownloadItem {
        return DownloadItem(
            id = id,
            groupId = groupId,
            taskType = taskType,
            title = title,
            fileName = fileName,
            url = url,
            createdAt = createdAt,
            status = status,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speedBytesPerSec,
            etaSeconds = etaSeconds,
            reason = reason,
            localUri = localUri,
            userPaused = userPaused,
            errorMessage = errorMessage,
            mediaParams = mediaParams,
            embeddedMetadata = embeddedMetadata,
        )
    }

    private interface ResumableTarget {
        var downloadedBytes: Long
        var totalBytes: Long
        var etag: String?
        var lastModified: String?
        var speedBytesPerSec: Long
        val tempFile: File
    }

    private data class ResumableState(
        val id: Long,
        val url: String,
        val fileName: String,
        override val tempFile: File,
        override var downloadedBytes: Long = 0,
        override var totalBytes: Long = 0,
        override var etag: String? = null,
        override var lastModified: String? = null,
        override var speedBytesPerSec: Long = 0,
    ) : ResumableTarget

    private data class ResumablePart(
        val url: String,
        val fileName: String,
        override val tempFile: File,
        override var downloadedBytes: Long = 0,
        override var totalBytes: Long = 0,
        override var etag: String? = null,
        override var lastModified: String? = null,
        override var speedBytesPerSec: Long = 0,
        var job: Job? = null,
        var failed: Boolean = false,
        var completed: Boolean = false,
    ) : ResumableTarget

    private data class MergedDownload(
        val id: Long,
        val title: String,
        val outputName: String,
        val video: ResumablePart,
        val audio: ResumablePart,
        var userPaused: Boolean = false,
        var isMerging: Boolean = false,
        var completed: Boolean = false,
        var failed: Boolean = false,
        var outputUri: String? = null,
        var lastProgressTimeMs: Long = 0,
    )

    private data class GroupInfo(
        val title: String,
        val subtitle: String?,
        val bvid: String?,
        val coverUrl: String?,
        val createdAt: Long,
        val relativePath: String,
    )

    private data class OutputTarget(
        val uri: Uri,
        val pfd: ParcelFileDescriptor,
    )

    private data class ManagedRestoreResult(
        val item: DownloadItem,
        val state: ResumableState?,
        val autoResume: Boolean,
    )

    private data class MergedRestoreResult(
        val item: DownloadItem,
        val mergeTask: MergedDownload?,
        val autoResume: Boolean,
        val autoMerge: Boolean,
    )

    private data class DownloadStore(
        val version: Int = STORE_VERSION,
        val groups: List<DownloadGroup> = emptyList(),
        val resumableStates: List<ResumableStateSnapshot> = emptyList(),
        val mergeStates: List<MergedTaskSnapshot> = emptyList(),
    )

    private data class ResumableStateSnapshot(
        val id: Long,
        val url: String,
        val fileName: String,
        val totalBytes: Long,
        val etag: String?,
        val lastModified: String?,
    )

    private data class ResumablePartSnapshot(
        val url: String,
        val fileName: String,
        val totalBytes: Long,
        val etag: String?,
        val lastModified: String?,
        val completed: Boolean,
    )

    private data class MergedTaskSnapshot(
        val id: Long,
        val outputName: String,
        val video: ResumablePartSnapshot,
        val audio: ResumablePartSnapshot,
    )

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS = 300L
        private const val PERSIST_DELAY_MS = 1000L
        private const val STORE_VERSION = 1
        private const val EXTRA_TASK_ID_START = -1_000_000_000L
    }
}
