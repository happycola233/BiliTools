package com.happycola233.bilitools.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val addMetadata: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val themeColor: AppThemeColor = AppThemeColor.Dynamic,
    val darkModePureBlack: Boolean = false,
    val downloadRootRelativePath: String = SettingsRepository.DEFAULT_DOWNLOAD_ROOT,
    val confirmCellularDownload: Boolean = true,
    val parseQuickActionEnabled: Boolean = true,
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
    Blue("blue"),
    Green("green"),
    Orange("orange"),
    Pink("pink"),
    ;

    companion object {
        fun fromValue(value: String?): AppThemeColor {
            return entries.firstOrNull { it.value == value } ?: Dynamic
        }
    }
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        applyTheme(_settings.value.themeMode)
    }

    fun currentSettings(): AppSettings = _settings.value

    fun shouldAddMetadata(): Boolean = _settings.value.addMetadata

    fun downloadRootRelativePath(): String = _settings.value.downloadRootRelativePath

    fun shouldConfirmCellularDownload(): Boolean = _settings.value.confirmCellularDownload

    fun shouldShowParseQuickAction(): Boolean = _settings.value.parseQuickActionEnabled

    fun setAddMetadata(enabled: Boolean) {
        val current = _settings.value
        if (current.addMetadata == enabled) return
        prefs.edit().putBoolean(KEY_ADD_METADATA, enabled).apply()
        _settings.value = current.copy(addMetadata = enabled)
    }

    fun setThemeMode(mode: AppThemeMode) {
        val current = _settings.value
        if (current.themeMode == mode) return
        prefs.edit().putString(KEY_THEME_MODE, mode.value).apply()
        _settings.value = current.copy(themeMode = mode)
        applyTheme(mode)
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

    fun setParseQuickActionEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.parseQuickActionEnabled == enabled) return
        prefs.edit().putBoolean(KEY_PARSE_QUICK_ACTION, enabled).apply()
        _settings.value = current.copy(parseQuickActionEnabled = enabled)
    }

    fun setDownloadRootRelativePath(relativePath: String) {
        val normalized = normalizeDownloadRoot(relativePath)
        val current = _settings.value
        if (current.downloadRootRelativePath == normalized) return
        prefs.edit().putString(KEY_DOWNLOAD_ROOT_RELATIVE_PATH, normalized).apply()
        _settings.value = current.copy(downloadRootRelativePath = normalized)
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
            parseQuickActionEnabled = prefs.getBoolean(KEY_PARSE_QUICK_ACTION, true),
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

    companion object {
        // Keep this a true compile-time constant for default values.
        const val DEFAULT_DOWNLOAD_ROOT = "Download/BiliTools"

        private const val PREFS_NAME = "app_settings"
        private const val KEY_ADD_METADATA = "add_metadata"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_DARK_MODE_PURE_BLACK = "dark_mode_pure_black"
        private const val KEY_DOWNLOAD_ROOT_RELATIVE_PATH = "download_root_relative_path"
        private const val KEY_CONFIRM_CELLULAR_DOWNLOAD = "confirm_cellular_download"
        private const val KEY_PARSE_QUICK_ACTION = "parse_quick_action"
    }
}
