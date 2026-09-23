package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.StringProvider
import com.happycola233.bilitools.core.DownloadMessageCatalog
import com.happycola233.bilitools.core.resolve
import com.happycola233.bilitools.data.model.DownloadMessage
import com.happycola233.bilitools.data.model.DownloadMessageCode
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
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.MediaProcessingEngine
import com.happycola233.bilitools.core.naming.NamingRenderer
import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.createHttpDiagnosticLoggingInterceptor
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadEmbedding
import com.happycola233.bilitools.data.model.DownloadExtraTaskOperation
import com.happycola233.bilitools.data.model.DownloadExtraTaskSpec
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadProgressRules
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadSource
import com.happycola233.bilitools.data.model.downloadSource
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.isResolvedWithoutFailure
import com.happycola233.bilitools.data.model.isManagedTransfer
import com.happycola233.bilitools.data.model.subtitleTaskKey
import com.happycola233.bilitools.data.model.subtitleTaskKeyFor
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private val extrasRepository: ExtrasRepository,
    private val exportRepository: ExportRepository,
) {
    private val strings = StringProvider(context)
    private val messageCatalog = DownloadMessageCatalog(context)
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

    private val resumableDownloader by lazy {
        ResumableDownloader(httpClient, onStateChanged = ::schedulePersist)
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
    private val managedTaskQueue = TaskConcurrencyQueue(
        settingsRepository.maxConcurrentDownloads(),
    )
    private val extraTaskQueue = TaskConcurrencyQueue(EXTRA_TASK_PARALLELISM)
    private val managedRetryTaskIds = ConcurrentHashMap.newKeySet<Long>()
    private val extraTaskSpecs = ConcurrentHashMap<Long, DownloadExtraTaskSpec>()
    private val mergeIds = AtomicLong(-1L)
    private val downloadIds = AtomicLong(0L)
    private val groupIds = AtomicLong(0L)
    private val extraTaskIds = AtomicLong(EXTRA_TASK_ID_START)
    private val groupInfo = ConcurrentHashMap<Long, GroupInfo>()
    private val groupTaskIds = ConcurrentHashMap<Long, MutableList<Long>>()
    private val tasks = ConcurrentHashMap<Long, DownloadItem>()
    private val deletingGroupIds = mutableSetOf<Long>()
    private val deletingTaskIds = mutableSetOf<Long>()
    private val lock = Any()
    private val schedulingLock = Any()

    init {
        scope.launch {
            settingsRepository.settings
                .map { settings -> settings.maxConcurrentDownloads }
                .distinctUntilChanged()
                .collect { limit ->
                    managedTaskQueue.updateLimit(limit)
                    startReadyManagedTasks()
                }
        }
    }

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
                val outputBytes = if (accessible && resolvedUri != null) {
                    readOutputSize(Uri.parse(resolvedUri)) ?: item.outputBytes
                } else {
                    item.outputBytes
                }
                val shouldUpdate = item.outputMissing != missing || item.localUri != resolvedUri ||
                    item.outputBytes != outputBytes
                if (shouldUpdate) {
                    changedCount++
                    Log.i(
                        TAG,
                        "[output-check] item output state changed, taskId=${item.id}, file=${item.fileName}, oldMissing=${item.outputMissing}, newMissing=$missing, oldUri=${item.localUri}, newUri=$resolvedUri",
                    )
                    updateTask(item.copy(localUri = resolvedUri, outputMissing = missing, outputBytes = outputBytes))
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
        sourceMetadata: DownloadEmbeddedMetadata? = null,
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
                sourceMetadata,
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
        embedding: DownloadEmbedding? = null,
        backupUrls: List<String> = emptyList(),
    ): DownloadItem {
        val id = downloadIds.incrementAndGet()
        val conversionTarget = resolveMediaConversionTarget(type, fileName, embedding)
        val outputFileName = MediaConversionPolicy.outputFileName(fileName, conversionTarget)
        val item = buildItem(
            id,
            groupId,
            type,
            taskTitle,
            outputFileName,
            url,
            mediaParams = mediaParams,
            embeddedMetadata = embeddedMetadata,
            embedding = embedding,
        )
        val state = ResumableState(
            id = id,
            source = DownloadSource(url, backupUrls),
            fileName = fileName,
            tempFile = tempFileFor(id, fileName),
            conversionTarget = conversionTarget,
        )
        downloadStates[id] = state
        addTask(item)
        requestManagedTaskStart(id)
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
        embedding: DownloadEmbedding? = null,
        videoBackupUrls: List<String> = emptyList(),
        audioBackupUrls: List<String> = emptyList(),
    ): DownloadItem {
        val conversionTarget = resolveMediaConversionTarget(DownloadTaskType.AudioVideo, outputFileName, embedding)
        val resolvedOutputFileName = MediaConversionPolicy.outputFileName(
            outputFileName,
            conversionTarget,
        )
        val baseName = outputFileName.substringBeforeLast('.')
        val videoName = "$baseName-video.m4s"
        val audioName = "$baseName-audio.m4s"
        val requestTitle = groupInfo[groupId]?.title ?: taskTitle
        val mergeId = mergeIds.getAndDecrement()
        val task = MergedDownload(
            id = mergeId,
            title = requestTitle,
            outputName = resolvedOutputFileName,
            video = ResumablePart(
                source = DownloadSource(videoUrl, videoBackupUrls),
                fileName = videoName,
                tempFile = tempFileFor(mergeId, videoName),
            ),
            audio = ResumablePart(
                source = DownloadSource(audioUrl, audioBackupUrls),
                fileName = audioName,
                tempFile = tempFileFor(mergeId, audioName),
            ),
            conversionTarget = conversionTarget,
        )
        mergeTasks[mergeId] = task
        val item = buildItem(
            mergeId,
            groupId,
            DownloadTaskType.AudioVideo,
            taskTitle,
            resolvedOutputFileName,
            videoUrl,
            mediaParams = mediaParams,
            embeddedMetadata = embeddedMetadata,
            embedding = embedding,
        )
        addTask(item)
        requestManagedTaskStart(mergeId)
        return item
    }

    fun enqueueExtraTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        spec: DownloadExtraTaskSpec,
    ): DownloadItem {
        val item = addExtraTask(
            groupId = groupId,
            type = type,
            taskTitle = taskTitle,
            fileName = fileName,
            status = DownloadStatus.Pending,
        )
        extraTaskSpecs[item.id] = spec
        schedulePersist()
        requestExtraTaskStart(item.id)
        return item
    }

    private fun addExtraTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        status: DownloadStatus,
        errorMessage: String? = null,
        localUri: String? = null,
        statusDetail: String? = null,
    ): DownloadItem {
        return addExtraTaskInternal(
            groupId = groupId,
            type = type,
            taskTitle = taskTitle,
            fileName = fileName,
            status = status,
            errorMessage = errorMessage,
            localUri = localUri,
            statusDetail = statusDetail,
            parentTaskId = null,
        )!!
    }

    fun addUnavailableTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        reason: String,
        fileName: String = "",
    ): DownloadItem {
        return addExtraTaskInternal(
            groupId = groupId,
            type = type,
            taskTitle = taskTitle,
            fileName = fileName,
            status = DownloadStatus.Unavailable,
            errorMessage = null,
            localUri = null,
            statusDetail = reason,
            parentTaskId = null,
        )!!
    }

    // 动态发现的附加任务必须与删除流程同锁校验，避免已删除的任务组被重新创建。
    private fun addExtraTaskIfParentActive(
        parentTaskId: Long,
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        status: DownloadStatus,
        errorMessage: String? = null,
        localUri: String? = null,
        statusDetail: String? = null,
    ): DownloadItem? {
        return addExtraTaskInternal(
            groupId = groupId,
            type = type,
            taskTitle = taskTitle,
            fileName = fileName,
            status = status,
            errorMessage = errorMessage,
            localUri = localUri,
            statusDetail = statusDetail,
            parentTaskId = parentTaskId,
        )
    }

    private fun addExtraTaskInternal(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        status: DownloadStatus,
        errorMessage: String?,
        localUri: String?,
        statusDetail: String?,
        parentTaskId: Long?,
    ): DownloadItem? {
        val item = synchronized(lock) {
            if (parentTaskId != null) {
                val parentTask = tasks[parentTaskId] ?: return@synchronized null
                val parentActive = parentTask.groupId == groupId &&
                    (parentTask.status == DownloadStatus.Pending ||
                        parentTask.status == DownloadStatus.Running)
                if (!parentActive ||
                    parentTaskId in deletingTaskIds ||
                    groupId in deletingGroupIds ||
                    groupInfo[groupId] == null ||
                    groupTaskIds[groupId] == null
                ) {
                    return@synchronized null
                }
            }

            val id = extraTaskIds.getAndDecrement()
            val progress = if (status.isResolvedWithoutFailure) 100 else 0
            val created = normalizeTask(
                buildItem(
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
                    statusDetail = statusDetail,
                ),
            )
            tasks[created.id] = created
            groupTaskIds.getOrPut(groupId) { mutableListOf() }.add(created.id)
            created
        } ?: return null
        updateGroups()
        schedulePersist()
        return item
    }

    private fun updateExtraTask(
        id: Long,
        status: DownloadStatus,
        progress: Int,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
        errorMessage: String? = null,
        localUri: String? = null,
        statusDetail: String? = null,
        progressIndeterminate: Boolean = false,
        userPaused: Boolean = false,
        statusMessage: DownloadMessage? = null,
        failureMessage: DownloadMessage? = null,
    ): Boolean {
        var updated = false
        var shouldPersist = false
        synchronized(lock) {
            val target = tasks[id] ?: return@synchronized
            if (isManagedTask(target)) return@synchronized
            val next = normalizeTask(
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
                    statusMessage = statusMessage,
                    failureMessage = failureMessage,
                    progressIndeterminate = progressIndeterminate,
                    userPaused = userPaused,
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

    private fun updateExtraTaskMetadata(
        id: Long,
        taskTitle: String,
        fileName: String,
    ): Boolean {
        var updated = false
        var shouldPersist = false
        synchronized(lock) {
            val target = tasks[id] ?: return@synchronized
            if (isManagedTask(target)) return@synchronized
            val next = normalizeTask(
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

    private fun requestManagedTaskStart(id: Long) {
        synchronized(schedulingLock) {
            val task = tasks[id] ?: return
            if (!isManagedTask(task) || task.status != DownloadStatus.Pending) return
            managedTaskQueue.enqueue(id)
            startReadyManagedTasks()
        }
    }

    private fun startReadyManagedTasks() {
        synchronized(schedulingLock) {
            var shouldCheckAgain: Boolean
            do {
                shouldCheckAgain = false
                managedTaskQueue.takeReady().forEach { slot ->
                    val id = slot.taskId
                    val task = tasks[id]
                    if (task == null ||
                        !isManagedTask(task) ||
                        task.status != DownloadStatus.Pending
                    ) {
                        managedTaskQueue.finish(slot)
                        shouldCheckAgain = true
                    } else if (mergeTasks[id] != null) {
                        startMergedDownloadsNow(mergeTasks.getValue(id), slot)
                    } else {
                        startDownloadNow(id, slot)
                    }
                }
            } while (shouldCheckAgain)
        }
    }

    private fun releaseManagedTaskSlot(slot: TaskConcurrencyQueue.TaskSlot) {
        synchronized(schedulingLock) {
            onManagedTaskSlotReleased(slot.taskId, managedTaskQueue.finish(slot))
        }
    }

    private fun releaseManagedTaskSlot(id: Long) {
        synchronized(schedulingLock) {
            onManagedTaskSlotReleased(id, managedTaskQueue.finishCurrent(id))
        }
    }

    private fun onManagedTaskSlotReleased(id: Long, released: Boolean) {
        if (!released) return
        val current = tasks[id]
        if (current != null && isManagedTask(current) && current.status == DownloadStatus.Pending) {
            // 失败状态刚展示时用户可能立即重试；旧执行结束后要保留这次重新入队请求。
            managedTaskQueue.enqueue(id)
        }
        startReadyManagedTasks()
    }

    private fun requestExtraTaskStart(id: Long) {
        synchronized(schedulingLock) {
            val task = tasks[id] ?: return
            if (isManagedTask(task) || task.status != DownloadStatus.Pending) return
            extraTaskQueue.enqueue(id)
            startReadyExtraTasks()
        }
    }

    private fun startReadyExtraTasks() {
        synchronized(schedulingLock) {
            var shouldCheckAgain: Boolean
            do {
                shouldCheckAgain = false
                extraTaskQueue.takeReady().forEach { slot ->
                    val id = slot.taskId
                    val task = tasks[id]
                    val spec = extraTaskSpecs[id]
                    if (task == null ||
                        isManagedTask(task) ||
                        task.status != DownloadStatus.Pending
                    ) {
                        extraTaskQueue.finish(slot)
                        shouldCheckAgain = true
                    } else if (spec == null) {
                        updateExtraTask(
                            id = id,
                            status = DownloadStatus.Failed,
                            progress = task.progress,
                            errorMessage = strings.get(
                                R.string.download_failure_retry_data_missing,
                            ),
                        )
                        extraTaskQueue.finish(slot)
                        shouldCheckAgain = true
                    } else {
                        startExtraTaskNow(task, spec, slot)
                    }
                }
            } while (shouldCheckAgain)
        }
    }

    private fun releaseExtraTaskSlot(slot: TaskConcurrencyQueue.TaskSlot) {
        synchronized(schedulingLock) {
            onExtraTaskSlotReleased(slot.taskId, extraTaskQueue.finish(slot))
        }
    }

    private fun releaseExtraTaskSlot(id: Long) {
        synchronized(schedulingLock) {
            onExtraTaskSlotReleased(id, extraTaskQueue.finishCurrent(id))
        }
    }

    private fun onExtraTaskSlotReleased(id: Long, released: Boolean) {
        if (!released) return
        val current = tasks[id]
        if (current != null && !isManagedTask(current) && current.status == DownloadStatus.Pending) {
            extraTaskQueue.enqueue(id)
        }
        startReadyExtraTasks()
    }

    private fun startExtraTaskNow(
        task: DownloadItem,
        spec: DownloadExtraTaskSpec,
        slot: TaskConcurrencyQueue.TaskSlot,
    ) {
        if (!extraTaskQueue.isCurrent(slot) || tasks[task.id]?.status != DownloadStatus.Pending) {
            releaseExtraTaskSlot(slot)
            return
        }
        val started = updateTaskIf(
            id = task.id,
            predicate = { current ->
                !isManagedTask(current) && current.status == DownloadStatus.Pending
            },
            transform = { current ->
                current.copy(
                    status = DownloadStatus.Running,
                    progress = 0,
                    downloadedBytes = 0,
                    totalBytes = 0,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    errorMessage = null,
                    localUri = null,
                    statusDetail = null,
                    progressIndeterminate = false,
                    userPaused = false,
                )
            },
        )
        if (!started) {
            releaseExtraTaskSlot(slot)
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!extraTaskQueue.isCurrent(slot) ||
                    tasks[task.id]?.status != DownloadStatus.Running
                ) {
                    return@launch
                }
                persistState()
                executeExtraTask(task.id, spec)
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                val current = tasks[task.id] ?: return@launch
                val message = extraTaskErrorMessage(current.title, err)
                Log.w(
                    TAG,
                    "[extra-task] failed, taskId=${task.id}, type=${task.taskType}, file=${current.fileName}",
                    err,
                )
                updateExtraTask(
                    id = task.id,
                    status = DownloadStatus.Failed,
                    progress = 0,
                    errorMessage = message.resolve(context),
                    failureMessage = message,
                )
            } finally {
                val currentJob = coroutineContext[Job]
                val ownsExecution = currentJob != null && extraJobs.remove(task.id, currentJob)
                if (ownsExecution) {
                    releaseExtraTaskSlot(slot)
                }
            }
        }
        extraJobs.put(task.id, job)?.cancel()
        job.start()
    }

    private suspend fun executeExtraTask(id: Long, spec: DownloadExtraTaskSpec) {
        val bytes = when (spec.operation) {
            DownloadExtraTaskOperation.StaticText -> spec.textContent
                ?.takeIf { it.isNotBlank() }
                ?.toByteArray(Charsets.UTF_8)

            DownloadExtraTaskOperation.FetchBytes ->
                extrasRepository.fetchBytes(requireNotNull(spec.sourceUrl))

            DownloadExtraTaskOperation.SubtitleDiscovery ->
                executeSubtitleDiscovery(id, spec)

            DownloadExtraTaskOperation.AiSummary -> extrasRepository.getAiSummaryMarkdown(
                title = requireNotNull(spec.summaryTitle),
                bvid = requireNotNull(spec.bvid),
                aid = requireNotNull(spec.aid),
                cid = requireNotNull(spec.cid),
            )
                ?.takeIf { content -> content.isNotBlank() }
                ?.toByteArray(Charsets.UTF_8)

            DownloadExtraTaskOperation.DanmakuLive -> {
                val onProgress: (DanmakuLiveProgress) -> Unit = { progress ->
                    val segmentCount = progress.segmentCount.coerceAtLeast(1)
                    val statusMessage = when (progress.phase) {
                        DanmakuLiveProgressPhase.FetchingSegment -> DownloadMessage(
                            DownloadMessageCode.DetailFetchingDanmakuSegment,
                            countArguments = listOf(progress.segmentIndex.coerceIn(1, segmentCount), segmentCount),
                        )

                        DanmakuLiveProgressPhase.Converting ->
                            DownloadMessage(DownloadMessageCode.DetailConvertingDanmaku)
                    }
                    updateExtraTask(
                        id = id,
                        status = DownloadStatus.Running,
                        progress = progress.progress.coerceIn(0, 99),
                        statusDetail = statusMessage.resolve(context),
                        statusMessage = statusMessage,
                        progressIndeterminate =
                            progress.phase == DanmakuLiveProgressPhase.Converting,
                    )
                }
                if (spec.convertDanmakuToAss) {
                    extrasRepository.getDanmakuLiveAss(
                        requireNotNull(spec.aid),
                        requireNotNull(spec.cid),
                        requireNotNull(spec.durationSeconds),
                        onProgress,
                    )
                } else {
                    extrasRepository.getDanmakuLiveXml(
                        requireNotNull(spec.aid),
                        requireNotNull(spec.cid),
                        requireNotNull(spec.durationSeconds),
                        onProgress,
                    )
                }
            }

            DownloadExtraTaskOperation.DanmakuHistory -> if (spec.convertDanmakuToAss) {
                extrasRepository.getDanmakuHistoryAss(
                    requireNotNull(spec.cid),
                    requireNotNull(spec.date),
                    spec.hour,
                )
            } else {
                extrasRepository.getDanmakuHistoryXml(
                    requireNotNull(spec.cid),
                    requireNotNull(spec.date),
                    spec.hour,
                )
            }
        }

        if (bytes == null) {
            updateExtraTask(
                id = id,
                status = DownloadStatus.Unavailable,
                progress = 100,
                statusDetail = spec.unavailableMessage,
            )
            return
        }
        val current = tasks[id] ?: return
        if (!updateExtraTask(
                id = id,
                status = DownloadStatus.Running,
                progress = 99,
                statusDetail = strings.get(R.string.download_detail_saving_file),
            )
        ) {
            return
        }
        val uri = exportRepository.saveBytes(
            fileName = current.fileName,
            mimeType = spec.mimeType,
            bytes = bytes,
            relativePath = groupRelativePath(current.groupId),
        )
        if (uri == null) {
            updateExtraTask(
                id = id,
                status = DownloadStatus.Failed,
                progress = 0,
                errorMessage = strings.get(R.string.download_failure_save),
            )
            return
        }
        updateExtraTask(
            id = id,
            status = DownloadStatus.Success,
            progress = 100,
            downloadedBytes = bytes.size.toLong(),
            totalBytes = bytes.size.toLong(),
            localUri = uri.toString(),
        )
    }

    internal suspend fun executeSubtitleDiscovery(
        id: Long,
        spec: DownloadExtraTaskSpec,
    ): ByteArray? {
        val subtitles = extrasRepository.getSubtitles(
            aid = requireNotNull(spec.aid),
            cid = requireNotNull(spec.cid),
        )
        val plan = planSubtitleDiscovery(subtitles, spec)
        val first = plan.firstOrNull() ?: return null
        val baseFileName = requireNotNull(spec.subtitleBaseFileName)
        val taskTitle = requireNotNull(spec.subtitleTaskTitle)
        val firstFileName = NamingRenderer.appendExtension(
            baseName = baseFileName,
            extension = "${first.subtitle.lan}.srt",
            cleanSeparators = spec.cleanFileNameSeparators,
        )
        if (!updateExtraTaskMetadata(id, "$taskTitle - ${first.subtitle.displayName}", firstFileName)) {
            throw CancellationException("Subtitle task is no longer active")
        }
        plan.drop(1).forEach { task ->
            if (!enqueueSubtitleTaskIfAbsent(id, task)) {
                throw CancellationException("Subtitle task is no longer active")
            }
        }
        // 所有子任务登记后，父任务也固定为首个语言。重试只刷新该语言的 URL，
        // 防止列表顺序变化或首语言消失时重复下载其他子任务的字幕。
        val active = synchronized(lock) {
            val current = tasks[id]
            if (current?.status == DownloadStatus.Pending || current?.status == DownloadStatus.Running) {
                extraTaskSpecs[id] = first.retrySpec
                true
            } else false
        }
        if (!active) throw CancellationException("Subtitle task is no longer active")
        schedulePersist()
        if (!first.available) return null
        return extrasRepository.getSubtitleSrt(first.subtitle).takeIf { it.isNotEmpty() }
    }

    private fun enqueueSubtitleTaskIfAbsent(
        parentTaskId: Long,
        plannedTask: SubtitleDiscoveryTask,
    ): Boolean {
        val parent = tasks[parentTaskId] ?: return false
        val subtitle = plannedTask.subtitle
        val parentSpec = plannedTask.retrySpec
        val baseFileName = requireNotNull(parentSpec.subtitleBaseFileName)
        val taskTitle = requireNotNull(parentSpec.subtitleTaskTitle)
        val fileName = NamingRenderer.appendExtension(
            baseName = baseFileName,
            extension = "${subtitle.lan}.srt",
            cleanSeparators = parentSpec.cleanFileNameSeparators,
        )
        val candidateKey = parentSpec.subtitleTaskKeyFor(subtitle)
        val duplicateExists = synchronized(lock) {
            groupTaskIds[parent.groupId].orEmpty().any { taskId ->
                val existingTask = tasks[taskId]
                existingTask?.taskType == DownloadTaskType.Subtitle &&
                    (existingTask.fileName == fileName ||
                        candidateKey != null &&
                        extraTaskSpecs[taskId]?.subtitleTaskKey() == candidateKey)
            }
        }
        if (duplicateExists) return true

        val task = addExtraTaskIfParentActive(
            parentTaskId = parentTaskId,
            groupId = parent.groupId,
            type = DownloadTaskType.Subtitle,
            taskTitle = "$taskTitle - ${subtitle.displayName}",
            fileName = fileName,
            status = if (plannedTask.available) DownloadStatus.Pending else DownloadStatus.Unavailable,
            statusDetail = parentSpec.unavailableMessage.takeUnless { plannedTask.available },
        ) ?: return false
        extraTaskSpecs[task.id] = plannedTask.retrySpec
        schedulePersist()
        if (plannedTask.available) requestExtraTaskStart(task.id)
        return true
    }

    private fun extraTaskErrorMessage(taskTitle: String, err: Throwable?): DownloadMessage {
        val detail = when (err) {
            null -> null
            is BiliHttpException -> {
                val base = err.message?.takeIf { it.isNotBlank() }
                    ?: strings.get(R.string.parse_error_failed)
                "$base (${err.code})"
            }

            else -> err.message
        }?.takeIf { it.isNotBlank() }
            ?: strings.get(R.string.download_reason_unknown)
        return DownloadMessage(DownloadMessageCode.FailureExtra, textArguments = listOf(taskTitle, detail))
    }

    fun pause(id: Long) {
        synchronized(schedulingLock) {
            if (pauseLocked(id)) {
                persistStateImmediately()
            }
        }
    }

    private fun pauseLocked(id: Long): Boolean {
        val target = tasks[id] ?: return false
        if (!isManagedTask(target)) {
            val paused = updateTaskIf(
                id = id,
                predicate = { current ->
                    !isManagedTask(current) && current.status == DownloadStatus.Pending
                },
                transform = { current ->
                    current.copy(
                        status = DownloadStatus.Paused,
                        progress = 0,
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                        userPaused = true,
                        errorMessage = null,
                        statusDetail = null,
                        progressIndeterminate = false,
                    )
                },
            )
            if (!paused) return false
            extraTaskQueue.pausePending(id)
            releaseExtraTaskSlot(id)
            return true
        }
        val mergeTask = mergeTasks[id]
        if (mergeTask != null) {
            return pauseMerged(mergeTask)
        }
        val paused = updateTaskIf(
            id = id,
            predicate = { current ->
                isManagedTask(current) &&
                    (current.status == DownloadStatus.Pending ||
                        current.status == DownloadStatus.Running ||
                        current.status == DownloadStatus.Merging)
            },
            transform = { current ->
                current.copy(
                    status = DownloadStatus.Paused,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = true,
                    statusDetail = null,
                )
            },
        )
        if (!paused) return false
        managedTaskQueue.pausePending(id)
        downloadJobs.remove(id)?.cancel()
        releaseManagedTaskSlot(id)
        return true
    }

    fun resume(id: Long) {
        synchronized(schedulingLock) {
            resumeLocked(id)
        }
    }

    private fun resumeLocked(id: Long) {
        val target = tasks[id] ?: return
        if (target.status != DownloadStatus.Paused || !target.userPaused) return
        if (!isManagedTask(target)) {
            if (extraTaskSpecs[id] == null) {
                updateExtraTask(
                    id = id,
                    status = DownloadStatus.Failed,
                    progress = target.progress,
                    errorMessage = strings.get(R.string.download_failure_retry_data_missing),
                )
                return
            }
            val queued = updateTaskIf(
                id = id,
                predicate = { current ->
                    !isManagedTask(current) &&
                        current.status == DownloadStatus.Paused && current.userPaused
                },
                transform = { current ->
                    current.copy(
                        status = DownloadStatus.Pending,
                        progress = 0,
                        downloadedBytes = 0,
                        totalBytes = 0,
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                        errorMessage = null,
                        localUri = null,
                        statusDetail = null,
                        progressIndeterminate = false,
                        userPaused = false,
                    )
                },
            )
            if (!queued) return
            requestExtraTaskStart(id)
            return
        }
        val mergeTask = mergeTasks[id]
        if (mergeTask != null) {
            resumeMerged(mergeTask)
            return
        }
        val queued = updateTaskIf(
            id = id,
            predicate = { current ->
                isManagedTask(current) &&
                    current.status == DownloadStatus.Paused && current.userPaused
            },
            transform = { current ->
                current.copy(
                    status = DownloadStatus.Pending,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = false,
                    errorMessage = null,
                    statusDetail = null,
                )
            },
        )
        if (!queued) return
        requestManagedTaskStart(id)
    }

    fun retry(id: Long) {
        synchronized(schedulingLock) {
            retryLocked(id)
        }
    }

    private fun retryLocked(id: Long) {
        val target = tasks[id] ?: return
        if (target.status != DownloadStatus.Failed) return
        if (!isManagedTask(target)) {
            if (extraTaskSpecs[id] == null) {
                updateExtraTask(
                    id = id,
                    status = DownloadStatus.Failed,
                    progress = target.progress,
                    errorMessage = strings.get(R.string.download_failure_retry_data_missing),
                )
                return
            }
            val queued = updateTaskIf(
                id = id,
                predicate = { current ->
                    !isManagedTask(current) && current.status == DownloadStatus.Failed
                },
                transform = { current ->
                    current.copy(
                        status = DownloadStatus.Pending,
                        progress = 0,
                        downloadedBytes = 0,
                        totalBytes = 0,
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                        errorMessage = null,
                        localUri = null,
                        statusDetail = null,
                        progressIndeterminate = false,
                        userPaused = false,
                    )
                },
            )
            if (!queued) return
            requestExtraTaskStart(id)
            return
        }
        val mergeTask = mergeTasks[id]
        if (mergeTask != null) {
            if (!prepareMergedTaskForQueue(mergeTask)) return
            requestManagedTaskStart(id)
            return
        }
        val queued = updateTaskIf(
            id = id,
            predicate = { current ->
                isManagedTask(current) && current.status == DownloadStatus.Failed
            },
            beforeUpdate = {
                managedRetryTaskIds.add(id)
            },
            transform = { current ->
                current.copy(
                    status = DownloadStatus.Pending,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = false,
                    errorMessage = null,
                    statusDetail = null,
                )
            },
        )
        if (!queued) return
        requestManagedTaskStart(id)
    }

    fun pauseGroup(groupId: Long) {
        synchronized(schedulingLock) {
            val ids = synchronized(lock) { groupTaskIds[groupId]?.toList().orEmpty() }
            var changed = false
            ids.mapNotNull { tasks[it] }
                .filter { item ->
                    when {
                        isManagedTask(item) -> item.status == DownloadStatus.Pending ||
                            item.status == DownloadStatus.Running ||
                            item.status == DownloadStatus.Merging
                        else -> item.status == DownloadStatus.Pending
                    }
                }
                .sortedBy { item -> if (item.status == DownloadStatus.Pending) 0 else 1 }
                .forEach { item ->
                    changed = pauseLocked(item.id) || changed
                }
            if (changed) {
                persistStateImmediately()
            }
        }
    }

    fun resumeGroup(groupId: Long) {
        synchronized(schedulingLock) {
            val ids = synchronized(lock) { groupTaskIds[groupId]?.toList().orEmpty() }
            ids.mapNotNull { tasks[it] }
                .filter { item ->
                    item.status == DownloadStatus.Paused && item.userPaused
                }
                .forEach { resumeLocked(it.id) }
        }
    }

    fun pauseAll() {
        synchronized(schedulingLock) {
            val ids = synchronized(lock) {
                tasks.values
                    .filter { item ->
                        when {
                            isManagedTask(item) -> item.status == DownloadStatus.Pending ||
                                item.status == DownloadStatus.Running ||
                                item.status == DownloadStatus.Merging
                            else -> item.status == DownloadStatus.Pending
                        }
                    }
                    .map { it.id }
            }
            var changed = false
            ids.sortedBy { id -> if (tasks[id]?.status == DownloadStatus.Pending) 0 else 1 }
                .forEach { id ->
                    changed = pauseLocked(id) || changed
                }
            if (changed) {
                persistStateImmediately()
            }
        }
    }

    fun startAll() {
        synchronized(schedulingLock) {
            val snapshot = synchronized(lock) {
                tasks.values
                    .filter { item ->
                        (item.status == DownloadStatus.Paused && item.userPaused) ||
                            item.status == DownloadStatus.Failed
                    }
                    .sortedWith(compareBy<DownloadItem>({ it.createdAt }, { it.id }))
            }
            snapshot.forEach { item ->
                when (item.status) {
                    DownloadStatus.Paused -> resumeLocked(item.id)
                    DownloadStatus.Failed -> retryLocked(item.id)
                    else -> Unit
                }
            }
        }
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
        val (relativePath, items) = synchronized(lock) {
            deletingGroupIds.add(groupId)
            val path = groupInfo[groupId]?.relativePath
            val groupItems = groupTaskIds[groupId].orEmpty().mapNotNull { tasks[it] }
            path to groupItems
        }
        try {
            if (items.isNotEmpty()) {
                deleteTasks(items, deleteFile)
            } else {
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
        } finally {
            synchronized(lock) {
                deletingGroupIds.remove(groupId)
            }
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

    private fun startDownloadNow(id: Long, slot: TaskConcurrencyQueue.TaskSlot) {
        if (!managedTaskQueue.isCurrent(slot)) {
            releaseManagedTaskSlot(slot)
            return
        }
        val started = updateTaskIf(
            id = id,
            predicate = { current ->
                isManagedTask(current) && current.status == DownloadStatus.Pending
            },
            transform = { current ->
                current.copy(
                    status = DownloadStatus.Running,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = false,
                    errorMessage = null,
                    statusDetail = null,
                )
            },
        )
        if (!started) {
            releaseManagedTaskSlot(slot)
            return
        }
        DownloadForegroundService.requestSync(context)
        downloadJobs.remove(id)?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var retrying = false
            try {
                if (!managedTaskQueue.isCurrent(slot) ||
                    tasks[id]?.status != DownloadStatus.Running
                ) {
                    return@launch
                }
                retrying = id in managedRetryTaskIds
                persistState()
                val state = if (retrying) {
                    prepareManagedRetry(id)?.also {
                        managedRetryTaskIds.remove(id)
                    }
                } else {
                    downloadStates[id]
                } ?: error(strings.get(R.string.download_failure_resume_data_missing))
                runResumableDownload(id, state)
            } catch (err: CancellationException) {
                // user pause/cancel
                if (retrying && tasks[id]?.status == DownloadStatus.Paused) {
                    managedRetryTaskIds.add(id)
                }
            } catch (err: Exception) {
                handleDownloadFailure(id, err)
            } finally {
                val currentJob = coroutineContext[Job]
                val ownsExecution = currentJob != null && downloadJobs.remove(id, currentJob)
                if (ownsExecution) {
                    releaseManagedTaskSlot(slot)
                }
            }
        }
        downloadJobs.put(id, job)?.cancel()
        job.start()
    }

    private suspend fun runResumableDownload(id: Long, state: ResumableState) {
        val startItem = tasks[id] ?: return
        resumableDownloader.download(
            target = state,
            refreshSource = { tasks[id]?.let { refreshManagedSourceIfPossible(it) } },
            onFailure = { url, attempt, failure -> logSourceFailure(id, "media", state, url, attempt, failure) },
        ) { downloaded, total, speed, eta ->
            updateTaskIf(
                id = id,
                predicate = { current ->
                    current.status == DownloadStatus.Running && !current.userPaused
                },
                transform = { current ->
                    current.copy(
                        status = DownloadStatus.Running,
                        progress = calculateProgress(downloaded, total, current.progress),
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedBytesPerSec = speed,
                        etaSeconds = eta,
                        userPaused = false,
                        errorMessage = null,
                    )
                },
            )
        }
        currentCoroutineContext().ensureActive()
        val finalizedSource = prepareFinalTempFile(state.id, state.fileName, state.tempFile)
        val processedTemp = try {
            prepareDownloadedMedia(
                item = startItem,
                inputFile = finalizedSource,
                conversionTarget = state.conversionTarget,
            )
        } catch (err: Throwable) {
            if (finalizedSource != state.tempFile) {
                runCatching { finalizedSource.delete() }
            }
            throw err
        }
        currentCoroutineContext().ensureActive()
        tasks[id]?.let { item ->
            applyEmbeddedContentIfPossible(item, processedTemp)
        }
        currentCoroutineContext().ensureActive()
        val relativePath = groupRelativePath(startItem.groupId)
        Log.d(
            TAG,
            "[save-chain] start save managed download, taskId=$id, groupId=${startItem.groupId}, file=${startItem.fileName}, temp=${processedTemp.absolutePath}, tempExists=${processedTemp.exists()}, tempSize=${processedTemp.length()}, relativePath=$relativePath",
        )
        // 新下载只能认可本次保存的 URI；同名旧文件可能没有本次选择的字幕或歌词。
        val uri = saveToDownloads(processedTemp, startItem.fileName, relativePath)
        Log.d(
            TAG,
            "[save-chain] save resolved, taskId=$id, file=${startItem.fileName}, resolvedUri=$uri",
        )
        if (uri != null && processedTemp != finalizedSource) {
            runCatching { finalizedSource.delete() }
        }
        if (uri != null && finalizedSource != state.tempFile) {
            runCatching { state.tempFile.delete() }
        }
        val current = tasks[id] ?: return
        if (uri != null) {
            updateTask(
                current.copy(
                    status = DownloadStatus.Success,
                    progress = 100,
                    downloadedBytes = state.downloadedBytes,
                    totalBytes = state.totalBytes,
                    outputBytes = readOutputSize(Uri.parse(uri)),
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    localUri = uri,
                    userPaused = false,
                    errorMessage = null,
                    statusDetail = null,
                ),
            )
            downloadStates.remove(id)
            persistState()
            Log.i(
                TAG,
                "[save-chain] managed download marked success, taskId=$id, file=${startItem.fileName}, localUri=$uri",
            )
        } else {
            updateTask(
                current.copy(
                    status = DownloadStatus.Failed,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = false,
                    errorMessage = strings.get(R.string.download_failure_save),
                    statusDetail = null,
                ),
            )
            schedulePersist()
            Log.e(
                TAG,
                "[save-chain] managed download failed to save output, taskId=$id, file=${startItem.fileName}, groupId=${startItem.groupId}",
            )
        }
    }

    private suspend fun prepareDownloadedMedia(
        item: DownloadItem,
        inputFile: File,
        conversionTarget: MediaConversionTarget?,
    ): File {
        // 文件封装属于下载的基本步骤，不依赖元数据或歌词开关。
        if (conversionTarget == null && item.taskType != DownloadTaskType.Audio && item.taskType != DownloadTaskType.Video) {
            return inputFile
        }

        val outputFile = processingTempFileFor(item.id, item.fileName)
        runCatching { outputFile.delete() }
        val statusDetail = when (conversionTarget) {
            MediaConversionTarget.MP3 ->
                strings.get(R.string.download_detail_converting_audio)
            MediaConversionTarget.MP4 ->
                strings.get(R.string.download_detail_converting_video)
            null -> strings.get(R.string.download_detail_preparing_media)
        }
        tasks[item.id]?.let { current ->
            updateTask(
                current.copy(
                    status = DownloadStatus.Merging,
                    progress = 99,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    statusDetail = statusDetail,
                ),
            )
        }

        try {
            when (conversionTarget) {
                MediaConversionTarget.MP3 ->
                    MediaProcessingEngine.convertAudioToMp3(inputFile, outputFile)
                MediaConversionTarget.MP4 ->
                    MediaProcessingEngine.convertVideoToMp4(inputFile, outputFile)
                null -> if (item.taskType == DownloadTaskType.Audio) {
                    MediaProcessingEngine.remuxAudio(inputFile, outputFile)
                } else {
                    MediaProcessingEngine.convertVideoToMp4(inputFile, outputFile)
                }
            }
        } catch (err: CancellationException) {
            runCatching { outputFile.delete() }
            throw err
        } catch (err: Throwable) {
            runCatching { outputFile.delete() }
            val message = when (conversionTarget) {
                MediaConversionTarget.MP3 ->
                    strings.get(R.string.download_failure_convert_audio)
                MediaConversionTarget.MP4 ->
                    strings.get(R.string.download_failure_convert_video)
                null -> strings.get(R.string.download_failure_prepare_media)
            }
            throw IllegalStateException(message, err)
        }
        return outputFile
    }

    private fun handleDownloadFailure(id: Long, err: Throwable) {
        val state = downloadStates[id]
        updateTaskIf(
            id = id,
            predicate = { current ->
                !current.userPaused &&
                    (current.status == DownloadStatus.Running ||
                        current.status == DownloadStatus.Merging)
            },
            transform = { current ->
                val downloaded = state?.downloadedBytes ?: current.downloadedBytes
                val total = state?.totalBytes ?: current.totalBytes
                current.copy(
                    status = DownloadStatus.Failed,
                    progress = calculateProgress(downloaded, total, current.progress),
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = false,
                    errorMessage = err.message?.takeIf { it.isNotBlank() }
                        ?: strings.get(R.string.download_failure_download_unknown),
                    statusDetail = null,
                )
            },
        )
        schedulePersist()
    }

    private fun logSourceFailure(
        id: Long,
        part: String,
        target: ResumableDownloadTarget,
        url: String,
        attempt: Int,
        failure: DownloadSourceFailure,
    ) {
        val params = tasks[id]?.mediaParams
        Log.w(
            TAG,
            "[download-source] taskId=$id, part=$part, attempt=$attempt, " +
                "host=${url.toHttpUrlOrNull()?.host}, stage=${failure.stage}, " +
                "offset=${target.tempFile.length()}, quality=${params?.resolutionId}, " +
                "codec=${params?.codecType}, audio=${params?.audioQualityId}",
            failure,
        )
    }

    private suspend fun prepareManagedRetry(id: Long): ResumableState? {
        val target = tasks[id] ?: return null
        if (!isManagedTask(target)) return null
        val refreshedSource = refreshManagedSourceIfPossible(target)
        val latest = tasks[id] ?: target
        val existingState = downloadStates[id]
        val sourceFileName = existingState?.fileName ?: latest.fileName
        val tempFile = existingState?.tempFile ?: tempFileFor(id, sourceFileName)
        val existing = if (tempFile.exists()) tempFile.length() else 0L
        val state = existingState ?: ResumableState(
            id = id,
            source = refreshedSource ?: DownloadSource(latest.url),
            fileName = sourceFileName,
            tempFile = tempFile,
            downloadedBytes = existing,
            totalBytes = latest.totalBytes,
        )
        if (refreshedSource != null) state.source = refreshedSource
        state.downloadedBytes = existing
        if (state.totalBytes <= 0 && latest.totalBytes > 0) {
            state.totalBytes = latest.totalBytes
        }
        downloadStates[id] = state
        return state
    }

    private suspend fun refreshManagedSourceIfPossible(item: DownloadItem): DownloadSource? {
        val source = resolveManagedRetrySource(item) ?: return null
        currentCoroutineContext().ensureActive()
        val updated = updateTaskIf(
            id = item.id,
            predicate = { it.status == DownloadStatus.Running && !it.userPaused },
            transform = { it.copy(url = source.url) },
        )
        if (!updated) return null
        downloadStates[item.id]?.source = source
        schedulePersist()
        Log.i(TAG, "[retry-refresh] managed sources refreshed, taskId=${item.id}, candidates=${source.orderedUrls().size}")
        return source
    }

    private suspend fun refreshMergedSourcesIfPossible(id: Long, task: MergedDownload) {
        val sources = resolveMergedRetrySources(id) ?: return
        currentCoroutineContext().ensureActive()
        val updated = updateTaskIf(
            id = id,
            predicate = { it.status == DownloadStatus.Running && !it.userPaused },
            transform = { it.copy(url = sources.video.url) },
        )
        if (!updated) return
        task.video.source = sources.video
        task.audio.source = sources.audio
        schedulePersist()
        Log.i(TAG, "[retry-refresh] merged sources refreshed, taskId=$id")
    }

    private suspend fun resolveManagedRetrySource(item: DownloadItem): DownloadSource? {
        if (item.taskType == DownloadTaskType.OpusImage) return null
        val resolved = resolveRetrySource(item) ?: return null
        return when (item.taskType) {
            DownloadTaskType.Audio -> selectAudioStreamForRetry(
                resolved.playUrlInfo.audio, item.mediaParams,
            )?.downloadSource()
            DownloadTaskType.Video, DownloadTaskType.AudioVideo -> selectVideoStreamForRetry(
                resolved.playUrlInfo.video, item.mediaParams,
            )?.downloadSource()
            else -> null
        }
    }

    private suspend fun resolveMergedRetrySources(id: Long): RefreshedMergeSources? {
        val item = tasks[id] ?: return null
        val resolved = resolveRetrySource(item, formatOverride = StreamFormat.Dash) ?: return null
        val video = selectVideoStreamForRetry(resolved.playUrlInfo.video, item.mediaParams) ?: return null
        val audio = selectAudioStreamForRetry(resolved.playUrlInfo.audio, item.mediaParams) ?: return null
        return RefreshedMergeSources(video.downloadSource(), audio.downloadSource())
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
                currentCoroutineContext().ensureActive()
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
                currentCoroutineContext().ensureActive()
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
                currentCoroutineContext().ensureActive()
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
        meta?.subtitleCid?.let { cid ->
            return candidates.firstOrNull { it.cid == cid }
        }
        val trackNumber = meta?.trackNumber
        if (trackNumber != null && info.type == MediaType.Video) {
            return candidates.firstOrNull { (it.page ?: (it.index + 1)) == trackNumber }
        }
        meta?.originalUrl?.let { url ->
            candidates.firstOrNull { it.url == url }?.let { return it }
        }

        val metaTitle = meta?.title?.trim().orEmpty()
        if (metaTitle.isNotBlank()) {
            candidates.firstOrNull { candidate ->
                candidate.title.trim() == metaTitle
            }?.let { return it }
        }

        val groupContentId = groupInfo[item.groupId]?.bvid?.trim().orEmpty()
        if (groupContentId.isNotBlank()) {
            candidates.singleOrNull { candidate ->
                candidate.displayContentId() == groupContentId
            }?.let { return it }
        }

        return candidates.singleOrNull { it.isTarget } ?: candidates.singleOrNull()
    }

    private fun inferRetryStreamFormat(item: DownloadItem): StreamFormat {
        if (item.taskType == DownloadTaskType.Audio || item.taskType == DownloadTaskType.Video) {
            return StreamFormat.Dash
        }
        val sourceFileName = downloadStates[item.id]?.fileName ?: item.fileName
        return when (sourceFileName.substringAfterLast('.', "").lowercase(Locale.US)) {
            "flv" -> StreamFormat.Flv
            else -> StreamFormat.Mp4
        }
    }

    private fun selectVideoStreamForRetry(
        streams: List<VideoStream>,
        params: DownloadMediaParams?,
    ): VideoStream? {
        params ?: return null
        // 新任务使用稳定 ID；旧任务只在原文案仍能准确匹配时刷新，绝不降级到另一条流续传。
        val resolutionId = params.resolutionId ?: streams.firstOrNull {
            mapResolutionLabel(it) == params.resolution
        }?.id ?: return null
        val codec = params.codecType ?: parseCodecLabel(params.codec)
        return selectRetryVideoStream(streams, resolutionId, codec)
    }

    private fun selectAudioStreamForRetry(
        streams: List<AudioStream>,
        params: DownloadMediaParams?,
    ): AudioStream? {
        params ?: return null
        val qualityId = params.audioQualityId ?: streams.firstOrNull {
            mapAudioLabel(it.id) == params.audioBitrate
        }?.id ?: return null
        return selectRetryAudioStream(streams, qualityId)
    }

    private fun parseCodecLabel(label: String?): VideoCodec? {
        val normalized = label?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return when (normalized) {
            strings.get(R.string.parse_codec_avc) -> VideoCodec.Avc
            strings.get(R.string.parse_codec_hevc) -> VideoCodec.Hevc
            strings.get(R.string.parse_codec_av1) -> VideoCodec.Av1
            else -> null
        }
    }

    private fun mapResolutionLabel(stream: VideoStream): String {
        return mapResolutionLabel(stream.id, stream.height)
    }

    private fun mapResolutionLabel(id: Int, height: Int?): String {
        return when (id) {
            127 -> strings.get(R.string.parse_resolution_8k)
            126 -> strings.get(R.string.parse_resolution_dolby)
            125 -> strings.get(R.string.parse_resolution_hdr)
            120 -> strings.get(R.string.parse_resolution_4k)
            116 -> strings.get(R.string.parse_resolution_1080_60)
            112 -> strings.get(R.string.parse_resolution_1080_high)
            80 -> strings.get(R.string.parse_resolution_1080)
            64 -> strings.get(R.string.parse_resolution_720)
            32 -> strings.get(R.string.parse_resolution_480)
            16 -> strings.get(R.string.parse_resolution_360)
            6 -> strings.get(R.string.parse_resolution_240)
            else -> {
                val resolvedHeight = height ?: 0
                when {
                    resolvedHeight >= 4320 -> strings.get(R.string.parse_resolution_8k)
                    resolvedHeight >= 2160 -> strings.get(R.string.parse_resolution_4k)
                    resolvedHeight >= 1080 -> strings.get(R.string.parse_resolution_1080)
                    resolvedHeight >= 720 -> strings.get(R.string.parse_resolution_720)
                    resolvedHeight >= 480 -> strings.get(R.string.parse_resolution_480)
                    resolvedHeight >= 360 -> strings.get(R.string.parse_resolution_360)
                    else -> strings.get(R.string.parse_resolution_other)
                }
            }
        }
    }

    private fun mapAudioLabel(id: Int): String {
        return strings.get(AudioQualities.labelRes(id))
    }

    private fun startMergedDownloadsNow(
        task: MergedDownload,
        slot: TaskConcurrencyQueue.TaskSlot,
    ) {
        if (task.userPaused || task.failed || task.completed) {
            managedTaskQueue.finish(slot)
            startReadyManagedTasks()
            return
        }
        if (!managedTaskQueue.isCurrent(slot)) {
            releaseManagedTaskSlot(slot)
            return
        }
        val started = updateTaskIf(
            id = task.id,
            predicate = { current ->
                isManagedTask(current) && current.status == DownloadStatus.Pending
            },
            transform = { current ->
                current.copy(
                    status = DownloadStatus.Running,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = false,
                    errorMessage = null,
                    statusDetail = null,
                )
            },
        )
        if (!started) {
            releaseManagedTaskSlot(slot)
            return
        }
        DownloadForegroundService.requestSync(context)
        val coordinator = scope.launch(start = CoroutineStart.LAZY) {
            var retrying = false
            try {
                if (!managedTaskQueue.isCurrent(slot) ||
                    tasks[task.id]?.status != DownloadStatus.Running
                ) {
                    return@launch
                }
                retrying = task.id in managedRetryTaskIds
                persistState()
                if (retrying) {
                    refreshMergedSourcesIfPossible(task.id, task)
                    prepareMergedTaskForRun(task)
                    managedRetryTaskIds.remove(task.id)
                }
                if (tasks[task.id]?.status != DownloadStatus.Running) return@launch
                startMergedTransfers(task, slot)
            } catch (err: CancellationException) {
                if (retrying && tasks[task.id]?.status == DownloadStatus.Paused) {
                    managedRetryTaskIds.add(task.id)
                }
                throw err
            } catch (err: Throwable) {
                task.failed = true
                updateMergedProgress(
                    task = task,
                    force = true,
                    errorMessage = err.message?.takeIf { it.isNotBlank() }
                        ?: strings.get(R.string.download_failure_download_unknown),
                )
                releaseManagedTaskSlot(slot)
            } finally {
                val currentJob = coroutineContext[Job]
                if (currentJob != null) {
                    downloadJobs.remove(task.id, currentJob)
                }
            }
        }
        downloadJobs.put(task.id, coordinator)?.cancel()
        coordinator.start()
    }

    private fun startMergedTransfers(
        task: MergedDownload,
        slot: TaskConcurrencyQueue.TaskSlot,
    ) {
        synchronized(schedulingLock) {
            if (!managedTaskQueue.isCurrent(slot) ||
                tasks[task.id]?.status != DownloadStatus.Running ||
                task.userPaused || task.failed || task.completed
            ) {
                return
            }
            if (task.video.completed && task.audio.completed) {
                startMerge(task, slot)
                return
            }
            // 两条流可能同时耗尽候选；本次任务运行只刷新一次播放接口，互不改写在途分任务。
            val refreshMutex = Mutex()
            var refreshAttempted = false
            var refreshedSources: RefreshedMergeSources? = null
            suspend fun refreshSources(): RefreshedMergeSources? = refreshMutex.withLock {
                if (!refreshAttempted) {
                    refreshedSources = resolveMergedRetrySources(task.id)
                    currentCoroutineContext().ensureActive()
                    refreshAttempted = true
                }
                refreshedSources
            }
            startPartDownload(task, task.video, slot) { refreshSources()?.video }
            startPartDownload(task, task.audio, slot) { refreshSources()?.audio }
            updateMergedProgress(task, true, null)
        }
    }

    private fun startPartDownload(
        task: MergedDownload,
        part: ResumablePart,
        slot: TaskConcurrencyQueue.TaskSlot,
        refreshSource: suspend () -> DownloadSource?,
    ) {
        if (part.completed || part.job?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!managedTaskQueue.isCurrent(slot) ||
                    tasks[task.id]?.status != DownloadStatus.Running
                ) {
                    return@launch
                }
                resumableDownloader.download(
                    target = part,
                    refreshSource = refreshSource,
                    onFailure = { url, attempt, failure ->
                        logSourceFailure(task.id, if (part === task.video) "video" else "audio", part, url, attempt, failure)
                    },
                ) { _, _, _, _ ->
                    updateMergedProgress(task, false, null)
                }
                currentCoroutineContext().ensureActive()
                part.completed = true
                part.speedBytesPerSec = 0
                schedulePersist()
                updateMergedProgress(task, true, null)
                if (task.video.completed && task.audio.completed) {
                    startMerge(task, slot)
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
                updateMergedProgress(
                    task,
                    true,
                    err.message?.takeIf { it.isNotBlank() }
                        ?: strings.get(R.string.download_failure_download_unknown),
                )
                releaseManagedTaskSlot(slot)
            }
        }
        part.job = job
        job.start()
    }

    private fun pauseMerged(task: MergedDownload): Boolean {
        task.userPaused = true
        val paused = updateTaskIf(
            id = task.id,
            predicate = { current ->
                isManagedTask(current) &&
                    (current.status == DownloadStatus.Pending ||
                        current.status == DownloadStatus.Running ||
                        current.status == DownloadStatus.Merging)
            },
            transform = { current ->
                current.copy(
                    status = DownloadStatus.Paused,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = true,
                    statusDetail = null,
                )
            },
        )
        if (!paused) {
            task.userPaused = false
            return false
        }
        managedTaskQueue.pausePending(task.id)
        task.video.job?.cancel()
        task.audio.job?.cancel()
        task.video.job = null
        task.audio.job = null
        task.video.speedBytesPerSec = 0
        task.audio.speedBytesPerSec = 0
        downloadJobs.remove(task.id)?.cancel()
        mergeJobs[task.id]?.cancel()
        if (mergeJobs[task.id]?.isActive != true) {
            task.isMerging = false
        }
        updateMergedProgress(task, true, null)
        releaseManagedTaskSlot(task.id)
        return true
    }

    private fun resumeMerged(task: MergedDownload) {
        if (!task.userPaused || task.failed || task.completed) return
        val total = task.video.totalBytes + task.audio.totalBytes
        val downloaded = task.video.downloadedBytes + task.audio.downloadedBytes
        val queued = updateTaskIf(
            id = task.id,
            predicate = { current ->
                isManagedTask(current) &&
                    current.status == DownloadStatus.Paused && current.userPaused
            },
            beforeUpdate = {
                task.userPaused = false
                task.isMerging = false
            },
            transform = { current ->
                current.copy(
                    status = DownloadStatus.Pending,
                    progress = calculateProgress(downloaded, total, current.progress),
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = false,
                    errorMessage = null,
                    statusDetail = null,
                )
            },
        )
        if (!queued) return
        requestManagedTaskStart(task.id)
    }

    private fun prepareMergedTaskForQueue(task: MergedDownload): Boolean {
        if (task.completed) return false
        val total = task.video.totalBytes + task.audio.totalBytes
        val downloaded = task.video.downloadedBytes + task.audio.downloadedBytes
        return updateTaskIf(
            id = task.id,
            predicate = { current ->
                isManagedTask(current) && current.status == DownloadStatus.Failed
            },
            beforeUpdate = {
                managedRetryTaskIds.add(task.id)
                task.failed = false
                task.userPaused = false
                task.isMerging = false
                task.video.job = null
                task.audio.job = null
                task.video.failed = false
                task.audio.failed = false
            },
            transform = { current ->
                current.copy(
                    status = DownloadStatus.Pending,
                    progress = calculateProgress(downloaded, total, current.progress),
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = false,
                    errorMessage = null,
                    statusDetail = null,
                )
            },
        )
    }

    private fun updateMergedTaskPending(task: MergedDownload) {
        val current = tasks[task.id] ?: return
        val total = task.video.totalBytes + task.audio.totalBytes
        val downloaded = task.video.downloadedBytes + task.audio.downloadedBytes
        updateTask(
            current.copy(
                status = DownloadStatus.Pending,
                progress = calculateProgress(downloaded, total, current.progress),
                downloadedBytes = downloaded,
                totalBytes = total,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = false,
                errorMessage = null,
                statusDetail = null,
            ),
        )
    }

    private fun prepareMergedTaskForRun(task: MergedDownload) {
        prepareMergedPartForRetry(task.video, "video/")
        prepareMergedPartForRetry(task.audio, "audio/")
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
        var downloadStateSnapshot = emptyList<ResumableState>()
        var taskSnapshot = emptyList<DownloadItem>()
        var mergeTaskSnapshot = emptyList<MergedDownload>()
        synchronized(schedulingLock) {
            synchronized(lock) {
                deletingGroupIds.addAll(groupInfo.keys)
                deletingTaskIds.addAll(tasks.keys)
                taskSnapshot = tasks.values.toList()
            }
            val downloadJobSnapshot = downloadJobs.values.toList()
            val mergeJobSnapshot = mergeJobs.values.toList()
            val extraJobSnapshot = extraJobs.values.toList()
            downloadJobs.clear()
            mergeJobs.clear()
            extraJobs.clear()
            managedTaskQueue.clear()
            extraTaskQueue.clear()
            managedRetryTaskIds.clear()
            extraTaskSpecs.clear()
            downloadJobSnapshot.forEach { it.cancel() }
            mergeJobSnapshot.forEach { it.cancel() }
            extraJobSnapshot.forEach { it.cancel() }

            downloadStateSnapshot = downloadStates.values.toList()
            downloadStates.clear()
            mergeTaskSnapshot = mergeTasks.values.toList()
            mergeTasks.clear()
            mergeTaskSnapshot.forEach { task ->
                task.video.job?.cancel()
                task.audio.job?.cancel()
                task.video.job = null
                task.audio.job = null
            }

            synchronized(lock) {
                groupInfo.clear()
                groupTaskIds.clear()
                tasks.clear()
                deletingGroupIds.clear()
                deletingTaskIds.clear()
            }
        }
        downloadStateSnapshot.forEach { state ->
            state.tempFile.delete()
            tempFilesForCleanup(state.id, state.fileName).forEach { it.delete() }
        }

        taskSnapshot.forEach { item ->
            tempFilesForCleanup(item.id, item.fileName).forEach { it.delete() }
        }

        mergeTaskSnapshot.forEach { task ->
            task.video.tempFile.delete()
            task.audio.tempFile.delete()
        }
        updateGroups()
        schedulePersist()
        startReadyManagedTasks()
        startReadyExtraTasks()
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
                    groupTasks.all { it.status.isResolvedWithoutFailure }) {
                    completedGroups.add(groupId)
                    taskIds.addAll(ids)
                }
            }
            taskIds.forEach { id ->
                tasks.remove(id)
                downloadJobs.remove(id)?.cancel()
                extraJobs.remove(id)?.cancel()
                managedTaskQueue.remove(id)
                extraTaskQueue.remove(id)
                managedRetryTaskIds.remove(id)
                extraTaskSpecs.remove(id)
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
        startReadyManagedTasks()
        startReadyExtraTasks()
    }

    private fun deleteTasks(items: List<DownloadItem>, deleteFile: Boolean) {
        if (items.isEmpty()) return
        val taskIds = items.mapTo(mutableSetOf()) { it.id }
        val groupIds = items.map { it.groupId }.distinct()
        synchronized(lock) {
            deletingTaskIds.addAll(taskIds)
        }
        try {
            synchronized(schedulingLock) {
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
            }
            updateGroups()
            schedulePersist()
            startReadyManagedTasks()
            startReadyExtraTasks()
        } finally {
            synchronized(lock) {
                deletingTaskIds.removeAll(taskIds)
            }
        }
    }

    private fun cleanupTaskResources(item: DownloadItem, deleteFile: Boolean) {
        downloadJobs.remove(item.id)?.cancel()
        extraJobs.remove(item.id)?.cancel()
        managedTaskQueue.remove(item.id)
        extraTaskQueue.remove(item.id)
        managedRetryTaskIds.remove(item.id)
        extraTaskSpecs.remove(item.id)
        val downloadState = downloadStates.remove(item.id)
        downloadState?.tempFile?.delete()
        downloadState?.let { state ->
            tempFilesForCleanup(item.id, state.fileName).forEach { it.delete() }
        }
        tempFilesForCleanup(item.id, item.fileName).forEach { it.delete() }
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
        val normalizedItem = normalizeTask(item)
        synchronized(lock) {
            tasks[normalizedItem.id] = normalizedItem
            val list = groupTaskIds.getOrPut(normalizedItem.groupId) { mutableListOf() }
            list.add(normalizedItem.id)
        }
        updateGroups()
        schedulePersist()
    }

    private fun normalizeTask(item: DownloadItem): DownloadItem = messageCatalog.capture(
        DownloadProgressRules.normalizeTask(item),
        previous = tasks[item.id],
    )

    private fun updateTask(item: DownloadItem) {
        val normalizedItem = normalizeTask(item)
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

    private fun updateTaskIf(
        id: Long,
        predicate: (DownloadItem) -> Boolean,
        beforeUpdate: (DownloadItem) -> Unit = {},
        transform: (DownloadItem) -> DownloadItem,
    ): Boolean {
        var updated = false
        var shouldPersist = false
        synchronized(lock) {
            val current = tasks[id] ?: return@synchronized
            if (!predicate(current)) return@synchronized
            beforeUpdate(current)
            val next = normalizeTask(transform(current))
            tasks[id] = next
            updated = true
            shouldPersist = shouldPersistTaskChange(current, next)
        }
        if (!updated) return false
        updateGroups()
        if (shouldPersist) {
            schedulePersist()
        }
        return true
    }

    private fun replaceTask(oldId: Long, newItem: DownloadItem) {
        val normalizedItem = normalizeTask(newItem)
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
        if (old.statusMessage != new.statusMessage || old.failureMessage != new.failureMessage) return true
        if (old.embeddingMessages != new.embeddingMessages) return true
        if (old.totalBytes != new.totalBytes && new.totalBytes > 0) return true
        if (old.outputBytes != new.outputBytes) return true
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
                    .filterNot { item -> item.status == DownloadStatus.Cancelled }
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
                    sourceMetadata = info.sourceMetadata,
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
                    allTasksResolved = activeTasks.all { item -> item.status.isResolvedWithoutFailure },
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
                synchronized(persistLock) {
                    persistJob = null
                }
                persistState()
            }
        }
    }

    private fun persistStateImmediately() {
        synchronized(persistLock) {
            persistJob?.cancel()
            persistJob = null
            writeStoreSnapshot()
        }
    }

    private fun persistState() {
        synchronized(persistLock) {
            writeStoreSnapshot()
        }
    }

    private fun writeStoreSnapshot() {
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
                    url = state.source.url,
                    backupUrls = state.source.backupUrls,
                    validatorUrl = state.validatorUrl,
                    fileName = state.fileName,
                    totalBytes = state.totalBytes,
                    etag = state.etag,
                    lastModified = state.lastModified,
                    conversionTarget = state.conversionTarget,
                )
            },
            mergeStates = mergeTasks.values.map { task ->
                MergedTaskSnapshot(
                    id = task.id,
                    outputName = task.outputName,
                    video = ResumablePartSnapshot(
                        url = task.video.source.url,
                        backupUrls = task.video.source.backupUrls,
                        validatorUrl = task.video.validatorUrl,
                        fileName = task.video.fileName,
                        totalBytes = task.video.totalBytes,
                        etag = task.video.etag,
                        lastModified = task.video.lastModified,
                        completed = task.video.completed,
                    ),
                    audio = ResumablePartSnapshot(
                        url = task.audio.source.url,
                        backupUrls = task.audio.source.backupUrls,
                        validatorUrl = task.audio.validatorUrl,
                        fileName = task.audio.fileName,
                        totalBytes = task.audio.totalBytes,
                        etag = task.audio.etag,
                        lastModified = task.audio.lastModified,
                        completed = task.audio.completed,
                    ),
                    conversionTarget = task.conversionTarget,
                )
            },
            extraTaskStates = extraTaskSpecs.map { (id, spec) ->
                ExtraTaskSnapshot(id = id, spec = spec)
            },
        )
    }

    private fun loadState() {
        val store = readStore() ?: return
        extraTaskSpecs.clear()
        val resumableById = store.resumableStates.associateBy { it.id }
        val mergeById = store.mergeStates.associateBy { it.id }
        val extraSpecById = store.extraTaskStates.associate { it.id to it.spec }
        val restoredGroupInfo = mutableMapOf<Long, GroupInfo>()
        val restoredGroupTaskIds = mutableMapOf<Long, MutableList<Long>>()
        val restoredTasks = mutableMapOf<Long, DownloadItem>()
        val restoredStates = mutableMapOf<Long, ResumableState>()
        val restoredMerges = mutableMapOf<Long, MergedDownload>()
        val completedTempFiles = mutableListOf<File>()
        var maxGroupId = 0L
        var maxDownloadId = 0L
        var minMergeId: Long? = null
        var minExtraId: Long? = null
        var restoredStateChanged = false

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
                group.sourceMetadata,
            )
            val ids = mutableListOf<Long>()
            for (task in group.tasks) {
                when {
                    task.id > 0 -> maxDownloadId = maxOf(maxDownloadId, task.id)
                    task.id <= EXTRA_TASK_ID_START -> {
                        minExtraId = minOf(minExtraId ?: task.id, task.id)
                    }
                    else -> minMergeId = minOf(minMergeId ?: task.id, task.id)
                }
                if (task.status == DownloadStatus.Cancelled) {
                    restoredStateChanged = true
                    continue
                }
                ids.add(task.id)

                if (task.taskType == DownloadTaskType.AudioVideo &&
                    task.id in (EXTRA_TASK_ID_START + 1)..-1L
                ) {
                    val restored = restoreMergedTask(task, mergeById[task.id])
                    restoredTasks[task.id] = restored.item
                    if (restored.item.status != task.status ||
                        restored.item.userPaused != task.userPaused
                    ) {
                        restoredStateChanged = true
                    }
                    completedTempFiles += restored.cleanupTempFiles
                    val mergeTask = restored.mergeTask
                    if (mergeTask != null) {
                        restoredMerges[task.id] = mergeTask
                    }
                    continue
                }

                if (isManagedTask(task)) {
                    val restored = restoreManagedTask(task, resumableById[task.id])
                    restoredTasks[task.id] = restored.item
                    if (restored.item.status != task.status ||
                        restored.item.userPaused != task.userPaused
                    ) {
                        restoredStateChanged = true
                    }
                    completedTempFiles += restored.cleanupTempFiles
                    if (restored.state != null) {
                        restoredStates[task.id] = restored.state
                    }
                    continue
                }

                val restoredLifecycle = DownloadRestartPolicy.restore(
                    status = task.status,
                    userPaused = task.userPaused,
                )
                val restoredExtraTask = task.copy(
                    status = restoredLifecycle.status,
                    speedBytesPerSec = 0,
                    etaSeconds = null,
                    userPaused = restoredLifecycle.userPaused,
                    errorMessage = when {
                        restoredLifecycle.interrupted ->
                            strings.get(R.string.download_error_unsafe_exit)
                        restoredLifecycle.status == DownloadStatus.Paused -> null
                        else -> task.errorMessage
                    },
                    statusDetail = if (restoredLifecycle.interrupted ||
                        restoredLifecycle.status == DownloadStatus.Paused
                    ) {
                        null
                    } else {
                        task.statusDetail
                    },
                    progressIndeterminate = false,
                )
                if (restoredExtraTask.status != task.status ||
                    restoredExtraTask.userPaused != task.userPaused ||
                    restoredExtraTask.errorMessage != task.errorMessage ||
                    restoredExtraTask.statusDetail != task.statusDetail ||
                    restoredExtraTask.progressIndeterminate != task.progressIndeterminate
                ) {
                    restoredStateChanged = true
                }
                restoredTasks[task.id] = restoredExtraTask
                extraSpecById[task.id]?.let { spec ->
                    extraTaskSpecs[task.id] = spec
                }
            }
            if (ids.isEmpty()) {
                restoredGroupInfo.remove(group.id)
            } else {
                restoredGroupTaskIds[group.id] = ids
            }
        }

        synchronized(lock) {
            groupInfo.clear()
            groupInfo.putAll(restoredGroupInfo)
            groupTaskIds.clear()
            groupTaskIds.putAll(restoredGroupTaskIds)
            tasks.clear()
            deletingGroupIds.clear()
            deletingTaskIds.clear()
            tasks.putAll(restoredTasks.mapValues { (_, item) ->
                normalizeTask(item)
            })
        }
        downloadStates.clear()
        downloadStates.putAll(restoredStates)
        mergeTasks.clear()
        mergeTasks.putAll(restoredMerges)
        managedTaskQueue.clear()
        extraTaskQueue.clear()
        managedRetryTaskIds.clear()

        groupIds.set(maxGroupId)
        downloadIds.set(maxDownloadId)
        mergeIds.set(if (minMergeId != null) minMergeId - 1L else -1L)
        extraTaskIds.set(if (minExtraId != null) minExtraId - 1L else EXTRA_TASK_ID_START)

        cleanupCompletedTempFiles(completedTempFiles)
        updateGroups()
        if (restoredStateChanged) {
            schedulePersist()
        }
    }

    private fun restoreManagedTask(
        item: DownloadItem,
        snapshot: ResumableStateSnapshot?,
    ): ManagedRestoreResult {
        val sourceFileName = snapshot?.fileName ?: item.fileName
        val tempFile = tempFileFor(item.id, sourceFileName)
        val cleanupTempFiles = (
            tempFilesForCleanup(item.id, sourceFileName) +
                tempFilesForCleanup(item.id, item.fileName)
            ).distinctBy { it.absolutePath }
        val downloaded = if (tempFile.exists()) tempFile.length() else 0L
        val total = if (snapshot != null && snapshot.totalBytes > 0) {
            snapshot.totalBytes
        } else {
            item.totalBytes
        }
        if (item.status == DownloadStatus.Success) {
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
                    cleanupTempFiles = cleanupTempFiles,
                )
            }
        }

        val restoredLifecycle = DownloadRestartPolicy.restore(item.status, item.userPaused)

        val progress = calculateProgress(downloaded, total, item.progress)
        val finalItem = item.copy(
            status = restoredLifecycle.status,
            progress = progress,
            downloadedBytes = downloaded,
            totalBytes = total,
            speedBytesPerSec = 0,
            etaSeconds = null,
            userPaused = restoredLifecycle.userPaused,
            errorMessage = when {
                restoredLifecycle.interrupted ->
                    strings.get(R.string.download_error_unsafe_exit)
                restoredLifecycle.status == DownloadStatus.Paused -> null
                else -> item.errorMessage
            },
            statusDetail = if (restoredLifecycle.interrupted ||
                restoredLifecycle.status == DownloadStatus.Paused
            ) {
                null
            } else {
                item.statusDetail
            },
        )
        Log.d(
            TAG,
            "[restore-managed] rebuilt task state, taskId=${item.id}, file=${item.fileName}, finalStatus=${finalItem.status}, downloaded=$downloaded, total=$total, progress=$progress",
        )
        val state = if (!finalItem.status.isResolvedWithoutFailure &&
            finalItem.status != DownloadStatus.Cancelled) {
            ResumableState(
                id = item.id,
                source = DownloadSource(snapshot?.url ?: item.url, snapshot?.backupUrls.orEmpty()),
                validatorUrl = snapshot?.validatorUrl,
                fileName = sourceFileName,
                tempFile = tempFile,
                downloadedBytes = downloaded,
                totalBytes = total,
                etag = snapshot?.etag,
                lastModified = snapshot?.lastModified,
                conversionTarget = snapshot?.conversionTarget,
            )
        } else {
            null
        }
        return ManagedRestoreResult(
            item = finalItem,
            state = state,
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
        if (item.status == DownloadStatus.Success) {
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
                    cleanupTempFiles = cleanupTempFiles,
                )
            }
        }
        if (snapshot == null) {
            if (item.status.isResolvedWithoutFailure || item.status == DownloadStatus.Cancelled) {
                return MergedRestoreResult(
                    item = item.copy(
                        speedBytesPerSec = 0,
                        etaSeconds = null,
                    ),
                    mergeTask = null,
                    cleanupTempFiles = emptyList(),
                )
            }
            val restoredLifecycle = DownloadRestartPolicy.restore(item.status, item.userPaused)
            val restoredItem = item.copy(
                status = restoredLifecycle.status,
                speedBytesPerSec = 0,
                etaSeconds = null,
                userPaused = restoredLifecycle.userPaused,
                errorMessage = when {
                    restoredLifecycle.interrupted ->
                        strings.get(R.string.download_error_unsafe_exit)
                    restoredLifecycle.status == DownloadStatus.Paused -> null
                    else -> item.errorMessage
                },
            )
            return MergedRestoreResult(
                item = restoredItem,
                mergeTask = null,
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

        if (item.status.isResolvedWithoutFailure ||
            item.status == DownloadStatus.Cancelled) {
            val finalItem = item.copy(
                progress = if (item.status.isResolvedWithoutFailure) 100 else item.progress,
                downloadedBytes = downloaded,
                totalBytes = total,
                speedBytesPerSec = 0,
                etaSeconds = null,
            )
            return MergedRestoreResult(
                item = finalItem,
                mergeTask = null,
                cleanupTempFiles = emptyList(),
            )
        }

        val restoredLifecycle = DownloadRestartPolicy.restore(item.status, item.userPaused)
        val progress = calculateProgress(downloaded, total, item.progress)
        val finalItem = item.copy(
            status = restoredLifecycle.status,
            progress = progress,
            downloadedBytes = downloaded,
            totalBytes = total,
            speedBytesPerSec = 0,
            etaSeconds = null,
            userPaused = restoredLifecycle.userPaused,
            errorMessage = when {
                restoredLifecycle.interrupted ->
                    strings.get(R.string.download_error_unsafe_exit)
                restoredLifecycle.status == DownloadStatus.Paused -> null
                else -> item.errorMessage
            },
            statusDetail = if (restoredLifecycle.interrupted ||
                restoredLifecycle.status == DownloadStatus.Paused
            ) {
                null
            } else {
                item.statusDetail
            },
        )
        val mergeTask = MergedDownload(
            id = item.id,
            title = item.title,
            outputName = item.fileName,
            video = ResumablePart(
                source = DownloadSource(snapshot.video.url, snapshot.video.backupUrls),
                validatorUrl = snapshot.video.validatorUrl,
                fileName = snapshot.video.fileName,
                tempFile = videoTemp,
                downloadedBytes = videoDownloaded,
                totalBytes = videoTotal,
                etag = snapshot.video.etag,
                lastModified = snapshot.video.lastModified,
                speedBytesPerSec = 0,
                completed = videoCompleted,
                failed = restoredLifecycle.status == DownloadStatus.Failed,
            ),
            audio = ResumablePart(
                source = DownloadSource(snapshot.audio.url, snapshot.audio.backupUrls),
                validatorUrl = snapshot.audio.validatorUrl,
                fileName = snapshot.audio.fileName,
                tempFile = audioTemp,
                downloadedBytes = audioDownloaded,
                totalBytes = audioTotal,
                etag = snapshot.audio.etag,
                lastModified = snapshot.audio.lastModified,
                speedBytesPerSec = 0,
                completed = audioCompleted,
                failed = restoredLifecycle.status == DownloadStatus.Failed,
            ),
            userPaused = restoredLifecycle.userPaused,
            isMerging = false,
            completed = false,
            failed = restoredLifecycle.status == DownloadStatus.Failed,
            outputUri = item.localUri,
            conversionTarget = snapshot.conversionTarget,
        )
        return MergedRestoreResult(
            item = finalItem,
            mergeTask = mergeTask,
            cleanupTempFiles = emptyList(),
        )
    }

    private fun isManagedTask(item: DownloadItem): Boolean {
        return item.taskType.isManagedTransfer
    }

    private fun resolveMediaConversionTarget(
        taskType: DownloadTaskType,
        fileName: String,
        embedding: DownloadEmbedding?,
    ): MediaConversionTarget? {
        val settings = settingsRepository.currentSettings()
        return MediaConversionPolicy.targetFor(
            taskType = taskType,
            convertAudioToMp3 = settings.convertAudioToMp3,
            convertVideoToMp4 = settings.convertVideoToMp4,
            sourceExtension = fileName.substringAfterLast('.', ""),
            embedSubtitles = embedding?.subtitles != null,
        )
    }

    private fun startMerge(
        task: MergedDownload,
        slot: TaskConcurrencyQueue.TaskSlot,
    ) {
        synchronized(schedulingLock) {
            startMergeLocked(task, slot)
        }
    }

    private fun startMergeLocked(
        task: MergedDownload,
        slot: TaskConcurrencyQueue.TaskSlot,
    ) {
        if (task.userPaused || task.failed || task.completed ||
            !managedTaskQueue.isCurrent(slot) ||
            task.isMerging || mergeJobs[task.id]?.isActive == true
        ) {
            return
        }
        DownloadForegroundService.requestSync(context)
        task.isMerging = true
        updateMergedProgress(task, true, null)
        Log.d(
            TAG,
            "[merge-chain] start merge, taskId=${task.id}, output=${task.outputName}, videoTemp=${task.video.tempFile.absolutePath}, audioTemp=${task.audio.tempFile.absolutePath}",
        )
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var cancelled = false
            var terminalExecution = false
            try {
                if (!managedTaskQueue.isCurrent(slot) || mergeTasks[task.id] !== task) {
                    return@launch
                }
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
                    terminalExecution = true
                    task.outputUri = uri
                    if (target != null) {
                        updateTask(
                            target.copy(
                                status = DownloadStatus.Success,
                                progress = 100,
                                localUri = uri,
                                outputBytes = readOutputSize(Uri.parse(uri)),
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
                    terminalExecution = true
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
                                errorMessage = strings.get(R.string.download_failure_merge),
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
                    terminalExecution = true
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
                                errorMessage = err.message?.takeIf { it.isNotBlank() }
                                    ?: strings.get(R.string.download_failure_merge),
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
                val currentJob = coroutineContext[Job]
                val ownsExecution = currentJob != null && mergeJobs.remove(task.id, currentJob)
                if (ownsExecution) {
                    task.isMerging = false
                    if (mergeTasks[task.id] === task) {
                        when {
                            cancelled && !task.userPaused && !task.failed && !task.completed &&
                                task.video.completed && task.audio.completed -> {
                                Log.d(
                                    TAG,
                                    "[merge-chain] continue queued merge after cancellation, taskId=${task.id}",
                                )
                                val queuedSlot = managedTaskQueue.currentSlot(task.id)
                                if (queuedSlot != null) {
                                    startMerge(task, queuedSlot)
                                } else {
                                    updateMergedTaskPending(task)
                                    requestManagedTaskStart(task.id)
                                }
                            }

                            cancelled || task.userPaused -> {
                                updateMergedProgress(task, true, null)
                            }
                        }
                    }
                    if (terminalExecution) {
                        releaseManagedTaskSlot(slot)
                    }
                }
            }
        }
        mergeJobs.put(task.id, job)?.cancel()
        job.start()
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
            try {
                MediaProcessingEngine.merge(
                    videoFile = videoFile,
                    audioFile = audioFile,
                    outputFile = outputTemp,
                )
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                val message = if (task.conversionTarget == MediaConversionTarget.MP4) {
                    strings.get(R.string.download_failure_convert_video)
                } else {
                    strings.get(R.string.download_failure_merge)
                }
                throw IllegalStateException(message, err)
            }
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
            applyEmbeddedContentIfPossible(item, outputTemp)
        }

        currentCoroutineContext().ensureActive()
        val uri = saveToDownloads(outputTemp, task.outputName, relativePath)
        if (uri == null) {
            Log.e(
                TAG,
                "[merge-chain] merge output could not be saved, taskId=${task.id}, output=${task.outputName}",
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

    /** 元数据受全局开关控制；内嵌字幕与歌词是这次下载单独选择的，开关关闭时照常写入。 */
    private suspend fun applyEmbeddedContentIfPossible(item: DownloadItem, tempFile: File) {
        val settings = settingsRepository.currentSettings()
        updateTaskIf(
            item.id,
            { it.embedWarning != null || it.embeddedSubtitleTitles.isNotEmpty() || it.embeddedLyricsSource != null },
        ) {
            it.copy(embedWarning = null, embeddedSubtitleTitles = emptyList(), embeddedLyricsSource = null)
        }
        val metadata = item.embeddedMetadata?.takeIf { settings.addMetadata }
        val embedding = item.embedding
        if (metadata == null && embedding == null) return
        val detail = when {
            metadata != null && embedding?.subtitles != null -> R.string.download_detail_metadata_subtitles
            metadata != null && embedding?.lyrics != null -> R.string.download_detail_metadata_lyrics
            metadata != null -> R.string.download_detail_metadata
            embedding?.subtitles != null -> R.string.download_detail_embed_subtitles
            else -> R.string.download_detail_embed_lyrics
        }
        updateTaskIf(item.id, { it.status == DownloadStatus.Running || it.status == DownloadStatus.Merging }) {
            it.copy(statusDetail = strings.get(detail), embedWarning = null)
        }
        val result = EmbeddedContentWriter(httpClient, extrasRepository, strings)
            .write(tempFile, item, metadata, settings.metadata)
        val warningMessages = result.issues.map { issue ->
            DownloadMessage(when (issue) {
                EmbeddedContentIssue.Cover -> DownloadMessageCode.MetadataCoverFailed
                EmbeddedContentIssue.LyricsUnavailable -> DownloadMessageCode.EmbedLyricsUnavailable
                EmbeddedContentIssue.LyricsFailed -> DownloadMessageCode.EmbedLyricsFailed
                EmbeddedContentIssue.SubtitlesUnavailable -> DownloadMessageCode.EmbedSubtitlesUnavailable
                EmbeddedContentIssue.SubtitlesFailed -> DownloadMessageCode.EmbedSubtitlesFailed
                EmbeddedContentIssue.SubtitlesPartiallyFailed -> DownloadMessageCode.EmbedSubtitlesPartial
                EmbeddedContentIssue.SubtitlesUnsupportedContainer -> DownloadMessageCode.EmbedSubtitlesContainer
                EmbeddedContentIssue.WriteFailed -> DownloadMessageCode.EmbedWriteFailed
                EmbeddedContentIssue.UnsupportedFormat -> DownloadMessageCode.EmbedUnsupported
            })
        }.toMutableList().apply {
            if (result.incompleteSubtitleTitles.isNotEmpty()) {
                add(DownloadMessage(
                    DownloadMessageCode.EmbedMissingLanguages,
                    textArguments = result.incompleteSubtitleTitles.distinct(),
                ))
            }
        }
        val warning = warningMessages.joinToString("；") { it.resolve(context) }.takeIf(String::isNotBlank)
        updateTaskIf(item.id, { it.status == DownloadStatus.Running || it.status == DownloadStatus.Merging }) {
            it.copy(
                embedWarning = warning,
                embeddingMessages = warningMessages,
                embeddedSubtitleTitles = result.subtitleTitles,
                embeddedLyricsSource = result.lyricsSource,
            )
        }
    }

    internal suspend fun saveToDownloads(
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
        var savedUri = try {
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
            if (finalizeErr == null) {
                resolveInsertedOutputUri(uri, expectedSize)
            } else {
                Log.w(
                    TAG,
                    "Finalize output failed but file copy succeeded, uri=$uri, file=$fileName, expectedSize=$expectedSize",
                    finalizeErr,
                )
                recoverAfterFinalizeFailure(
                    insertedUri = uri,
                    fileName = fileName,
                    expectedSize = expectedSize,
                    finalizeErr = finalizeErr,
                )
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
            if (copied) resolveInsertedOutputUri(uri, expectedSize) else null
        }
        if (savedUri != null && overwriteExisting) {
            // 新文件已完整写入并可见后才替换旧件，写入失败时两份原始数据都还在。
            deleteConflictingDownloadsForTarget(fileName, normalizedRelativePath, uri.toString())
            // MediaStore 可能为重名自动加序号；覆盖成功后恢复用户要求的文件名。
            runCatching { finalizeOutputFile(uri, finalDisplayName = fileName) }.onFailure { error ->
                Log.w(TAG, "Saved output could not be renamed to $fileName, uri=$uri", error)
            }
            savedUri = resolveInsertedOutputUri(uri, expectedSize)
        }
        if (savedUri != null) {
            runCatching { tempFile.delete() }
            Log.i(TAG, "[save-output] success, uri=$savedUri, file=$fileName, relativePath=$relativePath")
        } else {
            // 恢复只检查本次新建的 URI，绝不把可读的同名旧件当作这次下载的成果。
            runCatching { resolver.delete(uri, null, null) }
            Log.e(TAG, "[save-output] failed, preserved temp=${tempFile.absolutePath}, uri=$uri")
        }
        return savedUri
    }

    /** 优先读取成品文件本身，避免 MediaStore 的大小尚未刷新时显示为 0。 */
    private fun readOutputSize(uri: Uri): Long? =
        runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize.takeIf { it >= 0L }
            }
        }.getOrNull() ?: queryUriSize(uri)

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
        excludeUri: String,
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
                if (uri == excludeUri) continue
                val pending = if (pendingIndex >= 0) cursor.getInt(pendingIndex) else -1
                // 其他仍在写入的任务不是已完成的同名旧件，不能在这里删掉。
                if (pending == 1) continue
                val rows = runCatching {
                    resolver.delete(Uri.parse(uri), null, null)
                }.onFailure { err ->
                    Log.w(
                        TAG,
                        "[save-output] failed to delete conflicting row, file=$fileName, uri=$uri, path=$path, pending=$pending",
                        err,
                    )
                }.getOrDefault(0)
                if (rows > 0) {
                    deletedCount += rows
                    Log.w(
                        TAG,
                        "[save-output] deleted conflicting row, file=$fileName, uri=$uri, path=$path, pending=$pending, rows=$rows",
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

    private fun resolveInsertedOutputUri(
        insertedUri: Uri,
        expectedSize: Long,
    ): String? {
        val insertedUriString = insertedUri.toString()
        val pending = queryIsPending(insertedUri)
        // 保存成功必须确认本次输出确实可读；媒体库记录的 SIZE 不能代替文件描述符。
        val insertedAccessible = runCatching {
            resolver.openFileDescriptor(insertedUri, "r")?.use { descriptor ->
                descriptor.statSize >= expectedSize && FileInputStream(descriptor.fileDescriptor).use { input ->
                    expectedSize == 0L || input.read() >= 0
                }
            } ?: false
        }.getOrDefault(false)
        if (pending == 0 && insertedAccessible) {
            Log.d(
                TAG,
                "[save-output] inserted uri is stable, uri=$insertedUri, pending=$pending",
            )
            return insertedUriString
        }
        Log.w(
            TAG,
            "[save-output] inserted uri is not ready, uri=$insertedUri, pending=$pending, accessible=$insertedAccessible",
        )
        return null
    }

    private fun recoverAfterFinalizeFailure(
        insertedUri: Uri,
        fileName: String,
        expectedSize: Long,
        finalizeErr: Throwable,
    ): String? {
        Log.w(
            TAG,
            "[save-output] recover after finalize failure start, insertedUri=$insertedUri, file=$fileName, error=${finalizeErr.message}",
            finalizeErr,
        )
        val retryErr = runCatching { finalizeOutputFile(insertedUri) }.exceptionOrNull()
        if (retryErr == null) {
            Log.i(
                TAG,
                "[save-output] retry finalize succeeded, insertedUri=$insertedUri, file=$fileName",
            )
            return resolveInsertedOutputUri(insertedUri, expectedSize)
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
            )
            if (renamed != null) {
                return resolveInsertedOutputUri(insertedUri, expectedSize)
            }
        }
        return resolveInsertedOutputUri(insertedUri, expectedSize)
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
        return MediaConversionPolicy.mediaMimeType(ext) ?: when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "bmp" -> "image/bmp"
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

    private fun processingTempFileFor(id: Long, fileName: String): File {
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val extension = safeName.substringAfterLast('.', "")
        val baseName = if (extension.isNotBlank()) {
            safeName.removeSuffix(".$extension")
        } else {
            safeName
        }
        val processingName = if (extension.isNotBlank()) {
            "$id-$baseName.processing.$extension"
        } else {
            "$id-$baseName.processing"
        }
        return File(tempDir, processingName)
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
            processingTempFileFor(id, fileName),
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
        val pfd = try {
            resolver.openFileDescriptor(uri, "w")
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
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
        check(updatedRows > 0) { strings.get(R.string.download_saved_file_unavailable) }
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
        embedding: DownloadEmbedding? = null,
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
            embedding = embedding,
        )
    }

    private data class ResumableState(
        val id: Long,
        override var source: DownloadSource,
        val fileName: String,
        override val tempFile: File,
        override var downloadedBytes: Long = 0,
        override var totalBytes: Long = 0,
        override var etag: String? = null,
        override var lastModified: String? = null,
        override var speedBytesPerSec: Long = 0,
        override var validatorUrl: String? = null,
        override val transferMutex: Mutex = Mutex(),
        val conversionTarget: MediaConversionTarget? = null,
    ) : ResumableDownloadTarget

    private data class ResumablePart(
        override var source: DownloadSource,
        val fileName: String,
        override val tempFile: File,
        override var downloadedBytes: Long = 0,
        override var totalBytes: Long = 0,
        override var etag: String? = null,
        override var lastModified: String? = null,
        override var speedBytesPerSec: Long = 0,
        override var validatorUrl: String? = null,
        override val transferMutex: Mutex = Mutex(),
        var job: Job? = null,
        var failed: Boolean = false,
        var completed: Boolean = false,
    ) : ResumableDownloadTarget

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
        val conversionTarget: MediaConversionTarget? = null,
    )

    private data class GroupInfo(
        val title: String,
        val subtitle: String?,
        val bvid: String?,
        val coverUrl: String?,
        val createdAt: Long,
        val relativePath: String,
        val sourceMetadata: DownloadEmbeddedMetadata? = null,
    )

    private data class OutputTarget(
        val uri: Uri,
        val pfd: ParcelFileDescriptor,
    )

    private data class ManagedRestoreResult(
        val item: DownloadItem,
        val state: ResumableState?,
        val cleanupTempFiles: List<File>,
    )

    private data class MergedRestoreResult(
        val item: DownloadItem,
        val mergeTask: MergedDownload?,
        val cleanupTempFiles: List<File>,
    )

    private data class RetrySourceContext(
        val mediaInfo: MediaInfo,
        val item: MediaItem,
        val mediaType: MediaType,
        val playUrlInfo: com.happycola233.bilitools.data.model.PlayUrlInfo,
    )

    private data class RefreshedMergeSources(
        val video: DownloadSource,
        val audio: DownloadSource,
    )

    private data class DownloadStore(
        val version: Int = STORE_VERSION,
        val groups: List<DownloadGroup> = emptyList(),
        val resumableStates: List<ResumableStateSnapshot> = emptyList(),
        val mergeStates: List<MergedTaskSnapshot> = emptyList(),
        val extraTaskStates: List<ExtraTaskSnapshot> = emptyList(),
    )

    private data class ExtraTaskSnapshot(
        val id: Long,
        val spec: DownloadExtraTaskSpec,
    )

    private data class ResumableStateSnapshot(
        val id: Long,
        val url: String,
        val fileName: String,
        val totalBytes: Long,
        val etag: String?,
        val lastModified: String?,
        val backupUrls: List<String> = emptyList(),
        val validatorUrl: String? = url,
        val conversionTarget: MediaConversionTarget? = null,
    )

    private data class ResumablePartSnapshot(
        val url: String,
        val fileName: String,
        val totalBytes: Long,
        val etag: String?,
        val lastModified: String?,
        val backupUrls: List<String> = emptyList(),
        val validatorUrl: String? = url,
        val completed: Boolean,
    )

    private data class MergedTaskSnapshot(
        val id: Long,
        val outputName: String,
        val video: ResumablePartSnapshot,
        val audio: ResumablePartSnapshot,
        val conversionTarget: MediaConversionTarget? = null,
    )

    companion object {
        private const val TAG = "DownloadRepository"
        private const val BILI_STATUS_CODE_HEADER = "bili-status-code"
        private const val LOGIN_REQUIRED_CODE = -101
        private const val PROGRESS_UPDATE_INTERVAL_MS = 300L
        private const val PERSIST_DELAY_MS = 1000L
        private const val STORE_VERSION = 2
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
