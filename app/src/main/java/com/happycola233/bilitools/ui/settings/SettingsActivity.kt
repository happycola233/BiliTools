package com.happycola233.bilitools.ui.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.happycola233.bilitools.R
import com.happycola233.bilitools.databinding.ActivitySettingsBinding
import com.happycola233.bilitools.ui.applySettingsThemeOverlays

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        applySettingsThemeOverlays()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupToolbar()
    }

    private fun setupInsets() {
        val contentBaseBottom = binding.settingsFragmentContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsAppBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.settingsFragmentContainer.updatePadding(bottom = contentBaseBottom + bars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        binding.settingsToolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back_24)
        binding.settingsToolbar.setNavigationOnClickListener { finish() }
    }
}
