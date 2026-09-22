package com.happycola233.bilitools.ui.liquidtabs

import android.app.Application
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DampedDragAnimationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var animation: DampedDragAnimation

    @Test fun pressApproachesTheMenuCenterWithinAFewFramesWithoutTeleporting() {
        showAnimation()
        compose.runOnIdle {
            animation.pressAt(2f)
            assertEquals(0f, animation.value, 0.0001f)
        }
        advance(48)
        compose.runOnIdle { assertTrue(animation.value in 0.05f..1.95f) }
        advance(96)
        compose.runOnIdle { assertTrue("前 144ms 内应完成九成位移，弹簧尾段保留自然收敛", animation.value >= 1.8f) }
        advance(256)
        compose.runOnIdle { assertEquals(2f, animation.value, 0.001f) }
    }

    @Test fun movesImmediatelyRetargetTheApproachAndAccumulateWithinOneFrame() {
        showAnimation()
        compose.runOnIdle {
            animation.pressAt(2f)
            repeat(20) { animation.dragBy(-0.02f) }
            assertEquals(1.6f, animation.targetValue, 0.0001f)
        }
        advance(48)
        compose.runOnIdle { assertTrue("首批 move 不能等待靠拢动画或长按超时", animation.value > 0.1f) }
        advance(400)
        compose.runOnIdle { assertEquals(1.6f, animation.value, 0.002f) }
    }

    @Test fun pressingCurrentCellPreservesItsCenterAndTinyMovesAreNotSwallowed() {
        showAnimation(initialValue = 1f)
        compose.runOnIdle { animation.pressAt(1f) }
        advance(160)
        compose.runOnIdle {
            assertEquals(1f, animation.value, 0.0001f)
            animation.dragBy(0.02f)
            assertEquals(1.02f, animation.targetValue, 0.0001f)
        }
        advance(64)
        compose.runOnIdle { assertTrue(animation.value > 1f) }
    }

    @Test fun draggingMidApproachContinuesFromTheVisiblePositionOnTheNextFrame() {
        showAnimation()
        compose.runOnIdle { animation.pressAt(2f) }
        advance(64)
        var positionAtTakeover = 0f
        compose.runOnIdle {
            positionAtTakeover = animation.value
            animation.dragBy(0.02f)
            assertEquals(positionAtTakeover, animation.value, 0.0001f)
        }
        advance(16)
        compose.runOnIdle { assertTrue(animation.value > positionAtTakeover) }
        advance(400)
        compose.runOnIdle { assertEquals(2f, animation.value, 0.001f) }
    }

    @Test fun regrabbingCancelsThePreviousDelayedShrinkAndIgnoresPageAnimation() {
        showAnimation()
        compose.runOnIdle {
            animation.pressAt(0f)
            animation.dragBy(1.6f)
            animation.finishGesture(2f)
        }
        advance(32)
        compose.runOnIdle {
            val before = animation.value
            animation.pressAt(1f)
            assertEquals(before, animation.value, 0.0001f)
            animation.animateToValue(0f)
            assertEquals(1f, animation.targetValue, 0.0001f)
        }
        advance(800)
        compose.runOnIdle {
            assertTrue(animation.isPressed)
            assertEquals(1f, animation.pressProgress, 0.001f)
            assertEquals(78f / 56f, animation.scaleX, 0.002f)
            assertEquals(78f / 56f, animation.scaleY, 0.002f)
            assertEquals(1f, animation.value, 0.001f)
        }
    }

    @Test fun pageEchoAfterReleaseDoesNotRestartThePressTransition() {
        showAnimation()
        compose.runOnIdle { animation.pressAt(2f) }
        advance(64)
        compose.runOnIdle {
            animation.finishGesture(2f)
            animation.animateToValue(2f)
        }
        advance(80)
        compose.runOnIdle {
            assertTrue(animation.value >= 1.8f)
            assertTrue("页面回写不能提前结束折射展开", animation.pressProgress > 0.9f)
        }
        advance(800)
        compose.runOnIdle {
            assertEquals(2f, animation.value, 0.001f)
            assertFalse(animation.isPressed)
            assertEquals(1f, animation.scaleX, 0.001f)
            assertEquals(0f, animation.pressProgress, 0.001f)
        }
    }

    @Test fun dragRetainsInertiaAtTheEdgeAndReversesWithoutAnOverscrollDeadZone() {
        showAnimation()
        compose.runOnIdle { animation.pressAt(0f) }
        advance(500)
        repeat(16) {
            compose.runOnIdle { animation.dragBy(0.15f) }
            advance(16)
        }
        var edgeVelocity = 0f
        compose.runOnIdle {
            assertEquals(2f, animation.targetValue, 0.0001f)
            assertTrue("位置仍应带有原来的阻尼，不能替换为逐帧 snapTo", animation.value < 1.999f)
            edgeVelocity = animation.velocity
            assertTrue("碰到边缘时仍需保留速度形变", edgeVelocity > 0.05f)
        }
        repeat(20) {
            compose.runOnIdle { animation.dragBy(0.15f) }
            advance(16)
        }
        compose.runOnIdle {
            assertTrue(abs(animation.velocity) < edgeVelocity)
            animation.dragBy(-0.02f)
            assertEquals(1.98f, animation.targetValue, 0.0001f)
        }
        advance(64)
        compose.runOnIdle { assertTrue(animation.value < 1.999f) }
    }

    @Test fun pressedAxesKeepTheirDifferentUnderdampedJellyResponse() {
        showAnimation()
        compose.runOnIdle { animation.pressAt(0f) }
        advance(256)
        compose.runOnIdle {
            assertTrue(animation.scaleX > 78f / 56f)
            assertTrue(animation.scaleY > 78f / 56f)
            assertTrue(animation.scaleX > animation.scaleY)
        }
    }

    @Test fun shortAdjacentTapKeepsTheOriginalExpansionAndReleaseEnvelope() =
        verifyTapFeedback(targetIndex = 1, holdMillis = 32)

    @Test fun shortDistantTapKeepsTheOriginalExpansionAndReleaseEnvelope() =
        verifyTapFeedback(targetIndex = 2, holdMillis = 32)

    @Test fun slowerTapKeepsTheSameExpansionPeakInsteadOfCuttingItShortOnRelease() =
        verifyTapFeedback(targetIndex = 1, holdMillis = 112)

    private fun verifyTapFeedback(targetIndex: Int, holdMillis: Long) {
        showAnimation()
        compose.runOnIdle { animation.pressAt(targetIndex.toFloat()) }
        // 1317782 的完整点击周期，按动画起始帧对齐：现在按下即响应，旧版从抬手才开始。
        // 同时锁定放大、折射及退场曲线，持续拖动的基准无法发现短点击被提前截断的问题。
        val trajectory = javaClass.getResourceAsStream("/liquidtabs/legacy-tap-$targetIndex.csv")!!
            .bufferedReader().use { it.readLines().drop(1) }
        trajectory.forEach { row ->
            val (elapsedMillis, scaleX, scaleY, pressProgress) = row.split(',').map(String::toFloat)
            advance(16)
            compose.runOnIdle {
                if (elapsedMillis.toLong() == holdMillis) {
                    animation.finishGesture(targetIndex.toFloat())
                    animation.animateToValue(targetIndex.toFloat())
                }
                assertEquals("${elapsedMillis}ms scaleX", scaleX, animation.scaleX, 0.0001f)
                assertEquals("${elapsedMillis}ms scaleY", scaleY, animation.scaleY, 0.0001f)
                assertEquals("${elapsedMillis}ms pressProgress", pressProgress, animation.pressProgress, 0.0001f)
            }
        }
    }

    @Test fun dragAndEdgeReboundMatchTheOriginalFrameByFrameTrajectory() {
        showAnimation()
        compose.runOnIdle { animation.pressAt(0f) }
        advance(512)
        // 基准来自 1317782 的原始实现：按住 512ms 后，以 16ms 一帧慢拖、快拖、顶住边缘、
        // 反向拖动和停留。锁定位置及速度曲线，防止调度顺序或边缘去重悄悄改变 Q 弹手感。
        val trajectory = javaClass.getResourceAsStream("/liquidtabs/legacy-drag-trajectory.csv")!!
            .bufferedReader().use { it.readLines().drop(1) }
        trajectory.forEachIndexed { frame, row ->
            val (delta, position, velocity) = row.split(',').map(String::toFloat)
            compose.runOnIdle { animation.dragBy(delta) }
            advance(16)
            compose.runOnIdle {
                assertEquals("frame $frame position", position, animation.value, 0.0001f)
                assertEquals("frame $frame velocity", velocity, animation.velocity, 0.005f)
            }
        }
    }

    @Test fun interruptedExternalSelectionSettlesOnlyAtTheLatestPage() {
        showAnimation()
        compose.runOnIdle { animation.animateToValue(2f) }
        advance(64)
        compose.runOnIdle { animation.animateToValue(0f) }
        advance(32)
        compose.runOnIdle { animation.animateToValue(1f) }
        advance(1_000)
        compose.runOnIdle {
            assertEquals(1f, animation.value, 0.001f)
            assertEquals(1f, animation.scaleX, 0.001f)
            assertEquals(0f, animation.pressProgress, 0.001f)
        }
    }

    private fun showAnimation(initialValue: Float = 0f) {
        compose.setContent {
            animation = DampedDragAnimation(
                rememberCoroutineScope(), initialValue, 0f..2f, 0.001f, 1f, 78f / 56f,
                // Compose 帧时钟与 Robolectric 系统时钟独立，速度必须按同一条帧时间线采样。
                uptimeMillis = { compose.mainClock.currentTime },
            )
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }
}
