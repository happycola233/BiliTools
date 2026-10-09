package com.happycola233.bilitools.ui.liquidtabs

import android.app.Application
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiquidPullAnimationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var animation: LiquidPullAnimation

    @Test fun rubberBandHalvesAtHalfTravelGrowsHarderAndNeverReachesTheTop() {
        assertEquals(0f, liquidPullProgressOf(0f, HalfTravel), 0f)
        assertEquals(0.5f, liquidPullProgressOf(HalfTravel, HalfTravel), 0.0001f)
        assertEquals(0.75f, liquidPullProgressOf(HalfTravel * 3, HalfTravel), 0.0001f)
        assertTrue(liquidPullProgressOf(HalfTravel * 1_000, HalfTravel) < 1f)
        listOf(0f, 0.2f, 0.5f, 0.9f).forEach { progress ->
            val travel = liquidPullTravelOf(progress, HalfTravel)
            assertEquals(progress, liquidPullProgressOf(travel, HalfTravel), 0.0001f)
        }
    }

    @Test fun pullFollowsTheFingerWithoutLagAndIgnoresMovesBelowThePressPoint() {
        showAnimation()
        compose.runOnIdle {
            animation.press()
            animation.pullTo(HalfTravel)
            assertEquals(0.5f, animation.progress, 0.0001f)
            animation.pullTo(-HalfTravel)
            assertEquals("往下拖不跟随", 0f, animation.progress, 0f)
        }
    }

    @Test fun releaseSpringsBackWithTheMeasuredOvershootAndSettles() {
        showAnimation()
        compose.runOnIdle {
            animation.press()
            animation.pullTo(HalfTravel * 3)
            animation.release()
        }
        // 录屏拟合：约 220ms 越过静止位约 9.5%，此前单调回落，约 360ms 后已基本归位。
        var previous = 0.75f
        var lowest = 0f
        var lowestAt = 0L
        for (elapsed in 16L..640L step 16L) {
            advance(16)
            compose.runOnIdle {
                val progress = animation.progress
                if (progress < lowest) {
                    lowest = progress
                    lowestAt = elapsed
                }
                // 弹簧首帧停在起点，之后一路回落到越过静止位为止
                if (elapsed < 160L) assertTrue("回到静止位之前不应回摆", progress <= previous)
                previous = progress
            }
        }
        assertEquals(-0.75f * 0.095f, lowest, 0.006f)
        assertTrue("最低点出现在 ${lowestAt}ms", lowestAt in 192L..256L)
        compose.runOnIdle { assertEquals(0f, animation.progress, 0.003f) }
    }

    @Test fun tappingDuringReboundDoesNotFreezeIt() {
        showAnimation()
        compose.runOnIdle {
            animation.press()
            animation.pullTo(HalfTravel * 3)
            animation.release()
        }
        advance(48)
        var beforeTap = 0f
        compose.runOnIdle {
            beforeTap = animation.progress
            animation.press()
        }
        advance(32)
        compose.runOnIdle {
            assertTrue(animation.progress < beforeTap)
            animation.release()
        }
        advance(800)
        compose.runOnIdle { assertEquals(0f, animation.progress, 0.001f) }
    }

    @Test fun regrabbingMidReboundContinuesFromTheVisibleShape() {
        showAnimation()
        compose.runOnIdle {
            animation.press()
            animation.pullTo(HalfTravel * 3)
            animation.release()
        }
        advance(64)
        var visible = 0f
        compose.runOnIdle {
            visible = animation.progress
            assertTrue(visible in 0.1f..0.7f)
            animation.press()
            animation.pullTo(0.5f)
        }
        // 接管要先取消进行中的回弹，等主线程空闲后再看（不推进帧时钟）
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("重新拽住时形态不能跳变", visible, animation.progress, 0.01f)
            animation.pullTo(HalfTravel)
            assertTrue(animation.progress > visible)
            animation.pullTo(-HalfTravel * 10)
            assertEquals(0f, animation.progress, 0f)
        }
    }

    private fun showAnimation() {
        compose.setContent {
            animation = LiquidPullAnimation(rememberCoroutineScope(), HalfTravel)
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private companion object {
        const val HalfTravel = 28f
    }
}
