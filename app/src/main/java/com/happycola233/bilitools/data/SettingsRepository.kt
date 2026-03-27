package com.happycola233.bilitools.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppSettings(
    val addMetadata: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val themeColor: AppThemeColor = AppThemeColor.Dynamic,
    val darkModePureBlack: Boolean = false,
    val downloadRootRelativePath: String = SettingsRepository.DEFAULT_DOWNLOAD_ROOT,
    val confirmCellularDownload: Boolean = true,
    val hideDownloadedVideosInSystemAlbum: Boolean = false,
    val parseQuickActionEnabled: Boolean = true,
    val downloadsGlassDebugEnabled: Boolean = false,
    val downloadsGlassCornerRadiusDp: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CORNER_RADIUS_DP,
    val downloadsGlassBlurRadiusDp: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_BLUR_RADIUS_DP,
    val downloadsGlassRefractionHeightDp: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP,
    val downloadsGlassRefractionAmountFrac: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC,
    val downloadsGlassSurfaceAlpha: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_SURFACE_ALPHA,
    val downloadsGlassChromaticAberration: Boolean = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CHROMATIC_ABERRATION,
    val issueReportDetailedLoggingEnabled: Boolean = false,
    val issueReportDetailedLoggingStartedAtMillis: Long? = null,
    val issueReportLastExportedAtMillis: Long? = null,
    val ignoredUpdateVersion: String? = null,
)

enum class AppThemeMode(val value: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromValue(value: String?): AppThemeMode {
            return entries.firstOrNull { it.value == value } ?: System
        }
    }
}

enum class AppThemeColor(val value: String) {
    Dynamic("dynamic"),
    Coral("coral"),
    Rose("rose"),
    Orchid("orchid"),
    Periwinkle("periwinkle"),
    Sky("sky"),
    Cyan("cyan"),
    Turquoise("turquoise"),
    Leaf("leaf"),
    Lime("lime"),
    Olive("olive"),
    Gold("gold"),
    Apricot("apricot"),
    ;

