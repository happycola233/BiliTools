package com.happycola233.bilitools.ui.eggs

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.happycola233.bilitools.ui.BiliTvLaunchMotion
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 小电视的眼睛。平时是两颗竖着的胶囊，其余是各种情绪下的样子。 */
enum class MascotEyes {
    Open,

    /** ^ ^ */
    Happy,

    /** > < */
    Squint,

    /** × × */
    Dizzy,

    /** 睁圆的 o o */
    Surprised,

    /** 半睁的困倦眼，深夜用 */
    Sleepy,
}

/**
 * 小电视的全部可动部件。所有数值都在绘制阶段读取，动起来时只重绘、不重组。
 *
 * 位移类数值以小电视画布边长为单位：`lift = -1f` 表示向上跳起一个画布高度，
 * 同一套动作因此可以直接用在大小不同的小电视上。
 */
@Stable
class BiliTvMascotState(
    /** 天线晃动、视线回正这类附带动作在这里自行播放，主动作不必等它们停稳 */
    private val effectScope: CoroutineScope,
) {
    /** 离地高度，负值向上；只移动身体，影子留在地面上随高度变淡变小 */
    internal val lift = Animatable(0f)
    internal val scaleX = Animatable(1f)
    internal val scaleY = Animatable(1f)

    /** 绕脚底中心旋转的角度 */
    internal val tilt = Animatable(0f)

    /** 晕倒程度，1 表示整台侧躺在地上 */
    internal val fallen = Animatable(0f)

    /** 拖拽产生的整体位移（像素），影子跟着一起走 */
    internal val dragOffset = Animatable(Offset.Zero, Offset.VectorConverter)

    /** 天线摆角，左右两根同向摆动 */
    internal val antennaSwing = Animatable(0f)

    /** 眼睛睁开程度，眨眼时短暂变小 */
    internal val eyeOpenness = Animatable(1f)

    /** 视线偏移，以眼睛宽度为单位，眼睛整体在屏幕里平移 */
    internal val gaze = Animatable(Offset.Zero, Offset.VectorConverter)

    /** 屏幕从脸切到彩条测试图的进度，0 是脸，1 是完全显示彩条 */
    internal val screenPower = Animatable(0f)

    /** 彩条上干扰条的滚动相位，屏幕亮着时循环推进 */
    internal val screenPhase = Animatable(0f)

    var eyes by mutableStateOf(MascotEyes.Open)
    /** 愚人节倒挂 */
    var upsideDown by mutableStateOf(false)

    /** 跳一下：先压扁蓄力、再起跳、最后带回弹落地，节奏与启动动画一致 */
    suspend fun hop(height: Float = DEFAULT_HOP_HEIGHT, spin: Boolean = false) {
        kickAntennas(-220f)
        animatePose(
            lift = SQUASH_LIFT,
            scaleX = BiliTvLaunchMotion.ICON_SQUASH_SCALE_X,
            scaleY = BiliTvLaunchMotion.ICON_SQUASH_SCALE_Y,
            tilt = BiliTvLaunchMotion.ICON_SQUASH_ROTATION_DEGREES,
            durationMillis = BiliTvLaunchMotion.SQUASH_DURATION_MILLIS,
            easing = standardEasing,
        )
        if (spin) tilt.snapTo(tilt.value - 360f)
        animatePose(
            lift = -height,
            scaleX = BiliTvLaunchMotion.ICON_JUMP_SCALE_X,
            scaleY = BiliTvLaunchMotion.ICON_JUMP_SCALE_Y,
            tilt = BiliTvLaunchMotion.ICON_JUMP_ROTATION_DEGREES,
            durationMillis = BiliTvLaunchMotion.JUMP_DURATION_MILLIS * if (spin) 2 else 1,
            easing = emphasizedEasing,
        )
        kickAntennas(360f)
        animatePose(
            lift = 0f,
            scaleX = 1f,
            scaleY = 1f,
            tilt = 0f,
            durationMillis = BiliTvLaunchMotion.SETTLE_DURATION_MILLIS,
            easing = settleEasing,
        )
    }

    /**
     * 回到地面站好。跳到一半被别的动作打断时，身体会停在半空，
     * 接下来不带跳跃的动作（晕倒、被拖走、东张西望）先调用它落地。
     */
    suspend fun land() {
        val spec = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
        coroutineScope {
            launch { lift.animateTo(0f, spec) }
            launch { scaleX.animateTo(1f, spec) }
            launch { scaleY.animateTo(1f, spec) }
            tilt.animateTo(0f, spec)
        }
    }

    /** 晕倒：向一侧倒下躺一会儿，再晃晃悠悠站起来 */
    suspend fun faint(lieMillis: Long = 2_600) {
        land()
        eyes = MascotEyes.Dizzy
        kickAntennas(420f)
        fallen.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 240f, visibilityThreshold = 0.002f))
        delay(lieMillis)
        kickAntennas(-320f)
        fallen.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f, visibilityThreshold = 0.002f))
        eyes = MascotEyes.Open
    }

    suspend fun blink() {
        eyeOpenness.animateTo(0.08f, tween(70, easing = LinearEasing))
        eyeOpenness.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
    }

    /** 天线被弹了一下似的来回晃，等晃停才返回 */
    suspend fun wiggleAntennas(strength: Float = 260f) {
        antennaSwing.animateTo(0f, antennaSpring, initialVelocity = strength)
    }

    /** 弹一下天线就走，不等它晃停 */
    fun kickAntennas(strength: Float) {
        effectScope.launch { wiggleAntennas(strength) }
    }

    /** 往某个方向瞟一眼再看回来 */
    suspend fun glance(direction: Offset) {
        gaze.animateTo(direction, spring(stiffness = Spring.StiffnessMediumLow))
        delay(700)
        gaze.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessLow))
    }

    /** 屏幕亮起彩条测试图「收信号」，带一道老电视开机时的横向亮线 */
    suspend fun turnOn() {
        screenPower.animateTo(1f, tween(POWER_DURATION_MILLIS, easing = LinearEasing))
    }

    /** 关掉屏幕回到脸：画面收成一道线、缩成一个点，再露出眼睛 */
    suspend fun turnOff() {
        screenPower.animateTo(0f, tween(POWER_DURATION_MILLIS, easing = LinearEasing))
    }

    /** 彩条信号上下滚动的干扰条 */
    suspend fun animateScreenLoop() {
        while (true) {
            screenPhase.snapTo(0f)
            screenPhase.animateTo(1f, tween(SCREEN_LOOP_MILLIS, easing = LinearEasing))
        }
    }

    /** 松手后被弹簧拽回原位，天线顺着惯性甩一甩 */
    suspend fun springBack(velocity: Offset) {
        kickAntennas((-velocity.x / 6f).coerceIn(-600f, 600f))
        effectScope.launch { gaze.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessLow)) }
        dragOffset.animateTo(
            Offset.Zero,
            spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
            initialVelocity = velocity,
        )
    }

    /** 拖拽时眼睛看向拖动方向，天线朝反方向甩 */
    internal suspend fun followDrag(velocity: Offset) {
        coroutineScope {
            launch {
                val length = velocity.getDistance().coerceAtLeast(1f)
                val strength = (length / 1800f).coerceAtMost(1f)
                gaze.snapTo(Offset(velocity.x / length, velocity.y / length) * (0.28f * strength))
            }
            antennaSwing.snapTo((-velocity.x / 90f).coerceIn(-28f, 28f))
        }
    }

    /** 平时的小动作：随机眨眼，偶尔晃天线或者四处看看 */
    suspend fun idleLoop(random: Random = Random.Default) {
        while (true) {
            delay(random.nextLong(2_400, 5_200))
            when (random.nextInt(10)) {
                0, 1 -> wiggleAntennas(if (random.nextBoolean()) 200f else -200f)
                2 -> glance(Offset(if (random.nextBoolean()) 0.22f else -0.22f, 0f))
                3 -> {
                    blink()
                    delay(140)
                    blink()
                }
                else -> blink()
            }
        }
    }

    private suspend fun animatePose(
        lift: Float,
        scaleX: Float,
        scaleY: Float,
        tilt: Float,
        durationMillis: Int,
        easing: Easing,
    ) {
        val spec = tween<Float>(durationMillis = durationMillis, easing = easing)
        coroutineScope {
            launch { this@BiliTvMascotState.lift.animateTo(lift, spec) }
            launch { this@BiliTvMascotState.scaleX.animateTo(scaleX, spec) }
            launch { this@BiliTvMascotState.scaleY.animateTo(scaleY, spec) }
            this@BiliTvMascotState.tilt.animateTo(tilt, spec)
        }
    }

    companion object {
        /** 默认跳起自身画布的 1.2 倍高，与关于页原来的弹跳幅度相当 */
        const val DEFAULT_HOP_HEIGHT = 1.2f
        private const val SQUASH_LIFT = 0.12f
        private const val POWER_DURATION_MILLIS = 420
        private const val SCREEN_LOOP_MILLIS = 1_400

        private val antennaSpring = spring<Float>(dampingRatio = 0.22f, stiffness = 280f)
        private val standardEasing = CubicBezierEasing(
            BiliTvLaunchMotion.STANDARD_EASE_X1,
            BiliTvLaunchMotion.STANDARD_EASE_Y1,
            BiliTvLaunchMotion.STANDARD_EASE_X2,
            BiliTvLaunchMotion.STANDARD_EASE_Y2,
        )
        private val emphasizedEasing = CubicBezierEasing(
            BiliTvLaunchMotion.EMPHASIZED_EASE_X1,
            BiliTvLaunchMotion.EMPHASIZED_EASE_Y1,
            BiliTvLaunchMotion.EMPHASIZED_EASE_X2,
            BiliTvLaunchMotion.EMPHASIZED_EASE_Y2,
        )
        private val settleEasing = Easing(BiliTvLaunchMotion::settleOvershoot)
    }
}

