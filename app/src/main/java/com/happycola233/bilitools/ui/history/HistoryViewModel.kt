package com.happycola233.bilitools.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.StringProvider
import com.happycola233.bilitools.data.AuthRepository
import com.happycola233.bilitools.data.ExtrasRepository
import com.happycola233.bilitools.data.model.HistoryItem
import com.happycola233.bilitools.data.model.HistorySearchParams
import com.happycola233.bilitools.data.model.HistoryTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

enum class HistoryDurationFilter {
    All,
    Under10,
    Between10And30,
    Between30And60,
    Over60,
}

enum class HistoryTimeFilter {
    All,
    Today,
    Yesterday,
    Week,
    Custom,
}

enum class HistoryDeviceFilter(val apiValue: Int) {
    All(0),
    Pc(1),
    Phone(2),
    Pad(3),
    Tv(4),
}

data class HistoryFilter(
    val keyword: String = "",
    val duration: HistoryDurationFilter = HistoryDurationFilter.All,
    val time: HistoryTimeFilter = HistoryTimeFilter.All,
    val device: HistoryDeviceFilter = HistoryDeviceFilter.All,
    val customStartUtcMillis: Long? = null,
    val customEndUtcMillis: Long? = null,
)

data class HistoryUiState(
    val isLoggedIn: Boolean = false,
    val loading: Boolean = false,
    val tabs: List<HistoryTab> = emptyList(),
    val selectedBusiness: String? = null,
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val items: List<HistoryItem> = emptyList(),
    val filter: HistoryFilter = HistoryFilter(),
    val errorText: String? = null,
)

