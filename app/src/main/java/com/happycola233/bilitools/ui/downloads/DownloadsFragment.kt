package com.happycola233.bilitools.ui.downloads

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLog as Log
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.databinding.FragmentDownloadsBinding
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.theme.rememberAndroidThemeColorScheme
import kotlinx.coroutines.launch
import java.util.Locale

class DownloadsFragment : Fragment() {
    private data class GlobalManageState(
        val resumableCount: Int,
        val retryableCount: Int,
        val pausableCount: Int,
    ) {
        val startableCount: Int
            get() = resumableCount + retryableCount
    }

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadsViewModel by viewModels {
        AppViewModelFactory(requireContext().appContainer)
    }
    private val settingsRepository by lazy { requireContext().appContainer.settingsRepository }

    private var offsetListener: com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener? = null
    private var latestGroups by mutableStateOf<List<DownloadGroup>>(emptyList())
    private val selectedGroupIds = linkedSetOf<Long>()
    private var selectionMode = false
    private var composeSelectionMode by mutableStateOf(false)
    private var composeSelectedGroupIds by mutableStateOf<Set<Long>>(emptySet())
    private var composeExpandedGroupIds by mutableStateOf<Set<Long>>(emptySet())
    private var composeCollapsedSections by mutableStateOf<Set<DownloadSectionType>>(emptySet())
    private var composeSwipedGroupId by mutableStateOf<Long?>(null)
    private var composeEmptyStateVisible by mutableStateOf(false)
    private var composeBatchStatusText by mutableStateOf("")
    private var composeBatchSelectAllText by mutableStateOf("")
    private var composeBatchHintHtml by mutableStateOf("")
    private var composeBatchClearEnabled by mutableStateOf(false)
    private var composeBatchDeleteEnabled by mutableStateOf(false)
    private var composeDialogState by mutableStateOf<DownloadsDialogState?>(null)
    private var composeControlsOffsetPx by mutableStateOf(0f)
    private var composeGlassDebugEnabled by mutableStateOf(false)
    private var composeGlassCornerRadiusDp by mutableStateOf(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CORNER_RADIUS_DP)
    private var composeGlassBlurRadiusDp by mutableStateOf(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_BLUR_RADIUS_DP)
    private var composeGlassRefractionHeightDp by mutableStateOf(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP)
    private var composeGlassRefractionAmountFrac by mutableStateOf(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC)
    private var composeGlassSurfaceAlpha by mutableStateOf(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_SURFACE_ALPHA)
    private var composeGlassChromaticAberration by mutableStateOf(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CHROMATIC_ABERRATION)
    private var composeResumeAllCount by mutableStateOf(0)
    private var composePauseAllCount by mutableStateOf(0)
    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val appBar = requireActivity().findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
        offsetListener = com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            composeControlsOffsetPx = -verticalOffset.toFloat()
        }
        appBar?.addOnOffsetChangedListener(offsetListener)

        binding.downloadsCompose.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        binding.downloadsCompose.setContent {
            val colorScheme = rememberAndroidThemeColorScheme()
            MaterialExpressiveTheme(colorScheme = colorScheme) {
                DownloadsScreenContent(
                    groups = latestGroups,
                    selectionMode = composeSelectionMode,
                    selectedGroupIds = composeSelectedGroupIds,
                    expandedGroupIds = composeExpandedGroupIds,
                    collapsedSections = composeCollapsedSections,
                    swipedGroupId = composeSwipedGroupId,
                    emptyStateVisible = composeEmptyStateVisible,
                    batchStatusText = composeBatchStatusText,
                    batchSelectAllText = composeBatchSelectAllText,
                    batchHintHtml = composeBatchHintHtml,
                    batchClearEnabled = composeBatchClearEnabled,
                    batchDeleteEnabled = composeBatchDeleteEnabled,
                    dialogState = composeDialogState,
                    controlsOffsetPx = composeControlsOffsetPx,
                    resumeAllCount = composeResumeAllCount,
                    pauseAllCount = composePauseAllCount,
                    glassDebugEnabled = composeGlassDebugEnabled,
                    glassCornerRadiusDp = composeGlassCornerRadiusDp,
                    glassBlurRadiusDp = composeGlassBlurRadiusDp,
                    glassRefractionHeightDp = composeGlassRefractionHeightDp,
                    glassRefractionAmountFrac = composeGlassRefractionAmountFrac,
                    glassChromaticAberration = composeGlassChromaticAberration,
                    glassSurfaceAlpha = composeGlassSurfaceAlpha,
                    onBatchManage = { enterSelectionMode() },
                    onResumeAll = { performResumeAll() },
                    onPauseAll = { performPauseAll() },
                    onClearCompleted = { viewModel.clearCompleted() },
                    onClearAll = { viewModel.clearAll() },
                    onExitSelection = { exitSelectionMode() },
                    onSelectAll = { toggleSelectAll() },
                    onClearRecords = { confirmBatchDelete(deleteFile = false) },
                    onDeleteFiles = { confirmBatchDelete(deleteFile = true) },
                    onDialogDismiss = { dismissDownloadsDialog() },
                    onDialogConfirm = { deleteFile -> confirmDownloadsDialog(deleteFile) },
                    onTaskActionSelected = { action -> performTaskAction(action) },
                    onToggleSection = { toggleSection(it) },
                    onToggleGroupExpanded = { toggleGroupExpanded(it) },
                    onSwipedGroupChange = { composeSwipedGroupId = it },
                    onGroupSelectionToggle = { toggleGroupSelection(it) },
                    onGroupPause = { group -> viewModel.pauseGroup(group.id) },
                    onGroupResume = { group -> viewModel.resumeGroup(group.id) },
                    onGroupDelete = { group -> confirmGroupDelete(group) },
                    onTaskPauseResume = { item -> handleTaskPauseResume(item) },
                    onTaskRetry = { item ->
                        if (item.status == DownloadStatus.Failed) {
                            viewModel.retry(item.id)
                        }
                    },
                    onTaskDelete = { item -> confirmTaskDelete(item) },
                    onTaskClick = { item -> showTaskActions(item) },
                    onGlassCornerRadiusChange = { settingsRepository.setDownloadsGlassCornerRadiusDp(it) },
                    onGlassBlurRadiusChange = { settingsRepository.setDownloadsGlassBlurRadiusDp(it) },
                    onGlassRefractionHeightChange = { settingsRepository.setDownloadsGlassRefractionHeightDp(it) },
                    onGlassRefractionAmountChange = { settingsRepository.setDownloadsGlassRefractionAmountFrac(it) },
                    onGlassChromaticAberrationChange = { settingsRepository.setDownloadsGlassChromaticAberration(it) },
                    onGlassSurfaceAlphaChange = { settingsRepository.setDownloadsGlassSurfaceAlpha(it) },
                    onGlassReset = {
                        settingsRepository.setDownloadsGlassCornerRadiusDp(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CORNER_RADIUS_DP)
                        settingsRepository.setDownloadsGlassBlurRadiusDp(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_BLUR_RADIUS_DP)
                        settingsRepository.setDownloadsGlassRefractionHeightDp(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP)
                        settingsRepository.setDownloadsGlassRefractionAmountFrac(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC)
                        settingsRepository.setDownloadsGlassSurfaceAlpha(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_SURFACE_ALPHA)
                        settingsRepository.setDownloadsGlassChromaticAberration(SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CHROMATIC_ABERRATION)
                    },
                )
            }
        }

        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                exitSelectionMode()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
        updateSelectionUi()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.groups.collect { list ->
                    latestGroups = list
                    val currentIds = list.map { it.id }.toSet()
                    val currentTaskIds = list
                        .asSequence()
                        .flatMap { it.tasks.asSequence() }
                        .map { it.id }
                        .toSet()
                    if (selectionMode) {
                        if (list.isEmpty()) {
                            selectedGroupIds.clear()
                            selectionMode = false
                        } else {
                            selectedGroupIds.retainAll(currentIds)
                        }
                    }
                    composeExpandedGroupIds = composeExpandedGroupIds.intersect(currentIds)
                    if (composeSwipedGroupId !in currentIds) {
                        composeSwipedGroupId = null
                    }
                    composeDialogState = pruneDownloadsDialogState(
                        dialogState = composeDialogState,
                        currentGroupIds = currentIds,
                        currentTaskIds = currentTaskIds,
                    )
                    updateSelectionUi()
                    composeEmptyStateVisible = list.isEmpty()
                    val manageState = calculateGlobalManageState()
                    composeResumeAllCount = manageState.startableCount
                    composePauseAllCount = manageState.pausableCount
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.settings.collect { settings ->
                    composeGlassDebugEnabled = settings.downloadsGlassDebugEnabled
                    composeGlassCornerRadiusDp = settings.downloadsGlassCornerRadiusDp
                    composeGlassBlurRadiusDp = settings.downloadsGlassBlurRadiusDp
                    composeGlassRefractionHeightDp = settings.downloadsGlassRefractionHeightDp
                    composeGlassRefractionAmountFrac = settings.downloadsGlassRefractionAmountFrac
                    composeGlassSurfaceAlpha = settings.downloadsGlassSurfaceAlpha
                    composeGlassChromaticAberration = settings.downloadsGlassChromaticAberration
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshOutputAvailability()
    }

    private fun toggleSection(type: DownloadSectionType) {
        if (selectionMode) return
        composeCollapsedSections = composeCollapsedSections.toMutableSet().apply {
            if (!add(type)) {
                remove(type)
            }
        }
    }

    private fun toggleGroupExpanded(groupId: Long) {
        if (selectionMode) {
            toggleGroupSelection(groupId)
            return
        }
        if (composeSwipedGroupId != null) {
            composeSwipedGroupId = null
            return
        }
        composeExpandedGroupIds = composeExpandedGroupIds.toMutableSet().apply {
            if (!add(groupId)) {
                remove(groupId)
            }
        }
    }

    private fun handleTaskPauseResume(item: DownloadItem) {
        when (item.status) {
            DownloadStatus.Running -> viewModel.pause(item.id)
            DownloadStatus.Paused -> if (item.userPaused) {
                viewModel.resume(item.id)
            }
            else -> Unit
        }
    }

    private fun confirmTaskDelete(item: DownloadItem) {
        val canDeleteFile = item.status == DownloadStatus.Success &&
            !item.outputMissing &&
            !item.localUri.isNullOrBlank()
        composeDialogState = DownloadsDialogState.DeleteTask(
            itemId = item.id,
            canDeleteFile = canDeleteFile,
        )
    }

    private fun confirmGroupDelete(group: DownloadGroup) {
        val canDeleteFile = group.tasks.any {
            it.status == DownloadStatus.Success &&
                !it.outputMissing &&
                !it.localUri.isNullOrBlank()
        }
        composeDialogState = DownloadsDialogState.DeleteGroup(
            groupId = group.id,
            canDeleteFile = canDeleteFile,
        )
    }

    private fun buildSectionedList(groups: List<DownloadGroup>): List<DownloadsListItem> {
        if (groups.isEmpty()) return emptyList()
        val downloadingGroups = groups.filter { group ->
            group.tasks.any { it.status != DownloadStatus.Success }
        }
        val downloadedGroups = groups.filter { group ->
            group.tasks.isNotEmpty() && group.tasks.all { it.status == DownloadStatus.Success }
        }
        val totalSpeed = downloadingGroups.sumOf { group ->
            group.tasks.sumOf { task ->
                if (task.status == DownloadStatus.Running) task.speedBytesPerSec else 0
            }
        }
        val etaSeconds = calculateDownloadingEtaSeconds(downloadingGroups)
        val items = mutableListOf<DownloadsListItem>()
        items.add(
            DownloadsListItem.SectionHeader(
                DownloadSectionType.Downloading,
                downloadingGroups.size,
                totalSpeed,
                etaSeconds,
            ),
        )
        items.addAll(downloadingGroups.map { DownloadsListItem.GroupItem(it) })
        items.add(
            DownloadsListItem.SectionHeader(
                DownloadSectionType.Downloaded,
                downloadedGroups.size,
            ),
        )
        items.addAll(downloadedGroups.map { DownloadsListItem.GroupItem(it) })
        return items
    }

    private fun calculateDownloadingEtaSeconds(groups: List<DownloadGroup>): Long? {
        val activeTasks = groups.flatMap { group -> group.tasks }
            .filter { task ->
                isManagedTask(task) && when (task.status) {
                    DownloadStatus.Pending,
                    DownloadStatus.Running,
                    DownloadStatus.Paused,
                    DownloadStatus.Merging -> true
                    else -> false
                }
            }
        if (activeTasks.isEmpty()) return null

        val speedBytesPerSec = activeTasks.sumOf { task ->
            if (task.status == DownloadStatus.Running) task.speedBytesPerSec else 0L
        }
        if (speedBytesPerSec <= 0L) return null

        val sizeTasks = activeTasks.filter { task -> task.totalBytes > 0L }
        if (sizeTasks.isEmpty()) return null

        val totalBytes = sizeTasks.sumOf { task -> task.totalBytes }
        val downloadedBytes = sizeTasks.sumOf { task ->
            task.downloadedBytes.coerceAtMost(task.totalBytes)
        }
        return if (totalBytes > downloadedBytes) {
            (totalBytes - downloadedBytes) / speedBytesPerSec
        } else {
            null
        }
    }

    private fun enterSelectionMode(initialGroupId: Long? = null) {
        if (latestGroups.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.downloads_multi_no_task), Toast.LENGTH_SHORT).show()
            return
        }
        selectionMode = true
        if (initialGroupId != null) {
            if (!selectedGroupIds.add(initialGroupId)) {
                selectedGroupIds.remove(initialGroupId)
            }
        }
        updateSelectionUi()
    }

    private fun exitSelectionMode(clearSelection: Boolean = true) {
        selectionMode = false
        if (clearSelection) {
            selectedGroupIds.clear()
        }
        updateSelectionUi()
    }

    private fun toggleGroupSelection(groupId: Long) {
        if (!selectionMode) {
            enterSelectionMode(groupId)
            return
        }
        if (!selectedGroupIds.add(groupId)) {
            selectedGroupIds.remove(groupId)
        }
        updateSelectionUi()
    }

    private fun toggleSelectAll() {
        if (!selectionMode) return
        val allIds = latestGroups.map { it.id }
        if (allIds.isEmpty()) return
        val allSelected = selectedGroupIds.size == allIds.size && selectedGroupIds.containsAll(allIds)
        selectedGroupIds.clear()
        if (!allSelected) {
            selectedGroupIds.addAll(allIds)
        }
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        composeSelectionMode = selectionMode
        composeSelectedGroupIds = selectedGroupIds.toSet()
        if (::backPressedCallback.isInitialized) {
            backPressedCallback.isEnabled = selectionMode
        }

        if (selectionMode) {
            composeExpandedGroupIds = emptySet()
            composeSwipedGroupId = null
        }

        val total = latestGroups.size
        val selected = selectedGroupIds.size
        composeBatchStatusText = getString(R.string.downloads_multi_status, selected, total)
        val allSelected = total > 0 && selected == total
        composeBatchSelectAllText = getString(
            if (allSelected) R.string.downloads_multi_unselect_all else R.string.downloads_multi_select_all,
        )
        val selectedGroups = latestGroups.filter { selectedGroupIds.contains(it.id) }
        val hasSelection = selected > 0
        val hasRunningTask = selectedGroups.any { hasInProgressTask(it) }
        val clearEnabled = hasSelection
        val deleteEnabled = hasSelection
        composeBatchClearEnabled = clearEnabled
        composeBatchDeleteEnabled = deleteEnabled

        val hintRes = when {
            !hasSelection -> R.string.downloads_multi_hint_default
            hasRunningTask -> R.string.downloads_multi_hint_running
            else -> R.string.downloads_multi_hint_has_file
        }
        composeBatchHintHtml = getString(hintRes)
    }

    private fun confirmBatchDelete(deleteFile: Boolean) {
        val selectedGroups = latestGroups.filter { selectedGroupIds.contains(it.id) }
        if (selectedGroups.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.downloads_multi_no_task), Toast.LENGTH_SHORT).show()
            return
        }
        composeDialogState = DownloadsDialogState.BatchDelete(
            groupIds = selectedGroups.mapTo(linkedSetOf()) { it.id },
            deleteFile = deleteFile,
        )
    }

    private fun pruneDownloadsDialogState(
        dialogState: DownloadsDialogState?,
        currentGroupIds: Set<Long>,
        currentTaskIds: Set<Long>,
    ): DownloadsDialogState? {
        return when (dialogState) {
            is DownloadsDialogState.DeleteTask -> dialogState.takeIf { it.itemId in currentTaskIds }
            is DownloadsDialogState.DeleteGroup -> dialogState.takeIf { it.groupId in currentGroupIds }
            is DownloadsDialogState.BatchDelete -> dialogState.takeIf { state ->
                state.groupIds.any { it in currentGroupIds }
            }
            is DownloadsDialogState.TaskActions -> dialogState.takeIf { it.itemId in currentTaskIds }
            null -> null
        }
    }

    private fun dismissDownloadsDialog() {
        if (composeDialogState is DownloadsDialogState.DeleteTask ||
            composeDialogState is DownloadsDialogState.DeleteGroup
        ) {
            composeSwipedGroupId = null
        }
        composeDialogState = null
    }

    private fun confirmDownloadsDialog(deleteFile: Boolean) {
        when (val dialogState = composeDialogState) {
            is DownloadsDialogState.DeleteTask -> {
                composeSwipedGroupId = null
                viewModel.deleteTask(
                    dialogState.itemId,
                    if (dialogState.canDeleteFile) deleteFile else true,
                )
            }

            is DownloadsDialogState.DeleteGroup -> {
                composeSwipedGroupId = null
                viewModel.deleteGroup(
                    dialogState.groupId,
                    if (dialogState.canDeleteFile) deleteFile else true,
                )
            }

            is DownloadsDialogState.BatchDelete -> {
                performBatchDelete(
                    targetIds = dialogState.groupIds,
                    deleteFile = dialogState.deleteFile,
                )
            }

            is DownloadsDialogState.TaskActions -> return

            null -> return
        }
        composeDialogState = null
    }

    private fun hasInProgressTask(group: DownloadGroup): Boolean {
        return group.tasks.any { task ->
            when (task.status) {
                DownloadStatus.Pending,
                DownloadStatus.Running,
                DownloadStatus.Paused,
                DownloadStatus.Merging -> true
                else -> false
            }
        }
    }

    private fun performBatchDelete(
        targetIds: Collection<Long>,
        deleteFile: Boolean,
    ) {
        if (targetIds.isEmpty()) {
            return
        }
        viewModel.deleteGroups(targetIds, deleteFile)
        val toastRes = if (deleteFile) {
            R.string.downloads_multi_done_delete
        } else {
            R.string.downloads_multi_done_clear
        }
        Toast.makeText(requireContext(), getString(toastRes, targetIds.size), Toast.LENGTH_SHORT).show()
        exitSelectionMode()
    }

    private fun showTaskActions(item: DownloadItem) {
        if (!canShowTaskActionsDialog(item)) {
            return
        }
        composeDialogState = DownloadsDialogState.TaskActions(
            itemId = item.id,
            title = item.fileName.ifBlank { item.title },
        )
    }

    private fun canShowTaskActionsDialog(item: DownloadItem): Boolean {
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
        if (uri != null && !isUriReadyForUserAction(uri)) {
            Log.w(
                TAG,
                "[ui-locate] block actions: uri not ready, taskId=${item.id}, file=${item.fileName}, uri=$uri",
            )
            viewModel.refreshOutputAvailability()
            showUnavailable()
            return false
        }
        return true
    }

    private fun performTaskAction(action: DownloadsTaskAction) {
        val dialogState = composeDialogState as? DownloadsDialogState.TaskActions ?: return
        composeDialogState = null
        val item = findDownloadItem(dialogState.itemId) ?: return
        val uri = resolveTaskActionUri(item, action)
        if (uri == null) {
            showUnavailable()
            return
        }
        when (action) {
            DownloadsTaskAction.Open -> {
                Log.d(
                    TAG,
                    "[ui-locate] open requested, taskId=${item.id}, file=${item.fileName}, uri=$uri",
                )
                openWith(uri, item.fileName)
            }

            DownloadsTaskAction.Share -> {
                Log.d(
                    TAG,
                    "[ui-locate] share requested, taskId=${item.id}, file=${item.fileName}, uri=$uri",
                )
                shareWith(uri, item.fileName)
            }
        }
    }

    private fun findDownloadItem(itemId: Long): DownloadItem? {
        return latestGroups
            .asSequence()
            .flatMap { it.tasks.asSequence() }
            .firstOrNull { it.id == itemId }
    }

    private fun resolveTaskActionUri(
        item: DownloadItem,
        action: DownloadsTaskAction,
    ): Uri? {
        val uri = if (item.outputMissing) null else item.localUri?.let(Uri::parse)
        if (uri == null) {
            val actionName = when (action) {
                DownloadsTaskAction.Open -> "open"
                DownloadsTaskAction.Share -> "share"
            }
            Log.w(
                TAG,
                "[ui-locate] $actionName blocked: uri unavailable, taskId=${item.id}, file=${item.fileName}, outputMissing=${item.outputMissing}",
            )
            return null
        }
        if (!isUriReadyForUserAction(uri)) {
            val actionName = when (action) {
                DownloadsTaskAction.Open -> "open"
                DownloadsTaskAction.Share -> "share"
            }
            Log.w(
                TAG,
                "[ui-locate] $actionName blocked: uri not ready, taskId=${item.id}, file=${item.fileName}, uri=$uri",
            )
            viewModel.refreshOutputAvailability()
            return null
        }
        return uri
    }

    private fun showClearMenu(anchor: View) {
        val menu = PopupMenu(requireContext(), anchor)
        menu.menuInflater.inflate(R.menu.downloads_clear_menu, menu.menu)
        val state = calculateGlobalManageState()
        val resumeAllItem = menu.menu.findItem(R.id.action_resume_all)
        val pauseAllItem = menu.menu.findItem(R.id.action_pause_all)
        resumeAllItem?.title = getString(R.string.downloads_resume_all_with_count, state.startableCount)
        pauseAllItem?.title = getString(R.string.downloads_pause_all_with_count, state.pausableCount)
        resumeAllItem?.isEnabled = state.startableCount > 0
        pauseAllItem?.isEnabled = state.pausableCount > 0

        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_batch_manage -> {
                    enterSelectionMode()
                    true
                }
                R.id.action_resume_all -> {
                    val currentState = calculateGlobalManageState()
                    val retryableIds = collectRetryableManagedTaskIds()
                    val retryCount = retryableIds.size
                    if (currentState.startableCount <= 0) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.downloads_resume_all_empty),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        if (currentState.resumableCount > 0) {
                            viewModel.resumeAllManaged()
                        }
                        retryableIds.forEach { taskId ->
                            viewModel.retry(taskId)
                        }
                        val startedCount = currentState.resumableCount + retryCount
                        Toast.makeText(
                            requireContext(),
                            if (retryCount > 0) {
                                getString(
                                    R.string.downloads_resume_all_done_with_retry,
                                    startedCount,
                                    retryCount,
                                )
                            } else {
                                getString(R.string.downloads_resume_all_done, startedCount)
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    true
                }
                R.id.action_pause_all -> {
                    val currentState = calculateGlobalManageState()
                    if (currentState.pausableCount <= 0) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.downloads_pause_all_empty),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        viewModel.pauseAllManaged()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.downloads_pause_all_done, currentState.pausableCount),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    true
                }
                R.id.action_clear_completed -> {
                    viewModel.clearCompleted()
                    true
                }
                R.id.action_clear_all -> {
                    viewModel.clearAll()
                    true
                }
                else -> false
            }
        }
        menu.show()
    }

    private fun performResumeAll() {
        val currentState = calculateGlobalManageState()
        val retryableIds = collectRetryableManagedTaskIds()
        val retryCount = retryableIds.size
        if (currentState.startableCount <= 0) {
            Toast.makeText(
                requireContext(),
                getString(R.string.downloads_resume_all_empty),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            if (currentState.resumableCount > 0) {
                viewModel.resumeAllManaged()
            }
            retryableIds.forEach { taskId ->
                viewModel.retry(taskId)
            }
            val startedCount = currentState.resumableCount + retryCount
            Toast.makeText(
                requireContext(),
                if (retryCount > 0) {
                    getString(
                        R.string.downloads_resume_all_done_with_retry,
                        startedCount,
                        retryCount,
                    )
                } else {
                    getString(R.string.downloads_resume_all_done, startedCount)
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun performPauseAll() {
        val currentState = calculateGlobalManageState()
        if (currentState.pausableCount <= 0) {
            Toast.makeText(
                requireContext(),
                getString(R.string.downloads_pause_all_empty),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            viewModel.pauseAllManaged()
            Toast.makeText(
                requireContext(),
                getString(R.string.downloads_pause_all_done, currentState.pausableCount),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun calculateGlobalManageState(): GlobalManageState {
        var resumableCount = 0
        var retryableCount = 0
        var pausableCount = 0
        latestGroups.forEach { group ->
            group.tasks.forEach { task ->
                if (!isManagedTask(task)) return@forEach
                when (task.status) {
                    DownloadStatus.Pending,
                    DownloadStatus.Running,
                    DownloadStatus.Merging -> pausableCount++
                    DownloadStatus.Paused -> if (task.userPaused) resumableCount++
                    DownloadStatus.Failed -> retryableCount++
                    else -> Unit
                }
            }
        }
        return GlobalManageState(
            resumableCount = resumableCount,
            retryableCount = retryableCount,
            pausableCount = pausableCount,
        )
    }

    private fun collectRetryableManagedTaskIds(): List<Long> {
        val ids = mutableListOf<Long>()
        latestGroups.forEach { group ->
            group.tasks.forEach { task ->
                if (isManagedTask(task) && task.status == DownloadStatus.Failed) {
                    ids += task.id
                }
            }
        }
        return ids
    }

    private fun isManagedTask(task: DownloadItem): Boolean {
        return when (task.taskType) {
            DownloadTaskType.Video,
            DownloadTaskType.Audio,
            DownloadTaskType.AudioVideo -> true
            else -> false
        }
    }

    private fun openWith(uri: Uri, fileName: String) {
        val mimeType = resolveMimeType(uri, fileName)
        Log.d(
            TAG,
            "[ui-locate] openWith start, file=$fileName, uri=$uri, mimeType=$mimeType",
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (mimeType.isNullOrBlank()) {
                data = uri
            } else {
                setDataAndType(uri, mimeType)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(
                Intent.createChooser(
                    intent,
                    getString(R.string.download_action_open_with),
                ),
            )
            Log.d(
                TAG,
                "[ui-locate] openWith chooser launched, file=$fileName, uri=$uri",
            )
        }.onFailure { err ->
            Log.w(
                TAG,
                "[ui-locate] openWith failed, file=$fileName, uri=$uri, mimeType=$mimeType, error=${err.message}",
                err,
            )
            if (err is ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.download_action_unavailable),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun shareWith(uri: Uri, fileName: String) {
        val mimeType = resolveMimeType(uri, fileName) ?: "*/*"
        Log.d(
            TAG,
            "[ui-locate] shareWith start, file=$fileName, uri=$uri, mimeType=$mimeType",
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(
                Intent.createChooser(
                    intent,
                    getString(R.string.download_action_share),
                ),
            )
            Log.d(
                TAG,
                "[ui-locate] shareWith chooser launched, file=$fileName, uri=$uri",
            )
        }.onFailure { err ->
            Log.w(
                TAG,
                "[ui-locate] shareWith failed, file=$fileName, uri=$uri, mimeType=$mimeType, error=${err.message}",
                err,
            )
            if (err is ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.download_action_unavailable),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun resolveMimeType(uri: Uri, fileName: String): String? {
        val resolverType = requireContext().contentResolver.getType(uri)
        if (!resolverType.isNullOrBlank()) {
            Log.d(
                TAG,
                "[ui-locate] resolveMimeType by contentResolver, file=$fileName, uri=$uri, mimeType=$resolverType",
            )
            return resolverType
        }
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        if (ext.isBlank()) {
            Log.d(
                TAG,
                "[ui-locate] resolveMimeType failed: no extension, file=$fileName, uri=$uri",
            )
            return null
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        Log.d(
            TAG,
            "[ui-locate] resolveMimeType by extension, file=$fileName, ext=$ext, mimeType=$mime",
        )
        return mime
    }

    private fun isUriReadyForUserAction(uri: Uri): Boolean {
        val resolver = requireContext().contentResolver
        val pending = runCatching {
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
        }.getOrNull()
        if (pending == 1) {
            Log.w(
                TAG,
                "[ui-locate] uri pending=1, block action, uri=$uri",
            )
            return false
        }
        val accessible = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.onFailure { err ->
            Log.w(
                TAG,
                "[ui-locate] uri access check failed, uri=$uri, error=${err.message}",
                err,
            )
        }.getOrDefault(false)
        Log.d(
            TAG,
            "[ui-locate] uri ready check, uri=$uri, pending=$pending, accessible=$accessible",
        )
        return accessible
    }

    private fun copyLocation(path: String) {
        if (path.isBlank()) return
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.download_location_label), path),
        )
        Toast.makeText(
            requireContext(),
            getString(R.string.download_location_copied),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun showUnavailable() {
        Log.w(
            TAG,
            "[ui-locate] show unavailable toast",
        )
        Toast.makeText(
            requireContext(),
            getString(R.string.download_action_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
    }

    companion object {
        private const val TAG = "DownloadsFragment"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val appBar = requireActivity().findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
        offsetListener?.let { appBar?.removeOnOffsetChangedListener(it) }
        offsetListener = null
        composeControlsOffsetPx = 0f
        _binding = null
    }
}

