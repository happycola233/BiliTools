package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsGroupProgressColorsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightFailureUsesErrorRetryButtonAndPlainRing() = verifyColors(AppThemeMode.Light)
    @Test fun darkFailureUsesErrorRetryButtonAndPlainRing() = verifyColors(AppThemeMode.Dark)

    private fun verifyColors(mode: AppThemeMode) {
        val failed = DownloadItem(1, 1, DownloadTaskType.Video, "视频", "video.mp4", "", status = DownloadStatus.Failed, progress = 0)
        var tasks by mutableStateOf(listOf(failed, failed.copy(id = 2, status = DownloadStatus.Success)))
        var actionColor = Color.Unspecified
        var trackColor = Color.Unspecified
        var retryContainerColor = Color.Unspecified
        var oldErrorTrackColor = Color.Unspecified
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode)) {
                actionColor = MaterialTheme.colorScheme.primary
                trackColor = WavyProgressIndicatorDefaults.trackColor
                retryContainerColor = MaterialTheme.colorScheme.error.copy(alpha = RetryContainerAlpha).compositeOver(AppSurfaces.cardContainerColor)
                oldErrorTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f).compositeOver(AppSurfaces.cardContainerColor)
                DownloadsGroupCard(
                    group = DownloadGroup(1, "失败进度的下载组", null, createdAt = 0, tasks = tasks),
                    selectionMode = false, selected = false, expanded = false, swiped = false, anyGroupSwiped = false,
                    onSwipedGroupChange = {}, onToggleSelection = {}, onToggleExpanded = {}, onDelete = {},
                    onPauseGroup = {}, onResumeGroup = {}, onReparse = {}, onShowDetails = {}, onTaskPauseResume = {},
                    onTaskRetry = {}, onTaskClick = { _, _ -> },
                )
            }
        }
        fun pixels(bitmap: Bitmap, color: Color): Int {
            val expected = color.toArgb()
            return (0 until bitmap.height).sumOf { y ->
                (0 until bitmap.width).count { x ->
                    val pixel = bitmap.getPixel(x, y)
                    listOf(0, 8, 16).all { shift -> abs(((pixel shr shift) and 255) - ((expected shr shift) and 255)) <= 2 }
                }
            }
        }
        fun image(label: String) = compose.onNodeWithContentDescription(label).captureToImage().asAndroidBitmap()
        val partial = image("重试失败项")
        assertTrue("只剩失败项时使用淡错误色的实心重试按钮", pixels(partial, retryContainerColor) > partial.width * partial.height / 3)
        assertEquals("重试按钮不再叠加主题色进度弧", 0, pixels(partial, actionColor))
        compose.runOnIdle { tasks = tasks.map { it.copy(status = DownloadStatus.Failed) } }
        val zero = image("重试失败项")
        assertTrue("全部失败同样显示实心重试按钮", pixels(zero, retryContainerColor) > zero.width * zero.height / 3)
        val output = File("../.tmp/downloads-ui/failure-zero-${mode.name}.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnIdle { tasks = listOf(failed, failed.copy(id = 2, status = DownloadStatus.Paused, userPaused = true)) }
        val mixed = image("继续该组")
        assertTrue("仍可继续时保留进度环", pixels(mixed, trackColor) > 5)
        assertEquals("进度环不混入错误色底轨，失败由底部文案说明", 0, pixels(mixed, oldErrorTrackColor))
        assertEquals(0, pixels(mixed, retryContainerColor))
    }
}
