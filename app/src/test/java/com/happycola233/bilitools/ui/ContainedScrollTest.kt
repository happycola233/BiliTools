package com.happycola233.bilitools.ui

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContainedScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lazyListEdgePullStretchesWithoutMovingParent() = verifyEdgePull { contained ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(contained.connection)
                .testTag(LIST_TAG),
            overscrollEffect = contained.overscrollEffect,
        ) {
            items(30) { index -> Text("第 $index 行", Modifier.height(40.dp)) }
        }
    }

    @Test fun scrollColumnEdgePullStretchesWithoutMovingParent() = verifyEdgePull { contained ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(contained.connection)
                .verticalScroll(rememberScrollState(), contained.overscrollEffect)
                .testTag(LIST_TAG),
        ) {
            repeat(30) { index -> Text("第 $index 行", Modifier.height(40.dp)) }
        }
    }

    private fun verifyEdgePull(content: @Composable (ContainedScroll) -> Unit) {
        val parent = RecordingParent()
        val overscroll = RecordingOverscroll()
        val contained = ContainedScroll(overscroll)
        compose.setContent {
            Box(Modifier.size(200.dp).nestedScroll(parent)) { content(contained) }
        }
        // 列表已在顶部，继续下拉并快速松手：位移与惯性都应留给回弹，外层一点也拿不到。
        compose.onNodeWithTag(LIST_TAG).performTouchInput { swipeDown(durationMillis = 80) }
        compose.runOnIdle {
            assertEquals("外层收不到剩余位移", 0f, parent.scroll.y)
            assertEquals("外层收不到剩余惯性", 0f, parent.velocity.y)
            assertTrue("剩余位移交给回弹", overscroll.scroll.y > 0f)
            assertTrue("剩余惯性交给回弹", overscroll.velocity.y > 0f)
        }
    }

    private class RecordingParent : NestedScrollConnection {
        var scroll = Offset.Zero
        var velocity = Velocity.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            scroll += available
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            velocity += available
            return Velocity.Zero
        }
    }

    /**
     * 记录交到回弹手里的剩余量。与系统回弹一致：拖动时吃掉剩余位移形成拉伸；
     * 惯性滑动撞到边界时不吃逐帧位移，让惯性停下，再在 applyToFling 里吸收剩余速度。
     */
    private class RecordingOverscroll : OverscrollEffect {
        var scroll = Offset.Zero
        var velocity = Velocity.Zero
        override val isInProgress = false
        override val node: DelegatableNode = object : Modifier.Node() {}

        override fun applyToScroll(
            delta: Offset,
            source: NestedScrollSource,
            performScroll: (Offset) -> Offset,
        ): Offset {
            val consumed = performScroll(delta)
            if (source != NestedScrollSource.UserInput) return consumed
            scroll += delta - consumed
            return delta
        }

        override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
            this.velocity += velocity - performFling(velocity)
        }
    }

    private companion object {
        const val LIST_TAG = "contained_list"
    }
}
