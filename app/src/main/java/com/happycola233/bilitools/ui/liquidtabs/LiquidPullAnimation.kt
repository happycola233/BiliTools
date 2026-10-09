package com.happycola233.bilitools.ui.liquidtabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

// 以下手感参数逐帧测自 iOS 27 Apple Music 液态玻璃底栏的 60fps 录屏：
// 上拽时上移、横向收紧与纵向拉长始终同比例联动、面积基本守恒，两端内收的同时顶边比底边抬得多，
// 看起来就是两侧被往上拽、腰身收紧。录屏中拉到最远时横向 0.928、纵向 1.068、中心上移约 5pt，
// 这里取橡皮筋 75% 处（越过触摸阈值后再拉 3 × LiquidPullHalfTravel）对齐：横向收紧照搬，
// 上移与纵向拉长有意收到录屏的三分之一左右，底栏只被轻轻提起，不显得往上窜。
private val LiquidPullMaxLift = 2.5.dp
private const val LiquidPullMaxSqueeze = 0.1f
private const val LiquidPullMaxStretch = 0.03f
internal val LiquidPullHalfTravel = 28.dp

/**
 * 按住底栏向上拖动时的整体「果冻」跟随：玻璃底板、选中气泡与图标作为一个整体被往上拽，
 * 横向收紧、纵向拉长并上移；手指越远阻力越大，往下拖不跟随。松手后以单一弹簧弹回，
 * 略微越过静止位（稍宽、稍矮、稍下沉）再收敛——录屏拟合为 dampingRatio≈0.6、stiffness≈310，
 * 约 220ms 越过静止位 9%，约 360ms 收敛。
 */
internal class LiquidPullAnimation(
    private val animationScope: CoroutineScope,
    /** 越过触摸阈值后再上拽这么远，形变恰好达到上限的一半。 */
    private val halfPullTravel: Float,
) {

    private val releaseAnimationSpec = spring(0.6f, 310f, 0.001f)
    private val progressAnimation = Animatable(0f, 0.001f)

    // 本次手势首次竖向拖动时，当前形变折算成的拉动距离。回弹途中被重新拽住时由它接续，
    // 形态不跳变；按下但没有竖向拖动时回弹照常进行，不会被点击打断。
    private var gestureBaseTravel: Float? = null

    /** 0 为静止，上拽时趋近 1；回弹越过静止位时短暂为负。 */
    val progress: Float get() = progressAnimation.value

    fun press() {
        gestureBaseTravel = null
    }

    /** [travel] 为本次手势越过触摸阈值后的竖向位移，向上为正；低于按下点时底栏保持静止。 */
    fun pullTo(travel: Float) {
        val baseTravel = gestureBaseTravel
            ?: liquidPullTravelOf(progress.coerceAtLeast(0f), halfPullTravel)
                .also { gestureBaseTravel = it }
        val target = liquidPullProgressOf(
            travel = (baseTravel + travel).coerceAtLeast(0f),
            halfTravel = halfPullTravel,
        )
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            progressAnimation.snapTo(target)
        }
    }

    fun release() {
        gestureBaseTravel = null
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            progressAnimation.animateTo(0f, releaseAnimationSpec)
        }
    }
}

/** 橡皮筋：上拽距离 → 形变进度。[halfTravel] 处恰为一半，越往后越拽不动，且永远到不了 1。 */
internal fun liquidPullProgressOf(travel: Float, halfTravel: Float): Float =
    travel / (travel + halfTravel)

/** [liquidPullProgressOf] 的反函数。 */
internal fun liquidPullTravelOf(progress: Float, halfTravel: Float): Float =
    halfTravel * progress / (1f - progress)

/**
 * 把上拽形变叠加到图层上，须在该层自身的缩放写入之后调用。
 *
 * 只能写进 drawBackdrop 的 layerBlock：玻璃采样只会抵消这一层的缩放，放在外层会让背景跟着错位。
 * 各层都以自身中心为缩放轴，[centerOffsetX] 是本层中心相对底栏中心的水平距离，
 * 用来把收紧换算成以底栏中心为轴，让气泡和底板上的图标一起向中间收拢。
 */
internal fun GraphicsLayerScope.applyLiquidPull(pull: LiquidPullAnimation, centerOffsetX: Float = 0f) {
    val progress = pull.progress
    val squeeze = LiquidPullMaxSqueeze * progress
    scaleX *= 1f - squeeze
    scaleY *= 1f + LiquidPullMaxStretch * progress
    translationX -= centerOffsetX * squeeze
    translationY -= LiquidPullMaxLift.toPx() * progress
}
