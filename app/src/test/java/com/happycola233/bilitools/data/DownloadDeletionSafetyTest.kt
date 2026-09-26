package com.happycola233.bilitools.data

import android.content.pm.ProviderInfo
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], shadows = [DirectoryRemovalLinuxShadow::class])
class DownloadDeletionSafetyTest {
    @get:Rule val directoryRemoval = DirectoryRemovalLinuxShadow.Fixture()
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var directory: File
    private lateinit var provider: DownloadTestMediaProvider
    private lateinit var settings: SettingsRepository
    private lateinit var exports: ExportRepository
    private lateinit var repository: DownloadRepository
    private val root = "Download/Original"
    private val path = "$root/UP"

    @Before fun setUp() {
        val base = File("../.tmp/deletion-safety-tests").apply { mkdirs() }
        directory = Files.createTempDirectory(base.toPath(), "case-").toFile()
        provider = DownloadTestMediaProvider(directory)
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        settings = SettingsRepository(context).apply {
            setDownloadRootRelativePath(root)
            setNamingOverwriteExistingFiles(true)
        }
        exports = ExportRepository(context, settings)
        repository = newRepository()
    }

    @After fun tearDown() {
        ReflectionHelpers.getField<CoroutineScope>(settings, "settingsScope").cancel()
        directory.deleteRecursively()
    }

    private fun newRepository(): DownloadRepository {
        val cookies = CookieStore(context)
        val bili = BiliHttpClient(cookies, settings)
        val signer = WbiSigner(bili)
        return DownloadRepository(context, cookies, settings,
            MediaRepository(bili, signer, cookies, OpusRepository(bili, cookies)),
            ExtrasRepository(bili, signer), exports).also {
            ReflectionHelpers.getField<CoroutineScope>(it, "scope").cancel()
            ReflectionHelpers.setField(it, "directoryCleanup", DownloadDirectoryCleanup(directory))
            it.ensureLoaded()
        }
    }

    private fun group(directory: String = path): Long =
        repository.createGroup("UP", null, relativePath = directory)

    private fun item(group: Long, name: String = "video.mp4"): DownloadItem =
        repository.enqueue(group, DownloadTaskType.AudioVideo, name, name, "https://example.invalid/video")

    private fun replace(item: DownloadItem) {
        ReflectionHelpers.callInstanceMethod<Unit>(repository, "updateTask",
            ReflectionHelpers.ClassParameter.from(DownloadItem::class.java, item))
    }

    private fun persist() {
        ReflectionHelpers.callInstanceMethod<Unit>(repository, "persistStateImmediately")
    }

    private suspend fun saved(group: Long, name: String = "video.mp4"): DownloadItem {
        val task = item(group, name)
        val uri = requireNotNull(exports.outputStorage.save(name, "video/mp4",
            repository.groupRelativePath(group), root, false, 3, task.outputOwnerKey) {
            byteArrayOf(1, 2, 3).inputStream()
        })
        return task.copy(localUri = uri, status = DownloadStatus.Success, progress = 100).also(::replace)
    }

    @Test fun deletingFifteenGroupsInSharedDirectoryPreservesOther485Videos() = runBlocking {
        val selected = (1..15).map { saved(group(), "selected$it.mp4").groupId }
        val other = saved(group(), "other-task.mp4")
        repeat(484) { provider.add("unrelated$it.mp4", path) }
        assertEquals(500, provider.rows.size)
        val result = repository.deleteGroups(selected, true)
        assertEquals(15, result.removedTasks)
        assertEquals(485, provider.rows.size)
        assertNotNull(provider.row(requireNotNull(other.localUri)))
        assertEquals(listOf(other.groupId), repository.groups.value.map { it.id })
    }

    @Test fun rootChangesAndRestartStillDeleteOriginalFilesOnly() = runBlocking {
        val old = saved(group())
        settings.setDownloadRootRelativePath("Download/New")
        val unrelated = provider.add("video.mp4", "Download/New/UP")
        persist()
        repository = newRepository()
        assertEquals(root, repository.groups.value.single().downloadRootRelativePath)
        assertEquals(1, repository.deleteGroup(old.groupId, true).removedTasks)
        assertEquals(setOf(unrelated.lastPathSegment!!.toLong()), provider.rows.keys)
    }

    @Test fun emptyFolderTemplateNeverCreatesASiblingOfTheRoot() {
        settings.setNamingOverwriteExistingFiles(false)
        assertEquals(root, repository.groupRelativePath(group(root)))
        assertEquals(root, repository.groupRelativePath(group(root)))
    }