class HistoryViewModel(
    private val authRepository: AuthRepository,
    private val extrasRepository: ExtrasRepository,
    private val strings: StringProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadHistory(refreshTabs = true, targetPage = 1)
    }

    fun retry() {
        loadHistory(refreshTabs = false, targetPage = _state.value.page)
    }

    fun selectBusiness(type: String) {
        if (type == _state.value.selectedBusiness) return
        _state.update {
            it.copy(
                selectedBusiness = type,
                page = 1,
                errorText = null,
            )
        }
        loadHistory(refreshTabs = false, targetPage = 1)
    }

    fun goToPage(page: Int) {
        val target = page.coerceAtLeast(1)
        if (target == _state.value.page) return
        _state.update { it.copy(page = target, errorText = null) }
        loadHistory(refreshTabs = false, targetPage = target)
    }

    fun goToNextPage() {
        if (!_state.value.hasMore) return
        goToPage(_state.value.page + 1)
    }

    fun goToPrevPage() {
        if (_state.value.page <= 1) return
        goToPage(_state.value.page - 1)
    }

    fun applyFilter(filter: HistoryFilter) {
        _state.update { it.copy(filter = filter, page = 1, errorText = null) }
        loadHistory(refreshTabs = false, targetPage = 1)
    }

    fun refreshLoginState() {
        val loggedIn = authRepository.isLoggedIn()
        if (loggedIn != _state.value.isLoggedIn) {
            if (loggedIn) {
                refresh()
            } else {
                _state.update {
                    it.copy(
                        isLoggedIn = false,
                        loading = false,
                        tabs = emptyList(),
                        selectedBusiness = null,
                        page = 1,
                        total = 0,
                        hasMore = false,
                        items = emptyList(),
                        errorText = strings.get(R.string.history_login_required),
                    )
                }
            }
        }
    }

    private fun loadHistory(refreshTabs: Boolean, targetPage: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!authRepository.isLoggedIn()) {
                _state.update {
                    it.copy(
                        isLoggedIn = false,
                        loading = false,
                        tabs = emptyList(),
                        selectedBusiness = null,
                        page = 1,
                        total = 0,
                        hasMore = false,
                        items = emptyList(),
                        errorText = strings.get(R.string.history_login_required),
                    )
                }
                return@launch
            }

            try {
                val oldState = _state.value
                var tabs = oldState.tabs
                var selectedBusiness = oldState.selectedBusiness
                var page = targetPage.coerceAtLeast(1)

                if (refreshTabs || tabs.isEmpty() || selectedBusiness.isNullOrBlank()) {
                    val cursor = extrasRepository.getHistoryCursor()
                    tabs = cursor.tabs
                    val available = tabs.map { it.type }.toSet()
                    selectedBusiness = when {
                        !cursor.defaultBusiness.isNullOrBlank() &&
                            available.contains(cursor.defaultBusiness) -> {
                            cursor.defaultBusiness
                        }
                        !selectedBusiness.isNullOrBlank() &&
                            available.contains(selectedBusiness) -> {
                            selectedBusiness
                        }
                        tabs.isNotEmpty() -> tabs.first().type
                        !cursor.defaultBusiness.isNullOrBlank() -> cursor.defaultBusiness
                        else -> DEFAULT_HISTORY_BUSINESS
                    }
                    page = 1
                }

                _state.update {
                    it.copy(
                        isLoggedIn = true,
                        loading = true,
                        tabs = tabs,
                        selectedBusiness = selectedBusiness,
                        page = page,
                        errorText = null,
                    )
                }

                val request = _state.value.filter.toSearchParams(
                    page = page,
                    business = selectedBusiness ?: DEFAULT_HISTORY_BUSINESS,
                )
                val result = extrasRepository.getHistorySearch(request)

                _state.update {
                    it.copy(
                        isLoggedIn = true,
                        loading = false,
                        tabs = tabs,
                        selectedBusiness = selectedBusiness,
                        page = result.page.coerceAtLeast(1),
                        total = result.total,
                        hasMore = result.hasMore,
                        items = result.list,
                        errorText = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: BiliHttpException) {
                if (error.code == -101) {
                    _state.update {
                        it.copy(
                            isLoggedIn = false,
                            loading = false,
                            tabs = emptyList(),
                            selectedBusiness = null,
                            page = 1,
                            total = 0,
                            hasMore = false,
                            items = emptyList(),
                            errorText = strings.get(R.string.history_login_required),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoggedIn = true,
                            loading = false,
                            errorText = error.message ?: strings.get(R.string.history_error_load),
                            items = if (it.items.isNotEmpty()) it.items else emptyList(),
                        )
                    }
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isLoggedIn = true,
                        loading = false,
                        errorText = error.message ?: strings.get(R.string.history_error_load),
                        items = if (it.items.isNotEmpty()) it.items else emptyList(),
                    )
                }
            }
        }
    }

    private fun HistoryFilter.toSearchParams(
        page: Int,
        business: String,
    ): HistorySearchParams {
        val (arcMinDuration, arcMaxDuration) = when (duration) {
            HistoryDurationFilter.All -> 0 to 0
            HistoryDurationFilter.Under10 -> 0 to 599
            HistoryDurationFilter.Between10And30 -> 600 to 1800
            HistoryDurationFilter.Between30And60 -> 1800 to 3600
            HistoryDurationFilter.Over60 -> 3601 to 0
        }

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val startOfToday = today.atStartOfDay(zoneId).toEpochSecond()
        val (addTimeStart, addTimeEnd) = when (time) {
            HistoryTimeFilter.All -> 0L to 0L
            HistoryTimeFilter.Today -> startOfToday to 0L
            HistoryTimeFilter.Yesterday -> {
                val start = today.minusDays(1).atStartOfDay(zoneId).toEpochSecond()
                val end = startOfToday - 1
                start to end
            }
            HistoryTimeFilter.Week -> {
                val start = today
                    .minusDays(6)
                    .atStartOfDay(zoneId)
                    .toEpochSecond()
                start to 0L
            }
            HistoryTimeFilter.Custom -> {
                val startDate = customStartUtcMillis?.toLocalDateFromUtcMillis()
                val endDate = customEndUtcMillis?.toLocalDateFromUtcMillis()
                val start = startDate?.atStartOfDay(zoneId)?.toEpochSecond() ?: 0L
                val end = endDate
                    ?.plusDays(1)
                    ?.atStartOfDay(zoneId)
                    ?.toEpochSecond()
                    ?.minus(1)
                    ?: 0L
                start to end
            }
        }

        return HistorySearchParams(
            page = page,
            keyword = keyword.trim(),
            business = business,
            addTimeStart = addTimeStart,
            addTimeEnd = addTimeEnd,
            arcMaxDuration = arcMaxDuration,
            arcMinDuration = arcMinDuration,
            deviceType = device.apiValue,
        )
    }

    private fun Long.toLocalDateFromUtcMillis(): LocalDate {
        return Instant.ofEpochMilli(this)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
    }

    companion object {
        private const val DEFAULT_HISTORY_BUSINESS = "archive"
    }
}
