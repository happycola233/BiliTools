package com.happycola233.bilitools.ui.history

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.PaginationTestEnvironment
import com.happycola233.bilitools.awaitPagination
import com.happycola233.bilitools.historyPage
import com.happycola233.bilitools.historyTabs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistoryPaginationTest {
    private lateinit var env: PaginationTestEnvironment
    private var model: HistoryViewModel? = null

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        env = PaginationTestEnvironment()
    }

    @After fun tearDown() {
        model?.viewModelScope?.cancel()
        env.cookies.clear()
        Dispatchers.resetMain()
    }

    @Test fun opensVideoDespiteLiveCursorAndRefreshKeepsManualTab() = runTest {
        env.respond = { url ->
            if (url.encodedPath.endsWith("/cursor")) historyTabs else historyPage(1, 1)
        }
        val vm = env.history().also { model = it }
        awaitPagination { !vm.state.value.loading }
        assertEquals("archive", env.historyPages().single().queryParameter("business"))
        assertEquals("archive", vm.state.value.selectedBusiness)
        vm.selectBusiness("live")
        awaitPagination { !vm.state.value.loading }
        vm.refresh()
        assertTrue(vm.state.value.loading)
        vm.loadNextPage() // 栏目请求期间不能从旧列表发起分页。
        awaitPagination { !vm.state.value.loading }
        assertEquals("live", vm.state.value.selectedBusiness)
        assertEquals(listOf("1", "1", "1"), env.historyPages().map { it.queryParameter("pn") })
    }

    @Test fun emptyAndDuplicatePagesAdvanceUntilNewItemsWithoutStoppingAtShortPage() = runTest {
        env.respond = { url ->
            if (url.encodedPath.endsWith("/cursor")) historyTabs else when (url.queryParameter("pn")) {
                "1" -> historyPage(1, 1, 2)
                "2" -> historyPage(2)
                "3" -> historyPage(3, 1, 2)
                else -> historyPage(4, 3, 3, more = false)
            }
        }
        val vm = env.history().also { model = it }
        awaitPagination { !vm.state.value.loading }
        assertTrue(vm.state.value.canLoadNextPage)
        vm.loadNextPage()
        vm.loadNextPage()
        awaitPagination { !vm.state.value.appending }
        assertEquals(listOf(1, 2, 3, 4), vm.state.value.pages.map { it.page })
        assertEquals(listOf(2, 0, 0, 1), vm.state.value.pages.map { it.items.size })
        assertEquals(listOf("1", "2", "3", "4"), env.historyPages().map { it.queryParameter("pn") })
        assertFalse(vm.state.value.canLoadNextPage)
        val requestCount = env.historyPages().size
        vm.goToPage(4)
        assertEquals(4, vm.state.value.scrollRequest?.page)
        vm.onVisiblePageChange(1)
        assertEquals(4, vm.state.value.page)
        vm.onScrollHandled(vm.state.value.scrollRequest!!.id)
        vm.onVisiblePageChange(1)
        assertEquals(1, vm.state.value.page)
        assertEquals(requestCount, env.historyPages().size)
    }

    @Test fun initialEmptyPageLoadsFollowingPageAndFailureCanBeRetried() = runTest {
        var failSecond = true
        env.respond = { url ->
            if (url.encodedPath.endsWith("/cursor")) historyTabs else when (url.queryParameter("pn")) {
                "1" -> historyPage(1)
                else -> if (failSecond) """{"code":-500,"message":"暂时无法加载"}""" else historyPage(2, 2, more = false)
            }
        }
        val vm = env.history().also { model = it }
        awaitPagination { vm.state.value.appendFailed }
        assertTrue(vm.state.value.canLoadNextPage)
        assertFalse(vm.state.value.appending)
        failSecond = false
        vm.loadNextPage()
        awaitPagination { !vm.state.value.appending }
        assertFalse(vm.state.value.appendFailed)
        assertEquals(2L, vm.state.value.pages.last().items.single().oid)
    }

    @Test fun emptyTrailingPagesDisableNextButton() = runTest {
        env.respond = { url ->
            if (url.encodedPath.endsWith("/cursor")) historyTabs else when (url.queryParameter("pn")) {
                "1" -> historyPage(1, 1)
                else -> historyPage(2, more = false)
            }
        }
        val vm = env.history().also { model = it }
        awaitPagination { !vm.state.value.loading }
        vm.loadNextPage()
        awaitPagination { !vm.state.value.appending }
        assertFalse(vm.state.value.canGoNext)
        assertEquals(2, vm.state.value.lastLoadedPage)
    }

    @Test fun changingTabCancelsInFlightAppendSoOldItemsCannotEnterNewTab() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        env.respond = { url ->
            when {
                url.encodedPath.endsWith("/cursor") -> historyTabs
                url.queryParameter("business") == "live" -> historyPage(1, 99, more = false)
                url.queryParameter("pn") == "2" -> {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    returned.countDown()
                    historyPage(2, 2)
                }
                else -> historyPage(1, 1)
            }
        }
        try {
            val vm = env.history().also { model = it }
            awaitPagination { !vm.state.value.loading }
            vm.loadNextPage()
            awaitPagination { entered.count == 0L }
            vm.selectBusiness("live")
            awaitPagination { !vm.state.value.loading }
            release.countDown()
            awaitPagination { returned.count == 0L }
            assertEquals("live", vm.state.value.selectedBusiness)
            assertEquals(listOf(99L), vm.state.value.pages.flatMap { it.items }.map { it.oid })
            assertFalse(vm.state.value.appending)
        } finally {
            release.countDown()
        }
    }
}