    companion object {
        fun fromValue(value: String?): AppThemeColor {
            return entries.firstOrNull { it.value == value } ?: Dynamic
        }
    }
}

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val galleryVisibilityManager = DownloadGalleryVisibilityManager(appContext)
    private var galleryVisibilityJob: Job? = null

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        applyTheme(_settings.value.themeMode)
        scheduleDownloadGalleryVisibilitySync(_settings.value)
    }

    fun currentSettings(): AppSettings = _settings.value

    fun shouldAddMetadata(): Boolean = _settings.value.addMetadata

    fun downloadRootRelativePath(): String = _settings.value.downloadRootRelativePath

    fun shouldConfirmCellularDownload(): Boolean = _settings.value.confirmCellularDownload

    fun shouldHideDownloadedVideosInSystemAlbum(): Boolean =
        _settings.value.hideDownloadedVideosInSystemAlbum

    fun shouldShowParseQuickAction(): Boolean = _settings.value.parseQuickActionEnabled

    fun setAddMetadata(enabled: Boolean) {
        val current = _settings.value
        if (current.addMetadata == enabled) return
        prefs.edit().putBoolean(KEY_ADD_METADATA, enabled).apply()
        _settings.value = current.copy(addMetadata = enabled)
    }

    fun setThemeMode(mode: AppThemeMode, applyImmediately: Boolean = true) {
        val current = _settings.value
        if (current.themeMode == mode) return
        prefs.edit().putString(KEY_THEME_MODE, mode.value).apply()
        _settings.value = current.copy(themeMode = mode)
        if (applyImmediately) {
            applyTheme(mode)
        }
    }

    fun syncThemeMode() {
        applyTheme(_settings.value.themeMode)
    }

    fun setThemeColor(color: AppThemeColor) {
        val current = _settings.value
        if (current.themeColor == color) return
        prefs.edit().putString(KEY_THEME_COLOR, color.value).apply()
        _settings.value = current.copy(themeColor = color)
    }

    fun setDarkModePureBlack(enabled: Boolean) {
        val current = _settings.value
        if (current.darkModePureBlack == enabled) return
        prefs.edit().putBoolean(KEY_DARK_MODE_PURE_BLACK, enabled).apply()
        _settings.value = current.copy(darkModePureBlack = enabled)
    }

    fun setConfirmCellularDownload(enabled: Boolean) {
        val current = _settings.value
        if (current.confirmCellularDownload == enabled) return
        prefs.edit().putBoolean(KEY_CONFIRM_CELLULAR_DOWNLOAD, enabled).apply()
        _settings.value = current.copy(confirmCellularDownload = enabled)
    }

    fun setHideDownloadedVideosInSystemAlbum(enabled: Boolean) {
        val current = _settings.value
        if (current.hideDownloadedVideosInSystemAlbum == enabled) return
        prefs.edit().putBoolean(KEY_HIDE_DOWNLOADED_VIDEOS_IN_SYSTEM_ALBUM, enabled).apply()
        val updated = current.copy(hideDownloadedVideosInSystemAlbum = enabled)
        _settings.value = updated
        scheduleDownloadGalleryVisibilitySync(updated)
    }

    fun setParseQuickActionEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.parseQuickActionEnabled == enabled) return
        prefs.edit().putBoolean(KEY_PARSE_QUICK_ACTION, enabled).apply()
        _settings.value = current.copy(parseQuickActionEnabled = enabled)
    }

    fun setDownloadsGlassDebugEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.downloadsGlassDebugEnabled == enabled) return
        prefs.edit().putBoolean(KEY_DOWNLOADS_GLASS_DEBUG_ENABLED, enabled).apply()
        _settings.value = current.copy(downloadsGlassDebugEnabled = enabled)
    }

    fun setDownloadsGlassCornerRadiusDp(value: Float) {
        val normalized = value.coerceIn(0f, 64f)
        val current = _settings.value
        if (current.downloadsGlassCornerRadiusDp == normalized) return
        prefs.edit().putFloat(KEY_DOWNLOADS_GLASS_CORNER_RADIUS_DP, normalized).apply()
        _settings.value = current.copy(downloadsGlassCornerRadiusDp = normalized)
    }

    fun setDownloadsGlassBlurRadiusDp(value: Float) {
        val normalized = value.coerceIn(0f, 48f)
        val current = _settings.value
        if (current.downloadsGlassBlurRadiusDp == normalized) return
        prefs.edit().putFloat(KEY_DOWNLOADS_GLASS_BLUR_RADIUS_DP, normalized).apply()
        _settings.value = current.copy(downloadsGlassBlurRadiusDp = normalized)
    }

    fun setDownloadsGlassRefractionHeightDp(value: Float) {
        val normalized = value.coerceIn(0f, 72f)
        val current = _settings.value
        if (current.downloadsGlassRefractionHeightDp == normalized) return
        prefs.edit().putFloat(KEY_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP, normalized).apply()
        _settings.value = current.copy(downloadsGlassRefractionHeightDp = normalized)
    }

    fun setDownloadsGlassRefractionAmountFrac(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        val current = _settings.value
        if (current.downloadsGlassRefractionAmountFrac == normalized) return
        prefs.edit().putFloat(KEY_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC, normalized).apply()
        _settings.value = current.copy(downloadsGlassRefractionAmountFrac = normalized)
    }

    fun setDownloadsGlassSurfaceAlpha(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        val current = _settings.value
        if (current.downloadsGlassSurfaceAlpha == normalized) return
        prefs.edit().putFloat(KEY_DOWNLOADS_GLASS_SURFACE_ALPHA, normalized).apply()
        _settings.value = current.copy(downloadsGlassSurfaceAlpha = normalized)
    }

    fun setDownloadsGlassChromaticAberration(enabled: Boolean) {
        val current = _settings.value
        if (current.downloadsGlassChromaticAberration == enabled) return
        prefs.edit().putBoolean(KEY_DOWNLOADS_GLASS_CHROMATIC_ABERRATION, enabled).apply()
        _settings.value = current.copy(downloadsGlassChromaticAberration = enabled)
    }

    fun setIssueReportDetailedLoggingEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.issueReportDetailedLoggingEnabled == enabled) return

        val updatedStartedAt = if (enabled) {
            System.currentTimeMillis()
        } else {
            null
        }
        prefs.edit()
            .putBoolean(KEY_ISSUE_REPORT_DETAILED_LOGGING_ENABLED, enabled)
            .putLong(KEY_ISSUE_REPORT_DETAILED_LOGGING_STARTED_AT, updatedStartedAt ?: 0L)
            .apply()
        _settings.value = current.copy(
            issueReportDetailedLoggingEnabled = enabled,
            issueReportDetailedLoggingStartedAtMillis = updatedStartedAt,
        )
    }

    fun setIssueReportLastExportedAt(epochMillis: Long?) {
        val normalized = epochMillis?.takeIf { it > 0L }
        val current = _settings.value
        if (current.issueReportLastExportedAtMillis == normalized) return

        prefs.edit()
            .putLong(KEY_ISSUE_REPORT_LAST_EXPORTED_AT, normalized ?: 0L)
            .apply()
        _settings.value = current.copy(issueReportLastExportedAtMillis = normalized)
    }

    fun setIgnoredUpdateVersion(version: String?) {
        val normalized = normalizeUpdateVersion(version)
        val current = _settings.value
        if (current.ignoredUpdateVersion == normalized) return
        prefs.edit().putString(KEY_IGNORED_UPDATE_VERSION, normalized).apply()
        _settings.value = current.copy(ignoredUpdateVersion = normalized)
    }

    fun shouldIgnoreUpdate(version: String): Boolean {
        val normalized = normalizeUpdateVersion(version)
        return !normalized.isNullOrBlank() && _settings.value.ignoredUpdateVersion == normalized
    }

    fun setDownloadRootRelativePath(relativePath: String) {
        val normalized = normalizeDownloadRoot(relativePath)
        val current = _settings.value
        if (current.downloadRootRelativePath == normalized) return
        val previousRoot = current.downloadRootRelativePath
        prefs.edit().putString(KEY_DOWNLOAD_ROOT_RELATIVE_PATH, normalized).apply()
        val updated = current.copy(downloadRootRelativePath = normalized)
        _settings.value = updated
        scheduleDownloadGalleryVisibilitySync(
            settings = updated,
            previousRootRelativePath = previousRoot,
        )
    }

    fun setDownloadRootFromTreeUri(uri: Uri): Boolean {
        val relativePath = extractDownloadRelativePathFromTreeUri(uri) ?: return false
        setDownloadRootRelativePath(relativePath)
        return true
    }

    private fun loadSettings(): AppSettings {
        return AppSettings(
            addMetadata = prefs.getBoolean(KEY_ADD_METADATA, true),
            themeMode = AppThemeMode.fromValue(
                prefs.getString(KEY_THEME_MODE, AppThemeMode.System.value),
            ),
            themeColor = AppThemeColor.fromValue(
                prefs.getString(KEY_THEME_COLOR, AppThemeColor.Dynamic.value),
            ),
            darkModePureBlack = prefs.getBoolean(KEY_DARK_MODE_PURE_BLACK, false),
            downloadRootRelativePath = normalizeDownloadRoot(
                prefs.getString(KEY_DOWNLOAD_ROOT_RELATIVE_PATH, DEFAULT_DOWNLOAD_ROOT),
            ),
            confirmCellularDownload = prefs.getBoolean(KEY_CONFIRM_CELLULAR_DOWNLOAD, true),
            hideDownloadedVideosInSystemAlbum = prefs.getBoolean(
                KEY_HIDE_DOWNLOADED_VIDEOS_IN_SYSTEM_ALBUM,
                false,
            ),
            parseQuickActionEnabled = prefs.getBoolean(KEY_PARSE_QUICK_ACTION, true),
            downloadsGlassDebugEnabled = prefs.getBoolean(
                KEY_DOWNLOADS_GLASS_DEBUG_ENABLED,
                false,
            ),
            downloadsGlassCornerRadiusDp = prefs.getFloat(
                KEY_DOWNLOADS_GLASS_CORNER_RADIUS_DP,
                DEFAULT_DOWNLOADS_GLASS_CORNER_RADIUS_DP,
            ),
            downloadsGlassBlurRadiusDp = prefs.getFloat(
                KEY_DOWNLOADS_GLASS_BLUR_RADIUS_DP,
                DEFAULT_DOWNLOADS_GLASS_BLUR_RADIUS_DP,
            ),
            downloadsGlassRefractionHeightDp = prefs.getFloat(
                KEY_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP,
                DEFAULT_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP,
            ),
            downloadsGlassRefractionAmountFrac = prefs.getFloat(
                KEY_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC,
                DEFAULT_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC,
            ),
            downloadsGlassSurfaceAlpha = prefs.getFloat(
                KEY_DOWNLOADS_GLASS_SURFACE_ALPHA,
                DEFAULT_DOWNLOADS_GLASS_SURFACE_ALPHA,
            ),
            downloadsGlassChromaticAberration = prefs.getBoolean(
                KEY_DOWNLOADS_GLASS_CHROMATIC_ABERRATION,
                DEFAULT_DOWNLOADS_GLASS_CHROMATIC_ABERRATION,
            ),
            issueReportDetailedLoggingEnabled = prefs.getBoolean(
                KEY_ISSUE_REPORT_DETAILED_LOGGING_ENABLED,
                false,
            ),
            issueReportDetailedLoggingStartedAtMillis = prefs.getLong(
                KEY_ISSUE_REPORT_DETAILED_LOGGING_STARTED_AT,
                0L,
            ).takeIf { it > 0L },
            issueReportLastExportedAtMillis = prefs.getLong(
                KEY_ISSUE_REPORT_LAST_EXPORTED_AT,
                0L,
            ).takeIf { it > 0L },
            ignoredUpdateVersion = normalizeUpdateVersion(
                prefs.getString(KEY_IGNORED_UPDATE_VERSION, null),
            ),
        )
    }

    private fun applyTheme(mode: AppThemeMode) {
        val nightMode = when (mode) {
            AppThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun scheduleDownloadGalleryVisibilitySync(
        settings: AppSettings,
        previousRootRelativePath: String? = null,
    ) {
        val currentRoot = normalizeDownloadRoot(settings.downloadRootRelativePath)
        val previousRoot = previousRootRelativePath
            ?.let(::normalizeDownloadRoot)
            ?.takeIf { !it.equals(currentRoot, ignoreCase = true) }

        galleryVisibilityJob?.cancel()
        galleryVisibilityJob = settingsScope.launch {
            if (!previousRoot.isNullOrBlank()) {
                galleryVisibilityManager.applyPolicy(
                    downloadRootRelativePath = previousRoot,
                    hideFromSystemAlbum = false,
                )
            }
            galleryVisibilityManager.applyPolicy(
                downloadRootRelativePath = currentRoot,
                hideFromSystemAlbum = settings.hideDownloadedVideosInSystemAlbum,
            )
        }
    }

    private fun normalizeDownloadRoot(rawPath: String?): String {
        val cleaned = rawPath
            ?.replace('\\', '/')
            ?.trim()
            ?.trim('/')
            .orEmpty()
        if (cleaned.isBlank()) return DEFAULT_DOWNLOAD_ROOT

        val segments = cleaned.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return DEFAULT_DOWNLOAD_ROOT
        if (!segments.first().equals(Environment.DIRECTORY_DOWNLOADS, ignoreCase = true)) {
            return DEFAULT_DOWNLOAD_ROOT
        }
        return buildString {
            append(Environment.DIRECTORY_DOWNLOADS)
            if (segments.size > 1) {
                append('/')
                append(segments.drop(1).joinToString("/"))
            }
        }
    }

    private fun extractDownloadRelativePathFromTreeUri(uri: Uri): String? {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        val volume = treeId.substringBefore(':', "")
        if (!volume.equals("primary", ignoreCase = true)) return null

        val path = treeId
            .substringAfter(':', "")
            .replace('\\', '/')
            .trim()
            .trim('/')
        if (path.isBlank()) return null

        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        if (!segments.first().equals(Environment.DIRECTORY_DOWNLOADS, ignoreCase = true)) {
            return null
        }

        return buildString {
            append(Environment.DIRECTORY_DOWNLOADS)
            if (segments.size > 1) {
                append('/')
                append(segments.drop(1).joinToString("/"))
            }
        }
    }

    private fun normalizeUpdateVersion(rawVersion: String?): String? {
        return rawVersion
            ?.trim()
            ?.removePrefix("v")
            ?.removePrefix("V")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        // Keep this a true compile-time constant for default values.
        const val DEFAULT_DOWNLOAD_ROOT = "Download/BiliTools"
        const val DEFAULT_DOWNLOADS_GLASS_CORNER_RADIUS_DP = 22f
        const val DEFAULT_DOWNLOADS_GLASS_BLUR_RADIUS_DP = 6f
        const val DEFAULT_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP = 12f
        const val DEFAULT_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC = 0.5f
        const val DEFAULT_DOWNLOADS_GLASS_SURFACE_ALPHA = 0.7f
        const val DEFAULT_DOWNLOADS_GLASS_CHROMATIC_ABERRATION = true

        private const val PREFS_NAME = "app_settings"
        private const val KEY_ADD_METADATA = "add_metadata"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_DARK_MODE_PURE_BLACK = "dark_mode_pure_black"
        private const val KEY_DOWNLOAD_ROOT_RELATIVE_PATH = "download_root_relative_path"
        private const val KEY_CONFIRM_CELLULAR_DOWNLOAD = "confirm_cellular_download"
        private const val KEY_HIDE_DOWNLOADED_VIDEOS_IN_SYSTEM_ALBUM =
            "hide_downloaded_videos_in_system_album"
        private const val KEY_PARSE_QUICK_ACTION = "parse_quick_action"
        private const val KEY_DOWNLOADS_GLASS_DEBUG_ENABLED = "downloads_glass_debug_enabled"
        private const val KEY_DOWNLOADS_GLASS_CORNER_RADIUS_DP = "downloads_glass_corner_radius_dp"
        private const val KEY_DOWNLOADS_GLASS_BLUR_RADIUS_DP = "downloads_glass_blur_radius_dp"
        private const val KEY_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP = "downloads_glass_refraction_height_dp"
        private const val KEY_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC = "downloads_glass_refraction_amount_frac"
        private const val KEY_DOWNLOADS_GLASS_SURFACE_ALPHA = "downloads_glass_surface_alpha"
        private const val KEY_DOWNLOADS_GLASS_CHROMATIC_ABERRATION = "downloads_glass_chromatic_aberration"
        private const val KEY_ISSUE_REPORT_DETAILED_LOGGING_ENABLED =
            "issue_report_detailed_logging_enabled"
        private const val KEY_ISSUE_REPORT_DETAILED_LOGGING_STARTED_AT =
            "issue_report_detailed_logging_started_at"
        private const val KEY_ISSUE_REPORT_LAST_EXPORTED_AT = "issue_report_last_exported_at"
        private const val KEY_IGNORED_UPDATE_VERSION = "ignored_update_version"
    }
}
