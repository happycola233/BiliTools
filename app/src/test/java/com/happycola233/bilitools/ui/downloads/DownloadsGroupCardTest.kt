package com.happycola233.bilitools.ui.downloads

import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadMessage
import com.happycola233.bilitools.data.model.DownloadMessageCode
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsGroupCardTest {
    @get:Rule val compose = createComposeRule()
    private val sourceUrl = "https://www.bilibili.com/video/BV17x411w7KC?p=3"
    private val group = DownloadGroup(
        id = 1, title = "从零开始画出夏日的光影：线稿与色彩的练习 - P3", subtitle = null,
        bvid = "BV17x411w7KC", createdAt = 1_789_283_696_000, relativePath = "Download/BiliTools/夏日的光影/",
        sourceMetadata = DownloadEmbeddedMetadata(
            title = "光影练习", uploader = "绘画记录", durationSeconds = 625,
            publishedDate = "2026-09-01", originalUrl = sourceUrl,
        ),
        tasks = listOf(
            task(1, DownloadTaskType.AudioVideo, "音视频", "mp4", 72).copy(
                embedWarning = "没有可用字幕",
                embeddingMessages = listOf(DownloadMessage(DownloadMessageCode.EmbedSubtitlesUnavailable)),
            ),
            task(2, DownloadTaskType.Video, "视频", "mp4", 64),
            task(3, DownloadTaskType.Audio, "音频", "m4a", 8),
            task(4, DownloadTaskType.Subtitle, "中文字幕", "srt", 1),
            task(5, DownloadTaskType.Cover, "封面", "jpg", 2),
        ),
    )

    private fun task(id: Long, type: DownloadTaskType, title: String, extension: String, megabytes: Long) = DownloadItem(
        id = id, groupId = 1, taskType = type, title = title, fileName = "夏日光影-$title.$extension", url = "",
        status = DownloadStatus.Success, progress = 100, outputBytes = megabytes * 1024 * 1024,
        mediaParams = when (type) {
            DownloadTaskType.AudioVideo -> DownloadMediaParams("1080P 高清", "AVC (H.264)", "192K")
            DownloadTaskType.Video -> DownloadMediaParams("1080P 高清", "AVC (H.264)")
            DownloadTaskType.Audio -> DownloadMediaParams(audioBitrate = "192K")
            else -> null
        },
    )

    @Test fun lightCardsKeepTextBesideCoverAndExpandedActionsWork() = verifyCard(AppThemeMode.Light)
    @Test fun darkCardsKeepTextBesideCoverAndExpandedActionsWork() = verifyCard(AppThemeMode.Dark)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h891dp")
    fun narrowScreenAndLargeTextKeepMetadataBesideCoverAndButtonsIntact() = verifyCard(AppThemeMode.Light, 1.5f)

    private fun verifyCard(themeMode: AppThemeMode, fontScale: Float = 1f) {
        // 完成后只保留时间和必要的结果说明，不再分散展示已保存数与总项数。
        var cardTasks by mutableStateOf(group.tasks.map { task ->
            if (task.taskType == DownloadTaskType.Cover) task.copy(status = DownloadStatus.Unavailable) else task
        })
        var expanded by mutableStateOf(false)
        var selectionMode by mutableStateOf(false)
        var cardTitle by mutableStateOf(group.title)
        var reparsed = 0
        var detailsOpened = 0
        val themedContext = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_BiliTools)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides themedContext,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                BiliToolsTheme(AppSettings(themeMode = themeMode, themeColor = AppThemeColor.Sakura)) {
                    Column(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor).verticalScroll(rememberScrollState())) {
                        DownloadsGroupCard(
                            group = group.copy(title = cardTitle, tasks = cardTasks), selectionMode = selectionMode, selected = selectionMode,
                            expanded = expanded, swiped = false, anyGroupSwiped = false,
                            onSwipedGroupChange = {}, onToggleSelection = { selectionMode = !selectionMode },
                            onToggleExpanded = { expanded = !expanded }, onDelete = {}, onPauseGroup = {}, onResumeGroup = {},
                            onReparse = { reparsed++ }, onShowDetails = { detailsOpened++ },
                            onTaskPauseResume = {}, onTaskRetry = {}, onTaskDelete = {}, onTaskClick = { _, _ -> },
                        )
                    }
                }
            }
        }
        val timestamp = formatDownloadCreatedAt(themedContext, group.createdAt)
        val footer = "跳过 1 项"
        fun assertHeaderColumns() {
            val cover = compose.onNodeWithContentDescription("视频封面", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val title = compose.onNodeWithText(group.title, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val time = compose.onNodeWithText(timestamp, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("标题始终位于封面右侧", title.left >= cover.right)
            assertTrue("时间始终位于封面右侧", time.left >= cover.right)
            assertTrue("标题与封面并排起始", title.top < cover.bottom)
            assertEquals("标题与时间整体和封面垂直居中",
                (cover.top.value + cover.bottom.value) / 2,
                (title.top.value + time.bottom.value) / 2, 0.5f)
            val footerBounds = compose.onNodeWithText(footer, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("底部状态位于图文区域下方", footerBounds.top >= maxOf(cover.bottom, time.bottom))
            assertEquals("底部状态与封面左侧对齐，复选框下方留空", cover.left.value, footerBounds.left.value, 0.5f)
            compose.onNodeWithText("100%", substring = true).assertDoesNotExist()
            compose.onNodeWithText("已保存", substring = true).assertDoesNotExist()
            compose.onNodeWithText("5 项", useUnmergedTree = true).assertDoesNotExist()
        }
        assertHeaderColumns()
        compose.onNodeWithText(group.bvid!!).assertDoesNotExist()
        compose.onNodeWithText("创建于", substring = true).assertDoesNotExist()
        compose.onNodeWithText("重新解析").assertDoesNotExist()
        capture("collapsed-${themeMode.name}-$fontScale", compose.onNodeWithText(cardTitle))
        val longTitleBounds = compose.onNodeWithText(group.title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.runOnIdle { cardTitle = "绘画练习" }
        val shortTitleBounds = compose.onNodeWithText(cardTitle, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val timeBounds = compose.onNodeWithText(timestamp, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val coverBounds = compose.onNodeWithContentDescription("视频封面", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("短标题不再预留第二行空白",
            shortTitleBounds.bottom - shortTitleBounds.top < longTitleBounds.bottom - longTitleBounds.top)
        assertEquals("短标题与时间整体和封面垂直居中",
            (coverBounds.top.value + coverBounds.bottom.value) / 2,
            (shortTitleBounds.top.value + timeBounds.bottom.value) / 2, 0.5f)
        capture("collapsed-short-title-${themeMode.name}-$fontScale", compose.onNodeWithText(cardTitle))
        val cardWithNote = compose.onNodeWithText(cardTitle).getUnclippedBoundsInRoot()
        val skippedTasks = cardTasks
        compose.runOnIdle { cardTasks = group.tasks }
        compose.onNodeWithText(footer).assertDoesNotExist()
        compose.onNodeWithText("已保存", substring = true).assertDoesNotExist()
        compose.onAllNodesWithContentDescription("查看文件").assertCountEquals(1)
        val normalCard = compose.onNodeWithText(cardTitle).getUnclippedBoundsInRoot()
        assertTrue("普通完成卡片不为已移除的底栏预留空白", normalCard.bottom - normalCard.top < cardWithNote.bottom - cardWithNote.top)
        capture("completed-clean-${themeMode.name}-$fontScale", compose.onNodeWithText(cardTitle))
        compose.runOnIdle { cardTasks = group.tasks.map { if (it.id == 1L) it.copy(outputMissing = true) else it } }
        compose.onNodeWithText("1 个文件已丢失").assertIsDisplayed()
        compose.onNodeWithText("已保存", substring = true).assertDoesNotExist()
        compose.runOnIdle { cardTitle = group.title; cardTasks = skippedTasks }
        compose.onNodeWithContentDescription("查看文件").performClick()
        compose.onNodeWithText("重新解析").performScrollTo().performClick()
        compose.onNodeWithText("详细信息").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, reparsed)
            assertEquals(1, detailsOpened)
            assertTrue("点击子操作不能收起整张卡片", expanded)
        }
        compose.onNodeWithText("重新解析").performScrollTo()
        capture("expanded-${themeMode.name}-$fontScale")
        for (size in listOf("72.0 MB", "64.0 MB", "8.0 MB")) {
            compose.onNodeWithText(size.replace(' ', '\u00A0'), substring = true).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("没有可用字幕", substring = true).assertDoesNotExist()
        compose.onNodeWithText("参数：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("1080P 高清 / AVC (H.264) / 192K", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1.0 MB", substring = true).assertDoesNotExist()
        compose.onNodeWithText("2.0 MB").assertDoesNotExist()
        compose.onNodeWithText(group.title).performScrollTo()
        verifySelectionMotion(group.title, timestamp, compose.onNodeWithText(footer, useUnmergedTree = true), "收起文件", "expanded-completed-${themeMode.name}-$fontScale") {
            selectionMode = it
        }
        compose.runOnIdle { expanded = false; selectionMode = false }
        compose.onNodeWithText(group.title).performScrollTo()
        verifySelectionMotion(group.title, timestamp, compose.onNodeWithText(footer, useUnmergedTree = true), "查看文件", "completed-${themeMode.name}-$fontScale") {
            selectionMode = it
        }
        assertHeaderColumns()
        capture("selection-completed-${themeMode.name}-$fontScale", compose.onNodeWithText(group.title))
    }

    @Test fun lightProgressControlActsOnGroupWithoutExpanding() = verifyProgressControl(AppThemeMode.Light)
    @Test fun darkProgressControlActsOnGroupWithoutExpanding() = verifyProgressControl(AppThemeMode.Dark)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h891dp")
    fun narrowActiveCardKeepsProgressControlUsableWithLargeText() = verifyProgressControl(AppThemeMode.Light, 1.5f)

    private fun verifyProgressControl(mode: AppThemeMode, fontScale: Float = 1f) {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_BiliTools)
        val running = group.tasks.first().copy(
            status = DownloadStatus.Running, progress = 67, totalBytes = 100_000_000,
            downloadedBytes = 67_000_000, speedBytesPerSec = 2_000_000, etaSeconds = 17,
        )
        var cardGroup by mutableStateOf(group.copy(tasks = listOf(running) + group.tasks.drop(1)))
        var expanded by mutableStateOf(false)
        var selection by mutableStateOf(false)
        var pauses = 0
        var resumes = 0
        val retried = mutableListOf<Long>()
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Sakura)) {
                    Column(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor).verticalScroll(rememberScrollState())) {
                        DownloadsGroupCard(
                            group = cardGroup, selectionMode = selection, selected = selection,
                            expanded = expanded, swiped = false, anyGroupSwiped = false,
                            onSwipedGroupChange = {}, onToggleSelection = { selection = !selection },
                            onToggleExpanded = { expanded = !expanded }, onDelete = {},
                            onPauseGroup = { pauses++ }, onResumeGroup = { resumes++ },
                            onReparse = {}, onShowDetails = {}, onTaskPauseResume = {},
                            onTaskRetry = { retried.add(it.id) }, onTaskDelete = {}, onTaskClick = { _, _ -> },
                        )
                    }
                }
            }
        }
        fun captureProgress(name: String) {
            // 波浪振幅在绘制节点内过渡，等其稳定后再检查暂停与完成的形态。
            compose.onNodeWithText(cardGroup.title).captureToImage()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            capture("$name-${mode.name}-$fontScale", compose.onNodeWithText(cardGroup.title))
        }
        fun verifyCircularPressFeedback(label: String, name: String) {
            val button = compose.onNodeWithContentDescription(label)
            val normal = button.captureToImage().asAndroidBitmap()
            compose.mainClock.autoAdvance = false
            button.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(180)
            val pressed = button.captureToImage().asAndroidBitmap()
            val inset = normal.width / 8
            for (x in listOf(inset, normal.width - 1 - inset)) {
                for (y in listOf(inset, normal.height - 1 - inset)) {
                    assertEquals("圆形外侧不应出现方形按压阴影", normal.getPixel(x, y), pressed.getPixel(x, y))
                }
            }
            assertFalse("按压应有可见反馈", normal.sameAs(pressed))
            capture("pressed-$name-${mode.name}-$fontScale", compose.onNodeWithText(cardGroup.title))
            button.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
        val pause = compose.onNodeWithContentDescription("暂停该组")
        pause.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.8f, 0f..1f)))
        val ring = pause.getUnclippedBoundsInRoot()
        val cover = compose.onNodeWithContentDescription("视频封面", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(56f, (ring.right - ring.left).value, 0.5f)
        assertEquals((cover.top.value + cover.bottom.value) / 2, (ring.top.value + ring.bottom.value) / 2, 0.5f)
        // 外圈也属于暂停按钮；不能穿透到卡片的展开操作。
        pause.performTouchInput { click(Offset(4f, center.y)) }
        compose.runOnIdle { assertEquals(1, pauses); assertFalse(expanded) }
        compose.onNodeWithText("5 个任务").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue("点击任务数量展开同一组任务", expanded) }
        compose.onNodeWithText("共 5 项", substring = true).assertDoesNotExist()
        capture("expanded-active-${mode.name}-$fontScale")
        compose.onNodeWithContentDescription("收起任务").performClick()
        compose.runOnIdle { assertFalse(expanded) }
        captureProgress("running")
        verifySelectionMotion(cardGroup.title, "4 / 5 项已完成", compose.onNodeWithText("MB/s", substring = true, useUnmergedTree = true), "暂停该组", "running-${mode.name}-$fontScale") {
            selection = it
        }
        val selectedCover = compose.onNodeWithContentDescription("视频封面", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val selectedFooter = compose.onNodeWithText("MB/s", substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("多选时速度与剩余时间从封面左侧开始", selectedCover.left.value, selectedFooter.left.value, 0.5f)
        captureProgress("selection-running")
        compose.runOnIdle { selection = false }
        verifyCircularPressFeedback("暂停该组", "pause")
        compose.runOnIdle { cardGroup = cardGroup.copy(title = "绘画练习") }
        val shortTitle = compose.onNodeWithText(cardGroup.title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val shortSummary = compose.onNodeWithText("4 / 5 项已完成", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val shortCover = compose.onNodeWithContentDescription("视频封面", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((shortCover.top.value + shortCover.bottom.value) / 2, (shortTitle.top.value + shortSummary.bottom.value) / 2, 0.5f)
        captureProgress("running-short")

        compose.runOnIdle { cardGroup = cardGroup.copy(tasks = listOf(running.copy(status = DownloadStatus.Paused, userPaused = true)) + group.tasks.drop(1)) }
        compose.onNodeWithContentDescription("继续该组").performClick()
        compose.runOnIdle { assertEquals(1, resumes); assertFalse(expanded) }
        captureProgress("paused")
        verifyCircularPressFeedback("继续该组", "resume")
        for (status in listOf(DownloadStatus.Pending, DownloadStatus.Merging, DownloadStatus.Running)) {
            compose.runOnIdle { cardGroup = cardGroup.copy(tasks = listOf(running.copy(status = status, progressIndeterminate = true, speedBytesPerSec = 0)) + group.tasks.drop(1)) }
            compose.onNodeWithContentDescription("暂停该组").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.8f, 0f..1f)),
            )
            compose.onNodeWithText("67%", substring = true).assertDoesNotExist()
            captureProgress("unfinished-${status.name}")
        }
        compose.runOnIdle { cardGroup = group.copy(tasks = listOf(running.copy(progressIndeterminate = true))) }
        compose.onNodeWithContentDescription("暂停该组").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        )
        compose.onNodeWithText("0 / 1 项已完成").assertIsDisplayed()
        captureProgress("first-result")
        compose.runOnIdle {
            val sixTasks = group.tasks + task(6, DownloadTaskType.DanmakuLive, "弹幕", "xml", 1)
            cardGroup = group.copy(tasks = sixTasks.map {
                if (it.id <= 2) it.copy(status = DownloadStatus.Failed, progress = 100, downloadedBytes = 1_000, totalBytes = 1_000) else it
            })
        }
        compose.onNodeWithContentDescription("重试失败项").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(4f / 6f, 0f..1f)),
        )
        compose.onNodeWithText("4 / 6 项已完成").assertIsDisplayed()
        compose.onNodeWithText("99%", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("重试失败项").performClick()
        compose.runOnIdle { assertEquals(listOf(1L, 2L), retried); assertFalse(expanded) }
        compose.onNodeWithText("2 项失败").assertIsDisplayed()
        captureProgress("failed")
        verifyCircularPressFeedback("重试失败项", "retry")
        compose.runOnIdle { cardGroup = group }
        compose.onNodeWithText(formatDownloadCreatedAt(context, group.createdAt)).assertIsDisplayed()
        compose.onNodeWithText("已保存", substring = true).assertDoesNotExist()
        compose.onNodeWithText("5 个任务", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithContentDescription("查看文件").assertCountEquals(1)
        compose.onNodeWithContentDescription("查看文件").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        compose.onNodeWithContentDescription("查看文件").performClick()
        compose.runOnIdle { assertTrue(expanded) }
        compose.onNodeWithContentDescription("收起文件").performClick()
        compose.runOnIdle { assertFalse(expanded); selection = true }
        compose.onNodeWithContentDescription("查看文件").assertDoesNotExist()
    }

    private fun verifySelectionMotion(
        title: String,
        supportingText: String,
        footer: SemanticsNodeInteraction,
        actionLabel: String,
        screenshotName: String,
        setSelection: (Boolean) -> Unit,
    ) {
        fun alignedCoverLeft(): Float {
            val cover = compose.onNodeWithContentDescription("视频封面", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("动画每一帧底栏都与封面左边对齐", cover.left.value, footer.getUnclippedBoundsInRoot().left.value, 1f)
            return cover.left.value
        }
        fun changeSelection(selected: Boolean) {
            compose.runOnIdle { setSelection(selected); Snapshot.sendApplyNotifications() }
        }
        val initialLeft = alignedCoverLeft()
        // 文字完整排版后，以其下方信息的位置验证动画占位，不能再用未裁剪的文字高度代替。
        fun titleToSummaryDistance(): Float {
            val titleTop = compose.onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot().top
            val summaryTop = compose.onNodeWithText(supportingText, useUnmergedTree = true).getUnclippedBoundsInRoot().top
            return (summaryTop - titleTop).value
        }
        val initialTitleToSummaryDistance = titleToSummaryDistance()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(title).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        val enteringPositions = mutableListOf<Float>()
        val enteringTitleToSummaryDistances = mutableListOf<Float>()
        repeat(16) { frame ->
            compose.mainClock.advanceTimeByFrame()
            enteringPositions.add(alignedCoverLeft())
            enteringTitleToSummaryDistances.add(titleToSummaryDistance())
            if (frame == 4) capture("selection-enter-$screenshotName", compose.onNodeWithText(title))
        }
        compose.mainClock.advanceTimeBy(1_000)
        val selectedLeft = alignedCoverLeft()
        val selectedTitleToSummaryDistance = titleToSummaryDistance()
        if (initialTitleToSummaryDistance > selectedTitleToSummaryDistance + 2f) {
            assertTrue("展开标题或换行收缩应从原高度连续过渡，不能在进入第一帧就折叠",
                enteringTitleToSummaryDistances.first() > selectedTitleToSummaryDistance +
                    (initialTitleToSummaryDistance - selectedTitleToSummaryDistance) * 0.6f)
            assertTrue("标题下方信息应随占位连续移动", enteringTitleToSummaryDistances.distinct().size > 3)
        }
        assertTrue("进入多选为复选框让出空间", selectedLeft > initialLeft + 20f)
        assertTrue("底栏应连续移动，不能直接跳到最终位置", enteringPositions.distinct().size > 3)
        assertTrue("进入第一帧不能已经完成位移", enteringPositions.first() < selectedLeft - 2f)
        compose.onNodeWithContentDescription(actionLabel).assertDoesNotExist()

        changeSelection(false)
        val exitingPositions = mutableListOf<Float>()
        repeat(16) { frame ->
            compose.mainClock.advanceTimeByFrame()
            exitingPositions.add(alignedCoverLeft())
            if (frame == 4) capture("selection-exit-$screenshotName", compose.onNodeWithText(title))
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("退出多选恢复原来的位置", initialLeft, alignedCoverLeft(), 1f)
        assertTrue("退出时也要连续移动", exitingPositions.distinct().size > 3)
        enteringPositions.zip(exitingPositions).forEachIndexed { frame, (entering, exiting) ->
            assertEquals("进出多选的第 $frame 帧应有相同位移，保持一致速度",
                entering - initialLeft, selectedLeft - exiting, 1f)
        }
        compose.onNodeWithContentDescription(actionLabel).assertIsDisplayed()

        // 尚未完成就反向切换，必须从当前画面续接，不能重置起点。
        changeSelection(true)
        compose.mainClock.advanceTimeBy(64)
        val beforeReverse = alignedCoverLeft()
        assertTrue(beforeReverse > initialLeft && beforeReverse < selectedLeft)
        changeSelection(false)
        compose.mainClock.advanceTimeByFrame()
        assertTrue("反向切换不应瞬间回到原点", alignedCoverLeft() > initialLeft + 1f)
        repeat(16) { compose.mainClock.advanceTimeByFrame(); alignedCoverLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(initialLeft, alignedCoverLeft(), 1f)
        changeSelection(true)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test fun lightDetailsCopyTitleAndValuesOnlyOnLongPress() = verifyDetails(AppThemeMode.Light)
    @Test fun darkDetailsCopyTitleAndValuesOnlyOnLongPress() = verifyDetails(AppThemeMode.Dark)

    private fun verifyDetails(mode: AppThemeMode) {
        var dismissed = false
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Sakura)) {
                DownloadsDetailsSheet(group) { dismissed = true }
            }
        }
        compose.onNodeWithText("147.0 MB", substring = true).assertIsDisplayed()
        val sheet = compose.onNode(isDialog())
        val sectionLeft = compose.onNodeWithText("基本信息").getUnclippedBoundsInRoot().left.value
        assertEquals("详情字段与分区标题左对齐", sectionLeft,
            compose.onNodeWithText("UP 主", useUnmergedTree = true).getUnclippedBoundsInRoot().left.value, 0.5f)
        assertEquals("长字段与分区标题左对齐", sectionLeft,
            compose.onNodeWithText(sourceUrl, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value, 0.5f)
        capture("details-${mode.name}", sheet)
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        compose.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("test", "原有剪贴板")) }
        val title = compose.onNodeWithText(group.title)
        val titleBounds = title.getUnclippedBoundsInRoot()
        title.performTouchInput { click() }
        compose.runOnIdle { assertEquals("轻点标题不复制", "原有剪贴板", clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        compose.mainClock.autoAdvance = false
        title.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        capture("details-title-pressed-${mode.name}", sheet)
        assertEquals("标题按压反馈不改变布局", titleBounds, title.getUnclippedBoundsInRoot())
        title.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        title.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals("长按复制完整标题", group.title, clipboard.primaryClip!!.getItemAt(0).text.toString())
            clipboard.setPrimaryClip(ClipData.newPlainText("test", "原有剪贴板"))
        }
        compose.onNodeWithText("10:25").performScrollTo().assertIsDisplayed()
        val sourceLabel = compose.onNodeWithText("来源链接")
        sourceLabel.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        sourceLabel.performTouchInput { longClick() }
        compose.runOnIdle { assertEquals("字段名不触发复制", "原有剪贴板", clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        val source = compose.onNodeWithText(sourceUrl).performScrollTo()
        source.performTouchInput { click() }
        compose.runOnIdle { assertEquals("原有剪贴板", clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        val labelBeforePress = sourceLabel.captureToImage().asAndroidBitmap()
        val sourceBounds = source.getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        source.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        capture("details-pressed-${mode.name}", sheet)
        assertTrue("来源链接标签不显示取值的按压阴影", labelBeforePress.sameAs(sourceLabel.captureToImage().asAndroidBitmap()))
        assertEquals("按压不改变内容布局", sourceBounds, source.getUnclippedBoundsInRoot())
        source.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals("原有剪贴板", clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        source.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(sourceUrl, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        val duration = compose.onNodeWithText("10:25").performScrollTo()
        val durationLabel = compose.onNodeWithText("时长")
        val durationLabelBeforePress = durationLabel.captureToImage().asAndroidBitmap()
        compose.mainClock.autoAdvance = false
        duration.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        capture("details-value-pressed-${mode.name}", sheet)
        assertTrue("两列字段只给取值显示阴影", durationLabelBeforePress.sameAs(durationLabel.captureToImage().asAndroidBitmap()))
        duration.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Download/BiliTools/夏日的光影").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Download/BiliTools/夏日的光影/夏日光影-音视频.mp4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("没有可用字幕").performScrollTo().assertIsDisplayed()
        capture("details-file-${mode.name}", sheet)
        compose.onNodeWithText("Download/BiliTools/夏日的光影").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.runOnIdle {
            assertEquals("Download/BiliTools/夏日的光影", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.runOnIdle { assertTrue(dismissed) }
    }

    private fun capture(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        val output = File("../.tmp/downloads-ui/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
