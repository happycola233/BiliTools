package com.happycola233.bilitools.ui.parse

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.databinding.FragmentParseBinding
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.ExternalDownloadContract
import com.happycola233.bilitools.ui.normalizeHttpUrl
import com.happycola233.bilitools.ui.theme.rememberAndroidThemeColorScheme
import kotlinx.coroutines.launch

/**
 * Parse and download UI used by both:
 * 1) Main app home page
 * 2) External dialog entry (when another app shares/opens a URL)
 *
 * The Fragment intentionally owns platform side effects, while [ParseScreenContent]
 * owns all visual state and interaction rendering.
 */
class ParseFragment : Fragment() {
    private var _binding: FragmentParseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParseViewModel by viewModels {
        AppViewModelFactory(requireContext().appContainer)
    }

    private var pendingExternalUrl: String? = null
    private var externalMode = false
    private var closeAfterDownloadQueued = false
    private var offsetListener: com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener? = null

    private var inputText by mutableStateOf("")
    private var controlsOffsetPx by mutableFloatStateOf(0f)
    private var subtitleCopyDialogEntries by mutableStateOf<List<SubtitleCopyEntry>?>(null)
    private var aiSummaryCopyDialogEntries by mutableStateOf<List<AiSummaryCopyEntry>?>(null)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalMode = arguments?.getBoolean(ARG_EXTERNAL_MODE, false) == true
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
        if (!externalMode) {
            val appBar =
                requireActivity().findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
            offsetListener =
                com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
                    controlsOffsetPx = -verticalOffset.toFloat()
                }
            appBar?.addOnOffsetChangedListener(offsetListener)
        }

        binding.parseCompose.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        binding.parseCompose.setContent {
            val parseState by viewModel.state.collectAsState()
            val colorScheme = rememberAndroidThemeColorScheme()
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                motionScheme = MotionScheme.expressive(),
            ) {
                ParseScreenContent(
                    state = parseState,
                    inputText = inputText,
                    controlsOffsetPx = controlsOffsetPx,
                    externalMode = externalMode,
                    subtitleCopyDialogEntries = subtitleCopyDialogEntries,
                    aiSummaryCopyDialogEntries = aiSummaryCopyDialogEntries,
                    onInputChange = { next ->
                        inputText = next
                        if (next.isEmpty()) {
                            viewModel.clear()
                        }
                    },
                    onPaste = ::pasteFromClipboard,
                    onParse = { input -> viewModel.parse(input) },
                    onMediaTypeChange = viewModel::setMediaType,
                    onDownload = ::onDownloadClicked,
                    onSectionChange = viewModel::selectSection,
                    onSelectAllItems = viewModel::selectAllItems,
                    onClearSelectedItems = viewModel::clearSelectedItems,
                    onLoadPrevPage = viewModel::loadPrevPage,
                    onLoadNextPage = viewModel::loadNextPage,
                    onLoadPage = viewModel::loadPage,
                    onItemClick = viewModel::onItemRowClick,
                    onItemSelectionChange = { index, _ -> viewModel.toggleItemSelection(index) },
                    onFormatChange = viewModel::setFormat,
                    onOutputTypeChange = viewModel::setOutputType,
                    onCollectionModeChange = viewModel::setCollectionMode,
                    onResolutionModeChange = viewModel::setResolutionMode,
                    onResolutionChange = viewModel::setResolution,
                    onCodecChange = viewModel::setCodec,
                    onAudioBitrateModeChange = viewModel::setAudioBitrateMode,
                    onAudioBitrateChange = viewModel::setAudioBitrate,
                    onSubtitleEnabledChange = viewModel::setSubtitleEnabled,
                    onSubtitleLanguageChange = viewModel::setSubtitleLanguage,
                    onCopySubtitles = viewModel::copySubtitlesNow,
                    onAiSummaryEnabledChange = viewModel::setAiSummaryEnabled,
                    onCopyAiSummaries = viewModel::copyAiSummariesNow,
                    onNfoCollectionEnabledChange = viewModel::setNfoCollectionEnabled,
                    onNfoSingleEnabledChange = viewModel::setNfoSingleEnabled,
                    onDanmakuLiveEnabledChange = viewModel::setDanmakuLiveEnabled,
                    onDanmakuHistoryEnabledChange = viewModel::setDanmakuHistoryEnabled,
                    onDanmakuDateChange = viewModel::setDanmakuDate,
                    onDanmakuHourChange = viewModel::setDanmakuHour,
                    onImageSelectionChange = viewModel::setImageSelection,
                    onDismissSubtitleCopyDialog = { subtitleCopyDialogEntries = null },
                    onDismissAiSummaryCopyDialog = { aiSummaryCopyDialogEntries = null },
                    onCopyCurrentSubtitle = ::handleCopyCurrentSubtitle,
                    onCopyAllSubtitles = ::handleCopyAllSubtitles,
                    onCopyCurrentAiSummary = ::handleCopyCurrentAiSummary,
                    onCopyAllAiSummaries = ::handleCopyAllAiSummaries,
                )
            }
        }

        consumePendingExternalUrl()
        collectState()
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshLoginState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val appBar = requireActivity().findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
        offsetListener?.let { appBar?.removeOnOffsetChangedListener(it) }
        offsetListener = null
        controlsOffsetPx = 0f
        closeAfterDownloadQueued = false
        subtitleCopyDialogEntries = null
        aiSummaryCopyDialogEntries = null
        _binding = null
    }

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        handleStateSideEffects(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ParseEvent.CopySingleSubtitle -> handleCopySingleSubtitle(event.entry)
                            is ParseEvent.ShowSubtitleCopyDialog -> {
                                if (event.entries.isNotEmpty()) {
                                    aiSummaryCopyDialogEntries = null
                                    subtitleCopyDialogEntries = event.entries
                                }
                            }
                            is ParseEvent.CopySingleAiSummary -> handleCopySingleAiSummary(event.entry)
                            is ParseEvent.ShowAiSummaryCopyDialog -> {
                                if (event.entries.isNotEmpty()) {
                                    subtitleCopyDialogEntries = null
                                    aiSummaryCopyDialogEntries = event.entries
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleStateSideEffects(state: ParseUiState) {
        state.notice?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            viewModel.clearNotice()
        }

        if (externalMode && closeAfterDownloadQueued) {
            when {
                state.lastDownload != null -> {
                    closeAfterDownloadQueued = false
                    (activity as? ExternalDownloadHost)?.onExternalDownloadQueued()
                }
                !state.downloadStarting && !state.error.isNullOrBlank() -> {
                    closeAfterDownloadQueued = false
                }
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java) ?: return
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (!text.isNullOrEmpty()) {
            inputText = text
        }
    }

    private fun consumePendingExternalUrl() {
        val url = pendingExternalUrl ?: return
        if (_binding == null) return
        pendingExternalUrl = null
        inputText = url
        viewModel.parse(url)
    }

    private fun onDownloadClicked() {
        fun startDownloadAction() {
            if (externalMode) {
                closeAfterDownloadQueued = true
            }
            viewModel.download()
        }
        requestNotificationPermissionIfNeeded()
        val settingsRepository = requireContext().appContainer.settingsRepository
        if (!settingsRepository.shouldConfirmCellularDownload() || !isOnCellularNetwork()) {
            startDownloadAction()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.parse_mobile_confirm_title)
            .setMessage(R.string.parse_mobile_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.parse_mobile_confirm_action) { _, _ ->
                startDownloadAction()
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

    private fun handleCopySingleSubtitle(entry: SubtitleCopyEntry) {
        val content = entry.content
        if (!content.isNullOrBlank()) {
            copyTextToClipboard(
                getString(R.string.parse_subtitle_copy_clip_label_single, entry.title),
                content,
            )
            Toast.makeText(
                requireContext(),
                getString(R.string.parse_subtitle_copy_single_done),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(
                requireContext(),
                entry.error ?: getString(R.string.parse_error_no_subtitle),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun handleCopySingleAiSummary(entry: AiSummaryCopyEntry) {
        val content = entry.content
        if (!content.isNullOrBlank()) {
            copyTextToClipboard(
                getString(R.string.parse_ai_summary_copy_clip_label_single, entry.title),
                content,
            )
            Toast.makeText(
                requireContext(),
                getString(R.string.parse_ai_summary_copy_single_done),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(
                requireContext(),
                entry.error ?: getString(R.string.parse_error_no_ai),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun copyTextToClipboard(label: String, content: String) {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
    }

    private fun handleCopyCurrentSubtitle(entry: SubtitleCopyEntry) {
        val content = entry.content
        if (content.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                entry.error ?: getString(R.string.parse_subtitle_copy_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        copyTextToClipboard(
            getString(R.string.parse_subtitle_copy_clip_label_single, entry.title),
            content,
        )
        Toast.makeText(
            requireContext(),
            getString(R.string.parse_subtitle_copy_current_done),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun handleCopyAllSubtitles(entries: List<SubtitleCopyEntry>) {
        val merged = buildMergedSubtitleText(entries)
        if (merged.isBlank()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.parse_subtitle_copy_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        copyTextToClipboard(getString(R.string.parse_subtitle_copy_clip_label_all), merged)
        Toast.makeText(
            requireContext(),
            getString(R.string.parse_subtitle_copy_all_done),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun handleCopyCurrentAiSummary(entry: AiSummaryCopyEntry) {
        val content = entry.content
        if (content.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                entry.error ?: getString(R.string.parse_ai_summary_copy_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        copyTextToClipboard(
            getString(R.string.parse_ai_summary_copy_clip_label_single, entry.title),
            content,
        )
        Toast.makeText(
            requireContext(),
            getString(R.string.parse_ai_summary_copy_current_done),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun handleCopyAllAiSummaries(entries: List<AiSummaryCopyEntry>) {
        val merged = buildMergedAiSummaryText(entries)
        if (merged.isBlank()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.parse_ai_summary_copy_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        copyTextToClipboard(getString(R.string.parse_ai_summary_copy_clip_label_all), merged)
        Toast.makeText(
            requireContext(),
            getString(R.string.parse_ai_summary_copy_all_done),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun buildMergedSubtitleText(entries: List<SubtitleCopyEntry>): String {
        return entries.mapNotNull { entry ->
            val content = entry.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val subtitlePart = entry.subtitleName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            "【${entry.title}$subtitlePart】\n$content"
        }.joinToString("\n\n")
    }

    private fun buildMergedAiSummaryText(entries: List<AiSummaryCopyEntry>): String {
        return entries.mapNotNull { entry ->
            val content = entry.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "【${entry.title}】\n$content"
        }.joinToString("\n\n")
    }

    interface ExternalDownloadHost {
        fun onExternalDownloadQueued()
    }

    companion object {
        private const val ARG_EXTERNAL_MODE = "arg_external_mode"

        fun newInstance(externalMode: Boolean = false): ParseFragment {
            return ParseFragment().apply {
                arguments = bundleOf(ARG_EXTERNAL_MODE to externalMode)
            }
        }
    }
}