    @Test fun movedOutputIsBlockedAndTaskRemainsAvailableForRetry() = runBlocking {
        val task = saved(group())
        provider.row(task.localUri!!).directory = "Download/Personal/"
        val result = repository.deleteGroup(task.groupId, true)
        assertEquals(1, result.blockedFiles)
        assertEquals(0, result.removedTasks)
        assertEquals(task.id, repository.groups.value.single().tasks.single().id)
        assertEquals(1, provider.rows.size)
    }

    @Test fun uriOfAnotherTasksOutputCannotBeUsedToDeleteIt() = runBlocking {
        val original = saved(group())
        val forged = item(group()).copy(localUri = original.localUri, status = DownloadStatus.Success)
        replace(forged)
        // 清除原任务记录后，归属清单仍不能被另一个任务的 URI 覆盖。
        repository.deleteTask(original.id, false)
        assertEquals(1, repository.deleteTask(forged.id, true).blockedFiles)
        assertEquals(1, provider.rows.size)
    }

    @Test fun sharedReferenceKeepsFileWhileRemovingSelectedTask() = runBlocking {
        val original = saved(group())
        val reference = item(group()).copy(localUri = original.localUri, status = DownloadStatus.Success)
        replace(reference)
        val result = repository.deleteTask(original.id, true)
        assertEquals(1, result.sharedFiles)
        assertEquals(1, result.removedTasks)
        assertEquals(1, provider.rows.size)
    }

