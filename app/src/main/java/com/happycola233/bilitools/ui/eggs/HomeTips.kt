package com.happycola233.bilitools.ui.eggs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.EasterEggCopy
import com.happycola233.bilitools.data.EggMilestone
import java.time.LocalDateTime
import kotlin.random.Random
import kotlinx.coroutines.delay

/** 一句话出现时小电视的样子。 */
enum class HomeTipMood {
    Normal,

    /** 里程碑、节日：笑眯眯地跳一下 */
    Celebrate,

    /** 深夜说困话时：眼皮耷拉 */
    Sleepy,

    /** 愚人节：倒挂 */
    Fools,

    /** 输入「干杯」 */
    Cheers,

    /** 对着空输入框连点解析 */
    Puzzled,
}

/** 首页小电视说的一句话。[parseTarget] 非空时点它会直接解析这个内容号。 */
data class HomeTip(
    val text: String,
    val mood: HomeTipMood = HomeTipMood.Normal,
    val parseTarget: String? = null,
)

/**
 * 决定首页说哪句话：打开首页时先说当天最特别的那句（里程碑 → 节日 → 深夜），之后点一下换一句随机的。
 * 纯逻辑，时间与随机数由调用方传入，便于测试。
 */
internal class HomeTipPicker(
    private val copy: EasterEggCopy,
    private val random: Random = Random.Default,
) {
    fun opening(now: LocalDateTime, milestone: EggMilestone?): HomeTip {
        milestone?.let(copy::milestone)?.let { return HomeTip(it, HomeTipMood.Celebrate) }
        if (EggCalendar.isAprilFools(now)) return next(now, current = null)
        EggCalendar.appBirthdayAge(now)?.let { age ->
            return HomeTip(copy.appBirthday.formatEggCopy(age), HomeTipMood.Celebrate)
        }
        copy.dateTips[EggCalendar.dateKey(now)]?.let { return HomeTip(it, HomeTipMood.Celebrate) }
        if (EggCalendar.isLateNight(now)) {
            return HomeTip(copy.lateNightTips.random(random), HomeTipMood.Sleepy)
        }
        return next(now, current = null)
    }

    fun next(now: LocalDateTime, current: HomeTip?): HomeTip {
        val pool = when {
            EggCalendar.isAprilFools(now) -> copy.aprilFoolsTips.map { HomeTip(it, HomeTipMood.Fools) }
            // 深夜有一部分概率说困话，只有说困话时眯眼；其余照常，免得几句困话来回重复
            EggCalendar.isLateNight(now) && random.nextFloat() < LATE_NIGHT_SHARE ->
                copy.lateNightTips.map { HomeTip(it, HomeTipMood.Sleepy) }
            else -> copy.tips.map { HomeTip(it) } + rickroll()
        }
        return pool.filter { it.text != current?.text }.ifEmpty { pool }.random(random)
    }

    private fun rickroll() = HomeTip(copy.rickrollTip, parseTarget = EasterEggCopy.RICKROLL_TARGET)

    companion object {
        /** 深夜换句时抽到困话的概率 */
        const val LATE_NIGHT_SHARE = 0.4f
    }
}

/** 首页小贴士的状态：当前这句话，以及点击、解析时的彩蛋反应。 */
@Stable
class HomeTipController internal constructor(
    private val copy: EasterEggCopy,
    private val picker: HomeTipPicker,
    private val clock: () -> LocalDateTime,
    milestone: EggMilestone?,
) {
    var tip by mutableStateOf(picker.opening(clock(), milestone))
        private set

    /** 每换一次话自增；同一句话重复出现时小电视也会再做一次反应 */
    var serial by mutableIntStateOf(0)
        private set

    private var blankParseStreak = 0

    fun showNext() = show(picker.next(clock(), tip))

    /**
     * 解析前先看看是不是彩蛋口令。返回 true 表示已被彩蛋接住，不再走真正的解析。
     * 空输入仍交给正常流程提示错误，只是连点第 [AIR_PARSE_STREAK] 次时小电视会接一句。
     */
    fun interceptParse(input: String): Boolean {
        val normalized = input.trim().lowercase()
        if (normalized.isEmpty()) {
            blankParseStreak += 1
            if (blankParseStreak >= AIR_PARSE_STREAK) {
                blankParseStreak = 0
                show(HomeTip(copy.parseAir, HomeTipMood.Puzzled))
            }
            return false
        }
        blankParseStreak = 0
        if (normalized in CHEERS_WORDS) {
            show(HomeTip(copy.cheers, HomeTipMood.Cheers))
            return true
        }
        return false
    }

    private fun show(next: HomeTip) {
        tip = next
        serial += 1
    }

    companion object {
        const val AIR_PARSE_STREAK = 5
        const val AUTO_ROTATE_MILLIS = 30_000L
        private val CHEERS_WORDS = setOf("干杯", "乾杯", "cheers", "2233")
    }
}

