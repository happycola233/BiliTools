package com.happycola233.bilitools.ui

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.google.android.material.color.DynamicColors

internal data class ThemeSettingsSnapshot(
    val themeMode: AppThemeMode,
    val themeColor: AppThemeColor,
    val darkModePureBlack: Boolean,
    val nightModeMask: Int,
)

@StyleRes
internal fun AppThemeColor.overlayStyleResOrNull(): Int? {
    return when (this) {
        AppThemeColor.Dynamic -> null
        AppThemeColor.Coral -> R.style.ThemeOverlay_BiliTools_ColorCoral
        AppThemeColor.Rose -> R.style.ThemeOverlay_BiliTools_ColorRose
        AppThemeColor.Orchid -> R.style.ThemeOverlay_BiliTools_ColorOrchid
        AppThemeColor.Periwinkle -> R.style.ThemeOverlay_BiliTools_ColorPeriwinkle
        AppThemeColor.Sky -> R.style.ThemeOverlay_BiliTools_ColorSky
        AppThemeColor.Cyan -> R.style.ThemeOverlay_BiliTools_ColorCyan
        AppThemeColor.Turquoise -> R.style.ThemeOverlay_BiliTools_ColorTurquoise
        AppThemeColor.Leaf -> R.style.ThemeOverlay_BiliTools_ColorLeaf
        AppThemeColor.Lime -> R.style.ThemeOverlay_BiliTools_ColorLime
        AppThemeColor.Olive -> R.style.ThemeOverlay_BiliTools_ColorOlive
        AppThemeColor.Gold -> R.style.ThemeOverlay_BiliTools_ColorGold
        AppThemeColor.Apricot -> R.style.ThemeOverlay_BiliTools_ColorApricot
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

internal fun Context.currentThemeSettingsSnapshot(uiMode: Int): ThemeSettingsSnapshot {
    val settings = applicationContext.appContainer.settingsRepository.currentSettings()
    return ThemeSettingsSnapshot(
        themeMode = settings.themeMode,
        themeColor = settings.themeColor,
        darkModePureBlack = settings.darkModePureBlack,
        nightModeMask = uiMode and Configuration.UI_MODE_NIGHT_MASK,
    )
}

internal fun AppCompatActivity.applySettingsThemeOverlays(): ThemeSettingsSnapshot {
    val snapshot = applicationContext.currentThemeSettingsSnapshot(resources.configuration.uiMode)
    if (snapshot.themeColor == AppThemeColor.Dynamic) {
        DynamicColors.applyToActivityIfAvailable(this)
    } else {
        snapshot.themeColor.overlayStyleResOrNull()?.let { theme.applyStyle(it, true) }
    }
    if (snapshot.darkModePureBlack &&
        snapshot.nightModeMask == Configuration.UI_MODE_NIGHT_YES
    ) {
        theme.applyStyle(R.style.ThemeOverlay_BiliTools_DarkPureBlack, true)
    }
    return snapshot
}
