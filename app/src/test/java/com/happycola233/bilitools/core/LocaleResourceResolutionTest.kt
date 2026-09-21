package com.happycola233.bilitools.core

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import com.happycola233.bilitools.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 验证打包后的 Android 资源选择，XML 覆盖检查无法发现脚本、地区及语言优先级选错。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], qualifiers = "en-rUS", application = Application::class)
class LocaleResourceResolutionTest {
    private fun context(languageTags: String): Context {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(languageTags))
        }
        return base.createConfigurationContext(configuration)
    }

    @Test
    fun allThirteenLanguagesResolveTheirOwnCoreTextAndWritingDirection() {
        val expected = listOf(
            Triple("zh-Hans", "语言", "下载任务"),
            Triple("zh-Hant", "語言", "下載任務"),
            Triple("en", "Language", "Downloads"),
            Triple("ja", "言語", "ダウンロード"),
            Triple("es", "Idioma", "Descargas"),
            Triple("pt", "Idioma", "Transferências"),
            Triple("ar", "اللغة", "التنزيلات"),
            Triple("ru", "Язык", "Загрузки"),
            Triple("tr", "Dil", "İndirilenler"),
            Triple("th", "ภาษา", "ดาวน์โหลด"),
            Triple("ms", "Bahasa", "Muat Turun"),
            Triple("vi", "Ngôn ngữ", "Tải xuống"),
            Triple("id", "Bahasa", "Unduhan"),
        )
        val chineseLoginPrompt = context("zh-Hans").getString(R.string.login_status_idle)
        expected.forEach { (tag, languageTitle, downloadsTitle) ->
            val localized = context(tag)
            assertEquals("Language title for $tag", languageTitle, localized.getString(R.string.settings_language_title))
            assertEquals("Downloads title for $tag", downloadsTitle, localized.getString(R.string.downloads_title))
            if (tag != "zh-Hans") {
                assertNotEquals("Login prompt fell back to simplified Chinese: $tag", chineseLoginPrompt, localized.getString(R.string.login_status_idle))
            }
            assertEquals(
                "Writing direction for $tag",
                if (tag == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                localized.resources.configuration.layoutDirection,
            )
        }
    }

    @Test
    fun chineseRegionsResolveTheCorrectScriptWithoutAnExplicitScriptTag() {
        listOf("zh-CN", "zh-SG").forEach { tag ->
            val localized = context(tag)
            assertEquals(tag, "语言", localized.getString(R.string.settings_language_title))
            assertEquals(tag, "跟随系统", localized.getString(R.string.settings_language_system))
        }
        listOf("zh-TW", "zh-HK", "zh-MO").forEach { tag ->
            val localized = context(tag)
            assertEquals(tag, "語言", localized.getString(R.string.settings_language_title))
            assertEquals(tag, "跟隨系統", localized.getString(R.string.settings_language_system))
        }
    }

    @Test
    fun systemLanguageListUsesTheFirstSupportedLanguageRatherThanTheDefaultResource() {
        val expected = mapOf(
            "zh-Hans,en" to "语言",
            "en,zh-Hans" to "Language",
            "zh-CN,en-US" to "语言",
            "zh-HK,en-US" to "語言",
            "fr-FR,zh-Hans,en" to "语言",
            "fr-FR,en-US,zh-Hans" to "Language",
        )
        expected.forEach { (languageTags, title) ->
            assertEquals(languageTags, title, context(languageTags).getString(R.string.settings_language_title))
        }
    }
}
