package com.happycola233.bilitools.ui.diagnostics

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.DiagnosticReport
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.*
import com.happycola233.bilitools.ui.downloads.DownloadsDetailsSheet
import com.happycola233.bilitools.ui.settings.AboutSettingsScreen
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticReportScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test @Config(qualifiers = "zh-rCN-ldltr-w411dp-h891dp") fun zh411Light() = captureVariants("zh-411-light", AppThemeMode.Light, false)
    @Test @Config(qualifiers = "zh-rCN-ldltr-w411dp-h891dp") fun zh411Dark() = captureVariants("zh-411-dark", AppThemeMode.Dark, false)
    @Test @Config(qualifiers = "zh-rCN-ldltr-w320dp-h891dp") fun zh320Light() = captureVariants("zh-320-light", AppThemeMode.Light, false)
    @Test @Config(qualifiers = "zh-rCN-ldltr-w320dp-h891dp") fun zh320Dark() = captureVariants("zh-320-dark", AppThemeMode.Dark, false)
    @Test @Config(qualifiers = "ar-ldrtl-w411dp-h891dp") fun ar411Light() = captureVariants("ar-411-light", AppThemeMode.Light, true)
    @Test @Config(qualifiers = "ar-ldrtl-w411dp-h891dp") fun ar411Dark() = captureVariants("ar-411-dark", AppThemeMode.Dark, true)
    @Test @Config(qualifiers = "ar-ldrtl-w320dp-h891dp") fun ar320Light() = captureVariants("ar-320-light", AppThemeMode.Light, true)
    @Test @Config(qualifiers = "ar-ldrtl-w320dp-h891dp") fun ar320Dark() = captureVariants("ar-320-dark", AppThemeMode.Dark, true)

    private fun captureVariants(name: String, mode: AppThemeMode, rtl: Boolean) {
        val context = RuntimeEnvironment.getApplication()
        var screen by mutableIntStateOf(0)
        var expanded by mutableStateOf(false)
        var clearCalls = 0
        val report = DiagnosticReport("", """
            # BiliTools diagnostic report

            [Summary]
            version=3.0 (14)
            device=Example device
            android=15 (SDK 35)
            installedAbi=arm64

            [Account]
            loggedIn=true
            vipStatus=1

            [Recent problems]
            code=-352 message=risk control

            [Settings]
            convertVideoToMp4=true
        """.trimIndent(), 142, "2026-09-28T09:00Z – 2026-09-28T10:00Z")
        val group = DownloadGroup(
            id = 7, title = if (rtl) "فيديو تجريبي" else "示例视频", subtitle = null,
            bvid = "BV1test", createdAt = 0, relativePath = "Download/BiliTools/Example",
            tasks = listOf(DownloadItem(42, 7, DownloadTaskType.AudioVideo,
                title = if (rtl) "فيديو تجريبي" else "示例视频", fileName = "Example-1080P.mp4", url = "",
                status = DownloadStatus.Failed, progress = 12,
                failureMessage = DownloadMessage(DownloadMessageCode.FailureMerge),
                mediaParams = DownloadMediaParams(resolutionId = 80, codecType = VideoCodec.Hevc, audioQualityId = 30280),
            )),
        )
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Periwinkle)) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            when (screen) {
                                0 -> AboutSettingsScreen(
                                    versionName = "3.0", versionCode = 14,
                                    checkUpdateSummary = "3.0 (14)",
                                    onCheckUpdate = {}, onOpenSourceLicenses = {},
                                    onFeedback = { screen = 1 }, onClearDiagnostics = { clearCalls++ }, onBack = {},
                                )
                                1 -> DiagnosticReportSheet(
                                    description = "", onDescriptionChange = {}, report = report,
                                    expanded = expanded, busy = false, onTogglePreview = { expanded = !expanded },
                                    onShare = {}, onSave = {}, onGitHub = {}, onDismiss = {},
                                )
                                2 -> DiagnosticCrashDialog(onExport = {}, onIgnore = {})
                                3 -> DownloadsDetailsSheet(group, onDismiss = {})
                            }
                        }
                    }
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.diagnostic_clear_description)))
        compose.onNodeWithText(context.getString(R.string.diagnostic_feedback)).assertIsDisplayed()
        saveImage(compose.onRoot(), "$name-about")
        compose.onNodeWithText(context.getString(R.string.diagnostic_clear)).performClick()
        compose.onNodeWithText(context.getString(R.string.diagnostic_clear_explanation)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, clearCalls) }
        saveImage(compose.onNode(isDialog()), "$name-clear-confirm")
        compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(0, clearCalls) }
        compose.onNodeWithText(context.getString(R.string.diagnostic_clear)).performClick()
        compose.onNodeWithText(context.getString(R.string.diagnostic_clear_confirm)).performClick()
        compose.runOnIdle { assertEquals(1, clearCalls) }
        compose.onNodeWithText(context.getString(R.string.diagnostic_feedback)).performClick()
        compose.onNodeWithText(context.getString(R.string.diagnostic_share)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostic_save)).assertIsDisplayed()
        saveImage(compose.onNode(isDialog()), "$name-sheet-collapsed")
        compose.onNodeWithText(context.getString(R.string.diagnostic_preview)).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText(report.preview).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostic_share)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostic_save)).assertIsDisplayed()
        saveImage(compose.onNode(isDialog()), "$name-sheet-expanded")
        compose.onNodeWithText(context.getString(R.string.diagnostic_attach_hint)).assertIsDisplayed()
        saveImage(compose.onNode(isDialog()), "$name-sheet-actions")
        compose.runOnIdle { screen = 2 }
        compose.onNodeWithText(context.getString(R.string.diagnostic_crash_title)).assertIsDisplayed()
        saveImage(compose.onNode(isDialog()), "$name-crash")
        compose.runOnIdle { screen = 3 }
        compose.onNodeWithText(context.getString(R.string.diagnostic_feedback)).performScrollTo().assertIsDisplayed()
        saveImage(compose.onNode(isDialog()), "$name-failed-download")
        compose.runOnIdle { screen = 1; expanded = false }
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNodeWithText(context.getString(R.string.diagnostic_problem_hint)).assertIsDisplayed()
        saveImage(compose.onNode(isDialog()), "$name-sheet-focused")
    }

    private fun saveImage(node: SemanticsNodeInteraction, name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val file = File("../.tmp/diagnostic-report-compact/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
