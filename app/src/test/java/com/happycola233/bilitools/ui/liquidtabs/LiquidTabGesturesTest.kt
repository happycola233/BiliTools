package com.happycola233.bilitools.ui.liquidtabs

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
class LiquidTabGesturesTest {
    @get:Rule val compose = createComposeRule()
    private val presses = mutableListOf<Offset>()
    private val moves = mutableListOf<Float>()
    private val releases = mutableListOf<Boolean>()
    private var cancellations = 0

    @Test fun pressAndTwoPixelMoveAreDeliveredBeforeAnyTimeoutOrSlop() {
        showInput()
        input().performTouchInput { down(Offset(198f, 32f)) }
        assertEquals(listOf(Offset(198f, 32f)), presses)
        input().performTouchInput { moveTo(Offset(200f, 32f)) }
        assertEquals(listOf(2f), moves)
        input().performTouchInput { up() }
        assertEquals(listOf(false), releases)
    }

    @Test fun aFastDragAndALongHoldBothUseTheSameImmediateInputPath() {
        showInput()
        input().performTouchInput {
            down(Offset(150f, 32f))
            moveTo(Offset(230f, 32f), delayMillis = 16)
            up()
            down(Offset(50f, 32f))
            advanceEventTime(700)
            moveTo(Offset(100f, 32f))
            up()
        }
        assertEquals(listOf(80f, 50f), moves)
        assertEquals(listOf(true, true), releases)
    }

    @Test fun systemCancellationDoesNotSubmitASelection() {
        showInput()
        input().performTouchInput {
            down(Offset(150f, 32f))
            moveTo(Offset(250f, 32f))
            cancel()
        }
        assertTrue(releases.isEmpty())
        assertEquals(1, cancellations)
    }

    @Test fun leavingVerticallyCancelsATapButHorizontalDraggingCanLeaveTheContainer() {
        showInput()
        input().performTouchInput {
            down(Offset(150f, 32f))
            moveTo(Offset(150f, -40f))
            up()
            down(Offset(150f, 32f))
            moveTo(Offset(400f, -40f))
            up()
        }
        assertEquals(1, cancellations)
        assertEquals(listOf(true), releases)
    }

    @Test fun handingOverToAnotherFingerDoesNotAddTheDistanceBetweenFingers() {
        showInput()
        input().performTouchInput {
            down(0, Offset(50f, 32f))
            down(1, Offset(250f, 32f))
            up(0)
            moveTo(1, Offset(252f, 32f))
            up(1)
        }
        assertEquals(listOf(2f), moves)
        assertEquals(listOf(false), releases)
    }

    @Test fun menuHitTestingIncludesPaddingAndMirrorsInRtl() {
        val samples = mapOf(0f to 0, 103f to 0, 104f to 1, 203f to 1, 204f to 2, 307f to 2)
        samples.forEach { (x, expected) ->
            assertEquals(expected, liquidTabIndexAt(x, 100f, 4f, 3, true))
            assertEquals(2 - expected, liquidTabIndexAt(x, 100f, 4f, 3, false))
        }
    }

    private fun showInput() {
        compose.setContent {
            Box(
                Modifier.size(308.dp, 64.dp).testTag("input").pointerInput(Unit) {
                    detectLiquidTabGestures(
                        onPress = { presses += it },
                        onDrag = { moves += it },
                        onRelease = { releases += it },
                        onCancel = { cancellations++ },
                    )
                },
            )
        }
    }

    private fun input() = compose.onNodeWithTag("input")
}
