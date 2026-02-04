package com.happycola233.bilitools.ui.history

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.model.HistoryItem
import com.happycola233.bilitools.databinding.ActivityHistoryBinding
import com.happycola233.bilitools.databinding.DialogHistoryFilterBinding
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.darkPureBlackOverlayStyleResOrNull
import com.happycola233.bilitools.ui.ExternalDownloadContract
import com.happycola233.bilitools.ui.MainActivity
import com.happycola233.bilitools.ui.overlayStyleResOrNull
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var historyAdapter: HistoryAdapter

    private val viewModel: HistoryViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }

    private var latestState = HistoryUiState()
    private var renderedTabTypes: List<String> = emptyList()
    private var suppressTabSelection = false
    private var lastToastError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        applyThemeOverlays()
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupToolbar()
        setupPagerControls()
        setupList()
        setupTabs()
        collectState()
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshLoginState()
    }

    private fun applyThemeOverlays() {
        val settings = applicationContext
            .appContainer
            .settingsRepository
            .currentSettings()
        settings.themeColor.overlayStyleResOrNull()?.let { theme.applyStyle(it, true) }
        settings.darkPureBlackOverlayStyleResOrNull(resources.configuration.uiMode)
            ?.let { theme.applyStyle(it, true) }
    }

    private fun setupInsets() {
        val listBaseBottom = binding.historyList.paddingBottom
        val emptyBaseBottom = binding.historyEmptyState.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.historyRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.historyAppBar.updatePadding(top = bars.top)
            binding.historyList.updatePadding(bottom = listBaseBottom + bars.bottom)
            binding.historyEmptyState.updatePadding(bottom = emptyBaseBottom + bars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        binding.historyToolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back_24)
        binding.historyToolbar.setNavigationOnClickListener { finish() }
        binding.historyToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_history_refresh -> {
                    viewModel.refresh()
                    true
                }
                R.id.action_history_filter -> {
                    showFilterDialog(latestState.filter)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupPagerControls() {
        binding.historyPagePrev.setOnClickListener { viewModel.goToPrevPage() }
        binding.historyPageNext.setOnClickListener { viewModel.goToNextPage() }
        binding.historyPageInput.setOnEditorActionListener { _, actionId, event ->
            val handled = actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (handled) {
                applyPageInput()
            }
            handled
        }
        binding.historyPageInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) applyPageInput()
        }
    }

    private fun setupList() {
        historyAdapter = HistoryAdapter(
            onDownload = { item -> jumpToParse(item) },
            onAuthor = { item -> openAuthorSpace(item) },
        )
        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = historyAdapter
        binding.historyList.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.isNestedScrollingEnabled = v.canScrollVertically(-1) || v.canScrollVertically(1)
        }
    }

    private fun setupTabs() {
        binding.historyTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressTabSelection) return
                val type = tab.tag as? String ?: return
                viewModel.selectBusiness(type)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    latestState = state
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: HistoryUiState) {
        renderTabs(state)
        historyAdapter.submitList(state.items)

        if (!binding.historyPageInput.hasFocus()) {
            val pageText = state.page.toString()
            if (binding.historyPageInput.text?.toString() != pageText) {
                binding.historyPageInput.setText(pageText)
            }
        }

        binding.historyPagePrev.isEnabled = state.page > 1 && !state.loading
        binding.historyPageNext.isEnabled = state.hasMore && !state.loading
        binding.historyPageInput.isEnabled = !state.loading
        binding.historyPageHint.text = if (state.total > 0) {
            getString(R.string.history_page_status_with_total, state.page, state.total)
        } else {
            getString(R.string.history_page_status, state.page)
        }

        val showLoading = state.loading && state.items.isEmpty()
        binding.historyLoading.visibility = if (showLoading) View.VISIBLE else View.GONE

        val emptyVisible = !state.loading && state.items.isEmpty()
        binding.historyEmptyState.visibility = if (emptyVisible) View.VISIBLE else View.GONE
        binding.historyEmptyText.text = when {
            !state.isLoggedIn -> getString(R.string.history_login_required)
            !state.errorText.isNullOrBlank() -> state.errorText
            else -> getString(R.string.history_empty)
        }

        if (!state.errorText.isNullOrBlank() && state.items.isNotEmpty()) {
            if (state.errorText != lastToastError) {
                Toast.makeText(this, state.errorText, Toast.LENGTH_SHORT).show()
                lastToastError = state.errorText
            }
        } else if (state.errorText.isNullOrBlank()) {
            lastToastError = null
        }
    }

    private fun renderTabs(state: HistoryUiState) {
        val tabs = state.tabs
        if (tabs.isEmpty()) {
            binding.historyTabs.visibility = View.GONE
            renderedTabTypes = emptyList()
            return
        }
        binding.historyTabs.visibility = View.VISIBLE

        val tabTypes = tabs.map { it.type }
        if (tabTypes != renderedTabTypes) {
            suppressTabSelection = true
            binding.historyTabs.removeAllTabs()
            tabs.forEach { tab ->
                binding.historyTabs.addTab(
                    binding.historyTabs.newTab().setText(tab.name).setTag(tab.type),
                    false,
                )
            }
            renderedTabTypes = tabTypes
            suppressTabSelection = false
        }

        val selectedIndex = tabs.indexOfFirst { it.type == state.selectedBusiness }
        if (selectedIndex >= 0 && selectedIndex != binding.historyTabs.selectedTabPosition) {
            suppressTabSelection = true
            binding.historyTabs.getTabAt(selectedIndex)?.select()
            suppressTabSelection = false
        }
    }

    private fun showFilterDialog(current: HistoryFilter) {
        val dialogBinding = DialogHistoryFilterBinding.inflate(layoutInflater)

        dialogBinding.historyFilterKeywordInput.setText(current.keyword)
        dialogBinding.historyFilterDurationGroup.check(
            when (current.duration) {
                HistoryDurationFilter.All -> R.id.history_filter_duration_all
                HistoryDurationFilter.Under10 -> R.id.history_filter_duration_under_10
                HistoryDurationFilter.Between10And30 -> R.id.history_filter_duration_10_30
                HistoryDurationFilter.Between30And60 -> R.id.history_filter_duration_30_60
                HistoryDurationFilter.Over60 -> R.id.history_filter_duration_over_60
            },
        )
        dialogBinding.historyFilterTimeGroup.check(
            when (current.time) {
                HistoryTimeFilter.All -> R.id.history_filter_time_all
                HistoryTimeFilter.Today -> R.id.history_filter_time_today
                HistoryTimeFilter.Yesterday -> R.id.history_filter_time_yesterday
                HistoryTimeFilter.Week -> R.id.history_filter_time_week
                HistoryTimeFilter.Custom -> R.id.history_filter_time_custom
            },
        )
        dialogBinding.historyFilterDeviceGroup.check(
            when (current.device) {
                HistoryDeviceFilter.All -> R.id.history_filter_device_all
                HistoryDeviceFilter.Pc -> R.id.history_filter_device_pc
                HistoryDeviceFilter.Phone -> R.id.history_filter_device_phone
                HistoryDeviceFilter.Pad -> R.id.history_filter_device_pad
                HistoryDeviceFilter.Tv -> R.id.history_filter_device_tv
            },
        )

        var customStartUtc = current.customStartUtcMillis
        var customEndUtc = current.customEndUtcMillis

        fun updateCustomRangeVisibility() {
            val checkedId = dialogBinding.historyFilterTimeGroup.checkedChipId
            val customSelected = checkedId == R.id.history_filter_time_custom
            dialogBinding.historyFilterCustomRangeContainer.visibility =
                if (customSelected) View.VISIBLE else View.GONE
        }

        fun updateCustomRangeText() {
            dialogBinding.historyFilterCustomRangeText.text = if (customStartUtc == null || customEndUtc == null) {
                getString(R.string.history_filter_custom_range_none)
            } else {
                getString(
                    R.string.history_filter_custom_range_value,
                    formatUtcDate(customStartUtc),
                    formatUtcDate(customEndUtc),
                )
            }
        }

        dialogBinding.historyFilterTimeGroup.setOnCheckedStateChangeListener { _, _ ->
            updateCustomRangeVisibility()
        }

        dialogBinding.historyFilterCustomRangeButton.setOnClickListener {
            val pickerBuilder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.history_filter_custom_range_pick)
            if (customStartUtc != null && customEndUtc != null) {
                pickerBuilder.setSelection(Pair(customStartUtc, customEndUtc))
            }
            val picker = pickerBuilder.build()
            picker.addOnPositiveButtonClickListener { selection ->
                customStartUtc = selection.first
                customEndUtc = selection.second
                updateCustomRangeText()
            }
            picker.show(supportFragmentManager, "history_date_range_picker")
        }

        updateCustomRangeVisibility()
        updateCustomRangeText()

        MaterialAlertDialogBuilder(this)
            .setTitle(buildBoldTitle(getString(R.string.history_more_filters)))
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.history_filter_reset) { _, _ ->
                viewModel.applyFilter(HistoryFilter())
            }
            .setPositiveButton(R.string.history_filter_apply) { _, _ ->
                val filter = HistoryFilter(
                    keyword = dialogBinding.historyFilterKeywordInput.text?.toString().orEmpty(),
                    duration = when (dialogBinding.historyFilterDurationGroup.checkedChipId) {
                        R.id.history_filter_duration_under_10 -> HistoryDurationFilter.Under10
                        R.id.history_filter_duration_10_30 -> HistoryDurationFilter.Between10And30
                        R.id.history_filter_duration_30_60 -> HistoryDurationFilter.Between30And60
                        R.id.history_filter_duration_over_60 -> HistoryDurationFilter.Over60
                        else -> HistoryDurationFilter.All
                    },
                    time = when (dialogBinding.historyFilterTimeGroup.checkedChipId) {
                        R.id.history_filter_time_today -> HistoryTimeFilter.Today
                        R.id.history_filter_time_yesterday -> HistoryTimeFilter.Yesterday
                        R.id.history_filter_time_week -> HistoryTimeFilter.Week
                        R.id.history_filter_time_custom -> HistoryTimeFilter.Custom
                        else -> HistoryTimeFilter.All
                    },
                    device = when (dialogBinding.historyFilterDeviceGroup.checkedChipId) {
                        R.id.history_filter_device_pc -> HistoryDeviceFilter.Pc
                        R.id.history_filter_device_phone -> HistoryDeviceFilter.Phone
                        R.id.history_filter_device_pad -> HistoryDeviceFilter.Pad
                        R.id.history_filter_device_tv -> HistoryDeviceFilter.Tv
                        else -> HistoryDeviceFilter.All
                    },
                    customStartUtcMillis = customStartUtc,
                    customEndUtcMillis = customEndUtc,
                )
                viewModel.applyFilter(filter)
            }
            .show()
    }

    private fun buildBoldTitle(text: String): CharSequence {
        return SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun applyPageInput() {
        val currentPage = latestState.page
        val target = binding.historyPageInput.text?.toString()?.trim()?.toIntOrNull()
        if (target != null && target > 0) {
            viewModel.goToPage(target)
        } else {
            binding.historyPageInput.setText(currentPage.toString())
        }
        binding.historyPageInput.clearFocus()
        hideKeyboard()
    }

    private fun jumpToParse(item: HistoryItem) {
        val url = item.toParseUrl() ?: return
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(ExternalDownloadContract.EXTRA_URL, url)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun openAuthorSpace(item: HistoryItem) {
        val mid = item.authorMid ?: return
        openUrl("https://space.bilibili.com/$mid")
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, getString(R.string.history_open_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(binding.historyPageInput.windowToken, 0)
    }

    private fun formatUtcDate(utcMillis: Long?): String {
        if (utcMillis == null) return "--"
        return runCatching {
            Instant.ofEpochMilli(utcMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
        }.getOrDefault("--")
    }
}
