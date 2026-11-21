package com.happycola233.bilitools.ui.settings

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.MascotEggCopy
import com.happycola233.bilitools.ui.eggs.BiliTvMascot
import com.happycola233.bilitools.ui.eggs.BiliTvMascotState
import com.happycola233.bilitools.ui.eggs.rememberBiliTvMascotState
import com.happycola233.bilitools.ui.eggs.EggCalendar
import com.happycola233.bilitools.ui.eggs.MascotEyes
import com.happycola233.bilitools.ui.eggs.MascotIdleEffect
import com.happycola233.bilitools.ui.haptics.AppHaptics
import java.time.LocalDateTime
import kotlin.math.atanh
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 关于页的小电视：点一下跳一下，连着戳会一路「升级」反应；按住不放打开屏幕收信号；
 * 拖动能把它拽出来，松手被弹簧拉回原位。三种手势按「抬手 / 移动超过阈值 / 按满长按时长」
 * 谁先发生来区分，按住开屏后再拖动也可以，屏幕在松手时才关。
 *
 * [speech] 是小电视此刻想说的话，由标题区域显示；没有彩蛋文案的语言只有动作没有台词。
 */
@Stable
internal class AboutMascotController(
    val mascot: BiliTvMascotState,
    private val scope: CoroutineScope,
    private val copy: MascotEggCopy?,
    private val haptics: AppHaptics,
    private val maxDragPx: Float,
    /** 往上能拖的距离更短：再往上就钻到页面标题栏底下了 */
    private val maxDragUpPx: Float,
) {
    /** 小电视此刻说的话，标题区域用它暂时顶替版本号 */
    var speech by mutableStateOf<String?>(null)
        private set

    private var pokeCount = 0
    private var lastPokeAt = 0L

    /** 空翻、晕倒这类较长的表演；进行中时点按照样计数，但不再插入普通弹跳，拖动和长按也不响应 */
    private var performance: Job? = null
    private var hopJob: Job? = null
    private var speechJob: Job? = null
    private var screenJob: Job? = null
    /** 手指的实际拖动距离；屏幕上的位移是它经过 [rubberBand] 衰减后的结果 */
    private var rawDrag = Offset.Zero

    /**
     * 每次按下开始拖动、每次松手都加一。拖动的位置更新要排队到下一帧才执行，
     * 松手时还没执行的那几次据此作废，否则会把正在弹回的小电视一下拽回原点。
     */
    private var dragSession = 0

    private val performing get() = performance?.isActive == true

    fun onTap(now: Long = SystemClock.uptimeMillis()) {
        pokeCount = if (now - lastPokeAt <= COMBO_GAP_MILLIS) pokeCount + 1 else 1
        lastPokeAt = now
        haptics.tap()
        when (pokeCount) {
            SURPRISED_POKE -> react(MascotEyes.Surprised, SURPRISED_POKE)
            ANNOYED_POKE -> react(MascotEyes.Squint, ANNOYED_POKE)
            SPIN_POKE -> perform {
                say(SPIN_POKE)
                mascot.hop(height = SPIN_HOP_HEIGHT, spin = true)
            }
            FAINT_POKE -> {
                // 晕倒是这一轮的结尾，之后再戳从头开始
                pokeCount = 0
                perform {
                    haptics.reject()
                    say(FAINT_POKE, durationMillis = FAINT_SPEECH_MILLIS)
                    mascot.faint()
                }
            }
            else -> if (!performing) {
                if (pokeCount == 1) mascot.eyes = MascotEyes.Open
                hop()
            }
        }
    }

    fun onLongPress() {
        if (performing) return
        haptics.longPress()
        speechJob?.cancel()
        speech = copy?.signal
        screenJob?.cancel()
        screenJob = scope.launch {
            launch { mascot.wiggleAntennas() }
            mascot.turnOn()
        }
    }

    fun onDragStart() {
        if (performing) return
        // 跳到一半被抓住时先落回原来的高度，否则松手后会悬在半空
        hopJob?.cancel()
        hopJob = scope.launch { mascot.land() }
        speechJob?.cancel()
        // 开着屏收信号时被拖走，台词仍保持「收信号」
        if (mascot.screenPower.targetValue == 0f) speech = copy?.drag
        dragSession += 1
        // 弹回途中再次抓住：从它此刻所在的位置接着拖，不跳
        rawDrag = rawOffset(mascot.dragOffset.value)
    }

    fun onDrag(delta: Offset, velocity: Velocity) {
        if (performing) return
        rawDrag += delta
        val target = displayedOffset(rawDrag)
        val session = dragSession
        scope.launch {
            if (session != dragSession) return@launch
            mascot.dragOffset.snapTo(target)
            mascot.followDrag(Offset(velocity.x, velocity.y))
        }
    }

    /** 手指抬起：拖过就从当前位置弹回去，开着屏就关掉 */
    fun onRelease(velocity: Velocity, dragged: Boolean, longPressed: Boolean) {
        if (performing) return
        dragSession += 1
        if (dragged) {
            val shown = displayedOffset(rawDrag)
            // 越往外越拉不动，小电视实际移动得比手指慢，弹回的初速度按同样比例打折
            val shownVelocity = Offset(
                velocity.x * (1f - (shown.x / maxDragPx).let { it * it }),
                velocity.y * (1f - (shown.y / limitY(shown.y)).let { it * it }),
            )
            scope.launch {
                // 排在它前面的拖动更新已作废，先落到松手时的位置再开始弹
                mascot.dragOffset.snapTo(shown)
                mascot.springBack(shownVelocity)
            }
        }
        rawDrag = Offset.Zero
        if (longPressed) {
            screenJob?.cancel()
            screenJob = scope.launch { mascot.turnOff() }
        }
        if (dragged || longPressed) hideSpeech(after = RELEASE_SPEECH_MILLIS)
    }

    private fun hop() {
        hopJob?.cancel()
        hopJob = scope.launch { mascot.hop() }
    }

    /** 换个表情、说句话、跳一下；正在表演时只换表情和台词 */
    private fun react(eyes: MascotEyes, poke: Int) {
        if (performing) return
        mascot.eyes = eyes
        say(poke)
        hop()
    }

    /**
     * 较长的表演按顺序排队：上一段（比如空翻）还没演完，就等它演完再开始，
     * 连着戳不会漏掉晕倒。表演结束时重新计时，接着戳仍算同一轮连击。
     */
    private fun perform(block: suspend () -> Unit) {
        val previous = performance
        hopJob?.cancel()
        performance = scope.launch {
            previous?.join()
            block()
            lastPokeAt = SystemClock.uptimeMillis()
        }
    }

    private fun say(poke: Int, durationMillis: Long = SPEECH_MILLIS) {
        val text = copy?.pokes?.get(poke) ?: return
        speechJob?.cancel()
        speech = text
        hideSpeech(after = durationMillis)
    }

    private fun hideSpeech(after: Long) {
        speechJob?.cancel()
        speechJob = scope.launch {
            delay(after)
            speech = null
        }
    }

    /** 往上能拖的距离更短 */
    private fun limitY(y: Float) = if (y < 0f) maxDragUpPx else maxDragPx

    private fun displayedOffset(raw: Offset) =
        Offset(rubberBand(raw.x, maxDragPx), rubberBand(raw.y, limitY(raw.y)))

    private fun rawOffset(displayed: Offset) =
        Offset(inverseRubberBand(displayed.x, maxDragPx), inverseRubberBand(displayed.y, limitY(displayed.y)))

    /** 起初紧跟手指，越往外越拉不动，最多被拽出 [limit] */
    private fun rubberBand(distance: Float, limit: Float): Float = limit * tanh(distance / limit)

    private fun inverseRubberBand(displayed: Float, limit: Float): Float =
        limit * atanh((displayed / limit).coerceIn(-MAX_BAND_RATIO, MAX_BAND_RATIO))

    companion object {
        private const val COMBO_GAP_MILLIS = 1_000L

        /** 反算手指距离时避开 atanh 在 ±1 处的无穷大 */
        private const val MAX_BAND_RATIO = 0.99f
        private const val SURPRISED_POKE = 5
        private const val ANNOYED_POKE = 8
        private const val SPIN_POKE = 12
        private const val FAINT_POKE = 15
        private const val SPIN_HOP_HEIGHT = 1.45f
        private const val SPEECH_MILLIS = 2_200L
        private const val FAINT_SPEECH_MILLIS = 3_400L
        private const val RELEASE_SPEECH_MILLIS = 600L
    }
}

