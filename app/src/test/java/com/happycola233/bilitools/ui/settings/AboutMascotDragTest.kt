package com.happycola233.bilitools.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Velocity
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 关于页小电视被拖出去后松手，要从松手的位置平滑弹回，不能瞬间跳回原位。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class AboutMascotDragTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var controller: AboutMascotController

    private fun setContent() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            controller = rememberAboutMascotController(copy = null, haptics = rememberAppHaptics())
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AboutMascotIcon(controller = controller, contentDescription = "mascot")
            }
        }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun releasingSpringsBackFromWhereTheFingerLetGo() {
        setContent()
        // 拖动与抬手在同一批事件里送达：松手时最后几次拖动的位置更新还在排队
        compose.onNodeWithContentDescription("mascot").performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(40f, 0f)) }
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        val justReleased = controller.mascot.dragOffset.value.x
        assertTrue("松手后第一帧仍应停在外面，实际 $justReleased", justReleased > 120f)

        // 之后一路平滑回去：每帧位移都不应出现大跳变
        var previous = justReleased
        repeat(90) {
            compose.mainClock.advanceTimeByFrame()
            val current = controller.mascot.dragOffset.value.x
            assertTrue("单帧跳变过大：$previous → $current", abs(current - previous) < 60f)
            previous = current
        }
        assertTrue("最终应回到原位，实际 $previous", abs(previous) < 2f)
    }

    @Test
    fun grabbingAgainMidReturnDoesNotJump() {
        setContent()
        // 直接驱动控制器：拖出去、松手，弹回途中再次抓住
        compose.runOnIdle {
            controller.onDragStart()
            repeat(8) { controller.onDrag(Offset(40f, 0f), Velocity(2_000f, 0f)) }
            controller.onRelease(Velocity(2_000f, 0f), dragged = true, longPressed = false)
        }
        repeat(6) { compose.mainClock.advanceTimeByFrame() }
        val beforeGrab = controller.mascot.dragOffset.value.x
        compose.runOnIdle {
            controller.onDragStart()
            controller.onDrag(Offset(2f, 0f), Velocity(100f, 0f))
        }
        compose.mainClock.advanceTimeByFrame()
        val afterGrab = controller.mascot.dragOffset.value.x
        assertTrue("弹回途中应停在外面，实际 $beforeGrab", beforeGrab > 40f)
        assertTrue("重新抓住时不应跳动：$beforeGrab → $afterGrab", abs(afterGrab - beforeGrab) < 6f)
    }
}
