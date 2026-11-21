package com.happycola233.bilitools.ui.eggs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.EasterEggCopy
import com.happycola233.bilitools.data.EggCopyLanguage
import java.time.LocalDateTime
import java.util.Locale

/** 当前界面语言的彩蛋文案；没有对应词库的语言返回 null，调用方据此不显示文字类彩蛋。 */
@Composable
fun rememberEasterEggCopy(): EasterEggCopy? {
    val context = LocalContext.current
    val locales = LocalConfiguration.current.locales
    return remember(context, locales) {
        EggCopyLanguage.resolve(locales)?.let(context.applicationContext.appContainer.easterEggRepository::copy)
    }
}

/** 彩蛋里用到的日期判断，统一按设备本地时间计算。 */
internal object EggCalendar {
    /** BiliTools 1.0 发布于 2026 年 2 月 4 日，这一天算它的生日 */
    private const val APP_BIRTH_YEAR = 2026
    private const val APP_BIRTH_MONTH = 2
    private const val APP_BIRTH_DAY = 4

    fun isAprilFools(now: LocalDateTime): Boolean = now.monthValue == 4 && now.dayOfMonth == 1

    /** 凌晨 1 点到 5 点前 */
    fun isLateNight(now: LocalDateTime): Boolean = now.hour in 1..4

    fun dateKey(now: LocalDateTime): String = String.format(Locale.ROOT, "%02d-%02d", now.monthValue, now.dayOfMonth)

    /** BiliTools 生日当天返回满几岁，其余日子返回 null */
    fun appBirthdayAge(now: LocalDateTime): Int? =
        (now.year - APP_BIRTH_YEAR).takeIf {
            it > 0 && now.monthValue == APP_BIRTH_MONTH && now.dayOfMonth == APP_BIRTH_DAY
        }
}

/** 文案里的数字占位符一律按 ROOT 格式化，避免阿拉伯语等环境把数字换成本地字形后与中文混排。 */
internal fun String.formatEggCopy(vararg args: Any): String = String.format(Locale.ROOT, this, *args)
