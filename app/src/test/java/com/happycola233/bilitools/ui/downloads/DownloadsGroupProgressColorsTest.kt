package com.happycola233.bilitools.ui.downloads

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsGroupProgressColorsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightFailureRingPreservesProgressAndThemeAction() = verifyColors(AppThemeMode.Light)
    @Test fun darkFailureRingPreservesProgressAndThemeAction() = verifyColors(AppThemeMode.Dark)

    private fun verifyColors(mode: AppThemeMode) {
        val failed = DownloadItem(1, 1, DownloadTaskType.Video, "视频", "video.mp4", "", status = DownloadStatus.Failed, progress = 0)
        var tasks by mutableStateOf(listOf(failed, failed.copy(id = 2, status = DownloadStatus.Success)))
        var actionColor = Color.Unspecified
        var errorColor = Color.Unspecified
        var errorTrackColor = Color.Unspecified
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode)) {
                actionColor = MaterialTheme.colorScheme.primary
                errorColor = MaterialTheme.colorScheme.error
                errorTrackColor = errorColor.copy(alpha = 0.2f).compositeOver(AppSurfaces.cardContainerColor)
                DownloadsGroupCard(
                    group = DownloadGroup(1, "失败进度的下载组", null, createdAt = 0, tasks = tasks),
                    selectionMode = false, selected = false, expanded = false, swiped = false, anyGroupSwiped = false,
                    onSwipedGroupChange = {}, onToggleSelection = {}, onToggleExpanded = {}, onDelete = {},
                    onPauseGroup = {}, onResumeGroup = {}, onReparse = {}, onShowDetails = {}, onTaskPauseResume = {},
                    onTaskRetry = {}, onTaskDelete = {}, onTaskClick = { _, _ -> },
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
        assertTrue("已完成弧使用清晰错误色", pixels(partial, errorColor) > 5)
        assertTrue("未完成底轨使用淡错误色", pixels(partial, errorTrackColor) > 5)
        assertTrue("中心图标保持主题色", pixels(partial, actionColor) > 5)
        compose.runOnIdle { tasks = tasks.map { it.copy(status = DownloadStatus.Failed) } }
        val zero = image("重试失败项")
        assertTrue("零进度仍有淡红色错误底轨", pixels(zero, errorTrackColor) > 5)
        assertEquals("全部失败不应画成完成的实色弧", 0, pixels(zero, errorColor))
        assertTrue("零进度仍保留主题色继续图标", pixels(zero, actionColor) > 5)
        val output = File("../.tmp/downloads-ui/failure-zero-${mode.name}.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnIdle { tasks = listOf(failed, failed.copy(id = 2, status = DownloadStatus.Paused, userPaused = true)) }
        assertTrue("混合状态不能丢失错误底轨", pixels(image("继续该组"), errorTrackColor) > 5)
        compose.runOnIdle { tasks = tasks.map { it.copy(status = DownloadStatus.Paused, userPaused = true) } }
        assertEquals("失败解除后恢复正常底轨", 0, pixels(image("继续该组"), errorTrackColor))
    }
}
