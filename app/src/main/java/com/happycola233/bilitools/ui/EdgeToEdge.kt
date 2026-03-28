package com.happycola233.bilitools.ui

import android.content.res.Configuration
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

internal fun ComponentActivity.enableBiliEdgeToEdge() {
    enableEdgeToEdge()

    // Some ROMs still inject a light navigation-bar protection background in
    // gesture mode, which makes the bottom handle look detached in light theme.
    if (!isNightMode() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}

private fun ComponentActivity.isNightMode(): Boolean {
    return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
}