@Composable
fun rememberHomeTipController(
    copy: EasterEggCopy,
    takeMilestone: () -> EggMilestone?,
    clock: () -> LocalDateTime = LocalDateTime::now,
): HomeTipController = remember(copy) {
    HomeTipController(copy, HomeTipPicker(copy), clock, takeMilestone())
}

/**
 * 首页输入卡下方的小电视和它说的话。点小电视或气泡换一句，停在首页时每 30 秒自动换一句；
 * 正在解析时小电视打开屏幕「收信号」。
 *
 * [active] 为假（所在 Tab 不可见）时停下所有待机动画与自动换句。
 */
@Composable
fun HomeTipBanner(
    controller: HomeTipController,
    loading: Boolean,
    active: Boolean,
    onParseTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mascot = rememberBiliTvMascotState()
    val tip = controller.tip
    val animationsEnabled = remember { systemAnimationsEnabled() }
    val currentOnParseTarget by rememberUpdatedState(onParseTarget)
    val onTap = {
        val target = controller.tip.parseTarget
        if (target != null) currentOnParseTarget(target) else controller.showNext()
    }

    MascotIdleEffect(mascot, active = active && !loading)
    // 换话时小电视换表情并做个小反应；表情会一直保持到下一句
    LaunchedEffect(mascot, controller, controller.serial) {
        mascot.upsideDown = tip.mood == HomeTipMood.Fools
        mascot.eyes = when (tip.mood) {
            HomeTipMood.Celebrate, HomeTipMood.Cheers -> MascotEyes.Happy
            HomeTipMood.Sleepy -> MascotEyes.Sleepy
            HomeTipMood.Puzzled -> MascotEyes.Surprised
            HomeTipMood.Normal, HomeTipMood.Fools -> MascotEyes.Open
        }
        if (!animationsEnabled) return@LaunchedEffect
        when (tip.mood) {
            HomeTipMood.Celebrate -> mascot.hop(HOP_HEIGHT * 1.3f)
            HomeTipMood.Cheers -> {
                mascot.hop(HOP_HEIGHT)
                mascot.hop(HOP_HEIGHT * 0.7f)
            }
            HomeTipMood.Puzzled -> {
                // 上一句的跳跃可能刚被打断，先落地再东张西望
                mascot.land()
                mascot.glance(Offset(-0.22f, 0f))
                mascot.glance(Offset(0.22f, 0f))
            }
            HomeTipMood.Normal, HomeTipMood.Sleepy, HomeTipMood.Fools ->
                // 第一句是打开首页时自带的，不必跳
                if (controller.serial > 0) mascot.hop(HOP_HEIGHT)
        }
    }
    LaunchedEffect(mascot, loading) {
        if (loading) {
            mascot.kickAntennas(260f)
            mascot.turnOn()
        } else {
            mascot.turnOff()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(controller, controller.serial, active, lifecycleOwner) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(HomeTipController.AUTO_ROTATE_MILLIS)
            controller.showNext()
        }
    }

    val clickLabel = stringResource(
        if (tip.parseTarget != null) R.string.home_tip_parse_action else R.string.home_tip_next_action,
    )
    // 像空状态一样居中放在输入卡下方，不加容器：小电视不抢输入卡的视觉重心，文字用次要色
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = clickLabel,
                role = Role.Button,
                onClick = onTap,
            ),
    ) {
        BiliTvMascot(mascot, Modifier.size(MascotSize))
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = tip,
            // 新的一句像弹幕一样从尾侧飘进来；旧的一句先快速淡出，两句不重叠
            transitionSpec = {
                (slideInHorizontally(tween(TIP_ENTER_MILLIS, delayMillis = TIP_EXIT_MILLIS)) { it / 6 } +
                    fadeIn(tween(TIP_ENTER_MILLIS, delayMillis = TIP_EXIT_MILLIS)))
                    .togetherWith(slideOutHorizontally(tween(TIP_EXIT_MILLIS)) { -it / 8 } + fadeOut(tween(TIP_EXIT_MILLIS)))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.TopCenter,
            label = "homeTipText",
        ) { shown ->
            Text(
                text = shown.text,
                // 按段落平衡折行，避免第二行只剩一两个字
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
                // 点了会解析的那句用主色，提示它和别的句子不一样
                color = if (shown.parseTarget != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = TextMaxWidth),
            )
        }
    }
}

private val MascotSize = 36.dp
private val TextMaxWidth = 300.dp
private const val TIP_EXIT_MILLIS = 110
private const val TIP_ENTER_MILLIS = 260
private const val HOP_HEIGHT = 0.42f
