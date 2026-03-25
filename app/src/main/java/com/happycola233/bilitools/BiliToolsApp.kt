package com.happycola233.bilitools

import android.app.Application
import android.app.Activity
import android.os.Bundle
import com.happycola233.bilitools.core.AppContainer
import com.happycola233.bilitools.data.AppThemeColor
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

class BiliToolsApp : Application(), Application.ActivityLifecycleCallbacks {
    val container: AppContainer by lazy { AppContainer(this) }
    @Volatile
    private var startedActivityCount: Int = 0

    val isAppInForeground: Boolean
        get() = startedActivityCount > 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        // Load persisted settings at startup (including theme mode).
        val settingsRepository = container.settingsRepository
        val options = DynamicColorsOptions.Builder()
            .setPrecondition { _, _ ->
                settingsRepository.currentSettings().themeColor == AppThemeColor.Dynamic
            }
            .build()
        DynamicColors.applyToActivitiesIfAvailable(this, options)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
