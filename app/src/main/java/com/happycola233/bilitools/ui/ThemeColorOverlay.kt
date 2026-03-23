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
        AppThemeColor.Blue -> R.style.ThemeOverlay_BiliTools_ColorBlue
        AppThemeColor.Green -> R.style.ThemeOverlay_BiliTools_ColorGreen
        AppThemeColor.Orange -> R.style.ThemeOverlay_BiliTools_ColorOrange
        AppThemeColor.Pink -> R.style.ThemeOverlay_BiliTools_ColorPink
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
    snapshot.themeColor.overlayStyleResOrNull()?.let { theme.applyStyle(it, true) }
    if (snapshot.darkModePureBlack &&
        snapshot.nightModeMask == Configuration.UI_MODE_NIGHT_YES
    ) {
        theme.applyStyle(R.style.ThemeOverlay_BiliTools_DarkPureBlack, true)
    }
    return snapshot
}