/** 系统关闭了动画（开发者选项或无障碍的「移除动画」）时，不播放待机小动作与装饰性动画。 */
internal fun systemAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

/**
 * 页面可见时让小电视自己眨眼、晃天线。
 *
 * [active] 为假（例如所在 Tab 未显示）或应用退到后台时暂停，避免不可见的动画持续请求帧。
 */
@Composable
fun MascotIdleEffect(state: BiliTvMascotState, active: Boolean = true) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state, active, lifecycleOwner) {
        if (!active || !systemAnimationsEnabled()) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state.idleLoop()
        }
    }
}

@Composable
fun rememberBiliTvMascotState(): BiliTvMascotState {
    val effectScope = rememberCoroutineScope()
    return remember { BiliTvMascotState(effectScope) }
}

/**
 * 用代码画出的 BiliTools 小电视，外形与应用图标一致，但每个部件都能单独动。
 *
 * 画布是正方形，小电视居中、四周留出天线与影子的空间；跳跃、拖拽会画到画布之外，
 * 外层不要裁剪。
 */
@Composable
fun BiliTvMascot(
    state: BiliTvMascotState,
    modifier: Modifier = Modifier,
    /** 屏幕底色；默认透明，与应用图标一样透出背后的页面，深色模式下不会是一块刺眼的白 */
    screenColor: Color = Color.Transparent,
) {
    // 屏幕亮着时循环滚动彩条上的干扰条；只在开关屏时重组一次
    val screenOn = state.screenPower.targetValue > 0f
    LaunchedEffect(state, screenOn) {
        if (screenOn) state.animateScreenLoop()
    }
    Canvas(modifier = modifier) {
        val unit = size.minDimension / MascotGeometry.VIEW_SIZE
        val origin = Offset(
            (size.width - MascotGeometry.VIEW_SIZE * unit) / 2f - MascotGeometry.VIEW_MIN * unit,
            (size.height - MascotGeometry.VIEW_SIZE * unit) / 2f - MascotGeometry.VIEW_MIN * unit,
        )
        val drag = state.dragOffset.value
        val liftPx = state.lift.value * size.minDimension
        translate(origin.x + drag.x, origin.y + drag.y) {
            scale(unit, unit, pivot = Offset.Zero) {
                if (state.upsideDown) {
                    rotate(180f, pivot = MascotGeometry.BODY_CENTER) { drawMascot(state, liftPx / unit, screenColor) }
                } else {
                    drawShadow(liftPx / unit)
                    drawMascot(state, liftPx / unit, screenColor)
                }
            }
        }
    }
}

