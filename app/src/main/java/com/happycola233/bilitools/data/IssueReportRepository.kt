package com.happycola233.bilitools.data

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import com.happycola233.bilitools.BiliToolsApp
import com.happycola233.bilitools.core.AppLog
import com.happycola233.bilitools.core.DiagnosticLogSnapshot
import com.happycola233.bilitools.core.DiagnosticLogStats
import com.happycola233.bilitools.core.DiagnosticLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class IssueReportLogState(
    val enabled: Boolean = false,
    val loggingStartedAtMillis: Long? = null,
    val lastExportedAtMillis: Long? = null,
    val fileCount: Int = 0,
    val totalBytes: Long = 0L,
    val latestLogAtMillis: Long? = null,
)

class IssueReportRepository(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val exportRepository: ExportRepository,
    private val diagnosticLogStore: DiagnosticLogStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        buildState(
            settings = settingsRepository.currentSettings(),
            stats = diagnosticLogStore.stats(),
        ),
    )
    val state: StateFlow<IssueReportLogState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(
                settingsRepository.settings,
                diagnosticLogStore.statsFlow,
            ) { settings, stats ->
                buildState(settings, stats)
            }.collectLatest { combinedState ->
                _state.value = combinedState
            }
        }
    }

    fun refreshState() {
        _state.value = buildState(
            settings = settingsRepository.currentSettings(),
            stats = diagnosticLogStore.stats(),
        )
    }

    fun setDetailedLoggingEnabled(enabled: Boolean) {
        val current = settingsRepository.currentSettings()
        if (current.issueReportDetailedLoggingEnabled == enabled) {
            return
        }

        if (enabled) {
            settingsRepository.setIssueReportDetailedLoggingEnabled(true)
            diagnosticLogStore.startNewSession(
                reason = "Detailed issue logging enabled from About screen",
            )
            AppLog.i(TAG, "[issue-report] detailed logging enabled")
        } else {
            AppLog.i(TAG, "[issue-report] detailed logging disabled")
            settingsRepository.setIssueReportDetailedLoggingEnabled(false)
        }
    }

    suspend fun exportDetailedLogs(): android.net.Uri? {
        AppLog.i(TAG, "[issue-report] export requested")
        diagnosticLogStore.flush()

        val reportContent = buildReportText()
        val exportTimeMillis = System.currentTimeMillis()
        val fileName = buildString {
            append("BiliTools-issue-report-")
            append(FILE_NAME_TIME_FORMATTER.format(Instant.ofEpochMilli(exportTimeMillis).atZone(ZoneId.systemDefault())))
            append(".txt")
        }
        val uri = exportRepository.saveText(
            fileName = fileName,
            mimeType = "text/plain",
            content = reportContent,
            relativePath = "${Environment.DIRECTORY_DOWNLOADS}/BiliTools/IssueReports",
        )

        if (uri != null) {
            settingsRepository.setIssueReportLastExportedAt(exportTimeMillis)
            AppLog.i(TAG, "[issue-report] export completed, uri=$uri")
        }
        return uri
    }

    suspend fun clearLogs() {
        AppLog.i(TAG, "[issue-report] clear requested")
        diagnosticLogStore.clear()
        if (settingsRepository.currentSettings().issueReportDetailedLoggingEnabled) {
            diagnosticLogStore.startNewSession()
        }
    }

    private fun buildState(
        settings: AppSettings,
        stats: DiagnosticLogStats,
    ): IssueReportLogState {
        return IssueReportLogState(
            enabled = settings.issueReportDetailedLoggingEnabled,
            loggingStartedAtMillis = settings.issueReportDetailedLoggingStartedAtMillis,
            lastExportedAtMillis = settings.issueReportLastExportedAtMillis,
            fileCount = stats.fileCount,
            totalBytes = stats.totalBytes,
            latestLogAtMillis = stats.latestModifiedAtMillis,
        )
    }

    private fun buildReportText(): String {
        val generatedAtMillis = System.currentTimeMillis()
        val settings = settingsRepository.currentSettings()
        val logStats = diagnosticLogStore.stats()
        val logSnapshots = diagnosticLogStore.snapshots()
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)

        return buildString {
            appendLine("# BiliTools Issue Report")
            appendLine()
            appendLine("[Meta]")
            appendKeyValue("generatedAt", formatTimestamp(generatedAtMillis))
            appendKeyValue("timezone", ZoneId.systemDefault().id)
            appendKeyValue("locale", currentLocaleLabel())
            appendKeyValue("packageName", appContext.packageName)
            appendKeyValue("foreground", ((appContext as? BiliToolsApp)?.isAppInForeground ?: false).toString())
            appendLine()

            appendLine("[App]")
            appendKeyValue("versionName", currentVersionName())
            appendKeyValue("versionCode", currentVersionCode().toString())
            appendKeyValue("sessionId", diagnosticLogStore.currentSessionId())
            appendLine()

            appendLine("[Device]")
            appendKeyValue("manufacturer", Build.MANUFACTURER)
            appendKeyValue("brand", Build.BRAND)
            appendKeyValue("model", Build.MODEL)
            appendKeyValue("device", Build.DEVICE)
            appendKeyValue("product", Build.PRODUCT)
            appendKeyValue("sdkInt", Build.VERSION.SDK_INT.toString())
            appendKeyValue("release", Build.VERSION.RELEASE ?: "unknown")
            appendKeyValue("securityPatch", Build.VERSION.SECURITY_PATCH ?: "unknown")
            appendKeyValue("fingerprint", Build.FINGERPRINT)
            appendKeyValue("supportedAbis", Build.SUPPORTED_ABIS.joinToString())
            appendLine()

            appendLine("[Memory]")
            appendKeyValue("availMemBytes", memoryInfo.availMem.toString())
            appendKeyValue("thresholdBytes", memoryInfo.threshold.toString())
            appendKeyValue("lowMemory", memoryInfo.lowMemory.toString())
            appendKeyValue("nativeHeapAllocatedBytes", DebugBridge.nativeHeapAllocatedSize().toString())
            appendLine()

            appendLine("[Storage]")
            appendKeyValue("filesDir", appContext.filesDir.absolutePath)
            appendKeyValue("cacheDir", appContext.cacheDir.absolutePath)
            appendKeyValue("filesDirUsableBytes", appContext.filesDir.usableSpace.toString())
            appendKeyValue("cacheDirUsableBytes", appContext.cacheDir.usableSpace.toString())
            appendKeyValue("downloadRootRelativePath", settings.downloadRootRelativePath)
            appendLine()

            appendLine("[Network]")
            appendNetworkSummary()
            appendLine()

            appendLine("[Settings]")
            appendSettingsSummary(settings)
            appendLine()

            appendLine("[DiagnosticLogging]")
            appendDiagnosticSummary(settings, logStats)
            appendLine()

            appendLine("[Privacy]")
            appendLine("Cookies and authorization headers are redacted from exported network logs.")
            appendLine("The report may still contain URLs, file names, device metadata, and current app settings.")
            appendLine()

            appendLine("[Logs]")
            if (logSnapshots.isEmpty()) {
                appendLine("(no captured diagnostic log files)")
            } else {
                logSnapshots.forEach { snapshot ->
                    appendLogSnapshot(snapshot)
                }
            }
        }
    }

    private fun StringBuilder.appendSettingsSummary(settings: AppSettings) {
        appendKeyValue("addMetadata", settings.addMetadata.toString())
        appendKeyValue("themeMode", settings.themeMode.value)
        appendKeyValue("themeColor", settings.themeColor.value)
        appendKeyValue("darkModePureBlack", settings.darkModePureBlack.toString())
        appendKeyValue("confirmCellularDownload", settings.confirmCellularDownload.toString())
        appendKeyValue(
            "hideDownloadedVideosInSystemAlbum",
            settings.hideDownloadedVideosInSystemAlbum.toString(),
        )
        appendKeyValue("parseQuickActionEnabled", settings.parseQuickActionEnabled.toString())
        appendKeyValue("downloadsGlassDebugEnabled", settings.downloadsGlassDebugEnabled.toString())
        appendKeyValue(
            "downloadsGlassCornerRadiusDp",
            settings.downloadsGlassCornerRadiusDp.toString(),
        )
        appendKeyValue(
            "downloadsGlassBlurRadiusDp",
            settings.downloadsGlassBlurRadiusDp.toString(),
        )
        appendKeyValue(
            "downloadsGlassRefractionHeightDp",
            settings.downloadsGlassRefractionHeightDp.toString(),
        )
        appendKeyValue(
            "downloadsGlassRefractionAmountFrac",
            settings.downloadsGlassRefractionAmountFrac.toString(),
        )
        appendKeyValue(
            "downloadsGlassSurfaceAlpha",
            settings.downloadsGlassSurfaceAlpha.toString(),
        )
        appendKeyValue(
            "downloadsGlassChromaticAberration",
            settings.downloadsGlassChromaticAberration.toString(),
        )
        appendKeyValue("ignoredUpdateVersion", settings.ignoredUpdateVersion ?: "null")
    }

    private fun StringBuilder.appendDiagnosticSummary(
        settings: AppSettings,
        logStats: DiagnosticLogStats,
    ) {
        appendKeyValue(
            "issueReportDetailedLoggingEnabled",
            settings.issueReportDetailedLoggingEnabled.toString(),
        )
        appendKeyValue(
            "issueReportDetailedLoggingStartedAt",
            formatTimestamp(settings.issueReportDetailedLoggingStartedAtMillis),
        )
        appendKeyValue(
            "issueReportLastExportedAt",
            formatTimestamp(settings.issueReportLastExportedAtMillis),
        )
        appendKeyValue("logFileCount", logStats.fileCount.toString())
        appendKeyValue("logTotalBytes", logStats.totalBytes.toString())
        appendKeyValue("latestLogAt", formatTimestamp(logStats.latestModifiedAtMillis))
    }

    private fun StringBuilder.appendNetworkSummary() {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)

        appendKeyValue("activeNetwork", (activeNetwork != null).toString())
        appendKeyValue("isMetered", connectivityManager.isActiveNetworkMetered.toString())
        appendKeyValue(
            "transportWifi",
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI).toString(),
        )
        appendKeyValue(
            "transportCellular",
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR).toString(),
        )
        appendKeyValue(
            "transportVpn",
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN).toString(),
        )
        appendKeyValue(
            "validated",
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).toString(),
        )
        appendKeyValue(
            "notMeteredCapability",
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).toString(),
        )
    }

    private fun StringBuilder.appendLogSnapshot(snapshot: DiagnosticLogSnapshot) {
        appendLine("## ${snapshot.name}")
        appendKeyValue("sizeBytes", snapshot.sizeBytes.toString())
        appendKeyValue("lastModified", formatTimestamp(snapshot.lastModifiedAtMillis))
        appendLine()
        appendLine(snapshot.content.trimEnd())
        appendLine()
    }

    private fun StringBuilder.appendKeyValue(key: String, value: String) {
        append(key)
        append(": ")
        appendLine(value)
    }

    private fun currentVersionName(): String {
        @Suppress("DEPRECATION")
        return runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "0" }
    }

    private fun currentVersionCode(): Long {
        @Suppress("DEPRECATION")
        return runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
        }.getOrDefault(0L)
    }

    private fun currentLocaleLabel(): String {
        val locales = appContext.resources.configuration.locales
        return if (locales.isEmpty) {
            Locale.getDefault().toLanguageTag()
        } else {
            locales.get(0).toLanguageTag()
        }
    }

    private fun formatTimestamp(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) {
            return "null"
        }
        return REPORT_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    }

    private object DebugBridge {
        fun nativeHeapAllocatedSize(): Long {
            return runCatching {
                android.os.Debug.getNativeHeapAllocatedSize()
            }.getOrDefault(-1L)
        }
    }

    companion object {
        private const val TAG = "IssueReportRepository"
        private val REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss XXX",
            Locale.ROOT,
        )
        private val FILE_NAME_TIME_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmmss",
            Locale.ROOT,
        )
    }
}
