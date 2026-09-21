package com.happycola233.bilitools.core

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.happycola233.bilitools.ui.downloads.formatDownloadBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalizedFormattingTest {
    private fun context(tag: String): Context {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
        return base.createConfigurationContext(configuration)
    }

    @Test
    fun byteCountsUseTheRequestedContextDecimalAndDigits() {
        assertEquals("1.5 KB", context("en").formatDownloadBytes(1536))
        assertEquals("1,5 KB", context("es").formatDownloadBytes(1536))
        assertEquals("٠ B", context("ar").formatDownloadBytes(0))
    }

    @Test
    fun estimatedTimeUsesLocalUnitsAndHandlesHourBoundary() {
        val english = context("en")
        assertEquals("1 sec", english.formatEstimatedTime(1))
        assertEquals("1 min, 1 sec", english.formatEstimatedTime(61))
        assertEquals("1 hr, 1 min", english.formatEstimatedTime(3660))
        val arabic = context("ar").formatEstimatedTime(61)
        assertTrue(arabic.any { it in '\u0600'..'\u06ff' })
        assertFalse(arabic.contains("秒"))
        assertEquals(english.formatEstimatedTime(0), english.formatEstimatedTime(-1))
    }
}
