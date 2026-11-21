package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** 「重名时直接覆盖」关闭（默认）时，同一批共用文件夹的条目不能被拆进「名称(1)」「名称(2)」。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadFolderBatchTest {
    private lateinit var directory: File
    private lateinit var settings: SettingsRepository
    private lateinit var repository: DownloadRepository
    private val root get() = settings.downloadRootRelativePath()

    @Before fun setUp() {
        val base = File("../.tmp/download-folder-batch-tests").apply { mkdirs() }
        directory = Files.createTempDirectory(base.toPath(), "case-").toFile()
        val context = RuntimeEnvironment.getApplication()
        settings = SettingsRepository(context)
        val cookies = CookieStore(context)
        val bili = BiliHttpClient(cookies)
        val signer = WbiSigner(bili)
        repository = DownloadRepository(
            context, settings, MediaRepository(bili, signer, cookies, OpusRepository(bili, cookies)),
            ExtrasRepository(bili, signer), ExportRepository(context, settings),
        ).also {
            ReflectionHelpers.getField<CoroutineScope>(it, "scope").cancel()
            ReflectionHelpers.setField(it, "storeFile", File(directory, "downloads.json"))
            it.ensureLoaded()
        }
    }

    @After fun tearDown() {
        ReflectionHelpers.getField<CoroutineScope>(settings, "settingsScope").cancel()
        directory.deleteRecursively()
    }

    @Test fun itemsOfOneBatchShareTheFolderTheyAllRequest() {
        assertFalse(settings.shouldOverwriteExistingNamingTargets())
        val season = "$root/幕末Rock"
        val batch = DownloadFolderBatch()
        val paths = (1..3).map { pathOf(repository.createGroup("第${it}话", null, relativePath = season, folderBatch = batch)) }
        assertEquals(List(3) { season }, paths)
    }

    @Test fun aLaterBatchStillAvoidsTheFolderAnEarlierDownloadUsed() {
        val season = "$root/幕末Rock"
        repository.createGroup("第1话", null, relativePath = season, folderBatch = DownloadFolderBatch())
        val laterBatch = DownloadFolderBatch()
        val paths = (1..2).map {
            pathOf(repository.createGroup("第${it}话", null, relativePath = season, folderBatch = laterBatch))
        }
        assertEquals(List(2) { "$season(1)" }, paths)
    }

    private fun pathOf(groupId: Long): String {
        val info = ReflectionHelpers.getField<Map<Long, Any>>(repository, "groupInfo").getValue(groupId)
        return ReflectionHelpers.getField(info, "relativePath")
    }
}
