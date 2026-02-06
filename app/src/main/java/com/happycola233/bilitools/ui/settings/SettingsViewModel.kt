package com.happycola233.bilitools.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.SettingsRepository

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings = settingsRepository.settings

    fun setAddMetadata(enabled: Boolean) {
        settingsRepository.setAddMetadata(enabled)
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsRepository.setThemeMode(mode)
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

    fun setParseQuickActionEnabled(enabled: Boolean) {
        settingsRepository.setParseQuickActionEnabled(enabled)
    }

    fun setDownloadRootFromTreeUri(uri: Uri): Boolean {
        return settingsRepository.setDownloadRootFromTreeUri(uri)
    }
}
