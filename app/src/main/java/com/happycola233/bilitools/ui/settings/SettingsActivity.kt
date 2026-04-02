package com.happycola233.bilitools.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.UpdateCheckResult
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.attachNavigationEventDispatcherOwner
import com.happycola233.bilitools.ui.applySettingsThemeOverlays
import com.happycola233.bilitools.ui.enableBiliEdgeToEdge
import com.happycola233.bilitools.ui.update.UpdateDialog
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private val viewModel: SettingsViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }

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
        viewModel.refreshIssueReportState()

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        attachNavigationEventDispatcherOwner(composeView, this, TAG)
        setContentView(composeView)

        composeView.setContent {
            val settings by viewModel.settings.collectAsState()
            val issueReportState by viewModel.issueReportState.collectAsState()
            val updateRepository = remember { applicationContext.appContainer.updateRepository }
            val scope = rememberCoroutineScope()
            val versionName = remember { normalizeVersionLabel(updateRepository.currentVersionName()) }
            val versionCode = remember { currentVersionCode() }
            var checkUpdateSummary by rememberSaveable {
                mutableStateOf(
                    buildCheckUpdateSummary(
                        currentVersion = versionName,
                        statusText = getString(R.string.settings_check_update_desc),
                    )
                )
            }
            var checkingUpdate by rememberSaveable { mutableStateOf(false) }
            var exportingIssueReport by rememberSaveable { mutableStateOf(false) }
            var clearingIssueReport by rememberSaveable { mutableStateOf(false) }

            BiliToolsSettingsContent(
                settings = settings,
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
                onCheckUpdate = {
                    if (!checkingUpdate) {
                        checkingUpdate = true
                        checkUpdateSummary = buildCheckUpdateSummary(
                            currentVersion = versionName,
                            statusText = getString(R.string.settings_check_update_desc_checking),
                        )
                        scope.launch {
                            when (val result = updateRepository.checkForUpdate()) {
                                is UpdateCheckResult.UpdateAvailable -> {
                                    checkUpdateSummary = buildCheckUpdateSummary(
                                        currentVersion = normalizeVersionLabel(result.currentVersion),
                                        statusText = getString(
                                            R.string.settings_check_update_desc_available,
                                            result.release.tagName,
                                        ),
                                    )
                                    UpdateDialog.show(
                                        activity = this@SettingsActivity,
                                        release = result.release,
                                        currentVersion = result.currentVersion,
                                    )
                                }

                                is UpdateCheckResult.UpToDate -> {
                                    checkUpdateSummary = buildCheckUpdateSummary(
                                        currentVersion = normalizeVersionLabel(result.currentVersion),
                                        statusText = getString(R.string.settings_check_update_desc_latest),
                                    )
                                    Toast.makeText(
                                        this@SettingsActivity,
                                        getString(R.string.settings_check_update_toast_latest),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }

                                is UpdateCheckResult.Failed -> {
                                    checkUpdateSummary = buildCheckUpdateSummary(
                                        currentVersion = normalizeVersionLabel(result.currentVersion),
                                        statusText = getString(R.string.settings_check_update_desc_failed),
                                    )
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
                onThemeColorChange = { color ->
                    if (settings.themeColor != color) {
                        viewModel.setThemeColor(color)
                    }
                },
                onParseQuickActionChange = viewModel::setParseQuickActionEnabled,
                onLiveActivityStyleNotificationChange = viewModel::setLiveActivityStyleNotificationEnabled,
                onAddMetadataChange = viewModel::setAddMetadata,
                onConfirmCellularChange = viewModel::setConfirmCellularDownload,
                onHideInAlbumChange = viewModel::setHideDownloadedVideosInSystemAlbum,
                onBlackThemeChange = { enabled ->
                    if (settings.darkModePureBlack != enabled) {
                        viewModel.setDarkModePureBlack(enabled)
                    }
                },
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

    override fun onResume() {
        super.onResume()
        viewModel.refreshIssueReportState()
    }

    override fun finish() {
        viewModel.syncThemeMode()
        super.finish()
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

    private fun buildCheckUpdateSummary(currentVersion: String, statusText: String): String {
        return buildString {
            append(getString(R.string.settings_about_version, currentVersion))
            append("（")
            append(statusText)
            append('）')
        }
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
