package com.happycola233.bilitools.core

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import com.happycola233.bilitools.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29, 32, 33, 35], qualifiers = "en-rUS")
class AppLanguageTest {
    @Test
    fun languagePickerAndSystemSettingsAdvertiseTheSameThirteenLanguages() {
        val resources = RuntimeEnvironment.getApplication().resources
        val declaredTags = resources.getXml(R.xml.locales_config).use { parser ->
            buildList {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                        add(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name"))
                    }
                }
            }
        }
        assertEquals(13, declaredTags.size)
        assertEquals(AppLanguage.entries.filter { it != AppLanguage.System }.map { it.languageTag }, declaredTags)
    }

    @Test
    fun scriptAndRegionVariantsSelectTheCorrectLanguage() {
        val expected = mapOf(
            "zh-CN" to AppLanguage.SimplifiedChinese,
            "zh-SG" to AppLanguage.SimplifiedChinese,
            "zh-TW" to AppLanguage.TraditionalChinese,
            "zh-HK" to AppLanguage.TraditionalChinese,
            "zh-MO" to AppLanguage.TraditionalChinese,
            "zh-Hans" to AppLanguage.SimplifiedChinese,
            "zh-Hant" to AppLanguage.TraditionalChinese,
            "en-US" to AppLanguage.English,
            "pt-BR" to AppLanguage.Portuguese,
            "pt-PT" to AppLanguage.Portuguese,
            "id-ID" to AppLanguage.Indonesian,
        )
        expected.forEach { (tag, language) ->
            assertEquals(tag, language, AppLanguage.fromLocale(Locale.forLanguageTag(tag)))
        }
        assertEquals(AppLanguage.System, AppLanguage.fromLocale(null))
    }

    @Test
    fun languageSelectionPersistsAndReturningToSystemClearsTheOverride() {
        val app = RuntimeEnvironment.getApplication()
        Robolectric.buildActivity(LocaleTestActivity::class.java).setup().use {
            assertEquals(AppLanguage.System, AppLanguage.current())
            AppLanguage.select(AppLanguage.Arabic)
            assertEquals(AppLanguage.Arabic, AppLanguage.current())
            awaitStoredLanguage(app, "ar")
            if (Build.VERSION.SDK_INT >= 33) {
                assertEquals("ar", app.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
            } else {
                // 仓库、服务等应用级 Context 也必须使用刚选择的语言和书写方向。
                assertEquals("ar", app.localizedContext().resources.configuration.locales[0].language)
                assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, app.localizedContext().resources.configuration.layoutDirection)
            }

            AppLanguage.select(AppLanguage.System)
            assertEquals(AppLanguage.System, AppLanguage.current())
            awaitStoredLanguage(app, "")
            if (Build.VERSION.SDK_INT < 33) {
                assertEquals("en", app.localizedContext().resources.configuration.locales[0].language)
                assertEquals(android.view.View.LAYOUT_DIRECTION_LTR, app.localizedContext().resources.configuration.layoutDirection)
            }
        }
    }

    private fun awaitStoredLanguage(context: Context, expectedTag: String) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (LocaleManagerCompat.getApplicationLocales(context).toLanguageTags() != expectedTag && System.nanoTime() < deadline) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(10)
        }
        assertEquals(expectedTag, LocaleManagerCompat.getApplicationLocales(context).toLanguageTags())
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty == expectedTag.isEmpty())
    }
}

class LocaleTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_BiliTools)
        super.onCreate(savedInstanceState)
    }
}
