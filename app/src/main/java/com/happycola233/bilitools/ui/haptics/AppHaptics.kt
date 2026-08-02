package com.happycola233.bilitools.ui.haptics

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.HapticFeedbackLevel

/**
 * 语义化触感效果。调用点只描述「发生了什么交互」，具体震动常量与降级由 [AppHaptics] 决定。
 */
enum class HapticEffect {
    /** 普通点击确认：列表行、图标按钮等。 */
    Tap,

    /** 开关 / 复选框被勾选。 */
    ToggleOn,

    /** 开关 / 复选框被取消勾选。 */
    ToggleOff,

    /** 长按唤起菜单或多选模式。 */
    LongPress,

    /** 在若干互斥项之间切换：分段按钮、底部导航、画质选择。 */
    Select,

    /** 连续手势中跨过一个离散档位。务必配合 [HapticTicker] 节流。 */
    Tick,

    /** 手势越过吸附阈值，如侧滑打开操作区。 */
    ThresholdActivate,

    /** 操作被接受并产生了实际结果，如开始下载、删除确认。 */
    Confirm,

    /** 操作被拒绝或失败。 */
    Reject,
    ;

    /**
     * 「轻量」档只保留信息量高、频率低的反馈；点击、勾选、tick 这类高频反馈仅在「完整」档开启。
     */
    internal fun isEnabledAt(level: HapticFeedbackLevel): Boolean = when (level) {
        HapticFeedbackLevel.Off -> false
        HapticFeedbackLevel.Full -> true
        HapticFeedbackLevel.Light -> when (this) {
            LongPress, ThresholdActivate, Confirm, Reject -> true
            Tap, ToggleOn, ToggleOff, Select, Tick -> false
        }
    }

    /**
     * minSdk 为 29，而 CONFIRM / REJECT 需要 API 30、TOGGLE_* 与 SEGMENT_* / GESTURE_THRESHOLD_*
     * 需要 API 34，因此这里集中做降级；调用点不应再出现版本判断。
     */
    internal fun resolveConstant(): Int {
        val sdk = Build.VERSION.SDK_INT
        return when (this) {
            Tap -> HapticFeedbackConstants.VIRTUAL_KEY
            LongPress -> HapticFeedbackConstants.LONG_PRESS
            Select -> HapticFeedbackConstants.CONTEXT_CLICK
            ToggleOn -> if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_ON
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }

            ToggleOff -> if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_OFF
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }

            Tick -> if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.SEGMENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }

            ThresholdActivate -> if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }

            Confirm -> if (sdk >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }

            Reject -> if (sdk >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
    }
}

/**
 * 全局触感反馈入口。
 *
 * 统一走 [View.performHapticFeedback] 而不是 [android.os.Vibrator]，原因：
 * 1. 无需 `VIBRATE` 权限；
 * 2. 自动遵循系统「触感反馈」总开关与厂商强度调校，不会出现系统已关闭但应用仍在震的情况；
 * 3. 语义常量由 OEM 调校过，手感比自行拼 `VibrationEffect` 时长更接近系统原生体验。
 *
 * 用户开关判定与 API 等级降级都收敛在此，调用点不需要任何 `if (enabled)` 或版本判断。
 */
@Stable
class AppHaptics internal constructor(
    private val view: View?,
    private val levelProvider: () -> HapticFeedbackLevel,
) {
    fun tap() = perform(HapticEffect.Tap)

    fun toggle(on: Boolean) = perform(if (on) HapticEffect.ToggleOn else HapticEffect.ToggleOff)

    fun longPress() = perform(HapticEffect.LongPress)

    fun select() = perform(HapticEffect.Select)

    fun tick() = perform(HapticEffect.Tick)

    fun thresholdActivate() = perform(HapticEffect.ThresholdActivate)

    fun confirm() = perform(HapticEffect.Confirm)

    fun reject() = perform(HapticEffect.Reject)

    fun perform(effect: HapticEffect) {
        val target = view ?: return
        if (!effect.isEnabledAt(levelProvider())) return
        target.performHapticFeedback(effect.resolveConstant())
    }

    companion object {
        /** 预览、编辑态以及尚未挂载 Provider 时使用的空实现。 */
        val NoOp = AppHaptics(view = null, levelProvider = { HapticFeedbackLevel.Off })
    }
}

/**
 * 连续手势（滑块、滚动条拖拽）的 tick 节流器。
 *
 * 只有离散档位真正变化才触发，并再限制一个最小间隔：快速拖动时逐帧调用
 * `performHapticFeedback` 会明显掉帧且额外耗电。
 */
@Stable
class HapticTicker(private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS) {
    private var lastStep: Int? = null
    private var lastTickUptimeMillis: Long = 0L

    /**
     * 上报当前离散档位；档位相对上一次发生变化且未被最小间隔拦截时执行 [onTick]。
     * 首次上报只记录基准档位，不触发反馈，避免手指刚按下就震一下。
     */
    fun onStep(step: Int, onTick: () -> Unit) {
        val previous = lastStep
        if (previous == step) return
        lastStep = step
        if (previous == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTickUptimeMillis < minIntervalMillis) return
        lastTickUptimeMillis = now
        onTick()
    }

    /** 手势结束时调用，下一次手势重新建立基准档位。 */
    fun reset() {
        lastStep = null
    }

    private companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS = 32L
    }
}

/**
 * 阈值反馈的状态机：只在「跨越」阈值的那一刻震一次，手指在阈值附近来回拖动不会反复触发。
 */
@Stable
class HapticThresholdGate {
    private var wasPassed = false
    private var activatedDuringGesture = false

    fun update(passed: Boolean, onActivate: () -> Unit) {
        val crossedThreshold = !wasPassed && passed
        wasPassed = passed
        if (!crossedThreshold || activatedDuringGesture) return
        activatedDuringGesture = true
        onActivate()
    }

    /**
     * 开始新手势，并以上报的初始位置建立基准。若手势从阈值外开始，则视为本次手势已经激活，
     * 避免用户从已展开状态向回拖动时，在第一次 MOVE 事件中产生伪造的「跨越」反馈。
     */
    fun reset(passed: Boolean = false) {
        wasPassed = passed
        activatedDuringGesture = passed
    }
}

/**
 * Compose 侧的触感入口。
 *
 * 直接由 [LocalView] 派生，因此无需在每个 Compose 根挂载 Provider —— 本项目存在多个互相独立的
 * `setContent` 根（两个 Fragment 各自建主题、若干 Activity 走 `BiliToolsSettingsTheme`），
 * 逐个包 Provider 只会让根节点持续膨胀。
 */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) {
        if (view.isInEditMode) AppHaptics.NoOp else view.createAppHaptics()
    }
}

/**
 * View 体系（RecyclerView Adapter、BottomNavigationView 等）的触感入口，与 Compose 侧共用同一套语义与降级。
 */
fun View.performAppHaptic(effect: HapticEffect) {
    val level = context.applicationContext.appContainer.settingsRepository
        .currentSettings()
        .hapticFeedbackLevel
    if (!effect.isEnabledAt(level)) return
    performHapticFeedback(effect.resolveConstant())
}

private fun View.createAppHaptics(): AppHaptics {
    val settingsRepository = context.applicationContext.appContainer.settingsRepository
    return AppHaptics(this) { settingsRepository.currentSettings().hapticFeedbackLevel }
}
