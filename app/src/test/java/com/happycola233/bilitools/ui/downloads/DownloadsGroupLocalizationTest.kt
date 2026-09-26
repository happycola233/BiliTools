package com.happycola233.bilitools.ui.downloads

import android.app.Application
import android.content.res.Configuration
import android.icu.text.Bidi
import com.happycola233.bilitools.R
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DownloadsGroupLocalizationTest {
    private fun resources(tag: String) = RuntimeEnvironment.getApplication().let { base ->
        base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }).resources
    }

    @Test fun countsSelectEnglishSpanishPortugueseAndRussianForms() {
        val en = resources("en")
        assertEquals("1 task", en.getQuantityString(R.plurals.downloads_group_task_count, 1, 1))
        assertEquals("2 tasks", en.getQuantityString(R.plurals.downloads_group_task_count, 2, 2))
        assertEquals("1 file missing", en.getQuantityString(R.plurals.downloads_group_missing_count, 1, 1))
        assertEquals("2 files missing", en.getQuantityString(R.plurals.downloads_group_missing_count, 2, 2))
        assertEquals("1 fallido", resources("es").getQuantityString(R.plurals.downloads_group_failed_count, 1, 1))
        assertEquals("Falta 1 archivo", resources("es").getQuantityString(R.plurals.downloads_group_missing_count, 1, 1))
        assertEquals("1 falhou", resources("pt").getQuantityString(R.plurals.downloads_group_failed_count, 1, 1))
        assertEquals("1 tarefa", resources("pt").getQuantityString(R.plurals.downloads_group_task_count, 1, 1))
        val ru = resources("ru")
        for ((count, noun) in listOf(1 to "задача", 2 to "задачи", 5 to "задач", 21 to "задача", 22 to "задачи", 111 to "задач")) {
            assertEquals("$count $noun", ru.getQuantityString(R.plurals.downloads_group_task_count, count, count))
        }
    }

    @Test fun arabicCompletionHasUnambiguousNumeratorAndDenominator() {
        val text = resources("ar").getString(R.string.downloads_group_resolved_summary, 4, 6)
        assertEquals("اكتمل 4 من أصل 6", text)
        // 两种数字系统都应按 RTL 阅读顺序先读已完成数，再读总数。
        for (label in listOf(text, text.replace('4', '٤').replace('6', '٦'))) {
            val bidi = Bidi().apply { setPara(label, Bidi.RTL, null) }
            val numerator = label.indexOfFirst { Character.digit(it, 10) == 4 }
            val denominator = label.indexOfFirst { Character.digit(it, 10) == 6 }
            assertTrue(bidi.getVisualIndex(numerator) > bidi.getVisualIndex(denominator))
        }
        assertFalse(text.contains('/'))
    }

    @Test fun arabicQuantitiesCoverAllSixGrammarCategories() {
        val ar = resources("ar")
        val taskForms = mapOf(0 to "لا مهام", 1 to "مهمة واحدة", 2 to "مهمتان", 3 to "3 مهام", 11 to "11 مهمة", 100 to "100 مهمة")
        for ((count, expected) in taskForms) assertEquals(expected, ar.getQuantityString(R.plurals.downloads_group_task_count, count, count))
        assertEquals("ملف واحد مفقود", ar.getQuantityString(R.plurals.downloads_group_missing_count, 1, 1))
        assertEquals("ملفان مفقودان", ar.getQuantityString(R.plurals.downloads_group_missing_count, 2, 2))
        assertEquals("فشلت مهمة واحدة", ar.getQuantityString(R.plurals.downloads_group_failed_count, 1, 1))
        assertEquals("فشلت مهمتان", ar.getQuantityString(R.plurals.downloads_group_failed_count, 2, 2))
    }
}