@Composable
internal fun rememberAboutMascotController(
    copy: MascotEggCopy?,
    haptics: AppHaptics,
): AboutMascotController {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxDragPx = with(density) { MaxDragDistance.toPx() }
    val maxDragUpPx = with(density) { MaxDragUpDistance.toPx() }
    val mascot = rememberBiliTvMascotState()
    LaunchedEffect(mascot) {
        mascot.upsideDown = EggCalendar.isAprilFools(LocalDateTime.now())
    }
    return remember(mascot, copy, haptics, maxDragPx, maxDragUpPx) {
        AboutMascotController(mascot, scope, copy, haptics, maxDragPx, maxDragUpPx)
    }
}

/** 关于页卡片左侧：慢慢旋转的饼干形白底，加上一只能玩的小电视。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AboutMascotIcon(
    controller: AboutMascotController,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val backgroundRotation by rememberInfiniteTransition(label = "aboutIconBackground").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "aboutIconBackgroundRotation",
    )
    MascotIdleEffect(controller.mascot)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick { controller.onTap(); true }
            }
            .pointerInput(controller) { detectMascotGestures(controller) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = backgroundRotation }
                .background(Color.White, MaterialShapes.Cookie9Sided.toShape()),
        )
        BiliTvMascot(controller.mascot, Modifier.size(MascotSize))
    }
}

/**
 * 按下后看谁先发生：抬手是点按；移动超过触摸阈值是拖动；按满长按时长是长按开屏，
 * 之后再移动同样转为拖动。拖动期间消费事件，页面不会跟着滚动。
 */
