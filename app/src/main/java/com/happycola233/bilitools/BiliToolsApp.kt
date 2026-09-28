package com.happycola233.bilitools

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.happycola233.bilitools.core.AppLog
import com.happycola233.bilitools.core.AppContainer
import com.happycola233.bilitools.data.AppThemeColor
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import kotlinx.coroutines.runBlocking

class BiliToolsApp : Application(), Application.ActivityLifecycleCallbacks, SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }
    @Volatile
    private var startedActivityCount: Int = 0
    private val lifecycleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var foregroundLogged = false
    private val logBackground = Runnable {
        if (startedActivityCount == 0 && foregroundLogged) {
            foregroundLogged = false
            AppLog.i(TAG, "[lifecycle] background")
        }
    }
    private var previousUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    val isAppInForeground: Boolean
        get() = startedActivityCount > 0

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // Coil 3 默认忽略 HTTP 缓存头，显式保留原有的图片缓存与过期刷新行为。
                add(OkHttpNetworkFetcherFactory(cacheStrategy = { CacheControlCacheStrategy() }))
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLog.install(container.diagnosticLogStore)
        registerActivityLifecycleCallbacks(this)
        // Load persisted settings at startup (including theme mode).
        val settingsRepository = container.settingsRepository
        container.diagnosticExitHistory.initialize()
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val previousExit = container.diagnosticExitHistory.recent().firstOrNull()?.summary()
        AppLog.startNewDiagnosticSession("pid=${android.os.Process.myPid()} version=${packageInfo.versionName}/${packageInfo.longVersionCode} previousExit=$previousExit")
        AppLog.i(TAG, "[lifecycle] application created")
        installUncaughtExceptionLogging()
        container.updatePackageCleanupManager.cleanupAfterAppUpdateIfNeeded()
        val options = DynamicColorsOptions.Builder()
            .setPrecondition { _, _ ->
                settingsRepository.currentSettings().themeColor == AppThemeColor.Dynamic
            }
            .build()
        DynamicColors.applyToActivitiesIfAvailable(this, options)
        container.downloadNotifications.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.downloadNotifications.refresh(refreshChannels = true)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        lifecycleHandler.removeCallbacks(logBackground)
        if (!foregroundLogged) {
            foregroundLogged = true
            AppLog.i(TAG, "[lifecycle] foreground")
        }
    }

    override fun onActivityResumed(activity: Activity) {
        container.downloadNotifications.refresh()
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        // 与进程生命周期相同的短延迟，避免 Activity 切换或配置重建产生伪前后台事件。
        if (--startedActivityCount == 0 && !activity.isChangingConfigurations) lifecycleHandler.postDelayed(logBackground, 700)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun installUncaughtExceptionLogging() {
        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e(
                TAG,
                "[crash] uncaught exception on thread=${thread.name}",
                throwable,
            )
            container.diagnosticExitHistory.recordUncaughtException()
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
