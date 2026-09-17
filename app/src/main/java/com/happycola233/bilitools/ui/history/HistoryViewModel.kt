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
import com.happycola233.bilitools.data.model.HistorySearchResult
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

/** 已加载的一页历史记录；[HistoryUiState.pages] 中按页码升序且连续。 */
data class HistoryLoadedPage(
    val page: Int,
    val items: List<HistoryItem>,
)

/** 请求列表滚动到 [page] 的第一条；[id] 单调递增，保证重复跳转同一页也能触发。 */
data class HistoryScrollRequest(
    val page: Int,
    val id: Int,
)

data class HistoryUiState(
    val isLoggedIn: Boolean = false,
    /** 整体重载：刷新、切换分类、筛选、跳转到尚未加载的页。 */
    val loading: Boolean = false,
    /** 滑到列表底部后自动追加下一页。 */
    val appending: Boolean = false,
    /** 在列表顶部继续下拉时向前加载上一页。 */
    val prepending: Boolean = false,
    /** 追加下一页失败；停在底部时不再自动重试，改为用户点击重试。 */
    val appendFailed: Boolean = false,
    val tabs: List<HistoryTab> = emptyList(),
    val selectedBusiness: String? = null,
    /** 当前页：列表顶部可见项所属的页码，随滚动变化。 */
    val page: Int = 1,
    val totalPages: Int = 0,
    val total: Int = 0,
    /** 最后一个已加载页之后是否还有更多。 */
    val hasMore: Boolean = false,
    /** 已加载的连续页，按页码升序。 */
    val pages: List<HistoryLoadedPage> = emptyList(),
    val scrollRequest: HistoryScrollRequest? = null,
    val filter: HistoryFilter = HistoryFilter(),
    val errorText: String? = null,
) {
    val firstLoadedPage: Int? get() = pages.firstOrNull()?.page
    val lastLoadedPage: Int? get() = pages.lastOrNull()?.page
    val isEmpty: Boolean get() = pages.all { it.items.isEmpty() }

    val canLoadPrevPage: Boolean get() = (firstLoadedPage ?: 1) > 1
    val canLoadNextPage: Boolean
        get() {
            val last = lastLoadedPage ?: return false
            return hasMore && (totalPages <= 0 || last < totalPages)
        }

    val canGoPrev: Boolean get() = page > 1
    val canGoNext: Boolean get() = pages.any { it.page > page && it.items.isNotEmpty() } || canLoadNextPage
}

/**
 * 列表项唯一键：既作为 LazyColumn 的 key，也用于跨页去重
 * （翻页期间产生的新记录会让相邻两页出现同一条）。
 */
internal val HistoryItem.listKey: String
    get() = "${bvid ?: uri ?: oid ?: title}-$viewAt"

