package com.happycola233.bilitools.ui.downloads

import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsDetailsSheetTest {
    @get:Rule val compose = createComposeRule()

    private val failureReason = "连接超时，请检查网络后重试"
    private val unavailableReason = "未找到可用字幕"
    private val filename = "音视频 - 从零开始画出夏日的光影：线稿与色彩的练习 - 1080P 高清.mp4"
    private val directory = "Download/BiliTools/收藏夹 - 绘画练习 (2026-09-26)/夏日光影"
    private val group = DownloadGroup(
        id = 1, title = "从零开始画出夏日的光影：线稿与色彩的练习", subtitle = "P3 · 色彩练习",
        bvid = "BV17x411w7KC", createdAt = 1_789_283_696_000, relativePath = "$directory/",
        sourceMetadata = DownloadEmbeddedMetadata(
            uploader = "绘画记录", durationSeconds = 198, publishedDate = "2026-09-01",
            tags = listOf("绘画过程", "板绘", "原创"), comment = "用线条与色彩记录夏日的光影。",
        ),
        tasks = listOf(
            DownloadItem(
                id = 1, groupId = 1, taskType = DownloadTaskType.AudioVideo, title = "音视频",
                fileName = filename, url = "", status = DownloadStatus.Failed, progress = 35,
                downloadedBytes = 3_145_728, totalBytes = 9_437_184, errorMessage = failureReason,
                mediaParams = DownloadMediaParams(resolution = "1080P 高清", codec = "AVC (H.264)", audioBitrate = "192K"),
            ),
            DownloadItem(
                id = 2, groupId = 1, taskType = DownloadTaskType.Subtitle, title = "字幕",
                fileName = "字幕 - 夏日光影.srt", url = "", status = DownloadStatus.Unavailable,
                progress = 0, statusDetail = unavailableReason,
            ),
            DownloadItem(
                id = 3, groupId = 1, taskType = DownloadTaskType.NfoSingle, title = "单集刮削",
                fileName = "单集刮削 - 夏日光影.nfo", url = "", status = DownloadStatus.Success,
                progress = 100, outputBytes = 1126,
            ),
        ),
    )

    @Test fun lightFailureAndUnavailableRemainDistinct() = verifyDetails(AppThemeMode.Light)
    @Test fun darkFailureAndUnavailableRemainDistinct() = verifyDetails(AppThemeMode.Dark)
    @Test fun pureBlackFailureAndUnavailableRemainDistinct() = verifyDetails(AppThemeMode.Dark, pureBlack = true)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h720dp", fontScale = 1.6f)
    fun narrowLargeTextKeepsReasonsPathsAndCloseReachable() = verifyDetails(AppThemeMode.Dark)

    private fun verifyDetails(mode: AppThemeMode, pureBlack: Boolean = false) {
        var dismissed = false
        var errorColor = Color.Unspecified
        var noticeColor = Color.Unspecified
        var surfaceColor = Color.Unspecified
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Periwinkle, darkModePureBlack = pureBlack)) {
                errorColor = MaterialTheme.colorScheme.error
                noticeColor = MaterialTheme.colorScheme.tertiary
                surfaceColor = AppSurfaces.pageContainerColor
                DownloadsDetailsSheet(group) { dismissed = true }
            }
        }
        compose.onNodeWithText("1 项失败").assertIsDisplayed()
        val reason = compose.onNodeWithText(failureReason).performScrollTo()
        reason.assertIsDisplayed()
        assertTextColor("下载失败", errorColor)
        assertTextColor(failureReason, errorColor)
        assertTrue("失败原因必须先于文件名出现", reason.getUnclippedBoundsInRoot().top <
            compose.onNodeWithText(filename).getUnclippedBoundsInRoot().top)
        assertContrast(errorColor, surfaceColor)
        assertContrast(noticeColor, surfaceColor)
        capture("failure-$mode-$pureBlack")

        val fullPath = "$directory/$filename"
        compose.onNodeWithText(fullPath).assertDoesNotExist()
        compose.onNodeWithText(filename).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.runOnIdle {
            val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
            assertEquals("文件名仍能独立复制", filename, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithText(fullPath).assertDoesNotExist()
        compose.onAllNodesWithText("目标位置").onFirst().performScrollTo().performClick()
        compose.onNodeWithText(fullPath).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(fullPath).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.runOnIdle {
            val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
            assertEquals(fullPath, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        capture("path-$mode-$pureBlack")

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(unavailableReason))
        compose.onNodeWithText(unavailableReason).performScrollTo().assertIsDisplayed()
        assertTextColor("无对应资源", noticeColor)
        assertTextColor(unavailableReason, noticeColor)
        assertNotEquals("无资源不能使用失败色", errorColor, noticeColor)
        capture("unavailable-$mode-$pureBlack")

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("基本信息"))
        compose.onNodeWithText("P3 · 色彩练习").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("绘画记录").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText(fullPath).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("目标位置").onFirst().performScrollTo().performClick()
        compose.onNodeWithText(fullPath).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("用线条与色彩记录夏日的光影。"))
        compose.onNodeWithContentDescription("关闭下载详情").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun statusUpdatesKeepMissingOutputsAndIndeterminateProgressAccurate() {
        var task by mutableStateOf(group.tasks.first())
        var errorColor = Color.Unspecified
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = AppThemeMode.Light, themeColor = AppThemeColor.Periwinkle)) {
                errorColor = MaterialTheme.colorScheme.error
                DownloadsDetailsSheet(group.copy(tasks = listOf(task))) {}
            }
        }
        val statuses = listOf(
            DownloadStatus.Pending to "排队中",
            DownloadStatus.Running to "下载中 35%",
            DownloadStatus.Paused to "已暂停 35%",
            DownloadStatus.Cancelled to "已取消",
            DownloadStatus.Merging to "正在写入媒体信息",
            DownloadStatus.Success to "已完成",
        )
        statuses.forEach { (status, text) ->
            compose.runOnIdle { task = task.copy(status = status, statusDetail = "正在写入媒体信息") }
            compose.onNodeWithText(text).assertIsDisplayed()
            compose.onNodeWithText(failureReason).assertDoesNotExist()
        }
        compose.runOnIdle {
            task = task.copy(outputMissing = true, outputBytes = 9_437_184)
        }
        compose.onNodeWithText("已完成（文件已丢失）").assertIsDisplayed()
        compose.onNodeWithText("1 个文件已丢失").assertIsDisplayed()
        compose.onNodeWithText("已保存 0 / 1 项", substring = true).assertIsDisplayed()
        assertTextColor("已完成（文件已丢失）", errorColor)
        compose.runOnIdle {
            task = task.copy(status = DownloadStatus.Running, outputMissing = false, progressIndeterminate = true, statusDetail = "正在连接下载源")
        }
        compose.onNodeWithText("正在连接下载源").assertIsDisplayed()
        compose.onNodeWithText("下载中 35%").assertDoesNotExist()
    }

    @Test
    fun savedFileWithEmbeddingWarningKeepsItsCompletedStatus() {
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = AppThemeMode.Light, themeColor = AppThemeColor.Periwinkle)) {
                DownloadsDetailsSheet(group.copy(tasks = listOf(
                    group.tasks.first().copy(status = DownloadStatus.Success, outputBytes = 9_437_184, embedWarning = "没有可用字幕"),
                    group.tasks[1], group.tasks[2],
                ))) {}
            }
        }
        compose.onNodeWithText("已保存 2 / 3 项 · 9.0 MB").assertIsDisplayed()
        compose.onAllNodesWithText("已完成").onFirst().assertIsDisplayed()
        compose.onNodeWithText("没有可用字幕").assertIsDisplayed()
        compose.onNodeWithText(failureReason).assertDoesNotExist()
        capture("partial-success")
    }

    @Test fun lightMultipleSubtitlesKeepLocationsAndParametersCompact() = verifyMultipleSubtitles(AppThemeMode.Light)
    @Test fun darkMultipleSubtitlesKeepLocationsAndParametersCompact() = verifyMultipleSubtitles(AppThemeMode.Dark)

    private fun verifyMultipleSubtitles(mode: AppThemeMode) {
        val languages = listOf("中文", "English", "日本語", "Español", "العربية", "Português")
        val subtitleFileStem = "字幕 - 从零开始画出夏日的光影：线稿与色彩的练习"
        val subtitles = languages.mapIndexed { index, language ->
            group.tasks[1].copy(
                id = index + 2L, title = "字幕 - $language · AI", fileName = "$subtitleFileStem.ai-$index.srt",
                status = DownloadStatus.Success, statusDetail = null, progress = 100, outputBytes = 42_496L + index * 1_024,
            )
        }
        val files = listOf(group.tasks.first().copy(
            status = DownloadStatus.Success, outputBytes = 2_147_483_648, embeddedSubtitleTitles = languages.map { "$it · AI" },
        )) + subtitles + group.tasks.last().copy(id = 8)
        val renamed = "$subtitleFileStem.ai-5 (1).srt"
        val alternateDirectory = "Download/BiliTools/字幕/"
        val outputs = files.associate { file ->
            file.id to SavedOutput(
                name = if (file.id == 7L) renamed else file.fileName,
                directory = if (file.id == 7L) alternateDirectory else "$directory/",
                size = file.outputBytes!!,
            )
        }
        val provider = DetailsMediaProvider(outputs).apply {
            attachInfo(RuntimeEnvironment.getApplication(), ProviderInfo().apply { authority = "downloads.details.test" })
        }
        ShadowContentResolver.registerProviderInternal("downloads.details.test", provider)
        val savedGroup = group.copy(tasks = files.map { it.copy(localUri = "content://downloads.details.test/${it.id}") })
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Periwinkle, darkModePureBlack = false)) {
                DownloadsDetailsSheet(savedGroup) {}
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("保存位置").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("已保存 8 / 8 项 · 2.0 GB").assertIsDisplayed()
        compose.onAllNodesWithText("下载目录").assertCountEquals(1)
        compose.onNodeWithText(directory).assertIsDisplayed()
        val directoryBounds = compose.onNodeWithText(directory).getUnclippedBoundsInRoot()
        assertTrue("整组目录默认展开在文件之前", directoryBounds.bottom < compose.onNodeWithText("音视频").getUnclippedBoundsInRoot().top)
        val sizeBounds = compose.onNodeWithText("2.0 GB").getUnclippedBoundsInRoot()
        val formatBounds = compose.onAllNodesWithText("MP4").onFirst().getUnclippedBoundsInRoot()
        assertEquals("短参数在同一行并排展示", sizeBounds.top, formatBounds.top)
        assertTrue("第二列使用右半侧宽度", formatBounds.left > sizeBounds.right)
        val sourceBounds = compose.onNodeWithText(filename).getUnclippedBoundsInRoot()
        assertEquals("位置入口与文件名共享区域，不再单占按钮行", sourceBounds,
            compose.onAllNodesWithText("保存位置").onFirst().getUnclippedBoundsInRoot())
        val fileNameBounds = compose.onNodeWithText(filename, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pathLabelBounds = compose.onAllNodesWithText("保存位置", useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()
        assertEquals("位置入口跟随文件名左对齐", fileNameBounds.left, pathLabelBounds.left)
        assertTrue("位置入口位于文件名下方", pathLabelBounds.top > fileNameBounds.bottom)
        compose.onNodeWithText(subtitles.first().title).assertIsDisplayed()
        capture("multiple-subtitles-$mode")

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(renamed))
        compose.onNodeWithText(renamed).performScrollTo().assertIsDisplayed().performClick()
        val actualPath = alternateDirectory + renamed
        compose.onNodeWithText(actualPath).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(actualPath).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.runOnIdle {
            val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
            assertEquals("复制系统实际保存的路径，而非计划路径", actualPath, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithText(subtitles.last().fileName).assertDoesNotExist()
        compose.onNodeWithText("下载目录").assertDoesNotExist()
        capture("actual-location-$mode")
    }

    private data class SavedOutput(val name: String, val directory: String, val size: Long)

    /** 通过 ContentResolver 验证实际成品名和目录，覆盖系统重命名与不同保存目录。 */
    private class DetailsMediaProvider(private val outputs: Map<Long, SavedOutput>) : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/octet-stream"
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read only")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor {
            val output = outputs.getValue(uri.lastPathSegment!!.toLong())
            val columns = requireNotNull(projection)
            return MatrixCursor(columns).apply {
                addRow(columns.map { column ->
                    when (column) {
                        MediaStore.MediaColumns.DISPLAY_NAME -> output.name
                        MediaStore.MediaColumns.RELATIVE_PATH -> output.directory
                        MediaStore.MediaColumns.SIZE -> output.size
                        else -> error("Unexpected column: $column")
                    }
                }.toTypedArray<Any?>())
            }
        }
    }

    private fun assertTextColor(text: String, expected: Color) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(expected, layouts.single().layoutInput.style.color)
    }

    private fun assertContrast(foreground: Color, background: Color) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val dark = minOf(foreground.luminance(), background.luminance())
        assertTrue("状态文字对比度至少为 4.5:1", (light + 0.05f) / (dark + 0.05f) >= 4.5f)
    }

    private fun capture(name: String) {
        val width = RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp
        File("../.tmp/download-details-v3/$name-$width.png").apply {
            parentFile!!.mkdirs()
            outputStream().use { compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
