package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.model.DownloadExtraTaskOperation
import com.happycola233.bilitools.data.model.DownloadExtraTaskSpec
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class DownloadSubtitleDiscoveryTest {
    private val chinese = SubtitleInfo("zh-Hans", "中文（简体）", "https://example.com/zh.json")
    private val english = SubtitleInfo("en", "英语", "https://example.com/en.json")
    private val generated = SubtitleInfo("ai-zh", "中文", "https://example.com/ai.json", isAi = true)
    private val spec = DownloadExtraTaskSpec(
        operation = DownloadExtraTaskOperation.SubtitleDiscovery,
        mimeType = "application/x-subrip", unavailableMessage = "未找到所选字幕",
        aid = 1, cid = 2, subtitleBaseFileName = "视频", subtitleTaskTitle = "字幕",
    )

    @Test fun allLanguageRequestIncludesEverySourceAndKeepsAiLabels() {
        val subtitles = listOf(chinese, english.copy(isAi = true), generated.copy(isAi = false))
        val plan = planSubtitleDiscovery(
            subtitles,
            spec,
        )
        assertEquals(subtitles, plan.map { it.subtitle })
        assertEquals(listOf("中文（简体）", "英语 · AI 字幕", "中文 · AI 字幕"), plan.map { it.subtitle.displayName })
        assertEquals(listOf(listOf("zh-Hans"), listOf("en"), listOf("ai-zh")), plan.map { it.retrySpec.subtitleSelection.languages })
    }

    @Test fun everyExplicitMissingSourceGetsAnUnavailableTaskWithoutChangingTheLanguage() {
        val plan = planSubtitleDiscovery(
            listOf(english), spec.copy(subtitleSelection = SubtitleTrackEmbedding(listOf("zh-Hans", "en", "ai-zh"))),
        )
        assertEquals(listOf("en", "zh-Hans", "ai-zh"), plan.map { it.subtitle.lan })
        assertEquals(listOf(true, false, false), plan.map { it.available })
        assertEquals("中文 · AI 字幕", plan.last().subtitle.displayName)
        plan.forEach { task ->
            assertEquals(listOf(task.subtitle.lan), task.retrySpec.subtitleSelection.languages)
            assertEquals(spec.mimeType, task.retrySpec.mimeType)
            assertEquals(spec.subtitleBaseFileName, task.retrySpec.subtitleBaseFileName)
        }
    }

    @Test fun allLanguagesWithAnEmptyCatalogCreatesNoArtificialLanguageTask() {
        assertTrue(planSubtitleDiscovery(emptyList(), spec).isEmpty())
    }

    @Test fun missingUrlPreservesTheProvidedNameAndAiSource() {
        val noUrl = english.copy(name = "英语（美国）", url = "", isAi = true)
        val plan = planSubtitleDiscovery(listOf(noUrl), spec.copy(subtitleSelection = SubtitleTrackEmbedding(listOf("en"))))
        assertFalse(plan.single().available)
        assertEquals("英语（美国） · AI 字幕", plan.single().subtitle.displayName)
    }

    @Test fun duplicateRequestedOrReturnedLanguagesNeverProduceDuplicateFiles() {
        val selected = SubtitleTrackEmbedding(listOf("zh-Hans", "zh-Hans", "ja", "ja"))
        val plan = planSubtitleDiscovery(listOf(chinese, chinese.copy(url = "https://example.com/duplicate")), spec.copy(subtitleSelection = selected))
        assertEquals(listOf("zh-Hans", "ja"), plan.map { it.subtitle.lan })
    }

    @Test fun parentRetryRefreshesItsUrlWithoutChangingLanguageOrDuplicatingAnExistingChild() = runBlocking {
        val fixture = Fixture(spec)
        fixture.catalog = listOf(chinese, english)
        fixture.failSrt = true
        try {
            fixture.discover()
            fail("Expected the first subtitle download to fail")
        } catch (_: IOException) { }
        assertEquals(listOf("zh-Hans"), fixture.retrySpec.subtitleSelection.languages)
        assertEquals(2, fixture.items.size)

        fixture.failSrt = false
        fixture.catalog = listOf(english, chinese.copy(url = "https://example.com/zh-refreshed.json"))
        assertNotNull(fixture.discover())
        assertEquals("/zh-refreshed.json", fixture.requestPaths.last())
        assertEquals(2, fixture.items.size)
        assertEquals("视频.zh-Hans.srt", fixture.items.single { it.id == fixture.taskId }.fileName)

        // 原首语言被删除后，父任务不可用，不能接管已有英语子任务的文件。
        fixture.catalog = listOf(english)
        assertNull(fixture.discover())
        assertEquals(2, fixture.items.size)
        assertEquals("视频.zh-Hans.srt", fixture.items.single { it.id == fixture.taskId }.fileName)
    }

    @Test fun repositoryCreatesIndividualUnavailableRecordsForMissingSelectedLanguages() = runBlocking {
        val fixture = Fixture(spec.copy(subtitleSelection = SubtitleTrackEmbedding(listOf("en", "zh-Hans", "ai-zh"))))
        fixture.catalog = listOf(english)
        assertNotNull(fixture.discover())
        val unavailable = fixture.items.filter { it.status == DownloadStatus.Unavailable }
        assertEquals(setOf("视频.zh-Hans.srt", "视频.ai-zh.srt"), unavailable.map { it.fileName }.toSet())
        assertTrue(unavailable.all { it.statusDetail == spec.unavailableMessage })
        assertEquals(1, fixture.requestPaths.count { it.endsWith(".json") })
    }

    @Test fun emptySrtIsUnavailableInsteadOfBeingSavedAsAnEmptyFile() = runBlocking {
        val fixture = Fixture(spec.copy(subtitleSelection = SubtitleTrackEmbedding(listOf("zh-Hans"))))
        fixture.catalog = listOf(chinese)
        fixture.emptySrt = true
        assertNull(fixture.discover())
        assertEquals(listOf("zh-Hans"), fixture.retrySpec.subtitleSelection.languages)
        assertEquals(1, fixture.items.size)
    }

    @Test fun srtTimestampsUseAsciiDigitsRegardlessOfTheDeviceLocale() = runBlocking {
        val fixture = Fixture(spec.copy(subtitleSelection = SubtitleTrackEmbedding(listOf("zh-Hans"))))
        fixture.catalog = listOf(chinese)
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            val srt = requireNotNull(fixture.discover()).decodeToString()
            assertTrue(srt.contains("00:00:00,000 --> 00:00:01,000"))
            assertTrue(srt.contains("字幕示例"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    /** 使用真实 ExtrasRepository 解析响应与真实下载任务表，只暂停后台调度以直接检查一次发现操作。 */
    private class Fixture(initialSpec: DownloadExtraTaskSpec) {
        var catalog: List<SubtitleInfo> = emptyList()
        var failSrt = false
        var emptySrt = false
        val requestPaths = mutableListOf<String>()
        private val repository: DownloadRepository
        val taskId: Long
        private val tasks: MutableMap<Long, DownloadItem>
        private val specs: MutableMap<Long, DownloadExtraTaskSpec>
        val items: List<DownloadItem> get() = repository.groups.value.single().tasks
        val retrySpec: DownloadExtraTaskSpec get() = specs.getValue(taskId)

        init {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                requestPaths += path
                val content = if (path == "/x/player/wbi/v2") {
                    val entries = catalog.joinToString(",") { source ->
                        """{"lan":"${source.lan}","lan_doc":"${source.name}","subtitle_url":"${source.url}","ai_type":${if (source.isAi) 1 else 0}}"""
                    }
                    """{"code":0,"data":{"subtitle":{"subtitles":[$entries]}}}"""
                } else {
                    if (failSrt) throw IOException("Subtitle request failed")
                    if (emptySrt) """{"body":[]}""" else """{"body":[{"from":0,"to":1,"content":"字幕示例"}]}"""
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                    .body(content.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
            }.build()
            val context = RuntimeEnvironment.getApplication()
            val cookies = CookieStore(context)
            val settings = SettingsRepository(context)
            val bili = BiliHttpClient(cookies, settings)
            ReflectionHelpers.setField(bili, "client\$delegate", lazyOf(client))
            val signer = WbiSigner(bili)
            ReflectionHelpers.setField(signer, "cachedMixinKey", "fixture")
            ReflectionHelpers.setField(signer, "lastUpdateMs", System.currentTimeMillis())
            repository = DownloadRepository(
                context, cookies, settings, MediaRepository(bili, signer, cookies, OpusRepository(bili, cookies)),
                ExtrasRepository(bili, signer), ExportRepository(context, settings),
            )
            ReflectionHelpers.getField<CoroutineScope>(repository, "scope").cancel()
            val group = repository.createGroup("视频", null)
            val item = repository.addUnavailableTask(group, DownloadTaskType.Subtitle, "字幕", "", "视频.srt")
            taskId = item.id
            tasks = ReflectionHelpers.getField(repository, "tasks")
            specs = ReflectionHelpers.getField(repository, "extraTaskSpecs")
            tasks[taskId] = item.copy(status = DownloadStatus.Running)
            specs[taskId] = initialSpec
        }

        suspend fun discover(): ByteArray? = repository.executeSubtitleDiscovery(taskId, retrySpec)
    }
}
