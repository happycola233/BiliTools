package com.happycola233.bilitools.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertTrue

/** 逐帧追踪的帧数，约 0.75 秒，足以覆盖显隐动画的弹簧与收起后移除节点。 */
private const val TRACE_FRAMES = 45

/** 显隐动画在这么多帧（约 0.4 秒）后应已到位（误差 2dp 内）；AnimatedVisibility 默认的弹簧约 0.35 秒收敛到这一误差。 */
private const val SETTLE_FRAME_LIMIT = 24

/**
 * 跳变帧的位移至少是前后两帧各自位移的这么多倍。弹簧动画的逐帧位移连续变化，相邻帧不会相差这么悬殊；
 * 只比较倍数而不要求前后两帧完全静止，因为节点移除前弹簧尾段仍可能有 1 像素左右的位移。
 */
private const val JUMP_STEP_RATIO = 4f

/**
 * 先让界面静止，再停止自动推进时钟，执行 [change] 后逐帧推进 [frames] 帧，返回变化前与其后每一帧的采样。
 *
 * [sample] 必须读取语义树：advanceTimeByFrame 本身不发送快照应用通知，只有 waitForIdle 或读取语义树时
 * 才会发送；不逐帧读取时，[change] 写入的状态可能一直不触发重组。
 */
internal fun <T> ComposeTestRule.traceFrames(
    change: () -> Unit,
    frames: Int = TRACE_FRAMES,
    sample: () -> T,
): List<T> {
    mainClock.autoAdvance = true
    waitForIdle()
    mainClock.autoAdvance = false
    val samples = mutableListOf(sample())
    change()
    repeat(frames) {
        mainClock.advanceTimeByFrame()
        samples += sample()
    }
    return samples
}

/**
 * 断言 [change] 触发显隐动画时，[position]（以像素计、每次调用都读取语义树）随动画一次平滑移动到位。
 *
 * 弹簧动画的位移逐帧连续变化；若某帧移动超过 1dp 而前后两帧几乎不动，说明布局在动画之外整段跳变，
 * 例如 spacedBy 的父级间距在展开第一帧整段出现、在收起结束移除节点时整段消失。
 * 到位后也不能停顿一阵再补一段位移。
 */
internal fun ComposeTestRule.assertMovesInOneMotion(
    scenario: String,
    change: () -> Unit,
    position: () -> Float,
) {
    val positions = traceFrames(change, sample = position)
    mainClock.autoAdvance = true
    waitForIdle()
    val restingPosition = position()
    assertTrue("$scenario 时位置没有变化：$positions", restingPosition != positions.first())

    val jumpThreshold = with(density) { 1.dp.toPx() }
    val steps = positions.zipWithNext { previous, next -> abs(next - previous) }
    val jumpFrames = steps.indices.filter { frame ->
        val step = steps[frame]
        step > jumpThreshold &&
            steps.getOrElse(frame - 1) { 0f } * JUMP_STEP_RATIO <= step &&
            steps.getOrElse(frame + 1) { 0f } * JUMP_STEP_RATIO <= step
    }
    assertTrue("$scenario 时在第 $jumpFrames 帧跳变：$positions", jumpFrames.isEmpty())

    val settleTolerance = with(density) { 2.dp.toPx() }
    val unsettledPositions = positions.drop(SETTLE_FRAME_LIMIT).filter { abs(it - restingPosition) > settleTolerance }
    assertTrue(
        "$scenario 时到位过慢或停顿后补跳：$positions → $restingPosition",
        unsettledPositions.isEmpty(),
    )
}
