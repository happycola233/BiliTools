package com.happycola233.bilitools.ui.parse

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import coil.load
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.MediaTab
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.databinding.FragmentParseBinding
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.ExternalDownloadContract
import com.happycola233.bilitools.ui.normalizeHttpUrl
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.R as MaterialR
import kotlinx.coroutines.launch

class ParseFragment : Fragment() {
    private var _binding: FragmentParseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParseViewModel by viewModels {
        AppViewModelFactory(requireContext().appContainer)
    }

    private var itemListAdapter: ParseItemAdapter? = null
    private var sectionAdapter: ArrayAdapter<String>? = null
    private var mediaTypeAdapter: ArrayAdapter<String>? = null
    private var resolutionModeAdapter: ArrayAdapter<String>? = null
    private var resolutionAdapter: ArrayAdapter<String>? = null
    private var codecAdapter: ArrayAdapter<String>? = null
    private var bitrateModeAdapter: ArrayAdapter<String>? = null
    private var bitrateAdapter: ArrayAdapter<String>? = null
    private var subtitleAdapter: ArrayAdapter<String>? = null

    private var pendingScrollY: Int? = null
    private var pendingListAnchorTop: Int? = null
    private var anchorFixScheduled = false
    private var quickActionEnabled = false
    private var quickActionLoadEnabled = false
    private var quickActionDownloadEnabled = false
    private var latestState: ParseUiState? = null
    private var quickActionScrollPending = false
    private var offsetListener: com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener? = null
    private var quickActionShown = false
    private var currentPageIndex: Int = 1
    private var sectionTabs: List<MediaTab> = emptyList()
    private var mediaTypeOptions: List<Pair<MediaType?, String>> = emptyList()
    private var resolutionModeOptions: List<Pair<QualityMode, String>> = emptyList()
    private var resolutionOptions: List<QualityOption> = emptyList()
    private var codecOptions: List<CodecOption> = emptyList()
    private var bitrateModeOptions: List<Pair<QualityMode, String>> = emptyList()
    private var audioOptions: List<AudioOption> = emptyList()
    private var subtitleOptions = emptyList<SubtitleInfo>()
    private var imageOptionIds: List<String> = emptyList()
    private val imageChips = mutableMapOf<String, Chip>()
    private var pendingExternalUrl: String? = null
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                context?.let { safeContext ->
                    Toast.makeText(
                        safeContext,
                        getString(R.string.notification_permission_denied_tip),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

    private var suppressUi = false
    private fun cacheScrollPosition() {
        if (pendingScrollY == null) {
            pendingScrollY = binding.parseScroll.scrollY
        }
    }

    private fun cacheListAnchor() {
        if (pendingListAnchorTop != null) return
        val root = binding.parseScroll
        val list = binding.pageList
        if (!list.isLaidOut || !list.isShown) return
        val rootRect = Rect()
        val listRect = Rect()
        if (!root.getGlobalVisibleRect(rootRect) || !list.getGlobalVisibleRect(listRect)) return
        if (!Rect.intersects(rootRect, listRect)) return
        val rootLoc = IntArray(2)
        val listLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)
        list.getLocationInWindow(listLoc)
        pendingListAnchorTop = listLoc[1] - rootLoc[1]
    }

    private fun scheduleListAnchorFix(anchorTop: Int) {
        if (anchorFixScheduled) return
        val root = binding.parseScroll
        val list = binding.pageList
        anchorFixScheduled = true
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val observer = root.viewTreeObserver
                if (observer.isAlive) {
                    observer.removeOnPreDrawListener(this)
                }
                anchorFixScheduled = false
                if (!list.isLaidOut) return true
                val rootLoc = IntArray(2)
                val listLoc = IntArray(2)
                root.getLocationInWindow(rootLoc)
                list.getLocationInWindow(listLoc)
                val currentTop = listLoc[1] - rootLoc[1]
                val delta = currentTop - anchorTop
                if (delta != 0) {
                    val content = root.getChildAt(0)?.height ?: 0
                    val maxScroll = (content - root.height).coerceAtLeast(0)
                    val nextScroll = (root.scrollY + delta).coerceIn(0, maxScroll)
                    root.scrollTo(0, nextScroll)
                }
                return true
            }
        })
    }

    private fun scrollToOptionsCard() {
        val scroll = binding.parseScroll
        val target = binding.optionsCard
        scroll.post {
            if (!target.isShown) return@post
            val rect = Rect()
            target.getDrawingRect(rect)
            scroll.offsetDescendantRectToMyCoords(target, rect)
            scroll.smoothScrollTo(0, rect.top)
        }
    }

    private fun updateQuickActionFab(
        state: ParseUiState? = latestState,
        enabled: Boolean = quickActionEnabled,
    ) {
        val fab = binding.parseQuickAction
        val loadEnabled = quickActionLoadEnabled
        val downloadEnabled = quickActionDownloadEnabled
        val shouldShow = enabled &&
            state?.mediaInfo != null &&
            (loadEnabled || downloadEnabled)
        if (!shouldShow) {
            if (quickActionShown) {
                animateQuickAction(false)
                quickActionShown = false
            }
            return
        }
        if (!quickActionShown) {
            animateQuickAction(true)
            quickActionShown = true
        }
        val hasInfo = state.playUrlInfo != null
        fab.setImageResource(
            if (hasInfo) R.drawable.ic_save_alt_24 else R.drawable.ic_troubleshoot_24,
        )
        fab.contentDescription = getString(
            if (hasInfo) R.string.parse_download else R.string.parse_load_stream,
        )
        fab.isEnabled = if (hasInfo) downloadEnabled else loadEnabled
    }

    private fun animateQuickAction(show: Boolean) {
        val fab = binding.parseQuickAction
        fab.animate().cancel()
        if (show) {
            fab.visibility = View.VISIBLE
            if (fab.alpha < 1f || fab.scaleX < 1f || fab.scaleY < 1f) {
                fab.alpha = fab.alpha.takeIf { it > 0f } ?: 0f
                fab.scaleX = fab.scaleX.takeIf { it > 0f } ?: 0.9f
                fab.scaleY = fab.scaleY.takeIf { it > 0f } ?: 0.9f
            } else {
                fab.alpha = 0f
                fab.scaleX = 0.9f
                fab.scaleY = 0.9f
            }
            fab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else if (fab.visibility == View.VISIBLE) {
            fab.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(150)
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    fab.visibility = View.GONE
                }
                .start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(
            ExternalDownloadContract.RESULT_KEY,
            this,
        ) { _, result ->
            pendingExternalUrl = normalizeHttpUrl(
                result.getString(ExternalDownloadContract.RESULT_URL),
            )
            consumePendingExternalUrl()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentParseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val appBar = requireActivity().findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
        offsetListener = com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            binding.parseQuickAction.translationY = -verticalOffset.toFloat()
        }
        appBar?.addOnOffsetChangedListener(offsetListener)
        ViewCompat.setOnApplyWindowInsetsListener(binding.parseQuickAction) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val navHeight = (56 * resources.displayMetrics.density).toInt()
                val spacing = 0
                bottomMargin = navHeight + insets.bottom + spacing
                rightMargin = (16 * resources.displayMetrics.density).toInt()
            }
            WindowInsetsCompat.CONSUMED
        }
        binding.parseScroll.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.isNestedScrollingEnabled = v.canScrollVertically(-1) || v.canScrollVertically(1)
        }
        binding.parseAction.setOnClickListener {
            binding.inputLink.clearFocus()
            binding.mediaTypeDropdown.clearFocus()
            binding.parseScroll.requestFocus()
            hideKeyboard()
            val input = binding.inputLink.text?.toString().orEmpty()
            viewModel.parse(input)
        }
        binding.pasteAction.setOnClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val text = clipboard?.primaryClip?.getItemAt(0)?.text
            if (!text.isNullOrEmpty()) {
                binding.inputLink.setText(text)
            }
        }
        binding.inputLink.addTextChangedListener {
            if (it.isNullOrEmpty()) {
                viewModel.clear()
            }
        }

        binding.streamFormatHint.text = HtmlCompat.fromHtml(
            getString(R.string.parse_stream_format_hint),
            HtmlCompat.FROM_HTML_MODE_COMPACT,
        )
        binding.streamFormatHint.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        mediaTypeOptions = listOf(
            null to getString(R.string.parse_media_type_auto),
            MediaType.Video to getString(R.string.parse_media_type_video),
            MediaType.Bangumi to getString(R.string.parse_media_type_bangumi),
            MediaType.Lesson to getString(R.string.parse_media_type_lesson),
            MediaType.Music to getString(R.string.parse_media_type_music),
            MediaType.MusicList to getString(R.string.parse_media_type_music_list),
            MediaType.WatchLater to getString(R.string.parse_media_type_watch_later),
            MediaType.Favorite to getString(R.string.parse_media_type_favorite),
            MediaType.Opus to getString(R.string.parse_media_type_opus),
            MediaType.OpusList to getString(R.string.parse_media_type_opus_list),
            MediaType.UserVideo to getString(R.string.parse_media_type_user_video),
            MediaType.UserOpus to getString(R.string.parse_media_type_user_opus),
            MediaType.UserAudio to getString(R.string.parse_media_type_user_audio),
        )
        mediaTypeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mediaTypeOptions.map { it.second },
        )
        binding.mediaTypeDropdown.setAdapter(mediaTypeAdapter)
        binding.mediaTypeDropdown.setOnItemClickListener { _, _, position, _ ->
            mediaTypeOptions.getOrNull(position)?.let { option ->
                viewModel.setMediaType(option.first)
            }
        }
        setupDropdown(binding.mediaTypeLayout, binding.mediaTypeDropdown)

        binding.loadStream.setOnClickListener {
            pendingScrollY = binding.parseScroll.scrollY
            binding.inputLink.clearFocus()
            binding.parseScroll.requestFocus()
            hideKeyboard()
            viewModel.loadStream()
        }
        binding.download.setOnClickListener {
            cacheScrollPosition()
            onDownloadClicked()
        }
        binding.parseQuickAction.setOnClickListener {
            val state = latestState ?: return@setOnClickListener
            if (state.playUrlInfo == null) {
                quickActionScrollPending = true
                viewModel.loadStream()
            } else {
                onDownloadClicked()
            }
        }

        binding.sectionDropdown.setOnItemClickListener { _, _, position, _ ->
            sectionTabs.getOrNull(position)?.let { tab ->
                cacheScrollPosition()
                viewModel.selectSection(tab.id)
            }
        }
        setupDropdown(binding.sectionLayout, binding.sectionDropdown)

        itemListAdapter = ParseItemAdapter(
            onItemClick = { index ->
                cacheListAnchor()
                viewModel.onItemRowClick(index)
            },
            onItemCheckToggle = { index, _ ->
                cacheListAnchor()
                viewModel.toggleItemSelection(index)
            },
        )
        binding.pageList.layoutManager = LinearLayoutManager(requireContext())
        binding.pageList.adapter = itemListAdapter
        binding.pageSelectAll.setOnClickListener {
            cacheScrollPosition()
            viewModel.selectAllItems()
        }
        binding.pageClearAll.setOnClickListener {
            cacheScrollPosition()
            viewModel.clearSelectedItems()
        }
        binding.pagePrev.setOnClickListener {
            cacheScrollPosition()
            viewModel.loadPrevPage()
        }
        binding.pageNext.setOnClickListener {
            cacheScrollPosition()
            viewModel.loadNextPage()
        }
        binding.pageIndexInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyPageInput()
                true
            } else {
                false
            }
        }
        binding.pageIndexInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                applyPageInput()
            }
        }

        binding.formatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressUi) return@setOnCheckedChangeListener
            val format = when (checkedId) {
                R.id.format_mp4 -> StreamFormat.Mp4
                R.id.format_flv -> StreamFormat.Flv
                else -> StreamFormat.Dash
            }
            viewModel.setFormat(format)
        }

        binding.outputGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressUi) return@setOnCheckedChangeListener
            val output = when (checkedId) {
                R.id.output_audio -> OutputType.AudioOnly
                R.id.output_video -> OutputType.VideoOnly
                R.id.output_av -> OutputType.AudioVideo
                else -> null
            }
            viewModel.setOutputType(output)
        }

        binding.collectionToggle.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                cacheScrollPosition()
                viewModel.setCollectionMode(isChecked)
            }
        }

        resolutionModeOptions = listOf(
            QualityMode.Highest to getString(R.string.parse_resolution_mode_highest),
            QualityMode.Lowest to getString(R.string.parse_resolution_mode_lowest),
            QualityMode.Fixed to getString(R.string.parse_resolution_mode_fixed),
        )
        resolutionModeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            resolutionModeOptions.map { it.second },
        )
        binding.resolutionModeDropdown.setAdapter(resolutionModeAdapter)
        binding.resolutionModeDropdown.setOnItemClickListener { _, _, position, _ ->
            resolutionModeOptions.getOrNull(position)?.let { viewModel.setResolutionMode(it.first) }
        }
        setupDropdown(binding.resolutionModeLayout, binding.resolutionModeDropdown)

        binding.resolutionDropdown.setOnItemClickListener { _, _, position, _ ->
            resolutionOptions.getOrNull(position)?.let { viewModel.setResolution(it.id) }
        }
        setupDropdown(binding.resolutionLayout, binding.resolutionDropdown)

        binding.codecDropdown.setOnItemClickListener { _, _, position, _ ->
            codecOptions.getOrNull(position)?.let { viewModel.setCodec(it.codec) }
        }
        setupDropdown(binding.codecLayout, binding.codecDropdown)

        bitrateModeOptions = listOf(
            QualityMode.Highest to getString(R.string.parse_bitrate_mode_highest),
            QualityMode.Lowest to getString(R.string.parse_bitrate_mode_lowest),
            QualityMode.Fixed to getString(R.string.parse_bitrate_mode_fixed),
        )
        bitrateModeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            bitrateModeOptions.map { it.second },
        )
        binding.bitrateModeDropdown.setAdapter(bitrateModeAdapter)
        binding.bitrateModeDropdown.setOnItemClickListener { _, _, position, _ ->
            bitrateModeOptions.getOrNull(position)?.let { viewModel.setAudioBitrateMode(it.first) }
        }
        setupDropdown(binding.bitrateModeLayout, binding.bitrateModeDropdown)

        binding.bitrateDropdown.setOnItemClickListener { _, _, position, _ ->
            audioOptions.getOrNull(position)?.let { viewModel.setAudioBitrate(it.id) }
        }
        setupDropdown(binding.bitrateLayout, binding.bitrateDropdown)

        binding.subtitleDropdown.setOnItemClickListener { _, _, position, _ ->
            subtitleOptions.getOrNull(position)?.let { viewModel.setSubtitleLanguage(it.lan) }
        }
        setupDropdown(binding.subtitleLayout, binding.subtitleDropdown)

        binding.subtitleToggle.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                cacheScrollPosition()
                viewModel.setSubtitleEnabled(isChecked)
            }
        }
        binding.aiSummaryToggle.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                cacheScrollPosition()
                viewModel.setAiSummaryEnabled(isChecked)
            }
        }
        binding.nfoCollectionToggle.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                cacheScrollPosition()
                viewModel.setNfoCollectionEnabled(isChecked)
            }
        }
        binding.nfoSingleToggle.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                cacheScrollPosition()
                viewModel.setNfoSingleEnabled(isChecked)
            }
        }
        binding.danmakuLiveToggle.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                cacheScrollPosition()
                viewModel.setDanmakuLiveEnabled(isChecked)
            }
        }
        binding.danmakuHistoryToggle.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                cacheScrollPosition()
                viewModel.setDanmakuHistoryEnabled(isChecked)
            }
        }
        binding.danmakuDateInput.addTextChangedListener { text ->
            if (!suppressUi) viewModel.setDanmakuDate(text?.toString().orEmpty())
        }
        binding.danmakuHourInput.addTextChangedListener { text ->
            if (!suppressUi) viewModel.setDanmakuHour(text?.toString().orEmpty())
        }

        consumePendingExternalUrl()

        val settingsRepository = requireContext().appContainer.settingsRepository
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        latestState = state
                        suppressUi = true
                    
                    val isLoading = state.loading
                    binding.parseActionLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
                    binding.parseAction.text = if (isLoading) "" else getString(R.string.parse_action)
                    binding.parseAction.icon = if (isLoading) null else androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_movie_filter_24)
                    binding.parseAction.isClickable = !isLoading
                    
                    binding.parseErrorCard.visibility =
                        if (state.error.isNullOrBlank()) View.GONE else View.VISIBLE
                    binding.parseError.text = state.error.orEmpty()

                    val info = state.mediaInfo
                    val item = state.items.getOrNull(state.selectedItemIndex)

                    mediaTypeOptions.firstOrNull { it.first == state.selectedMediaType }
                        ?.let { binding.mediaTypeDropdown.setText(it.second, false) }

                    if (info == null) {
                        binding.videoCard.visibility = View.GONE
                        binding.optionsCard.visibility = View.GONE
                    } else {
                        binding.videoCard.visibility = View.VISIBLE
                        binding.optionsCard.visibility =
                            if (state.playUrlInfo == null) View.GONE else View.VISIBLE
                        val display = resolveVideoCardDisplay(state, info, item)
                        val prevTitle = binding.title.text?.toString().orEmpty()
                        val prevDesc = binding.desc.text?.toString().orEmpty()
                        val prevStatVisible = binding.statScroll.visibility == View.VISIBLE
                        val nextStatVisible = hasAnyStat(display.stat)
                        if (
                            pendingListAnchorTop == null &&
                            (prevTitle != display.title ||
                                prevDesc != display.description ||
                                prevStatVisible != nextStatVisible)
                        ) {
                            cacheListAnchor()
                        }
                        binding.title.text = display.title
                        binding.desc.text = display.description
                        binding.cover.load(display.coverUrl)
                        bindStats(display.stat)
                    }

                    val hasSelection = state.selectedItemIndices.isNotEmpty()

                    // Load Stream Button State
                    val isStreamLoading = state.streamLoading
                    binding.loadStreamLoading.visibility = if (isStreamLoading) View.VISIBLE else View.GONE
                    binding.loadStream.text = if (isStreamLoading) "" else getString(R.string.parse_load_stream)
                    binding.loadStream.icon = if (isStreamLoading) null else androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_troubleshoot_24)
                    // Disable if loading stream OR if conditions not met (info/item missing or global loading)
                    val loadStreamEnabled =
                        (info != null && item != null && !state.loading && !isStreamLoading && hasSelection)
                    binding.loadStream.isEnabled = loadStreamEnabled
                    quickActionLoadEnabled = loadStreamEnabled

                    // Download Button State
                    val isDownloadStarting = state.downloadStarting
                    binding.downloadLoading.visibility = if (isDownloadStarting) View.VISIBLE else View.GONE
                    binding.download.text = if (isDownloadStarting) "" else getString(R.string.parse_download)
                    binding.download.icon = if (isDownloadStarting) null else androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_save_alt_24)
                    // Disable if downloading starting OR if conditions not met (playUrl missing or global loading)
                    val streamReady = state.outputType == null || state.playUrlInfo != null
                    val downloadEnabled = (!state.loading && !isDownloadStarting && hasSelection && streamReady)
                    binding.download.isEnabled = downloadEnabled
                    quickActionDownloadEnabled = downloadEnabled

                    val sections = state.sections
                    val inCollection = state.collectionMode
                    val mediaType = info?.type
                    val sectionLabelRes = when (mediaType) {
                        MediaType.Favorite -> R.string.parse_section_label_favorite
                        MediaType.Bangumi -> R.string.parse_section_label_bangumi
                        else -> R.string.parse_section_label
                    }
                    val pageLabelRes = when (mediaType) {
                        MediaType.Favorite -> R.string.parse_page_label_video
                        MediaType.Bangumi,
                        MediaType.Lesson,
                        MediaType.WatchLater -> R.string.parse_page_label_episode
                        else -> R.string.parse_page_label
                    }
                    binding.pageLabel.text = getString(if (inCollection) sectionLabelRes else pageLabelRes)
                    binding.sectionLabel.text = getString(sectionLabelRes)
                    if (sections == null || sections.tabs.isEmpty() || inCollection) {
                        binding.sectionLabel.visibility = View.GONE
                        binding.sectionLayout.visibility = View.GONE
                        binding.sectionDropdown.setText("", false)
                    } else {
                        binding.sectionLabel.visibility = View.VISIBLE
                        binding.sectionLayout.visibility = View.VISIBLE
                        sectionTabs = sections.tabs
                        val sectionLabels = sectionTabs.map { it.name }
                        if (sectionAdapter == null) {
                            sectionAdapter = ArrayAdapter(
                                requireContext(),
                                android.R.layout.simple_dropdown_item_1line,
                                sectionLabels,
                            )
                            binding.sectionDropdown.setAdapter(sectionAdapter)
                        } else {
                            sectionAdapter?.clear()
                            sectionAdapter?.addAll(sectionLabels)
                            sectionAdapter?.notifyDataSetChanged()
                        }
                        val selectedSectionIndex =
                            sectionTabs.indexOfFirst { it.id == state.selectedSectionId }
                        if (selectedSectionIndex >= 0 && selectedSectionIndex < sectionLabels.size) {
                            binding.sectionDropdown.setText(sectionLabels[selectedSectionIndex], false)
                        }
                    }

                    val totalItems = state.items.size
                    val selectedCount = state.selectedItemIndices.size
                    val isMultiSelect = selectedCount > 1
                    val allowAnyExtras = isMultiSelect
                    val rowClickEnabled = when {
                        info == null -> false
                        info.type == MediaType.Video -> state.collectionMode && info.collection
                        else -> true
                    }
                    itemListAdapter?.setRowClickEnabled(rowClickEnabled)
                    val highlightIndex = if (rowClickEnabled) {
                        state.collectionPreviewIndex ?: state.selectedItemIndex
                    } else {
                        -1
                    }
                    itemListAdapter?.submitList(state.items) {
                        itemListAdapter?.updateSelection(state.selectedItemIndices, highlightIndex)
                    }
                    binding.pageSelectedCount.text = getString(
                        R.string.parse_selected_count,
                        selectedCount,
                        totalItems,
                    )
                    val paged = info?.paged == true
                    val showPageModule = totalItems > 1 || paged
                    binding.pageActions.visibility = if (showPageModule) View.VISIBLE else View.GONE
                    binding.pageList.visibility = if (showPageModule) View.VISIBLE else View.GONE
                    binding.pageSelectAll.isEnabled = showPageModule && selectedCount < totalItems
                    binding.pageClearAll.isEnabled = selectedCount > 0
                    binding.pageNav.visibility = if (paged) View.VISIBLE else View.GONE
                    if (paged) {
                        currentPageIndex = state.pageIndex
                        if (!binding.pageIndexInput.hasFocus()) {
                            binding.pageIndexInput.setText(state.pageIndex.toString())
                        }
                    }
                    binding.pageIndexLayout.isEnabled = paged && !state.loading
                    binding.pagePrev.isEnabled = paged && state.pageIndex > 1 && !state.loading
                    binding.pageNext.isEnabled = paged && !state.loading

                    binding.formatGroup.check(
                        when (state.format) {
                            StreamFormat.Mp4 -> R.id.format_mp4
                            StreamFormat.Flv -> R.id.format_flv
                            StreamFormat.Dash -> R.id.format_dash
                        },
                    )

                    when (state.outputType) {
                        OutputType.AudioOnly -> binding.outputGroup.check(R.id.output_audio)
                        OutputType.VideoOnly -> binding.outputGroup.check(R.id.output_video)
                        OutputType.AudioVideo -> binding.outputGroup.check(R.id.output_av)
                        null -> binding.outputGroup.clearCheck()
                    }

                    val formatEnabled = state.outputType != null
                    binding.formatGroup.isEnabled = formatEnabled
                    binding.formatDash.isEnabled = formatEnabled
                    binding.formatMp4.isEnabled = formatEnabled
                    binding.formatFlv.isEnabled = formatEnabled

                    val hasVideo = state.videoStreams.isNotEmpty()
                    val hasAudio = state.audioStreams.isNotEmpty()
                    val isDash = state.format == StreamFormat.Dash
                    val allowAv = if (isDash) hasVideo && hasAudio else hasVideo
                    binding.outputVideo.isEnabled = isDash && hasVideo
                    binding.outputAv.isEnabled = allowAv
                    binding.outputAudio.isEnabled = isDash && hasAudio

                    binding.resolutionModeLayout.visibility = if (isMultiSelect) View.VISIBLE else View.GONE
                    binding.bitrateModeLayout.visibility = if (isMultiSelect) View.VISIBLE else View.GONE
                    binding.resolutionMultiHint.visibility = if (isMultiSelect) View.VISIBLE else View.GONE
                    binding.bitrateMultiHint.visibility = if (isMultiSelect) View.VISIBLE else View.GONE

                    val resolutionModeLabel =
                        resolutionModeOptions.firstOrNull { it.first == state.resolutionMode }?.second
                    if (resolutionModeLabel != null) {
                        binding.resolutionModeDropdown.setText(resolutionModeLabel, false)
                    }
                    val bitrateModeLabel =
                        bitrateModeOptions.firstOrNull { it.first == state.audioBitrateMode }?.second
                    if (bitrateModeLabel != null) {
                        binding.bitrateModeDropdown.setText(bitrateModeLabel, false)
                    }

                    val resolutionModeEnabled = isMultiSelect && formatEnabled && hasVideo
                    binding.resolutionModeLayout.isEnabled = resolutionModeEnabled
                    binding.resolutionModeDropdown.isEnabled = resolutionModeEnabled
                    val bitrateModeEnabled = isMultiSelect && formatEnabled && hasAudio
                    binding.bitrateModeLayout.isEnabled = bitrateModeEnabled
                    binding.bitrateModeDropdown.isEnabled = bitrateModeEnabled

                    resolutionOptions = state.resolutions
                    val resolutionLabels = resolutionOptions.map { it.label }
                    if (resolutionAdapter == null) {
                        resolutionAdapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            resolutionLabels,
                        )
                        binding.resolutionDropdown.setAdapter(resolutionAdapter)
                    } else {
                        resolutionAdapter?.clear()
                        resolutionAdapter?.addAll(resolutionLabels)
                        resolutionAdapter?.notifyDataSetChanged()
                    }
                    val resolutionLabel = resolutionOptions.firstOrNull { it.id == state.selectedResolutionId }?.label
                    if (resolutionLabel != null) {
                        binding.resolutionDropdown.setText(resolutionLabel, false)
                    }
                    val resolutionEnabled =
                        formatEnabled && hasVideo && (!isMultiSelect || state.resolutionMode == QualityMode.Fixed)
                    binding.resolutionLayout.isEnabled = resolutionEnabled
                    binding.resolutionDropdown.isEnabled = resolutionEnabled

                    codecOptions = state.codecs
                    val codecLabels = codecOptions.map { it.label }
                    if (codecAdapter == null) {
                        codecAdapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            codecLabels,
                        )
                        binding.codecDropdown.setAdapter(codecAdapter)
                    } else {
                        codecAdapter?.clear()
                        codecAdapter?.addAll(codecLabels)
                        codecAdapter?.notifyDataSetChanged()
                    }
                    val codecLabel = codecOptions.firstOrNull { it.codec == state.selectedCodec }?.label
                    if (codecLabel != null) {
                        binding.codecDropdown.setText(codecLabel, false)
                    }
                    val codecEnabled = formatEnabled && hasVideo
                    binding.codecLayout.isEnabled = codecEnabled
                    binding.codecDropdown.isEnabled = codecEnabled

                    audioOptions = state.audioBitrates
                    val bitrateLabels = audioOptions.map { it.label }
                    if (bitrateAdapter == null) {
                        bitrateAdapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            bitrateLabels,
                        )
                        binding.bitrateDropdown.setAdapter(bitrateAdapter)
                    } else {
                        bitrateAdapter?.clear()
                        bitrateAdapter?.addAll(bitrateLabels)
                        bitrateAdapter?.notifyDataSetChanged()
                    }
                    val bitrateLabel = audioOptions.firstOrNull { it.id == state.selectedAudioId }?.label
                    if (bitrateLabel != null) {
                        binding.bitrateDropdown.setText(bitrateLabel, false)
                    }
                    val bitrateEnabled =
                        formatEnabled && hasAudio && (!isMultiSelect || state.audioBitrateMode == QualityMode.Fixed)
                    binding.bitrateLayout.isEnabled = bitrateEnabled
                    binding.bitrateDropdown.isEnabled = bitrateEnabled

                    subtitleOptions = state.subtitleList
                    val subtitleLabels = subtitleOptions.map { it.name }
                    if (subtitleAdapter == null) {
                        subtitleAdapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            subtitleLabels,
                        )
                        binding.subtitleDropdown.setAdapter(subtitleAdapter)
                    } else {
                        subtitleAdapter?.clear()
                        subtitleAdapter?.addAll(subtitleLabels)
                        subtitleAdapter?.notifyDataSetChanged()
                    }
                    val subtitleLabel = subtitleOptions.firstOrNull { it.lan == state.selectedSubtitleLan }?.name
                    if (subtitleLabel != null) {
                        binding.subtitleDropdown.setText(subtitleLabel, false)
                    }

                    binding.subtitleToggle.isChecked = state.subtitleEnabled
                    binding.subtitleToggle.isEnabled = state.subtitleList.isNotEmpty() || allowAnyExtras
                    binding.subtitleLayout.isEnabled =
                        state.subtitleEnabled && (state.subtitleList.isNotEmpty() || allowAnyExtras)

                    binding.aiSummaryToggle.isChecked = state.aiSummaryEnabled
                    binding.aiSummaryToggle.isEnabled = state.aiSummaryAvailable || allowAnyExtras

                    val collectionAvailable =
                        info?.collection == true && !info.nfo.showTitle.isNullOrBlank()
                    val collectionModeAvailable = info?.type == MediaType.Video && info?.collection == true
                    binding.collectionToggle.visibility =
                        if (collectionModeAvailable) View.VISIBLE else View.GONE
                    binding.collectionToggleHint.visibility =
                        if (collectionModeAvailable) View.VISIBLE else View.GONE
                    binding.collectionToggle.isEnabled = collectionModeAvailable && !state.loading
                    binding.collectionToggle.isChecked = state.collectionMode

                    binding.nfoCollectionToggle.isChecked = state.nfoCollectionEnabled
                    binding.nfoSingleToggle.isChecked = state.nfoSingleEnabled
                    binding.nfoCollectionToggle.isEnabled = collectionAvailable || allowAnyExtras

                    binding.danmakuLiveToggle.isChecked = state.danmakuLiveEnabled
                    binding.danmakuHistoryToggle.isChecked = state.danmakuHistoryEnabled
                    binding.danmakuLiveToggle.isEnabled =
                        (item?.aid != null && item.cid != null) || allowAnyExtras
                    binding.danmakuHistoryToggle.isEnabled = (item?.cid != null) || allowAnyExtras
                    binding.danmakuDateLayout.isEnabled = state.danmakuHistoryEnabled
                    binding.danmakuHourLayout.isEnabled = state.danmakuHistoryEnabled
                    binding.danmakuDateInput.setText(state.danmakuDate)
                    binding.danmakuHourInput.setText(state.danmakuHour)

                    val imageOptions = state.imageOptions
                    val showImageOptions = imageOptions.isNotEmpty()
                    binding.imageOptionsGroup.visibility = if (showImageOptions) View.VISIBLE else View.GONE
                    if (showImageOptions) {
                        val group = binding.imageOptionsGroup
                        val optionIds = imageOptions.map { it.id }
                        if (optionIds != imageOptionIds) {
                            imageOptionIds = optionIds
                            imageChips.clear()
                            group.removeAllViews()
                            imageOptions.forEach { option ->
                                val chip = Chip(
                                    ContextThemeWrapper(
                                        requireContext(),
                                        MaterialR.style.Widget_Material3_Chip_Filter,
                                    ),
                                )
                                chip.text = option.label
                                chip.isCheckable = true
                                chip.isFocusable = false
                                chip.isFocusableInTouchMode = false
                                chip.isChecked = state.selectedImageIds.contains(option.id)
                                chip.setOnCheckedChangeListener { _, isChecked ->
                                    if (!suppressUi) {
                                        cacheScrollPosition()
                                        viewModel.setImageSelection(option.id, isChecked)
                                    }
                                }
                                group.addView(chip)
                                imageChips[option.id] = chip
                            }
                        }
                        imageOptions.forEach { option ->
                            val chip = imageChips[option.id] ?: return@forEach
                            if (chip.text != option.label) {
                                chip.text = option.label
                            }
                            val selected = state.selectedImageIds.contains(option.id)
                            if (chip.isChecked != selected) {
                                chip.isChecked = selected
                            }
                        }
                    } else if (imageOptionIds.isNotEmpty()) {
                        imageOptionIds = emptyList()
                        imageChips.clear()
                        binding.imageOptionsGroup.removeAllViews()
                    }

                    binding.streamInfo.text = state.streamInfo
                    binding.loginHintCard.visibility =
                        if (!state.isLoggedIn && info != null) View.VISIBLE else View.GONE

                    state.notice?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.clearNotice()
                    }
                    if (quickActionScrollPending && state.playUrlInfo != null) {
                        quickActionScrollPending = false
                        scrollToOptionsCard()
                    }
                    pendingListAnchorTop?.let { anchor ->
                        scheduleListAnchorFix(anchor)
                        if (!state.loading && !state.streamLoading && !state.downloadStarting) {
                            pendingListAnchorTop = null
                            pendingScrollY = null
                        }
                    } ?: pendingScrollY?.let { target ->
                        binding.parseScroll.post {
                            val content = binding.parseScroll.getChildAt(0)?.height ?: 0
                            val maxScroll = (content - binding.parseScroll.height).coerceAtLeast(0)
                            binding.parseScroll.scrollTo(0, target.coerceIn(0, maxScroll))
                        }
                        if (!state.loading && !state.streamLoading && !state.downloadStarting) {
                            pendingScrollY = null
                        }
                    }
                    updateQuickActionFab(state, quickActionEnabled)
                    suppressUi = false
                }
                }
                launch {
                    settingsRepository.settings.collect { settings ->
                        quickActionEnabled = settings.parseQuickActionEnabled
                        updateQuickActionFab(latestState, quickActionEnabled)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val appBar = requireActivity().findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
        offsetListener?.let { appBar?.removeOnOffsetChangedListener(it) }
        offsetListener = null
        _binding = null
        itemListAdapter = null
        sectionAdapter = null
        mediaTypeAdapter = null
        resolutionModeAdapter = null
        resolutionAdapter = null
        codecAdapter = null
        bitrateModeAdapter = null
        bitrateAdapter = null
        subtitleAdapter = null
        imageOptionIds = emptyList()
        imageChips.clear()
        latestState = null
        quickActionScrollPending = false
        quickActionShown = false
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshLoginState()
    }

    private fun setupDropdown(
        layout: TextInputLayout,
        dropdown: MaterialAutoCompleteTextView,
    ) {
        dropdown.threshold = 0
        dropdown.showSoftInputOnFocus = false
        // UX: tap field or end icon to toggle menu; tap again closes; drag should not open menu.
        val touchSlop = ViewConfiguration.get(dropdown.context).scaledTouchSlop
        fun createToggleTouchListener(): View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var dragging = false
            var wasShowing = false
            return View.OnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        dragging = false
                        wasShowing = dropdown.isPopupShowing
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                dragging = true
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        !dragging
                    }
                    MotionEvent.ACTION_UP -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        if (!dragging) {
                            if (wasShowing) {
                                dropdown.dismissDropDown()
                            } else {
                                val adapter = dropdown.adapter
                                if (adapter != null && adapter.count > 0) {
                                    dropdown.requestFocus()
                                    dropdown.showDropDown()
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        false
                    }
                    else -> false
                }
            }
        }
        dropdown.setOnTouchListener(createToggleTouchListener())
        layout.setOnTouchListener(createToggleTouchListener())
        layout.findViewById<View>(MaterialR.id.text_input_end_icon)
            ?.setOnTouchListener(createToggleTouchListener())
        dropdown.setOnClickListener(null)
        layout.setOnClickListener(null)
        layout.setEndIconOnClickListener(null)
    }

    private fun applyPageInput() {
        val page = binding.pageIndexInput.text?.toString()?.toIntOrNull()
        if (page != null && page != currentPageIndex) {
            cacheScrollPosition()
            viewModel.loadPage(page)
        } else {
            binding.pageIndexInput.setText(currentPageIndex.toString())
        }
        binding.pageIndexInput.clearFocus()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(binding.inputLink.windowToken, 0)
    }

    private fun consumePendingExternalUrl() {
        val url = pendingExternalUrl ?: return
        val binding = _binding ?: return
        pendingExternalUrl = null

        binding.inputLink.setText(url)
        binding.inputLink.setSelection(url.length)
        viewModel.parse(url)
    }

    private fun onDownloadClicked() {
        requestNotificationPermissionIfNeeded()
        val settingsRepository = requireContext().appContainer.settingsRepository
        if (!settingsRepository.shouldConfirmCellularDownload() || !isOnCellularNetwork()) {
            viewModel.download()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.parse_mobile_confirm_title)
            .setMessage(R.string.parse_mobile_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.parse_mobile_confirm_action) { _, _ ->
                viewModel.download()
            }
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val context = requireContext()
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun isOnCellularNetwork(): Boolean {
        val cm = requireContext().getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return true
        }
        val hasWifiLikeTransport =
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return !hasWifiLikeTransport && cm.isActiveNetworkMetered
    }

    private data class VideoCardDisplay(
        val title: String,
        val description: String,
        val coverUrl: String,
        val stat: MediaStat,
    )

    private fun resolveVideoCardDisplay(
        state: ParseUiState,
        info: MediaInfo,
        selectedItem: MediaItem?,
    ): VideoCardDisplay {
        val isCollectionVideo = state.collectionMode && info.type == MediaType.Video && info.collection
        val previewItem = if (isCollectionVideo) {
            state.collectionPreviewIndex?.let { index -> state.items.getOrNull(index) }
        } else {
            null
        }

        if (previewItem != null) {
            val title = previewItem.title.trim().ifBlank {
                info.nfo.showTitle?.trim().orEmpty()
            }
            val description = previewItem.description.trim().ifBlank {
                info.nfo.intro?.trim().orEmpty()
            }
            val coverUrl = previewItem.coverUrl.ifBlank {
                info.nfo.thumbs.firstOrNull { it.id == "cover" }?.url
                    ?: info.nfo.thumbs.firstOrNull { it.id == "ugc" }?.url
                    ?: info.nfo.thumbs.firstOrNull()?.url
                    ?: ""
            }
            val stat = state.collectionPreviewStat ?: info.nfo.stat
            return VideoCardDisplay(
                title = title,
                description = description,
                coverUrl = coverUrl,
                stat = stat,
            )
        }

        if (isCollectionVideo) {
            val title = info.nfo.showTitle?.trim().ifNullOrBlank {
                selectedItem?.title?.trim().orEmpty()
            }
            val description = info.nfo.intro?.trim().ifNullOrBlank {
                selectedItem?.description?.trim().orEmpty()
            }
            val coverUrl = info.nfo.thumbs.firstOrNull { it.id == "ugc" }?.url
                ?: info.nfo.thumbs.firstOrNull()?.url
                ?: selectedItem?.coverUrl.orEmpty()
            return VideoCardDisplay(
                title = title,
                description = description,
                coverUrl = coverUrl,
                stat = selectedItem?.stat ?: state.selectedItemStat ?: info.nfo.stat,
            )
        }

        if (info.type == MediaType.Video) {
            val title = resolveNonCollectionVideoTitle(state, info, selectedItem)
            val description = selectedItem?.description?.trim().ifNullOrBlank {
                info.nfo.intro?.trim().orEmpty()
            }
            val coverUrl = selectedItem?.coverUrl?.ifBlank { null }
                ?: info.nfo.thumbs.firstOrNull()?.url
                ?: ""
            return VideoCardDisplay(
                title = title,
                description = description,
                coverUrl = coverUrl,
                stat = info.nfo.stat,
            )
        }

        val title = selectedItem?.title?.trim().ifNullOrBlank {
            info.nfo.showTitle?.trim().orEmpty()
        }
        val fallbackDescription = when (info.type) {
            MediaType.Favorite,
            MediaType.WatchLater,
            -> ""
            else -> info.nfo.intro?.trim().orEmpty()
        }
        val description = selectedItem?.description?.trim().ifNullOrBlank {
            fallbackDescription
        }
        val coverUrl = selectedItem?.coverUrl?.ifBlank { null }
            ?: info.nfo.thumbs.firstOrNull()?.url
            ?: ""
        val stat = when (info.type) {
            MediaType.Favorite,
            MediaType.WatchLater,
            -> selectedItem?.stat ?: state.selectedItemStat ?: MediaStat()
            else -> selectedItem?.stat ?: state.selectedItemStat ?: info.nfo.stat
        }
        return VideoCardDisplay(
            title = title,
            description = description,
            coverUrl = coverUrl,
            stat = stat,
        )
    }

    private fun resolveNonCollectionVideoTitle(
        state: ParseUiState,
        info: MediaInfo,
        selectedItem: MediaItem?,
    ): String {
        val sectionTitle = state.sections
            ?.tabs
            ?.firstOrNull { it.id == state.selectedSectionId }
            ?.name
            ?.trim()
            .orEmpty()
        if (sectionTitle.isNotBlank()) return sectionTitle

        val baseTitle = info.nfo.showTitle?.trim().orEmpty()
        if (!info.collection && baseTitle.isNotBlank()) return baseTitle

        if (info.collection && state.items.size == 1) {
            val singleTitle = selectedItem?.title?.trim().orEmpty()
            if (singleTitle.isNotBlank()) return singleTitle
        }

        return if (baseTitle.isNotBlank()) {
            baseTitle
        } else {
            selectedItem?.title?.trim().orEmpty()
        }
    }

    private fun String?.ifNullOrBlank(fallback: () -> String): String {
        return this?.takeIf { it.isNotBlank() } ?: fallback()
    }

    private fun hasAnyStat(stat: MediaStat): Boolean {
        return listOf(
            stat.play,
            stat.danmaku,
            stat.reply,
            stat.like,
            stat.coin,
            stat.favorite,
            stat.share,
        ).any { it != null }
    }

    private fun bindStats(stat: MediaStat) {
        val hasAny = hasAnyStat(stat)
        binding.statScroll.visibility = if (hasAny) View.VISIBLE else View.GONE
        updateStat(binding.statPlayItem, binding.statPlayText, stat.play)
        updateStat(binding.statDanmakuItem, binding.statDanmakuText, stat.danmaku)
        updateStat(binding.statReplyItem, binding.statReplyText, stat.reply)
        updateStat(binding.statLikeItem, binding.statLikeText, stat.like)
        updateStat(binding.statCoinItem, binding.statCoinText, stat.coin)
        updateStat(binding.statFavoriteItem, binding.statFavoriteText, stat.favorite)
        updateStat(binding.statShareItem, binding.statShareText, stat.share)
    }

    private fun updateStat(container: View, label: TextView, value: Long?) {
        if (value == null) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        label.text = formatStat(value)
    }

    private fun formatStat(value: Long): String {
        return when {
            value >= 100_000_000L -> String.format("%.1f亿", value / 100_000_000.0)
            value >= 10_000L -> String.format("%.1f万", value / 10_000.0)
            else -> value.toString()
        }
    }
}