class HistoryViewModel(
    private val authRepository: AuthRepository,
    private val extrasRepository: ExtrasRepository,
    private val strings: StringProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var reloadJob: Job? = null
    private var appendJob: Job? = null
    private var prependJob: Job? = null
    private var scrollRequestId = 0

    init {
        refresh()
    }

    fun refresh() {
        reload(refreshTabs = true, targetPage = 1)
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
        reload(refreshTabs = false, targetPage = 1)
    }

    /** 跳转到某页：缓存页直接定位，相邻下一页追加后定位，其余页重新加载。 */
    fun goToPage(page: Int) {
        val current = _state.value
        val maxPage = current.totalPages.takeIf { it > 0 } ?: Int.MAX_VALUE
        val target = page.coerceIn(1, maxPage)
        if (current.loading) return
        if (current.pages.any { it.page == target }) {
            _state.update {
                it.copy(
                    page = target,
                    scrollRequest = nextScrollRequest(target),
                    errorText = null,
                )
            }
            return
        }
        if (target == (current.lastLoadedPage ?: 0) + 1 && current.canLoadNextPage) {
            _state.update { it.copy(page = target, scrollRequest = nextScrollRequest(target), errorText = null) }
            loadNextPage()
            return
        }
        _state.update { it.copy(page = target, errorText = null) }
        reload(refreshTabs = false, targetPage = target)
    }

    fun goToNextPage() {
        if (!_state.value.canGoNext) return
        goToPage(_state.value.page + 1)
    }

    fun goToPrevPage() {
        if (!_state.value.canGoPrev) return
        goToPage(_state.value.page - 1)
    }

    /** 列表滚动后同步顶部可见项所属页码；整体重载期间以目标页为准，忽略旧列表的上报。 */
    fun onVisiblePageChange(page: Int) {
        _state.update {
            if (it.loading || it.scrollRequest != null || it.page == page) it else it.copy(page = page)
        }
    }

    fun onScrollHandled(requestId: Int) {
        _state.update { if (it.scrollRequest?.id == requestId) it.copy(scrollRequest = null) else it }
    }

    /** 空页、去重后无新增的页仍占服务端页码；继续前进直到取得新条目或接口明确结束。 */
    fun loadNextPage() {
        val current = _state.value
        if (current.loading || current.appending || !current.canLoadNextPage) return
        _state.update { it.copy(appending = true, appendFailed = false, errorText = null) }
        appendJob = viewModelScope.launch {
            try {
                do {
                    val nextPage = _state.value.lastLoadedPage!! + 1
                    val result = fetchPage(nextPage)
                    val newItems = result.list.distinctFrom(_state.value.pages)
                    _state.update {
                        it.copy(
                            pages = it.pages + HistoryLoadedPage(nextPage, newItems),
                            hasMore = result.hasMore,
                            total = result.total,
                            totalPages = result.totalPages,
                        )
                    }
                } while (newItems.isEmpty() && _state.value.canLoadNextPage)
                _state.update { it.copy(appending = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failLoad(error) { it.copy(appending = false, appendFailed = true, scrollRequest = null) }
            }
        }
    }

    /** 顶部继续下拉：在已加载内容之前插入上一页。 */
    fun loadPrevPage() {
        val current = _state.value
        val first = current.firstLoadedPage ?: return
        if (current.loading || current.prepending || !current.canLoadPrevPage) return
        _state.update { it.copy(prepending = true, errorText = null) }
        prependJob = viewModelScope.launch {
            try {
                val result = fetchPage(first - 1)
                _state.update {
                    it.copy(
                        prepending = false,
                        pages = listOf(HistoryLoadedPage(first - 1, result.list.distinctFrom(it.pages))) + it.pages,
                        total = result.total,
                        totalPages = result.totalPages,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failLoad(error) { it.copy(prepending = false) }
            }
        }
    }

    fun applyFilter(filter: HistoryFilter) {
        _state.update { it.copy(filter = filter, page = 1, errorText = null) }
        reload(refreshTabs = false, targetPage = 1)
    }

    fun refreshLoginState() {
        val loggedIn = authRepository.isLoggedIn()
        if (loggedIn != _state.value.isLoggedIn) {
            if (loggedIn) {
                refresh()
            } else {
                cancelAllLoads()
                _state.update { it.loggedOut() }
            }
        }
    }

    private fun reload(refreshTabs: Boolean, targetPage: Int) {
        cancelAllLoads()
        // 查询栏目也是重载的一部分，先置 loading，避免旧列表在等待栏目时又发起追加请求。
        _state.update {
            it.copy(
                loading = true,
                appending = false,
                prepending = false,
                appendFailed = false,
                scrollRequest = null,
                errorText = null,
            )
        }
        reloadJob = viewModelScope.launch {
            if (!authRepository.isLoggedIn()) {
                _state.update { it.loggedOut() }
                return@launch
            }
            try {
                val current = _state.value
                val tabs = if (refreshTabs || current.tabs.isEmpty()) {
                    extrasRepository.getHistoryTabs()
                } else {
                    current.tabs
                }
                // 首次进入固定为视频；刷新保留用户手动选中的栏目，不使用历史游标决定入口。
                val business = current.selectedBusiness?.takeIf { selected -> tabs.any { it.type == selected } }
                    ?: DEFAULT_HISTORY_BUSINESS
                val page = targetPage.coerceAtLeast(1)
                _state.update { it.copy(isLoggedIn = true, tabs = tabs, selectedBusiness = business, page = page) }
                val result = fetchPage(page)
                _state.update {
                    it.copy(
                        loading = false,
                        totalPages = result.totalPages,
                        total = result.total,
                        hasMore = result.hasMore,
                        pages = listOf(HistoryLoadedPage(page, result.list.distinctBy { item -> item.listKey })),
                        scrollRequest = nextScrollRequest(page),
                    )
                }
                if (_state.value.isEmpty && _state.value.canLoadNextPage) loadNextPage()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failLoad(error) { it.copy(loading = false) }
            }
        }
    }

    private suspend fun fetchPage(page: Int): HistorySearchResult {
        val current = _state.value
        val request = current.filter.toSearchParams(
            page = page,
            business = current.selectedBusiness ?: DEFAULT_HISTORY_BUSINESS,
        )
        return extrasRepository.getHistorySearch(request)
    }

    private fun failLoad(error: Throwable, resetProgress: (HistoryUiState) -> HistoryUiState) {
        if (error is BiliHttpException && error.code == -101) {
            _state.update { it.loggedOut() }
            return
        }
        val message = error.message ?: strings.get(R.string.history_error_load)
        _state.update { resetProgress(it).copy(isLoggedIn = true, errorText = message) }
    }

    private fun cancelAllLoads() {
        reloadJob?.cancel()
        appendJob?.cancel()
        prependJob?.cancel()
    }

    private fun nextScrollRequest(page: Int): HistoryScrollRequest {
        return HistoryScrollRequest(page = page, id = ++scrollRequestId)
    }

    private fun HistoryUiState.loggedOut(): HistoryUiState {
        return copy(
            isLoggedIn = false,
            loading = false,
            appending = false,
            prepending = false,
            appendFailed = false,
            tabs = emptyList(),
            selectedBusiness = null,
            page = 1,
            totalPages = 0,
            total = 0,
            hasMore = false,
            pages = emptyList(),
            scrollRequest = null,
            errorText = strings.get(R.string.history_login_required),
        )
    }

    /** 去掉已存在于 [loaded] 各页中的记录，保证列表 key 唯一。 */
    private fun List<HistoryItem>.distinctFrom(loaded: List<HistoryLoadedPage>): List<HistoryItem> {
        val existing = loaded.flatMapTo(HashSet()) { page -> page.items.map { it.listKey } }
        return filter { existing.add(it.listKey) }
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
