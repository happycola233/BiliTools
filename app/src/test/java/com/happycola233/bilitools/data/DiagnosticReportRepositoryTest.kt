package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.DiagnosticExitHistory
import com.happycola233.bilitools.core.DiagnosticLogStore
import com.happycola233.bilitools.core.LogRedactor
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, shadows = [DiagnosticReportFileProviderShadow::class])
class DiagnosticReportRepositoryTest {
    @Test fun createsPrivateShareableReportAndClearsOldArtifacts() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.nativeLibraryDir = File(context.filesDir, "lib/arm64").path
        val logs = DiagnosticLogStore(context, LogRedactor { "987654321" })
        logs.append(5, "test", "failure uid=987654321 SESSDATA=test-secret", null)
        val task = DownloadItem(42, 7, DownloadTaskType.AudioVideo, title = "Example", fileName = "test.mp4", url = "https://example.bilivideo.com/test.m4s?upsig=test-secret", status = DownloadStatus.Failed, progress = 0)
        val group = DownloadGroup(7, "Example", null, "BV1test", createdAt = 0, tasks = listOf(task, task.copy(id = 43, fileName = "sibling.mp4")))
        val repository = DiagnosticReportRepository(context, SettingsRepository(context), logs, CookieStore(context), DiagnosticExitHistory(context), { listOf(group) }, { emptyMap() })
        val directory = File(context.cacheDir, "diagnostic-reports").apply { mkdirs() }
        val old = File(directory, "old.txt").apply { writeText("old") }
        val prepared = repository.prepare("Cookie: test-cookie", focusTaskId = 42)
        assertFalse(old.exists())
        assertEquals(directory, prepared.file.parentFile)
        assertEquals("content", prepared.uri.scheme)
        assertEquals(prepared.report.text, prepared.file.readText())
        assertFalse(prepared.report.text.contains("test-secret"))
        assertFalse(prepared.report.text.contains("987654321"))
        assertTrue(prepared.report.text.contains("[Focus]"))
        assertTrue(prepared.report.text.contains("sibling.mp4"))
        assertTrue(repository.issueUrl().contains("template=bug_report.yml"))
        assertTrue(repository.issueUrl().contains("version="))
        assertTrue(repository.issueUrl().contains("device="))
        repository.clear()
        assertTrue(logs.snapshots().isEmpty())
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}

// FileProvider 的路径检查使用 Android 的 '/'，Windows JVM 的 canonicalPath 使用 '\\'。
// 只替换这一平台边界；报告生成、脱敏、缓存路径及清除均使用真实实现。
@Implements(FileProvider::class)
class DiagnosticReportFileProviderShadow {
    companion object {
        @JvmStatic @Implementation
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            assertEquals(File(context.cacheDir, "diagnostic-reports"), file.parentFile)
            val provider = context.packageManager.resolveContentProvider(authority, android.content.pm.PackageManager.GET_META_DATA)!!
            val paths = provider.loadXmlMetaData(context.packageManager, "android.support.FILE_PROVIDER_PATHS")!!
            var configured = false
            paths.use {
                while (it.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (it.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && it.name == "cache-path" && it.getAttributeValue(null, "path") == "diagnostic-reports/") configured = true
                }
            }
            assertTrue(configured)
            return Uri.Builder().scheme("content").authority(authority).appendPath("diagnostic_reports").appendPath(file.name).build()
        }
    }
}
