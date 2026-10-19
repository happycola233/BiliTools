package com.happycola233.bilitools.data

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class DownloadRootSafetyTest {
    private lateinit var settings: SettingsRepository
    private val repositories = mutableListOf<SettingsRepository>()

    @Before fun setUp() { settings = open() }
    @After fun tearDown() {
        repositories.forEach { ReflectionHelpers.getField<CoroutineScope>(it, "settingsScope").cancel() }
    }
    private fun open() = SettingsRepository(RuntimeEnvironment.getApplication()).also(repositories::add)

    @Test fun switchingRootsPersistsOldRootsAcrossRestart() {
        settings.setDownloadRootRelativePath("Download/Old")
        settings.setDownloadRootRelativePath("Download/New")
        val reopened = open()
        assertEquals("Download/New", reopened.downloadRootRelativePath())
        assertEquals("Download/Old", reopened.historicalRootFor("Download/Old/UP"))
        assertNull(reopened.historicalRootFor("Download/Old2/UP"))
    }

    @Test fun pickerRejectsOtherProvidersVolumesAndTraversal() {
        for (uri in listOf(
            "content://other.provider/tree/primary%3ADownload%2FBiliTools",
            "content://com.android.externalstorage.documents/tree/secondary%3ADownload%2FBiliTools",
            "content://com.android.externalstorage.documents/tree/primary%3ADownload%2F..%2FPersonal",
            "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
        )) assertFalse(settings.setDownloadRootFromTreeUri(Uri.parse(uri)))
        assertEquals("Download/BiliTools", settings.downloadRootRelativePath())
        assertTrue(settings.setDownloadRootFromTreeUri(
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FOld")))
        assertEquals("Download/Old", settings.downloadRootRelativePath())
    }
}
