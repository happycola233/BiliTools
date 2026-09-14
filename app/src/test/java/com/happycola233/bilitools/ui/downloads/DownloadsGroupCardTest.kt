package com.happycola233.bilitools.ui.downloads

import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
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
            task(1, DownloadTaskType.AudioVideo, "音视频", "mp4", 72),
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
        var expanded by mutableStateOf(false)
        var selectionMode by mutableStateOf(false)
        var cardTitle by mutableStateOf(group.title)
        var reparsed = 0
        var detailsOpened = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BiliToolsTheme(AppSettings(themeMode = themeMode, themeColor = AppThemeColor.Sakura)) {
                    Column(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor).verticalScroll(rememberScrollState())) {
                        DownloadsGroupCard(
                            group = group.copy(title = cardTitle), selectionMode = selectionMode, selected = selectionMode,
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
        val timestamp = formatDownloadCreatedAt(RuntimeEnvironment.getApplication(), group.createdAt)
        fun assertHeaderColumns() {
            val cover = compose.onNodeWithContentDescription("视频封面", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val title = compose.onNodeWithText(group.title, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val time = compose.onNodeWithText(timestamp, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("标题始终位于封面右侧", title.left >= cover.right)
            assertTrue("完整时间始终位于封面右侧", time.left >= cover.right)
            assertTrue("标题与封面并排起始", title.top < cover.bottom)
        }
        assertHeaderColumns()
        compose.onNodeWithText(group.bvid!!).assertDoesNotExist()
        compose.onNodeWithText("创建于", substring = true).assertDoesNotExist()
        compose.onNodeWithText("重新解析").assertDoesNotExist()
        capture("collapsed-${themeMode.name}-$fontScale")
        val longTitleBounds = compose.onNodeWithText(group.title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.runOnIdle { cardTitle = "绘画练习" }
        val shortTitleBounds = compose.onNodeWithText(cardTitle, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val timeBounds = compose.onNodeWithText(timestamp, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardBounds = compose.onNodeWithText(cardTitle).getUnclippedBoundsInRoot()
        assertTrue("短标题不再预留第二行空白",
            shortTitleBounds.bottom - shortTitleBounds.top < longTitleBounds.bottom - longTitleBounds.top)
        assertEquals("标题与时间作为整体在卡片内垂直居中",
            (cardBounds.top.value + cardBounds.bottom.value) / 2,
            (shortTitleBounds.top.value + timeBounds.bottom.value) / 2, 0.5f)
        capture("collapsed-short-title-${themeMode.name}-$fontScale")
        compose.runOnIdle { cardTitle = group.title }
        compose.onNodeWithText(group.title).performClick()
        compose.onNodeWithText("重新解析").performScrollTo().performClick()
        compose.onNodeWithText("查看详细信息").performScrollTo().performClick()
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
        compose.onNodeWithText("1.0 MB", substring = true).assertDoesNotExist()
        compose.onNodeWithText("2.0 MB").assertDoesNotExist()
        compose.runOnIdle { expanded = false; selectionMode = true }
        compose.onNodeWithText(group.title).performScrollTo()
        assertHeaderColumns()
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
