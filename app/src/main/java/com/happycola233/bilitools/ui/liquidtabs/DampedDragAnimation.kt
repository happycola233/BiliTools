// 移植自 Kyant0/AndroidLiquidGlass 2.0.0 catalog（Apache-2.0）：
// app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt
// 改动：触摸由底板统一分发；位置目标同步更新，按压可中断旧的收尾任务。
package com.happycola233.bilitools.ui.liquidtabs

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    private val uptimeMillis: () -> Long = SystemClock::uptimeMillis,
) {

    // 靠拢、拖动和归位共用原版位置弹簧；缩回时机由位置收敛决定，必须与按压弹簧协同。
    // 单独把靠拢改成短 tween 会提前触发缩回，截断放大峰值和折射展开，削弱点击的 Q 弹。
    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec =
        spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec =
        spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec =
        spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.7f, 250f, 0.001f)

    private val valueAnimation =
        Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation =
        Animatable(0f, 5f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private var releaseJob: Job? = null
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    // 不能用异步动画的 targetValue 累加输入，否则同一帧内的多次 move 会丢失位移。
    var targetValue: Float = initialValue
        private set
    var isPressed: Boolean = false
        private set
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    fun pressAt(value: Float) {
        isPressed = true
        press()
        animatePosition(value)
    }

    fun dragBy(delta: Float) {
        val target = (targetValue + delta).coerceIn(valueRange)
        // 不等待按下时的靠拢动画；首个 move 就接管，并沿用原来的位置弹簧和速度采样。
        // 到边缘后也接收每次增量，保留原来的减速采样及速度弹簧回摆，不能按相同目标去重。
        animatePosition(target, trackVelocity = true)
    }

    fun finishGesture(value: Float) {
        isPressed = false
        settleTo(value)
    }

    /** 页面状态只在没有手指持有气泡时驱动位置；松手后的同目标回写不再重复启动动画。 */
    fun animateToValue(value: Float) {
        if (isPressed || value.coerceIn(valueRange) == targetValue) return
        press()
        settleTo(value)
    }

    private fun press() {
        releaseJob?.cancel()
        velocityTracker.resetTracking()
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
        }
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec)
        }
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec)
        }
    }

    private fun settleTo(value: Float) {
        val target = value.coerceIn(valueRange)
        if (target != targetValue) {
            animatePosition(target)
        }
        animationScope.launch {
            velocityAnimation.animateTo(0f, velocityAnimationSpec)
        }
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            withFrameNanos { }
            val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
            snapshotFlow { abs(valueAnimation.value - target) < threshold }.first { it }
            animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
            }
            animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec)
            }
            animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec)
            }
        }
    }

    private fun animatePosition(
        value: Float,
        trackVelocity: Boolean = false,
    ) {
        targetValue = value.coerceIn(valueRange)
        // 立即启动，交由同一个 Animatable 接续速度并互斥旧动画，避免排队和显式 stop 清零速度。
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.animateTo(targetValue, valueAnimationSpec) {
                if (trackVelocity && isPressed) updateVelocity()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            uptimeMillis(),
            Offset(value, 0f),
        )
        val targetVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        // 沿用原来的帧后调度。若在位置帧回调里立即重启，会取消本帧尚未推进的速度动画，
        // 连续拖动时速度形变会一直停在零，尤其会丢失碰到边缘时的果冻惯性。
        animationScope.launch {
            velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec)
        }
    }
}
