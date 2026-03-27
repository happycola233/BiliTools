package com.happycola233.bilitools.core

import android.content.Context
import com.happycola233.bilitools.data.AuthRepository
import com.happycola233.bilitools.data.DownloadRepository
import com.happycola233.bilitools.data.ExportRepository
import com.happycola233.bilitools.data.ExtrasRepository
import com.happycola233.bilitools.data.IssueReportRepository
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.data.MediaRepository
import com.happycola233.bilitools.data.UpdateRepository
import com.happycola233.bilitools.data.VideoRepository
import com.happycola233.bilitools.update.AppUpdateManager
import com.happycola233.bilitools.update.GitHubRouteManager
import com.happycola233.bilitools.update.UpdatePackageCleanupManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val strings by lazy { StringProvider(appContext) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val diagnosticLogStore by lazy { DiagnosticLogStore(appContext, settingsRepository) }
    val cookieStore by lazy { CookieStore(appContext) }
    val httpClient by lazy { BiliHttpClient(cookieStore, settingsRepository) }
    val wbiSigner by lazy { WbiSigner(httpClient) }
    val gitHubRouteManager by lazy { GitHubRouteManager(appContext) }

    val authRepository by lazy { AuthRepository(httpClient, cookieStore, wbiSigner) }
    val videoRepository by lazy { VideoRepository(httpClient, wbiSigner, cookieStore) }
    val mediaRepository by lazy { MediaRepository(httpClient, wbiSigner, cookieStore) }
    val extrasRepository by lazy { ExtrasRepository(httpClient, wbiSigner) }
    val updateRepository by lazy { UpdateRepository(appContext, gitHubRouteManager, settingsRepository) }
    val appUpdateManager by lazy { AppUpdateManager(appContext) }
    internal val updatePackageCleanupManager by lazy { UpdatePackageCleanupManager(appContext) }
    val downloadRepository by lazy { DownloadRepository(appContext, cookieStore, settingsRepository) }
    val exportRepository by lazy { ExportRepository(appContext) }
    val issueReportRepository by lazy {
        IssueReportRepository(
            context = appContext,
            settingsRepository = settingsRepository,
            exportRepository = exportRepository,
            diagnosticLogStore = diagnosticLogStore,
        )
    }
}
