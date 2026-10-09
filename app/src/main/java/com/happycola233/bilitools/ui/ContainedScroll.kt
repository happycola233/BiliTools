package com.happycola233.bilitools.ui

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Velocity

/**
 * 嵌在可拖动外层（底部面板、整页滚动）里的纵向滚动区域：滚到边界后剩余的位移与惯性不再交给外层，
 * 用户快速往回翻时不会顺势拖动外层；同时保留系统的边缘拉伸回弹。
 *
 * [connection] 与 [overscrollEffect] 共用同一份记账，必须用在同一个滚动容器上：
 * `Modifier.nestedScroll(connection)` 放在滚动修饰符之前（成为它的父级），
 * [overscrollEffect] 传给 `LazyColumn(overscrollEffect = …)` 或 `verticalScroll(state, overscrollEffect)`。
 */
internal class ContainedScroll(systemOverscroll: OverscrollEffect?) {
    private var blockedScroll = Offset.Zero
    private var blockedVelocity = Velocity.Zero

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val blocked = Offset(x = 0f, y = available.y)
            blockedScroll += blocked
            return blocked
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val blocked = Velocity(x = 0f, y = available.y)
            blockedVelocity += blocked
            return blocked
        }
    }

    /** 系统不提供回弹效果时为 null，此时边界处只是停住。 */
    val overscrollEffect: OverscrollEffect? = systemOverscroll?.let(::EdgeOverscroll)

    /**
     * [connection] 为了拦住外层而“消耗”的剩余量，对回弹来说其实没有被消耗；
     * 这里把它从已消耗量中扣掉，交还给系统回弹，边缘拉伸才会照常出现。
     */
    private inner class EdgeOverscroll(private val delegate: OverscrollEffect) : OverscrollEffect {
        override val isInProgress: Boolean
            get() = delegate.isInProgress

        override val node: DelegatableNode
            get() = delegate.node

        override fun applyToScroll(
            delta: Offset,
            source: NestedScrollSource,
            performScroll: (Offset) -> Offset,
        ): Offset = delegate.applyToScroll(delta, source) { scrollDelta ->
            blockedScroll = Offset.Zero
            performScroll(scrollDelta) - blockedScroll
        }

        override suspend fun applyToFling(
            velocity: Velocity,
            performFling: suspend (Velocity) -> Velocity,
        ) = delegate.applyToFling(velocity) { flingVelocity ->
            blockedVelocity = Velocity.Zero
            performFling(flingVelocity) - blockedVelocity
        }
    }
}

@Composable
internal fun rememberContainedScroll(): ContainedScroll {
    val systemOverscroll = rememberOverscrollEffect()
    return remember(systemOverscroll) { ContainedScroll(systemOverscroll) }
}
