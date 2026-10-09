package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.AppDestructiveColors
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewGroup

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp", shadows = [SwipeHapticViewGroupShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsGroupSwipeTest {
    @get:Rule val compose = createComposeRule()

    private val group = DownloadGroup(
        id = 1, title = "侧滑删除的下载条目", subtitle = null, bvid = null, createdAt = 1,
        tasks = listOf(
            DownloadItem(
                id = 1, groupId = 1, taskType = DownloadTaskType.Video, title = "视频",
                fileName = "video.mp4", url = "", status = DownloadStatus.Success, progress = 100,
            ),
        ),
    )
    private var swiped by mutableStateOf(false)
    private var deleteRequests = 0
    private lateinit var hapticViewShadow: SwipeHapticViewGroupShadow
    private val feedback get() = hapticViewShadow.feedback
    private var touchSlopPx = 0f
    private var density = 1f
    private val card get() = compose.onNodeWithTag("swipe-card")
    private val clearButton get() = compose.onNodeWithContentDescription("清除记录")
    private val filesButton get() = compose.onNodeWithContentDescription("删除文件")
    private val requestedFileDeletion = mutableListOf<Boolean>()
    private var dragSign = -1f
    private var referenceSize by mutableStateOf(DpSize.Zero)

    @Test fun lightSwipeUsesTwoActionThresholdsAndDocksBeforeRemoval() = verifySwipe(AppThemeMode.Light)
    @Test fun darkSwipeUsesTwoActionThresholdsAndDocksBeforeRemoval() = verifySwipe(AppThemeMode.Dark)
    @Test fun rtlLightSwipeUsesTwoActionThresholdsAndDocksBeforeRemoval() = verifySwipe(AppThemeMode.Light, LayoutDirection.Rtl)
    @Test fun rtlDarkSwipeUsesTwoActionThresholdsAndDocksBeforeRemoval() = verifySwipe(AppThemeMode.Dark, LayoutDirection.Rtl)

    @Test fun lightConcurrentSwipesReturnTheOtherGroupToRest() = verifyConcurrentSwipes(AppThemeMode.Light)
    @Test fun darkConcurrentSwipesReturnTheOtherGroupToRest() = verifyConcurrentSwipes(AppThemeMode.Dark)

    @Test fun rtlSwipeFollowsTheFingerAndRevealsDeleteAtTheStartEdge() {
        compose.setContent {
            density = LocalDensity.current.density
            touchSlopPx = LocalViewConfiguration.current.touchSlop
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BiliToolsTheme(AppSettings(themeMode = AppThemeMode.Dark)) {
                    DownloadsGroupCard(
                        group = group, selectionMode = false, selected = false, expanded = false,
                        swiped = swiped, anyGroupSwiped = swiped,
                        onSwipedGroupChange = { swiped = it == group.id },
                        onToggleSelection = {}, onToggleExpanded = {}, onDelete = { deleteRequests++ },
                        onPauseGroup = {}, onResumeGroup = {}, onReparse = {}, onShowDetails = {},
                        onTaskPauseResume = {}, onTaskRetry = {}, onTaskClick = { _, _ -> },
                        modifier = Modifier.testTag("swipe-card"),
                    )
                }
            }
        }
        fun cardLeft() = compose.onNodeWithText(group.title).getUnclippedBoundsInRoot().left.value
        val closed = cardLeft()
        fun drag(distance: Float) = card.performTouchInput {
            down(Offset(120f * density, center.y))
            moveBy(Offset(touchSlopPx + distance * density, 0f), delayMillis = 32)
        }
        // RTL 向左拖动不能让卡片向反方向移动。
        card.performTouchInput { down(center); moveBy(Offset(-80f * density, 0f)); up() }
        assertEquals(closed, cardLeft(), 0.5f)
        drag(30f)
        assertEquals(closed + 30f, cardLeft(), 0.5f)
        card.performTouchInput { up() }
        assertEquals(closed, cardLeft(), 0.5f)
        drag(88f)
        card.performTouchInput { up() }
        assertEquals(closed + 88f, cardLeft(), 0.5f)
        val actionBounds = clearButton.getUnclippedBoundsInRoot()
        assertEquals(closed, actionBounds.left.value, 0.5f)
        assertEquals(8f, cardLeft() - actionBounds.right.value, 0.5f)
        // 继续右拖跨过删除阈值，松开后仍回到同一停靠点。
        drag(80f)
        card.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, deleteRequests) }
        assertEquals(closed + 88f, cardLeft(), 0.5f)
    }

    @Test fun rtlConcurrentSwipesReturnTheOtherGroupToRest() = verifyConcurrentSwipes(AppThemeMode.Light, LayoutDirection.Rtl)

    private fun verifyConcurrentSwipes(mode: AppThemeMode, direction: LayoutDirection = LayoutDirection.Ltr) {
        val sign = if (direction == LayoutDirection.Rtl) -1f else 1f
        val groups = listOf(group, group.copy(id = 2, title = "另一个下载组"))
        var swipedGroupId by mutableStateOf<Long?>(null)
        var selectionMode by mutableStateOf(false)
        compose.setContent {
            density = LocalDensity.current.density
            touchSlopPx = LocalViewConfiguration.current.touchSlop
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Periwinkle)) {
                    Column(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor).testTag("swipe-list")) {
                        groups.forEach { item ->
                            DownloadsGroupCard(
                                group = item, selectionMode = selectionMode, selected = false, expanded = false,
                                swiped = swipedGroupId == item.id, anyGroupSwiped = swipedGroupId != null,
                                onSwipedGroupChange = { swipedGroupId = it },
                                onToggleSelection = {}, onToggleExpanded = {}, onDelete = { deleteRequests++ },
                                onPauseGroup = {}, onResumeGroup = {}, onReparse = {}, onShowDetails = {},
                                onTaskPauseResume = {}, onTaskRetry = {}, onTaskClick = { _, _ -> },
                            )
                        }
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("swipe-list")
        fun bounds(index: Int) = compose.onNodeWithText(groups[index].title).getUnclippedBoundsInRoot()
        fun edge(index: Int) = if (direction == LayoutDirection.Rtl) bounds(index).left.value else bounds(index).right.value
        val closedEdge = edge(0)
        fun position(index: Int) = bounds(index).let { Offset((if (direction == LayoutDirection.Rtl) 150f else 250f) * density, (it.top.value + it.bottom.value) / 2 * density) }
        fun assertOffset(index: Int, offset: Float) {
            assertEquals("下载组 ${index + 1} 应回到确定的停靠位置", offset, (closedEdge - edge(index)) * sign, 0.5f)
        }

        // 两个手指分别拖动两个组；任意一个先停靠，另一个尚未松手也应归位。
        for ((interrupted, docked) in listOf(0 to 1, 1 to 0)) {
            compose.runOnIdle { swipedGroupId = null }
            compose.waitForIdle()
            val partialOffset = if (interrupted == 0) 30f else 60f
            val interruptedPosition = position(interrupted)
            val dockedPosition = position(docked)
            list.performTouchInput {
                down(0, interruptedPosition)
                moveBy(0, Offset(-sign * (touchSlopPx + partialOffset * density), 0f), delayMillis = 32)
                down(1, dockedPosition)
                moveBy(1, Offset(-sign * (touchSlopPx + 88f * density), 0f), delayMillis = 32)
            }
            assertOffset(interrupted, partialOffset)
            list.performTouchInput { up(1) }
            compose.runOnIdle { assertEquals(groups[docked].id, swipedGroupId) }
            assertOffset(interrupted, 0f)
            assertOffset(docked, 88f)
            // 被打断的旧手势继续移动和松手，不得重新停靠、误删或清除新组的状态。
            list.performTouchInput {
                moveBy(0, Offset(-sign * 160f * density, 0f), delayMillis = 32)
                up(0)
            }
            compose.runOnIdle { assertEquals(groups[docked].id, swipedGroupId); assertEquals(0, deleteRequests) }
            assertOffset(interrupted, 0f)
            assertOffset(docked, 88f)
        }

        // 上一个组已停靠时，下一次侧滑应一次完成，不能因为收起旧组而中断新手势。
        val nextPosition = position(1)
        list.performTouchInput {
            down(nextPosition)
            moveBy(Offset(-sign * (touchSlopPx + 30f * density), 0f), delayMillis = 32)
        }
        assertOffset(0, 0f)
        assertOffset(1, 30f)
        list.performTouchInput { moveBy(Offset(-sign * 58f * density, 0f), delayMillis = 32); up() }
        compose.runOnIdle { assertEquals(groups[1].id, swipedGroupId) }
        assertOffset(1, 88f)

        // 多选会移除侧滑处理器；被取消的拖动也必须释放，退出多选后不能残留位移。
        val selectionDragPosition = position(1)
        list.performTouchInput {
            down(selectionDragPosition)
            moveBy(Offset(-sign * (touchSlopPx + 30f * density), 0f), delayMillis = 32)
        }
        compose.runOnIdle { selectionMode = true; swipedGroupId = null }
        list.performTouchInput { cancel() }
        compose.runOnIdle { selectionMode = false }
        assertOffset(0, 0f)
        assertOffset(1, 0f)
        compose.runOnIdle { assertEquals(0, deleteRequests) }
    }

    private fun verifySwipe(mode: AppThemeMode, direction: LayoutDirection = LayoutDirection.Ltr) {
        dragSign = if (direction == LayoutDirection.Rtl) 1f else -1f
        compose.setContent {
            hapticViewShadow = Shadow.extract(LocalView.current)
            density = LocalDensity.current.density
            touchSlopPx = LocalViewConfiguration.current.touchSlop
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Periwinkle)) {
                    Column(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor)) {
                        DownloadsGroupCard(
                            group = group, selectionMode = false, selected = false, expanded = false,
                            swiped = swiped, anyGroupSwiped = swiped,
                            onSwipedGroupChange = { swiped = it == group.id },
                            onToggleSelection = {}, onToggleExpanded = {},
                            onDelete = { requestedFileDeletion += it },
                            onPauseGroup = {}, onResumeGroup = {}, onReparse = {}, onShowDetails = {},
                            onTaskPauseResume = {}, onTaskRetry = {}, onTaskClick = { _, _ -> },
                            modifier = Modifier.testTag("swipe-card"),
                        )
                        Surface(
                            color = AppDestructiveColors.strongContainer,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.size(referenceSize).testTag("strong-reference"),
                        ) {}
                        Row(
                            modifier = Modifier.padding(top = 16.dp).testTag("icon-comparison"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DownloadsSwipeStage.entries.forEach { stage ->
                                Surface(color = androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(20.dp), modifier = Modifier.size(80.dp, 88.dp)) {
                                    DownloadsSwipeAction(stage)
                                }
                            }
                        }
                    }
                }
            }
        }
        val activate = HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        val deactivate = HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
        clearButton.assertDoesNotExist()
        startDrag(30f)
        card.performTouchInput { up() }
        compose.runOnIdle { assertFalse(swiped); assertTrue(feedback.isEmpty()) }
        clearButton.assertDoesNotExist()

        // 前半程揭开固定宽度的按钮，图标仍被前景卡片遮住；完全露出后才开始拉伸。
        startDrag(40f)
        val partiallyRevealedBounds = clearButton.getUnclippedBoundsInRoot()
        assertEquals("揭露期间保持完整按钮宽度", 80f,
            (partiallyRevealedBounds.right - partiallyRevealedBounds.left).value, 0.5f)
        val foreground = compose.onNodeWithText(group.title).getUnclippedBoundsInRoot()
        val overlap = if (direction == LayoutDirection.Rtl) {
            partiallyRevealedBounds.right - foreground.left
        } else {
            foreground.right - partiallyRevealedBounds.left
        }
        assertEquals("卡片仍遮住按钮的一半", 40f, overlap.value, 0.5f)
        capture("revealing-${mode.name}-${direction.name}")
        moveFurther(40f)
        assertEquals("揭露期间按钮位置与宽度固定", partiallyRevealedBounds, clearButton.getUnclippedBoundsInRoot())
        capture("revealed-${mode.name}-${direction.name}")
        moveFurther(8f)
        card.performTouchInput { up() }
        compose.runOnIdle { assertTrue(swiped); assertTrue(feedback.isEmpty()) }
        assertActionGeometry("清除记录", 80f, direction)
        val restingIconTop = iconTop(clearButton.captureToImage().asAndroidBitmap())
        val restingIconSize = iconSize(clearButton.captureToImage().asAndroidBitmap())
        val restingInkArea = measureIconInk(clearButton.captureToImage().asAndroidBitmap()).area
        capture("resting-${mode.name}-${direction.name}")
        saveImage("icons-${mode.name}-${direction.name}", compose.onNodeWithTag("icon-comparison").captureToImage().asAndroidBitmap())

        // 停靠与触发前始终显示列表移除图标，直到 140dp 才放大并震动。
        startDrag(51f)
        assertFeedback()
        assertActionGeometry("清除记录", 131f, direction)
        assertEquals("移除阈值前图标不提前放大", restingIconTop, iconTop(clearButton.captureToImage().asAndroidBitmap()))
        moveFurther(1f)
        assertFeedback(activate)
        assertActionGeometry("清除记录", 132f, direction)
        compose.runOnIdle { assertTrue(requestedFileDeletion.isEmpty()) }
        assertTrue("达到移除阈值后图标放大", iconTop(clearButton.captureToImage().asAndroidBitmap()) < restingIconTop)
        val recordsIconSize = iconSize(clearButton.captureToImage().asAndroidBitmap())
        val recordsInkArea = measureIconInk(clearButton.captureToImage().asAndroidBitmap()).area
        assertTrue("触发后可见图标明显大于初始状态", recordsIconSize >= restingIconSize + 3 * density)
        assertTrue("触发后的列表图标分量大于初始状态", recordsInkArea > restingInkArea * 1.3)
        capture("clear-${mode.name}-${direction.name}")
        moveFurther(79f)
        assertFeedback(activate)
        assertActionGeometry("清除记录", 211f, direction)

        // 正好在 220dp 开盖、换色并再次震动；颜色沿手势方向扫过，不整面闪换。
        val pale = clearButton.captureToImage().asAndroidBitmap().let { it.getPixel(it.width / 2, it.height / 4) }
        compose.mainClock.autoAdvance = false
        moveFurther(1f)
        assertFeedback(activate, activate)
        compose.mainClock.advanceTimeBy(96)
        val sweep = filesButton.captureToImage().asAndroidBitmap()
        val left = sweep.getPixel(sweep.width / 10, sweep.height / 4)
        val right = sweep.getPixel(sweep.width * 9 / 10, sweep.height / 4)
        assertEquals("颜色从逻辑 end 向 start 过渡", pale, if (direction == LayoutDirection.Rtl) right else left)
        assertTrue("end 一侧先变成深红", pale != if (direction == LayoutDirection.Rtl) left else right)
        val strong = if (direction == LayoutDirection.Rtl) left else right
        fun frontAt(y: Int): Int = if (direction == LayoutDirection.Rtl) {
            (0 until sweep.width).last { sweep.getPixel(it, y) == strong }
        } else {
            (0 until sweep.width).first { sweep.getPixel(it, y) == strong }
        }
        val roundedFront = frontAt((3 * density).toInt())
        val straightFront = frontAt((24 * density).toInt())
        assertTrue("深红背景前缘保留圆角", if (direction == LayoutDirection.Rtl) roundedFront < straightFront else roundedFront > straightFront)
        saveImage("wipe-${mode.name}-${direction.name}", sweep)
        compose.mainClock.autoAdvance = true
        assertActionGeometry("删除文件", 212f, direction)
        val trashInkArea = measureIconInk(filesButton.captureToImage().asAndroidBitmap()).area
        assertTrue("垃圾桶应有与触发后列表图标相近的笔画分量：$trashInkArea / $recordsInkArea",
            trashInkArea in recordsInkArea * 0.9..recordsInkArea * 1.15)
        assertTrue("垃圾桶的笔画分量也应明显大于初始状态", trashInkArea > restingInkArea * 1.3)
        assertSolidBackgroundCorners()
        capture("files-${mode.name}-${direction.name}")
        moveFurther(30f)
        assertFeedback(activate, activate)
        assertActionGeometry("删除文件", 242f, direction)

        // 回拖准确撤销当前档位；在移除档松手只清记录，不沿用文件删除。
        moveFurther(-31f)
        assertFeedback(activate, activate, deactivate)
        assertActionGeometry("清除记录", 211f, direction)
        card.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(false), requestedFileDeletion) }
        assertActionGeometry("清除记录", 80f, direction)

        compose.runOnIdle { swiped = false }
        compose.waitForIdle()
        feedback.clear()
        // 快速跨过两档，只产生一次有效触感，松手后继续完成开盖并保持居中。
        compose.mainClock.autoAdvance = false
        startDrag(250f)
        compose.mainClock.advanceTimeBy(48)
        val fastSwipe = filesButton.captureToImage().asAndroidBitmap()
        assertEquals("快速跨档仍从浅红开始过渡", pale, fastSwipe.getPixel(fastSwipe.width / 2, fastSwipe.height / 4))
        card.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        assertFeedback(activate)
        compose.runOnIdle { assertEquals(listOf(false, true), requestedFileDeletion) }
        assertActionGeometry("删除文件", 80f, direction)
        assertSolidBackgroundCorners()
        capture("confirmation-${mode.name}-${direction.name}")

        compose.runOnIdle { swiped = false }
        compose.waitForIdle()
        startDrag(88f)
        card.performTouchInput { up() }
        feedback.clear()
        startDrag(132f)
        card.performTouchInput { cancel() }
        compose.runOnIdle { assertEquals(2, requestedFileDeletion.size); assertTrue(swiped) }
        assertActionGeometry("清除记录", 80f, direction)

        // 回到移除阈值内再松手，只停靠；快速反向不能留下动画或删除请求。
        feedback.clear()
        compose.mainClock.autoAdvance = false
        startDrag(132f)
        compose.mainClock.advanceTimeBy(48)
        moveFurther(-81f)
        card.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        assertFeedback(activate, deactivate)
        compose.runOnIdle { assertEquals(2, requestedFileDeletion.size) }
        assertActionGeometry("清除记录", 80f, direction)
        clearButton.performClick()
        compose.runOnIdle { assertEquals(listOf(false, true, false), requestedFileDeletion) }
    }

    private fun startDrag(distanceDp: Float) {
        card.performTouchInput {
            down(center)
            moveBy(Offset(dragSign * (touchSlopPx + distanceDp * density), 0f), delayMillis = 32)
        }
        compose.waitForIdle()
    }

    private fun moveFurther(distanceDp: Float) {
        card.performTouchInput { moveBy(Offset(dragSign * distanceDp * density, 0f), delayMillis = 32) }
        compose.waitForIdle()
    }

    private fun assertFeedback(vararg expected: Int) {
        compose.runOnIdle { assertEquals(expected.toList(), feedback) }
    }

    private fun assertActionGeometry(description: String, width: Float, direction: LayoutDirection) {
        val action = compose.onNodeWithContentDescription(description)
        val bounds = action.getUnclippedBoundsInRoot()
        val foreground = compose.onNodeWithText(group.title).getUnclippedBoundsInRoot()
        assertEquals("按钮宽度跟随条目位移", width, (bounds.right - bounds.left).value, 0.5f)
        val gap = if (direction == LayoutDirection.Rtl) foreground.left - bounds.right else bounds.left - foreground.right
        assertEquals("条目与按钮始终相隔 8dp", 8f, gap.value, 0.5f)
        val bitmap = action.captureToImage().asAndroidBitmap()
        val ink = iconBounds(bitmap)
        assertEquals("$description 图标整体水平居中", bitmap.width / 2f, ink.exactCenterX(), density)
        assertEquals("$description 图标笔画重心垂直居中", bitmap.height / 2f, measureIconInk(bitmap).centerY, 1.2f * density)
    }

    private fun iconBounds(bitmap: Bitmap): Rect {
        val background = bitmap.getPixel(bitmap.width / 2, bitmap.height / 8)
        val pixels = mutableListOf<Pair<Int, Int>>()
        val halfSize = (26 * density).toInt()
        for (y in bitmap.height / 2 - halfSize until bitmap.height / 2 + halfSize) {
            for (x in bitmap.width / 2 - halfSize until bitmap.width / 2 + halfSize) {
                if (bitmap.getPixel(x, y) != background) pixels += x to y
            }
        }
        assertTrue("图标必须可见", pixels.isNotEmpty())
        return Rect(pixels.minOf { it.first }, pixels.minOf { it.second },
            pixels.maxOf { it.first } + 1, pixels.maxOf { it.second } + 1)
    }

    private fun iconSize(bitmap: Bitmap): Float = iconBounds(bitmap).let { maxOf(it.width(), it.height()).toFloat() }

    private data class IconInk(val area: Double, val centerY: Float)

    /** 按前景覆盖率累计笔画面积与重心，排除开盖留白，并计入抗锯齿。 */
    private fun measureIconInk(bitmap: Bitmap): IconInk {
        val bounds = iconBounds(bitmap)
        val background = bitmap.getPixel(bitmap.width / 2, bitmap.height / 8)
        val pixels = (bounds.top until bounds.bottom).flatMap { y ->
            (bounds.left until bounds.right).map { x -> bitmap.getPixel(x, y) }
        }
        fun contrast(color: Int): Int {
            val r = Color.red(color) - Color.red(background)
            val g = Color.green(color) - Color.green(background)
            val b = Color.blue(color) - Color.blue(background)
            return r * r + g * g + b * b
        }
        val foreground = pixels.maxBy(::contrast)
        val redDelta = Color.red(foreground) - Color.red(background)
        val greenDelta = Color.green(foreground) - Color.green(background)
        val blueDelta = Color.blue(foreground) - Color.blue(background)
        val contrastSquared = contrast(foreground).toDouble()
        var area = 0.0
        var weightedY = 0.0
        pixels.forEachIndexed { index, color ->
            val coverage = ((Color.red(color) - Color.red(background)) * redDelta +
                (Color.green(color) - Color.green(background)) * greenDelta +
                (Color.blue(color) - Color.blue(background)) * blueDelta) / contrastSquared
            area += coverage
            weightedY += coverage * (bounds.top + index / bounds.width() + 0.5)
        }
        return IconInk(area, (weightedY / area).toFloat())
    }

    private fun assertSolidBackgroundCorners() {
        val bounds = filesButton.getUnclippedBoundsInRoot()
        compose.runOnIdle { referenceSize = DpSize(bounds.right - bounds.left, bounds.bottom - bounds.top) }
        val actual = filesButton.captureToImage().asAndroidBitmap()
        val expected = compose.onNodeWithTag("strong-reference").captureToImage().asAndroidBitmap()
        val radius = (20 * density).toInt()
        var maxDifference = 0
        for (y in 0 until radius) for (x in 0 until radius) {
            for (px in listOf(x, actual.width - 1 - x)) for (py in listOf(y, actual.height - 1 - y)) {
                val a = actual.getPixel(px, py)
                val e = expected.getPixel(px, py)
                maxDifference = maxOf(maxDifference,
                    kotlin.math.abs(Color.red(a) - Color.red(e)),
                    kotlin.math.abs(Color.green(a) - Color.green(e)),
                    kotlin.math.abs(Color.blue(a) - Color.blue(e)))
            }
        }
        assertTrue("四个圆角应与单层纯深红一致，不能露出浅红细边：最大色差 $maxDifference", maxDifference <= 2)
    }

    private fun capture(name: String) = saveImage(name, card.captureToImage().asAndroidBitmap())

    private fun iconTop(bitmap: Bitmap): Int = iconBounds(bitmap).top

    private fun saveImage(name: String, bitmap: Bitmap) {
        val output = File("../.tmp/downloads-swipe/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

}

/** 记录真实 Compose 宿主收到的触感调用，不替换 LocalView，保持手势与 View 互操作的运行环境。 */
@Implements(ViewGroup::class)
class SwipeHapticViewGroupShadow : ShadowViewGroup() {
    val feedback = mutableListOf<Int>()

    @Implementation
    override fun performHapticFeedback(feedbackConstant: Int): Boolean {
        feedback += feedbackConstant
        return true
    }
}
