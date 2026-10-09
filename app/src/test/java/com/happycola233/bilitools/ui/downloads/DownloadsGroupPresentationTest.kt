package com.happycola233.bilitools.ui.downloads

import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import org.junit.Assert.*
import org.junit.Test

class DownloadsGroupPresentationTest {
    private val video = DownloadItem(
        id = 1, groupId = 1, taskType = DownloadTaskType.Video, title = "视频", fileName = "video.mp4", url = "",
        status = DownloadStatus.Running, progress = 20, totalBytes = 1_000, downloadedBytes = 200, speedBytesPerSec = 100, etaSeconds = 8,
    )
    private val cover = video.copy(
        id = 2, taskType = DownloadTaskType.Cover, status = DownloadStatus.Success,
        progress = 100, totalBytes = 10, downloadedBytes = 10, speedBytesPerSec = 0,
    )
    private fun group(vararg tasks: DownloadItem) = DownloadGroup(1, "测试", null, createdAt = 0, tasks = tasks.toList())
    private fun present(vararg tasks: DownloadItem) = resolveDownloadsGroupPresentation(group(*tasks))

    @Test fun mediaBytesDominateProgressWhileAuxiliaryTasksShareASmallPart() {
        val state = present(video, cover)
        // 媒体占 90%，按传输进度计入（后处理前最多 95%）；附属任务平分其余 10%。
        assertEquals(0.9f * 0.2f * 0.95f + 0.1f, state.progressFraction, 1e-4f)
        assertEquals(1, state.resolvedCount)
        assertEquals(8L, state.etaSeconds)
        assertEquals(DownloadsGroupAction.Pause, state.action)
        assertTrue(present(video.copy(downloadedBytes = 900, progress = 90), cover).progressFraction > state.progressFraction)
    }

    @Test fun largeVideoBarelyStartedIsNotOutweighedByFinishedAttachments() {
        val gigabyte = 1L shl 30
        val audioVideo = video.copy(taskType = DownloadTaskType.AudioVideo, progress = 7, totalBytes = gigabyte, downloadedBytes = gigabyte * 7 / 100)
        val summary = cover.copy(id = 3, taskType = DownloadTaskType.AiSummary)
        val danmaku = cover.copy(id = 4, taskType = DownloadTaskType.DanmakuHistory)
        val state = present(audioVideo, summary, danmaku)
        assertEquals("文字仍按项数", 2, state.resolvedCount)
        assertTrue("2 / 3 项已完成时圆环应接近视频实际进度而非 2/3：${state.progressFraction}", state.progressFraction < 0.2f)
    }

    @Test fun mediaWeightsFollowFileSizes() {
        val small = video.copy(id = 3, totalBytes = 100, downloadedBytes = 0, progress = 0)
        val large = video.copy(id = 4, totalBytes = 900, downloadedBytes = 900, progress = 100, status = DownloadStatus.Success)
        assertEquals(0.9f, present(small, large).progressFraction, 1e-4f)
        // 大小未知的媒体按已知任务的平均大小计。
        val unknown = small.copy(totalBytes = 0)
        assertEquals(0.5f, present(unknown, large).progressFraction, 1e-4f)
    }

    @Test fun postProcessingCountsAsNearlyDoneButNeverFull() {
        assertEquals(0.95f, present(video.copy(status = DownloadStatus.Merging)).progressFraction, 1e-4f)
        assertTrue("传输完毕但尚未后处理时低于合并阶段", present(video.copy(progress = 100, downloadedBytes = 1_000)).progressFraction < 0.95f)
        assertTrue(present(video.copy(status = DownloadStatus.Merging), cover).progressFraction < 1f)
    }

    @Test fun groupsWithoutMediaFallBackToTaskCount() {
        val subtitle = cover.copy(id = 3, taskType = DownloadTaskType.Subtitle, status = DownloadStatus.Running)
        assertEquals(0.5f, present(subtitle, cover).progressFraction, 0f)
    }

    @Test fun twoFailedTasksWithAllBytesPresentLeaveOneThirdOfSixTaskRingEmpty() {
        val tasks = (1L..6L).map { id ->
            video.copy(id = id, status = if (id <= 2) DownloadStatus.Failed else DownloadStatus.Success,
                downloadedBytes = 1_000, progress = 100)
        }
        val state = present(*tasks.toTypedArray())
        assertEquals("同样大小的媒体任务按完成数平分", 4f / 6f, state.progressFraction, 1e-4f)
        assertEquals(4, state.resolvedCount)
        assertEquals(2, state.failedCount)
        assertEquals(DownloadsGroupAction.Retry, state.action)
        assertFalse(state.executing)
        assertNull(state.etaSeconds)
    }

    @Test fun onlySuccessfulAndUnavailableTasksFillTheRing() {
        for (status in DownloadStatus.entries) {
            val state = present(video.copy(status = status, progress = 100, downloadedBytes = 1_000), cover)
            val resolved = status == DownloadStatus.Success || status == DownloadStatus.Unavailable
            if (resolved) assertEquals(1f, state.progressFraction, 0f)
            else assertTrue("$status 不应被字节进度误判为完成", state.progressFraction < 1f)
            assertEquals(if (resolved) 2 else 1, state.resolvedCount)
        }
        // 失败与取消不计已传输量，重试从头开始时圆环不会倒退。
        assertEquals(0.1f, present(video.copy(status = DownloadStatus.Failed, progress = 100), cover).progressFraction, 1e-4f)
        assertEquals(0.1f, present(video.copy(status = DownloadStatus.Cancelled, progress = 100), cover).progressFraction, 1e-4f)
    }

