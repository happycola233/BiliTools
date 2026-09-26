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

    @Test fun groupCompletionIsIndependentOfTransferredBytes() {
        val state = present(video, cover)
        assertEquals(0.5f, state.completionFraction, 0f)
        assertEquals(1, state.resolvedCount)
        assertEquals(8L, state.etaSeconds)
        assertEquals(DownloadsGroupAction.Pause, state.action)
        assertEquals(state.completionFraction, present(video.copy(downloadedBytes = 1_000, progress = 100), cover).completionFraction)
    }

    @Test fun twoFailedTasksWithAllBytesPresentLeaveOneThirdOfSixTaskRingEmpty() {
        val tasks = (1L..6L).map { id ->
            video.copy(id = id, status = if (id <= 2) DownloadStatus.Failed else DownloadStatus.Success,
                downloadedBytes = 1_000, progress = 100)
        }
        val state = present(*tasks.toTypedArray())
        assertEquals(4f / 6f, state.completionFraction, 0f)
        assertEquals(4, state.resolvedCount)
        assertEquals(2, state.failedCount)
        assertEquals(DownloadsGroupAction.Retry, state.action)
        assertFalse(state.executing)
        assertNull(state.etaSeconds)
    }

    @Test fun onlySuccessfulAndUnavailableTasksCountAsResolved() {
        for (status in DownloadStatus.entries) {
            val state = present(video.copy(status = status, progress = 100, downloadedBytes = 1_000), cover)
            val expected = if (status == DownloadStatus.Success || status == DownloadStatus.Unavailable) 1f else 0.5f
            assertEquals("$status 不应被字节进度误判为完成", expected, state.completionFraction, 0f)
        }
    }

    @Test fun waitingForFirstResultAnimatesOnlyWhileWorkIsActuallyExecuting() {
        for (status in DownloadStatus.entries) {
            val state = present(video.copy(status = status, progressIndeterminate = true))
            assertEquals(status == DownloadStatus.Running || status == DownloadStatus.Merging, state.awaitingFirstResult)
        }
        assertFalse(present(video.copy(progressIndeterminate = true), cover).awaitingFirstResult)
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
        assertEquals(1f / 3f, mixed.completionFraction, 0f)
        assertEquals(1, mixed.failedCount)
        assertNull(mixed.etaSeconds)
        assertEquals(DownloadsGroupAction.Resume, present(video.copy(status = DownloadStatus.Paused, userPaused = true), failed).action)
        assertEquals(DownloadsGroupAction.Retry, present(failed).action)
    }

    @Test fun retryCannotIncreaseCompletionUntilTaskActuallyFinishes() {
        val failed = video.copy(status = DownloadStatus.Failed, progress = 100, downloadedBytes = 1_000)
        val pending = failed.copy(status = DownloadStatus.Pending, progress = 0, downloadedBytes = 0)
        assertEquals(present(failed, cover).completionFraction, present(pending, cover).completionFraction)
        assertEquals(1f, present(pending.copy(status = DownloadStatus.Success), cover).completionFraction, 0f)
    }

    @Test fun resolvedSkippedAndMissingFilesStayDistinct() {
        val state = present(video.copy(status = DownloadStatus.Success), cover.copy(status = DownloadStatus.Unavailable))
        assertTrue(state.completed)
        assertEquals(DownloadsGroupAction.Expand, state.action)
        assertEquals(1, state.skippedCount)
        assertEquals(2, state.resolvedCount)
        assertEquals(1f, state.completionFraction, 0f)
        val missing = present(cover.copy(outputMissing = true))
        assertEquals(1, missing.missingCount)
        assertTrue(missing.completed)
    }

    @Test fun allFailedOrCancelledTasksNeverLookComplete() {
        assertEquals(0f, present(video.copy(status = DownloadStatus.Failed, progress = 100)).completionFraction, 0f)
        assertEquals(0f, present(video.copy(status = DownloadStatus.Cancelled, progress = 100)).completionFraction, 0f)
        assertEquals(0f, present().completionFraction, 0f)
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
