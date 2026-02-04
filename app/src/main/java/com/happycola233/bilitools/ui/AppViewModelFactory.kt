package com.happycola233.bilitools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.happycola233.bilitools.core.AppContainer
import com.happycola233.bilitools.ui.downloads.DownloadsViewModel
import com.happycola233.bilitools.ui.history.HistoryViewModel
import com.happycola233.bilitools.ui.login.LoginViewModel
import com.happycola233.bilitools.ui.parse.ParseViewModel
import com.happycola233.bilitools.ui.settings.SettingsViewModel

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(container.authRepository, container.strings)
            }
            modelClass.isAssignableFrom(ParseViewModel::class.java) -> {
                ParseViewModel(
                    container.mediaRepository,
                    container.extrasRepository,
                    container.downloadRepository,
                    container.exportRepository,
                    container.authRepository,
                    container.strings,
                )
            }
            modelClass.isAssignableFrom(DownloadsViewModel::class.java) -> {
                DownloadsViewModel(container.downloadRepository)
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(container.settingsRepository)
            }
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(
                    authRepository = container.authRepository,
                    extrasRepository = container.extrasRepository,
                    strings = container.strings,
                )
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        } as T
    }
}
