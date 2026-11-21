package com.happycola233.bilitools.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.isResolvedWithoutFailure

private val workingStatuses = setOf(DownloadStatus.Pending, DownloadStatus.Running, DownloadStatus.Merging)

/**
 * 判断是否「一批下载刚刚全部完成」：上一刻还有任务在排队、下载或合并，这一刻已经没有，
 * 而且那些任务都顺利完成（至少一个真正保存了文件，没有失败、暂停或被取消的）。
 * 期间被删除的任务不计入。
 */
internal fun isBatchJustCompleted(previous: List<DownloadGroup>, current: List<DownloadGroup>): Boolean {
    val workingIds = previous.asSequence()
        .flatMap { it.tasks }
        .filter { it.status in workingStatuses }
        .map { it.id }
        .toSet()
    if (workingIds.isEmpty()) return false
    val currentTasks = current.flatMap { it.tasks }
    if (currentTasks.any { it.status in workingStatuses }) return false
    val finished = currentTasks.filter { it.id in workingIds }
    return finished.isNotEmpty() &&
        finished.all { it.status.isResolvedWithoutFailure } &&
        finished.any { it.status == DownloadStatus.Success }
}

/**
 * 下载页可见时，一批下载全部完成就撒一把彩纸；页面不可见时完成的不补放。
 * 返回值每撒一次自增，交给 ConfettiBurst 作触发键。
 */
@Composable
internal fun rememberDownloadCelebrationTrigger(groups: List<DownloadGroup>, visible: Boolean): Int {
    var trigger by remember { mutableIntStateOf(0) }
    val previous = remember { PreviousGroups(groups) }
    LaunchedEffect(groups) {
        if (visible && isBatchJustCompleted(previous.value, groups)) trigger += 1
        previous.value = groups
    }
    return trigger
}

/** 只用来和下一次比较，不参与绘制，因此不需要是可观察状态 */
private class PreviousGroups(var value: List<DownloadGroup>)

/** 彩纸颜色：哔哩粉、小电视蓝和几种明快的辅色，深浅模式下都醒目 */
internal val celebrationColors = listOf(
    Color(0xFFFB7299),
    Color(0xFF23ADE5),
    Color(0xFFFFC93C),
    Color(0xFF4FD1A5),
    Color(0xFFA98BF5),
)
