package com.happycola233.bilitools.ui.theme

import android.content.res.Configuration
import com.happycola233.bilitools.data.AppThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsThemeModeTest {
    @Test
    fun systemModeUsesDarkDeviceThemeAfterAppWasForcedLight() {
        assertTrue(
            AppThemeMode.System.usesDarkTheme(
                deviceUiMode = Configuration.UI_MODE_NIGHT_YES,
            ),
        )
    }

    @Test
    fun systemModeUsesLightDeviceTheme() {
        assertFalse(
            AppThemeMode.System.usesDarkTheme(
                deviceUiMode = Configuration.UI_MODE_NIGHT_NO,
            ),
        )
    }

    @Test
    fun explicitModesIgnoreDeviceTheme() {
        assertFalse(
            AppThemeMode.Light.usesDarkTheme(
                deviceUiMode = Configuration.UI_MODE_NIGHT_YES,
            ),
        )
        assertTrue(
            AppThemeMode.Dark.usesDarkTheme(
                deviceUiMode = Configuration.UI_MODE_NIGHT_NO,
            ),
        )
    }
}
