package com.happycola233.bilitools

import android.app.Application
import com.happycola233.bilitools.core.AppContainer
import com.happycola233.bilitools.data.AppThemeColor
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

class BiliToolsApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Load persisted settings at startup (including theme mode).
        val settingsRepository = container.settingsRepository
        val options = DynamicColorsOptions.Builder()
            .setPrecondition { _, _ ->
                settingsRepository.currentSettings().themeColor == AppThemeColor.Dynamic
            }
            .build()
        DynamicColors.applyToActivitiesIfAvailable(this, options)
    }
}
