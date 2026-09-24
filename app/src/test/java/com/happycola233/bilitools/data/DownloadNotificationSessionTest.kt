package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import org.junit.Assert.*
import org.junit.Test

class DownloadNotificationSessionTest {
    private val session = DownloadNotificationSession()
    private val tasks = linkedMapOf<Long, DownloadItem>()

    private fun put(item: DownloadItem) {
        session.record(item, tasks[item.id])
        tasks[item.id] = item
    }

    private fun task(id: Long, status: DownloadStatus = DownloadStatus.Running) = DownloadItem(
        id = id, groupId = id, taskType = DownloadTaskType.Video, title = "视频 $id",
        fileName = "$id.mp4", url = "", status = status, progress = 90,
        downloadedBytes = 90, totalBytes = 100, speedBytesPerSec = 10,
    )

    @Test fun completingOneFilePreservesTheBatchDenominatorAndCompletedCount() {
        put(task(1, DownloadStatus.Merging).copy(downloadedBytes = 100))
        put(task(2).copy(downloadedBytes = 10))
        assertNull(session.snapshot(tasks).transferProgress)
        put(tasks.getValue(1).copy(status = DownloadStatus.Success))
        val state = session.snapshot(tasks)
        assertEquals(2, state.totalCount)
        assertEquals(1, state.completedCount)
        assertEquals(1, state.runningCount)
        assertNull(state.totalBytes)
        assertNull(state.transferProgress)
    }

    @Test fun unknownQueuedFilesCannotTurnOneFilesProgressIntoBatchProgress() {
        put(task(1))
        (2L..15L).forEach { put(task(it, DownloadStatus.Pending).copy(totalBytes = 0, downloadedBytes = 0)) }
        val state = session.snapshot(tasks)
        assertEquals(15, state.totalCount)
        assertEquals(0, state.completedCount)
        assertEquals(14, state.pendingCount)
        assertNull(state.transferProgress)
    }

    @Test fun failedAndCompletedFilesRemainInTheFinalOutcome() {
        (1L..3L).forEach { put(task(it)) }
        put(tasks.getValue(1).copy(status = DownloadStatus.Failed))
        put(tasks.getValue(2).copy(status = DownloadStatus.Success))
        put(tasks.getValue(3).copy(status = DownloadStatus.Success))
        val state = session.snapshot(tasks)
        assertTrue(state.isFinished)
        assertEquals(DownloadOutcomeSummary(successCount = 2, failedCount = 1), state.outcome)
    }

    @Test fun removalKeepsCompletedResultsAndCountsUnfinishedFilesAsCancelled() {
        put(task(1))
        put(task(2))
        put(tasks.getValue(1).copy(status = DownloadStatus.Success))
        session.remove(1)
        session.remove(2)
        tasks.clear()
        assertEquals(DownloadOutcomeSummary(successCount = 1, cancelledCount = 1), session.snapshot(tasks).outcome)
    }

    @Test fun mixedPhasesHaveIndependentCounts() {
        put(task(1))
        put(task(2, DownloadStatus.Merging))
        put(task(3, DownloadStatus.Pending))
        put(task(4, DownloadStatus.Paused))
        val state = session.snapshot(tasks)
        assertEquals(listOf(1, 1, 1, 1), listOf(state.runningCount, state.processingCount, state.pendingCount, state.pausedCount))
        assertEquals(setOf(1L, 2L, 3L), state.pausableTaskIds)
        assertTrue(state.hasForegroundWork)
    }

    @Test fun pausedDownloadsDoNotRequireAForegroundService() {
        put(task(1, DownloadStatus.Paused))
        val state = session.snapshot(tasks)
        assertTrue(state.isPaused)
        assertFalse(state.hasForegroundWork)
        assertFalse(state.isFinished)
    }

    @Test fun singleFileRequiresTheWholeSizeBeforeShowingAPercentage() {
        put(task(1))
        assertEquals(90, session.snapshot(tasks).transferProgress)
        assertNull(session.snapshot(tasks) { false }.transferProgress)
        put(tasks.getValue(1).copy(totalBytes = 0))
        assertNull(session.snapshot(tasks).transferProgress)
        assertEquals(90L, session.snapshot(tasks).downloadedBytes)
    }

    @Test fun processingTransitionBypassesByteProgressThrottle() {
        put(task(1))
        val before = session.snapshot(tasks)
        put(tasks.getValue(1).copy(status = DownloadStatus.Merging))
        assertFalse(before.hasSameStructure(session.snapshot(tasks)))
    }

    @Test fun extraTasksKeepTheSessionOpenUntilTheirOutcomeIsKnown() {
        put(task(1))
        put(task(2).copy(taskType = DownloadTaskType.Subtitle))
        put(tasks.getValue(1).copy(status = DownloadStatus.Success))
        val state = session.snapshot(tasks)
        assertEquals(1, state.processingCount)
        assertTrue(state.hasForegroundWork)
        assertFalse(state.isFinished)
        put(tasks.getValue(2).copy(status = DownloadStatus.Failed))
        assertEquals(1, session.snapshot(tasks).failedCount)
    }

    @Test fun submissionPreventsPrematureCompletionBetweenEnqueuedFiles() {
        session.beginSubmission()
        put(task(1))
        put(tasks.getValue(1).copy(status = DownloadStatus.Success))
        assertFalse(session.snapshot(tasks).isFinished)
        put(task(2))
        session.endSubmission()
        assertEquals(2, session.snapshot(tasks).totalCount)
    }

    @Test fun aNewSubmissionDoesNotIncludeAnOldPausedBatch() {
        put(task(1, DownloadStatus.Paused))
        session.beginSubmission()
        put(task(2))
        session.endSubmission()
        assertEquals(setOf(2L), session.snapshot(tasks).taskIds)
    }

    @Test fun restartKeepsPriorOutcomesAndReportsCompletionOnlyOnce() {
        put(task(1))
        put(task(2, DownloadStatus.Paused))
        put(tasks.getValue(1).copy(status = DownloadStatus.Failed))
        val restored = DownloadNotificationSession()
        restored.restore(session.saved(), tasks)
        assertEquals(session.snapshot(tasks), restored.snapshot(tasks))
        val previous = tasks.getValue(2)
        val finished = previous.copy(status = DownloadStatus.Success)
        tasks[2] = finished
        restored.record(finished, previous)
        restored.markCompletionReported(restored.snapshot(tasks).sessionId)
        val reloaded = DownloadNotificationSession()
        reloaded.restore(restored.saved(), tasks)
        assertTrue(reloaded.snapshot(tasks).completionReported)
        assertEquals(1, reloaded.snapshot(tasks).failedCount)
    }
}
