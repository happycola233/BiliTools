package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
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
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsSelectionMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightListMovesAsOneLayout() = verifyList(AppThemeMode.Light)
    @Test fun darkListMovesAsOneLayout() = verifyList(AppThemeMode.Dark)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h891dp")
    fun narrowListWithLargeTextMovesAsOneLayout() = verifyList(AppThemeMode.Light, 1.5f)

    @Test fun wrappingTitlesUseTheSameLayoutClock() = verifyList(AppThemeMode.Light, wrapping = true)
    @Test fun rtlWrappingTitlesUseTheSameLayoutClock() = verifyList(AppThemeMode.Dark, wrapping = true, direction = LayoutDirection.Rtl)

    private fun verifyList(
        mode: AppThemeMode, fontScale: Float = 1f, wrapping: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val groups = (1L..3L).map { id ->
            DownloadGroup(
                id = id, title = if (wrapping) "测".repeat(12) + id else "下载组 $id", subtitle = null, bvid = null, createdAt = 100 - id,
                tasks = listOf(DownloadItem(
                    id = id, groupId = id, taskType = DownloadTaskType.Video, title = "视频", fileName = "video.mp4", url = "",
                    status = DownloadStatus.Paused, progress = 40, userPaused = true,
                )),
            )
        }
        var selection by mutableStateOf(false)
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_BiliTools)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalDensity provides Density(LocalDensity.current.density, fontScale), LocalLayoutDirection provides direction) {
                BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Sakura)) {
                    val motion = rememberDownloadsSelectionMotion(selection)
                    Box(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor)) {
                        DownloadsListContent(
                            groups = groups, selectionMode = selection, selectionMotion = motion,
                            selectedGroupIds = if (selection) setOf(1L) else emptySet(), expandedGroupIds = emptySet(),
                            collapsedSections = emptySet(), swipedGroupId = null, contentTopPadding = 0.dp,
                            listBottomPadding = 24.dp + 180.dp * motion.progress,
                            onToggleSection = {}, onToggleGroupExpanded = {}, onSwipedGroupChange = {},
                            onGroupSelectionToggle = { selection = true }, onGroupDelete = {}, onGroupPause = {}, onGroupResume = {},
                            onGroupReparse = {}, onGroupShowDetails = {}, onTaskPauseResume = {}, onTaskRetry = {}, onTaskDelete = {},
                            onTaskClick = { _, _ -> }, modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        fun bounds() = groups.map { compose.onNodeWithText(it.title).getUnclippedBoundsInRoot() }
        fun titleHeight() = compose.onNodeWithText(groups.first().title, useUnmergedTree = true).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        fun startEdge(bounds: androidx.compose.ui.unit.DpRect) = if (direction == LayoutDirection.Rtl) bounds.right.value else bounds.left.value
        val initialTitleHeight = titleHeight()
        val initial = bounds()
        val gaps = initial.zipWithNext { first, second -> (second.top - first.bottom).value }
        fun verifyFrame() {
            val cards = bounds()
            cards.zipWithNext().forEachIndexed { index, (first, second) ->
                assertEquals("列表不能追赶卡片尺寸动画而拉开组间距", gaps[index], (second.top - first.bottom).value, 1f)
            }
            val covers = compose.onAllNodesWithContentDescription("视频封面", useUnmergedTree = true)
            val footers = compose.onAllNodesWithText("已暂停", useUnmergedTree = true)
            repeat(groups.size) { index ->
                assertEquals("所有卡片边距同步变化", cards.first().left.value, cards[index].left.value, 1f)
                assertEquals("每张卡片的封面和底栏始终对齐", startEdge(covers[index].getUnclippedBoundsInRoot()),
                    startEdge(footers[index].getUnclippedBoundsInRoot()), 1f)
            }
        }
        fun capture(name: String) {
            val file = File("../.tmp/downloads-ui/list-selection-$name-${mode.name}-$fontScale.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun select(value: Boolean) = compose.runOnIdle { selection = value; Snapshot.sendApplyNotifications() }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(groups.first().title).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        repeat(36) { frame ->
            compose.mainClock.advanceTimeByFrame()
            verifyFrame()
            if (frame == 5) capture("enter")
        }
        compose.mainClock.advanceTimeBy(1_000)
        val selected = bounds()
        if (wrapping) assertTrue("测试标题必须在多选前后发生换行变化", titleHeight() < initialTitleHeight)
        assertTrue("多选会收紧卡片高度，这才覆盖列表位移的回归", selected.last().top < initial.last().top)
        select(false)
        repeat(36) { frame ->
            compose.mainClock.advanceTimeByFrame()
            verifyFrame()
            if (frame == 5) capture("exit")
            if (frame == 11) {
                assertTrue("退出后 200 毫秒内整组条目应基本归位",
                    abs(bounds().last().top.value - initial.last().top.value) <= 3f)
            }
        }
        val endOfExit = bounds()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("标题不能在多选结束后继续牵动卡片", endOfExit, bounds())
        initial.zip(bounds()).forEach { (before, after) -> assertEquals(before, after) }
        // 退出尚未完成又重新进入，后面的条目也不能滞后或突然跳回。
        select(true)
        compose.mainClock.advanceTimeBy(1_000)
        select(false)
        repeat(4) { compose.mainClock.advanceTimeByFrame(); verifyFrame() }
        select(true)
        repeat(36) { compose.mainClock.advanceTimeByFrame(); verifyFrame() }
        compose.mainClock.advanceTimeBy(1_000)
        verifyFrame()
        compose.mainClock.autoAdvance = true
    }
}
