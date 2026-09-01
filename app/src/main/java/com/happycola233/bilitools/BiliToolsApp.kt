package com.happycola233.bilitools

import android.app.Application
import android.app.Activity
import android.os.Bundle
import com.happycola233.bilitools.core.AppLog
import com.happycola233.bilitools.core.AppContainer
import com.happycola233.bilitools.data.AppThemeColor
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import kotlinx.coroutines.runBlocking

class BiliToolsApp : Application(), Application.ActivityLifecycleCallbacks {
    val container: AppContainer by lazy { AppContainer(this) }
    @Volatile
    private var startedActivityCount: Int = 0
    private var previousUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    val isAppInForeground: Boolean
        get() = startedActivityCount > 0

    override fun onCreate() {
        super.onCreate()
        AppLog.install(container.diagnosticLogStore)
        container.updatePackageCleanupManager.cleanupAfterAppUpdateIfNeeded()
        registerActivityLifecycleCallbacks(this)
        // Load persisted settings at startup (including theme mode).
        val settingsRepository = container.settingsRepository
        if (settingsRepository.currentSettings().issueReportDetailedLoggingEnabled) {
            AppLog.startNewDiagnosticSession("Application created")
        }
        AppLog.i(TAG, "[lifecycle] application created")
        installUncaughtExceptionLogging()
        val options = DynamicColorsOptions.Builder()
            .setPrecondition { _, _ ->
                settingsRepository.currentSettings().themeColor == AppThemeColor.Dynamic
            }
            .build()
        DynamicColors.applyToActivitiesIfAvailable(this, options)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        AppLog.d(
            TAG,
            "[lifecycle] activity created=${activity.localClassName}, restored=${savedInstanceState != null}",
        )
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
        AppLog.d(
            TAG,
            "[lifecycle] activity started=${activity.localClassName}, startedCount=$startedActivityCount",
        )
    }

    override fun onActivityResumed(activity: Activity) {
        AppLog.d(TAG, "[lifecycle] activity resumed=${activity.localClassName}")
    }

    override fun onActivityPaused(activity: Activity) {
        AppLog.d(TAG, "[lifecycle] activity paused=${activity.localClassName}")
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        AppLog.d(
            TAG,
            "[lifecycle] activity stopped=${activity.localClassName}, startedCount=$startedActivityCount",
        )
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        AppLog.d(TAG, "[lifecycle] activity saveInstanceState=${activity.localClassName}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        AppLog.d(TAG, "[lifecycle] activity destroyed=${activity.localClassName}")
    }

    private fun installUncaughtExceptionLogging() {
        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e(
                TAG,
                "[crash] uncaught exception on thread=${thread.name}",
                throwable,
            )
            runCatching {
                runBlocking {
                    AppLog.flushDiagnosticLogs()
                }
            }
            previousUncaughtExceptionHandler?.uncaughtException(thread, throwable)
                ?: run {
                    throw throwable
                }
        }
    }

    companion object {
        private const val TAG = "BiliToolsApp"
    }
}
