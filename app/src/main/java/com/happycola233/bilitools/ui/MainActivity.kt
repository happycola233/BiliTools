package com.happycola233.bilitools.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.databinding.ActivityMainBinding
import com.happycola233.bilitools.ui.downloads.DownloadsFragment
import com.happycola233.bilitools.ui.login.LoginFragment
import com.happycola233.bilitools.ui.parse.ParseFragment
import com.happycola233.bilitools.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        applyThemeColorOverlay()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle edge-to-edge manually
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            binding.appBar.updatePadding(top = insets.top)
            binding.bottomNav.updatePadding(bottom = insets.bottom)

            // Adjust ViewPager padding to avoid content being covered by the bottom nav
            // 80dp is roughly the height of bottom nav, plus the system bottom inset
            val basePadding = (80 * resources.displayMetrics.density).toInt()
            binding.viewPager.updatePadding(bottom = basePadding + insets.bottom)

            windowInsets
        }

        val pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1 // Keep adjacent page only to improve cold start
        binding.viewPager.isUserInputEnabled = false // Disable swipe if desired, or keep true

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val menuId = when (position) {
                    0 -> R.id.parseFragment
                    1 -> R.id.downloadsFragment
                    2 -> R.id.loginFragment
                    3 -> R.id.settingsFragment
                    else -> R.id.parseFragment
                }
                binding.bottomNav.menu.findItem(menuId).isChecked = true
                
                // Update title
                binding.collapsingToolbar.title = when (position) {
                    0 -> getString(R.string.app_name)
                    1 -> getString(R.string.nav_downloads)
                    2 -> getString(R.string.nav_login)
                    3 -> getString(R.string.nav_settings)
                    else -> getString(R.string.app_name)
                }
            }
        })

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.parseFragment -> binding.viewPager.setCurrentItem(0, false)
                R.id.downloadsFragment -> binding.viewPager.setCurrentItem(1, false)
                R.id.loginFragment -> binding.viewPager.setCurrentItem(2, false)
                R.id.settingsFragment -> binding.viewPager.setCurrentItem(3, false)
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

        handleOpenDownloadsIntent(intent)
        handleExternalDownloadIntent(intent)
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

    private fun applyThemeColorOverlay() {
        val settings = applicationContext
            .appContainer
            .settingsRepository
            .currentSettings()
        settings.themeColor.overlayStyleResOrNull()?.let { theme.applyStyle(it, true) }
        settings.darkPureBlackOverlayStyleResOrNull(resources.configuration.uiMode)
            ?.let { theme.applyStyle(it, true) }
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "extra_open_downloads"
    }
}

class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): androidx.fragment.app.Fragment {
        return when (position) {
            // Use factory so main mode and external mode share the same fragment class.
            0 -> ParseFragment.newInstance()
            1 -> DownloadsFragment()
            2 -> LoginFragment()
            3 -> SettingsFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
