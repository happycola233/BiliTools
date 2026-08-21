package com.happycola233.bilitools.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.appcompat.app.AppCompatDelegate
import com.happycola233.bilitools.core.AudioQualities
import com.happycola233.bilitools.core.DownloadNaming
import com.happycola233.bilitools.notification.isLiveUpdateSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadNamingSettings(
    val topLevelFolderMode: TopLevelFolderMode = TopLevelFolderMode.Auto,
    val overwriteExistingFiles: Boolean = false,
    val cleanSeparators: Boolean = true,
    val topLevelFolderTemplate: String = SettingsRepository.DEFAULT_TOP_LEVEL_FOLDER_TEMPLATE,
    val itemFolderTemplate: String = SettingsRepository.DEFAULT_ITEM_FOLDER_TEMPLATE,
    val fileTemplate: String = SettingsRepository.DEFAULT_FILE_TEMPLATE,
)

data class DefaultDownloadQualitySettings(
    val resolutionMode: DownloadQualityMode = DownloadQualityMode.Highest,
    val fixedResolutionId: Int = SettingsRepository.DEFAULT_DOWNLOAD_RESOLUTION_ID,
    val codec: DefaultDownloadVideoCodec = DefaultDownloadVideoCodec.Avc,
    val audioBitrateMode: DownloadQualityMode = DownloadQualityMode.Highest,
    val fixedAudioBitrateId: Int = SettingsRepository.DEFAULT_DOWNLOAD_AUDIO_BITRATE_ID,
)

data class AppSettings(
    val addMetadata: Boolean = true,
    val convertXmlDanmakuToAss: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val themeColor: AppThemeColor = AppThemeColor.Dynamic,
    val darkModePureBlack: Boolean = false,
    val launchSplashAnimationEnabled: Boolean = true,
    val liquidBottomTabsEnabled: Boolean = true,
    val liquidBarWidthFraction: Float = SettingsRepository.DEFAULT_LIQUID_BAR_WIDTH_FRACTION,
    val liquidBarGlassBlurRadiusDp: Float = SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_BLUR_RADIUS_DP,
    val liquidBarGlassRefractionHeightDp: Float = SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_REFRACTION_HEIGHT_DP,
    val liquidBarGlassRefractionAmountFrac: Float = SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_REFRACTION_AMOUNT_FRAC,
    val liquidBarGlassSurfaceAlpha: Float = SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_SURFACE_ALPHA,
    val liquidBarGlassChromaticAberration: Boolean = SettingsRepository.DEFAULT_LIQUID_BAR_GLASS_CHROMATIC_ABERRATION,
    val hapticFeedbackLevel: HapticFeedbackLevel = HapticFeedbackLevel.Full,
    val liveActivityStyleNotificationEnabled: Boolean = true,
    val downloadRootRelativePath: String = SettingsRepository.DEFAULT_DOWNLOAD_ROOT,
    val confirmCellularDownload: Boolean = true,
    val hideDownloadedVideosInSystemAlbum: Boolean = false,
    val downloadsGlassDebugEnabled: Boolean = false,
    val downloadsGlassCornerRadiusDp: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CORNER_RADIUS_DP,
    val downloadsGlassBlurRadiusDp: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_BLUR_RADIUS_DP,
    val downloadsGlassRefractionHeightDp: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP,
    val downloadsGlassRefractionAmountFrac: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC,
    val downloadsGlassSurfaceAlpha: Float = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_SURFACE_ALPHA,
    val downloadsGlassChromaticAberration: Boolean = SettingsRepository.DEFAULT_DOWNLOADS_GLASS_CHROMATIC_ABERRATION,
    val defaultDownloadQuality: DefaultDownloadQualitySettings = DefaultDownloadQualitySettings(),
    val naming: DownloadNamingSettings = DownloadNamingSettings(),
    val issueReportDetailedLoggingEnabled: Boolean = false,
    val issueReportDetailedLoggingStartedAtMillis: Long? = null,
    val issueReportLastExportedAtMillis: Long? = null,
    val ignoredUpdateVersion: String? = null,
)

enum class DownloadQualityMode(val value: String) {
    Highest("highest"),
    Lowest("lowest"),
    Fixed("fixed"),
    ;

    companion object {
        fun fromValue(value: String?): DownloadQualityMode {
            return entries.firstOrNull { it.value == value } ?: Highest
        }
    }
}

