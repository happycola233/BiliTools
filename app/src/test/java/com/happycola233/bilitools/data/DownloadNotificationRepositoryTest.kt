package com.happycola233.bilitools.data

import com.happycola233.bilitools.BiliToolsApp
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DownloadNotificationRepositoryTest {
    private val repository get() = (RuntimeEnvironment.getApplication() as BiliToolsApp).container.downloadRepository

    private fun item(id: Long, groupId: Long, status: DownloadStatus = DownloadStatus.Running) = DownloadItem(
        id = id, groupId = groupId, taskType = DownloadTaskType.Video, title = "视频",
        fileName = "$id.mp4", url = "", status = status, progress = 50,
        userPaused = status == DownloadStatus.Paused,
    )

    private fun add(task: DownloadItem) {
        ReflectionHelpers.callInstanceMethod<Unit>(repository, "addTask", ClassParameter.from(DownloadItem::class.java, task))
    }

    private fun update(task: DownloadItem) {
        ReflectionHelpers.callInstanceMethod<Unit>(repository, "updateTask", ClassParameter.from(DownloadItem::class.java, task))
    }

    @Test fun repositoryKeepsResultsEvenWhenCollectorsSkipIntermediateSnapshots() {
        val groupId = repository.createGroup("测试", null)
        val tasks = (1L..3L).map { item(it, groupId) }
        tasks.forEach(::add)
        update(tasks[0].copy(status = DownloadStatus.Failed))
        update(tasks[1].copy(status = DownloadStatus.Success))
        update(tasks[2].copy(status = DownloadStatus.Success))
        val state = repository.notificationState.value
        assertEquals(3, state.totalCount)
        assertEquals(DownloadOutcomeSummary(successCount = 2, failedCount = 1), state.outcome)
        assertTrue(state.isFinished)
    }

    @Test fun notificationResumeDoesNotRetryFailuresOrResumeOtherPausedTasks() {
        val groupId = repository.createGroup("测试", null)
        val paused = item(1, groupId, DownloadStatus.Paused)
        add(paused)
        add(item(2, groupId, DownloadStatus.Failed))
        add(item(3, groupId, DownloadStatus.Paused))
        // 占满执行名额，验证入队范围而不启动网络请求。
        val queue = ReflectionHelpers.getField<TaskConcurrencyQueue>(repository, "managedTaskQueue")
        (90L..99L).forEach(queue::enqueue)
        queue.takeReady()
        repository.resumeNotificationTasks(setOf(1, 2))
        val tasks = repository.groups.value.single().tasks.associateBy { it.id }
        assertEquals(DownloadStatus.Pending, tasks.getValue(1).status)
        assertEquals(DownloadStatus.Failed, tasks.getValue(2).status)
        assertEquals(DownloadStatus.Paused, tasks.getValue(3).status)
    }

    @Test fun pauseOnlyAffectsTheNotificationsTasks() {
        val groupId = repository.createGroup("测试", null)
        add(item(1, groupId))
        add(item(2, groupId))
        repository.pauseNotificationTasks(setOf(1))
        val tasks = repository.groups.value.single().tasks.associateBy { it.id }
        assertEquals(DownloadStatus.Paused, tasks.getValue(1).status)
        assertEquals(DownloadStatus.Running, tasks.getValue(2).status)
    }

    @Test fun staleCompletionAcknowledgementDoesNotConsumeNewlyAddedWork() {
        val groupId = repository.createGroup("测试", null)
        val first = item(1, groupId)
        add(first)
        update(first.copy(status = DownloadStatus.Success))
        val finished = repository.notificationState.value
        add(item(2, groupId))
        repository.markNotificationCompletionReported(finished)
        assertFalse(repository.notificationState.value.completionReported)
        assertTrue(repository.notificationState.value.hasForegroundWork)
        assertEquals(2, repository.notificationState.value.totalCount)
    }

    @Test fun foregroundTimeoutPausesMediaAndStopsRunningExtraTasks() {
        val groupId = repository.createGroup("测试", null)
        add(item(1, groupId))
        add(item(2, groupId).copy(taskType = DownloadTaskType.Subtitle))
        repository.pauseForForegroundTimeout()
        val state = repository.notificationState.value
        assertFalse(state.hasForegroundWork)
        assertEquals(setOf(1L, 2L), state.pausedTaskIds)
        assertEquals(0, state.failedCount)
    }

    @Test fun failedSubmissionAlwaysExitsPreparingState() = runBlocking {
        runCatching {
            repository.withNotificationSubmission<Unit> {
                assertTrue(repository.notificationState.value.isPreparing)
                error("submission failed")
            }
        }
        assertFalse(repository.notificationState.value.isPreparing)
        assertFalse(repository.notificationState.value.hasForegroundWork)
    }
}
