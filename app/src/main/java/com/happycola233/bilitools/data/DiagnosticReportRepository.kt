package com.happycola233.bilitools.data

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.webkit.WebView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.happycola233.bilitools.core.*
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PreparedDiagnosticReport(val report: DiagnosticReport, val file: File, val uri: Uri)

class DiagnosticReportRepository(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val diagnosticLogStore: DiagnosticLogStore,
    private val cookieStore: CookieStore,
    private val exitHistory: DiagnosticExitHistory,
    private val groups: () -> List<DownloadGroup>,
    private val accountSummary: () -> Map<String, String>,
) {
    private val appContext = context.applicationContext
    private val reportDirectory = File(appContext.cacheDir, "diagnostic-reports")
    private val mutex = Mutex()

    suspend fun prepare(description: String = "", focusTaskId: Long? = null): PreparedDiagnosticReport =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val report = DiagnosticReportBuilder.build(collectSnapshot(focusTaskId), description, diagnosticLogStore.redactor)
                clearCachedReports()
                val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).format(Instant.now().atZone(ZoneId.systemDefault()))
                val file = File(reportDirectory, "BiliTools-report-$timestamp.txt")
                file.writeText(report.text)
                PreparedDiagnosticReport(report, file, FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file))
            }
        }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            diagnosticLogStore.clear()
            clearCachedReports()
        }
    }

    fun issueUrl(): String {
        @Suppress("DEPRECATION")
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        return Uri.parse("https://github.com/happycola233/BiliTools/issues/new").buildUpon()
            .appendQueryParameter("template", "bug_report.yml")
            .appendQueryParameter("version", "${info.versionName} (${info.longVersionCode})")
            .appendQueryParameter("device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            .build().toString()
    }

    private fun clearCachedReports() {
        reportDirectory.listFiles()?.forEach { if (!it.delete()) throw java.io.IOException("Unable to remove cached diagnostic report") }
        reportDirectory.mkdirs()
    }

    private suspend fun collectSnapshot(focusTaskId: Long?): DiagnosticReportSnapshot {
        @Suppress("DEPRECATION")
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val settings = settingsRepository.currentSettings()
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val links = network?.let(connectivity::getLinkProperties)
        val manager = appContext.getSystemService(ActivityManager::class.java)
        val power = appContext.getSystemService(PowerManager::class.java)
        val usage = appContext.getSystemService(UsageStatsManager::class.java)
        val webView = WebView.getCurrentWebViewPackage()
        val allGroups = groups()
        fun taskSnapshot(task: DownloadItem, group: DownloadGroup, includeDetails: Boolean = false) = DiagnosticTaskSnapshot(
            id = task.id,
            summary = "taskId=${task.id} contentId=${group.bvid ?: task.embeddedMetadata?.subtitleAid ?: task.embeddedMetadata?.musicSid} type=${task.taskType} status=${task.status} failure=${task.failureMessage ?: task.errorMessage} quality=${task.mediaParams?.resolutionId} codec=${task.mediaParams?.codecType} audio=${task.mediaParams?.audioQualityId} bytes=${task.downloadedBytes}/${task.totalBytes}",
            fields = if (includeDetails) DiagnosticReportBuilder.fieldsOf(task) else emptyMap(),
            groupFields = if (includeDetails) DiagnosticReportBuilder.fieldsOf(group) else emptyMap(),
        )
        val focusGroup = allGroups.firstOrNull { group -> group.tasks.any { it.id == focusTaskId } }
        val tasks = allGroups.flatMap { group -> group.tasks.map { it to group } }
            .filter { (task, _) -> task.status in setOf(DownloadStatus.Failed, DownloadStatus.Paused, DownloadStatus.Pending, DownloadStatus.Running, DownloadStatus.Merging) }
            .sortedWith(compareBy<Pair<DownloadItem, DownloadGroup>> { if (it.first.status == DownloadStatus.Failed) 0 else 1 }.thenByDescending { it.first.createdAt })
            .take(20).map { (task, group) -> taskSnapshot(task, group) }
        return DiagnosticReportSnapshot(
            summary = linkedMapOf(
                "version" to "${info.versionName} (${info.longVersionCode})",
                "debuggable" to ((appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0).toString(),
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                "buildDisplay" to Build.DISPLAY,
                "installedAbi" to when (val directory = File(appContext.applicationInfo.nativeLibraryDir).name) {
                    "arm64" -> "arm64-v8a"
                    "arm" -> "armeabi-v7a"
                    else -> directory
                },
                "language" to appContext.localizedContext().resources.configuration.locales[0].toLanguageTag(),
                "timezone" to ZoneId.systemDefault().id,
            ),
            account = linkedMapOf("loggedIn" to cookieStore.isLoggedIn().toString()) + accountSummary(),
            environment = linkedMapOf(
                "network" to listOfNotNull(
                    "wifi".takeIf { capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true },
                    "cellular".takeIf { capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true },
                    "ethernet".takeIf { capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true },
                ).joinToString().ifEmpty { if (network == null) "offline" else "other" },
                "validated" to (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true).toString(),
                "vpn" to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true).toString(),
                "metered" to connectivity.isActiveNetworkMetered.toString(),
                "restrictBackgroundStatus" to connectivity.restrictBackgroundStatus.toString(),
                "privateDnsActive" to links?.isPrivateDnsActive.toString(),
                "notificationsAllowed" to NotificationManagerCompat.from(appContext).areNotificationsEnabled().toString(),
                "ignoringBatteryOptimizations" to power.isIgnoringBatteryOptimizations(appContext.packageName).toString(),
                "backgroundRestricted" to manager.isBackgroundRestricted.toString(),
                "standbyBucket" to usage.appStandbyBucket.toString(),
                "internalFreeBytes" to appContext.filesDir.usableSpace.toString(),
                "externalFreeBytes" to Environment.getExternalStorageDirectory().usableSpace.toString(),
                "downloadRoot" to settings.downloadRootRelativePath,
                "webView" to (webView?.let { "${it.packageName} ${it.versionName}" } ?: "unavailable"),
            ),
            exits = exitHistory.recent(includeTrace = true),
            tasks = tasks,
            settings = settings,
            logs = diagnosticLogStore.snapshots(),
            focus = focusGroup?.let { group -> taskSnapshot(group.tasks.first { it.id == focusTaskId }, group, includeDetails = true) },
        )
    }
}
