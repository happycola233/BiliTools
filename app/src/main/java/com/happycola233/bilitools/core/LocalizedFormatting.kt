package com.happycola233.bilitools.core

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit

private val chineseNumberUnitBoundary = Regex("""(?<=\p{Nd})(?=\p{IsHan})|(?<=\p{IsHan})(?=\p{Nd})""")

/** 使用 Android ICU 的数字系统，避免格式化结果随宿主 JVM 的 CLDR 版本变化。 */
internal fun Context.formatByteCount(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    val number = NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply {
        isGroupingUsed = false
        minimumFractionDigits = if (bytes > 0L) 1 else 0
        maximumFractionDigits = minimumFractionDigits
    }.format(value)
    return "$number ${units[unitIndex]}"
}

/** 时长的单位、数字及复数规则交给平台语言数据，供界面和后台通知共用。 */
internal fun Context.formatEstimatedTime(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val remainingSeconds = seconds % 60
    val measures = when {
        hours > 0L -> arrayOf(Measure(hours, MeasureUnit.HOUR), Measure(minutes, MeasureUnit.MINUTE))
        minutes > 0L -> arrayOf(Measure(minutes, MeasureUnit.MINUTE), Measure(remainingSeconds, MeasureUnit.SECOND))
        else -> arrayOf(Measure(remainingSeconds, MeasureUnit.SECOND))
    }
    val locale = resources.configuration.locales[0]
    val formatted = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        .formatMeasures(*measures)
    // ICU 中文短格式会连写数字与单位；统一补齐间距，保留平台的简繁体单位和数字格式。
    return if (locale.language == "zh") formatted.replace(chineseNumberUnitBoundary, " ") else formatted
}
