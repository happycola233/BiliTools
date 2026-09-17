package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.SubtitleInfo
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SubtitleCopyTest {
    private val chinese = SubtitleInfo("zh-Hans", "中文（简体）", "https://example.invalid/zh")
    private val english = SubtitleInfo("en", "英语", "https://example.invalid/en")
    private val generated = SubtitleInfo("ai-zh", "中文（自动生成）", "https://example.invalid/ai", isAi = true)

    @Test
    fun partialSuccessRetainsMissingRequestedLanguageInPreview() = runBlocking {
        val entries = copy(
            selection = SubtitleLanguageSelection.Languages(setOf("zh-Hans", "en")),
            loadSubtitles = { listOf(chinese) },
        )
        assertEquals(2, entries.size)
        assertEquals("content:zh-Hans", entries[0].content)
        assertEquals("英语", entries[1].subtitleName)
        assertNull(entries[1].content)
        assertEquals("没有可用字幕", entries[1].error)
    }

    @Test
    fun directoryFailureRetainsAccurateErrorForEveryRequestedLanguage() = runBlocking {
        val entries = copy(
            selection = SubtitleLanguageSelection.Languages(setOf("zh-Hans", "en")),
            loadSubtitles = { throw IOException("目录连接超时") },
        )
        assertEquals(listOf("中文（简体）", "英语"), entries.map { it.subtitleName })
        assertEquals(listOf("目录连接超时", "目录连接超时"), entries.map { it.error })
        assertTrue(entries.all { it.content == null })
    }

    @Test
    fun singleContentFailureRetainsNetworkErrorInsteadOfClaimingNoSubtitles() = runBlocking {
        val entry = copy(
            loadSubtitles = { listOf(chinese) },
            loadContent = { throw IOException("字幕文件下载失败") },
        ).single()
        assertEquals("中文（简体）", entry.subtitleName)
        assertEquals("字幕文件下载失败", entry.error)
        assertNull(entry.content)
    }

    @Test
    fun oneContentFailureDoesNotDiscardOtherSuccessfulPreviewEntries() = runBlocking {
        val entries = copy(loadContent = {
            if (it.lan == "en") throw IOException("英语字幕连接失败")
            "中文内容"
        })
        assertEquals("中文内容", entries[0].content)
        assertNull(entries[0].error)
        assertEquals("英语字幕连接失败", entries[1].error)
    }

    @Test
    fun emptySelectionDoesNotRequestDirectoryOrContent() = runBlocking {
        val entries = copy(
            selection = SubtitleLanguageSelection.Languages(emptySet()),
            loadSubtitles = { error("空选不应读取目录") },
            loadContent = { error("空选不应读取字幕文件") },
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun explicitAiChoiceIsCopiedWithItsSourceLabel() = runBlocking {
        val entries = copy(
            selection = SubtitleLanguageSelection.Languages(setOf("ai-zh")),
            loadSubtitles = { listOf(chinese, generated) },
        )
        assertEquals("content:ai-zh", entries.single().content)
        assertEquals("中文 · AI 字幕", entries.single().subtitleName)
    }

    @Test
    fun allSubtitlesCopyIncludesAiSourcesWithoutASeparateFilter() = runBlocking {
        val entries = copy(loadSubtitles = { listOf(chinese, generated) })
        assertEquals(listOf("content:zh-Hans", "content:ai-zh"), entries.map { it.content })
        assertEquals(listOf("中文（简体）", "中文 · AI 字幕"), entries.map { it.subtitleName })
    }

    @Test
    fun directoryAndContentCancellationPropagateWithoutCreatingUnavailableEntries() = runBlocking {
        val directoryCancellation = CancellationException("cancel directory")
        try {
            copy(loadSubtitles = { throw directoryCancellation })
            fail("Directory cancellation should propagate")
        } catch (cancelled: CancellationException) {
            assertSame(directoryCancellation, cancelled)
        }

        val contentCancellation = CancellationException("cancel content")
        var requestedFiles = 0
        try {
            copy(loadContent = {
                requestedFiles += 1
                throw contentCancellation
            })
            fail("Content cancellation should propagate")
        } catch (cancelled: CancellationException) {
            assertSame(contentCancellation, cancelled)
        }
        assertEquals(1, requestedFiles)
    }

    private suspend fun copy(
        selection: SubtitleLanguageSelection = SubtitleLanguageSelection.All,
        loadSubtitles: suspend () -> List<SubtitleInfo> = { listOf(chinese, english) },
        loadContent: suspend (SubtitleInfo) -> String = { "content:${it.lan}" },
    ): List<SubtitleCopyEntry> = loadSubtitleCopyEntries(
        title = "视频",
        selection = selection,
        knownSubtitles = listOf(chinese, english, generated),
        loadSubtitles = loadSubtitles,
        loadContent = loadContent,
        unavailableMessage = "没有可用字幕",
        errorMessage = { it.message ?: "读取失败" },
    )
}
