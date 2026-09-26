package com.happycola233.bilitools.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLanguage
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.UpdateCheckResult
import com.happycola233.bilitools.notification.isLiveUpdateSupported
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.attachNavigationEventDispatcherOwner
import com.happycola233.bilitools.ui.applySettingsThemeOverlays
import com.happycola233.bilitools.ui.enableBiliEdgeToEdge
import com.happycola233.bilitools.ui.update.UpdateDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private val viewModel: SettingsViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }
    private val liveUpdateSupported = MutableStateFlow(false)
    private val selectedLanguage = MutableStateFlow(AppLanguage.System)

    private val openFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        }

        val updated = viewModel.setDownloadRootFromTreeUri(uri)
        val messageRes = if (updated) {
            R.string.settings_download_location_updated
        } else {
            R.string.settings_download_location_invalid
        }
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableBiliEdgeToEdge()
        applySettingsThemeOverlays()
        super.onCreate(savedInstanceState)
        selectedLanguage.value = AppLanguage.current()
        viewModel.refreshIssueReportState()
        liveUpdateSupported.value = applicationContext.isLiveUpdateSupported()

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        attachNavigationEventDispatcherOwner(composeView, this, TAG)
        setContentView(composeView)

        composeView.setContent {
            val settings by viewModel.settings.collectAsState()
            val issueReportState by viewModel.issueReportState.collectAsState()
            val isLiveUpdateSupported by liveUpdateSupported.collectAsState()
            val currentLanguage by selectedLanguage.collectAsState()
            val updateRepository = remember { applicationContext.appContainer.updateRepository }
            val scope = rememberCoroutineScope()
            val versionName = remember { normalizeVersionLabel(updateRepository.currentVersionName()) }
            val versionCode = remember { currentVersionCode() }
            // 设置页切换语言时保留窗口和操作状态，展示文案始终由当前资源重新生成。
            var updateCheckResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
            var checkingUpdate by remember { mutableStateOf(false) }
            var exportingIssueReport by remember { mutableStateOf(false) }
            var clearingIssueReport by remember { mutableStateOf(false) }
            val checkedVersion = when (val result = updateCheckResult) {
                is UpdateCheckResult.UpdateAvailable -> normalizeVersionLabel(result.currentVersion)
                is UpdateCheckResult.UpToDate -> normalizeVersionLabel(result.currentVersion)
                else -> versionName
            }
            val updateStatus = if (checkingUpdate) {
                stringResource(R.string.settings_check_update_desc_checking)
            } else {
                when (val result = updateCheckResult) {
                    is UpdateCheckResult.UpdateAvailable -> stringResource(
                        R.string.settings_check_update_desc_available,
                        result.release.tagName,
                    )
                    is UpdateCheckResult.UpToDate -> stringResource(R.string.settings_check_update_desc_latest)
                    is UpdateCheckResult.Failed -> stringResource(R.string.settings_check_update_desc_failed)
                    null -> stringResource(R.string.settings_check_update_desc)
                }
            }
            val checkUpdateSummary = stringResource(
                R.string.settings_update_summary_format,
                stringResource(R.string.settings_about_version, checkedVersion),
                updateStatus,
            )

            BiliToolsSettingsContent(
                settings = settings,
                liveUpdateSupported = isLiveUpdateSupported,
                issueReportState = issueReportState,
                backStack = viewModel.backStack,
                checkUpdateSummary = checkUpdateSummary,
                versionName = versionName,
                versionCode = versionCode,
                issueReportExporting = exportingIssueReport,
                issueReportClearing = clearingIssueReport,
                onExit = ::finish,
                onNavigate = viewModel::navigateTo,
                onNavigateBack = viewModel::popDestination,
                selectedLanguage = currentLanguage,
                onLanguageChange = { language ->
                    // 所选语言与系统相同时配置不会改变，仍需立即更新选中标记。
                    selectedLanguage.value = language
                    AppLanguage.select(language)
                },
                onCheckUpdate = {
                    if (!checkingUpdate) {
                        checkingUpdate = true
                        scope.launch {
                            val result = updateRepository.checkForUpdate()
                            updateCheckResult = result
                            when (result) {
                                is UpdateCheckResult.UpdateAvailable -> {
                                    UpdateDialog.show(
                                        activity = this@SettingsActivity,
                                        release = result.release,
                                        currentVersion = result.currentVersion,
                                    )
                                }

                                is UpdateCheckResult.UpToDate -> {
                                    Toast.makeText(
                                        this@SettingsActivity,
                                        getString(R.string.settings_check_update_toast_latest),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }

                                is UpdateCheckResult.Failed -> {
                                    Toast.makeText(
                                        this@SettingsActivity,
                                        getString(
                                            R.string.settings_check_update_toast_failed,
                                            result.errorMessage,
                                        ),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                            checkingUpdate = false
                        }
                    }
                },
                onOpenDownloadLocationPicker = { path ->
                    openFolderLauncher.launch(buildInitialTreeUri(path))
                },
                onThemeModeChange = { mode ->
                    viewModel.setThemeMode(mode, applyImmediately = false)
                },
                onDynamicColorEnabledChange = viewModel::setDynamicColorEnabled,
                onThemeColorChange = { color ->
                    if (settings.themeColor != color) {
                        viewModel.setThemeColor(color)
                    }
                },
                onLiveActivityStyleNotificationChange = viewModel::setLiveActivityStyleNotificationEnabled,
                onLiveUpdateIconChange = viewModel::setLiveUpdateIcon,
                onDefaultDownloadQualityChange = viewModel::setDefaultDownloadQuality,
                onDownloadPreferenceMemoryChange = viewModel::setDownloadPreferenceMemory,
                onAddMetadataChange = viewModel::setAddMetadata,
                onDownloadMetadataChange = viewModel::setDownloadMetadata,
                onConvertXmlDanmakuToAssChange = viewModel::setConvertXmlDanmakuToAss,
                onConvertAudioToMp3Change = viewModel::setConvertAudioToMp3,
                onConvertVideoToMp4Change = viewModel::setConvertVideoToMp4,
                onMaxConcurrentDownloadsChange = viewModel::setMaxConcurrentDownloads,
                onConfirmCellularChange = viewModel::setConfirmCellularDownload,
                onHideInAlbumChange = viewModel::setHideDownloadedVideosInSystemAlbum,
                onNamingTopLevelFolderModeChange = viewModel::setNamingTopLevelFolderMode,
                onNamingOverwriteExistingFilesChange = viewModel::setNamingOverwriteExistingFiles,
                onNamingCleanSeparatorsChange = viewModel::setNamingCleanSeparators,
                onNamingShowSinglePageNumberChange = viewModel::setNamingShowSinglePageNumber,
                onNamingTemplateChange = viewModel::setNamingTemplate,
                onNamingTemplateReset = viewModel::clearNamingTemplate,
                onRestoreNamingDefaults = {
                    viewModel.restoreNamingDefaults()
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.settings_naming_restore_defaults_done),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onBlackThemeChange = { enabled ->
                    if (settings.darkModePureBlack != enabled) {
                        viewModel.setDarkModePureBlack(enabled)
                    }
                },
                onLaunchSplashAnimationChange = viewModel::setLaunchSplashAnimationEnabled,
                onLiquidBottomTabsChange = viewModel::setLiquidBottomTabsEnabled,
                onLiquidGlassPanelsChange = viewModel::setLiquidGlassPanelsEnabled,
                onLiquidBarWidthChange = viewModel::setLiquidBarWidthFraction,
                onHapticFeedbackLevelChange = viewModel::setHapticFeedbackLevel,
                onGlassDebugChange = viewModel::setDownloadsGlassDebugEnabled,
                onIssueReportLoggingChange = viewModel::setIssueReportDetailedLoggingEnabled,
                onExportIssueReport = {
                    if (!exportingIssueReport) {
                        exportingIssueReport = true
                        scope.launch {
                            val exportUri = viewModel.exportDetailedIssueLogs()
                            if (exportUri != null) {
                                Toast.makeText(
                                    this@SettingsActivity,
                                    getString(R.string.settings_issue_report_export_success),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                shareIssueReport(exportUri)
                            } else {
                                Toast.makeText(
                                    this@SettingsActivity,
                                    getString(R.string.settings_issue_report_export_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            exportingIssueReport = false
                        }
                    }
                },
                onClearIssueReport = {
                    if (!clearingIssueReport) {
                        clearingIssueReport = true
                        scope.launch {
                            viewModel.clearDetailedIssueLogs()
                            Toast.makeText(
                                this@SettingsActivity,
                                getString(R.string.settings_issue_report_clear_success),
                                Toast.LENGTH_SHORT,
                            ).show()
                            clearingIssueReport = false
                        }
                    }
                },
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // 由 AppCompat 更新资源、Compose 接收配置变化；不拆除窗口和现有组合，避免闪屏。
        super.onConfigurationChanged(newConfig)
        // 旧版 Android 的 AppCompat 只分发资源配置，保留窗口时还需同步其布局方向。
        window.decorView.layoutDirection = newConfig.layoutDirection
        selectedLanguage.value = AppLanguage.current()
    }

    override fun onResume() {
        super.onResume()
        selectedLanguage.value = AppLanguage.current()
        viewModel.refreshIssueReportState()
        liveUpdateSupported.value = applicationContext.isLiveUpdateSupported()
    }

    override fun onDestroy() {
        val shouldSyncThemeMode = isFinishing
        super.onDestroy()
        if (shouldSyncThemeMode) {
            /*
             * 其他未接管 uiMode 的 AppCompatActivity 仍会重建；主界面则收到配置变化并重组。
             * 等当前设置页完成退场后再同步，避免设置页自身参与这轮主题切换。
             */
            applicationContext.appContainer.settingsRepository.syncThemeMode()
        }
    }

    private fun currentVersionCode(): Long {
        @Suppress("DEPRECATION")
        return runCatching {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        }.getOrDefault(0L)
    }

    private fun buildInitialTreeUri(relativePath: String): Uri? {
        val normalized = relativePath
            .replace('\\', '/')
            .trim()
            .trim('/')
            .takeIf { it.isNotBlank() }
            ?: return null
        val treeId = "primary:$normalized"
        return runCatching {
            DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE_PROVIDER, treeId)
        }.getOrNull()
    }

    private fun shareIssueReport(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(
                Intent.createChooser(
                    intent,
                    getString(R.string.settings_issue_report_export_title),
                ),
            )
        }.onFailure {
            if (it is ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    getString(R.string.settings_issue_report_share_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun normalizeVersionLabel(version: String): String {
        return version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .ifBlank { "0" }
    }

    companion object {
        private const val EXTERNAL_STORAGE_PROVIDER = "com.android.externalstorage.documents"
        private const val TAG = "SettingsActivity"
    }
}