/** 以下坐标都取自 1024×1024 的应用图标，便于与图标逐像素对照。 */
private object MascotGeometry {
    const val VIEW_MIN = 200f
    const val VIEW_SIZE = 624f

    val BODY = RoundRect(241f, 349f, 781f, 722f, CornerRadius(112f))
    val SCREEN = RoundRect(302f, 407f, 720f, 662f, CornerRadius(56f))
    val SCREEN_CENTER = Offset(511f, 534.5f)
    val BODY_CENTER = Offset(511f, 512f)

    /** 脚底中心，压扁、倾斜都以这里为支点，落地时脚不会离开地面 */
    val GROUND_PIVOT = Offset(511f, 752f)

    /** 晕倒时原地侧躺：绕机身中心转 90°，再抬高一点让侧面正好落在地上 */
    val FALL_PIVOT = Offset(511f, 535f)
    const val FALL_DEGREES = 90f
    const val FALL_RAISE = 52f

    val EYE_LEFT = Offset(407.5f, 509f)
    val EYE_RIGHT = Offset(614.5f, 509f)
    const val EYE_WIDTH = 54f
    const val EYE_HEIGHT = 89f

    val ANTENNA_LEFT_TIP = Offset(380f, 300f)
    val ANTENNA_RIGHT_TIP = Offset(644f, 300f)

