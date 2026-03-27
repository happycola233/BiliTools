package com.happycola233.bilitools.ui.settings

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.SettingsRepository

sealed class SettingsDestination : NavKey {
    data object Main : SettingsDestination()
    data object General : SettingsDestination()
    data object Download : SettingsDestination()
    data object Appearance : SettingsDestination()
    data object About : SettingsDestination()
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings = settingsRepository.settings
    val backStack = mutableStateListOf<SettingsDestination>(SettingsDestination.Main)

    fun navigateTo(destination: SettingsDestination) {
        if (backStack.lastOrNull() == destination) return
        if (backStack.size < 2) {
            backStack.add(destination)
        } else {
            backStack[1] = destination
        }
    }

    fun popDestination() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun setAddMetadata(enabled: Boolean) {
        settingsRepository.setAddMetadata(enabled)
    }

    fun setThemeMode(mode: AppThemeMode, applyImmediately: Boolean = true) {
        settingsRepository.setThemeMode(mode, applyImmediately)
    }

    fun syncThemeMode() {
        settingsRepository.syncThemeMode()
    }

    fun setThemeColor(color: AppThemeColor) {
        settingsRepository.setThemeColor(color)
    }

    fun setDarkModePureBlack(enabled: Boolean) {
        settingsRepository.setDarkModePureBlack(enabled)
    }

    fun setConfirmCellularDownload(enabled: Boolean) {
        settingsRepository.setConfirmCellularDownload(enabled)
    }

    fun setHideDownloadedVideosInSystemAlbum(enabled: Boolean) {
        settingsRepository.setHideDownloadedVideosInSystemAlbum(enabled)
    }

    fun setParseQuickActionEnabled(enabled: Boolean) {
        settingsRepository.setParseQuickActionEnabled(enabled)
    }

    fun setDownloadsGlassDebugEnabled(enabled: Boolean) {
        settingsRepository.setDownloadsGlassDebugEnabled(enabled)
    }

    fun setDownloadRootFromTreeUri(uri: Uri): Boolean {
        return settingsRepository.setDownloadRootFromTreeUri(uri)
    }
}
