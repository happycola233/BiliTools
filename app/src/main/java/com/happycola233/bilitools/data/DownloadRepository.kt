package com.happycola233.bilitools.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AudioQualities
import com.happycola233.bilitools.core.AppLog as Log
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.MediaMergeEngine
import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.createHttpDiagnosticLoggingInterceptor
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadProgressRules
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoStream
import com.happycola233.bilitools.download.DownloadForegroundService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private val mediaRepository: MediaRepository,
) {
    private val resolver = context.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _groups = MutableStateFlow<List<DownloadGroup>>(emptyList())
    val groups: StateFlow<List<DownloadGroup>> = _groups.asStateFlow()
    private val _notificationState = MutableStateFlow(DownloadNotificationState())
    val notificationState: StateFlow<DownloadNotificationState> = _notificationState.asStateFlow()

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
                if (response.header(BILI_STATUS_CODE_HEADER)?.toIntOrNull() == LOGIN_REQUIRED_CODE) {
                    cookieStore.invalidateLogin()
                }
                response
            }
            .addInterceptor(
                createHttpDiagnosticLoggingInterceptor(
                    tag = TAG,
                    settingsRepository = settingsRepository,
                ),
            )
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
    private val extraJobs = ConcurrentHashMap<Long, Job>()
    private val downloadStates = ConcurrentHashMap<Long, ResumableState>()
    private val mergeTasks = ConcurrentHashMap<Long, MergedDownload>()
    private val mergeJobs = ConcurrentHashMap<Long, Job>()
    private val extraTaskSemaphore = Semaphore(EXTRA_TASK_PARALLELISM)
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
            var checkedSuccessCount = 0
            var detectedMissingCount = 0
            var changedCount = 0
            Log.d(
                TAG,
                "[output-check] refresh start, totalTasks=${snapshot.size}",
            )
            snapshot.forEach { item ->
                if (item.status != DownloadStatus.Success) {
                    return@forEach
                }
                checkedSuccessCount++
                var resolvedUri = item.localUri
                var accessible = isLocalUriAccessible(
                    item.localUri,
                    "refresh-task-${item.id}",
                )
                if (!accessible) {
                    Log.w(
                        TAG,
                        "[output-check] inaccessible success item, try relocate, taskId=${item.id}, groupId=${item.groupId}, file=${item.fileName}, localUri=${item.localUri}, previousMissing=${item.outputMissing}",
                    )
                    val recoveredUri = findAccessibleDownload(item.fileName, item.groupId)
                        ?: findAccessibleDownloadAnywhere(item.fileName)
                    if (recoveredUri != null) {
                        resolvedUri = recoveredUri
                        accessible = true
                        Log.i(
                            TAG,
                            "[output-check] relocated success item uri, taskId=${item.id}, file=${item.fileName}, oldUri=${item.localUri}, newUri=$recoveredUri",
                        )
                    } else {
                        detectedMissingCount++
                    }
                } else {
                    Log.d(
                        TAG,
                        "[output-check] accessible success item, taskId=${item.id}, groupId=${item.groupId}, file=${item.fileName}, localUri=${item.localUri}",
                    )
                }

                val missing = !accessible
                val shouldUpdate = item.outputMissing != missing || item.localUri != resolvedUri
                if (shouldUpdate) {
                    changedCount++
                    Log.i(
                        TAG,
                        "[output-check] item output state changed, taskId=${item.id}, file=${item.fileName}, oldMissing=${item.outputMissing}, newMissing=$missing, oldUri=${item.localUri}, newUri=$resolvedUri",
                    )
                    updateTask(item.copy(localUri = resolvedUri, outputMissing = missing))
                }
            }
            Log.d(
                TAG,
                "[output-check] refresh end, totalTasks=${snapshot.size}, checkedSuccess=$checkedSuccessCount, missingDetected=$detectedMissingCount, changed=$changedCount",
            )
        }
    }

    fun createGroup(
        title: String,
        subtitle: String?,
        bvid: String? = null,
        coverUrl: String? = null,
        relativePath: String? = null,
    ): Long {
        val id = groupIds.incrementAndGet()
        val resolvedRelativePath = relativePath?.takeIf { it.isNotBlank() }?.let(
            ::resolveRequestedGroupRelativePath,
        ) ?: run {
            val folderName = buildGroupFolderName(
                title = title,
                bvid = bvid,
                existingNames = existingGroupFolderNames(),
            )
            resolveRequestedGroupRelativePath(buildGroupRelativePath(folderName))
        }
        synchronized(lock) {
            groupInfo[id] = GroupInfo(
                title,
                subtitle,
                bvid,
                coverUrl,
                System.currentTimeMillis(),
                resolvedRelativePath,
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
        val relativePath = resolveRequestedGroupRelativePath(buildGroupRelativePath(folderName))
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

    fun updateExtraTask(
        id: Long,
        status: DownloadStatus,
        progress: Int,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
        errorMessage: String? = null,
        localUri: String? = null,
        statusDetail: String? = null,
        progressIndeterminate: Boolean = false,
    ): Boolean {
        var updated = false
        var shouldPersist = false
        synchronized(lock) {
            val target = tasks[id] ?: return@synchronized
            if (isManagedTask(target)) return@synchronized
            val next = DownloadProgressRules.normalizeTask(
                target.copy(
                    status = status,
                    progress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = null,
                    errorMessage = errorMessage,
                    localUri = localUri,
                    statusDetail = statusDetail,
                    progressIndeterminate = progressIndeterminate,
                ),
            )
            tasks[id] = next
            updated = true
            shouldPersist = shouldPersistTaskChange(target, next)
        }
        if (!updated) return false
        updateGroups()
        if (shouldPersist) {
            schedulePersist()
        }
        return true
    }

    fun updateExtraTaskMetadata(
        id: Long,
        taskTitle: String,
        fileName: String,
    ): Boolean {
        var updated = false
        var shouldPersist = false
        synchronized(lock) {
            val target = tasks[id] ?: return@synchronized
            if (isManagedTask(target)) return@synchronized
            val next = DownloadProgressRules.normalizeTask(
                target.copy(
                    title = taskTitle,
                    fileName = fileName,
                ),
            )
            tasks[id] = next
            updated = true
            shouldPersist = shouldPersistTaskChange(target, next)
        }
        if (!updated) return false
        updateGroups()
        if (shouldPersist) {
            schedulePersist()
        }
        return true
    }

    fun launchExtraTask(id: Long, block: suspend () -> Unit): Boolean {
        if (!isActiveExtraTask(id)) return false
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val currentJob = coroutineContext[Job]
            try {
                extraTaskSemaphore.withPermit {
                    if (!isActiveExtraTask(id)) return@withPermit
                    block()
                }
            } catch (err: CancellationException) {
                if (currentJob == null || extraJobs[id] == currentJob) {
                    cancelExtraTaskIfActive(id, err.message)
                }
                throw err
            } finally {
                currentJob?.let {
                    extraJobs.remove(id, it)
                }
            }
        }
        extraJobs.put(id, job)?.cancel()
        job.start()
        return true
    }

    private fun cancelExtraTaskIfActive(id: Long, errorMessage: String?) {
        val active = synchronized(lock) {
            val task = tasks[id] ?: return@synchronized false
            !isManagedTask(task) &&
                (task.status == DownloadStatus.Pending || task.status == DownloadStatus.Running)
        }
        if (!active) return
        updateExtraTask(
            id,
            DownloadStatus.Cancelled,
            progress = 0,
            errorMessage = errorMessage,
        )
    }

    private fun isActiveExtraTask(id: Long): Boolean {
        return synchronized(lock) {
            val task = tasks[id] ?: return@synchronized false
            !isManagedTask(task) &&
                (task.status == DownloadStatus.Pending || task.status == DownloadStatus.Running)
        }
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
            scope.launch {
                retryMergedWithRefresh(id)
            }
            return
        }
        if (target.status != DownloadStatus.Failed) return
        scope.launch {
            retryManagedWithRefresh(id)
        }
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

    fun pauseAllManaged() {
        val ids = synchronized(lock) {
            tasks.values
                .filter { item ->
                    isManagedTask(item) && when (item.status) {
                        DownloadStatus.Pending,
                        DownloadStatus.Running,
                        DownloadStatus.Merging -> true
                        else -> false
                    }
                }
                .map { it.id }
        }
        ids.forEach { pause(it) }
    }

    fun resumeAllManaged() {
        val ids = synchronized(lock) {
            tasks.values
                .filter { item ->
                    isManagedTask(item) &&
                        item.status == DownloadStatus.Paused &&
                        item.userPaused
                }
                .map { it.id }
        }
        ids.forEach { resume(it) }
    }

    fun summarizeTaskOutcomes(taskIds: Set<Long>): DownloadOutcomeSummary {
        if (taskIds.isEmpty()) return DownloadOutcomeSummary()
        val snapshot = synchronized(lock) {
            taskIds.mapNotNull { id -> tasks[id] }
        }
        var successCount = 0
        var failedCount = 0
        var cancelledCount = 0
        snapshot.forEach { item ->
            when (item.status) {
                DownloadStatus.Success -> successCount++
                DownloadStatus.Failed -> failedCount++
                DownloadStatus.Cancelled -> cancelledCount++
                else -> Unit
            }
        }
        return DownloadOutcomeSummary(
            successCount = successCount,
            failedCount = failedCount,
            cancelledCount = cancelledCount,
        )
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
             val normalizedRelativePath = relativePath.replace('\\', '/').trim().trim('/')
             if (normalizedRelativePath.isBlank()) return@runCatching
             val dir = File(Environment.getExternalStorageDirectory(), normalizedRelativePath)
             if (dir.exists() && !dir.deleteRecursively() && dir.exists()) {
                 return@runCatching
             }
             cleanupEmptyManagedParentFolders(normalizedRelativePath)
        }
    }

    private fun cleanupEmptyManagedParentFolders(relativePath: String) {
        val normalizedTarget = normalizeRelativePath(relativePath)
        val managedRoot = normalizeRelativePath(settingsRepository.downloadRootRelativePath())
        if (normalizedTarget.isBlank() || managedRoot.isBlank()) return
        if (!normalizedTarget.startsWith(managedRoot, ignoreCase = true)) return

        var current = File(Environment.getExternalStorageDirectory(), relativePath)
            .parentFile
        val managedRootDir = File(
            Environment.getExternalStorageDirectory(),
            managedRoot.trimEnd('/'),
        )

        while (current != null &&
            !current.path.equals(managedRootDir.path, ignoreCase = true)
        ) {
            val children = current.listFiles()
            if (children == null || children.isNotEmpty()) {
                return
            }
            if (!current.delete()) {
                return
            }
            current = current.parentFile
        }
    }

    private fun startDownload(id: Long) {
        val state = downloadStates[id] ?: return
        DownloadForegroundService.requestSync(context)
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
        Log.d(
            TAG,
            "[save-chain] start save managed download, taskId=$id, groupId=${startItem.groupId}, file=${state.fileName}, temp=${finalizedTemp.absolutePath}, tempExists=${finalizedTemp.exists()}, tempSize=${finalizedTemp.length()}, relativePath=$relativePath",
        )
        var uri = saveToDownloads(finalizedTemp, state.fileName, relativePath)
        if (uri == null) {
            Log.w(
                TAG,
                "[save-chain] saveToDownloads returned null, fallback search in-group first, taskId=$id, file=${state.fileName}, groupId=${startItem.groupId}",
            )
            uri = findAccessibleDownload(state.fileName, startItem.groupId)
                ?: findAccessibleDownloadAnywhere(state.fileName)
        }
        Log.d(
            TAG,
            "[save-chain] save+fallback resolved, taskId=$id, file=${state.fileName}, resolvedUri=$uri",
        )
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
            Log.i(
                TAG,
                "[save-chain] managed download marked success, taskId=$id, file=${state.fileName}, localUri=$uri",
            )
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
            Log.e(
                TAG,
                "[save-chain] managed download failed to resolve output uri after save and fallback, taskId=$id, file=${state.fileName}, groupId=${startItem.groupId}",
            )
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
                        resetTargetForFreshDownload(target, resetTotalBytes = false)
                        existing = 0
                        resumed = false
                        restarted = true
                        continue
                    }
                }
                if (resumed && response.code == 416) {
                    val remoteTotal = parseContentRangeTotal(response.header("Content-Range"))
                        ?: target.totalBytes
                    if (remoteTotal > 0L && existing == remoteTotal) {
                        target.totalBytes = remoteTotal
                        target.downloadedBytes = existing
                        target.speedBytesPerSec = 0
                        onProgress(existing, remoteTotal, 0, null)
                        return
                    }
                    if (!restarted) {
                        resetTargetForFreshDownload(target, resetTotalBytes = false)
                        if (remoteTotal > 0L) {
                            target.totalBytes = remoteTotal
                        }
                        existing = 0
                        resumed = false
                        restarted = true
                        continue
                    }
                    throw RuntimeException("HTTP ${response.code}")
                }
                if (!response.isSuccessful && response.code != 206) {
                    if (resumed && !restarted) {
                        resetTargetForFreshDownload(target, resetTotalBytes = false)
                        existing = 0
                        resumed = false
                        restarted = true
                        continue
                    }
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

    private suspend fun retryManagedWithRefresh(id: Long) {
        val target = tasks[id] ?: return
        if (!isManagedTask(target) || target.status != DownloadStatus.Failed) return
        val prepared = refreshManagedUrlIfPossible(target) ?: target
        val latest = tasks[id] ?: prepared
        val tempFile = tempFileFor(id, latest.fileName)
        val existing = if (tempFile.exists()) tempFile.length() else 0L
        val state = downloadStates[id] ?: ResumableState(
            id = id,
            url = latest.url,
            fileName = latest.fileName,
            tempFile = tempFile,
            downloadedBytes = existing,
            totalBytes = latest.totalBytes,
        )
        state.url = latest.url
        state.downloadedBytes = existing
        state.etag = null
        state.lastModified = null
        if (state.totalBytes <= 0 && latest.totalBytes > 0) {
            state.totalBytes = latest.totalBytes
        }
        downloadStates[id] = state
        updateTask(
            latest.copy(
                status = DownloadStatus.Pending,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = false,
                errorMessage = null,
            ),
        )
        startDownload(id)
    }

    private suspend fun retryMergedWithRefresh(id: Long) {
        val task = mergeTasks[id] ?: return
        refreshMergedUrlsIfPossible(id, task)
        val latest = mergeTasks[id] ?: task
        retryMerged(latest)
    }

    private fun resetTargetForFreshDownload(
        target: ResumableTarget,
        resetTotalBytes: Boolean,
    ) {
        runCatching { target.tempFile.delete() }
        target.downloadedBytes = 0
        target.speedBytesPerSec = 0
        if (resetTotalBytes) {
            target.totalBytes = 0
        }
    }

    private suspend fun refreshManagedUrlIfPossible(item: DownloadItem): DownloadItem? {
        val refreshedUrl = resolveManagedRetryUrl(item) ?: return null
        if (refreshedUrl == item.url) return item
        val updated = item.copy(url = refreshedUrl)
        updateTask(updated)
        downloadStates[item.id]?.let { state ->
            state.url = refreshedUrl
            state.etag = null
            state.lastModified = null
        }
        schedulePersist()
        Log.i(
            TAG,
            "[retry-refresh] managed url refreshed, taskId=${item.id}, oldUrl=${item.url}, newUrl=$refreshedUrl",
        )
        return updated
    }

    private suspend fun refreshMergedUrlsIfPossible(
        id: Long,
        task: MergedDownload,
    ): Boolean {
        val refreshed = resolveMergedRetryUrls(id) ?: return false
        var changed = false
        if (task.video.url != refreshed.videoUrl) {
            task.video.url = refreshed.videoUrl
            task.video.etag = null
            task.video.lastModified = null
            changed = true
        }
        if (task.audio.url != refreshed.audioUrl) {
            task.audio.url = refreshed.audioUrl
            task.audio.etag = null
            task.audio.lastModified = null
            changed = true
        }
        if (changed) {
            tasks[id]?.let { current ->
                updateTask(current.copy(url = refreshed.videoUrl))
            }
            schedulePersist()
            Log.i(
                TAG,
                "[retry-refresh] merged urls refreshed, taskId=$id, videoUrl=${refreshed.videoUrl}, audioUrl=${refreshed.audioUrl}",
            )
        }
        return changed
    }

    private suspend fun resolveManagedRetryUrl(item: DownloadItem): String? {
        val resolved = resolveRetrySource(item) ?: return null
        return when (item.taskType) {
            DownloadTaskType.Audio -> {
                val audio = selectAudioStreamForRetry(
                    streams = resolved.playUrlInfo.audio,
                    params = item.mediaParams,
                ) ?: return null
                audio.url
            }

            DownloadTaskType.Video,
            DownloadTaskType.AudioVideo,
            -> {
                val stream = selectVideoStreamForRetry(
                    streams = resolved.playUrlInfo.video,
                    params = item.mediaParams,
                    preferMergeCompatible = false,
                ) ?: return null
                stream.url
            }

            else -> null
        }
    }

    private suspend fun resolveMergedRetryUrls(id: Long): RefreshedMergeUrls? {
        val item = tasks[id] ?: return null
        val resolved = resolveRetrySource(item, formatOverride = StreamFormat.Dash) ?: return null
        val video = selectVideoStreamForRetry(
            streams = resolved.playUrlInfo.video,
            params = item.mediaParams,
            preferMergeCompatible = true,
        ) ?: return null
        val audio = selectAudioStreamForRetry(
            streams = resolved.playUrlInfo.audio,
            params = item.mediaParams,
        ) ?: return null
        return RefreshedMergeUrls(
            videoUrl = video.url,
            audioUrl = audio.url,
        )
    }

    private suspend fun resolveRetrySource(
        item: DownloadItem,
        formatOverride: StreamFormat? = null,
    ): RetrySourceContext? = withContext(Dispatchers.IO) {
        val sourceInput = item.embeddedMetadata?.originalUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: groupInfo[item.groupId]?.bvid?.trim()?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val parsed = runCatching { mediaRepository.parseInput(sourceInput, allowRaw = false) }
            .getOrElse { err ->
                Log.w(
                    TAG,
                    "[retry-refresh] parse input failed, taskId=${item.id}, source=$sourceInput",
                    err,
                )
                return@withContext null
            }
        val type = parsed.type ?: return@withContext null
        val info = runCatching { mediaRepository.getMediaInfo(parsed.id, type) }
            .getOrElse { err ->
                Log.w(
                    TAG,
                    "[retry-refresh] get media info failed, taskId=${item.id}, source=$sourceInput, type=$type",
                    err,
                )
                return@withContext null
            }
        val sourceItem = findRetrySourceItem(info, item) ?: return@withContext null
        val format = formatOverride ?: inferRetryStreamFormat(item)
        val playUrlInfo = runCatching { mediaRepository.getPlayUrlInfo(sourceItem, type, format) }
            .getOrElse { err ->
                Log.w(
                    TAG,
                    "[retry-refresh] get playurl failed, taskId=${item.id}, source=${sourceItem.url}, type=$type, format=$format",
                    err,
                )
                return@withContext null
            }
        RetrySourceContext(
            mediaInfo = info,
            item = sourceItem,
            mediaType = type,
            playUrlInfo = playUrlInfo,
        )
    }

    private fun findRetrySourceItem(
        info: MediaInfo,
        item: DownloadItem,
    ): MediaItem? {
        val candidates = info.list
        if (candidates.isEmpty()) return null

        val meta = item.embeddedMetadata
        val trackNumber = meta?.trackNumber
        if (trackNumber != null) {
            candidates.firstOrNull { it.index + 1 == trackNumber }?.let { return it }
        }

        val metaTitle = meta?.title?.trim().orEmpty()
        if (metaTitle.isNotBlank()) {
            candidates.firstOrNull { candidate ->
                candidate.title.trim() == metaTitle
            }?.let { return it }
        }

        val groupBvid = groupInfo[item.groupId]?.bvid?.trim().orEmpty()
        if (groupBvid.isNotBlank()) {
            candidates.firstOrNull { candidate -> candidate.bvid == groupBvid }?.let { return it }
        }

        return candidates.firstOrNull { it.isTarget } ?: candidates.firstOrNull()
    }

    private fun inferRetryStreamFormat(item: DownloadItem): StreamFormat {
        if (item.taskType == DownloadTaskType.Audio) {
            return StreamFormat.Dash
        }
        return when (item.fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
            "m4s" -> StreamFormat.Dash
            "flv" -> StreamFormat.Flv
            else -> StreamFormat.Mp4
        }
    }

    private fun selectVideoStreamForRetry(
        streams: List<VideoStream>,
        params: DownloadMediaParams?,
        preferMergeCompatible: Boolean,
    ): VideoStream? {
        if (streams.isEmpty()) return null
        var candidates = streams
        val targetResolution = params?.resolution?.trim().orEmpty()
        if (targetResolution.isNotBlank()) {
            val matched = streams.filter { mapResolutionLabel(it) == targetResolution }
            if (matched.isNotEmpty()) {
                candidates = matched
            }
        }

        val targetCodec = parseCodecLabel(params?.codec)
        if (targetCodec != null) {
            val matched = candidates.filter { (it.codec ?: VideoCodec.Avc) == targetCodec }
            if (matched.isNotEmpty()) {
                candidates = matched
            }
        }

        var selected = candidates.maxByOrNull { it.bandwidth ?: 0L }
            ?: streams.maxByOrNull { it.bandwidth ?: 0L }
            ?: streams.first()
        if (preferMergeCompatible && selected.codec == VideoCodec.Av1) {
            val sameResolution = streams.filter { it.id == selected.id }
            selected = sameResolution.firstOrNull { it.codec == VideoCodec.Avc }
                ?: sameResolution.firstOrNull { it.codec == VideoCodec.Hevc }
                ?: selected
        }
        return selected
    }

    private fun selectAudioStreamForRetry(
        streams: List<AudioStream>,
        params: DownloadMediaParams?,
    ): AudioStream? {
        if (streams.isEmpty()) return null
        val targetAudio = params?.audioBitrate?.trim().orEmpty()
        if (targetAudio.isNotBlank()) {
            val matched = streams.filter { mapAudioLabel(it.id) == targetAudio }
            if (matched.isNotEmpty()) {
                return matched.maxByOrNull { it.bandwidth ?: 0L } ?: matched.first()
            }
        }
        val highestId = AudioQualities.highest(streams.map { it.id })
        if (highestId != null) {
            val highest = streams.filter { it.id == highestId }
            if (highest.isNotEmpty()) {
                return highest.maxByOrNull { it.bandwidth ?: 0L } ?: highest.first()
            }
        }
        return streams.maxByOrNull { it.bandwidth ?: 0L } ?: streams.first()
    }

    private fun parseCodecLabel(label: String?): VideoCodec? {
        val normalized = label?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return when (normalized) {
            context.getString(R.string.parse_codec_avc) -> VideoCodec.Avc
            context.getString(R.string.parse_codec_hevc) -> VideoCodec.Hevc
            context.getString(R.string.parse_codec_av1) -> VideoCodec.Av1
            else -> null
        }
    }

    private fun mapResolutionLabel(stream: VideoStream): String {
        return mapResolutionLabel(stream.id, stream.height)
    }

    private fun mapResolutionLabel(id: Int, height: Int?): String {
        return when (id) {
            127 -> context.getString(R.string.parse_resolution_8k)
            126 -> context.getString(R.string.parse_resolution_dolby)
            125 -> context.getString(R.string.parse_resolution_hdr)
            120 -> context.getString(R.string.parse_resolution_4k)
            116 -> context.getString(R.string.parse_resolution_1080_60)
            112 -> context.getString(R.string.parse_resolution_1080_high)
            80 -> context.getString(R.string.parse_resolution_1080)
            64 -> context.getString(R.string.parse_resolution_720)
            32 -> context.getString(R.string.parse_resolution_480)
            16 -> context.getString(R.string.parse_resolution_360)
            6 -> context.getString(R.string.parse_resolution_240)
            else -> {
                val resolvedHeight = height ?: 0
                when {
                    resolvedHeight >= 4320 -> context.getString(R.string.parse_resolution_8k)
                    resolvedHeight >= 2160 -> context.getString(R.string.parse_resolution_4k)
                    resolvedHeight >= 1080 -> context.getString(R.string.parse_resolution_1080)
                    resolvedHeight >= 720 -> context.getString(R.string.parse_resolution_720)
                    resolvedHeight >= 480 -> context.getString(R.string.parse_resolution_480)
                    resolvedHeight >= 360 -> context.getString(R.string.parse_resolution_360)
                    else -> context.getString(R.string.parse_resolution_other)
                }
            }
        }
    }

    private fun mapAudioLabel(id: Int): String {
        return context.getString(AudioQualities.labelRes(id))
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
        DownloadForegroundService.requestSync(context)
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
        task.video.speedBytesPerSec = 0
        task.audio.speedBytesPerSec = 0
        mergeJobs[task.id]?.cancel()
        if (mergeJobs[task.id]?.isActive != true) {
            task.isMerging = false
        }
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
        task.isMerging = false
        task.video.failed = false
        task.audio.failed = false
        prepareMergedPartForRetry(task.video, "video/")
        prepareMergedPartForRetry(task.audio, "audio/")
        updateMergedProgress(task, true, null)
        startMergedDownloads(task)
    }

    private fun prepareMergedPartForRetry(part: ResumablePart, mimePrefix: String) {
        part.job = null
        val file = part.tempFile
        if (!file.exists()) {
            resetMergedPartForFreshDownload(part)
            return
        }

        val currentSize = file.length().coerceAtLeast(0L)
        if (part.completed) {
            if (!isMediaPartUsable(file, mimePrefix)) {
                runCatching { file.delete() }
                resetMergedPartForFreshDownload(part)
                return
            }
            part.downloadedBytes = currentSize
            if (part.totalBytes <= 0L) {
                part.totalBytes = currentSize
            }
            return
        }

        part.downloadedBytes = currentSize
        if (currentSize <= 0L) {
            resetMergedPartForFreshDownload(part)
        }
    }

    private fun resetMergedPartForFreshDownload(part: ResumablePart) {
        part.completed = false
        part.failed = false
        part.downloadedBytes = 0
        part.speedBytesPerSec = 0
    }

    private fun cancelMerged(task: MergedDownload) {
        task.userPaused = false
        task.video.job?.cancel()
        task.audio.job?.cancel()
        task.video.job = null
        task.audio.job = null
        task.video.speedBytesPerSec = 0
        task.audio.speedBytesPerSec = 0
        mergeJobs[task.id]?.cancel()
        task.isMerging = false
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
        val extraJobSnapshot = extraJobs.values.toList()
        downloadJobs.clear()
        mergeJobs.clear()
        extraJobs.clear()
        downloadJobSnapshot.forEach { it.cancel() }
        mergeJobSnapshot.forEach { it.cancel() }
        extraJobSnapshot.forEach { it.cancel() }

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
                extraJobs.remove(id)?.cancel()
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
        extraJobs.remove(item.id)?.cancel()
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
        return isLocalUriAccessible(uriString, "default")
    }

    private fun isLocalUriAccessible(uriString: String?, traceSource: String): Boolean {
        if (uriString.isNullOrBlank()) {
            Log.d(
                TAG,
                "[output-check][$traceSource] uri empty, accessible=false",
            )
            return false
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrElse { err ->
            Log.w(
                TAG,
                "[output-check][$traceSource] uri parse failed, uri=$uriString",
                err,
            )
            return false
        }
        val result = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                Log.d(
                    TAG,
                    "[output-check][$traceSource] openFileDescriptor success, uri=$uriString, statSize=${pfd.statSize}",
                )
                true
            } ?: false
        }
        result.exceptionOrNull()?.let { err ->
            Log.w(
                TAG,
                "[output-check][$traceSource] openFileDescriptor failed, uri=$uriString",
                err,
            )
        }
        val accessible = result.getOrDefault(false)
        if (!accessible) {
            Log.d(
                TAG,
                "[output-check][$traceSource] uri not accessible, uri=$uriString",
            )
        }
        return accessible
    }

    private fun addTask(item: DownloadItem) {
        val normalizedItem = DownloadProgressRules.normalizeTask(item)
        synchronized(lock) {
            tasks[normalizedItem.id] = normalizedItem
            val list = groupTaskIds.getOrPut(normalizedItem.groupId) { mutableListOf() }
            list.add(normalizedItem.id)
        }
        updateGroups()
        schedulePersist()
    }

    private fun updateTask(item: DownloadItem) {
        val normalizedItem = DownloadProgressRules.normalizeTask(item)
        val shouldPersist = synchronized(lock) {
            val previous = tasks[normalizedItem.id]
            tasks[normalizedItem.id] = normalizedItem
            shouldPersistTaskChange(previous, normalizedItem)
        }
        updateGroups()
        if (shouldPersist) {
            schedulePersist()
        }
    }

    private fun replaceTask(oldId: Long, newItem: DownloadItem) {
        val normalizedItem = DownloadProgressRules.normalizeTask(newItem)
        val shouldPersist = synchronized(lock) {
            tasks.remove(oldId)
            tasks[normalizedItem.id] = normalizedItem
            val list = groupTaskIds[normalizedItem.groupId] ?: mutableListOf()
            val idx = list.indexOf(oldId)
            if (idx >= 0) {
                list[idx] = normalizedItem.id
            } else {
                list.add(normalizedItem.id)
            }
            groupTaskIds[normalizedItem.groupId] = list
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
        if (old.title != new.title) return true
        if (old.fileName != new.fileName) return true
        if (old.userPaused != new.userPaused) return true
        if (old.errorMessage != new.errorMessage) return true
        if (old.url != new.url) return true
        if (old.localUri != new.localUri) return true
        if (old.outputMissing != new.outputMissing) return true
        if (old.progressIndeterminate != new.progressIndeterminate) return true
        if (old.statusDetail != new.statusDetail) return true
        if (old.totalBytes != new.totalBytes && new.totalBytes > 0) return true
        return false
    }

    private fun updateGroups() {
        _groups.value = snapshotGroups()
        _notificationState.value = snapshotNotificationState()
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

    private fun snapshotNotificationState(): DownloadNotificationState {
        return synchronized(lock) {
            val managedTasks = tasks.values.filter { item -> isManagedTask(item) }
            if (managedTasks.isEmpty()) {
                return@synchronized DownloadNotificationState()
            }

            val activeStatuses = setOf(
                DownloadStatus.Pending,
                DownloadStatus.Running,
                DownloadStatus.Paused,
                DownloadStatus.Merging,
            )
            val foregroundStatuses = setOf(
                DownloadStatus.Pending,
                DownloadStatus.Running,
                DownloadStatus.Merging,
            )

            val activeTasks = managedTasks.filter { item -> item.status in activeStatuses }
            val foregroundTasks = activeTasks.filter { item -> item.status in foregroundStatuses }

            val speedBytesPerSec = activeTasks.sumOf { item ->
                if (item.status == DownloadStatus.Running) item.speedBytesPerSec else 0L
            }

            val sizeTasks = activeTasks.filter { item -> item.totalBytes > 0 }
            val totalBytes = sizeTasks.sumOf { item -> item.totalBytes }
            val downloadedBytes = sizeTasks.sumOf { item ->
                item.downloadedBytes.coerceAtMost(item.totalBytes)
            }

            val rawProgress = if (totalBytes > 0L) {
                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
            } else if (activeTasks.isNotEmpty()) {
                activeTasks.map { item ->
                    DownloadProgressRules.normalizeTaskProgress(item.status, item.progress)
                }.average().toInt()
            } else {
                0
            }
            val progress = if (activeTasks.isNotEmpty()) {
                DownloadProgressRules.normalizeAggregateProgress(
                    progress = rawProgress,
                    allTasksSucceeded = activeTasks.all { item -> item.status == DownloadStatus.Success },
                )
            } else {
                0
            }

            val etaSeconds = if (speedBytesPerSec > 0L && totalBytes > downloadedBytes) {
                (totalBytes - downloadedBytes) / speedBytesPerSec
            } else {
                null
            }

            val primaryTask = activeTasks
                .sortedWith(
                    compareBy<DownloadItem>(
                        { item ->
                            when (item.status) {
                                DownloadStatus.Running -> 0
                                DownloadStatus.Merging -> 1
                                DownloadStatus.Pending -> 2
                                DownloadStatus.Paused -> 3
                                else -> 4
                            }
                        },
                        { item -> -item.createdAt },
                    ),
                )
                .firstOrNull()

            DownloadNotificationState(
                activeTaskIds = activeTasks.map { item -> item.id }.toSet(),
                inProgressCount = foregroundTasks.size,
                pausedCount = activeTasks.count { item -> item.status == DownloadStatus.Paused },
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                speedBytesPerSec = speedBytesPerSec,
                etaSeconds = etaSeconds,
                primaryTitle = primaryTask?.title?.ifBlank { primaryTask.fileName },
                primaryStatus = primaryTask?.status,
                hasForegroundWork = activeTasks.isNotEmpty(),
            )
        }
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
        val completedTempFiles = mutableListOf<File>()
        val autoResumeIds = mutableListOf<Long>()
        val autoResumeMerges = mutableListOf<MergedDownload>()
        val autoMerge = mutableListOf<MergedDownload>()
        var maxGroupId = 0L
        var maxDownloadId = 0L
        var minMergeId: Long? = null
        var minExtraId: Long? = null
        var restoredExtraStatusChanged = false

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
                    completedTempFiles += restored.cleanupTempFiles
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
                    completedTempFiles += restored.cleanupTempFiles
                    if (restored.state != null) {
                        restoredStates[task.id] = restored.state
                    }
                    if (restored.autoResume) {
                        autoResumeIds.add(task.id)
                    }
                    continue
                }

                val restoredExtraTask = if (task.status == DownloadStatus.Pending ||
                    task.status == DownloadStatus.Running
                ) {
                    restoredExtraStatusChanged = true
                    task.copy(
                        status = DownloadStatus.Cancelled,
                        progress = 0,
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                        statusDetail = null,
                        progressIndeterminate = false,
                    )
                } else {
                    task.copy(
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                    )
                }
                restoredTasks[task.id] = restoredExtraTask
            }
            restoredGroupTaskIds[group.id] = ids
        }

        synchronized(lock) {
            groupInfo.clear()
            groupInfo.putAll(restoredGroupInfo)
            groupTaskIds.clear()
            groupTaskIds.putAll(restoredGroupTaskIds)
            tasks.clear()
            tasks.putAll(restoredTasks.mapValues { (_, item) ->
                DownloadProgressRules.normalizeTask(item)
            })
        }
        downloadStates.clear()
        downloadStates.putAll(restoredStates)
        mergeTasks.clear()
        mergeTasks.putAll(restoredMerges)

        groupIds.set(maxGroupId)
        downloadIds.set(maxDownloadId)
        mergeIds.set(if (minMergeId != null) minMergeId - 1L else -1L)
        extraTaskIds.set(if (minExtraId != null) minExtraId - 1L else EXTRA_TASK_ID_START)

        cleanupCompletedTempFiles(completedTempFiles)
        updateGroups()
        if (restoredExtraStatusChanged) {
            schedulePersist()
        }

        autoResumeIds.forEach { startDownload(it) }
        autoMerge.forEach { startMerge(it) }
        autoResumeMerges.forEach { startMergedDownloads(it) }
    }

    private fun restoreManagedTask(
        item: DownloadItem,
        snapshot: ResumableStateSnapshot?,
    ): ManagedRestoreResult {
        val tempFile = tempFileFor(item.id, item.fileName)
        val cleanupTempFiles = tempFilesForCleanup(item.id, item.fileName)
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
            Log.d(
                TAG,
                "[restore-managed] checking existing output, taskId=${item.id}, groupId=${item.groupId}, file=${item.fileName}, status=${item.status}, userPaused=${item.userPaused}",
            )
            val existingUri = findAccessibleDownload(item.fileName, item.groupId)
                ?: findAccessibleDownloadAnywhere(item.fileName)
            if (existingUri != null) {
                Log.i(
                    TAG,
                    "[restore-managed] found existing output, mark success, taskId=${item.id}, file=${item.fileName}, uri=$existingUri",
                )
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
                    cleanupTempFiles = cleanupTempFiles,
                )
            }
            Log.d(
                TAG,
                "[restore-managed] existing output not found, taskId=${item.id}, file=${item.fileName}",
            )
        } else if (item.status == DownloadStatus.Success) {
            val existingUri = resolveCompletedOutputUri(
                item = item,
                traceSource = "restore-managed-success-${item.id}",
            )
            if (existingUri != null) {
                Log.i(
                    TAG,
                    "[restore-managed] verified completed output, cleanup temp, taskId=${item.id}, file=${item.fileName}, uri=$existingUri",
                )
                return ManagedRestoreResult(
                    item = item.copy(
                        progress = 100,
                        localUri = existingUri,
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                        userPaused = false,
                        errorMessage = null,
                    ),
                    state = null,
                    autoResume = false,
                    cleanupTempFiles = cleanupTempFiles,
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
            // Preserve the temp file so retry can continue from the breakpoint when possible.
            status = DownloadStatus.Failed
            userPaused = false
            autoResume = false
            errorMessage = context.getString(R.string.download_error_unsafe_exit)
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
        Log.d(
            TAG,
            "[restore-managed] rebuilt task state, taskId=${item.id}, file=${item.fileName}, finalStatus=${finalItem.status}, downloaded=$downloaded, total=$total, progress=$progress, autoResume=$autoResume",
        )
        val state = if (finalItem.status != DownloadStatus.Success &&
            finalItem.status != DownloadStatus.Cancelled) {
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
        return ManagedRestoreResult(
            item = finalItem,
            state = state,
            autoResume = autoResume,
            cleanupTempFiles = emptyList(),
        )
    }

    private fun restoreMergedTask(
        item: DownloadItem,
        snapshot: MergedTaskSnapshot?,
    ): MergedRestoreResult {
        val cleanupTempFiles = mergedTempFilesForCleanup(
            id = item.id,
            outputName = item.fileName,
            snapshot = snapshot,
        )
        val shouldCheckExisting = item.status == DownloadStatus.Pending ||
            item.status == DownloadStatus.Running ||
            item.status == DownloadStatus.Merging ||
            (item.status == DownloadStatus.Paused && !item.userPaused)
        if (shouldCheckExisting) {
            Log.d(
                TAG,
                "[restore-merge] checking existing output, taskId=${item.id}, groupId=${item.groupId}, file=${item.fileName}, status=${item.status}, userPaused=${item.userPaused}",
            )
            val existingUri = findAccessibleDownload(item.fileName, item.groupId)
                ?: findAccessibleDownloadAnywhere(item.fileName)
            if (existingUri != null) {
                Log.i(
                    TAG,
                    "[restore-merge] found existing output, mark success, taskId=${item.id}, file=${item.fileName}, uri=$existingUri",
                )
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
                    cleanupTempFiles = cleanupTempFiles,
                )
            }
            Log.d(
                TAG,
                "[restore-merge] existing output not found, taskId=${item.id}, file=${item.fileName}",
            )
        } else if (item.status == DownloadStatus.Success) {
            val existingUri = resolveCompletedOutputUri(
                item = item,
                traceSource = "restore-merge-success-${item.id}",
            )
            if (existingUri != null) {
                Log.i(
                    TAG,
                    "[restore-merge] verified completed output, cleanup temp, taskId=${item.id}, file=${item.fileName}, uri=$existingUri",
                )
                return MergedRestoreResult(
                    item = item.copy(
                        progress = 100,
                        localUri = existingUri,
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                        errorMessage = null,
                    ),
                    mergeTask = null,
                    autoResume = false,
                    autoMerge = false,
                    cleanupTempFiles = cleanupTempFiles,
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
                    cleanupTempFiles = emptyList(),
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
                cleanupTempFiles = emptyList(),
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
                cleanupTempFiles = emptyList(),
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
            // Preserve the temp parts so retry can resume or restart them selectively.
            status = DownloadStatus.Failed
            userPaused = false
            autoResume = false
            autoMerge = false
            errorMessage = context.getString(R.string.download_error_unsafe_exit)
        } else if (status == DownloadStatus.Merging) {
            // Preserve source parts so retry can try merge again first.
            status = DownloadStatus.Failed
            userPaused = false
            autoResume = false
            autoMerge = false
            errorMessage = context.getString(R.string.download_error_unsafe_exit)
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
                failed = status == DownloadStatus.Failed,
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
                failed = status == DownloadStatus.Failed,
            ),
            userPaused = userPaused,
            isMerging = false,
            completed = false,
            failed = status == DownloadStatus.Failed,
            outputUri = item.localUri,
        )
        return MergedRestoreResult(
            item = finalItem,
            mergeTask = mergeTask,
            autoResume = autoResume,
            autoMerge = autoMerge,
            cleanupTempFiles = emptyList(),
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
        DownloadForegroundService.requestSync(context)
        task.isMerging = true
        updateMergedProgress(task, true, null)
        Log.d(
            TAG,
            "[merge-chain] start merge, taskId=${task.id}, output=${task.outputName}, videoTemp=${task.video.tempFile.absolutePath}, audioTemp=${task.audio.tempFile.absolutePath}",
        )
        mergeJobs[task.id] = scope.launch {
            var cancelled = false
            try {
                var uri = performMerge(task)
                val target = tasks[task.id]
                if (mergeTasks[task.id] !== task) {
                    Log.i(
                        TAG,
                        "[merge-chain] merge finished for stale task, ignore result, taskId=${task.id}, uri=$uri",
                    )
                    return@launch
                }
                if (uri == null && target != null) {
                    Log.w(
                        TAG,
                        "[merge-chain] performMerge returned null, fallback lookup, taskId=${task.id}, file=${target.fileName}, groupId=${target.groupId}",
                    )
                    uri = findAccessibleDownload(target.fileName, target.groupId)
                        ?: findAccessibleDownloadAnywhere(target.fileName)
                }
                if (uri != null) {
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
                    Log.i(
                        TAG,
                        "[merge-chain] merge completed and resolved output, taskId=${task.id}, file=${target?.fileName}, uri=$uri",
                    )
                } else {
                    task.failed = true
                    if (target != null) {
                        Log.w(
                            TAG,
                            "Merge failed for task=${task.id}, file=${target.fileName}, error=Merge failed",
                        )
                        updateTask(
                            target.copy(
                                status = DownloadStatus.Failed,
                                speedBytesPerSec = 0,
                                etaSeconds = null,
                                errorMessage = "Merge failed",
                            ),
                        )
                    }
                    Log.e(
                        TAG,
                        "[merge-chain] merge failed to resolve output, taskId=${task.id}, file=${target?.fileName}, performMergeError=null",
                    )
                }
            } catch (err: CancellationException) {
                cancelled = true
                Log.i(
                    TAG,
                    "[merge-chain] merge cancelled, taskId=${task.id}, userPaused=${task.userPaused}",
                )
            } catch (err: Exception) {
                if (mergeTasks[task.id] === task) {
                    task.failed = true
                    val target = tasks[task.id]
                    if (target != null) {
                        Log.w(
                            TAG,
                            "Merge failed for task=${task.id}, file=${target.fileName}, error=${err.message}",
                            err,
                        )
                        updateTask(
                            target.copy(
                                status = DownloadStatus.Failed,
                                speedBytesPerSec = 0,
                                etaSeconds = null,
                                errorMessage = err.message ?: "Merge failed",
                            ),
                        )
                    }
                    Log.e(
                        TAG,
                        "[merge-chain] merge failed to resolve output, taskId=${task.id}, file=${target?.fileName}, performMergeError=${err.message}",
                        err,
                    )
                }
            } finally {
                task.isMerging = false
                mergeJobs.remove(task.id)
                if (mergeTasks[task.id] === task) {
                    when {
                        cancelled && !task.userPaused && !task.failed && !task.completed &&
                            task.video.completed && task.audio.completed -> {
                            Log.d(
                                TAG,
                                "[merge-chain] restart merge after cancellation, taskId=${task.id}",
                            )
                            startMerge(task)
                        }

                        cancelled || task.userPaused -> {
                            updateMergedProgress(task, true, null)
                        }
                    }
                }
            }
        }
    }

    private suspend fun performMerge(task: MergedDownload): String? {
        val groupId = tasks[task.id]?.groupId
        val relativePath = if (groupId != null) {
            groupRelativePath(groupId)
        } else {
            settingsRepository.downloadRootRelativePath()
        }
        val videoFile = task.video.tempFile
        val audioFile = task.audio.tempFile
        Log.d(
            TAG,
            "[merge-chain] performMerge begin, taskId=${task.id}, output=${task.outputName}, groupId=$groupId, relativePath=$relativePath, videoExists=${videoFile.exists()}, videoSize=${videoFile.length()}, audioExists=${audioFile.exists()}, audioSize=${audioFile.length()}",
        )
        if (!videoFile.exists() || !audioFile.exists()) {
            Log.w(
                TAG,
                "[merge-chain] merge source missing, taskId=${task.id}, videoExists=${videoFile.exists()}, audioExists=${audioFile.exists()}",
            )
            return null
        }
        val videoUsable = isMediaPartUsable(videoFile, "video/")
        val audioUsable = isMediaPartUsable(audioFile, "audio/")
        if (!videoUsable || !audioUsable) {
            Log.w(
                TAG,
                "[merge-chain] merge source unreadable, taskId=${task.id}, videoUsable=$videoUsable, audioUsable=$audioUsable",
            )
            return null
        }
        val outputTemp = tempFileFor(task.id, task.outputName)
        runCatching { outputTemp.delete() }
        var merged = false
        try {
            currentCoroutineContext().ensureActive()
            MediaMergeEngine.merge(videoFile, audioFile, outputTemp)
            currentCoroutineContext().ensureActive()
            merged = true
            Log.d(
                TAG,
                "[merge-chain] mux success, taskId=${task.id}, outputTemp=${outputTemp.absolutePath}, outputTempSize=${outputTemp.length()}",
            )
        } finally {
            if (!merged) {
                runCatching { outputTemp.delete() }
            }
        }

        if (!merged) return null

        currentCoroutineContext().ensureActive()
        tasks[task.id]?.let { item ->
            applyEmbeddedMetadataIfPossible(item, outputTemp)
        }

        currentCoroutineContext().ensureActive()
        var uri = saveToDownloads(outputTemp, task.outputName, relativePath)
        if (uri == null && groupId != null) {
            Log.w(
                TAG,
                "[merge-chain] saveToDownloads returned null, fallback in group, taskId=${task.id}, output=${task.outputName}, groupId=$groupId",
            )
            uri = findAccessibleDownload(task.outputName, groupId)
        }
        if (uri == null) {
            Log.w(
                TAG,
                "[merge-chain] in-group fallback miss, fallback anywhere, taskId=${task.id}, output=${task.outputName}",
            )
            uri = findAccessibleDownloadAnywhere(task.outputName)
        }
        if (uri == null) {
            runCatching { outputTemp.delete() }
            Log.e(
                TAG,
                "[merge-chain] merge output unresolved after save+fallback, taskId=${task.id}, output=${task.outputName}",
            )
        } else {
            runCatching { videoFile.delete() }
            runCatching { audioFile.delete() }
            Log.i(
                TAG,
                "[merge-chain] merge output resolved, taskId=${task.id}, output=${task.outputName}, uri=$uri",
            )
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

    private fun isMediaPartUsable(file: File, prefix: String): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val extractor = MediaExtractor()
        val input = runCatching { FileInputStream(file) }.getOrNull() ?: return false
        return try {
            extractor.setDataSource(input.fd)
            selectTrack(extractor, prefix) >= 0
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { extractor.release() }
            runCatching { input.close() }
        }
    }

    private fun applyEmbeddedMetadataIfPossible(item: DownloadItem, tempFile: File) {
        if (!settingsRepository.shouldAddMetadata()) return
        val metadata = item.embeddedMetadata ?: return
        if (!tempFile.exists()) return

        val ext = item.fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val supported = ext == "mp3" || ext == "m4a" || ext == "mp4" || ext == "flac"
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

    private suspend fun saveToDownloads(
        tempFile: File,
        fileName: String,
        relativePath: String,
    ): String? {
        val expectedSize = tempFile.length().coerceAtLeast(0L)
        val normalizedRelativePath = normalizeRelativePath(relativePath)
        val overwriteExisting = settingsRepository.shouldOverwriteExistingNamingTargets()
        Log.d(
            TAG,
            "[save-output] start, file=$fileName, relativePath=$relativePath, temp=${tempFile.absolutePath}, tempExists=${tempFile.exists()}, expectedSize=$expectedSize",
        )
        val preDeleteCount = deleteConflictingDownloadsForTarget(
            fileName = fileName,
            relativePath = normalizedRelativePath,
            excludeUri = null,
            overwriteExisting = overwriteExisting,
        )
        if (preDeleteCount > 0) {
            Log.w(
                TAG,
                "[save-output] removed conflicting rows before insert, file=$fileName, relativePath=$normalizedRelativePath, deletedCount=$preDeleteCount",
            )
        }
        val output = createOutputFile(fileName, guessMimeType(fileName), relativePath) ?: run {
            Log.e(
                TAG,
                "[save-output] createOutputFile returned null, file=$fileName, relativePath=$relativePath",
            )
            return null
        }
        val uri = output.uri
        var copied = false
        Log.d(
            TAG,
            "[save-output] output target prepared, uri=$uri, file=$fileName",
        )
        return try {
            output.pfd.use { pfd ->
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
                copied = true
                Log.d(
                    TAG,
                    "[save-output] file copy done, uri=$uri, file=$fileName, expectedSize=$expectedSize, statSizeAfterCopy=${pfd.statSize}",
                )
            }
            val finalizeErr = runCatching { finalizeOutputFile(uri) }.exceptionOrNull()
            val resolvedUri = if (finalizeErr == null) {
                uri.toString()
            } else {
                Log.w(
                    TAG,
                    "Finalize output failed but file copy succeeded, uri=$uri, file=$fileName, expectedSize=$expectedSize",
                    finalizeErr,
                )
                recoverAfterFinalizeFailure(
                    insertedUri = uri,
                    fileName = fileName,
                    relativePath = normalizedRelativePath,
                    expectedSize = expectedSize,
                    finalizeErr = finalizeErr,
                    overwriteExisting = overwriteExisting,
                )
            }
            runCatching { tempFile.delete() }
            if (resolvedUri != null) {
                if (resolvedUri != uri.toString()) {
                    runCatching { resolver.delete(uri, null, null) }
                    Log.w(
                        TAG,
                        "[save-output] cleaned up inserted unstable uri after recovery, insertedUri=$uri, resolvedUri=$resolvedUri, file=$fileName",
                    )
                }
                Log.i(
                    TAG,
                    "[save-output] success, insertedUri=$uri, resolvedUri=$resolvedUri, file=$fileName, relativePath=$relativePath",
                )
                resolvedUri
            } else {
                Log.e(
                    TAG,
                    "[save-output] failed to resolve stable uri after copy, file=$fileName, insertedUri=$uri, relativePath=$relativePath",
                )
                runCatching { resolver.delete(uri, null, null) }
                null
            }
        } catch (err: CancellationException) {
            Log.i(
                TAG,
                "[save-output] cancelled, cleanup partial output, file=$fileName, uri=$uri, copied=$copied",
            )
            runCatching { resolver.delete(uri, null, null) }
            throw err
        } catch (err: Throwable) {
            Log.w(
                TAG,
                "[save-output] exception during save, file=$fileName, uri=$uri, copied=$copied, expectedSize=$expectedSize, error=${err.message}",
                err,
            )
            val recoveredUri = resolveStableOutputUri(
                insertedUri = uri,
                fileName = fileName,
                relativePath = normalizedRelativePath,
                expectedSize = expectedSize,
            )
            if (recoveredUri != null) {
                if (recoveredUri != uri.toString()) {
                    runCatching { resolver.delete(uri, null, null) }
                    Log.w(
                        TAG,
                        "[save-output] cleaned up inserted unstable uri after exception recovery, insertedUri=$uri, recoveredUri=$recoveredUri, file=$fileName",
                    )
                }
                Log.w(
                    TAG,
                    "[save-output] recovered stable uri after exception, file=$fileName, insertedUri=$uri, recoveredUri=$recoveredUri",
                    err,
                )
                runCatching { tempFile.delete() }
                recoveredUri
            } else {
                Log.w(
                    TAG,
                    "Save failed and output not persisted, cleanup uri=$uri, file=$fileName, expectedSize=$expectedSize",
                    err,
                )
                runCatching { resolver.delete(uri, null, null) }
                Log.e(
                    TAG,
                    "[save-output] cleanup finished and return null, file=$fileName, uri=$uri",
                )
                null
            }
        }
    }

    private fun existsInMediaStore(uri: Uri): Boolean {
        val result = runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.Downloads._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }
        result.exceptionOrNull()?.let { err ->
            Log.w(
                TAG,
                "[media-check] query failed for uri=$uri",
                err,
            )
        }
        val exists = result.getOrDefault(false)
        Log.d(
            TAG,
            "[media-check] existsInMediaStore uri=$uri -> $exists",
        )
        return exists
    }

    private fun isSavedOutputAccessible(uri: Uri, expectedSize: Long): Boolean {
        val statSize = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize
            }
        }.getOrNull()
        val knownSize = when {
            statSize != null && statSize >= 0L -> statSize
            else -> queryUriSize(uri)
        }
        if (knownSize != null && knownSize >= 0L) {
            val result = if (expectedSize > 0L) {
                knownSize >= expectedSize
            } else {
                knownSize > 0L || expectedSize == 0L
            }
            Log.d(
                TAG,
                "[media-check] size check, uri=$uri, statSize=$statSize, knownSize=$knownSize, expectedSize=$expectedSize, accessible=$result",
            )
            return result
        }
        val streamResult = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                if (expectedSize > 0L) {
                    var remaining = expectedSize
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (remaining > 0) {
                        val read = input.read(
                            buffer,
                            0,
                            minOf(buffer.size.toLong(), remaining).toInt(),
                        )
                        if (read <= 0) return@use false
                        remaining -= read
                    }
                    true
                } else {
                    input.read() >= 0
                }
            } ?: false
        }
        streamResult.exceptionOrNull()?.let { err ->
            Log.w(
                TAG,
                "[media-check] stream check failed, uri=$uri, expectedSize=$expectedSize",
                err,
            )
        }
        val accessible = streamResult.getOrDefault(false)
        Log.d(
            TAG,
            "[media-check] stream check, uri=$uri, expectedSize=$expectedSize, accessible=$accessible",
        )
        return accessible
    }

    private fun queryUriSize(uri: Uri): Long? {
        val result = runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex).takeIf { it >= 0L }
                } else {
                    null
                }
            }
        }
        result.exceptionOrNull()?.let { err ->
            Log.w(
                TAG,
                "[media-check] queryUriSize failed, uri=$uri",
                err,
            )
        }
        val size = result.getOrNull()
        Log.d(
            TAG,
            "[media-check] queryUriSize uri=$uri -> $size",
        )
        return size
    }

    private fun normalizeRelativePath(relativePath: String): String {
        val trimmed = relativePath.trim().trimEnd('/')
        return if (trimmed.isBlank()) "" else "$trimmed/"
    }

    private fun isManagedDownloadRelativePath(relativePath: String): Boolean {
        val normalizedTargetPath = normalizeRelativePath(relativePath)
        if (normalizedTargetPath.isBlank()) return false
        val managedRoot = normalizeRelativePath(settingsRepository.downloadRootRelativePath())
        if (managedRoot.isBlank()) return false
        return normalizedTargetPath.startsWith(managedRoot, ignoreCase = true)
    }

    private fun isSameRelativePath(candidatePath: String, normalizedTargetPath: String): Boolean {
        if (normalizedTargetPath.isBlank()) return false
        val normalizedCandidate = normalizeRelativePath(candidatePath)
        return normalizedCandidate == normalizedTargetPath
    }

    private fun deleteConflictingDownloadsForTarget(
        fileName: String,
        relativePath: String,
        excludeUri: String?,
        overwriteExisting: Boolean,
    ): Int {
        if (fileName.isBlank()) return 0
        val normalizedTargetPath = normalizeRelativePath(relativePath)
        if (normalizedTargetPath.isBlank()) return 0
        if (!isManagedDownloadRelativePath(normalizedTargetPath)) {
            Log.w(
                TAG,
                "[save-output] skip deleting conflicts outside managed download root, file=$fileName, relativePath=$normalizedTargetPath",
            )
            return 0
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.RELATIVE_PATH,
            MediaStore.Downloads.IS_PENDING,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(fileName)
        var deletedCount = 0
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Downloads._ID)
            val pathIndex = cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)
            val pendingIndex = cursor.getColumnIndex(MediaStore.Downloads.IS_PENDING)
            while (idIndex >= 0 && cursor.moveToNext()) {
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                if (!isSameRelativePath(path, normalizedTargetPath)) continue
                val id = cursor.getLong(idIndex)
                val uri = Uri.withAppendedPath(collection, id.toString()).toString()
                if (excludeUri != null && uri == excludeUri) continue
                val pending = if (pendingIndex >= 0) cursor.getInt(pendingIndex) else -1
                val accessible = isLocalUriAccessible(uri, "deleteConflictingDownloadsForTarget")
                val shouldDelete = overwriteExisting || pending == 1 || !accessible
                if (!shouldDelete) {
                    Log.d(
                        TAG,
                        "[save-output] keep existing healthy row, file=$fileName, uri=$uri, path=$path, pending=$pending",
                    )
                    continue
                }
                val rows = runCatching {
                    resolver.delete(Uri.parse(uri), null, null)
                }.onFailure { err ->
                    Log.w(
                        TAG,
                        "[save-output] failed to delete conflicting row, file=$fileName, uri=$uri, path=$path, pending=$pending, accessible=$accessible",
                        err,
                    )
                }.getOrDefault(0)
                if (rows > 0) {
                    deletedCount += rows
                    Log.w(
                        TAG,
                        "[save-output] deleted conflicting row, file=$fileName, uri=$uri, path=$path, pending=$pending, accessible=$accessible, rows=$rows",
                    )
                }
            }
        }
        return deletedCount
    }

    private fun queryIsPending(uri: Uri): Int? {
        val result = runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.Downloads.IS_PENDING),
                null,
                null,
                null,
            )?.use { cursor ->
                val pendingIndex = cursor.getColumnIndex(MediaStore.Downloads.IS_PENDING)
                if (pendingIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getInt(pendingIndex)
                } else {
                    null
                }
            }
        }
        result.exceptionOrNull()?.let { err ->
            Log.w(
                TAG,
                "[media-check] queryIsPending failed, uri=$uri",
                err,
            )
        }
        val pending = result.getOrNull()
        Log.d(
            TAG,
            "[media-check] queryIsPending uri=$uri -> $pending",
        )
        return pending
    }

    private fun findAccessibleDownloadInRelativePath(
        fileName: String,
        relativePath: String,
        excludeUri: String? = null,
    ): String? {
        if (fileName.isBlank()) return null
        if (relativePath.isBlank()) return null
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.Downloads.IS_PENDING,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(fileName)
        var scanned = 0
        resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Downloads._ID)
            val pathIndex = cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val pendingIndex = cursor.getColumnIndex(MediaStore.Downloads.IS_PENDING)
            while (idIndex >= 0 && cursor.moveToNext()) {
                scanned++
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                if (!isSameRelativePath(path, relativePath)) continue
                val pending = if (pendingIndex >= 0) cursor.getInt(pendingIndex) else -1
                if (pending == 1) {
                    Log.d(
                        TAG,
                        "[locate] path candidate skipped by pending=1, file=$fileName, relativePath=$relativePath, scanned=$scanned",
                    )
                    continue
                }
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                if (size == 0L) {
                    Log.d(
                        TAG,
                        "[locate] path candidate skipped by zero size, file=$fileName, relativePath=$relativePath, scanned=$scanned",
                    )
                    continue
                }
                val id = cursor.getLong(idIndex)
                val uri = Uri.withAppendedPath(collection, id.toString()).toString()
                if (excludeUri != null && uri == excludeUri) continue
                val accessible = isLocalUriAccessible(uri, "findAccessibleDownloadInRelativePath")
                Log.d(
                    TAG,
                    "[locate] path candidate, file=$fileName, relativePath=$relativePath, scanned=$scanned, size=$size, pending=$pending, uri=$uri, accessible=$accessible",
                )
                if (accessible) {
                    Log.i(
                        TAG,
                        "[locate] path lookup hit, file=$fileName, relativePath=$relativePath, scanned=$scanned, uri=$uri",
                    )
                    return uri
                }
            }
        }
        Log.d(
            TAG,
            "[locate] path lookup miss, file=$fileName, relativePath=$relativePath, scanned=$scanned",
        )
        return null
    }

    private fun findAccessibleFileInFilesCollection(
        fileName: String,
        relativePath: String? = null,
        excludeUri: String? = null,
    ): String? {
        if (fileName.isBlank()) return null
        val filesCollection = MediaStore.Files.getContentUri("external")
        val normalizedPath = relativePath?.let { normalizeRelativePath(it) }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_PENDING,
        )
        val selection: String
        val selectionArgs: Array<String>
        if (!normalizedPath.isNullOrBlank()) {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            selectionArgs = arrayOf(fileName, normalizedPath)
        } else {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            selectionArgs = arrayOf(fileName)
        }
        var scanned = 0
        resolver.query(
            filesCollection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val pendingIndex = cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
            while (idIndex >= 0 && cursor.moveToNext()) {
                scanned++
                val rowPath = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                if (!normalizedPath.isNullOrBlank() && !isSameRelativePath(rowPath, normalizedPath)) {
                    continue
                }
                val pending = if (pendingIndex >= 0) cursor.getInt(pendingIndex) else -1
                if (pending == 1) {
                    Log.d(
                        TAG,
                        "[locate-files] skip pending row, file=$fileName, relativePath=$normalizedPath, scanned=$scanned",
                    )
                    continue
                }
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                if (size == 0L) continue
                val id = cursor.getLong(idIndex)
                val uri = Uri.withAppendedPath(filesCollection, id.toString()).toString()
                if (excludeUri != null && uri == excludeUri) continue
                val accessible = isLocalUriAccessible(uri, "findAccessibleFileInFilesCollection")
                Log.d(
                    TAG,
                    "[locate-files] candidate, file=$fileName, relativePath=$normalizedPath, scanned=$scanned, rowPath=$rowPath, size=$size, pending=$pending, uri=$uri, accessible=$accessible",
                )
                if (accessible) {
                    Log.i(
                        TAG,
                        "[locate-files] hit, file=$fileName, relativePath=$normalizedPath, scanned=$scanned, uri=$uri",
                    )
                    return uri
                }
            }
        }
        Log.d(
            TAG,
            "[locate-files] miss, file=$fileName, relativePath=$normalizedPath, scanned=$scanned",
        )
        return null
    }

    private fun resolveStableOutputUri(
        insertedUri: Uri,
        fileName: String,
        relativePath: String,
        expectedSize: Long,
    ): String? {
        val insertedUriString = insertedUri.toString()
        val pending = queryIsPending(insertedUri)
        val insertedAccessible = isSavedOutputAccessible(insertedUri, expectedSize)
        if (pending != 1 && insertedAccessible) {
            Log.d(
                TAG,
                "[save-output] inserted uri is stable, uri=$insertedUri, file=$fileName, pending=$pending",
            )
            return insertedUriString
        }
        val pathUri = findAccessibleDownloadInRelativePath(
            fileName = fileName,
            relativePath = relativePath,
            excludeUri = insertedUriString,
        )
        if (pathUri != null) return pathUri
        val filesPathUri = findAccessibleFileInFilesCollection(
            fileName = fileName,
            relativePath = relativePath,
            excludeUri = insertedUriString,
        )
        if (filesPathUri != null) return filesPathUri
        val anywhereUri = findAccessibleDownloadAnywhere(fileName)
        if (anywhereUri != null && anywhereUri != insertedUriString) return anywhereUri
        val filesAnywhereUri = findAccessibleFileInFilesCollection(
            fileName = fileName,
            relativePath = null,
            excludeUri = insertedUriString,
        )
        if (filesAnywhereUri != null) return filesAnywhereUri
        Log.w(
            TAG,
            "[save-output] unable to resolve stable uri, insertedUri=$insertedUri, file=$fileName, pending=$pending, insertedAccessible=$insertedAccessible",
        )
        return null
    }

    private fun recoverAfterFinalizeFailure(
        insertedUri: Uri,
        fileName: String,
        relativePath: String,
        expectedSize: Long,
        finalizeErr: Throwable,
        overwriteExisting: Boolean,
    ): String? {
        val insertedUriString = insertedUri.toString()
        Log.w(
            TAG,
            "[save-output] recover after finalize failure start, insertedUri=$insertedUri, file=$fileName, relativePath=$relativePath, error=${finalizeErr.message}",
            finalizeErr,
        )
        val deletedConflict = deleteConflictingDownloadsForTarget(
            fileName = fileName,
            relativePath = relativePath,
            excludeUri = insertedUriString,
            overwriteExisting = overwriteExisting,
        )
        if (deletedConflict > 0) {
            Log.w(
                TAG,
                "[save-output] deleted conflicts before retry finalize, insertedUri=$insertedUri, file=$fileName, deletedCount=$deletedConflict",
            )
        }
        val retryErr = runCatching { finalizeOutputFile(insertedUri) }.exceptionOrNull()
        if (retryErr == null) {
            Log.i(
                TAG,
                "[save-output] retry finalize succeeded, insertedUri=$insertedUri, file=$fileName",
            )
            return insertedUriString
        }
        Log.w(
            TAG,
            "[save-output] retry finalize still failed, insertedUri=$insertedUri, file=$fileName, error=${retryErr.message}",
            retryErr,
        )
        if (isLikelyDataPathUniqueConstraint(retryErr)) {
            val renamed = tryFinalizeWithAlternativeNames(
                insertedUri = insertedUri,
                originalFileName = fileName,
                relativePath = relativePath,
                expectedSize = expectedSize,
            )
            if (renamed != null) {
                return renamed
            }
        }
        return resolveStableOutputUri(
            insertedUri = insertedUri,
            fileName = fileName,
            relativePath = relativePath,
            expectedSize = expectedSize,
        )
    }

    private fun isLikelyDataPathUniqueConstraint(err: Throwable): Boolean {
        val message = err.message.orEmpty().lowercase(Locale.US)
        return message.contains("unique constraint failed") && message.contains("files._data")
    }

    private fun splitNameAndExtension(fileName: String): Pair<String, String> {
        val index = fileName.lastIndexOf('.')
        return if (index <= 0 || index == fileName.length - 1) {
            fileName to ""
        } else {
            fileName.substring(0, index) to fileName.substring(index)
        }
    }

    private fun buildAlternativeFileName(fileName: String, index: Int): String {
        val (base, ext) = splitNameAndExtension(fileName)
        return "$base ($index)$ext"
    }

    private fun tryFinalizeWithAlternativeNames(
        insertedUri: Uri,
        originalFileName: String,
        relativePath: String,
        expectedSize: Long,
    ): String? {
        for (index in 1..20) {
            val candidate = buildAlternativeFileName(originalFileName, index)
            val err = runCatching {
                finalizeOutputFile(insertedUri, finalDisplayName = candidate)
            }.exceptionOrNull()
            if (err == null) {
                Log.i(
                    TAG,
                    "[save-output] finalize succeeded with alternative name, insertedUri=$insertedUri, originalFile=$originalFileName, finalName=$candidate",
                )
                return insertedUri.toString()
            }
            Log.w(
                TAG,
                "[save-output] finalize with alternative name failed, insertedUri=$insertedUri, candidate=$candidate, error=${err.message}",
                err,
            )
            if (!isLikelyDataPathUniqueConstraint(err)) {
                break
            }
        }
        return null
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "m4s" -> "video/mp4"
            "m4a" -> "audio/mp4"
            "flv" -> "video/x-flv"
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "eac3" -> "audio/eac3"
            "aac" -> "audio/aac"
            else -> "application/octet-stream"
        }
    }

    private fun tempLegacyFileFor(id: Long, fileName: String): File {
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return File(tempDir, "$id-$safeName.part")
    }

    private fun tempFileFor(id: Long, fileName: String): File {
        val legacy = tempLegacyFileFor(id, fileName)
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

    private fun tempFilesForCleanup(id: Long, fileName: String): List<File> {
        return listOf(
            tempLegacyFileFor(id, fileName),
            tempFileForNewSchema(id, fileName),
        ).distinctBy { it.absolutePath }
    }

    private fun mergedTempFilesForCleanup(
        id: Long,
        outputName: String,
        snapshot: MergedTaskSnapshot?,
    ): List<File> {
        val candidates = linkedSetOf(outputName)
        if (snapshot != null) {
            candidates += snapshot.video.fileName
            candidates += snapshot.audio.fileName
        } else {
            val baseName = outputName.substringBeforeLast('.')
            candidates += "$baseName-video.m4s"
            candidates += "$baseName-audio.m4s"
        }
        return candidates
            .flatMap { fileName -> tempFilesForCleanup(id, fileName) }
            .distinctBy { it.absolutePath }
    }

    private fun resolveCompletedOutputUri(item: DownloadItem, traceSource: String): String? {
        val currentUri = item.localUri
        if (isLocalUriAccessible(currentUri, traceSource)) {
            return currentUri
        }
        return findAccessibleDownload(item.fileName, item.groupId)
            ?: findAccessibleDownloadAnywhere(item.fileName)
    }

    private fun cleanupCompletedTempFiles(files: Collection<File>) {
        files.distinctBy { it.absolutePath }.forEach { file ->
            if (!file.exists()) return@forEach
            val deleted = runCatching { file.delete() }.getOrDefault(false)
            if (deleted) {
                Log.i(
                    TAG,
                    "[temp-cleanup] deleted completed task temp file, path=${file.absolutePath}",
                )
            } else {
                Log.w(
                    TAG,
                    "[temp-cleanup] failed to delete completed task temp file, path=${file.absolutePath}",
                )
            }
        }
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

    private fun resolveRequestedGroupRelativePath(requestedRelativePath: String): String {
        val normalized = requestedRelativePath
            .replace('\\', '/')
            .trim()
            .trim('/')
        if (normalized.isBlank()) {
            return buildGroupRelativePath("BiliTools")
        }
        if (settingsRepository.shouldOverwriteExistingNamingTargets()) {
            return normalized
        }
        if (!groupRelativePathExists(normalized) && !relativePathHasExistingOutputs(normalized)) {
            return normalized
        }
        return buildUniqueRelativePath(normalized)
    }

    private fun groupRelativePathExists(relativePath: String): Boolean {
        return synchronized(lock) {
            groupInfo.values.any { info ->
                info.relativePath.equals(relativePath, ignoreCase = true)
            }
        }
    }

    private fun relativePathHasExistingOutputs(relativePath: String): Boolean {
        val collection = MediaStore.Files.getContentUri("external")
        val normalized = normalizeRelativePath(relativePath)
        if (normalized.isBlank()) return false
        return resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(normalized),
            null,
        )?.use { cursor ->
            cursor.moveToFirst()
        } ?: false
    }

    private fun buildUniqueRelativePath(relativePath: String): String {
        val normalized = relativePath.replace('\\', '/').trim().trim('/')
        val parent = normalized.substringBeforeLast('/', "")
        val leaf = normalized.substringAfterLast('/')
        if (leaf.isBlank()) return normalized
        var index = 1
        while (index <= 200) {
            val candidateLeaf = "$leaf($index)"
            val candidate = if (parent.isBlank()) {
                candidateLeaf
            } else {
                "$parent/$candidateLeaf"
            }
            if (!groupRelativePathExists(candidate) && !relativePathHasExistingOutputs(candidate)) {
                return candidate
            }
            index++
        }
        return normalized
    }

    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun createOutputFile(
        fileName: String,
        mimeType: String,
        relativePath: String,
    ): OutputTarget? {
        Log.d(
            TAG,
            "[output-create] start, file=$fileName, mimeType=$mimeType, relativePath=$relativePath",
        )
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: run {
            Log.e(
                TAG,
                "[output-create] MediaStore insert returned null, file=$fileName, relativePath=$relativePath",
            )
            return null
        }
        val pfd = resolver.openFileDescriptor(uri, "w")
        if (pfd == null) {
            runCatching { resolver.delete(uri, null, null) }
            Log.e(
                TAG,
                "[output-create] openFileDescriptor returned null, cleanup uri=$uri, file=$fileName",
            )
            return null
        }
        Log.d(
            TAG,
            "[output-create] success, uri=$uri, file=$fileName",
        )
        return OutputTarget(uri, pfd)
    }

    private fun finalizeOutputFile(uri: Uri, finalDisplayName: String? = null) {
        val update = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
            if (!finalDisplayName.isNullOrBlank()) {
                put(MediaStore.Downloads.DISPLAY_NAME, finalDisplayName)
            }
        }
        val updatedRows = resolver.update(uri, update, null, null)
        Log.d(
            TAG,
            "[output-create] finalize pending->0, uri=$uri, displayName=$finalDisplayName, updatedRows=$updatedRows",
        )
    }

    private fun findExistingDownload(fileName: String, groupId: Long): String? {
        if (fileName.isBlank()) {
            Log.d(
                TAG,
                "[locate] findExistingDownload skipped, blank fileName, groupId=$groupId",
            )
            return null
        }
        val relativePath = groupRelativePath(groupId).trim()
        if (relativePath.isBlank()) {
            Log.d(
                TAG,
                "[locate] findExistingDownload skipped, blank relativePath, file=$fileName, groupId=$groupId",
            )
            return null
        }
        val normalizedPath = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(fileName)
        Log.d(
            TAG,
            "[locate] query in-group start, file=$fileName, groupId=$groupId, relativePath=$relativePath, normalizedPath=$normalizedPath",
        )
        var scanned = 0
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Downloads._ID)
            val pathIndex = cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)
            while (idIndex >= 0 && cursor.moveToNext()) {
                scanned++
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                if (path == normalizedPath || path == relativePath) {
                    val id = cursor.getLong(idIndex)
                    val uri = Uri.withAppendedPath(collection, id.toString()).toString()
                    Log.i(
                        TAG,
                        "[locate] query in-group hit, file=$fileName, groupId=$groupId, scanned=$scanned, matchedPath=$path, uri=$uri",
                    )
                    return uri
                }
            }
        }
        Log.d(
            TAG,
            "[locate] query in-group miss, file=$fileName, groupId=$groupId, scanned=$scanned",
        )
        return null
    }

    private fun findAccessibleDownload(fileName: String, groupId: Long): String? {
        val relativePath = normalizeRelativePath(groupRelativePath(groupId))
        val uri = findAccessibleDownloadInRelativePath(
            fileName = fileName,
            relativePath = relativePath,
        ) ?: findAccessibleFileInFilesCollection(
            fileName = fileName,
            relativePath = relativePath,
        )
        if (uri == null) {
            Log.d(
                TAG,
                "[locate] in-group locate miss, file=$fileName, groupId=$groupId, relativePath=$relativePath",
            )
        } else {
            Log.i(
                TAG,
                "[locate] in-group locate hit, file=$fileName, groupId=$groupId, relativePath=$relativePath, uri=$uri",
            )
        }
        return uri
    }

    private fun findAccessibleDownloadAnywhere(fileName: String): String? {
        if (fileName.isBlank()) {
            Log.d(
                TAG,
                "[locate] findAccessibleDownloadAnywhere skipped, blank fileName",
            )
            return null
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.Downloads.IS_PENDING,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(fileName)
        Log.d(
            TAG,
            "[locate] anywhere lookup start, file=$fileName",
        )
        var scanned = 0
        resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Downloads._ID)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val pendingIndex = cursor.getColumnIndex(MediaStore.Downloads.IS_PENDING)
            while (idIndex >= 0 && cursor.moveToNext()) {
                scanned++
                val pending = if (pendingIndex >= 0) cursor.getInt(pendingIndex) else -1
                if (pending == 1) {
                    Log.d(
                        TAG,
                        "[locate] anywhere candidate skipped by pending=1, file=$fileName, scanned=$scanned",
                    )
                    continue
                }
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                if (size == 0L) {
                    Log.d(
                        TAG,
                        "[locate] anywhere candidate skipped by zero size, file=$fileName, scanned=$scanned",
                    )
                    continue
                }
                val id = cursor.getLong(idIndex)
                val uri = Uri.withAppendedPath(collection, id.toString()).toString()
                val accessible = isLocalUriAccessible(uri, "findAccessibleDownloadAnywhere")
                Log.d(
                    TAG,
                    "[locate] anywhere candidate, file=$fileName, scanned=$scanned, size=$size, pending=$pending, uri=$uri, accessible=$accessible",
                )
                if (accessible) {
                    Log.i(
                        TAG,
                        "[locate] anywhere lookup hit, file=$fileName, scanned=$scanned, uri=$uri",
                    )
                    return uri
                }
            }
        }
        Log.d(
            TAG,
            "[locate] anywhere lookup miss, file=$fileName, scanned=$scanned",
        )
        val filesUri = findAccessibleFileInFilesCollection(
            fileName = fileName,
            relativePath = null,
        )
        if (filesUri != null) {
            Log.i(
                TAG,
                "[locate] anywhere lookup recovered from files collection, file=$fileName, uri=$filesUri",
            )
        }
        return filesUri
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
        progressIndeterminate: Boolean = false,
        downloadedBytes: Long = 0,
        totalBytes: Long = 0,
        speedBytesPerSec: Long = 0,
        etaSeconds: Long? = null,
        reason: Int? = null,
        localUri: String? = null,
        userPaused: Boolean = false,
        errorMessage: String? = null,
        statusDetail: String? = null,
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
            progressIndeterminate = progressIndeterminate,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speedBytesPerSec,
            etaSeconds = etaSeconds,
            reason = reason,
            localUri = localUri,
            userPaused = userPaused,
            errorMessage = errorMessage,
            statusDetail = statusDetail,
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
        var url: String,
        val fileName: String,
        override val tempFile: File,
        override var downloadedBytes: Long = 0,
        override var totalBytes: Long = 0,
        override var etag: String? = null,
        override var lastModified: String? = null,
        override var speedBytesPerSec: Long = 0,
    ) : ResumableTarget

    private data class ResumablePart(
        var url: String,
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
        val cleanupTempFiles: List<File>,
    )

    private data class MergedRestoreResult(
        val item: DownloadItem,
        val mergeTask: MergedDownload?,
        val autoResume: Boolean,
        val autoMerge: Boolean,
        val cleanupTempFiles: List<File>,
    )

    private data class RetrySourceContext(
        val mediaInfo: MediaInfo,
        val item: MediaItem,
        val mediaType: MediaType,
        val playUrlInfo: com.happycola233.bilitools.data.model.PlayUrlInfo,
    )

    private data class RefreshedMergeUrls(
        val videoUrl: String,
        val audioUrl: String,
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
        private const val TAG = "DownloadRepository"
        private const val BILI_STATUS_CODE_HEADER = "bili-status-code"
        private const val LOGIN_REQUIRED_CODE = -101
        private const val PROGRESS_UPDATE_INTERVAL_MS = 300L
        private const val PERSIST_DELAY_MS = 1000L
        private const val STORE_VERSION = 1
        private const val EXTRA_TASK_PARALLELISM = 3
        private const val EXTRA_TASK_ID_START = -1_000_000_000L
    }
}

data class DownloadNotificationState(
    val activeTaskIds: Set<Long> = emptySet(),
    val inProgressCount: Int = 0,
    val pausedCount: Int = 0,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long? = null,
    val primaryTitle: String? = null,
    val primaryStatus: DownloadStatus? = null,
    val hasForegroundWork: Boolean = false,
)

data class DownloadOutcomeSummary(
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val cancelledCount: Int = 0,
)