    /** 天线插进机身的位置，摆动时绕这里转 */
    val ANTENNA_LEFT_ROOT = Offset(429f, 349f)
    val ANTENNA_RIGHT_ROOT = Offset(595f, 349f)
    const val ANTENNA_WIDTH = 56f

    val FOOT_LEFT = Offset(394f, 720f)
    val FOOT_RIGHT = Offset(650f, 720f)
    const val FOOT_RADIUS = 33f

    /** 影子是一团压扁的柔光：中心藏在机身下，露出来的部分从脚底向外慢慢化开 */
    val SHADOW_CENTER = Offset(511f, 742f)
    val SHADOW_RADIUS = Size(320f, 70f)

    val bodyBrush = Brush.verticalGradient(
        0f to Color(0xFF62D8FF),
        1f to Color(0xFF2A8FEE),
        startY = 272f,
        endY = 755f,
    )
    val eyeBrush = Brush.verticalGradient(
        0f to Color(0xFF51BEFA),
        1f to Color(0xFF3A9CF2),
        startY = 465f,
        endY = 553f,
    )
    val eyeColor = Color(0xFF3FA3F4)
    val footColor = Color(0xFF288BF0)
    val shadowColor = Color(0xFF9EC4EB)

    /**
     * 按应用图标影子量出的衰减曲线：相当于一个实心椭圆做了高斯模糊，
     * 半透明的边缘约占半径的一半，比线性渐变更柔和。
     */
    val shadowFalloff = listOf(
        0f to 1f, 0.3f to 0.89f, 0.45f to 0.67f, 0.53f to 0.5f, 0.62f to 0.31f,
        0.72f to 0.15f, 0.82f to 0.06f, 0.92f to 0.017f, 1f to 0f,
    )
    const val SHADOW_ALPHA = 0.94f

    /** 经典 SMPTE 彩条：上段七色、中段反向窄条、下段 PLUGE */
    val signalTopBars = listOf(
        Color(0xFFBFBFBF), Color(0xFFBFBF00), Color(0xFF00BFBF), Color(0xFF00BF00),
        Color(0xFFBF00BF), Color(0xFFBF0000), Color(0xFF0000BF),
    )
    val signalMiddleBars = listOf(
        Color(0xFF0000BF), Color(0xFF131313), Color(0xFFBF00BF), Color(0xFF131313),
        Color(0xFF00BFBF), Color(0xFF131313), Color(0xFFBFBFBF),
    )
    val signalBottomBars = listOf(
        Color(0xFF00214C), Color(0xFFFFFFFF), Color(0xFF32006A), Color(0xFF131313),
        Color(0xFF090909), Color(0xFF131313), Color(0xFF1D1D1D),
    )
}

private fun DrawScope.drawShadow(lift: Float) {
    // 跳得越高影子越小越淡
    val height = (-lift).coerceAtLeast(0f)
    val shrink = (1f - height / 900f).coerceIn(0.45f, 1f)
    val radius = MascotGeometry.SHADOW_RADIUS
    scale(shrink, shrink, pivot = MascotGeometry.SHADOW_CENTER) {
        scale(1f, radius.height / radius.width, pivot = MascotGeometry.SHADOW_CENTER) {
            val alpha = MascotGeometry.SHADOW_ALPHA * shrink
            drawCircle(
                brush = Brush.radialGradient(
                    *MascotGeometry.shadowFalloff
                        .map { (stop, strength) -> stop to MascotGeometry.shadowColor.copy(alpha = alpha * strength) }
                        .toTypedArray(),
                    center = MascotGeometry.SHADOW_CENTER,
                    radius = radius.width,
                ),
                radius = radius.width,
                center = MascotGeometry.SHADOW_CENTER,
            )
        }
    }
}

