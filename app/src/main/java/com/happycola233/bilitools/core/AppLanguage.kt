package com.happycola233.bilitools.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.happycola233.bilitools.R
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 语言名称始终使用其本名，让误选语言后仍能找到熟悉的选项。 */
enum class AppLanguage(val languageTag: String, private val nativeName: String) {
    System("", ""),
    SimplifiedChinese("zh-Hans", "简体中文"),
    TraditionalChinese("zh-Hant", "繁體中文"),
    English("en", "English"),
    Japanese("ja", "日本語"),
    Spanish("es", "Español"),
    Portuguese("pt", "Português"),
    Arabic("ar", "اللغة العربية"),
    Russian("ru", "Русский"),
    Turkish("tr", "Türkçe"),
    Thai("th", "ไทย"),
    Malay("ms", "Bahasa Melayu"),
    Vietnamese("vi", "Tiếng Việt"),
    Indonesian("id", "Bahasa Indonesia"),
    ;

    fun displayName(context: Context): String =
        if (this == System) context.getString(R.string.settings_language_system) else nativeName

    companion object {
        private val localeChangeCount = MutableStateFlow(0L)
        /** 已运行的后台服务订阅此状态，切换语言不应为了刷新通知而启动新服务。 */
        val changes = localeChangeCount.asStateFlow()
        @Volatile
        internal var selectedLocalesInProcess: LocaleListCompat? = null
            private set

        fun current(): AppLanguage = fromLocale(AppCompatDelegate.getApplicationLocales()[0])

        internal fun fromLocale(locale: Locale?): AppLanguage {
            if (locale == null) return System
            return entries.firstOrNull {
                it != System && LocaleListCompat.matchesLanguageAndScript(
                    Locale.forLanguageTag(it.languageTag),
                    locale,
                )
            } ?: System
        }

        @MainThread
        fun select(language: AppLanguage) {
            // 空语言列表表示跟随系统；持久化、系统设置同步及配置分发均交给 AppCompat。
            val locales = LocaleListCompat.forLanguageTags(language.languageTag)
            selectedLocalesInProcess = locales
            AppCompatDelegate.setApplicationLocales(locales)
            localeChangeCount.value += 1
        }
    }
}

/** 应用和后台服务的 Context 在 Android 12 及以下不会自动继承 AppCompat 的语言。 */
fun Context.localizedContext(): Context {
    val selected = AppLanguage.selectedLocalesInProcess
    if (Build.VERSION.SDK_INT < 33 && selected != null) {
        // AppCompat 异步落盘；这次进程内的选择先于存储生效，让已运行服务立即获得新文案。
        val locales = if (selected.isEmpty) Resources.getSystem().configuration.locales else LocaleList.forLanguageTags(selected.toLanguageTags())
        return createConfigurationContext(Configuration(resources.configuration).apply { setLocales(locales) })
    }
    return ContextCompat.getContextForLanguage(this)
}
