package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class DownloadsGroupProgressIndicatorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightDeterminatePauseKeepsWavePhase() = verifyPauseResume(AppThemeMode.Light, resolvedCount = 2)
    @Test fun darkDeterminatePauseKeepsWavePhase() = verifyPauseResume(AppThemeMode.Dark, resolvedCount = 2)
    @Test fun lightIndeterminatePauseKeepsWavePhase() = verifyPauseResume(AppThemeMode.Light, resolvedCount = 0)
    @Test fun darkIndeterminatePauseKeepsWavePhase() = verifyPauseResume(AppThemeMode.Dark, resolvedCount = 0)

    private fun verifyPauseResume(themeMode: AppThemeMode, resolvedCount: Int) {
        val running = presentation(resolvedCount)
        val paused = running.copy(executing = false, action = DownloadsGroupAction.Resume)
        var current by mutableStateOf(running)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = themeMode, themeColor = AppThemeColor.Sakura)) {
                Row {
                    for (tag in listOf("subject", "running", "paused")) {
                        Box(
                            Modifier.size(64.dp).background(MaterialTheme.colorScheme.surface).testTag(tag),
                            contentAlignment = Alignment.Center,
                        ) {
                            val color = WavyProgressIndicatorDefaults.indicatorColor
                            val trackColor = WavyProgressIndicatorDefaults.trackColor
                            when (tag) {
                                "subject" -> DownloadsGroupProgressIndicator(current, color, trackColor)
                                "running" -> if (running.awaitingFirstResult) {
                                    CircularWavyProgressIndicator(color = color, trackColor = trackColor)
                                } else {
                                    CircularWavyProgressIndicator(
                                        progress = { running.completionFraction }, color = color, trackColor = trackColor,
                                    )
                                }
                                else -> CircularWavyProgressIndicator(
                                    progress = { paused.completionFraction }, color = color, trackColor = trackColor,
                                    amplitude = { 0f },
                                )
                            }
                        }
                    }
                }
            }
        }
        val prefix = "${themeMode.name}-$resolvedCount"
        capture()
        advanceAndCapture(368)
        assertImagesClose("初始波形应与原生组件一致", capture(), capture("running"))
        save(capture(), "$prefix-running")

        compose.runOnIdle { current = paused }
        compose.mainClock.advanceTimeByFrame()
        val firstPausedFrame = capture()
        // 同一时钟下的原生运行组件提供当前相位，能抓住速度归零和组件重建导致的首帧跳变。
        assertImagesClose("暂停首帧应承接当前波形", firstPausedFrame, capture("running"))
        save(firstPausedFrame, "$prefix-pause-first")
        for (frame in 1..12) {
            val bitmap = advanceAndCapture(64)
            if (frame in listOf(1, 3, 6, 12)) save(bitmap, "$prefix-pause-$frame")
        }
        assertImagesClose("暂停后应收拢为对应进度的圆环", capture(), capture("paused"))
        val settled = capture()
        assertImagesClose("暂停稳定后不应继续运动", settled, advanceAndCapture(256))

        compose.runOnIdle { current = running }
        compose.mainClock.advanceTimeByFrame()
        assertImagesClose("继续首帧不应突然出现完整波浪", settled, capture())
        repeat(12) { advanceAndCapture(64) }
        val resumed = capture()
        assertTrue("继续后应恢复波浪运动", imageDifference(resumed, advanceAndCapture(128)) > 0.002)
        save(resumed, "$prefix-resumed")

        // 收拢途中继续，再展开途中暂停，最后一次目标必须生效，不能卡在旧动画终点。
        repeat(3) {
            compose.runOnIdle { current = paused }
            advanceAndCapture(80)
            compose.runOnIdle { current = running }
            advanceAndCapture(80)
        }
        compose.runOnIdle { current = paused }
        repeat(16) { advanceAndCapture(64) }
        assertImagesClose("快速切换后应停在最后要求的圆环形态", capture(), capture("paused"))
        compose.runOnIdle { current = running }
        repeat(16) { advanceAndCapture(64) }
        val finalRunning = capture()
        assertTrue("快速切换后仍可继续运动", imageDifference(finalRunning, advanceAndCapture(128)) > 0.002)

        if (resolvedCount == 0) {
            // 首个结果产生时同样会切换组件，旧波浪应保留到交接完成。
            val beforeResult = capture()
            compose.runOnIdle { current = presentation(resolvedCount = 2) }
            compose.mainClock.advanceTimeByFrame()
            assertTrue("首个结果不应让波浪瞬间消失", imageDifference(beforeResult, capture()) < 0.015)
            repeat(16) { advanceAndCapture(64) }
            val afterResult = capture()
            assertTrue("切为确定进度后应继续运动", imageDifference(afterResult, advanceAndCapture(128)) > 0.002)
        }
    }

    private fun presentation(resolvedCount: Int) = DownloadsGroupPresentation(
        action = DownloadsGroupAction.Pause, completed = false, executing = true,
        completionFraction = resolvedCount / 4f, resolvedCount = resolvedCount,
        skippedCount = 0, failedCount = 0, missingCount = 0, speedBytesPerSec = 1_000, etaSeconds = 10,
    )

    private fun capture(tag: String = "subject") = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

    private fun advanceAndCapture(millis: Long): Bitmap {
        compose.mainClock.advanceTimeBy(millis)
        return capture()
    }

    private fun assertImagesClose(message: String, actual: Bitmap, expected: Bitmap) {
        val difference = imageDifference(actual, expected)
        assertTrue("$message，像素平均差异为 $difference", difference < 0.004)
    }

    private fun imageDifference(first: Bitmap, second: Bitmap): Double {
        var difference = 0L
        for (y in 0 until first.height) for (x in 0 until first.width) {
            val a = first.getPixel(x, y)
            val b = second.getPixel(x, y)
            for (shift in listOf(0, 8, 16)) difference += abs(((a shr shift) and 255) - ((b shr shift) and 255))
        }
        return difference.toDouble() / (first.width * first.height * 3 * 255)
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("../.tmp/downloads-wave-motion/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
