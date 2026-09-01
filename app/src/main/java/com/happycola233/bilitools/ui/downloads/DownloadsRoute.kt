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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLog as Log
import com.happycola233.bilitools.core.appContainer
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
 * 列表展开、批量选择等纯 UI 状态另由 [rememberSaveable] 保存以跨进程重建恢复。
 *
 * 任务操作菜单需要位于主壳最上层才能覆盖底栏并采样完整背景，因此本路由只向
 * [taskActionsOverlayState] 提交请求；主壳负责在内容和底栏之后组合
 * [DownloadsTaskActionsGlassOverlay]。
 */
@Composable
internal fun DownloadsRoute(
    viewModel: DownloadsViewModel,
    contentTopPadding: Dp,
    taskActionsOverlayState: DownloadsTaskActionsOverlayState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settingsRepository = context.appContainer.settingsRepository
    val groups by viewModel.groups.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val routeState = rememberSaveable(saver = DownloadsRouteUiState.Saver) {
        DownloadsRouteUiState()
    }

    LaunchedEffect(viewModel) {
        viewModel.refreshOutputAvailability()
    }
    LaunchedEffect(groups) {
        routeState.pruneAgainst(groups)
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
    val selectedCount = routeState.selectedGroupIds.size
    val allSelected = groups.isNotEmpty() && selectedCount == groups.size
    val hasSelection = selectedCount > 0
    val hasRunningTask = selectedGroups.any(::hasInProgressTask)
    val batchHintRes = when {
        !hasSelection -> R.string.downloads_multi_hint_default
        hasRunningTask -> R.string.downloads_multi_hint_running
        else -> R.string.downloads_multi_hint_has_file
    }

    fun showUnavailable() {
        Log.w(TAG, "[ui-locate] show unavailable toast")
        Toast.makeText(
            context,
            resources.getString(R.string.download_action_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun showTaskActions(item: DownloadItem, anchorInWindow: Rect) {
        if (!canShowTaskActionsDialog(context, viewModel, item, ::showUnavailable)) return
        taskActionsOverlayState.show(
            request = DownloadsTaskActionsOverlayRequest(
                itemId = item.id,
                title = item.fileName.ifBlank { item.title },
                anchorInWindow = anchorInWindow,
                glassStyle = settings.toDownloadsGlassStyle(),
            ),
            onActionSelected = { action ->
                performTaskAction(
                    context = context,
                    viewModel = viewModel,
                    groups = groups,
                    itemId = item.id,
                    action = action,
                    onUnavailable = ::showUnavailable,
                )
            },
        )
    }

    DownloadsScreenContent(
        groups = groups,
        selectionMode = routeState.selectionMode,
        selectedGroupIds = routeState.selectedGroupIds,
        expandedGroupIds = routeState.expandedGroupIds,
        collapsedSections = routeState.collapsedSections,
        swipedGroupId = routeState.swipedGroupId,
        emptyStateVisible = groups.isEmpty(),
        batchStatusText = stringResource(
            R.string.downloads_multi_status,
            selectedCount,
            groups.size,
        ),
        batchSelectAllText = stringResource(
            if (allSelected) {
                R.string.downloads_multi_unselect_all
            } else {
                R.string.downloads_multi_select_all
            },
        ),
        batchHintHtml = stringResource(batchHintRes),
        batchClearEnabled = hasSelection,
        batchDeleteEnabled = hasSelection,
        dialogState = routeState.dialogState,
        contentTopPadding = contentTopPadding,
        resumeAllCount = manageState.startableCount,
        pauseAllCount = manageState.pausableCount,
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
        onExitSelection = routeState::exitSelectionMode,
        onSelectAll = { routeState.toggleSelectAll(groups) },
        onClearRecords = {
            if (!routeState.confirmBatchDelete(groups, deleteFile = false)) {
                Toast.makeText(
                    context,
                    resources.getString(R.string.downloads_multi_no_task),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onDeleteFiles = {
            if (!routeState.confirmBatchDelete(groups, deleteFile = true)) {
                Toast.makeText(
                    context,
                    resources.getString(R.string.downloads_multi_no_task),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onDialogDismiss = routeState::dismissDialog,
        onDialogConfirm = { deleteFile ->
            when (val dialogState = routeState.dialogState) {
                is DownloadsDialogState.DeleteTask -> {
                    routeState.swipedGroupId = null
                    viewModel.deleteTask(
                        dialogState.itemId,
                        if (dialogState.canDeleteFile) deleteFile else true,
                    )
                }

                is DownloadsDialogState.DeleteGroup -> {
                    routeState.swipedGroupId = null
                    viewModel.deleteGroup(
                        dialogState.groupId,
                        if (dialogState.canDeleteFile) deleteFile else true,
                    )
                }

                is DownloadsDialogState.BatchDelete -> {
                    val targetIds = dialogState.groupIds
                    if (targetIds.isNotEmpty()) {
                        viewModel.deleteGroups(targetIds, dialogState.deleteFile)
                        val toastRes = if (dialogState.deleteFile) {
                            R.string.downloads_multi_done_delete
                        } else {
                            R.string.downloads_multi_done_clear
                        }
                        Toast.makeText(
                            context,
                            resources.getString(toastRes, targetIds.size),
                            Toast.LENGTH_SHORT,
                        ).show()
                        routeState.exitSelectionMode()
                    }
                }

                null -> Unit
            }
            routeState.dialogState = null
        },
        onToggleSection = routeState::toggleSection,
        onToggleGroupExpanded = routeState::toggleGroupExpanded,
        onSwipedGroupChange = { routeState.swipedGroupId = it },
        onGroupSelectionToggle = { groupId ->
            routeState.toggleGroupSelection(groups, groupId)
        },
        onGroupPause = { group -> viewModel.pauseGroup(group.id) },
        onGroupResume = { group -> viewModel.resumeGroup(group.id) },
        onGroupDelete = routeState::confirmGroupDelete,
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
        onTaskDelete = routeState::confirmTaskDelete,
        onTaskClick = ::showTaskActions,
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
}

@Stable
private class DownloadsRouteUiState(
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

    fun toggleSection(type: DownloadSectionType) {
        if (selectionMode) return
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

    fun confirmTaskDelete(item: DownloadItem) {
        dialogState = DownloadsDialogState.DeleteTask(
            itemId = item.id,
            canDeleteFile = item.status == DownloadStatus.Success &&
                !item.outputMissing &&
                !item.localUri.isNullOrBlank(),
        )
    }

    fun confirmGroupDelete(group: DownloadGroup) {
        dialogState = DownloadsDialogState.DeleteGroup(
            groupId = group.id,
            canDeleteFile = group.tasks.any {
                it.status == DownloadStatus.Success &&
                    !it.outputMissing &&
                    !it.localUri.isNullOrBlank()
            },
        )
    }

    fun confirmBatchDelete(groups: List<DownloadGroup>, deleteFile: Boolean): Boolean {
        val targetIds = groups
            .asSequence()
            .map { it.id }
            .filter { it in selectedGroupIds }
            .toCollection(linkedSetOf())
        if (targetIds.isEmpty()) return false
        dialogState = DownloadsDialogState.BatchDelete(targetIds, deleteFile)
        return true
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

private fun hasInProgressTask(group: DownloadGroup): Boolean = group.tasks.any { task ->
    when (task.status) {
        DownloadStatus.Pending,
        DownloadStatus.Running,
        DownloadStatus.Paused,
        DownloadStatus.Merging,
        -> true
        else -> false
    }
}

private fun canShowTaskActionsDialog(
    context: Context,
    viewModel: DownloadsViewModel,
    item: DownloadItem,
    onUnavailable: () -> Unit,
): Boolean {
    if (item.status == DownloadStatus.Success && item.outputMissing) {
        Log.w(
            TAG,
            "[ui-locate] block actions: success item marked missing, taskId=${item.id}, file=${item.fileName}, localUri=${item.localUri}",
        )
        return false
    }
    val uri = if (item.outputMissing) null else item.localUri?.let(Uri::parse)
    Log.d(
        TAG,
        "[ui-locate] show actions, taskId=${item.id}, file=${item.fileName}, status=${item.status}, outputMissing=${item.outputMissing}, localUri=${item.localUri}, parsedUri=$uri",
    )
    if (uri != null && !isUriReadyForUserAction(context, uri)) {
        Log.w(
            TAG,
            "[ui-locate] block actions: uri not ready, taskId=${item.id}, file=${item.fileName}, uri=$uri",
        )
        viewModel.refreshOutputAvailability()
        onUnavailable()
        return false
    }
    return true
}

private fun performTaskAction(
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
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
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
