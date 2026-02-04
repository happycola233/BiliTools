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
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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
import com.happycola233.bilitools.databinding.FragmentDownloadsBinding
import com.happycola233.bilitools.ui.AppViewModelFactory
import kotlinx.coroutines.launch
import java.util.Locale

class DownloadsFragment : Fragment() {
    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadsViewModel by viewModels {
        AppViewModelFactory(requireContext().appContainer)
    }

    private lateinit var adapter: DownloadsAdapter
    private var offsetListener: com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener? = null

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
             // Counteract the upward movement of the parent ViewPager by translating the FAB down
             binding.downloadsClear.translationY = -verticalOffset.toFloat()
        }
        appBar?.addOnOffsetChangedListener(offsetListener)

        // Adjust FAB position to avoid being covered by Bottom Navigation Bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.downloadsClear) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                // Bottom Nav height (adjusted to 56dp) + Insets + Spacing (0dp)
                val navHeight = (56 * resources.displayMetrics.density).toInt()
                val spacing = 0
                bottomMargin = navHeight + insets.bottom + spacing
                rightMargin = (16 * resources.displayMetrics.density).toInt() // Keep side margin at 16dp
            }
            WindowInsetsCompat.CONSUMED
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.groups.collect { list ->
                    adapter.submitFullList(buildSectionedList(list))
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
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
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
        _binding = null
    }
}
