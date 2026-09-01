package com.happycola233.bilitools.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.google.android.material.color.DynamicColors

@StyleRes
internal fun AppThemeColor.overlayStyleResOrNull(): Int? {
    return when (this) {
        AppThemeColor.Dynamic -> null
        AppThemeColor.Sakura -> R.style.ThemeOverlay_BiliTools_ColorSakura
        AppThemeColor.Coral -> R.style.ThemeOverlay_BiliTools_ColorCoral
        AppThemeColor.Apricot -> R.style.ThemeOverlay_BiliTools_ColorApricot
        AppThemeColor.Sand -> R.style.ThemeOverlay_BiliTools_ColorSand
        AppThemeColor.Matcha -> R.style.ThemeOverlay_BiliTools_ColorMatcha
        AppThemeColor.Mint -> R.style.ThemeOverlay_BiliTools_ColorMint
        AppThemeColor.Seafoam -> R.style.ThemeOverlay_BiliTools_ColorSeafoam
        AppThemeColor.Lagoon -> R.style.ThemeOverlay_BiliTools_ColorLagoon
        AppThemeColor.Sky -> R.style.ThemeOverlay_BiliTools_ColorSky
        AppThemeColor.Iris -> R.style.ThemeOverlay_BiliTools_ColorIris
        AppThemeColor.Periwinkle -> R.style.ThemeOverlay_BiliTools_ColorPeriwinkle
        AppThemeColor.Lilac -> R.style.ThemeOverlay_BiliTools_ColorLilac
        AppThemeColor.Orchid -> R.style.ThemeOverlay_BiliTools_ColorOrchid
    }
}

@StringRes
internal fun AppThemeColor.displayNameRes(): Int {
    return when (this) {
        AppThemeColor.Dynamic -> R.string.settings_color_scheme_dynamic
        AppThemeColor.Sakura -> R.string.settings_color_scheme_sakura
        AppThemeColor.Coral -> R.string.settings_color_scheme_coral
        AppThemeColor.Apricot -> R.string.settings_color_scheme_apricot
        AppThemeColor.Sand -> R.string.settings_color_scheme_sand
        AppThemeColor.Matcha -> R.string.settings_color_scheme_matcha
        AppThemeColor.Mint -> R.string.settings_color_scheme_mint
        AppThemeColor.Seafoam -> R.string.settings_color_scheme_seafoam
        AppThemeColor.Lagoon -> R.string.settings_color_scheme_lagoon
        AppThemeColor.Sky -> R.string.settings_color_scheme_sky
        AppThemeColor.Iris -> R.string.settings_color_scheme_iris
        AppThemeColor.Periwinkle -> R.string.settings_color_scheme_periwinkle
        AppThemeColor.Lilac -> R.string.settings_color_scheme_lilac
        AppThemeColor.Orchid -> R.string.settings_color_scheme_orchid
    }
}

/** 设置页色块的取色结果：填充色，以及压在它上面的对勾颜色 */
internal data class ThemeColorSwatch(
    @ColorInt val fill: Int,
    @ColorInt val onFill: Int,
)

/**
 * 从配色 overlay 里直接取填充色，保证设置页色块与实际按钮完全同色。
 * 填充色属于 fixed 色组、深浅模式取值相同，因此无需区分当前模式。
 */
internal fun Context.resolveOverlaySwatch(@StyleRes overlayStyleRes: Int): ThemeColorSwatch {
    val fillAttr = com.google.android.material.R.attr.colorPrimaryFixedDim
    val onFillAttr = com.google.android.material.R.attr.colorOnPrimaryFixed
    // obtainStyledAttributes 要求属性数组按 ID 升序，排序后再按 ID 反查下标
    val attrs = intArrayOf(fillAttr, onFillAttr).sortedArray()
    val typedArray = obtainStyledAttributes(overlayStyleRes, attrs)
    return try {
        ThemeColorSwatch(
            fill = typedArray.getColor(attrs.indexOf(fillAttr), Color.TRANSPARENT),
            onFill = typedArray.getColor(attrs.indexOf(onFillAttr), Color.BLACK),
        )
    } finally {
        typedArray.recycle()
    }
}

@StyleRes
internal fun AppSettings.darkPureBlackOverlayStyleResOrNull(uiMode: Int): Int? {
    val isDarkMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return if (darkModePureBlack && isDarkMode) {
        R.style.ThemeOverlay_BiliTools_DarkPureBlack
    } else {
        null
    }
}

/**
 * 给 Activity 启动窗口及仍使用 View 的平台组件叠加当前配色。
 * Compose 内容统一由 `BiliToolsTheme` 直接观察 AppSettings，不再比较 Activity 主题快照。
 */
internal fun AppCompatActivity.applySettingsThemeOverlays() {
    val settings = applicationContext.appContainer.settingsRepository.currentSettings()
    if (settings.themeColor == AppThemeColor.Dynamic) {
        DynamicColors.applyToActivityIfAvailable(this)
    } else {
        settings.themeColor.overlayStyleResOrNull()?.let { theme.applyStyle(it, true) }
    }
    settings.darkPureBlackOverlayStyleResOrNull(resources.configuration.uiMode)
        ?.let { theme.applyStyle(it, true) }
}
