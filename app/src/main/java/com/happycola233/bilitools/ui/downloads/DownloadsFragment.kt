package com.happycola233.bilitools.ui.downloads

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.databinding.FragmentDownloadsBinding
import com.happycola233.bilitools.ui.AppViewModelFactory
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

    private lateinit var adapter: DownloadsAdapter
    private var offsetListener: com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener? = null
    private var latestGroups: List<DownloadGroup> = emptyList()
    private val selectedGroupIds = linkedSetOf<Long>()
    private var selectionMode = false
    private var batchUiVisible = false
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
        // Find AppBarLayout to fix FAB position during scroll
        val appBar = requireActivity().findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
        offsetListener = com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            // Counteract the upward movement of the parent ViewPager by translating floating controls down
            val translateY = -verticalOffset.toFloat()
            binding.downloadsClear.translationY = translateY
            binding.downloadsBatchPanel.translationY = translateY
        }
        appBar?.addOnOffsetChangedListener(offsetListener)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.downloadsClear.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val navHeight = (56 * resources.displayMetrics.density).toInt()
                bottomMargin = navHeight + insets.bottom
                rightMargin = (16 * resources.displayMetrics.density).toInt()
            }
            binding.downloadsBatchPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val navHeight = (56 * resources.displayMetrics.density).toInt()
                bottomMargin = navHeight + insets.bottom + (8 * resources.displayMetrics.density).toInt()
            }
            updateListBottomPadding()
            windowInsets
        }

        adapter = DownloadsAdapter(
            onPauseResume = { item ->
                when (item.status) {
                    DownloadStatus.Running -> viewModel.pause(item.id)
                    DownloadStatus.Paused -> if (item.userPaused) {
                        viewModel.resume(item.id)
                    }
                    else -> Unit
                }
            },
            onRetry = { item ->
                if (item.status == DownloadStatus.Failed) {
                    viewModel.retry(item.id)
                }
            },
            onDelete = { item ->
                 val canDeleteFile = item.status == DownloadStatus.Success &&
                     !item.outputMissing &&
                     !item.localUri.isNullOrBlank()
                 showDeleteConfirmation(
                     title = getString(R.string.download_delete),
                     message = getString(R.string.download_delete_confirm_task),
                     showCheckbox = canDeleteFile,
                     onConfirm = { deleteFile -> viewModel.deleteTask(item.id, deleteFile) }
                 )
            },
            onTaskClick = { item -> showTaskActions(item) },
            onGroupPause = { group -> viewModel.pauseGroup(group.id) },
            onGroupResume = { group -> viewModel.resumeGroup(group.id) },
            onGroupDelete = { group, onCancel -> 
                 val canDeleteFile = group.tasks.any { it.status == DownloadStatus.Success && !it.outputMissing && !it.localUri.isNullOrBlank() }
                 showDeleteConfirmation(
                     title = getString(R.string.downloads_group_delete),
                     message = getString(R.string.download_delete_confirm_group),
                     showCheckbox = canDeleteFile,
                     onConfirm = { deleteFile -> viewModel.deleteGroup(group.id, deleteFile) },
                     onCancel = onCancel
                 )
            },
            onLocationClick = { path -> copyLocation(path) },
            onGroupSelectionToggle = { group -> toggleGroupSelection(group.id) },
        )
        binding.downloadsList.layoutManager = LinearLayoutManager(requireContext())
        binding.downloadsList.adapter = adapter
        val decoration = StickyHeaderItemDecoration(adapter)
        binding.downloadsList.addItemDecoration(decoration)
        binding.downloadsList.addOnItemTouchListener(decoration)
        // Disable change animations to prevent flashing during custom expansion animations
        (binding.downloadsList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        
        binding.downloadsList.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.isNestedScrollingEnabled = v.canScrollVertically(-1) || v.canScrollVertically(1)
        }

        binding.downloadsClear.setOnClickListener { showClearMenu(it) }
        binding.downloadsBatchExit.setOnClickListener { exitSelectionMode() }
        binding.downloadsBatchSelectAll.setOnClickListener { toggleSelectAll() }
        binding.downloadsBatchClearRecords.setOnClickListener { confirmBatchDelete(deleteFile = false) }
        binding.downloadsBatchDeleteFiles.setOnClickListener { confirmBatchDelete(deleteFile = true) }

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
                    if (selectionMode) {
                        if (list.isEmpty()) {
                            selectedGroupIds.clear()
                            selectionMode = false
                        } else {
                            selectedGroupIds.retainAll(list.map { it.id }.toSet())
                        }
                    }
                    adapter.submitFullList(buildSectionedList(list))
                    updateSelectionUi()
                    val empty = list.isEmpty()
                    binding.downloadsEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshOutputAvailability()
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
        val items = mutableListOf<DownloadsListItem>()
        items.add(
            DownloadsListItem.SectionHeader(
                DownloadSectionType.Downloading,
                downloadingGroups.size,
                totalSpeed,
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
        if (!::adapter.isInitialized || _binding == null) return
        adapter.updateSelectionState(selectionMode, selectedGroupIds)
        renderBatchUi(selectionMode)
        if (::backPressedCallback.isInitialized) {
            backPressedCallback.isEnabled = selectionMode
        }

        val total = latestGroups.size
        val selected = selectedGroupIds.size
        binding.downloadsBatchStatus.text = getString(R.string.downloads_multi_status, selected, total)
        val allSelected = total > 0 && selected == total
        binding.downloadsBatchSelectAll.text = getString(
            if (allSelected) R.string.downloads_multi_unselect_all else R.string.downloads_multi_select_all,
        )
        val selectedGroups = latestGroups.filter { selectedGroupIds.contains(it.id) }
        val hasSelection = selected > 0
        val hasRunningTask = selectedGroups.any { hasInProgressTask(it) }
        val clearEnabled = hasSelection
        val deleteEnabled = hasSelection
        binding.downloadsBatchClearRecords.isEnabled = clearEnabled
        binding.downloadsBatchDeleteFiles.isEnabled = deleteEnabled
        binding.downloadsBatchClearRecords.alpha = if (clearEnabled) 1f else 0.45f
        binding.downloadsBatchDeleteFiles.alpha = if (deleteEnabled) 1f else 0.45f

        val hintRes = when {
            !hasSelection -> R.string.downloads_multi_hint_default
            hasRunningTask -> R.string.downloads_multi_hint_running
            else -> R.string.downloads_multi_hint_has_file
        }
        binding.downloadsBatchHint.text = asRichText(getString(hintRes))

        updateListBottomPadding()
    }

    private fun renderBatchUi(visible: Boolean) {
        if (_binding == null) return
        if (batchUiVisible == visible) return
        batchUiVisible = visible
        val duration = 220L
        val panel = binding.downloadsBatchPanel
        val fab = binding.downloadsClear
        val interpolator = FastOutSlowInInterpolator()

        panel.animate().cancel()
        fab.animate().cancel()

        if (visible) {
            panel.visibility = View.VISIBLE
            panel.alpha = 0f
            panel.scaleX = 0.98f
            panel.scaleY = 0.98f
            panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction { updateListBottomPadding() }
                .start()
            fab.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(180L)
                .setInterpolator(interpolator)
                .withEndAction {
                    fab.visibility = View.GONE
                    fab.scaleX = 1f
                    fab.scaleY = 1f
                }
                .start()
            return
        }

        fab.visibility = View.VISIBLE
        fab.alpha = 0f
        fab.scaleX = 0.92f
        fab.scaleY = 0.92f
        fab.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .start()
        panel.animate()
            .alpha(0f)
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(180L)
            .setInterpolator(interpolator)
            .withEndAction {
                panel.visibility = View.GONE
                panel.scaleX = 1f
                panel.scaleY = 1f
            }
            .start()
    }

    private fun updateListBottomPadding() {
        if (_binding == null) return
        val density = resources.displayMetrics.density
        val basePadding = (88 * density).toInt()
        val extraPadding = if (selectionMode) {
            val panelHeight = binding.downloadsBatchPanel.height.takeIf { it > 0 } ?: (188 * density).toInt()
            panelHeight + (20 * density).toInt()
        } else {
            0
        }
        val bottomPadding = basePadding + extraPadding
        binding.downloadsList.updatePadding(bottom = bottomPadding)
        binding.downloadsEmptyState.updatePadding(bottom = bottomPadding)
    }

    private fun confirmBatchDelete(deleteFile: Boolean) {
        val selectedGroups = latestGroups.filter { selectedGroupIds.contains(it.id) }
        if (selectedGroups.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.downloads_multi_no_task), Toast.LENGTH_SHORT).show()
            return
        }
        if (!deleteFile) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.downloads_multi_confirm_clear_title)
                .setMessage(
                    asRichText(
                        getString(
                            R.string.downloads_multi_confirm_clear_message,
                            selectedGroups.size,
                        ),
                    ),
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.download_delete) { _, _ ->
                    performBatchDelete(deleteFile = false)
                }
                .show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.downloads_multi_confirm_delete_title)
            .setMessage(
                asRichText(
                    getString(
                        R.string.downloads_multi_confirm_delete_message,
                        selectedGroups.size,
                    ),
                ),
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.download_delete) { _, _ ->
                performBatchDelete(deleteFile = true)
            }
            .show()
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

    private fun asRichText(text: String): CharSequence {
        return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun performBatchDelete(deleteFile: Boolean) {
        val targetIds = latestGroups
            .map { it.id }
            .filter { selectedGroupIds.contains(it) }
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
        if (item.status == DownloadStatus.Success && item.outputMissing) {
            return
        }
        val uri = if (item.outputMissing) null else item.localUri?.let { Uri.parse(it) }
        val title = item.fileName.ifBlank { item.title }
        val options = arrayOf(
            getString(R.string.download_action_open),
            getString(R.string.download_action_share),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (uri == null) {
                        showUnavailable()
                    } else {
                        openWith(uri, item.fileName)
                    }
                    1 -> if (uri == null) {
                        showUnavailable()
                    } else {
                        shareWith(uri, item.fileName)
                    }
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(
        title: String,
        message: String,
        showCheckbox: Boolean,
        onConfirm: (Boolean) -> Unit,
        onCancel: (() -> Unit)? = null,
    ) {
        val context = requireContext()
        
        var container: android.view.View? = null
        var checkbox: com.google.android.material.checkbox.MaterialCheckBox? = null
        
        if (showCheckbox) {
            checkbox = com.google.android.material.checkbox.MaterialCheckBox(context).apply {
                text = getString(R.string.download_delete_with_file)
                isChecked = true
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, 0, padding, 0)
            }
            
            val frame = android.widget.FrameLayout(context)
            val params = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val margin = (24 * resources.displayMetrics.density).toInt()
            params.marginStart = margin
            params.marginEnd = margin
            frame.addView(checkbox, params)
            container = frame
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel?.invoke() }
            .setOnCancelListener { onCancel?.invoke() }
            .setPositiveButton(R.string.download_delete) { _, _ ->
                val deleteFile = if (showCheckbox && checkbox != null) {
                    checkbox.isChecked
                } else {
                    true
                }
                onConfirm(deleteFile)
            }
            .show()
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
        }.onFailure { err ->
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
        }.onFailure { err ->
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
        if (!resolverType.isNullOrBlank()) return resolverType
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        if (ext.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
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
        Toast.makeText(
            requireContext(),
            getString(R.string.download_action_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val appBar = requireActivity().findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
        offsetListener?.let { appBar?.removeOnOffsetChangedListener(it) }
        offsetListener = null
        batchUiVisible = false
        _binding = null
    }
}
