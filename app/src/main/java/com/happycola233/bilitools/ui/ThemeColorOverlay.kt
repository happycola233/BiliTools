package com.happycola233.bilitools.ui

import android.content.res.Configuration
import androidx.annotation.StyleRes
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppSettings

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
