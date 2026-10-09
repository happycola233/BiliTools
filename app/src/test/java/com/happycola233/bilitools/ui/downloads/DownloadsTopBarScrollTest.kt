package com.happycola233.bilitools.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.ui.MainCollapsingTopBar
import com.happycola233.bilitools.ui.MainTopBarCollapsedHeight
import com.happycola233.bilitools.ui.MainTopBarExpandedHeight
import com.happycola233.bilitools.ui.collapsingTopBarOffset
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsTopBarScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun holdingHorizontalSwipeKeepsCollapsedTopBarInLightTheme() = verifySwipeHold(AppThemeMode.Light)
    @Test fun holdingHorizontalSwipeKeepsCollapsedTopBarInDarkTheme() = verifySwipeHold(AppThemeMode.Dark)
    @Test fun holdingSwipeNearListStartKeepsCollapsedTopBarInLightTheme() =
        verifySwipeHold(AppThemeMode.Light, nearListStart = true)
    @Test fun holdingSwipeNearListStartKeepsCollapsedTopBarInDarkTheme() =
        verifySwipeHold(AppThemeMode.Dark, nearListStart = true)

    private fun verifySwipeHold(mode: AppThemeMode, nearListStart: Boolean = false) {
        val groups = (1L..40L).map { id ->
            DownloadGroup(
                id = id, title = "下载组 $id", subtitle = null, createdAt = id,
                tasks = listOf(DownloadItem(
                    id = id, groupId = id, taskType = DownloadTaskType.Video,
                    title = "视频", fileName = "video.mp4", url = "",
                    status = DownloadStatus.Success, progress = 100,
                )),
            )
        }
        val routeState = DownloadsRouteUiState()
        lateinit var barState: TopAppBarState
        var touchSlopPx = 0f
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode)) {
                val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                barState = behavior.state
                touchSlopPx = LocalViewConfiguration.current.touchSlop
                Box(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
                    DownloadsListContent(
                        groups = groups, selectionMode = routeState.selectionMode,
                        selectedGroupIds = routeState.selectedGroupIds,
                        expandedGroupIds = routeState.expandedGroupIds,
                        collapsedSections = routeState.collapsedSections,
                        swipedGroupId = routeState.swipedGroupId,
                        contentTopPadding = MainTopBarExpandedHeight, listBottomPadding = 16.dp,
                        onToggleSection = routeState::toggleSection,
                        onToggleGroupExpanded = routeState::toggleGroupExpanded,
                        onSwipedGroupChange = { routeState.swipedGroupId = it },
                        onGroupSelectionToggle = { routeState.toggleGroupSelection(groups, it) },
                        onGroupDelete = { _, _ -> }, onGroupPause = {}, onGroupResume = {},
                        onGroupReparse = {}, onGroupShowDetails = {}, onTaskPauseResume = {},
                        onTaskRetry = {}, onTaskClick = { _, _ -> },
                        onDragSelectionStart = { routeState.startDragSelection(groups, it) },
                        onDragSelectionRange = routeState::updateDragSelection,
                        onDragSelectionEnd = routeState::finishDragSelection,
                        modifier = Modifier.collapsingTopBarOffset { barState.heightOffset }
                            .fillMaxSize().testTag("downloads-list"),
                    )
                    MainCollapsingTopBar(title = "下载", state = barState)
                }
            }
        }
        val list = compose.onNodeWithTag("downloads-list")
        if (!nearListStart) list.performScrollToIndex(5)
        list.performTouchInput {
            // 通过实际纵向手势折叠顶栏。
            val distance = if (nearListStart) {
                (MainTopBarExpandedHeight - MainTopBarCollapsedHeight).toPx() + touchSlopPx
            } else {
                100.dp.toPx()
            }
            swipe(center, center - Offset(0f, distance), durationMillis = 500)
        }
        compose.runOnIdle { assertEquals(1f, barState.collapsedFraction, 0f) }
        // 列表起点处没有可向下回滚的内容，泄漏的位移会直接展开顶栏。
        if (nearListStart) list.performScrollToIndex(0)
        val swipedGroupId = if (nearListStart) 3L else 8L
        val title = compose.onNodeWithText("下载组 $swipedGroupId")
        val before = title.getUnclippedBoundsInRoot()
        val listBounds = list.getUnclippedBoundsInRoot()
        val position = with(compose.density) {
            Offset(
                ((before.left + before.right) / 2 - listBounds.left).toPx(),
                ((before.top + before.bottom) / 2 - listBounds.top).toPx(),
            )
        }
        compose.mainClock.autoAdvance = false
        list.performTouchInput {
            down(position)
            moveBy(Offset(-80.dp.toPx(), 16.dp.toPx()), delayMillis = 32)
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue("已识别侧滑并移动卡片", title.getUnclippedBoundsInRoot().left < before.left - 40.dp)
        compose.runOnIdle { assertEquals("静止停留保持折叠", 1f, barState.collapsedFraction, 0f) }
        // 手指停留时仍可能产生只有纵向抖动的 MOVE 事件。
        list.performTouchInput {
            moveBy(Offset(0f, 1f), delayMillis = 1_000)
            moveBy(Offset(0f, 1f), delayMillis = 16)
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            assertFalse("侧滑停留不能触发长按多选", routeState.selectionMode)
            assertEquals("侧滑停留不能展开顶栏", 1f, barState.collapsedFraction, 0f)
        }
        assertEquals("侧滑停留不能改变列表纵向位置", before.top, title.getUnclippedBoundsInRoot().top)
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals("松手后仍保持折叠", 1f, barState.collapsedFraction, 0f)
            assertEquals("侧滑松手仍能正常停靠", swipedGroupId, routeState.swipedGroupId)
            routeState.swipedGroupId = null
        }
        list.performScrollToIndex(0)
        list.performTouchInput {
            swipe(center, center + Offset(0f, 150.dp.toPx()), durationMillis = 500)
        }
        compose.runOnIdle { assertEquals("正常下滑到顶部仍可展开顶栏", 0f, barState.collapsedFraction, 0f) }
    }
}