private fun DrawScope.drawMascot(state: BiliTvMascotState, lift: Float, screenColor: Color) {
    val pivot = MascotGeometry.GROUND_PIVOT
    withTransform({
        translate(0f, lift)
        translate(0f, -state.fallen.value * MascotGeometry.FALL_RAISE)
        rotate(state.fallen.value * MascotGeometry.FALL_DEGREES, MascotGeometry.FALL_PIVOT)
        rotate(state.tilt.value, pivot)
        scale(state.scaleX.value, state.scaleY.value, pivot)
    }) {
        val swing = state.antennaSwing.value
        drawAntenna(MascotGeometry.ANTENNA_LEFT_TIP, MascotGeometry.ANTENNA_LEFT_ROOT, swing)
        drawAntenna(MascotGeometry.ANTENNA_RIGHT_TIP, MascotGeometry.ANTENNA_RIGHT_ROOT, swing * 0.85f)
        drawCircle(MascotGeometry.footColor, MascotGeometry.FOOT_RADIUS, MascotGeometry.FOOT_LEFT)
        drawCircle(MascotGeometry.footColor, MascotGeometry.FOOT_RADIUS, MascotGeometry.FOOT_RIGHT)
        // 机身是一圈边框，屏幕处镂空
        val frame = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(MascotGeometry.BODY)
            addRoundRect(MascotGeometry.SCREEN)
        }
        drawPath(frame, MascotGeometry.bodyBrush)
        drawScreen(state, screenColor)
    }
}

