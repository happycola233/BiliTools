package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadMessage
import com.happycola233.bilitools.data.model.DownloadMessageCode
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.ui.MainCollapsingTopBar
import com.happycola233.bilitools.ui.MainTopBarExpandedHeight
import com.happycola233.bilitools.ui.liquidtabs.LiquidGlassStyle
import com.happycola233.bilitools.ui.liquidtabs.MainLiquidBottomBar
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/** 下载页整页效果图：只用于人工比对界面改版前后的观感，不做断言。 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsShowcaseScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun light() = captureAll(AppThemeMode.Light)
    @Test fun dark() = captureAll(AppThemeMode.Dark)

    private val mib = 1024L * 1024L
    private val createdAt = 1_789_283_696_000L

    private fun task(
        id: Long, groupId: Long, type: DownloadTaskType, title: String, status: DownloadStatus,
        progress: Int = if (status == DownloadStatus.Success) 100 else 0,
        totalMb: Long = 0, downloadedMb: Long = 0, speedMb: Long = 0, eta: Long? = null,
        params: DownloadMediaParams? = null, failure: DownloadMessageCode? = null,
        outputMissing: Boolean = false, indeterminate: Boolean = false,
    ) = DownloadItem(
        id = id, groupId = groupId, taskType = type, title = title, fileName = "$title.mp4", url = "",
        status = status, progress = progress, progressIndeterminate = indeterminate,
        totalBytes = totalMb * mib, downloadedBytes = downloadedMb * mib, speedBytesPerSec = speedMb * mib,
        etaSeconds = eta, userPaused = status == DownloadStatus.Paused, mediaParams = params,
        failureMessage = failure?.let(::DownloadMessage), outputMissing = outputMissing,
        localUri = if (status == DownloadStatus.Success) "content://media/$id" else null,
        outputBytes = if (status == DownloadStatus.Success && type == DownloadTaskType.AudioVideo) totalMb * mib else null,
    )

    private val fourK = DownloadMediaParams("4K 超高清", "AVC (H.264)", "192K")
    private val hd = DownloadMediaParams("1080P 高清", "HEVC (H.265)", "192K")

    private val groups = listOf(
        DownloadGroup(
            id = 1, title = "2.5小时速通上海高中数学知识点+答题技巧+易错点", subtitle = null, bvid = "BV1aa",
            createdAt = createdAt, tasks = listOf(
                task(11, 1, DownloadTaskType.AudioVideo, "音视频", DownloadStatus.Running, 7, 1126, 86, 13, 77, fourK),
                task(12, 1, DownloadTaskType.AiSummary, "AI 总结", DownloadStatus.Success),
                task(13, 1, DownloadTaskType.DanmakuHistory, "历史弹幕", DownloadStatus.Success),
            ),
        ),
        DownloadGroup(
            id = 2, title = "【4K】城市夜景延时摄影合集", subtitle = null, bvid = "BV1bb",
            createdAt = createdAt, tasks = listOf(
                task(21, 2, DownloadTaskType.AudioVideo, "音视频", DownloadStatus.Paused, 42, 820, 344, params = hd),
                task(22, 2, DownloadTaskType.Cover, "封面", DownloadStatus.Success),
            ),
        ),
        DownloadGroup(
            id = 3, title = "从零开始画出夏日的光影：线稿与色彩的练习", subtitle = null, bvid = "BV1cc",
            createdAt = createdAt, tasks = listOf(
                task(31, 3, DownloadTaskType.AudioVideo, "音视频", DownloadStatus.Failed, 63, 512, 322, params = hd,
                    failure = DownloadMessageCode.FailureMerge),
                task(32, 3, DownloadTaskType.Subtitle, "中文字幕", DownloadStatus.Failed,
                    failure = DownloadMessageCode.FailureDownloadUnknown),
                task(33, 3, DownloadTaskType.Cover, "封面", DownloadStatus.Success),
                task(34, 3, DownloadTaskType.AiSummary, "AI 总结", DownloadStatus.Unavailable),
            ),
        ),
        DownloadGroup(
            id = 4, title = "高考物理压轴题精讲", subtitle = null, bvid = "BV1dd",
            createdAt = createdAt, tasks = listOf(
                task(41, 4, DownloadTaskType.AudioVideo, "音视频", DownloadStatus.Pending, params = hd),
            ),
        ),
        DownloadGroup(
            id = 5, title = "一口气了解宋朝三百年", subtitle = null, bvid = "BV1ee",
            createdAt = createdAt - 86_400_000, tasks = listOf(
                task(51, 5, DownloadTaskType.AudioVideo, "音视频", DownloadStatus.Success, totalMb = 642, params = hd),
                task(52, 5, DownloadTaskType.Subtitle, "中文字幕", DownloadStatus.Success),
            ),
        ),
        DownloadGroup(
            id = 6, title = "猫猫的一天 vlog", subtitle = null, bvid = "BV1ff",
            createdAt = createdAt - 2 * 86_400_000, tasks = listOf(
                task(61, 6, DownloadTaskType.AudioVideo, "音视频", DownloadStatus.Success, totalMb = 210,
                    params = hd, outputMissing = true),
            ),
        ),
    )

    private class Scenario(
        val name: String,
        val groups: List<DownloadGroup>,
        val expanded: Set<Long> = emptySet(),
        val selection: Set<Long>? = null,
        val dialog: DownloadsDialogState? = null,
        val openFab: Boolean = false,
        val openTaskMenu: Boolean = false,
        val pressTitle: String? = null,
        val swipeTitle: String? = null,
    )

    private fun scenarios() = listOf(
        Scenario("01-list", groups),
        Scenario("02-expanded-running", groups, expanded = setOf(1)),
        Scenario("03-expanded-failed", groups.drop(2), expanded = setOf(3)),
        Scenario("04-selection", groups, selection = setOf(1)),
        Scenario("05-batch-confirm", groups, selection = setOf(1, 5), dialog = DownloadsDialogState.BatchDelete(setOf(1, 5), false)),
        Scenario("06-fab-menu", groups, openFab = true),
        Scenario("07-task-menu", groups.drop(4), expanded = setOf(5), openTaskMenu = true),
        Scenario("08-empty", emptyList()),
        Scenario("09-expanded-completed", groups.drop(4), expanded = setOf(5)),
        Scenario("10-pressed-card", groups.drop(4), pressTitle = groups[4].title),
        // 拖到停靠宽度之前，分段卡片的间隙里不能透出背后的删除按钮。
        Scenario("11-swipe-expanded", groups.drop(4), expanded = setOf(5), swipeTitle = groups[4].title),
    )

    private fun captureAll(mode: AppThemeMode) {
        var scenario by mutableStateOf(scenarios().first())
        val overlayState = DownloadsTaskActionsOverlayState()
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_BiliTools)
        val settings = AppSettings(themeMode = mode, themeColor = AppThemeColor.Periwinkle)
        val density = compose.density
        compose.setContent {
            // 与真机一样走硬件加速分支，让液态玻璃底栏和浮层按实际效果绘制。
            val attachInfo = ReflectionHelpers.getField<Any>(LocalView.current, "mAttachInfo")
            ReflectionHelpers.setField(attachInfo, "mHardwareAccelerated", true)
            CompositionLocalProvider(LocalContext provides context) {
                BiliToolsTheme(settings) {
                    val current = scenario
                    val contentBackdrop = rememberLayerBackdrop()
                    val topBarState = rememberTopAppBarState()
                    val selecting = current.selection != null
                    val selectedCount = current.selection.orEmpty().size
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
                            // 每个场景重建页面，菜单展开等内部状态不带入下一张。
                            key(current.name) { DownloadsScreenContentShowcase(current, settings) }
                            // 与主壳相同的多选顶栏组合方式。
                            MainCollapsingTopBar(
                                title = if (selecting) {
                                    pluralStringResource(R.plurals.downloads_selection_title, selectedCount, selectedCount)
                                } else {
                                    context.getString(R.string.nav_downloads)
                                },
                                state = topBarState,
                                titleKey = selecting,
                                navigationIcon = { DownloadsSelectionCloseButton(visible = selecting, onClick = {}) },
                                actions = {
                                    DownloadsSelectAllButton(
                                        visible = selecting,
                                        selectedGroupIds = current.selection.orEmpty(),
                                        groups = MutableStateFlow(current.groups),
                                        onToggleSelectAll = {},
                                    )
                                },
                            )
                        }
                        // 多选时主壳把底栏沉到屏幕外，这里直接不绘制。
                        if (!selecting) MainLiquidBottomBar(
                            selectedTabIndex = { 1 }, onTabSelected = {}, backdrop = contentBackdrop,
                            glassStyle = LiquidGlassStyle(
                                blurRadiusDp = settings.liquidBarGlassBlurRadiusDp,
                                refractionHeightDp = settings.liquidBarGlassRefractionHeightDp,
                                refractionAmountFrac = settings.liquidBarGlassRefractionAmountFrac,
                                chromaticAberration = settings.liquidBarGlassChromaticAberration,
                            ),
                            surfaceAlpha = settings.liquidBarGlassSurfaceAlpha,
                            widthFraction = settings.liquidBarWidthFraction,
                            modifier = Modifier.fillMaxSize().align(Alignment.BottomCenter),
                        )
                        DownloadsTaskActionsOverlay(overlayState, contentBackdrop, settings.liquidGlassPanelsEnabled)
                    }
                }
            }
        }
        scenarios().forEach { next ->
            compose.runOnIdle { overlayState.dismissImmediately(); scenario = next }
            compose.mainClock.advanceTimeBy(2_000)
            if (next.openFab) {
                compose.onNodeWithContentDescription(context.getString(R.string.downloads_actions_menu)).performClick()
                compose.mainClock.advanceTimeBy(2_000)
            }
            if (next.openTaskMenu) {
                val anchor = compose.onNodeWithText("音视频", useUnmergedTree = true).getBoundsInRoot()
                compose.runOnIdle {
                    overlayState.show(
                        DownloadsTaskActionsOverlayRequest(
                            51, "音视频.mp4",
                            with(density) { Rect(16.dp.toPx(), anchor.top.toPx() - 12.dp.toPx(), 395.dp.toPx(), anchor.bottom.toPx() + 52.dp.toPx()) },
                            settings.toDownloadsGlassStyle(),
                            listOf(DownloadsTaskAction.Open, DownloadsTaskAction.Share, DownloadsTaskAction.Delete),
                        ),
                    ) {}
                }
                compose.mainClock.advanceTimeBy(2_000)
            }
            next.pressTitle?.let { title ->
                compose.mainClock.autoAdvance = false
                compose.onNodeWithText(title).performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(400)
            }
            next.swipeTitle?.let { title ->
                compose.onNodeWithText(title).performTouchInput {
                    down(center)
                    moveBy(Offset(-(viewConfiguration.touchSlop + with(density) { 60.dp.toPx() }), 0f), delayMillis = 32)
                }
                compose.mainClock.advanceTimeBy(400)
            }
            if (next.pressTitle == null && next.swipeTitle == null) compose.waitForIdle()
            val output = File("../.tmp/downloads-md3e/current/${next.name}-${mode.name.lowercase()}.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            (next.pressTitle ?: next.swipeTitle)?.let { title ->
                compose.onNodeWithText(title).performTouchInput { cancel() }
                compose.mainClock.autoAdvance = true
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DownloadsScreenContentShowcase(
        scenario: Scenario,
        settings: AppSettings,
    ) {
        val selection = scenario.selection
        var expanded by remember(scenario) { mutableStateOf(scenario.expanded) }
        DownloadsScreenContent(
            groups = scenario.groups,
            selectionMode = selection != null,
            selectedGroupIds = selection.orEmpty(),
            expandedGroupIds = expanded,
            collapsedSections = emptySet(),
            swipedGroupId = null,
            dialogState = scenario.dialog,
            contentTopPadding = MainTopBarExpandedHeight,
            resumeAllCount = 1,
            pauseAllCount = 2,
            completedGroupCount = scenario.groups.count { it.isCompleted },
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
            onToggleGroupExpanded = { id -> expanded = if (id in expanded) expanded - id else expanded + id },
            onSwipedGroupChange = {}, onGroupSelectionToggle = {}, onGroupPause = {}, onGroupResume = {},
            onGroupReparse = {}, onGroupShowDetails = {}, onGroupDelete = { _, _ -> },
            onTaskPauseResume = {}, onTaskRetry = {}, onTaskClick = { _, _ -> },
            onDragSelectionStart = {}, onDragSelectionRange = {}, onDragSelectionEnd = {},
            onGlassCornerRadiusChange = {}, onGlassBlurRadiusChange = {}, onGlassRefractionHeightChange = {},
            onGlassRefractionAmountChange = {}, onGlassChromaticAberrationChange = {}, onGlassSurfaceAlphaChange = {},
            onGlassReset = {}, onBarGlassBlurRadiusChange = {}, onBarGlassRefractionHeightChange = {},
            onBarGlassRefractionAmountChange = {}, onBarGlassChromaticAberrationChange = {},
            onBarGlassSurfaceAlphaChange = {}, onBarGlassReset = {},
        )
    }
}