    @Test fun unknownProgressAnimatesOnlyWhileWorkIsExecutingWithoutAnyMeasurableProgress() {
        for (status in DownloadStatus.entries) {
            val state = present(video.copy(status = status, progressIndeterminate = true))
            // 合并中已有可计的进度（下载已完成），不再显示不定进度。
            assertEquals("$status", status == DownloadStatus.Running, state.progressUnknown)
        }
        assertFalse(present(video.copy(progressIndeterminate = true), cover).progressUnknown)
    }

    @Test fun pauseAndResumeFollowRepositoryCapabilities() {
        val paused = video.copy(status = DownloadStatus.Paused, userPaused = true)
        assertEquals(DownloadsGroupAction.Resume, present(paused, cover).action)
        assertFalse(present(paused, cover).executing)
        assertEquals(DownloadsGroupAction.Expand, present(paused.copy(userPaused = false)).action)
        val writingCover = cover.copy(status = DownloadStatus.Running)
        assertEquals(DownloadsGroupAction.Expand, present(writingCover).action)
        assertEquals(DownloadsGroupAction.Pause, present(writingCover.copy(status = DownloadStatus.Pending)).action)
    }

    @Test fun activeWorkTakesPriorityOverRetryButRetainsFailureCount() {
        val failed = cover.copy(status = DownloadStatus.Failed)
        val mixed = present(video, failed, cover.copy(id = 3))
        assertEquals(DownloadsGroupAction.Pause, mixed.action)
        assertEquals(0.9f * 0.2f * 0.95f + 0.1f * 0.5f, mixed.progressFraction, 1e-4f)
        assertEquals(1, mixed.failedCount)
        assertNull(mixed.etaSeconds)
        assertEquals(DownloadsGroupAction.Resume, present(video.copy(status = DownloadStatus.Paused, userPaused = true), failed).action)
        assertEquals(DownloadsGroupAction.Retry, present(failed).action)
    }

    @Test fun retryCannotIncreaseCompletionUntilTaskActuallyFinishes() {
        val failed = video.copy(status = DownloadStatus.Failed, progress = 100, downloadedBytes = 1_000)
        val pending = failed.copy(status = DownloadStatus.Pending, progress = 0, downloadedBytes = 0)
        assertEquals(present(failed, cover).progressFraction, present(pending, cover).progressFraction)
        assertEquals(1f, present(pending.copy(status = DownloadStatus.Success), cover).progressFraction, 0f)
    }

    @Test fun resolvedSkippedAndMissingFilesStayDistinct() {
        val state = present(video.copy(status = DownloadStatus.Success), cover.copy(status = DownloadStatus.Unavailable))
        assertTrue(state.completed)
        assertEquals(DownloadsGroupAction.Expand, state.action)
        assertEquals(1, state.skippedCount)
        assertEquals(2, state.resolvedCount)
        assertEquals(1f, state.progressFraction, 0f)
        val missing = present(cover.copy(outputMissing = true))
        assertEquals(1, missing.missingCount)
        assertTrue(missing.completed)
    }

    @Test fun allFailedOrCancelledTasksNeverLookComplete() {
        assertEquals(0f, present(video.copy(status = DownloadStatus.Failed, progress = 100)).progressFraction, 0f)
        assertEquals(0f, present(video.copy(status = DownloadStatus.Cancelled, progress = 100)).progressFraction, 0f)
        assertEquals(0f, present().progressFraction, 0f)
    }

    @Test fun unknownRemainingSizeDoesNotProduceMisleadingEta() {
        val unknown = video.copy(id = 3, status = DownloadStatus.Pending, totalBytes = 0, downloadedBytes = 0)
        assertNull(calculateDownloadingEtaSeconds(listOf(group(video, unknown))))
        assertNull(present(video.copy(speedBytesPerSec = 0)).etaSeconds)
        assertNull(present(video, video.copy(id = 3, speedBytesPerSec = 0, etaSeconds = null)).etaSeconds)
        assertNull(present(video.copy(etaSeconds = null)).etaSeconds)
    }

    @Test fun blockedOrUnmeasurableTasksSuppressWholeGroupAndSectionEta() {
        for (status in listOf(DownloadStatus.Failed, DownloadStatus.Cancelled, DownloadStatus.Paused, DownloadStatus.Merging)) {
            val blocked = video.copy(id = 3, status = status)
            assertNull("$status 不能承诺完成时间", present(video, blocked).etaSeconds)
            assertNull(calculateDownloadingEtaSeconds(listOf(group(video), group(blocked))))
        }
        assertNull(present(video, cover.copy(status = DownloadStatus.Pending)).etaSeconds)
        assertNull(present(video.copy(progressIndeterminate = true)).etaSeconds)
    }

    @Test fun unknownMergedStreamCannotBorrowAnotherDownloadsEstimate() {
        // 音频已完成，视频仍在等待大小；已下载字节刚好等于目前已知的总大小。
        val waitingForVideo = video.copy(
            id = 3, taskType = DownloadTaskType.AudioVideo,
            totalBytes = 100, downloadedBytes = 100, speedBytesPerSec = 0, etaSeconds = null,
        )
        assertNull(present(video, waitingForVideo).etaSeconds)
        assertNull(calculateDownloadingEtaSeconds(listOf(group(video), group(waitingForVideo))))
        val measurable = waitingForVideo.copy(totalBytes = 900, speedBytesPerSec = 100, etaSeconds = 8)
        assertEquals(8L, present(video, measurable).etaSeconds)
        assertEquals(8L, present(video, waitingForVideo.copy(status = DownloadStatus.Success)).etaSeconds)
    }
}
