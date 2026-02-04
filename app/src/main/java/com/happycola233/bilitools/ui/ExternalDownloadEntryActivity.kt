package com.happycola233.bilitools.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ExternalDownloadEntryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeIntent(intent)
    }

    private fun routeIntent(sourceIntent: Intent?) {
        val url = sourceIntent?.extractExternalDownloadUrl()
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        val targetIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(ExternalDownloadContract.EXTRA_URL, url)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(targetIntent)
        finish()
    }
}
