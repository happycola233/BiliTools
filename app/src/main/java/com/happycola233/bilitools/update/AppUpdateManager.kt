package com.happycola233.bilitools.update

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.ReleaseInfo

sealed interface UpdateStartResult {
    data object Started : UpdateStartResult

    data object AlreadyRunning : UpdateStartResult

    data object MissingAsset : UpdateStartResult

    data class Failed(val errorMessage: String) : UpdateStartResult
}

class AppUpdateManager(context: Context) {
    private val appContext = context.applicationContext

    fun startDownload(release: ReleaseInfo): UpdateStartResult {
        val asset = release.apkAsset ?: return UpdateStartResult.MissingAsset
        if (UpdateDownloadService.isDownloading) {
            return UpdateStartResult.AlreadyRunning
        }

        val intent = Intent(appContext, UpdateDownloadService::class.java).apply {
            action = UpdateDownloadService.ACTION_START
            putExtra(UpdateDownloadService.EXTRA_VERSION_NAME, release.versionName)
            putExtra(UpdateDownloadService.EXTRA_TAG_NAME, release.tagName)
            putExtra(UpdateDownloadService.EXTRA_RELEASE_TITLE, release.title ?: release.tagName)
            putExtra(UpdateDownloadService.EXTRA_RELEASE_URL, release.htmlUrl)
            putExtra(UpdateDownloadService.EXTRA_APK_DOWNLOAD_URL, asset.downloadUrl)
            putExtra(UpdateDownloadService.EXTRA_APK_SIZE_BYTES, asset.sizeBytes)
        }

        return runCatching {
            ContextCompat.startForegroundService(appContext, intent)
            UpdateStartResult.Started
        }.getOrElse { error ->
            UpdateStartResult.Failed(error.message ?: "Unknown error")
        }
    }

    fun ignoreRelease(versionName: String) {
        appContext.appContainer.settingsRepository.setIgnoredUpdateVersion(versionName)
    }
}
