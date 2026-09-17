package com.happycola233.bilitools.ui.parse

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.PaginationTestEnvironment
import com.happycola233.bilitools.awaitPagination
import com.happycola233.bilitools.favoriteFolders
import com.happycola233.bilitools.favoritePage
import com.happycola233.bilitools.data.model.MediaType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
class ParseViewModelPaginationTest {
    private lateinit var env: PaginationTestEnvironment
    private lateinit var model: ParseViewModel

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        env = PaginationTestEnvironment()
        model = env.parse()
    }

    @After fun tearDown() {
        model.viewModelScope.cancel()
        env.cookies.clear()
        Dispatchers.resetMain()
    }

    @Test fun appendSkipsEmptyAndDuplicatePagesAndButtonUsesCachedPage() = runTest {
        env.respond = { url ->
            when {
                url.encodedPath.endsWith("/created/list-all") -> favoriteFolders
                url.encodedPath.endsWith("/resource/list") -> when (url.queryParameter("pn")) {
                    "1" -> favoritePage(1, 2)
                    "2" -> favoritePage(nullItems = true)
                    "3" -> favoritePage(1, 2)
                    else -> favoritePage(3, more = false)
                }
                else -> """{"code":-404}"""
            }
        }
        prime()
        model.appendNextPage()
        model.appendNextPage()
        awaitPagination { !model.state.value.pagination.appending }
        val state = model.state.value
        assertEquals(listOf(1L, 2L, 3L), state.items.map { it.aid })
        assertEquals(listOf(0, 1), state.selectedItemIndices)
        assertEquals(1, state.previewItemIndex)
        assertEquals(listOf(2, 0, 0, 1), state.pagination.pages.map { it.itemKeys.size })
        assertEquals(listOf("1", "2", "3", "4"), env.favoritePages().map { it.queryParameter("pn") })
        model.loadPage(4)
        assertEquals(4, model.state.value.pagination.scrollRequest?.page)
        model.onVisiblePageChange(1)
        assertEquals(4, model.state.value.pageIndex)
        model.onPageScrollHandled(model.state.value.pagination.scrollRequest!!.id)
        model.onVisiblePageChange(1)
        assertEquals(1, model.state.value.pageIndex)
        assertEquals(4, env.favoritePages().size)
    }

    @Test fun nextButtonAppendsAndFailureCanBeRetriedWithoutLosingSelection() = runTest {
        var fail = true
        env.respond = { url ->
            when {
                url.encodedPath.endsWith("/created/list-all") -> favoriteFolders
                url.encodedPath.endsWith("/resource/list") && url.queryParameter("pn") == "1" -> favoritePage(1, 2)
                url.encodedPath.endsWith("/resource/list") -> if (fail) """{"code":-500,"message":"暂时无法加载"}""" else favoritePage(3, more = false)
                else -> """{"code":-404}"""
            }
        }
        prime()
        model.loadNextPage()
        awaitPagination { model.state.value.pagination.appendError != null }
        assertEquals(listOf(0, 1), model.state.value.selectedItemIndices)
        assertEquals(listOf(1L, 2L), model.state.value.items.map { it.aid })
        assertNull(model.state.value.pagination.scrollRequest)
        fail = false
        model.appendNextPage()
        awaitPagination { !model.state.value.pagination.appending }
        assertNull(model.state.value.pagination.appendError)
        assertEquals(listOf(0, 1), model.state.value.selectedItemIndices)
        assertEquals(1, model.state.value.previewItemIndex)
        assertEquals(listOf(1L, 2L, 3L), model.state.value.items.map { it.aid })
    }

    @Test fun clearingWhileAppendIsInFlightDoesNotRestoreOldResult() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        env.respond = { url ->
            when {
                url.encodedPath.endsWith("/created/list-all") -> favoriteFolders
                url.encodedPath.endsWith("/resource/list") && url.queryParameter("pn") == "1" -> favoritePage(1, 2)
                url.encodedPath.endsWith("/resource/list") -> {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    returned.countDown()
                    favoritePage(3, more = false)
                }
                else -> """{"code":-404}"""
            }
        }
        try {
            prime()
            model.appendNextPage()
            awaitPagination { entered.count == 0L }
            model.clear()
            release.countDown()
            awaitPagination { returned.count == 0L }
            assertTrue(model.state.value.items.isEmpty())
            assertNull(model.state.value.mediaInfo)
            assertFalse(model.state.value.pagination.appending)
        } finally {
            release.countDown()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun prime() {
        val info = env.media.getMediaInfo("42", MediaType.Favorite)
        val state = ParseUiState(outputType = null).withNewList(info, 1, ParseScrollRequest(1, 1)).copy(
            selectedItemIndices = listOf(0, 1), previewItemIndex = 1,
        )
        val flow = ParseViewModel::class.java.getDeclaredField("_state")
            .apply { isAccessible = true }.get(model) as MutableStateFlow<ParseUiState>
        flow.value = state.copy(pagination = state.pagination.copy(scrollRequest = null))
    }
}
