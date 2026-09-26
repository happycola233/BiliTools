package com.happycola233.bilitools.ui.downloads

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.happycola233.bilitools.data.DownloadTransferEstimate
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DownloadsTransferEstimateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun interleavedUpdatesAreSampledTogetherAndStatusChangesRemainImmediate() {
        val task = DownloadItem(
            id = 1, groupId = 1, taskType = DownloadTaskType.Video, title = "视频", fileName = "video.mp4", url = "",
            status = DownloadStatus.Running, progress = 10, speedBytesPerSec = 100, etaSeconds = 10,
        )
        var tasks by mutableStateOf(listOf(task, task.copy(id = 2)))
        var eta by mutableStateOf<Long?>(10)
        var displayed = DownloadTransferEstimate()
        compose.setContent {
            displayed = rememberDownloadTransferEstimate(tasks, tasks.sumOf { it.speedBytesPerSec }, eta)
            BasicText("${displayed.speedBytesPerSec} / ${displayed.etaSeconds}")
        }
        compose.mainClock.autoAdvance = false
        repeat(3) { index ->
            compose.runOnIdle {
                tasks = tasks.map { it.copy(speedBytesPerSec = (index + 2) * 100L, downloadedBytes = index * 100L) }
                eta = 9L - index
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeBy(200)
            compose.runOnIdle { assertEquals(DownloadTransferEstimate(200, 10), displayed) }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(DownloadTransferEstimate(800, 7), displayed) }
        compose.runOnIdle {
            tasks = tasks.map { it.copy(status = DownloadStatus.Paused, speedBytesPerSec = 0) }
            eta = null
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(DownloadTransferEstimate(), displayed) }
        compose.runOnIdle {
            tasks = tasks.map { it.copy(status = DownloadStatus.Running) }
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals("恢复时不能带回暂停前的估算", DownloadTransferEstimate(), displayed) }
    }
}
