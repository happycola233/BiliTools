package com.happycola233.bilitools.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLog as Log
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.isManagedTransfer
import java.util.Locale

/**
 * 下载页的 Compose 路由。
 *
 * [viewModel] 由 Activity 创建并传入；本页首次进入后常驻主壳组合，切走只是不再绘制，
 * 列表展开、批量选择等纯 UI 状态集中在 [DownloadsRouteUiState]，由主壳以 rememberSaveable 保存，跨进程重建后恢复。
 *
 * 任务操作菜单需要位于主壳最上层才能覆盖底栏并采样完整背景，因此本路由只向
 * [taskActionsOverlayState] 提交请求；主壳负责在内容和底栏之后组合
 * [DownloadsTaskActionsOverlay]。多选时顶栏与底栏也随之切换，所以 [routeState] 同样由主壳持有。
 */
@Composable
internal fun DownloadsRoute(
    viewModel: DownloadsViewModel,
    routeState: DownloadsRouteUiState,
    contentTopPadding: Dp,
    taskActionsOverlayState: DownloadsTaskActionsOverlayState,
    onOpenParse: () -> Unit,
    onOpenParseUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settingsRepository = context.appContainer.settingsRepository
    val groups by viewModel.groups.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    var detailsGroupId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.refreshOutputAvailability()
    }
    LaunchedEffect(viewModel, resources) {
        viewModel.historyReadFailed.collect { failed ->
            if (failed) Toast.makeText(context, resources.getString(R.string.download_history_unavailable), Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(viewModel, resources) {
        viewModel.deletionEvents.collect { result ->
            val message = when {
                result.blockedFiles > 0 || result.failedFiles > 0 -> R.string.download_delete_incomplete
                result.sharedFiles > 0 -> R.string.download_delete_shared_kept
                result.deleteFiles -> R.string.download_delete_finished
                else -> R.string.download_clear_finished
            }
            Toast.makeText(context, resources.getString(message), Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(groups) {
        routeState.pruneAgainst(groups)
        if (groups.none { it.id == detailsGroupId }) detailsGroupId = null
        val currentTaskIds = groups
            .asSequence()
            .flatMap { it.tasks.asSequence() }
            .map { it.id }
            .toSet()
        if (taskActionsOverlayState.activeItemId?.let { it !in currentTaskIds } == true) {
            taskActionsOverlayState.dismiss()
        }
    }
    DisposableEffect(taskActionsOverlayState) {
        onDispose { taskActionsOverlayState.dismissImmediately() }
    }

    BackHandler(enabled = routeState.selectionMode) {
        routeState.exitSelectionMode()
    }

    val manageState = calculateGlobalManageState(groups)
    val selectedGroups = groups.filter { it.id in routeState.selectedGroupIds }

    fun showUnavailable() {
        Log.w(TAG, "[ui-locate] show unavailable toast")
        Toast.makeText(
            context,
            resources.getString(R.string.download_action_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun executeDeletion(request: DownloadsDialogState) {
        routeState.dismissDialog()
        routeState.swipedGroupId = null
        when (request) {
            is DownloadsDialogState.DeleteTask -> viewModel.deleteTask(request.itemId, request.deleteFiles)
            is DownloadsDialogState.DeleteGroup -> viewModel.deleteGroup(request.groupId, request.deleteFiles)
            is DownloadsDialogState.BatchDelete -> {
                viewModel.deleteGroups(request.groupIds, request.deleteFiles)
                routeState.exitSelectionMode()
            }
        }
    }

    fun requestDeletion(request: DownloadsDialogState) {
        routeState.requestDelete(request, settings, ::executeDeletion)
    }

    fun showTaskActions(item: DownloadItem, anchorInWindow: Rect) {
        taskActionsOverlayState.show(
            request = DownloadsTaskActionsOverlayRequest(
                itemId = item.id,
                title = item.fileName.ifBlank { item.title },
                anchorInWindow = anchorInWindow,
                glassStyle = settings.toDownloadsGlassStyle(),
                actions = resolveTaskMenuActions(context, viewModel, item),
            ),
            onActionSelected = { action ->
                if (action == DownloadsTaskAction.Delete) {
                    requestDeletion(DownloadsDialogState.DeleteTask(
                        itemId = item.id,
                        deleteFiles = !item.localUri.isNullOrBlank(),
                    ))
                } else {
                    performFileAction(
                        context = context,
                        viewModel = viewModel,
                        groups = groups,
                        itemId = item.id,
                        action = action,
                        onUnavailable = ::showUnavailable,
                    )
                }
            },
        )
    }

    fun requestBatchDeletion(deleteFiles: Boolean) {
        if (selectedGroups.isEmpty()) {
            Toast.makeText(context, resources.getString(R.string.downloads_multi_no_task), Toast.LENGTH_SHORT).show()
            return
        }
        requestDeletion(DownloadsDialogState.BatchDelete(
            groupIds = selectedGroups.mapTo(linkedSetOf()) { it.id },
            deleteFiles = deleteFiles,
        ))
    }

    DownloadsScreenContent(
        groups = groups,
        selectionMode = routeState.selectionMode,
        selectedGroupIds = routeState.selectedGroupIds,
        expandedGroupIds = routeState.expandedGroupIds,
        collapsedSections = routeState.collapsedSections,
        swipedGroupId = routeState.swipedGroupId,
        dialogState = routeState.dialogState,
        contentTopPadding = contentTopPadding,
        resumeAllCount = manageState.startableCount,
        pauseAllCount = manageState.pausableCount,
        completedGroupCount = groups.count { it.isCompleted },
        liquidGlassPanelsEnabled = settings.liquidGlassPanelsEnabled,
        glassDebugEnabled = settings.downloadsGlassDebugEnabled,
        glassCornerRadiusDp = settings.downloadsGlassCornerRadiusDp,
        glassBlurRadiusDp = settings.downloadsGlassBlurRadiusDp,
        glassRefractionHeightDp = settings.downloadsGlassRefractionHeightDp,
        glassRefractionAmountFrac = settings.downloadsGlassRefractionAmountFrac,
        glassChromaticAberration = settings.downloadsGlassChromaticAberration,
        glassSurfaceAlpha = settings.downloadsGlassSurfaceAlpha,
        barGlassBlurRadiusDp = settings.liquidBarGlassBlurRadiusDp,
        barGlassRefractionHeightDp = settings.liquidBarGlassRefractionHeightDp,
        barGlassRefractionAmountFrac = settings.liquidBarGlassRefractionAmountFrac,
        barGlassChromaticAberration = settings.liquidBarGlassChromaticAberration,
        barGlassSurfaceAlpha = settings.liquidBarGlassSurfaceAlpha,
        onOpenParse = onOpenParse,
        onBatchManage = {
            if (!routeState.enterSelectionMode(groups)) {
                Toast.makeText(
                    context,
                    resources.getString(R.string.downloads_multi_no_task),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onResumeAll = { performResumeAll(context, viewModel, manageState) },
        onPauseAll = { performPauseAll(context, viewModel, manageState) },
        onClearCompleted = viewModel::clearCompleted,
        onClearAll = viewModel::clearAll,
        onClearRecords = { requestBatchDeletion(deleteFiles = false) },
        onDeleteFiles = { requestBatchDeletion(deleteFiles = true) },
        onDialogDismiss = routeState::dismissDialog,
        onDialogConfirm = { dontAskAgain ->
            routeState.dialogState?.let { request ->
                if (dontAskAgain) settingsRepository.skipDownloadDeletionConfirmation(request.deleteFiles)
                executeDeletion(request)
            }
        },
        onToggleSection = routeState::toggleSection,
        onToggleGroupExpanded = routeState::toggleGroupExpanded,
        onSwipedGroupChange = { routeState.swipedGroupId = it },
        onGroupSelectionToggle = { groupId ->
            routeState.toggleGroupSelection(groups, groupId)
        },
        onGroupPause = { group -> viewModel.pauseGroup(group.id) },
        onGroupResume = { group -> viewModel.resumeGroup(group.id) },
        onGroupReparse = { group -> group.sourceUrl()?.let(onOpenParseUrl) },
        onGroupShowDetails = { group -> detailsGroupId = group.id },
        onGroupDelete = { group, deleteFiles ->
            requestDeletion(DownloadsDialogState.DeleteGroup(
                groupId = group.id,
                deleteFiles = deleteFiles && group.tasks.any { !it.localUri.isNullOrBlank() },
            ))
        },
        onTaskPauseResume = { item ->
            when (item.status) {
                DownloadStatus.Pending,
                DownloadStatus.Running,
                DownloadStatus.Merging,
                -> viewModel.pause(item.id)

                DownloadStatus.Paused -> if (item.userPaused) viewModel.resume(item.id)
                else -> Unit
            }
        },
        onTaskRetry = { item ->
            if (item.status == DownloadStatus.Failed) viewModel.retry(item.id)
        },
        onTaskClick = ::showTaskActions,
        onDragSelectionStart = { groupId -> routeState.startDragSelection(groups, groupId) },
        onDragSelectionRange = routeState::updateDragSelection,
        onDragSelectionEnd = routeState::finishDragSelection,
        onGlassCornerRadiusChange = settingsRepository::setDownloadsGlassCornerRadiusDp,
        onGlassBlurRadiusChange = settingsRepository::setDownloadsGlassBlurRadiusDp,
        onGlassRefractionHeightChange = settingsRepository::setDownloadsGlassRefractionHeightDp,
        onGlassRefractionAmountChange = settingsRepository::setDownloadsGlassRefractionAmountFrac,
        onGlassChromaticAberrationChange = settingsRepository::setDownloadsGlassChromaticAberration,
        onGlassSurfaceAlphaChange = settingsRepository::setDownloadsGlassSurfaceAlpha,
        onGlassReset = { resetDownloadsGlass(settingsRepository) },
        onBarGlassBlurRadiusChange = settingsRepository::setLiquidBarGlassBlurRadiusDp,
        onBarGlassRefractionHeightChange = settingsRepository::setLiquidBarGlassRefractionHeightDp,
        onBarGlassRefractionAmountChange = settingsRepository::setLiquidBarGlassRefractionAmountFrac,
        onBarGlassChromaticAberrationChange = settingsRepository::setLiquidBarGlassChromaticAberration,
        onBarGlassSurfaceAlphaChange = settingsRepository::setLiquidBarGlassSurfaceAlpha,
        onBarGlassReset = { resetLiquidBarGlass(settingsRepository) },
        modifier = modifier,
    )
    groups.firstOrNull { it.id == detailsGroupId }?.let { group ->
        DownloadsDetailsSheet(group = group, onDismiss = { detailsGroupId = null })
    }
}

@Stable
internal class DownloadsRouteUiState(
    selectionMode: Boolean = false,
    selectedGroupIds: Set<Long> = emptySet(),
    expandedGroupIds: Set<Long> = emptySet(),
    collapsedSections: Set<DownloadSectionType> = emptySet(),
    swipedGroupId: Long? = null,
) {
    var selectionMode by mutableStateOf(selectionMode)
        private set
    var selectedGroupIds by mutableStateOf(selectedGroupIds)
        private set
    var expandedGroupIds by mutableStateOf(expandedGroupIds)
        private set
    var collapsedSections by mutableStateOf(collapsedSections)
        private set
    var swipedGroupId by mutableStateOf(swipedGroupId)
    var dialogState by mutableStateOf<DownloadsDialogState?>(null)

    /** 拖动多选开始前的选中集合；为空表示当前没有拖动多选。 */
    private var dragSelectionBase: Set<Long>? = null

    fun toggleSection(type: DownloadSectionType) {
        collapsedSections = collapsedSections.toMutableSet().apply {
            if (!add(type)) remove(type)
        }
    }

    fun toggleGroupExpanded(groupId: Long) {
        if (selectionMode) {
            toggleGroupSelection(emptyList(), groupId)
            return
        }
        if (swipedGroupId != null) {
            swipedGroupId = null
            return
        }
        expandedGroupIds = expandedGroupIds.toMutableSet().apply {
            if (!add(groupId)) remove(groupId)
        }
    }

    fun enterSelectionMode(groups: List<DownloadGroup>, initialGroupId: Long? = null): Boolean {
        if (groups.isEmpty()) return false
        selectionMode = true
        if (initialGroupId != null) {
            selectedGroupIds = selectedGroupIds.toMutableSet().apply {
                if (!add(initialGroupId)) remove(initialGroupId)
            }
        }
        expandedGroupIds = emptySet()
        swipedGroupId = null
        return true
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedGroupIds = emptySet()
        dragSelectionBase = null
    }

    fun toggleGroupSelection(groups: List<DownloadGroup>, groupId: Long) {
        if (!selectionMode) {
            if (groups.isNotEmpty()) enterSelectionMode(groups, groupId)
            return
        }
        selectedGroupIds = selectedGroupIds.toMutableSet().apply {
            if (!add(groupId)) remove(groupId)
        }
    }

    fun toggleSelectAll(groups: List<DownloadGroup>) {
        if (!selectionMode || groups.isEmpty()) return
        val allIds = groups.mapTo(linkedSetOf()) { it.id }
        selectedGroupIds = if (
            selectedGroupIds.size == allIds.size && selectedGroupIds.containsAll(allIds)
        ) {
            emptySet()
        } else {
            allIds
        }
    }

    /**
     * 长按卡片开始拖动多选：未在多选时先进入多选。无论起点原本是否选中，拖过的范围都选中，
     * 已选中的组保持不变；取消选择通过往回拖或单击完成。
     */
    fun startDragSelection(groups: List<DownloadGroup>, anchorGroupId: Long) {
        if (!selectionMode && !enterSelectionMode(groups)) return
        dragSelectionBase = selectedGroupIds
        updateDragSelection(setOf(anchorGroupId))
    }

    /** [range] 为起点到手指当前所在卡片之间的组；退出范围的组恢复拖动开始前的状态。 */
    fun updateDragSelection(range: Set<Long>) {
        val base = dragSelectionBase ?: return
        selectedGroupIds = base + range
    }

    fun finishDragSelection() {
        dragSelectionBase = null
    }

    fun requestDelete(
        request: DownloadsDialogState,
        settings: AppSettings,
        onDelete: (DownloadsDialogState) -> Unit,
    ) {
        val needsConfirmation = if (request.deleteFiles) settings.confirmDownloadedFileDeletion
        else settings.confirmDownloadRecordRemoval
        if (needsConfirmation) dialogState = request else onDelete(request)
    }

    fun dismissDialog() {
        if (
            dialogState is DownloadsDialogState.DeleteTask ||
            dialogState is DownloadsDialogState.DeleteGroup
        ) {
            swipedGroupId = null
        }
        dialogState = null
    }

    fun pruneAgainst(groups: List<DownloadGroup>) {
        val currentGroupIds = groups.mapTo(hashSetOf()) { it.id }
        val currentTaskIds = groups
            .asSequence()
            .flatMap { it.tasks.asSequence() }
            .mapTo(hashSetOf()) { it.id }
        if (selectionMode) {
            if (groups.isEmpty()) {
                selectionMode = false
                selectedGroupIds = emptySet()
            } else {
                selectedGroupIds = selectedGroupIds.intersect(currentGroupIds)
            }
        }
        expandedGroupIds = expandedGroupIds.intersect(currentGroupIds)
        if (swipedGroupId !in currentGroupIds) swipedGroupId = null
        dialogState = when (val currentDialog = dialogState) {
            is DownloadsDialogState.DeleteTask -> currentDialog.takeIf {
                it.itemId in currentTaskIds
            }
            is DownloadsDialogState.DeleteGroup -> currentDialog.takeIf {
                it.groupId in currentGroupIds
            }
            is DownloadsDialogState.BatchDelete -> currentDialog.takeIf { state ->
                state.groupIds.any { it in currentGroupIds }
            }
            null -> null
        }
    }

    companion object {
        val Saver = listSaver<DownloadsRouteUiState, Any>(
            save = { state ->
                listOf(
                    state.selectionMode,
                    state.selectedGroupIds.toLongArray(),
                    state.expandedGroupIds.toLongArray(),
                    ArrayList(state.collapsedSections.map { it.name }),
                    state.swipedGroupId ?: NO_GROUP_ID,
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                DownloadsRouteUiState(
                    selectionMode = saved[0] as Boolean,
                    selectedGroupIds = (saved[1] as LongArray).toSet(),
                    expandedGroupIds = (saved[2] as LongArray).toSet(),
                    collapsedSections = (saved[3] as ArrayList<String>)
                        .mapTo(linkedSetOf(), DownloadSectionType::valueOf),
                    swipedGroupId = (saved[4] as Long).takeUnless { it == NO_GROUP_ID },
                )
            },
        )

        private const val NO_GROUP_ID = Long.MIN_VALUE
    }
}

private data class GlobalManageState(
    val resumableCount: Int,
    val retryableCount: Int,
    val pausableCount: Int,
) {
    val startableCount: Int
        get() = resumableCount + retryableCount
}

private fun calculateGlobalManageState(groups: List<DownloadGroup>): GlobalManageState {
    var resumableCount = 0
    var retryableCount = 0
    var pausableCount = 0
    groups.forEach { group ->
        group.tasks.forEach { task ->
            when (task.status) {
                DownloadStatus.Pending -> pausableCount++
                DownloadStatus.Running,
                DownloadStatus.Merging,
                -> if (task.taskType.isManagedTransfer) pausableCount++
                DownloadStatus.Paused -> if (task.userPaused) resumableCount++
                DownloadStatus.Failed -> retryableCount++
                else -> Unit
            }
        }
    }
    return GlobalManageState(resumableCount, retryableCount, pausableCount)
}

private fun performResumeAll(
    context: Context,
    viewModel: DownloadsViewModel,
    state: GlobalManageState,
) {
    if (state.startableCount <= 0) {
        Toast.makeText(
            context,
            context.getString(R.string.downloads_resume_all_empty),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    viewModel.startAll()
    val startedCount = state.resumableCount + state.retryableCount
    val message = if (state.retryableCount > 0) {
        context.getString(
            R.string.downloads_resume_all_done_with_retry,
            startedCount,
            state.retryableCount,
        )
    } else {
        context.getString(R.string.downloads_resume_all_done, startedCount)
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun performPauseAll(
    context: Context,
    viewModel: DownloadsViewModel,
    state: GlobalManageState,
) {
    if (state.pausableCount <= 0) {
        Toast.makeText(
            context,
            context.getString(R.string.downloads_pause_all_empty),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    viewModel.pauseAll()
    Toast.makeText(
        context,
        context.getString(R.string.downloads_pause_all_done, state.pausableCount),
        Toast.LENGTH_SHORT,
    ).show()
}

/**
 * 任务菜单的可用操作：文件已保存且可读时提供打开与分享；删除对所有任务可用，
 * 包括文件已丢失或尚未下载完成的任务。
 */
private fun resolveTaskMenuActions(
    context: Context,
    viewModel: DownloadsViewModel,
    item: DownloadItem,
): List<DownloadsTaskAction> {
    val uri = if (item.outputMissing) null else item.localUri?.let(Uri::parse)
    Log.d(
        TAG,
        "[ui-locate] show actions, taskId=${item.id}, file=${item.fileName}, status=${item.status}, outputMissing=${item.outputMissing}, localUri=${item.localUri}, parsedUri=$uri",
    )
    val fileReady = uri != null && isUriReadyForUserAction(context, uri)
    if (uri != null && !fileReady) {
        Log.w(TAG, "[ui-locate] file actions hidden: uri not ready, taskId=${item.id}, file=${item.fileName}, uri=$uri")
        viewModel.refreshOutputAvailability()
    }
    return if (fileReady) {
        listOf(DownloadsTaskAction.Open, DownloadsTaskAction.Share, DownloadsTaskAction.Delete)
    } else {
        listOf(DownloadsTaskAction.Delete)
    }
}

private fun performFileAction(
    context: Context,
    viewModel: DownloadsViewModel,
    groups: List<DownloadGroup>,
    itemId: Long,
    action: DownloadsTaskAction,
    onUnavailable: () -> Unit,
) {
    val item = groups
        .asSequence()
        .flatMap { it.tasks.asSequence() }
        .firstOrNull { it.id == itemId }
        ?: return
    val uri = resolveTaskActionUri(context, viewModel, item, action)
    if (uri == null) {
        onUnavailable()
        return
    }
    when (action) {
        DownloadsTaskAction.Open -> openWith(context, uri, item.fileName)
        DownloadsTaskAction.Share -> shareWith(context, uri, item.fileName)
        DownloadsTaskAction.Delete -> Unit
    }
}

private fun resolveTaskActionUri(
    context: Context,
    viewModel: DownloadsViewModel,
    item: DownloadItem,
    action: DownloadsTaskAction,
): Uri? {
    val uri = if (item.outputMissing) null else item.localUri?.let(Uri::parse)
    if (uri == null) {
        Log.w(
            TAG,
            "[ui-locate] ${action.logName} blocked: uri unavailable, taskId=${item.id}, file=${item.fileName}, outputMissing=${item.outputMissing}",
        )
        return null
    }
    if (!isUriReadyForUserAction(context, uri)) {
        Log.w(
            TAG,
            "[ui-locate] ${action.logName} blocked: uri not ready, taskId=${item.id}, file=${item.fileName}, uri=$uri",
        )
        viewModel.refreshOutputAvailability()
        return null
    }
    return uri
}

private val DownloadsTaskAction.logName: String
    get() = when (this) {
        DownloadsTaskAction.Open -> "open"
        DownloadsTaskAction.Share -> "share"
        DownloadsTaskAction.Delete -> "delete"
    }

private fun openWith(context: Context, uri: Uri, fileName: String) {
    val mimeType = resolveMimeType(context, uri, fileName)
    Log.d(TAG, "[ui-locate] openWith start, file=$fileName, uri=$uri, mimeType=$mimeType")
    val intent = Intent(Intent.ACTION_VIEW).apply {
        if (mimeType.isNullOrBlank()) data = uri else setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.download_action_open_with)),
        )
    }.onFailure { error ->
        Log.w(
            TAG,
            "[ui-locate] openWith failed, file=$fileName, uri=$uri, mimeType=$mimeType, error=${error.message}",
            error,
        )
        if (error is ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.download_action_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

private fun shareWith(context: Context, uri: Uri, fileName: String) {
    val mimeType = resolveMimeType(context, uri, fileName) ?: "*/*"
    Log.d(TAG, "[ui-locate] shareWith start, file=$fileName, uri=$uri, mimeType=$mimeType")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.download_action_share)),
        )
    }.onFailure { error ->
        Log.w(
            TAG,
            "[ui-locate] shareWith failed, file=$fileName, uri=$uri, mimeType=$mimeType, error=${error.message}",
            error,
        )
        if (error is ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.download_action_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

private fun resolveMimeType(context: Context, uri: Uri, fileName: String): String? {
    context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }?.let { return it }
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension.isBlank()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
}

private fun isUriReadyForUserAction(context: Context, uri: Uri): Boolean {
    val resolver = context.contentResolver
    val pending = runCatching {
        resolver.query(
            uri,
            arrayOf(MediaStore.Downloads.IS_PENDING),
            null,
            null,
            null,
        )?.use { cursor ->
            val pendingIndex = cursor.getColumnIndex(MediaStore.Downloads.IS_PENDING)
            if (pendingIndex >= 0 && cursor.moveToFirst()) cursor.getInt(pendingIndex) else null
        }
    }.getOrNull()
    if (pending == 1) return false
    return runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.onFailure { error ->
        Log.w(
            TAG,
            "[ui-locate] uri access check failed, uri=$uri, error=${error.message}",
            error,
        )
    }.getOrDefault(false)
}

private fun resetDownloadsGlass(settingsRepository: SettingsRepository) {
    settingsRepository.setDownloadsGlassCornerRadiusDp(
        SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CORNER_RADIUS_DP,
    )
    settingsRepository.setDownloadsGlassBlurRadiusDp(
        SettingsRepository.DEFAULT_DOWNLOADS_GLASS_BLUR_RADIUS_DP,
    )
    settingsRepository.setDownloadsGlassRefractionHeightDp(
        SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP,
    )
    settingsRepository.setDownloadsGlassRefractionAmountFrac(
        SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC,
    )
    settingsRepository.setDownloadsGlassSurfaceAlpha(
        SettingsRepository.DEFAULT_DOWNLOADS_GLASS_SURFACE_ALPHA,
    )
    settingsRepository.setDownloadsGlassChromaticAberration(
        SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CHROMATIC_ABERRATION,
    )
}

private fun resetLiquidBarGlass(settingsRepository: SettingsRepository) {
    settingsRepository.setLiquidBarGlassBlurRadiusDp(
        SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_BLUR_RADIUS_DP,
    )
    settingsRepository.setLiquidBarGlassRefractionHeightDp(
        SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_REFRACTION_HEIGHT_DP,
    )
    settingsRepository.setLiquidBarGlassRefractionAmountFrac(
        SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_REFRACTION_AMOUNT_FRAC,
    )
    settingsRepository.setLiquidBarGlassSurfaceAlpha(
        SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_SURFACE_ALPHA,
    )
    settingsRepository.setLiquidBarGlassChromaticAberration(
        SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_CHROMATIC_ABERRATION,
    )
}

private const val TAG = "DownloadsRoute"