private suspend fun PointerInputScope.detectMascotGestures(
    controller: AboutMascotController,
) {
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown()
        val pointerId = down.id
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var travel = Offset.Zero
        var dragging = false
        var longPressed = false

        // 第一段：长按时长内等抬手或拖动
        val firstOutcome = withTimeoutOrNull(longPressTimeout) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: return@withTimeoutOrNull false
                tracker.addPosition(change.uptimeMillis, change.position)
                if (change.changedToUp()) {
                    change.consume()
                    return@withTimeoutOrNull false
                }
                travel += change.positionChange()
                if (travel.getDistance() > touchSlop) {
                    change.consume()
                    return@withTimeoutOrNull true
                }
            }
        }
        when (firstOutcome) {
            false -> {
                // 抬手前没有移动也没到长按：普通点按
                controller.onTap()
                return@awaitEachGesture
            }
            true -> {
                dragging = true
                controller.onDragStart()
                controller.onDrag(travel, tracker.calculateVelocity())
            }
            null -> {
                longPressed = true
                controller.onLongPress()
            }
        }

        // 第二段：拖动或保持长按，直到抬手
        travel = Offset.Zero
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: break
            tracker.addPosition(change.uptimeMillis, change.position)
            if (change.changedToUp()) {
                change.consume()
                break
            }
            val delta = change.positionChange()
            if (!dragging) {
                travel += delta
                if (travel.getDistance() > touchSlop) {
                    dragging = true
                    controller.onDragStart()
                    controller.onDrag(travel, tracker.calculateVelocity())
                }
            } else {
                controller.onDrag(delta, tracker.calculateVelocity())
            }
            if (dragging) change.consume()
        }
        controller.onRelease(tracker.calculateVelocity(), dragged = dragging, longPressed = longPressed)
    }
}

/** 小电视画布边长；外层饼干形白底是 64dp */
private val MascotSize = 34.dp
private val MaxDragDistance = 140.dp
private val MaxDragUpDistance = 36.dp