    @Test fun waitsForWriterAndFindsOutputNotYetAssignedToLocalUri() = runBlocking {
        val task = item(group())
        replace(task.copy(status = DownloadStatus.Running))
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = launch {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    cancelled.complete(Unit)
                    release.await()
                    exports.outputStorage.save(task.fileName, "video/mp4", path, root, false, 3,
                        task.outputOwnerKey) { byteArrayOf(1, 2, 3).inputStream() }
                }
            }
        }
        ReflectionHelpers.getField<ConcurrentHashMap<Long, Job>>(repository, "downloadJobs")[task.id] = writer
        started.await()
        val deletion = async { repository.deleteTask(task.id, true) }
        cancelled.await()
        assertFalse(deletion.isCompleted)
        repository.retry(task.id)
        repository.resume(task.id)
        release.complete(Unit)
        assertEquals(1, deletion.await().removedTasks)
        assertTrue(writer.isCompleted)
        assertTrue(provider.rows.isEmpty())
        // 迟到的回调不得复活已删除任务。
        replace(task.copy(status = DownloadStatus.Success))
        assertTrue(repository.groups.value.isEmpty())
    }

    @Test fun clearRecordsPreservesCompletedFileAndOwnership() = runBlocking {
        val task = saved(group())
        assertEquals(1, repository.clearAllGroups().removedTasks)
        assertNotNull(exports.outputStorage.record(task.localUri))
        assertEquals(1, provider.rows.size)
    }

    @Test fun restartDoesNotRelinkMissingOutputToASameNameReplacement() = runBlocking {
        val task = saved(group())
        provider.rows.remove(task.localUri!!.substringAfterLast('/').toLong())!!.file.delete()
        provider.add(task.fileName, path)
        persist()
        repository = newRepository()
        val restored = repository.groups.value.single().tasks.single()
        assertEquals(task.localUri, restored.localUri)
        repository.deleteGroup(task.groupId, true)
        assertEquals(1, provider.rows.size)
    }

    @Test fun pausedWriterRemainsTrackedUntilItActuallyExits() = runBlocking {
        val task = item(group())
        replace(task.copy(status = DownloadStatus.Running))
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = launch {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    cancelled.complete(Unit)
                    release.await()
                }
            }
        }
        ReflectionHelpers.callInstanceMethod<Unit>(repository, "trackTaskJob",
            ReflectionHelpers.ClassParameter.from(Long::class.javaPrimitiveType!!, task.id),
            ReflectionHelpers.ClassParameter.from(Job::class.java, writer))
        ReflectionHelpers.getField<ConcurrentHashMap<Long, Job>>(repository, "downloadJobs")[task.id] = writer
        started.await()
        repository.pause(task.id)
        cancelled.await()
        val deletion = async { repository.deleteTask(task.id, true) }
        yield()
        assertFalse(deletion.isCompleted)
        release.complete(Unit)
        assertEquals(1, deletion.await().removedTasks)
        assertTrue(writer.isCompleted)
    }

    @Test fun legacyTasksInForgottenRootsMigrateWithinTheirOriginalDirectory() = runBlocking {
        val task = item(group())
        val uri = provider.add(task.fileName, path).toString()
        replace(task.copy(status = DownloadStatus.Success, localUri = uri))
        persist()
        val state = File(context.filesDir, "downloads_state.json")
        val legacy = org.json.JSONObject(state.readText()).apply {
            put("version", 2)
            getJSONArray("groups").getJSONObject(0).remove("downloadRootRelativePath")
            getJSONArray("groups").getJSONObject(0).getJSONArray("tasks").getJSONObject(0).remove("outputOwnerKey")
        }
        state.writeText(legacy.toString())
        settings.setDownloadRootRelativePath("Download/New")
        ReflectionHelpers.getField<android.content.SharedPreferences>(settings, "prefs")
            .edit().remove("download_root_history").commit()
        repository = newRepository()
        assertEquals(path, repository.groups.value.single().downloadRootRelativePath)
        // 在任务新格式写回前再次启动，迁移标识仍然相同。
        repository = newRepository()
        assertEquals(1, repository.deleteGroup(task.groupId, true).removedTasks)
        assertTrue(provider.rows.isEmpty())
    }

    @Test fun corruptHistoryIsPreservedAndCannotBeOverwrittenByNewTasks() = runBlocking {
        val state = File(context.filesDir, "downloads_state.json").apply { writeText("{broken") }
        repository = newRepository()
        assertTrue(repository.historyReadFailed.value)
        assertFalse(repository.ensureLoaded())
        assertTrue(runCatching { group() }.isFailure)
        assertEquals(1, repository.deleteGroup(1, true).failedFiles)
        assertEquals("{broken", state.readText())
    }

    @Test fun fileDeletionCleansEmptyTaskDirectoryAfterChangingDownloadRoot() = runBlocking {
        val folder = File(directory, path).apply { mkdirs() }
        val task = saved(group())
        settings.setDownloadRootRelativePath("Download/New")
        val newFolder = File(directory, "Download/New").apply { mkdirs() }
        assertEquals(1, repository.deleteTask(task.id, true).removedTasks)
        assertFalse(folder.exists())
        assertTrue(File(directory, root).isDirectory)
        assertTrue(newFolder.isDirectory)
    }

    @Test fun directoryUsedByAnUnselectedTaskIsKeptEvenWhenEmpty() = runBlocking {
        val folder = File(directory, path).apply { mkdirs() }
        val first = saved(group(), "first.mp4")
        val second = saved(group(), "second.mp4")
        repository.deleteTask(first.id, true)
        assertTrue(folder.isDirectory)
        repository.deleteTask(second.id, true)
        assertFalse(folder.exists())
    }

    @Test fun clearingOnlyRecordsDoesNotRemoveDirectories() = runBlocking {
        val folder = File(directory, path).apply { mkdirs() }
        val task = saved(group())
        repository.deleteTask(task.id, false)
        assertTrue(folder.isDirectory)
    }

    @Test fun nestedHistoricalDownloadRootIsNotRemovedAsAnEmptyTaskDirectory() = runBlocking {
        val folder = File(directory, path).apply { mkdirs() }
        val task = saved(group())
        settings.setDownloadRootRelativePath(path)
        settings.setDownloadRootRelativePath("Download/New")
        repository.deleteTask(task.id, true)
        assertTrue(folder.isDirectory)
    }

    @Test fun completedOutputIsRecoveredAfterCrashBeforeTaskWriteback() = runBlocking {
        val task = item(group())
        persist()
        val uri = requireNotNull(exports.outputStorage.save(task.fileName, "video/mp4", path, root,
            false, 3, task.outputOwnerKey) { byteArrayOf(1, 2, 3).inputStream() })
        repository = newRepository()
        val recovered = repository.groups.value.single().tasks.single()
        assertEquals(DownloadStatus.Success, recovered.status)
        assertEquals(uri, recovered.localUri)
        assertEquals(1, repository.deleteGroup(task.groupId, true).removedTasks)
        assertTrue(provider.rows.isEmpty())
    }

    @Test fun failedDeletionRetainsUriEvenWhenTaskWritebackHadNotFinished() = runBlocking {
        val task = item(group())
        val uri = requireNotNull(exports.outputStorage.save(task.fileName, "video/mp4", path, root,
            false, 3, task.outputOwnerKey) { byteArrayOf(1, 2, 3).inputStream() })
        provider.failure = DownloadTestMediaProvider.Failure.Delete
        assertEquals(1, repository.deleteTask(task.id, true).failedFiles)
        assertEquals(uri, repository.groups.value.single().tasks.single().localUri)
        provider.failure = DownloadTestMediaProvider.Failure.None
        assertEquals(1, repository.deleteTask(task.id, true).removedTasks)
    }
}