private fun DrawScope.drawAntenna(tip: Offset, root: Offset, swing: Float) {
    // 天线延伸进机身里一截，摆动时根部不会露出缝隙
    val inside = root + (root - tip) * 0.45f
    rotate(swing, pivot = root) {
        drawLine(
            brush = MascotGeometry.bodyBrush,
            start = tip,
            end = inside,
            strokeWidth = MascotGeometry.ANTENNA_WIDTH,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawScreen(state: BiliTvMascotState, screenColor: Color) {
    val screen = MascotGeometry.SCREEN
    val screenPath = Path().apply { addRoundRect(screen) }
    drawPath(screenPath, screenColor)
    val power = state.screenPower.value
    clipPath(screenPath) {
        if (power <= 0f) {
            drawFace(state, collapse = 0f)
            return@clipPath
        }
        // 开机分三段：脸压扁成线（0~0.3）→ 亮线横向拉开（0.3~0.5）→ 画面上下展开（0.5~1）
        val bounds = Rect(screen.left, screen.top, screen.right, screen.bottom)
        when {
            power < 0.3f -> drawFace(state, collapse = power / 0.3f)
            power < 0.5f -> {
                val width = bounds.width * ((power - 0.3f) / 0.2f)
                drawLine(
                    color = Color(0xFFB8E6FF),
                    start = Offset(bounds.center.x - width / 2f, bounds.center.y),
                    end = Offset(bounds.center.x + width / 2f, bounds.center.y),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round,
                )
            }
            else -> {
                val open = (power - 0.5f) / 0.5f
                val halfHeight = bounds.height / 2f * open
                val visible = Rect(bounds.left, bounds.center.y - halfHeight, bounds.right, bounds.center.y + halfHeight)
                clipPath(Path().apply { addRect(visible) }) {
                    drawSignal(bounds, state.screenPhase.value)
                }
                // 刚展开时画面泛白，像显像管刚亮起
                drawRect(Color.White.copy(alpha = (1f - open) * 0.7f), visible.topLeft, visible.size)
            }
        }
    }
}

private fun DrawScope.drawFace(state: BiliTvMascotState, collapse: Float) {
    val gaze = state.gaze.value
    val shift = Offset(gaze.x * MascotGeometry.EYE_WIDTH * 2f, gaze.y * MascotGeometry.EYE_HEIGHT)
    val squeeze = 1f - collapse
    scale(1f, squeeze.coerceAtLeast(0.04f), pivot = MascotGeometry.SCREEN_CENTER) {
        drawEye(MascotGeometry.EYE_LEFT + shift, state.eyes, state.eyeOpenness.value, isLeft = true)
        drawEye(MascotGeometry.EYE_RIGHT + shift, state.eyes, state.eyeOpenness.value, isLeft = false)
    }
}

private fun DrawScope.drawEye(center: Offset, eyes: MascotEyes, openness: Float, isLeft: Boolean) {
    val width = MascotGeometry.EYE_WIDTH
    val height = MascotGeometry.EYE_HEIGHT
    val lineWidth = 20f
    val color = MascotGeometry.eyeColor
    when (eyes) {
        MascotEyes.Open -> {
            // 眨眼时整颗眼睛上下压扁，最扁时也保留一道线
            val eyeHeight = (height * openness).coerceAtLeast(width * 0.3f)
            drawRoundRect(
                brush = MascotGeometry.eyeBrush,
                topLeft = Offset(center.x - width / 2f, center.y - eyeHeight / 2f),
                size = Size(width, eyeHeight),
                cornerRadius = CornerRadius(width / 2f),
            )
        }
        MascotEyes.Sleepy -> {
            // 上眼皮耷拉下来：把整颗眼睛的上半截切掉，留下平平的眼睑
            val lidTop = center.y + height / 2f - height * 0.46f * openness.coerceAtLeast(0.4f)
            clipRect(
                left = center.x - width,
                top = lidTop,
                right = center.x + width,
                bottom = center.y + height,
            ) {
                drawRoundRect(
                    brush = MascotGeometry.eyeBrush,
                    topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(width / 2f),
                )
            }
        }
        MascotEyes.Happy -> {
            val path = Path().apply {
                moveTo(center.x - 34f, center.y + 14f)
                quadraticTo(center.x, center.y - 46f, center.x + 34f, center.y + 14f)
            }
            drawPath(path, color, style = Stroke(width = lineWidth + 2f, cap = StrokeCap.Round))
        }
        MascotEyes.Squint -> {
            // 左眼 >，右眼 <
            val direction = if (isLeft) 1f else -1f
            val path = Path().apply {
                moveTo(center.x - 22f * direction, center.y - 30f)
                lineTo(center.x + 24f * direction, center.y)
                lineTo(center.x - 22f * direction, center.y + 30f)
            }
            drawPath(path, color, style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        MascotEyes.Dizzy -> {
            val arm = 26f
            drawLine(color, center + Offset(-arm, -arm), center + Offset(arm, arm), lineWidth, StrokeCap.Round)
            drawLine(color, center + Offset(-arm, arm), center + Offset(arm, -arm), lineWidth, StrokeCap.Round)
        }
        MascotEyes.Surprised -> {
            val radius = 30f * openness.coerceAtLeast(0.2f)
            drawCircle(MascotGeometry.eyeBrush, radius = radius, center = center)
            drawCircle(Color.White.copy(alpha = 0.85f), radius = radius * 0.3f, center = center + Offset(radius * 0.32f, -radius * 0.36f))
        }
    }
}

private fun DrawScope.drawSignal(bounds: Rect, phase: Float) {
    val barWidth = bounds.width / 7f
    val topHeight = bounds.height * 0.67f
    val middleHeight = bounds.height * 0.08f
    fun row(colors: List<Color>, top: Float, height: Float) {
        colors.forEachIndexed { index, color ->
            // 每条多画 1 个单位，避免缩放后相邻色条之间出现发丝缝
            drawRect(color, Offset(bounds.left + barWidth * index, top), Size(barWidth + 1f, height))
        }
    }
    row(MascotGeometry.signalTopBars, bounds.top, topHeight)
    row(MascotGeometry.signalMiddleBars, bounds.top + topHeight, middleHeight)
    row(MascotGeometry.signalBottomBars, bounds.top + topHeight + middleHeight, bounds.height - topHeight - middleHeight)
    // 收信号时自上而下滚过的两道干扰条
    repeat(2) { index ->
        val y = bounds.top + ((phase + index * 0.5f) % 1f) * (bounds.height + 60f) - 30f
        drawRect(Color.White.copy(alpha = 0.22f), Offset(bounds.left, y), Size(bounds.width, 18f))
        drawRect(Color.Black.copy(alpha = 0.10f), Offset(bounds.left, y + 18f), Size(bounds.width, 6f))
    }
}
