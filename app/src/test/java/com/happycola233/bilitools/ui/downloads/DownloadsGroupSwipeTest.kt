package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
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
    private val deleteButton get() = compose.onNodeWithContentDescription("删除")
    private val deleteIcon get() = compose.onNodeWithContentDescription("删除", useUnmergedTree = true)

    @Test fun lightSwipeKeepsGapAndOnlyVibratesAcrossDeleteThreshold() = verifySwipe(AppThemeMode.Light)
    @Test fun darkSwipeKeepsGapAndOnlyVibratesAcrossDeleteThreshold() = verifySwipe(AppThemeMode.Dark)

    private fun verifySwipe(mode: AppThemeMode) {
        compose.setContent {
            hapticViewShadow = Shadow.extract(LocalView.current)
            density = LocalDensity.current.density
            touchSlopPx = LocalViewConfiguration.current.touchSlop
            BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Periwinkle)) {
                Column(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor)) {
                    DownloadsGroupCard(
                        group = group, selectionMode = false, selected = false, expanded = false,
                        swiped = swiped, anyGroupSwiped = swiped,
                        onSwipedGroupChange = { swiped = it == group.id },
                        onToggleSelection = {}, onToggleExpanded = {}, onDelete = { deleteRequests++ },
                        onPauseGroup = {}, onResumeGroup = {}, onReparse = {}, onShowDetails = {},
                        onTaskPauseResume = {}, onTaskRetry = {}, onTaskDelete = {}, onTaskClick = { _, _ -> },
                        modifier = Modifier.testTag("swipe-card"),
                    )
                }
            }
        }

        // 停靠距离的一半不再发出反馈，小幅拖动后仍能正常收回。
        val closedRight = compose.onNodeWithText(group.title).getUnclippedBoundsInRoot().right.value
        startDragLeft(30f)
        card.performTouchInput { up() }
        compose.runOnIdle { assertFalse(swiped); assertTrue(feedback.isEmpty()); assertEquals(0, deleteRequests) }
        assertEquals(closedRight, compose.onNodeWithText(group.title).getUnclippedBoundsInRoot().right.value, 0.5f)

        startDragLeft(88f)
        card.performTouchInput { up() }
        compose.runOnIdle { assertTrue(swiped); assertTrue(feedback.isEmpty()); assertEquals(0, deleteRequests) }
        val restingButton = deleteButton.getUnclippedBoundsInRoot()
        val restingIcon = deleteIcon.getUnclippedBoundsInRoot()
        val closedIconImage = deleteIcon.captureToImage().asAndroidBitmap()
        assertEquals(80f, (restingButton.right - restingButton.left).value, 0.5f)
        assertEquals(88f, closedRight - compose.onNodeWithText(group.title).getUnclippedBoundsInRoot().right.value, 0.5f)
        capture("resting-${mode.name}")

        // 触发前保持闭合；越过开盖触发点后手指停住，图标也会自行完成带回弹的动作。
        startDragLeft(10f)
        assertTrue("尚未到开盖触发点时保持闭合", closedIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        compose.mainClock.autoAdvance = false
        moveHorizontally(-4f)
        compose.mainClock.advanceTimeBy(48)
        val openingIconImage = deleteIcon.captureToImage().asAndroidBitmap()
        val openingFrames = (1..36).map { frame ->
            compose.mainClock.advanceTimeBy(16)
            val bitmap = deleteButton.captureToImage().asAndroidBitmap()
            saveImage("spring-${mode.name}-${frame.toString().padStart(2, '0')}", bitmap)
            bitmap
        }
        compose.mainClock.autoAdvance = true
        assertFeedback()
        val openIconImage = deleteIcon.captureToImage().asAndroidBitmap()
        assertFalse("保持手指位置不动也会继续完成动画", openingIconImage.sameAs(openIconImage))
        assertFalse("尚未达到删除阈值就已完成开盖", closedIconImage.sameAs(openIconImage))
        val settledButtonImage = deleteButton.captureToImage().asAndroidBitmap()
        assertTrue("桶身轻微放大过冲后回落", openingFrames.maxOf(::bodyInkWeight) > bodyInkWeight(settledButtonImage) * 1.01)
        assertTrue("桶盖弹开过冲后回落", openingFrames.minOf(::lidInkCenterY) < lidInkCenterY(settledButtonImage) - 0.2 * density)
        capture("triggered-open-${mode.name}")

        // 回退缓冲区内不重新播放；退回关闭点后合盖，再超过打开点可以重新触发。
        moveHorizontally(6f) // 96dp，位于 92..100dp 的缓冲区。
        assertTrue("轻微回拖不反复开合", openIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        moveHorizontally(6f) // 90dp，已退回关闭点。
        assertTrue("退回关闭点时自然合盖", closedIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        moveHorizontally(-6f) // 回到 96dp，仍保持闭合。
        assertTrue("缓冲区也不会提前重新开盖", closedIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        moveHorizontally(-6f) // 再次到 102dp。
        assertTrue("再次越过打开点时可以重新开盖", openIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        assertFeedback()
        moveHorizontally(-38f) // 第二阈值为 140dp。
        assertTrue("继续拖动不会改变或重播已完成的图标动画", openIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        capture("fully-open-${mode.name}")

        // 从第二阈值继续左滑，盖子保持全开且仍可删除，不逐帧重复震动。
        moveHorizontally(-20f)
        assertFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
        assertExtendedButton(restingButton, restingIcon, 152f)
        moveHorizontally(-40f)
        assertFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
        assertExtendedButton(restingButton, restingIcon, 192f)
        assertTrue("超过第二阈值后保持全开", openIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        compose.runOnIdle { assertEquals("达到阈值只就绪，松手才请求删除", 0, deleteRequests) }
        capture("extended-${mode.name}")

        // 向右退回第二阈值内会取消，且同一手势可以再次进入和退出。
        moveHorizontally(70f)
        assertFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
        assertExtendedButton(restingButton, restingIcon, 122f)
        moveHorizontally(16f)
        assertTrue("退回删除阈值内仍保持视觉预备状态", openIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        moveHorizontally(-46f)
        moveHorizontally(30f)
        assertFeedback(
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE,
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE,
        )
        card.performTouchInput { up() }
        compose.runOnIdle { assertTrue(swiped); assertEquals(0, deleteRequests) }
        assertEquals(restingButton, deleteButton.getUnclippedBoundsInRoot())
        assertEquals(restingIcon, deleteIcon.getUnclippedBoundsInRoot())
        assertTrue("回到第一停靠点后合盖并恢复原始尺寸与位置", closedIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))

        // 松手触发删除确认后，条目回到停靠点，图标独立回中，整个过程保持开盖和放大。
        feedback.clear()
        startDragLeft(112f)
        moveHorizontally(-60f)
        val beforeReleaseImage = deleteButton.captureToImage().asAndroidBitmap()
        var previousIconOffset = bodyOffsetFromActionCenter(beforeReleaseImage)
        assertEquals("拖动时图标左移 2dp", -2.0 * density, previousIconOffset, 0.15 * density)
        compose.mainClock.autoAdvance = false
        card.performTouchInput { up() }
        repeat(16) {
            compose.mainClock.advanceTimeBy(16)
            val frame = deleteButton.captureToImage().asAndroidBitmap()
            assertEquals("回中过程不合盖", lidInkCenterY(beforeReleaseImage), lidInkCenterY(frame), 0.15 * density)
            assertEquals("回中过程保持放大", bodyInkWeight(beforeReleaseImage), bodyInkWeight(frame), bodyInkWeight(beforeReleaseImage) * 0.03)
            val iconOffset = bodyOffsetFromActionCenter(frame)
            assertTrue("图标平稳向右回位", iconOffset >= previousIconOffset - 0.15 * density && iconOffset <= 1.15 * density)
            previousIconOffset = iconOffset
        }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(1, deleteRequests); assertTrue(swiped) }
        assertFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
        assertEquals(restingButton, deleteButton.getUnclippedBoundsInRoot())
        val centeredOpenIconImage = deleteIcon.captureToImage().asAndroidBitmap()
        val centeredButtonImage = deleteButton.captureToImage().asAndroidBitmap()
        assertEquals("等待删除确认时向右视觉补偿 1dp", 1.0 * density, bodyOffsetFromActionCenter(centeredButtonImage), 0.15 * density)
        assertEquals("水平补偿保持开盖和垂直位置", lidInkCenterY(beforeReleaseImage), lidInkCenterY(centeredButtonImage), 0.15 * density)
        capture("delete-confirmation-${mode.name}")

        // 路由关闭删除弹窗会清除侧滑条目；再次打开操作区时不能残留开盖状态。
        compose.runOnIdle { swiped = false }
        compose.waitForIdle()
        startDragLeft(88f)
        card.performTouchInput { up() }
        assertTrue("取消删除后再次侧滑时图标已复位", closedIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        assertEquals(restingButton, deleteButton.getUnclippedBoundsInRoot())

        // 系统取消手势也不能请求删除，并恢复原来的停靠状态。
        feedback.clear()
        startDragLeft(72f)
        card.performTouchInput { cancel() }
        compose.runOnIdle { assertEquals(1, deleteRequests); assertTrue(swiped) }
        assertEquals(restingButton, deleteButton.getUnclippedBoundsInRoot())
        assertTrue("取消手势后恢复图标", closedIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))

        // 动画尚未结束就快速回拖、重开、松手，也必须从当前画面自然接续并收拢。
        feedback.clear()
        compose.mainClock.autoAdvance = false
        startDragLeft(14f)
        compose.mainClock.advanceTimeBy(80)
        moveHorizontally(12f)
        compose.mainClock.advanceTimeBy(32)
        moveHorizontally(-12f)
        compose.mainClock.advanceTimeBy(48)
        card.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        assertTrue("快速反向和松手后恢复原始图标", closedIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        assertEquals(restingButton, deleteButton.getUnclippedBoundsInRoot())
        assertFeedback()
        compose.runOnIdle { assertEquals(1, deleteRequests) }

        // 从完全收起状态一口气滑过删除阈值，开盖仍能在松手后继续完成并保持。
        compose.runOnIdle { swiped = false }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        startDragLeft(160f)
        card.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(2, deleteRequests); assertTrue(swiped) }
        assertTrue("快速滑动触发删除后继续完成开盖并居中", centeredOpenIconImage.sameAs(deleteIcon.captureToImage().asAndroidBitmap()))
        assertEquals(restingButton, deleteButton.getUnclippedBoundsInRoot())
    }

    private fun startDragLeft(distanceDp: Float) {
        card.performTouchInput {
            down(center)
            moveBy(Offset(-(touchSlopPx + distanceDp * density), 0f), delayMillis = 32)
        }
        compose.waitForIdle()
    }

    private fun moveHorizontally(distanceDp: Float) {
        card.performTouchInput { moveBy(Offset(distanceDp * density, 0f), delayMillis = 32) }
        compose.waitForIdle()
    }

    private fun assertFeedback(vararg expected: Int) {
        compose.runOnIdle { assertEquals(expected.toList(), feedback) }
    }

    private fun assertExtendedButton(restingButton: DpRect, restingIcon: DpRect, widthDp: Float) {
        val button = deleteButton.getUnclippedBoundsInRoot()
        assertEquals("按钮宽度跟随条目位移", widthDp, (button.right - button.left).value, 0.5f)
        assertEquals("按钮右边缘保持固定", restingButton.right, button.right)
        assertEquals("图标的布局占位保持固定", restingIcon, deleteIcon.getUnclippedBoundsInRoot())
        val foreground = compose.onNodeWithText(group.title).getUnclippedBoundsInRoot()
        assertEquals("条目与删除背景之间始终留 8dp", 8f, button.left.value - foreground.right.value, 0.5f)
    }

    private fun capture(name: String) {
        saveImage(name, card.captureToImage().asAndroidBitmap())
    }

    private fun saveImage(name: String, bitmap: Bitmap) {
        val output = File("../.tmp/downloads-swipe/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    // 从真实渲染的按钮中央取样：粉色背景红通道为 255，轮廓更暗，差值同时计入抗锯齿像素。
    // 桶盖与桶身分区测量，验证各自的过冲，而不是仅检查任意两帧是否不同。
    private data class IconInk(val weight: Double, val weightedX: Double, val weightedY: Double)

    private fun bodyInkWeight(bitmap: Bitmap): Double = iconInk(bitmap, lid = false).weight

    private fun bodyOffsetFromActionCenter(bitmap: Bitmap): Double {
        val ink = iconInk(bitmap, lid = false)
        return ink.weightedX / ink.weight - (bitmap.width - 40f * density)
    }

    private fun lidInkCenterY(bitmap: Bitmap): Double {
        val ink = iconInk(bitmap, lid = true)
        return ink.weightedY / ink.weight
    }

    private fun iconInk(bitmap: Bitmap, lid: Boolean): IconInk {
        val centerX = bitmap.width - 40f * density
        val centerY = bitmap.height / 2f
        val top = if (lid) centerY - 32f * density else centerY
        val bottom = if (lid) centerY - 8f * density else centerY + 18f * density
        var weight = 0.0
        var weightedX = 0.0
        var weightedY = 0.0
        for (y in top.toInt() until bottom.toInt()) {
            for (x in (centerX - 24f * density).toInt() until (centerX + 24f * density).toInt()) {
                val ink = 255 - android.graphics.Color.red(bitmap.getPixel(x, y))
                weight += ink
                weightedX += ink * (x + 0.5)
                weightedY += ink * y
            }
        }
        return IconInk(weight, weightedX, weightedY)
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
