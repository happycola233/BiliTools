package com.happycola233.bilitools.ui.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.happycola233.bilitools.BiliToolsApp
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(sdk = [33, 35])
    fun switchesRemainIndependentInLightTheme() = verifySwitches(AppThemeMode.Light)
    @Test fun switchesRemainIndependentInPureBlackTheme() = verifySwitches(AppThemeMode.Dark)

    @Test fun existingBottomBarPreferenceDoesNotDisableNewPanels() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("liquid_bottom_tabs_enabled", false)
            .apply()
        val settings = SettingsRepository(app).currentSettings()
        assertFalse(settings.liquidBottomTabsEnabled)
        assertTrue(settings.liquidGlassPanelsEnabled)
    }

    @Test
    @Config(sdk = [29, 32])
    fun unsupportedDevicesKeepPreferencesAndHideBottomBarWidth() {
        val repository = showSettings(AppThemeMode.Light)
        compose.onNodeWithText("液态玻璃面板").performScrollTo()
        compose.onNodeWithText("当前设备暂不支持液态玻璃，已自动使用 Material 风格面板。").assertExists()
        compose.onNodeWithText("液态底栏宽度").assertDoesNotExist()
        assertTrue(repository.currentSettings().liquidBottomTabsEnabled)
        assertTrue(repository.currentSettings().liquidGlassPanelsEnabled)
    }

    private fun verifySwitches(mode: AppThemeMode) {
        val repository = showSettings(mode)
        assertTrue(repository.currentSettings().liquidBottomTabsEnabled)
        assertTrue(repository.currentSettings().liquidGlassPanelsEnabled)
        toggle("液态玻璃面板")
        assertFalse(repository.currentSettings().liquidGlassPanelsEnabled)
        assertTrue(repository.currentSettings().liquidBottomTabsEnabled)
        compose.onNodeWithText("液态底栏宽度").assertExists()
        toggle("液态玻璃底栏")
        compose.onNodeWithText("液态底栏宽度").assertDoesNotExist()
        toggle("液态玻璃面板")
        assertTrue(repository.currentSettings().liquidGlassPanelsEnabled)
        assertFalse(repository.currentSettings().liquidBottomTabsEnabled)
        toggle("液态玻璃面板")
        val restored = SettingsRepository(RuntimeEnvironment.getApplication()).currentSettings()
        assertFalse(restored.liquidBottomTabsEnabled)
        assertFalse(restored.liquidGlassPanelsEnabled)
        val output = File("../.tmp/glass-panels/appearance-${mode.name.lowercase()}.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun showSettings(mode: AppThemeMode): SettingsRepository {
        val app = RuntimeEnvironment.getApplication() as BiliToolsApp
        val repository = app.container.settingsRepository
        compose.setContent {
            val attachInfo = ReflectionHelpers.getField<Any>(LocalView.current, "mAttachInfo")
            ReflectionHelpers.setField(attachInfo, "mHardwareAccelerated", true)
            val savedSettings by repository.settings.collectAsState()
            val settings = savedSettings.copy(themeMode = mode)
            BiliToolsTheme(settings) {
                AppearanceSettingsScreen(
                    settings = settings,
                    onThemeModeChange = {},
                    onDynamicColorEnabledChange = {},
                    onThemeColorChange = {},
                    onBlackThemeChange = {},
                    onLiquidBottomTabsChange = repository::setLiquidBottomTabsEnabled,
                    onLiquidGlassPanelsChange = repository::setLiquidGlassPanelsEnabled,
                    onLiquidBarWidthChange = repository::setLiquidBarWidthFraction,
                    onGlassDebugChange = {},
                    onBack = {},
                )
            }
        }
        return repository
    }

    private fun toggle(title: String) {
        compose.onNodeWithText(title).performScrollTo()
        compose.onNodeWithText(title, useUnmergedTree = true)
            .onParent().onChildren().filterToOne(isToggleable()).performClick()
    }
}
