package com.happycola233.bilitools.ui.downloads

import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class DownloadsDragSelectionTest {
    @get:Rule val compose = createComposeRule()

    private val groups = (1L..12L).map { id ->
        DownloadGroup(
            id = id, title = "下载组 $id", subtitle = null, createdAt = 1_789_283_696_000L - id,
            tasks = listOf(DownloadItem(
                id = id, groupId = id, taskType = DownloadTaskType.Video, title = "视频", fileName = "video.mp4", url = "",
                status = DownloadStatus.Success, progress = 100, localUri = "content://media/$id",
            )),
        )
    }
    private val state = DownloadsRouteUiState()
    private var expandToggles = 0

    private fun setContent() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_BiliTools)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                BiliToolsTheme(AppSettings()) {
                    Box(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor)) {
                        DownloadsListContent(
                            groups = groups, selectionMode = state.selectionMode, selectedGroupIds = state.selectedGroupIds,
                            expandedGroupIds = state.expandedGroupIds, collapsedSections = state.collapsedSections,
                            swipedGroupId = state.swipedGroupId, contentTopPadding = 16.dp, listBottomPadding = 16.dp,
                            onToggleSection = state::toggleSection,
                            onToggleGroupExpanded = { expandToggles++; state.toggleGroupExpanded(it) },
                            onSwipedGroupChange = { state.swipedGroupId = it },
                            onGroupSelectionToggle = { state.toggleGroupSelection(groups, it) },
                            onGroupDelete = { _, _ -> }, onGroupPause = {}, onGroupResume = {}, onGroupReparse = {},
                            onGroupShowDetails = {}, onTaskPauseResume = {}, onTaskRetry = {}, onTaskClick = { _, _ -> },
                            onDragSelectionStart = { state.startDragSelection(groups, it) },
                            onDragSelectionRange = state::updateDragSelection,
                            onDragSelectionEnd = state::finishDragSelection,
                            modifier = Modifier.fillMaxSize().testTag("list"),
                        )
                    }
                }
            }
        }
    }

    /** 某个下载组标题中心在列表坐标系中的位置（像素）。 */
    private fun centerOf(id: Long): Offset {
        val list = compose.onNodeWithTag("list").getBoundsInRoot()
        val title = compose.onNodeWithText("下载组 $id").getBoundsInRoot()
        return with(compose.density) {
            Offset(((list.right - list.left) / 2).toPx(), ((title.top + title.bottom) / 2 - list.top).toPx())
        }
    }

    private fun longPressAt(id: Long) {
        val position = centerOf(id)
        compose.onNodeWithTag("list").performTouchInput { down(position) }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun moveTo(id: Long) {
        val position = centerOf(id)
        compose.onNodeWithTag("list").performTouchInput { moveTo(position, delayMillis = 32) }
        compose.mainClock.advanceTimeBy(100)
    }

    private fun release() {
        compose.onNodeWithTag("list").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test fun longPressDragSelectsARangeAndDraggingBackRestoresIt() {
        setContent()
        compose.mainClock.autoAdvance = false
        longPressAt(2)
        assertTrue("长按进入多选", state.selectionMode)
        assertEquals(setOf(2L), state.selectedGroupIds)
        moveTo(3)
        moveTo(5)
        assertEquals("拖过的连续范围全部选中", setOf(2L, 3L, 4L, 5L), state.selectedGroupIds)
        moveTo(3)
        assertEquals("不松手往回拖，退出范围的组取消选择", setOf(2L, 3L), state.selectedGroupIds)
        release()
        assertEquals("松手不再触发一次点击", setOf(2L, 3L), state.selectedGroupIds)
        assertEquals(0, expandToggles)
        compose.mainClock.autoAdvance = true
    }

    @Test fun longPressOnASelectedCheckboxStillSelectsTheDraggedRange() {
        setContent()
        compose.runOnIdle {
            state.enterSelectionMode(groups)
            listOf(1L, 3L).forEach { state.toggleGroupSelection(groups, it) }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // 复现：多选态下在已选中项的复选框上长按，再往下拖。
        val list = compose.onNodeWithTag("list").getBoundsInRoot()
        val checkbox = compose.onAllNodes(isToggleable(), useUnmergedTree = true)[2].getBoundsInRoot()
        val checkboxCenter = with(compose.density) {
            Offset(((checkbox.left + checkbox.right) / 2 - list.left).toPx(), ((checkbox.top + checkbox.bottom) / 2 - list.top).toPx())
        }
        compose.onNodeWithTag("list").performTouchInput { down(checkboxCenter) }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("长按已选中项不改变其选中状态", setOf(1L, 3L), state.selectedGroupIds)
        moveTo(5)
        assertEquals("拖过的范围全部选中，已选中的保持不变", setOf(1L, 3L, 4L, 5L), state.selectedGroupIds)
        moveTo(3)
        assertEquals("往回拖只撤销本次拖动新增的选中", setOf(1L, 3L), state.selectedGroupIds)
        release()
        assertEquals("松手不会再切换复选框", setOf(1L, 3L), state.selectedGroupIds)
        compose.mainClock.autoAdvance = true
    }

    @Test fun quickTapAndHorizontalSwipeKeepTheirOwnBehaviour() {
        setContent()
        compose.onNodeWithText("下载组 2").performTouchInput { down(center); up() }
        compose.runOnIdle {
            assertEquals("轻点仍展开卡片", 1, expandToggles)
            assertFalse(state.selectionMode)
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("下载组 3").performTouchInput {
            down(center)
            moveBy(Offset(-(viewConfiguration.touchSlop + 60.dp.toPx()), 0f), delayMillis = 32)
        }
        compose.mainClock.advanceTimeBy(1_500)
        assertFalse("横滑后按住不放不会被识别成长按多选", state.selectionMode)
        compose.onNodeWithText("下载组 3").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
    }

    @Test fun holdingNearTheBottomEdgeScrollsAndExtendsTheRange() {
        setContent()
        compose.mainClock.autoAdvance = false
        longPressAt(1)
        compose.onNodeWithTag("list").performTouchInput { moveTo(Offset(center.x, bottom - 4f), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(3_000)
        assertTrue("贴住底边自动滚动并把后面的组纳入范围：${state.selectedGroupIds}", state.selectedGroupIds.size >= 8)
        assertEquals("范围连续", (1L..state.selectedGroupIds.max()).toSet(), state.selectedGroupIds)
        release()
        compose.mainClock.autoAdvance = true
    }
}
