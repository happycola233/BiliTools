package com.happycola233.bilitools.ui.settings

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import com.happycola233.bilitools.BiliToolsApp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLanguage
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allLanguagesCanBeSelectedInLightTheme() = verifyLanguageOptions(AppThemeMode.Light)

    @Test fun allLanguagesCanBeSelectedInDarkTheme() = verifyLanguageOptions(AppThemeMode.Dark)

    @Test fun languageListSupportsRightToLeftAndLargeText() =
        verifyLanguageOptions(AppThemeMode.Dark, rtl = true, fontScale = 1.3f)

    @Test
    fun languageNavigationKeepsGeneralAsItsParent() {
        val container = (RuntimeEnvironment.getApplication() as BiliToolsApp).container
        val viewModel = SettingsViewModel(container.settingsRepository, container.issueReportRepository)
        viewModel.navigateTo(SettingsDestination.General)
        viewModel.navigateTo(SettingsDestination.Language)
        assertEquals(
            listOf(SettingsDestination.Main, SettingsDestination.General, SettingsDestination.Language),
            viewModel.backStack.toList(),
        )
        viewModel.popDestination()
        assertEquals(SettingsDestination.General, viewModel.backStack.last())
    }

    private fun verifyLanguageOptions(mode: AppThemeMode, rtl: Boolean = false, fontScale: Float = 1f) {
        val app = RuntimeEnvironment.getApplication() as BiliToolsApp
        val configuration = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(if (rtl) "ar" else "zh-Hans"))
            this.fontScale = fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (mode == AppThemeMode.Dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val languageContext = ContextThemeWrapper(app.createConfigurationContext(configuration), R.style.Theme_BiliTools)
        var selected = AppLanguage.System
        compose.setContent {
            var currentLanguage by remember { mutableStateOf(AppLanguage.System) }
            CompositionLocalProvider(
                LocalContext provides languageContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                BiliToolsTheme(AppSettings(themeMode = mode)) {
                    LanguageSettingsScreen(
                        selectedLanguage = currentLanguage,
                        onLanguageChange = { selected = it; currentLanguage = it },
                        onBack = {},
                    )
                }
            }
        }
        compose.onNodeWithText(AppLanguage.System.displayName(languageContext)).assertIsSelected()
        AppLanguage.entries.drop(1).forEach { language ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(language.displayName(languageContext)))
            compose.onNodeWithText(language.displayName(languageContext)).performClick()
            compose.onNodeWithText(language.displayName(languageContext)).assertIsSelected()
            compose.runOnIdle { assertEquals(language, selected) }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(AppLanguage.System.displayName(languageContext)))
        compose.onNodeWithText(AppLanguage.System.displayName(languageContext)).performClick()
        compose.onNodeWithText(AppLanguage.System.displayName(languageContext)).assertIsSelected()
        compose.runOnIdle { assertEquals(AppLanguage.System, selected) }

        val output = File("../.tmp/locale/language-${mode.name.lowercase()}-${if (rtl) "rtl" else "ltr"}.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
