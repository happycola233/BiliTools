package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.AppDialogDefaults
import com.happycola233.bilitools.ui.isLiquidGlassSupported
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsPanelsTest {
    @get:Rule val compose = createComposeRule()
    private val title = "音视频 - 线稿绘画效果测试 - 1080P 高清.mp4"

    // Robolectric API 33 的子 shader 编译有已知限制，玻璃渲染在 API 35 验证；API 33 在设置页验证开关与宽度项。
    // https://github.com/robolectric/robolectric/issues/9691
    @Test fun lightPanelsKeepLayoutAndActions() = verifyPanels(AppThemeMode.Light, false)
    @Test fun darkPanelsKeepLayoutAndActions() = verifyPanels(AppThemeMode.Dark, false)
    @Test fun pureBlackPanelsKeepLayoutAndActions() = verifyPanels(AppThemeMode.Dark, true)

    @Test
    @Config(sdk = [29, 31, 32])
    fun oldAndroidUsesOpaqueMaterialEvenWithZeroGlassTint() = verifyOpaqueFallback(hardwareAccelerated = true)

    @Test fun softwareRenderingUsesOpaqueMaterialEvenWithZeroGlassTint() = verifyOpaqueFallback(hardwareAccelerated = false)

    private fun verifyOpaqueFallback(hardwareAccelerated: Boolean) {
        var expectedColor = 0
        var supported = true
        val settings = AppSettings(themeMode = AppThemeMode.Dark, darkModePureBlack = true)
        val view = graphicsView(hardwareAccelerated)
        compose.setContent {
            CompositionLocalProvider(LocalView provides view) {
                BiliToolsTheme(settings) {
                    supported = isLiquidGlassSupported()
                    expectedColor = AppDialogDefaults.containerColor.toArgb()
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.size(240.dp).testTag("surface")) {
                        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                            Box(Modifier.fillMaxWidth().height(120.dp).background(Color.White))
                            Box(Modifier.fillMaxSize().background(Color.Magenta))
                        }
                        Box(
                            Modifier.fillMaxSize().downloadsPanelSurface(
                                backdrop = backdrop,
                                style = settings.toDownloadsGlassStyle().copy(surfaceAlpha = 0f),
                                liquidGlassEnabled = true,
                            ),
                        )
                    }
                }
            }
        }
        compose.runOnIdle { assertFalse(supported) }
        val bitmap = compose.onNodeWithTag("surface").captureToImage().asAndroidBitmap()
        // 上下两种高反差底色都必须被完全遮住，不能只靠提高玻璃 tint 掩盖穿透。
        assertEquals(expectedColor, bitmap.getPixel(bitmap.width / 2, bitmap.height / 4))
        assertEquals(expectedColor, bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4))
    }

    private fun verifyPanels(mode: AppThemeMode, pureBlack: Boolean) {
        val settings = AppSettings(themeMode = mode, darkModePureBlack = pureBlack, themeColor = AppThemeColor.Sakura)
        val state = DownloadsTaskActionsOverlayState()
        var glassEnabled by mutableStateOf(true)
        var selectedAction: DownloadsTaskAction? = null
        var clearCount = 0
        var deleteCount = 0
        var supported = false
        var batchHeight = 0
        var outline = Color.Transparent
        compose.setContent {
            // 模拟硬件加速宿主，保留 Compose 的真实 Owner，AndroidView 文本仍走正常测量。
            val attachInfo = ReflectionHelpers.getField<Any>(LocalView.current, "mAttachInfo")
            ReflectionHelpers.setField(attachInfo, "mHardwareAccelerated", true)
            BiliToolsTheme(settings) {
                supported = isLiquidGlassSupported()
                outline = AppDialogDefaults.outlineColor
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().layerBackdrop(backdrop).background(AppSurfaces.pageContainerColor)) {
                        repeat(12) { Text("正在下载　已完成　底层文字", color = MaterialTheme.colorScheme.onSurface) }
                    }
                    DownloadsBatchPanel(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        backdrop = backdrop,
                        statusText = "已选择 1/1",
                        selectAllText = "取消全选",
                        hintHtml = stringResource(R.string.downloads_multi_hint_has_file),
                        clearEnabled = true,
                        deleteEnabled = true,
                        bottomPadding = 24.dp,
                        glassStyle = settings.toDownloadsGlassStyle(),
                        liquidGlassEnabled = glassEnabled,
                        onExitSelection = {},
                        onSelectAll = {},
                        onClearRecords = { clearCount++ },
                        onDeleteFiles = { deleteCount++ },
                        onHeightChanged = { batchHeight = it },
                    )
                    DownloadsTaskActionsOverlay(state, backdrop, liquidGlassEnabled = glassEnabled)
                }
            }
        }
        compose.runOnIdle { assertTrue("布局对照必须覆盖真实玻璃分支", supported) }
        val glassStatusBounds = compose.onNodeWithText("已选择 1/1").getUnclippedBoundsInRoot()
        val glassBatchHeight = batchHeight
        compose.runOnIdle { glassEnabled = false }
        assertEquals(glassStatusBounds, compose.onNodeWithText("已选择 1/1").getUnclippedBoundsInRoot())
        assertEquals(glassBatchHeight, batchHeight)
        if (mode == AppThemeMode.Dark) assertTrue("深色面板需要可见描边", outline.alpha > 0f)
        capture("batch-${mode.name.lowercase()}-$pureBlack")
        compose.onNodeWithText("清除记录").performClick()
        compose.onNodeWithText("删除文件").performClick()
        compose.runOnIdle {
            assertEquals(1, clearCount)
            assertEquals(1, deleteCount)
            glassEnabled = true
            state.show(
                DownloadsTaskActionsOverlayRequest(1, title, Rect(28f, 240f, 720f, 340f), settings.toDownloadsGlassStyle()),
            ) { selectedAction = it }
        }
        val glassTitleBounds = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
        val glassOpenBounds = compose.onNodeWithText("打开方式").getUnclippedBoundsInRoot()
        val glassShareBounds = compose.onNodeWithText("分享").getUnclippedBoundsInRoot()
        compose.runOnIdle { glassEnabled = false }
        assertEquals(glassTitleBounds, compose.onNodeWithText(title).getUnclippedBoundsInRoot())
        assertEquals(glassOpenBounds, compose.onNodeWithText("打开方式").getUnclippedBoundsInRoot())
        assertEquals(glassShareBounds, compose.onNodeWithText("分享").getUnclippedBoundsInRoot())
        capture("menu-${mode.name.lowercase()}-$pureBlack")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("分享").performClick()
        compose.runOnIdle { assertEquals("动作应等待退场动画结束", null, selectedAction) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            assertEquals(DownloadsTaskAction.Share, selectedAction)
            assertEquals(null, state.request)
        }
    }

    private fun graphicsView(hardwareAccelerated: Boolean) = object : View(RuntimeEnvironment.getApplication()) {
        override fun isHardwareAccelerated() = hardwareAccelerated
    }

    private fun capture(name: String) {
        val output = File("../.tmp/glass-panels/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
