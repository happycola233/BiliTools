package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.model.DownloadTaskType
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadSourcePersistenceTest {
    private lateinit var directory: File
    private val storeFile get() = File(directory, "downloads.json")
    private val videoUrl = "https://cdn.example.com/video"
    private val audioUrl = "https://cdn.example.com/audio"
    private val videoBackups = listOf("https://mirror.example.com/video", "https://backup.example.com/video")
    private val audioBackups = listOf("https://mirror.example.com/audio")

    @Before fun setUp() {
        val root = File("../.tmp/download-source-persistence-tests").apply { mkdirs() }
        directory = Files.createTempDirectory(root.toPath(), "case-").toFile()
    }

    @After fun tearDown() { directory.deleteRecursively() }

    @Test fun queuePersistenceRestoresBothMediaSourcesAndTheValidatorOwner() {
        val repository = repository()
        val group = repository.createGroup("视频", null)
        val single = repository.enqueue(group, DownloadTaskType.Video, "视频", "video.mp4", videoUrl, backupUrls = videoBackups)
        val merged = repository.enqueueDashMerge(
            group, "音视频", "merged.mp4", videoUrl, audioUrl,
            videoBackupUrls = videoBackups, audioBackupUrls = audioBackups,
        )
        val original = state(repository, single.id).apply {
            tempFile.writeText("ab")
            totalBytes = 6
            validatorUrl = videoBackups.first()
            etag = "\"mirror-etag\""
        }
        ReflectionHelpers.callInstanceMethod<Unit>(repository, "persistState")

        val restored = repository().apply { ensureLoaded() }
        val singleState = state(restored, single.id)
        assertEquals(original.source, singleState.source)
        assertEquals(original.validatorUrl, singleState.validatorUrl)
        assertEquals(original.etag, singleState.etag)
        assertEquals(2L, singleState.downloadedBytes)
        assertEquals(videoBackups, part(restored, merged.id, "video").source.backupUrls)
        assertEquals(audioBackups, part(restored, merged.id, "audio").source.backupUrls)
    }

    @Test fun legacySnapshotsWithoutBackupFieldsRemainReadable() {
        val repository = repository()
        val group = repository.createGroup("视频", null)
        val single = repository.enqueue(group, DownloadTaskType.Video, "视频", "video.mp4", videoUrl)
        val merged = repository.enqueueDashMerge(group, "音视频", "merged.mp4", videoUrl, audioUrl)
        state(repository, single.id).etag = "\"legacy\""
        ReflectionHelpers.callInstanceMethod<Unit>(repository, "persistState")
        val json = JSONObject(storeFile.readText())
        val states = json.getJSONArray("resumableStates")
        for (index in 0 until states.length()) stripNewFields(states.getJSONObject(index))
        val merges = json.getJSONArray("mergeStates")
        for (index in 0 until merges.length()) {
            stripNewFields(merges.getJSONObject(index).getJSONObject("video"))
            stripNewFields(merges.getJSONObject(index).getJSONObject("audio"))
        }
        storeFile.writeText(json.toString())

        val restored = repository().apply { ensureLoaded() }
        assertEquals(emptyList<String>(), state(restored, single.id).source.backupUrls)
        assertEquals(videoUrl, state(restored, single.id).validatorUrl)
        assertEquals("\"legacy\"", state(restored, single.id).etag)
        assertEquals(videoUrl, part(restored, merged.id, "video").source.url)
        assertEquals(audioUrl, part(restored, merged.id, "audio").source.url)
    }

    private fun stripNewFields(json: JSONObject) {
        json.remove("backupUrls")
        json.remove("validatorUrl")
    }

    private fun state(repository: DownloadRepository, id: Long): ResumableDownloadTarget =
        ReflectionHelpers.getField<Map<Long, ResumableDownloadTarget>>(repository, "downloadStates").getValue(id)

    private fun part(repository: DownloadRepository, id: Long, name: String): ResumableDownloadTarget {
        val merged = ReflectionHelpers.getField<Map<Long, Any>>(repository, "mergeTasks").getValue(id)
        return ReflectionHelpers.getField(merged, name)
    }

    private fun repository(): DownloadRepository {
        val context = RuntimeEnvironment.getApplication()
        val cookies = CookieStore(context)
        val settings = SettingsRepository(context)
        val bili = BiliHttpClient(cookies, settings)
        val signer = WbiSigner(bili)
        return DownloadRepository(
            context, cookies, settings, MediaRepository(bili, signer, cookies, OpusRepository(bili, cookies)),
            ExtrasRepository(bili, signer), ExportRepository(context, settings),
        ).also {
            // 只测试真实入队、序列化和恢复，暂停后台调度，避免发起外部下载。
            ReflectionHelpers.getField<CoroutineScope>(it, "scope").cancel()
            ReflectionHelpers.setField(it, "storeFile", storeFile)
            ReflectionHelpers.setField(it, "tempDir\$delegate", lazyOf(directory))
        }
    }
}
