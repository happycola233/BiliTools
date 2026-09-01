package com.happycola233.bilitools.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.doOnPreDraw
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.ui.downloads.DownloadsViewModel
import com.happycola233.bilitools.ui.login.LoginViewModel
import com.happycola233.bilitools.ui.parse.ParseViewModel
import com.happycola233.bilitools.ui.theme.BiliToolsTheme

class MainActivity : AppCompatActivity() {
    private val parseViewModel: ParseViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }
    private val downloadsViewModel: DownloadsViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }
    private val loginViewModel: LoginViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }

    private val selectedTabIndex = mutableIntStateOf(MAIN_TAB_PARSE)
    private var mainContentView: View? = null
    private var launchFlashGuard: View? = null
    private val launchFlashGuardTimeoutRunnable = Runnable { removeLaunchFlashGuard() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动页背景色必须在 installSplashScreen() 把主题切到 postSplashScreenTheme 之前读取。
        val splashBackgroundColor = resolveSplashScreenBackgroundColor()
        val splashScreen = installSplashScreen()
        val playLaunchSplashAnimation = savedInstanceState == null &&
            applicationContext.appContainer.settingsRepository
                .currentSettings().launchSplashAnimationEnabled
        if (playLaunchSplashAnimation) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                MainLaunchSplashAnimator.play(
                    splashScreenView = splashScreenView,
                    contentView = mainContentView,
                )
                releaseLaunchFlashGuardAfterSplashDrawn()
            }
        }

        enableBiliEdgeToEdge()
        applySettingsThemeOverlays()
        super.onCreate(savedInstanceState)

        selectedTabIndex.intValue = savedInstanceState
            ?.getInt(STATE_SELECTED_TAB, MAIN_TAB_PARSE)
            ?: MAIN_TAB_PARSE
        handleOpenDownloadsIntent(intent)
        handleExternalDownloadIntent(intent)

        setContent {
            val settings by applicationContext.appContainer.settingsRepository
                .settings.collectAsState()
            // Tab 状态通过稳定 provider 下沉到真正使用它的子组合，切页时不让整棵主壳
            // 连同玻璃效果一起重组；页面宿主与两种底栏各自只刷新自己的最小范围。
            val selectedTabIndexProvider = remember { { selectedTabIndex.intValue } }
            val selectTab = remember { { index: Int -> selectedTabIndex.intValue = index } }
            BiliToolsTheme(settings = settings) {
                MainScreen(
                    activity = this@MainActivity,
                    checkForUpdates = savedInstanceState == null,
                    settings = settings,
                    selectedTabIndex = selectedTabIndexProvider,
                    onTabSelected = selectTab,
                    parseViewModel = parseViewModel,
                    downloadsViewModel = downloadsViewModel,
                    loginViewModel = loginViewModel,
                    onOpenParseUrl = ::openParseUrl,
                )
            }
        }
        mainContentView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

        if (playLaunchSplashAnimation) {
            installLaunchFlashGuard(splashBackgroundColor)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenDownloadsIntent(intent)
        handleExternalDownloadIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_TAB, selectedTabIndex.intValue)
        super.onSaveInstanceState(outState)
    }

    private fun handleOpenDownloadsIntent(sourceIntent: Intent?) {
        if (sourceIntent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) != true) return
        selectedTabIndex.intValue = MAIN_TAB_DOWNLOADS
        sourceIntent.removeExtra(EXTRA_OPEN_DOWNLOADS)
    }

    private fun handleExternalDownloadIntent(sourceIntent: Intent?) {
        val url = normalizeHttpUrl(
            sourceIntent?.getStringExtra(ExternalDownloadContract.EXTRA_URL),
        ) ?: return
        openParseUrl(url)
        sourceIntent?.removeExtra(ExternalDownloadContract.EXTRA_URL)
    }

    private fun openParseUrl(url: String) {
        parseViewModel.submitExternalUrl(url)
        selectedTabIndex.intValue = MAIN_TAB_PARSE
    }

    /**
     * 读取启动页主题（Theme.BiliTools.Splash）声明的 windowSplashScreenBackground。
     * 该属性只存在于启动页主题上，installSplashScreen() 切换主题后便无法再解析。
     */
    private fun resolveSplashScreenBackgroundColor(): Int? {
        val typedValue = TypedValue()
        val resolved = theme.resolveAttribute(
            androidx.core.splashscreen.R.attr.windowSplashScreenBackground,
            typedValue,
            true,
        )
        if (!resolved) return null
        return if (
            typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        ) {
            typedValue.data
        } else {
            null
        }
    }

    /**
     * 防闪帧遮罩：Android 12+ 上注册 setOnExitAnimationListener 后，系统启动窗口移除与
     * SplashScreenView 移交到应用窗口之间存在一帧竞态。遮罩让这一帧仍显示启动页背景。
     */
    private fun installLaunchFlashGuard(backgroundColor: Int?) {
        if (backgroundColor == null) return
        val guard = View(this).apply {
            setBackgroundColor(backgroundColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        findViewById<ViewGroup>(android.R.id.content).addView(
            guard,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        launchFlashGuard = guard
        guard.doOnPreDraw {
            guard.postDelayed(
                launchFlashGuardTimeoutRunnable,
                LAUNCH_FLASH_GUARD_TIMEOUT_MILLIS,
            )
        }
    }

    /** 退场回调已拿到 SplashScreenView，等两帧确保其盖住内容后再撤掉遮罩。 */
    private fun releaseLaunchFlashGuardAfterSplashDrawn() {
        if (launchFlashGuard == null) return
        val decorView = window.decorView
        decorView.postOnAnimation {
            decorView.postOnAnimation { removeLaunchFlashGuard() }
        }
    }

    private fun removeLaunchFlashGuard() {
        val guard = launchFlashGuard ?: return
        launchFlashGuard = null
        guard.removeCallbacks(launchFlashGuardTimeoutRunnable)
        (guard.parent as? ViewGroup)?.removeView(guard)
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "extra_open_downloads"
        private const val STATE_SELECTED_TAB = "main_selected_tab"
        private const val MAIN_TAB_PARSE = 0
        private const val MAIN_TAB_DOWNLOADS = 1
        private const val LAUNCH_FLASH_GUARD_TIMEOUT_MILLIS = 400L
    }
}
