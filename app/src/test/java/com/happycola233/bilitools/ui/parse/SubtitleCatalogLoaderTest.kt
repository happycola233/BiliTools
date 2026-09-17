package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.SubtitleInfo
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCatalogLoaderTest {
    private val chinese = SubtitleInfo("zh-Hans", "中文", "https://example.invalid/zh")
    private val english = SubtitleInfo("en", "英语", "https://example.invalid/en")

    @Test
    fun batchResolvesMissingCidAndCountsEveryCollectionPage() = runBlocking {
        val resolvedIds = mutableListOf<Long>()
        val requestedTargets = mutableListOf<SubtitleTarget>()
        val loader = SubtitleCatalogLoader(
            resolve = { item ->
                resolvedIds += item.aid!!
                item.copy(cid = item.cid ?: 11)
            },
            expand = { item -> listOf(item.copy(cid = 21), item.copy(cid = 22)) },
            fetch = { target ->
                requestedTargets += target
                if (target.cid == 21L) listOf(chinese, english) else listOf(chinese)
            },
        )
        var catalog = SubtitleCatalog()
        loader.load(
            listOf(SubtitleCatalogSource(item(1, cid = null)), SubtitleCatalogSource(item(2), expandPages = true)),
        ) { catalog = it }

        assertEquals(listOf(1L, 2L, 2L), resolvedIds)
        assertEquals(listOf(SubtitleTarget(1, 11), SubtitleTarget(2, 21), SubtitleTarget(2, 22)), requestedTargets)
        assertEquals(3, catalog.total)
        assertEquals(3, catalog.completed)
        assertEquals(mapOf("zh-Hans" to 3, "en" to 1), catalog.availableCounts)
    }

    @Test
    fun loaderLimitsParallelRequestsToThree() = runBlocking {
        var active = 0
        var maxActive = 0
        val firstThreeStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loader = loader { _ ->
            active += 1
            maxActive = maxOf(maxActive, active)
            if (active == 3) firstThreeStarted.complete(Unit)
            release.await()
            active -= 1
            listOf(chinese)
        }
        var final = SubtitleCatalog()
        val job = launch { loader.load((1L..8L).map { SubtitleCatalogSource(item(it)) }) { final = it } }
        firstThreeStarted.await()
        assertEquals(3, active)
        release.complete(Unit)
        job.join()
        assertEquals(3, maxActive)
        assertEquals(8, final.availableCounts["zh-Hans"])
    }

    @Test
    fun retryUsesSuccessfulCacheAndRetriesFailedSourceAndSubtitleRequests() = runBlocking {
        var fail = true
        val resolveCalls = mutableMapOf<Long, Int>()
        val fetchCalls = mutableMapOf<Long, Int>()
        val loader = SubtitleCatalogLoader(
            resolve = { item ->
                val aid = item.aid!!
                resolveCalls[aid] = resolveCalls.getOrDefault(aid, 0) + 1
                if (aid == 3L && fail) throw IOException("missing page list")
                item
            },
            expand = { listOf(it) },
            fetch = { target ->
                fetchCalls[target.aid] = fetchCalls.getOrDefault(target.aid, 0) + 1
                if (target.aid == 2L && fail) throw IOException("subtitle service unavailable")
                listOf(chinese)
            },
        )
        val sources = (1L..3L).map { SubtitleCatalogSource(item(it)) }
        var final = SubtitleCatalog()
        loader.load(sources) { final = it }
        assertEquals(2, final.failed)
        assertEquals(3, final.completed)

        fail = false
        loader.load(sources) { final = it }
        assertEquals(0, final.failed)
        assertEquals(3, final.availableCounts["zh-Hans"])
        assertEquals(mapOf(1L to 1, 2L to 1, 3L to 2), resolveCalls)
        assertEquals(mapOf(1L to 1, 2L to 2, 3L to 1), fetchCalls)
    }

    @Test
    fun cancellingPreviousBatchDoesNotPublishOrCacheItsLateResult() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val loader = loader {
            calls += 1
            if (calls == 1) {
                started.complete(Unit)
                // 即使上游直到网络返回才响应取消，也不能发布旧结果或把它缓存到新解析中。
                withContext(NonCancellable) { release.await() }
            }
            listOf(chinese)
        }
        val reports = mutableListOf<SubtitleCatalog>()
        val sources = listOf(SubtitleCatalogSource(item(1)))
        val first = launch { loader.load(sources, reports::add) }
        started.await()
        val reportCountBeforeCancellation = reports.size
        first.cancel()
        loader.clear()
        release.complete(Unit)
        first.join()
        assertEquals(reportCountBeforeCancellation, reports.size)

        loader.load(sources, reports::add)
        assertEquals(2, calls)
        assertEquals(1, reports.last().completed)
    }

    @Test
    fun clearRejectsOldResultsEvenBeforeCollectorCancellationArrives() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loader = loader {
            started.complete(Unit)
            release.await()
            listOf(chinese)
        }
        val reports = mutableListOf<SubtitleCatalog>()
        val job = launch { loader.load(listOf(SubtitleCatalogSource(item(1))), reports::add) }
        started.await()
        val before = reports.size
        loader.clear()
        release.complete(Unit)
        job.join()
        assertEquals(before, reports.size)
    }

    @Test
    fun changingBatchAndRetryingPreservesAllExplicitSelections() {
        val chosen = SubtitleLanguageSelection.Languages(setOf("zh-Hans", "en"))
        val state = state(item(1), item(2)).copy(
            subtitleEnabled = true,
            embedSubtitlesEnabled = true,
            embedLyricsEnabled = true,
            subtitleLanguageSelection = chosen,
            embedSubtitleSelection = chosen,
            embedLyricsLanguage = "en",
        )
        val partial = state.withSubtitleCatalog(
            SubtitleCatalog(listOf(chinese), mapOf("zh-Hans" to 1), total = 2, completed = 1),
        )
        val failed = partial.withSubtitleCatalog(
            SubtitleCatalog(listOf(chinese), mapOf("zh-Hans" to 1), total = 2, completed = 2, failed = 1),
        )
        val changedBatch = failed.withSubtitleCatalog(
            SubtitleCatalog(listOf(chinese), mapOf("zh-Hans" to 1), total = 1, completed = 1),
        )

        assertEquals(SubtitleLoadStatus.Loading, partial.subtitleLoadStatus)
        assertEquals(SubtitleLoadStatus.Failed, failed.subtitleLoadStatus)
        assertEquals(chosen, changedBatch.subtitleLanguageSelection)
        assertEquals(chosen, changedBatch.embedSubtitleSelection)
        assertEquals("en", changedBatch.embedLyricsLanguage)
        assertTrue(changedBatch.subtitleEnabled && changedBatch.embedSubtitlesEnabled && changedBatch.embedLyricsEnabled)
    }

    @Test
    fun lyricsAreFilledOnlyAfterCompleteUnambiguousDiscovery() {
        val state = state(item(1), item(2))
        assertNull(state.withSubtitleCatalog(
            SubtitleCatalog(listOf(chinese), total = 2, completed = 1),
        ).embedLyricsLanguage)
        assertNull(state.withSubtitleCatalog(
            SubtitleCatalog(listOf(chinese), total = 2, completed = 2, failed = 1),
        ).embedLyricsLanguage)
        assertNull(state.withSubtitleCatalog(
            SubtitleCatalog(listOf(chinese, english), total = 2, completed = 2),
        ).embedLyricsLanguage)
        assertEquals("zh-Hans", state.withSubtitleCatalog(
            SubtitleCatalog(listOf(chinese), total = 2, completed = 2),
        ).embedLyricsLanguage)
    }

    @Test
    fun batchDiscoveryWaitsForRelevantOptionButSingleSelectionAlwaysPreviews() {
        val batch = state(item(1), item(2))
        assertTrue(batch.subtitleCatalogTargets().isEmpty())
        assertEquals(2, batch.copy(subtitleEnabled = true).subtitleCatalogTargets().size)
        assertEquals(2, batch.copy(embedSubtitlesEnabled = true).subtitleCatalogTargets().size)
        assertEquals(2, batch.copy(embedLyricsEnabled = true, outputType = OutputType.AudioOnly).subtitleCatalogTargets().size)
        assertTrue(batch.copy(embedLyricsEnabled = true).subtitleCatalogTargets().isEmpty())
        assertEquals(1, state(item(1)).subtitleCatalogTargets().size)

        val collection = state(item(1)).copy(collectionMode = true)
        assertTrue(collection.subtitleCatalogTargets().isEmpty())
        assertTrue(collection.copy(subtitleEnabled = true).subtitleCatalogTargets().single().expandPages)
        assertTrue(collection.copy(subtitleEnabled = true, collectionModeLoading = true).subtitleCatalogTargets().isEmpty())
        assertTrue(state(item(1)).copy(loading = true).subtitleCatalogTargets().isEmpty())
        // UP/标题等展示信息补全不会取消正在进行的同一份目录请求。
        assertEquals(
            state(item(1)).subtitleCatalogTargets(),
            state(item(1).copy(title = "补全后的标题")).subtitleCatalogTargets(),
        )
    }

    @Test
    fun newParseClearsLanguageChoicesAndCatalogWithoutLosingOptionSwitches() {
        val state = state(item(1)).copy(
            subtitleEnabled = true,
            embedSubtitlesEnabled = true,
            embedLyricsEnabled = true,
            subtitleLanguageSelection = SubtitleLanguageSelection.Languages(setOf("en")),
            embedSubtitleSelection = SubtitleLanguageSelection.Languages(setOf("en")),
            embedLyricsLanguage = "en",
        ).withSubtitleCatalog(SubtitleCatalog(listOf(english), mapOf("en" to 1), 1, 1))
        val reset = state.resetSubtitleChoices()
        assertEquals(SubtitleLanguageSelection.All, reset.subtitleLanguageSelection)
        assertEquals(SubtitleLanguageSelection.All, reset.embedSubtitleSelection)
        assertNull(reset.embedLyricsLanguage)
        assertTrue(reset.subtitleKnownLanguages.isEmpty())
        assertTrue(reset.subtitleList.isEmpty())
        assertTrue(reset.subtitleAvailableCounts.isEmpty())
        assertTrue(reset.subtitleEnabled && reset.embedSubtitlesEnabled && reset.embedLyricsEnabled)
        assertEquals(state.subtitleRefreshRevision + 1, reset.subtitleRefreshRevision)
    }

    private fun loader(fetch: suspend (SubtitleTarget) -> List<SubtitleInfo>) = SubtitleCatalogLoader(
        resolve = { it },
        expand = { listOf(it) },
        fetch = fetch,
    )

    private fun item(aid: Long, cid: Long? = aid * 10) = MediaItem(
        title = "视频 $aid", coverUrl = "", description = "", url = "", duration = 0, pubTime = 0,
        type = MediaType.Video, isTarget = true, index = 0, aid = aid, cid = cid,
    )

    private fun state(vararg items: MediaItem): ParseUiState = ParseUiState(
        mediaInfo = MediaInfo(MediaType.Video, "test", MediaNfo(), items.toList()),
        items = items.toList(),
        selectedItemIndices = items.indices.toList(),
    )
}
