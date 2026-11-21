package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val FRAME_MILLIS = 16L

/** 单任务线性波浪进度条：合并阶段的振幅、列表复用后的不定进度运动，以及快速暂停/继续后的最终形态。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsTaskProgressIndicatorTest {
    @get:Rule val compose = createComposeRule()

    private var indicatorColor = Color.Unspecified
    private lateinit var hostView: View
    private val frameCanvas by lazy { Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)) }

    @Test fun lightMergingKeepsFullWave() = verifyMergingKeepsFullWave(AppThemeMode.Light)
    @Test fun darkMergingKeepsFullWave() = verifyMergingKeepsFullWave(AppThemeMode.Dark)

    /** 下载到 99% 后进入合并：不定进度的振幅必须与原生组件一致，不能停在交接首帧的微小振幅。 */
    private fun verifyMergingKeepsFullWave(themeMode: AppThemeMode) {
        var merging by mutableStateOf(false)
        setThemedContent(themeMode) {
            Column {
                IndicatorSlot("subject") {
                    DownloadsTaskProgressIndicator(progress = 0.99f, indeterminate = merging, transferring = !merging)
                }
                IndicatorSlot("native") { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }
        advance(1_000)
        compose.runOnIdle { merging = true }
        advance(1_000)
        val subjectHeight = maxWaveHeight("subject")
        val nativeHeight = maxWaveHeight("native")
        assertTrue(
            "合并阶段应保持完整振幅：实际 $subjectHeight px，原生 $nativeHeight px",
            subjectHeight >= nativeHeight - 2,
        )
    }

    /** 列表项滑出后再滑回会复用布局节点；不定进度的两段线条必须继续移动，而不是只剩波浪在原地流动。 */
    @Test fun reusedListItemKeepsIndeterminateLinesMoving() {
        setThemedContent {
            LazyColumn(Modifier.height(240.dp).testTag("list")) {
                items(count = 60, key = { it }) { index ->
                    IndicatorSlot("task-$index") {
                        DownloadsTaskProgressIndicator(progress = 0f, indeterminate = true, transferring = false)
                    }
                }
            }
        }
        advance(500)
        assertLinesMove("首次组合时线条应移动", "task-0")

        scrollTo(59)
        scrollTo(0)
        assertLinesMove("列表项复用后线条仍应移动", "task-0")
    }

    /** 振幅过渡途中反复切换，最终必须停在最后一次要求的形态。 */
    @Test fun rapidPauseResumeSettlesOnLastAmplitudeTarget() {
        var transferring by mutableStateOf(true)
        setThemedContent {
            Column {
                IndicatorSlot("subject") {
                    DownloadsTaskProgressIndicator(progress = 0.5f, indeterminate = false, transferring = transferring)
                }
                IndicatorSlot("flat") {
                    LinearWavyProgressIndicator(progress = { 0.5f }, modifier = Modifier.fillMaxWidth(), amplitude = { 0f })
                }
                IndicatorSlot("wavy") {
                    LinearWavyProgressIndicator(progress = { 0.5f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        advance(1_000)
        for (interruptionMillis in listOf(16L, 80L, 240L)) {
            compose.runOnIdle { transferring = false }
            advance(interruptionMillis)
            compose.runOnIdle { transferring = true }
            advance(interruptionMillis)
            compose.runOnIdle { transferring = false }
            advance(1_500)
            assertWaveHeight("展开途中暂停（间隔 ${interruptionMillis}ms）应收平", "flat")

            compose.runOnIdle { transferring = true }
            advance(interruptionMillis)
            compose.runOnIdle { transferring = false }
            advance(interruptionMillis)
            compose.runOnIdle { transferring = true }
            advance(1_500)
            assertWaveHeight("收拢途中继续（间隔 ${interruptionMillis}ms）应恢复完整波浪", "wavy")
        }
    }

    private fun setThemedContent(themeMode: AppThemeMode = AppThemeMode.Light, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            hostView = LocalView.current
            BiliToolsTheme(AppSettings(themeMode = themeMode, themeColor = AppThemeColor.Sakura)) {
                indicatorColor = WavyProgressIndicatorDefaults.indicatorColor
                content()
            }
        }
    }

    @Composable
    private fun IndicatorSlot(tag: String, content: @Composable () -> Unit) {
        Box(
            Modifier
                .width(240.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp)
                .testTag(tag),
        ) { content() }
    }

    private fun scrollTo(index: Int) {
        compose.onNodeWithTag("list").performScrollToIndex(index)
        advance(64)
    }

    /**
     * 逐帧推进并绘制。组件在绘制阶段读取振幅目标并启动过渡；真机上波浪每帧重绘，
     * 而 Robolectric 不会自动绘制，只推进时钟会让中间状态从未被组件看到，掩盖振幅卡在中途的问题。
     */
    private fun advance(millis: Long) {
        repeat((millis / FRAME_MILLIS).toInt()) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnUiThread { hostView.draw(frameCanvas) }
        }
    }

    private fun capture(tag: String): Bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

    private fun assertWaveHeight(message: String, expectedTag: String) {
        val actual = waveHeight(capture("subject"))
        val expected = waveHeight(capture(expectedTag))
        assertTrue("$message：实际 $actual px，期望 $expected px", abs(actual - expected) <= 2)
    }

    /** 不定进度的线条会周期性缩短甚至消失，取一个完整周期内的最大波形高度。 */
    private fun maxWaveHeight(tag: String): Int {
        var height = 0
        repeat(40) {
            height = maxOf(height, waveHeight(capture(tag)))
            advance(48)
        }
        return height
    }

    /** 线条位置冻结时，波浪平移只改变线段内部像素，覆盖的列不变；正常运动时每段时间后覆盖范围都会变化。 */
    private fun assertLinesMove(message: String, tag: String) {
        var previous = coveredColumns(capture(tag))
        var movedSamples = 0
        repeat(20) {
            advance(80)
            val current = coveredColumns(capture(tag))
            if (current.indices.count { current[it] != previous[it] } > current.size / 50) movedSamples++
            previous = current
        }
        assertTrue("$message：20 次采样中仅 $movedSamples 次检测到线条移动", movedSamples >= 10)
    }

    private fun waveHeight(bitmap: Bitmap): Int {
        var top = -1
        var bottom = -1
        for (y in 0 until bitmap.height) {
            if ((0 until bitmap.width).any { x -> isIndicatorPixel(bitmap.getPixel(x, y)) }) {
                if (top < 0) top = y
                bottom = y
            }
        }
        return if (top < 0) 0 else bottom - top + 1
    }

    private fun coveredColumns(bitmap: Bitmap): BooleanArray = BooleanArray(bitmap.width) { x ->
        (0 until bitmap.height).any { y -> isIndicatorPixel(bitmap.getPixel(x, y)) }
    }

    private fun isIndicatorPixel(pixel: Int): Boolean {
        val indicator = indicatorColor.toArgb()
        var distance = 0
        for (shift in listOf(0, 8, 16)) distance += abs(((pixel shr shift) and 255) - ((indicator shr shift) and 255))
        return distance < 60
    }
}