enum class DefaultDownloadVideoCodec(val value: String) {
    Avc("avc"),
    Hevc("hevc"),
    Av1("av1"),
    ;

    companion object {
        fun fromValue(value: String?): DefaultDownloadVideoCodec {
            return entries.firstOrNull { it.value == value } ?: Avc
        }
    }
}

/**
 * 触感反馈强度档位。个人偏好差异较大，只做布尔开关容易让用户直接全关，
 * 因此「轻量」档保留长按、阈值、成功/失败这类信息量高的反馈，点击与勾选等高频反馈仅「完整」档开启。
 */
enum class HapticFeedbackLevel(val value: String) {
    Off("off"),
    Light("light"),
    Full("full"),
    ;

    companion object {
        fun fromValue(value: String?): HapticFeedbackLevel {
            return entries.firstOrNull { it.value == value } ?: Full
        }
    }
}

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

enum class TopLevelFolderMode(val value: String) {
    Auto("auto"),
    Enabled("enabled"),
    Disabled("disabled"),
    ;

    companion object {
        fun fromValue(value: String?): TopLevelFolderMode {
            return entries.firstOrNull { it.value == value } ?: Auto
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

    fun shouldConvertXmlDanmakuToAss(): Boolean = _settings.value.convertXmlDanmakuToAss

    fun downloadRootRelativePath(): String = _settings.value.downloadRootRelativePath

    fun shouldConfirmCellularDownload(): Boolean = _settings.value.confirmCellularDownload

    fun shouldHideDownloadedVideosInSystemAlbum(): Boolean =
        _settings.value.hideDownloadedVideosInSystemAlbum

    fun shouldUseLiveActivityStyleNotification(): Boolean =
        _settings.value.liveActivityStyleNotificationEnabled && appContext.isLiveUpdateSupported()

    fun currentNamingSettings(): DownloadNamingSettings = _settings.value.naming

    fun shouldOverwriteExistingNamingTargets(): Boolean = _settings.value.naming.overwriteExistingFiles

    fun currentDefaultDownloadQuality(): DefaultDownloadQualitySettings =
        _settings.value.defaultDownloadQuality

    fun setAddMetadata(enabled: Boolean) {
        val current = _settings.value
        if (current.addMetadata == enabled) return
        prefs.edit().putBoolean(KEY_ADD_METADATA, enabled).apply()
        _settings.value = current.copy(addMetadata = enabled)
    }

    fun setConvertXmlDanmakuToAss(enabled: Boolean) {
        val current = _settings.value
        if (current.convertXmlDanmakuToAss == enabled) return
        prefs.edit().putBoolean(KEY_CONVERT_XML_DANMAKU_TO_ASS, enabled).apply()
        _settings.value = current.copy(convertXmlDanmakuToAss = enabled)
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

    fun setLaunchSplashAnimationEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.launchSplashAnimationEnabled == enabled) return
        prefs.edit().putBoolean(KEY_LAUNCH_SPLASH_ANIMATION_ENABLED, enabled).apply()
        _settings.value = current.copy(launchSplashAnimationEnabled = enabled)
    }

    fun setLiquidBottomTabsEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.liquidBottomTabsEnabled == enabled) return
        prefs.edit().putBoolean(KEY_LIQUID_BOTTOM_TABS_ENABLED, enabled).apply()
        _settings.value = current.copy(liquidBottomTabsEnabled = enabled)
    }

    fun setLiquidBarWidthFraction(value: Float) {
        val normalized = value.coerceIn(MIN_LIQUID_BAR_WIDTH_FRACTION, 1f)
        val current = _settings.value
        if (current.liquidBarWidthFraction == normalized) return
        prefs.edit().putFloat(KEY_LIQUID_BAR_WIDTH_FRACTION, normalized).apply()
        _settings.value = current.copy(liquidBarWidthFraction = normalized)
    }

    fun setLiquidBarGlassBlurRadiusDp(value: Float) {
        val normalized = value.coerceIn(0f, 48f)
        val current = _settings.value
        if (current.liquidBarGlassBlurRadiusDp == normalized) return
        prefs.edit().putFloat(KEY_LIQUID_BAR_GLASS_BLUR_RADIUS_DP, normalized).apply()
        _settings.value = current.copy(liquidBarGlassBlurRadiusDp = normalized)
    }

    fun setLiquidBarGlassRefractionHeightDp(value: Float) {
        val normalized = value.coerceIn(0f, 72f)
        val current = _settings.value
        if (current.liquidBarGlassRefractionHeightDp == normalized) return
        prefs.edit().putFloat(KEY_LIQUID_BAR_GLASS_REFRACTION_HEIGHT_DP, normalized).apply()
        _settings.value = current.copy(liquidBarGlassRefractionHeightDp = normalized)
    }

    fun setLiquidBarGlassRefractionAmountFrac(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        val current = _settings.value
        if (current.liquidBarGlassRefractionAmountFrac == normalized) return
        prefs.edit().putFloat(KEY_LIQUID_BAR_GLASS_REFRACTION_AMOUNT_FRAC, normalized).apply()
        _settings.value = current.copy(liquidBarGlassRefractionAmountFrac = normalized)
    }

    fun setLiquidBarGlassSurfaceAlpha(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        val current = _settings.value
        if (current.liquidBarGlassSurfaceAlpha == normalized) return
        prefs.edit().putFloat(KEY_LIQUID_BAR_GLASS_SURFACE_ALPHA, normalized).apply()
        _settings.value = current.copy(liquidBarGlassSurfaceAlpha = normalized)
    }

    fun setLiquidBarGlassChromaticAberration(enabled: Boolean) {
        val current = _settings.value
        if (current.liquidBarGlassChromaticAberration == enabled) return
        prefs.edit().putBoolean(KEY_LIQUID_BAR_GLASS_CHROMATIC_ABERRATION, enabled).apply()
        _settings.value = current.copy(liquidBarGlassChromaticAberration = enabled)
    }

    fun setHapticFeedbackLevel(level: HapticFeedbackLevel) {
        val current = _settings.value
        if (current.hapticFeedbackLevel == level) return
        prefs.edit().putString(KEY_HAPTIC_FEEDBACK_LEVEL, level.value).apply()
        _settings.value = current.copy(hapticFeedbackLevel = level)
    }

    fun setLiveActivityStyleNotificationEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.liveActivityStyleNotificationEnabled == enabled) return
        prefs.edit().putBoolean(KEY_LIVE_ACTIVITY_STYLE_NOTIFICATION_ENABLED, enabled).apply()
        _settings.value = current.copy(liveActivityStyleNotificationEnabled = enabled)
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
        scheduleDownloadGalleryVisibilitySync(updated, forceRefresh = true)
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

    fun setDefaultDownloadQuality(quality: DefaultDownloadQualitySettings) {
        val normalized = normalizeDefaultDownloadQuality(quality)
        val current = _settings.value
        if (current.defaultDownloadQuality == normalized) return
        prefs.edit()
            .putString(KEY_DEFAULT_DOWNLOAD_RESOLUTION_MODE, normalized.resolutionMode.value)
            .putInt(KEY_DEFAULT_DOWNLOAD_RESOLUTION_ID, normalized.fixedResolutionId)
            .putString(KEY_DEFAULT_DOWNLOAD_CODEC, normalized.codec.value)
            .putString(KEY_DEFAULT_DOWNLOAD_AUDIO_BITRATE_MODE, normalized.audioBitrateMode.value)
            .putInt(KEY_DEFAULT_DOWNLOAD_AUDIO_BITRATE_ID, normalized.fixedAudioBitrateId)
            .apply()
        _settings.value = current.copy(defaultDownloadQuality = normalized)
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

    fun setNamingTopLevelFolderMode(mode: TopLevelFolderMode) {
        val current = _settings.value
        if (current.naming.topLevelFolderMode == mode) return
        prefs.edit().putString(KEY_NAMING_TOP_LEVEL_FOLDER_MODE, mode.value).apply()
        _settings.value = current.copy(
            naming = current.naming.copy(topLevelFolderMode = mode),
        )
    }

    fun setNamingOverwriteExistingFiles(enabled: Boolean) {
        val current = _settings.value
        if (current.naming.overwriteExistingFiles == enabled) return
        prefs.edit().putBoolean(KEY_NAMING_OVERWRITE_EXISTING_FILES, enabled).apply()
        _settings.value = current.copy(
            naming = current.naming.copy(overwriteExistingFiles = enabled),
        )
    }

    fun setNamingCleanSeparators(enabled: Boolean) {
        val current = _settings.value
        if (current.naming.cleanSeparators == enabled) return
        prefs.edit().putBoolean(KEY_NAMING_CLEAN_SEPARATORS, enabled).apply()
        _settings.value = current.copy(
            naming = current.naming.copy(cleanSeparators = enabled),
        )
    }

    fun setNamingTopLevelFolderTemplate(template: String) {
        updateNamingTemplate(
            currentValue = _settings.value.naming.topLevelFolderTemplate,
            nextValue = template,
            key = KEY_NAMING_TOP_LEVEL_FOLDER_TEMPLATE,
        ) { naming, next ->
            naming.copy(topLevelFolderTemplate = next)
        }
    }

    fun setNamingItemFolderTemplate(template: String) {
        updateNamingTemplate(
            currentValue = _settings.value.naming.itemFolderTemplate,
            nextValue = template,
            key = KEY_NAMING_ITEM_FOLDER_TEMPLATE,
        ) { naming, next ->
            naming.copy(itemFolderTemplate = next)
        }
    }

    fun setNamingFileTemplate(template: String) {
        updateNamingTemplate(
            currentValue = _settings.value.naming.fileTemplate,
            nextValue = template,
            key = KEY_NAMING_FILE_TEMPLATE,
        ) { naming, next ->
            naming.copy(fileTemplate = next)
        }
    }

    fun restoreNamingDefaults() {
        val current = _settings.value
        val defaults = DownloadNamingSettings()
        val templatesAlreadyDefault =
            current.naming.topLevelFolderTemplate == defaults.topLevelFolderTemplate &&
                current.naming.itemFolderTemplate == defaults.itemFolderTemplate &&
                current.naming.fileTemplate == defaults.fileTemplate
        if (templatesAlreadyDefault) return
        prefs.edit()
            .putString(KEY_NAMING_TOP_LEVEL_FOLDER_TEMPLATE, defaults.topLevelFolderTemplate)
            .putString(KEY_NAMING_ITEM_FOLDER_TEMPLATE, defaults.itemFolderTemplate)
            .putString(KEY_NAMING_FILE_TEMPLATE, defaults.fileTemplate)
            .apply()
        _settings.value = current.copy(
            naming = current.naming.copy(
                topLevelFolderTemplate = defaults.topLevelFolderTemplate,
                itemFolderTemplate = defaults.itemFolderTemplate,
                fileTemplate = defaults.fileTemplate,
            ),
        )
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
            forceRefresh = true,
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
            convertXmlDanmakuToAss = prefs.getBoolean(KEY_CONVERT_XML_DANMAKU_TO_ASS, true),
            themeMode = AppThemeMode.fromValue(
                prefs.getString(KEY_THEME_MODE, AppThemeMode.System.value),
            ),
            themeColor = AppThemeColor.fromValue(
                prefs.getString(KEY_THEME_COLOR, AppThemeColor.Dynamic.value),
            ),
            darkModePureBlack = prefs.getBoolean(KEY_DARK_MODE_PURE_BLACK, false),
            launchSplashAnimationEnabled = prefs.getBoolean(
                KEY_LAUNCH_SPLASH_ANIMATION_ENABLED,
                true,
            ),
            liquidBottomTabsEnabled = prefs.getBoolean(
                KEY_LIQUID_BOTTOM_TABS_ENABLED,
                true,
            ),
            liquidBarWidthFraction = prefs.getFloat(
                KEY_LIQUID_BAR_WIDTH_FRACTION,
                DEFAULT_LIQUID_BAR_WIDTH_FRACTION,
            ),
            liquidBarGlassBlurRadiusDp = prefs.getFloat(
                KEY_LIQUID_BAR_GLASS_BLUR_RADIUS_DP,
                DEFAULT_LIQUID_BAR_GLASS_BLUR_RADIUS_DP,
            ),
            liquidBarGlassRefractionHeightDp = prefs.getFloat(
                KEY_LIQUID_BAR_GLASS_REFRACTION_HEIGHT_DP,
                DEFAULT_LIQUID_BAR_GLASS_REFRACTION_HEIGHT_DP,
            ),
            liquidBarGlassRefractionAmountFrac = prefs.getFloat(
                KEY_LIQUID_BAR_GLASS_REFRACTION_AMOUNT_FRAC,
                DEFAULT_LIQUID_BAR_GLASS_REFRACTION_AMOUNT_FRAC,
            ),
            liquidBarGlassSurfaceAlpha = prefs.getFloat(
                KEY_LIQUID_BAR_GLASS_SURFACE_ALPHA,
                DEFAULT_LIQUID_BAR_GLASS_SURFACE_ALPHA,
            ),
            liquidBarGlassChromaticAberration = prefs.getBoolean(
                KEY_LIQUID_BAR_GLASS_CHROMATIC_ABERRATION,
                DEFAULT_LIQUID_BAR_GLASS_CHROMATIC_ABERRATION,
            ),
            hapticFeedbackLevel = HapticFeedbackLevel.fromValue(
                prefs.getString(KEY_HAPTIC_FEEDBACK_LEVEL, HapticFeedbackLevel.Full.value),
            ),
            liveActivityStyleNotificationEnabled = prefs.getBoolean(
                KEY_LIVE_ACTIVITY_STYLE_NOTIFICATION_ENABLED,
                true,
            ),
            downloadRootRelativePath = normalizeDownloadRoot(
                prefs.getString(KEY_DOWNLOAD_ROOT_RELATIVE_PATH, DEFAULT_DOWNLOAD_ROOT),
            ),
            confirmCellularDownload = prefs.getBoolean(KEY_CONFIRM_CELLULAR_DOWNLOAD, true),
            hideDownloadedVideosInSystemAlbum = prefs.getBoolean(
                KEY_HIDE_DOWNLOADED_VIDEOS_IN_SYSTEM_ALBUM,
                false,
            ),
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
            defaultDownloadQuality = normalizeDefaultDownloadQuality(
                DefaultDownloadQualitySettings(
                    resolutionMode = DownloadQualityMode.fromValue(
                        prefs.getString(
                            KEY_DEFAULT_DOWNLOAD_RESOLUTION_MODE,
                            DownloadQualityMode.Highest.value,
                        ),
                    ),
                    fixedResolutionId = prefs.getInt(
                        KEY_DEFAULT_DOWNLOAD_RESOLUTION_ID,
                        DEFAULT_DOWNLOAD_RESOLUTION_ID,
                    ),
                    codec = DefaultDownloadVideoCodec.fromValue(
                        prefs.getString(
                            KEY_DEFAULT_DOWNLOAD_CODEC,
                            DefaultDownloadVideoCodec.Avc.value,
                        ),
                    ),
                    audioBitrateMode = DownloadQualityMode.fromValue(
                        prefs.getString(
                            KEY_DEFAULT_DOWNLOAD_AUDIO_BITRATE_MODE,
                            DownloadQualityMode.Highest.value,
                        ),
                    ),
                    fixedAudioBitrateId = prefs.getInt(
                        KEY_DEFAULT_DOWNLOAD_AUDIO_BITRATE_ID,
                        DEFAULT_DOWNLOAD_AUDIO_BITRATE_ID,
                    ),
                ),
            ),
            naming = DownloadNamingSettings(
                topLevelFolderMode = TopLevelFolderMode.fromValue(
                    prefs.getString(
                        KEY_NAMING_TOP_LEVEL_FOLDER_MODE,
                        TopLevelFolderMode.Auto.value,
                    ),
                ),
                overwriteExistingFiles = prefs.getBoolean(
                    KEY_NAMING_OVERWRITE_EXISTING_FILES,
                    false,
                ),
                cleanSeparators = prefs.getBoolean(
                    KEY_NAMING_CLEAN_SEPARATORS,
                    true,
                ),
                topLevelFolderTemplate = prefs.getString(
                    KEY_NAMING_TOP_LEVEL_FOLDER_TEMPLATE,
                    DEFAULT_TOP_LEVEL_FOLDER_TEMPLATE,
                ) ?: DEFAULT_TOP_LEVEL_FOLDER_TEMPLATE,
                itemFolderTemplate = prefs.getString(
                    KEY_NAMING_ITEM_FOLDER_TEMPLATE,
                    DEFAULT_ITEM_FOLDER_TEMPLATE,
                ) ?: DEFAULT_ITEM_FOLDER_TEMPLATE,
                fileTemplate = prefs.getString(
                    KEY_NAMING_FILE_TEMPLATE,
                    DEFAULT_FILE_TEMPLATE,
                ) ?: DEFAULT_FILE_TEMPLATE,
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
        forceRefresh: Boolean = false,
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
                    forceRefresh = forceRefresh,
                )
            }
            galleryVisibilityManager.applyPolicy(
                downloadRootRelativePath = currentRoot,
                hideFromSystemAlbum = settings.hideDownloadedVideosInSystemAlbum,
                forceRefresh = forceRefresh,
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

    private fun normalizeDefaultDownloadQuality(
        quality: DefaultDownloadQualitySettings,
    ): DefaultDownloadQualitySettings {
        val resolutionId = quality.fixedResolutionId
            .takeIf { DEFAULT_DOWNLOAD_RESOLUTION_IDS.contains(it) }
            ?: DEFAULT_DOWNLOAD_RESOLUTION_ID
        val audioBitrateId = quality.fixedAudioBitrateId
            .takeIf { AudioQualities.allIds.contains(it) }
            ?: DEFAULT_DOWNLOAD_AUDIO_BITRATE_ID

        return quality.copy(
            fixedResolutionId = resolutionId,
            fixedAudioBitrateId = audioBitrateId,
        )
    }

    private fun updateNamingTemplate(
        currentValue: String,
        nextValue: String,
        key: String,
        update: (DownloadNamingSettings, String) -> DownloadNamingSettings,
    ) {
        if (currentValue == nextValue) return
        val current = _settings.value
        prefs.edit().putString(key, nextValue).apply()
        _settings.value = current.copy(
            naming = update(current.naming, nextValue),
        )
    }

    companion object {
        // Keep this a true compile-time constant for default values.
        const val DEFAULT_DOWNLOAD_ROOT = "Download/BiliTools"
        const val DEFAULT_LIQUID_BAR_WIDTH_FRACTION = 0.8f
        const val MIN_LIQUID_BAR_WIDTH_FRACTION = 0.6f
        const val DEFAULT_LIQUID_BAR_GLASS_BLUR_RADIUS_DP = 6f
        const val DEFAULT_LIQUID_BAR_GLASS_REFRACTION_HEIGHT_DP = 6f
        const val DEFAULT_LIQUID_BAR_GLASS_REFRACTION_AMOUNT_FRAC = 0.75f
        const val DEFAULT_LIQUID_BAR_GLASS_SURFACE_ALPHA = 0.55f
        const val DEFAULT_LIQUID_BAR_GLASS_CHROMATIC_ABERRATION = true
        const val DEFAULT_DOWNLOADS_GLASS_CORNER_RADIUS_DP = 22f
        const val DEFAULT_DOWNLOADS_GLASS_BLUR_RADIUS_DP = 6f
        const val DEFAULT_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP = 12f
        const val DEFAULT_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC = 0.5f
        const val DEFAULT_DOWNLOADS_GLASS_SURFACE_ALPHA = 0.7f
        const val DEFAULT_DOWNLOADS_GLASS_CHROMATIC_ABERRATION = true
        val DEFAULT_DOWNLOAD_RESOLUTION_IDS = listOf(127, 126, 125, 120, 116, 112, 80, 64, 32, 16, 6)
        const val DEFAULT_DOWNLOAD_RESOLUTION_ID = 127
        const val DEFAULT_DOWNLOAD_AUDIO_BITRATE_ID = AudioQualities.LOSSLESS_FLAC
        const val DEFAULT_TOP_LEVEL_FOLDER_TEMPLATE = DownloadNaming.DEFAULT_TOP_FOLDER_TEMPLATE
        const val DEFAULT_ITEM_FOLDER_TEMPLATE = DownloadNaming.DEFAULT_ITEM_FOLDER_TEMPLATE
        const val DEFAULT_FILE_TEMPLATE = DownloadNaming.DEFAULT_FILE_TEMPLATE

        private const val PREFS_NAME = "app_settings"
        private const val KEY_ADD_METADATA = "add_metadata"
        private const val KEY_CONVERT_XML_DANMAKU_TO_ASS = "convert_xml_danmaku_to_ass"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_DARK_MODE_PURE_BLACK = "dark_mode_pure_black"
        private const val KEY_LAUNCH_SPLASH_ANIMATION_ENABLED = "launch_splash_animation_enabled"
        private const val KEY_LIQUID_BOTTOM_TABS_ENABLED = "liquid_bottom_tabs_enabled"
        private const val KEY_LIQUID_BAR_WIDTH_FRACTION = "liquid_bar_width_fraction"
        private const val KEY_LIQUID_BAR_GLASS_BLUR_RADIUS_DP = "liquid_bar_glass_blur_radius_dp"
        private const val KEY_LIQUID_BAR_GLASS_REFRACTION_HEIGHT_DP =
            "liquid_bar_glass_refraction_height_dp"
        private const val KEY_LIQUID_BAR_GLASS_REFRACTION_AMOUNT_FRAC =
            "liquid_bar_glass_refraction_amount_frac"
        private const val KEY_LIQUID_BAR_GLASS_SURFACE_ALPHA = "liquid_bar_glass_surface_alpha"
        private const val KEY_LIQUID_BAR_GLASS_CHROMATIC_ABERRATION =
            "liquid_bar_glass_chromatic_aberration"
        private const val KEY_HAPTIC_FEEDBACK_LEVEL = "haptic_feedback_level"
        private const val KEY_LIVE_ACTIVITY_STYLE_NOTIFICATION_ENABLED =
            "live_activity_style_notification_enabled"
        private const val KEY_DOWNLOAD_ROOT_RELATIVE_PATH = "download_root_relative_path"
        private const val KEY_CONFIRM_CELLULAR_DOWNLOAD = "confirm_cellular_download"
        private const val KEY_HIDE_DOWNLOADED_VIDEOS_IN_SYSTEM_ALBUM =
            "hide_downloaded_videos_in_system_album"
        private const val KEY_DOWNLOADS_GLASS_DEBUG_ENABLED = "downloads_glass_debug_enabled"
        private const val KEY_DOWNLOADS_GLASS_CORNER_RADIUS_DP = "downloads_glass_corner_radius_dp"
        private const val KEY_DOWNLOADS_GLASS_BLUR_RADIUS_DP = "downloads_glass_blur_radius_dp"
        private const val KEY_DOWNLOADS_GLASS_REFRACTION_HEIGHT_DP = "downloads_glass_refraction_height_dp"
        private const val KEY_DOWNLOADS_GLASS_REFRACTION_AMOUNT_FRAC = "downloads_glass_refraction_amount_frac"
        private const val KEY_DOWNLOADS_GLASS_SURFACE_ALPHA = "downloads_glass_surface_alpha"
        private const val KEY_DOWNLOADS_GLASS_CHROMATIC_ABERRATION = "downloads_glass_chromatic_aberration"
        private const val KEY_DEFAULT_DOWNLOAD_RESOLUTION_MODE = "default_download_resolution_mode"
        private const val KEY_DEFAULT_DOWNLOAD_RESOLUTION_ID = "default_download_resolution_id"
        private const val KEY_DEFAULT_DOWNLOAD_CODEC = "default_download_codec"
        private const val KEY_DEFAULT_DOWNLOAD_AUDIO_BITRATE_MODE = "default_download_audio_bitrate_mode"
        private const val KEY_DEFAULT_DOWNLOAD_AUDIO_BITRATE_ID = "default_download_audio_bitrate_id"
        private const val KEY_NAMING_TOP_LEVEL_FOLDER_MODE = "naming_top_level_folder_mode"
        private const val KEY_NAMING_OVERWRITE_EXISTING_FILES = "naming_overwrite_existing_files"
        private const val KEY_NAMING_CLEAN_SEPARATORS = "naming_clean_separators"
        private const val KEY_NAMING_TOP_LEVEL_FOLDER_TEMPLATE = "naming_top_level_folder_template"
        private const val KEY_NAMING_ITEM_FOLDER_TEMPLATE = "naming_item_folder_template"
        private const val KEY_NAMING_FILE_TEMPLATE = "naming_file_template"
        private const val KEY_ISSUE_REPORT_DETAILED_LOGGING_ENABLED =
            "issue_report_detailed_logging_enabled"
        private const val KEY_ISSUE_REPORT_DETAILED_LOGGING_STARTED_AT =
            "issue_report_detailed_logging_started_at"
        private const val KEY_ISSUE_REPORT_LAST_EXPORTED_AT = "issue_report_last_exported_at"
        private const val KEY_IGNORED_UPDATE_VERSION = "ignored_update_version"
    }
}
