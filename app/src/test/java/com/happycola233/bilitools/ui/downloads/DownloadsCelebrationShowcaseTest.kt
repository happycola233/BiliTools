package com.happycola233.bilitools.ui.downloads

import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.ui.MainCollapsingTopBar
import com.happycola233.bilitools.ui.MainTopBarExpandedHeight
import com.happycola233.bilitools.ui.eggs.saveEggShowcase
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 下载页里一批任务全部完成时撒彩纸的整页动画帧，只用于人工查看。 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h760dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsCelebrationShowcaseTest {
    @get:Rule val compose = createComposeRule()

    private val mib = 1024L * 1024L
    private val createdAt = 1_789_283_696_000L
    private val params = DownloadMediaParams("1080P 高清", "HEVC (H.265)", "192K")

    private fun task(id: Long, groupId: Long, status: DownloadStatus, progress: Int, totalMb: Long) = DownloadItem(
        id = id, groupId = groupId, taskType = DownloadTaskType.AudioVideo, title = "音视频", fileName = "$id.mp4", url = "",
        status = status, progress = progress, totalBytes = totalMb * mib, downloadedBytes = totalMb * mib * progress / 100,
        speedBytesPerSec = if (status == DownloadStatus.Running) 12 * mib else 0, mediaParams = params,
        localUri = if (status == DownloadStatus.Success) "content://media/$id" else null,
        outputBytes = if (status == DownloadStatus.Success) totalMb * mib else null,
    )

    private fun groups(done: Boolean) = listOf(
        DownloadGroup(
            id = 1, title = "一口气了解宋朝三百年", subtitle = null, bvid = "BV1ee", createdAt = createdAt,
            tasks = listOf(task(11, 1, if (done) DownloadStatus.Success else DownloadStatus.Running, if (done) 100 else 96, 642)),
        ),
        DownloadGroup(
            id = 2, title = "猫猫的一天 vlog", subtitle = null, bvid = "BV1ff", createdAt = createdAt,
            tasks = listOf(task(21, 2, DownloadStatus.Success, 100, 210)),
        ),
    )

    @Test fun light() = capture(dark = false)

    @Test fun dark() = capture(dark = true)

    private fun capture(dark: Boolean) {
        val settings = AppSettings(themeMode = if (dark) AppThemeMode.Dark else AppThemeMode.Light, themeColor = AppThemeColor.Periwinkle)
        var current by mutableStateOf(groups(done = false))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val themed = ContextThemeWrapper(LocalContext.current, R.style.Theme_BiliTools)
            CompositionLocalProvider(LocalContext provides themed) {
                BiliToolsTheme(settings) {
                    Box(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor)) {
                        val trigger = rememberDownloadCelebrationTrigger(current, visible = true)
                        DownloadsPage(current, settings, trigger)
                        MainCollapsingTopBar(title = "下载", state = rememberTopAppBarState())
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        val name = "anim/downloads-confetti-${if (dark) "dark" else "light"}"
        var index = 0
        fun frame() = compose.onRoot().saveEggShowcase("$name/${"%04d".format(index++)}")
        repeat(6) { compose.mainClock.advanceTimeBy(40); frame() }
        compose.runOnIdle { current = groups(done = true) }
        compose.waitForIdle()
        repeat(64) { compose.mainClock.advanceTimeBy(40); frame() }
    }

    @androidx.compose.runtime.Composable
    private fun DownloadsPage(groups: List<DownloadGroup>, settings: AppSettings, celebrationTrigger: Int) {
        DownloadsScreenContent(
            groups = groups,
            selectionMode = false,
            selectedGroupIds = emptySet(),
            expandedGroupIds = emptySet(),
            collapsedSections = emptySet(),
            swipedGroupId = null,
            dialogState = null,
            contentTopPadding = MainTopBarExpandedHeight,
            resumeAllCount = 0,
            pauseAllCount = groups.count { !it.isCompleted },
            completedGroupCount = groups.count { it.isCompleted },
            liquidGlassPanelsEnabled = settings.liquidGlassPanelsEnabled,
            glassDebugEnabled = false,
            glassCornerRadiusDp = settings.downloadsGlassCornerRadiusDp,
            glassBlurRadiusDp = settings.downloadsGlassBlurRadiusDp,
            glassRefractionHeightDp = settings.downloadsGlassRefractionHeightDp,
            glassRefractionAmountFrac = settings.downloadsGlassRefractionAmountFrac,
            glassChromaticAberration = settings.downloadsGlassChromaticAberration,
            glassSurfaceAlpha = settings.downloadsGlassSurfaceAlpha,
            barGlassBlurRadiusDp = settings.liquidBarGlassBlurRadiusDp,
            barGlassRefractionHeightDp = settings.liquidBarGlassRefractionHeightDp,
            barGlassRefractionAmountFrac = settings.liquidBarGlassRefractionAmountFrac,
            barGlassChromaticAberration = settings.liquidBarGlassChromaticAberration,
            barGlassSurfaceAlpha = settings.liquidBarGlassSurfaceAlpha,
            onOpenParse = {}, onBatchManage = {}, onResumeAll = {}, onPauseAll = {}, onClearCompleted = {}, onClearAll = {},
            onClearRecords = {}, onDeleteFiles = {},
            onDialogDismiss = {}, onDialogConfirm = {}, onToggleSection = {},
            onToggleGroupExpanded = {},
            onSwipedGroupChange = {}, onGroupSelectionToggle = {}, onGroupPause = {}, onGroupResume = {},
            onGroupReparse = {}, onGroupShowDetails = {}, onGroupDelete = { _, _ -> },
            onTaskPauseResume = {}, onTaskRetry = {}, onTaskClick = { _, _ -> },
            onDragSelectionStart = {}, onDragSelectionRange = {}, onDragSelectionEnd = {},
            onGlassCornerRadiusChange = {}, onGlassBlurRadiusChange = {}, onGlassRefractionHeightChange = {},
            onGlassRefractionAmountChange = {}, onGlassChromaticAberrationChange = {}, onGlassSurfaceAlphaChange = {},
            onGlassReset = {}, onBarGlassBlurRadiusChange = {}, onBarGlassRefractionHeightChange = {},
            onBarGlassRefractionAmountChange = {}, onBarGlassChromaticAberrationChange = {},
            onBarGlassSurfaceAlphaChange = {}, onBarGlassReset = {},
            celebrationTrigger = celebrationTrigger,
        )
    }
}
