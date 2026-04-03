package com.happycola233.bilitools.ui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.UpdateCheckResult
import com.happycola233.bilitools.databinding.ActivityMainBinding
import com.happycola233.bilitools.ui.downloads.DownloadsFragment
import com.happycola233.bilitools.ui.me.MeFragment
import com.happycola233.bilitools.ui.parse.ParseFragment
import com.happycola233.bilitools.ui.update.UpdateDialog
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appliedThemeSnapshot: ThemeSettingsSnapshot

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableBiliEdgeToEdge()
        appliedThemeSnapshot = applySettingsThemeOverlays()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val bottomNavBaseBottomPadding = binding.bottomNav.paddingBottom
        val viewPagerBaseBottomPadding = binding.viewPager.paddingBottom

        // Handle edge-to-edge manually.
        // AppBar gets its own listener that returns CONSUMED so the Material library's
        // internal AppBarLayout.onApplyWindowInsets() cannot re-apply top insets.
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, windowInsets ->
            val topInsets = windowInsets.getInsets(TOP_BAR_INSET_TYPES)
            v.updatePadding(top = topInsets.top)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val bottomInsets = windowInsets.getInsets(BOTTOM_BAR_INSET_TYPES)
            binding.bottomNav.updatePadding(bottom = bottomNavBaseBottomPadding + bottomInsets.bottom)
            binding.viewPager.updatePadding(bottom = viewPagerBaseBottomPadding + bottomInsets.bottom)
            windowInsets
        }
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        setContentView(binding.root)
        requestInsetsRefresh()

        val pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1 // Keep adjacent page only to improve cold start
        binding.viewPager.isUserInputEnabled = false // Disable swipe if desired, or keep true

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val menuId = when (position) {
                    0 -> R.id.parseFragment
                    1 -> R.id.downloadsFragment
                    2 -> R.id.meFragment
                    else -> R.id.parseFragment
                }
                binding.bottomNav.menu.findItem(menuId).isChecked = true

                // Update title
                binding.collapsingToolbar.title = when (position) {
                    0 -> getString(R.string.app_name)
                    1 -> getString(R.string.nav_downloads)
                    2 -> getString(R.string.nav_me)
                    else -> getString(R.string.app_name)
                }

                updateTopBarColor(position)
            }
        })

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.parseFragment -> binding.viewPager.setCurrentItem(0, false)
                R.id.downloadsFragment -> binding.viewPager.setCurrentItem(1, false)
                R.id.meFragment -> binding.viewPager.setCurrentItem(2, false)
            }
            true
        }

        // Disable long click toast on bottom navigation items
        val menuView = binding.bottomNav.getChildAt(0) as? android.view.ViewGroup
        menuView?.let {
            for (i in 0 until it.childCount) {
                it.getChildAt(i).setOnLongClickListener { true }
            }
        }

        updateTopBarColor(binding.viewPager.currentItem)
        handleOpenDownloadsIntent(intent)
        handleExternalDownloadIntent(intent)
        if (savedInstanceState == null) {
            checkForUpdatesInBackground()
        }
    }

    override fun onStart() {
        super.onStart()
        recreateIfThemeSettingsChanged()
    }

    override fun onResume() {
        super.onResume()
        requestInsetsRefresh()
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        requestInsetsRefresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenDownloadsIntent(intent)
        handleExternalDownloadIntent(intent)
    }

    private fun handleOpenDownloadsIntent(sourceIntent: Intent?) {
        if (sourceIntent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) != true) return
        binding.viewPager.setCurrentItem(1, false)
        sourceIntent.removeExtra(EXTRA_OPEN_DOWNLOADS)
    }

    private fun handleExternalDownloadIntent(sourceIntent: Intent?) {
        val url = normalizeHttpUrl(
            sourceIntent?.getStringExtra(ExternalDownloadContract.EXTRA_URL),
        ) ?: return

        binding.viewPager.setCurrentItem(0, false)
        supportFragmentManager.setFragmentResult(
            ExternalDownloadContract.RESULT_KEY,
            bundleOf(ExternalDownloadContract.RESULT_URL to url),
        )
        sourceIntent?.removeExtra(ExternalDownloadContract.EXTRA_URL)
    }

    private fun recreateIfThemeSettingsChanged() {
        val currentSnapshot = applicationContext.currentThemeSettingsSnapshot(
            resources.configuration.uiMode,
        )
        if (currentSnapshot != appliedThemeSnapshot) {
            recreate()
            return
        }
        appliedThemeSnapshot = currentSnapshot
    }

    private fun requestInsetsRefresh() {
        if (!::binding.isInitialized) return
        binding.root.post {
            if (binding.root.isAttachedToWindow) {
                ViewCompat.requestApplyInsets(binding.root)
            }
        }
    }

    private fun checkForUpdatesInBackground() {
        lifecycleScope.launch {
            when (val result = applicationContext.appContainer.updateRepository.checkForUpdate()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    if (applicationContext.appContainer.settingsRepository.shouldIgnoreUpdate(
                            result.release.versionName,
                        )
                    ) {
                        return@launch
                    }
                    UpdateDialog.show(this@MainActivity, result.release, result.currentVersion)
                }

                is UpdateCheckResult.UpToDate -> Unit
                is UpdateCheckResult.Failed -> Unit
            }
        }
    }

    private fun updateTopBarColor(position: Int) {
        val color = when (position) {
            2 -> resolveMeTopBarColor()
            else -> resolveThemeColor(com.google.android.material.R.attr.colorSurface)
        }
        binding.appBar.setBackgroundColor(color)
        binding.collapsingToolbar.setBackgroundColor(color)
        binding.collapsingToolbar.setContentScrimColor(color)
        binding.collapsingToolbar.setStatusBarScrimColor(color)
        binding.toolbar.setBackgroundColor(color)
    }

    private fun resolveMeTopBarColor(): Int {
        val surface = resolveThemeColor(com.google.android.material.R.attr.colorSurface)
        val background = resolveThemeColor(android.R.attr.colorBackground, surface)
        return if (surface == Color.BLACK && background == Color.BLACK) {
            surface
        } else {
            resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainer, surface)
        }
    }

    private fun resolveThemeColor(attr: Int, fallback: Int = Color.BLACK): Int {
        return MaterialColors.getColor(binding.root, attr, fallback)
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "extra_open_downloads"
        private val TOP_BAR_INSET_TYPES =
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        private val BOTTOM_BAR_INSET_TYPES =
            WindowInsetsCompat.Type.navigationBars()
    }
}

class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): androidx.fragment.app.Fragment {
        return when (position) {
            // Use factory so main mode and external mode share the same fragment class.
            0 -> ParseFragment.newInstance()
            1 -> DownloadsFragment()
            2 -> MeFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
