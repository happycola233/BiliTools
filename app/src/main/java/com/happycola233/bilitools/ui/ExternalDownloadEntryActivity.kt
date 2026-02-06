package com.happycola233.bilitools.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.commitNow
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.ui.parse.ParseFragment
import com.google.android.material.appbar.MaterialToolbar

/**
 * Public entry for external apps that want to hand over a URL to BiliTools.
 *
 * This activity is exported and uses a dialog/translucent theme so users can complete
 * "download & export" without a full-screen jump to MainActivity.
 */
class ExternalDownloadEntryActivity : AppCompatActivity(), ParseFragment.ExternalDownloadHost {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep visual style aligned with app settings (theme color/pure black) before inflate.
        applyThemeOverlays()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_external_download_entry)
        // Tap outside or toolbar back closes this transient UI and returns to source app.
        findViewById<View>(R.id.touch_outside).setOnClickListener { finish() }
        findViewById<MaterialToolbar>(R.id.external_toolbar).setNavigationOnClickListener { finish() }

        // Always host ParseFragment in external mode; it owns parse/load/download logic.
        ensureParseFragment()
        if (savedInstanceState == null) {
            dispatchExternalUrl(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchExternalUrl(intent)
    }

    override fun onExternalDownloadQueued() {
        // ParseFragment notifies when at least one download task is enqueued.
        // Close immediately so user lands back in caller app while download continues in background.
        finish()
    }

    private fun ensureParseFragment() {
        if (supportFragmentManager.findFragmentByTag(PARSE_TAG) != null) return
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            replace(
                R.id.external_parse_container,
                ParseFragment.newInstance(externalMode = true),
                PARSE_TAG,
            )
        }
    }

    private fun dispatchExternalUrl(sourceIntent: Intent?) {
        // Normalize/extract a URL from ACTION_VIEW or ACTION_SEND payload.
        val url = sourceIntent?.extractExternalDownloadUrl()
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        // Push URL into fragment via FragmentResult so we can reuse existing ParseFragment pipeline.
        supportFragmentManager.setFragmentResult(
            ExternalDownloadContract.RESULT_KEY,
            bundleOf(ExternalDownloadContract.RESULT_URL to url),
        )
    }

    private fun applyThemeOverlays() {
        val settings = applicationContext
            .appContainer
            .settingsRepository
            .currentSettings()
        settings.themeColor.overlayStyleResOrNull()?.let { theme.applyStyle(it, true) }
        settings.darkPureBlackOverlayStyleResOrNull(resources.configuration.uiMode)
            ?.let { theme.applyStyle(it, true) }
    }

    companion object {
        // Stable tag to avoid adding duplicate fragment instances after configuration changes.
        private const val PARSE_TAG = "external_parse"
    }
}
