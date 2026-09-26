package com.happycola233.bilitools.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.happycola233.bilitools.data.DownloadTransferEstimate
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 多任务的回调交错到达时，速度与 ETA 仍一起每秒显示一次，字节进度不受影响。 */
@Composable
internal fun rememberDownloadTransferEstimate(
    tasks: List<DownloadItem>,
    speedBytesPerSec: Long,
    etaSeconds: Long?,
): DownloadTransferEstimate {
    val current = DownloadTransferEstimate(speedBytesPerSec, etaSeconds)
    val latest by rememberUpdatedState(current)
    // 状态改变立即更新，暂停/重试/完成不能等待下一次定时刷新。
    val phases = tasks.map { Triple(it.id, it.status, it.progressIndeterminate) }
    var displayed by remember(phases) { mutableStateOf(current) }
    LaunchedEffect(phases) {
        if (tasks.none { it.status == DownloadStatus.Running }) return@LaunchedEffect
        while (isActive) {
            delay(1_000)
            displayed = latest
        }
    }
    return displayed
}
