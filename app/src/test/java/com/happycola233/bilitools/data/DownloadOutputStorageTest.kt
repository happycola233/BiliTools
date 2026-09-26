package com.happycola233.bilitools.data

import android.content.pm.ProviderInfo
import android.net.Uri
import com.happycola233.bilitools.core.naming.NamingRenderer
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class DownloadOutputStorageTest {
    private lateinit var directory: File
    private lateinit var provider: DownloadTestMediaProvider
    private lateinit var storage: DownloadOutputStorage
    private val root = "Download/OldRoot"
    private val path = "$root/UP"

    @Before fun setUp() {
        val base = File("../.tmp/output-storage-tests").apply { mkdirs() }
        directory = Files.createTempDirectory(base.toPath(), "case-").toFile()
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "download_outputs.json").delete()
        provider = DownloadTestMediaProvider(directory)
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        storage = DownloadOutputStorage(context)
    }

    @After fun tearDown() { directory.deleteRecursively() }

    private suspend fun save(name: String = "video.mp4", owner: String = "task", overwrite: Boolean = false): String =
        requireNotNull(storage.save(name, "video/mp4", path, root, overwrite, 3, owner) { byteArrayOf(4, 5, 6).inputStream() })

    @Test fun storedOwnershipSurvivesRestartAndDoesNotDependOnCurrentSettings() = runBlocking {
        val uri = save()
        val reopened = DownloadOutputStorage(RuntimeEnvironment.getApplication())
        assertEquals(root, reopened.record(uri)?.root)
        assertEquals(OutputDeleteResult.Deleted, reopened.delete(uri))
    }

    @Test fun overwriteReplacesUntrackedSameNameFileWhenSystemAllowsIt() = runBlocking {
        val old = provider.add("video.mp4", path).toString()
        provider.row(old).owner = "another.app"
        val uri = save(overwrite = true)
        assertEquals(1, provider.rows.size)
        assertEquals("video.mp4", provider.row(uri).name)
        assertTrue(provider.events.indexOf("published:2") < provider.events.indexOf("deleted:1"))
    }

    @Test fun replacesKnownOutputAfterNewOutputIsPublishedAndRemovesOldUriAliases() = runBlocking {
        val first = save(owner = "old")
        val second = save(owner = "new", overwrite = true)
        assertEquals(1, provider.rows.size)
        assertNull(storage.record(first))
        assertEquals("video.mp4", provider.row(second).name)
        assertTrue(provider.events.indexOf("published:2") < provider.events.indexOf("deleted:1"))
    }

    @Test fun rejectsRenamedOutputAndMovesEvenWithinOriginalRoot() = runBlocking {
        val moved = save("moved.mp4")
        provider.row(moved).directory = "$root/Moved/"
        val renamed = save("renamed.mp4")
        provider.row(renamed).name = "user.mp4"
        for (uri in listOf(moved, renamed)) assertEquals(OutputDeleteResult.Blocked, storage.delete(uri))
        assertEquals(2, provider.rows.size)
    }

    @Test fun sizeChangeDoesNotPreventDeletingTheSameFileAfterRestart() = runBlocking {
        val uri = save()
        provider.row(uri).file.writeText("edited video with different size")
        provider.row(uri).generation++
        val reopened = DownloadOutputStorage(RuntimeEnvironment.getApplication())
        assertEquals(OutputDeleteResult.Deleted, reopened.delete(uri))
        assertTrue(provider.rows.isEmpty())
    }

    @Test fun sizeChangeDuringDeletionIsStillRejected() = runBlocking {
        val uri = save()
        provider.beforeDelete = { it.file.writeText("concurrent write") }
        assertEquals(OutputDeleteResult.Blocked, storage.delete(uri))
        assertNotNull(storage.record(uri))
    }

    @Test fun overwriteOnlyTargetsExactNameDirectoryAndPrimaryVolume() = runBlocking {
        provider.add("video.mp4", path)
        provider.add("video.mp4", "$path/Sub")
        provider.add("video.mp4", "${path}Backup")
        provider.add("video.mp4", "Download/Other")
        provider.add("other.mp4", path)
        val sd = provider.add("video.mp4", path).toString()
        provider.row(sd).volume = "1234-5678"
        val result = save(overwrite = true)
        assertEquals("video.mp4", provider.row(result).name)
        assertEquals(setOf(2L, 3L, 4L, 5L, 6L, 7L), provider.rows.keys)
    }

    @Test fun disabledOverwriteKeepsUntrackedFile() = runBlocking {
        provider.add("video.mp4", path)
        val uri = save()
        assertEquals("video (1).mp4", provider.row(uri).name)
        assertEquals(2, provider.rows.size)
    }

    @Test fun directoryAndPendingFileCannotBeReplaced() = runBlocking {
        val conflict = provider.add("video.mp4", path).toString()
        for (directoryMime in listOf(null, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)) {
            provider.row(conflict).mimeType = directoryMime
            assertNull(storage.save("video.mp4", "video/mp4", path, root, true, 3) { byteArrayOf(1, 2, 3).inputStream() })
        }
        provider.row(conflict).mimeType = "video/mp4"
        provider.row(conflict).pending = 1
        assertNull(storage.save("video.mp4", "video/mp4", path, root, true, 3) { byteArrayOf(1, 2, 3).inputStream() })
        assertEquals(1, provider.rows.size)
        assertTrue(provider.events.isEmpty())
    }

    @Test fun overwriteDeniedBySystemDoesNotSilentlyBecomeSuccessfulDuplicate() = runBlocking {
        val old = provider.add("video.mp4", path).toString()
        provider.beforeDelete = { throw SecurityException("Not granted") }
        assertNull(storage.save("video.mp4", "video/mp4", path, root, true, 3) { byteArrayOf(1, 2, 3).inputStream() })
        assertEquals(setOf(old.substringAfterLast('/').toLong()), provider.rows.keys)
        assertFalse(provider.events.contains("deleted:1"))
    }

    @Test fun overwriteTargetMovedBetweenCheckAndDeleteIsPreserved() = runBlocking {
        provider.add("video.mp4", path)
        provider.beforeDelete = { it.directory = "Download/Outside/" }
        assertNull(storage.save("video.mp4", "video/mp4", path, root, true, 3) { byteArrayOf(1, 2, 3).inputStream() })
        assertEquals(setOf(1L), provider.rows.keys)
    }

    @Test fun renameFailureAfterReplacementRetainsNewDataButDoesNotRecoverAsSuccess() = runBlocking {
        for (failure in listOf(DownloadTestMediaProvider.Failure.Rename, DownloadTestMediaProvider.Failure.RenameZeroRows)) {
            val old = provider.add("video.mp4", path)
            provider.failure = failure
            assertNull(storage.save("video.mp4", "video/mp4", path, root, true, 3, "new") { byteArrayOf(4, 5, 6).inputStream() })
            assertFalse(provider.rows.containsKey(old.lastPathSegment!!.toLong()))
            val output = storage.outputsFor("new").single()
            assertArrayEquals(byteArrayOf(4, 5, 6), provider.row(output.uri).file.readBytes())
            val reopened = DownloadOutputStorage(RuntimeEnvironment.getApplication())
            assertNull(reopened.completedOutputFor("new"))
            provider.failure = DownloadTestMediaProvider.Failure.None
            assertEquals(OutputDeleteResult.Deleted, storage.delete(output.uri))
        }
    }

    @Test fun conditionalDeleteBlocksMoveBetweenCheckAndDelete() = runBlocking {
        val uri = save()
        provider.beforeDelete = { it.directory = "Download/Outside/" }
        assertEquals(OutputDeleteResult.Blocked, storage.delete(uri))
        assertEquals(1, provider.rows.size)
    }

    @Test fun mediaDatabaseResetCannotAuthorizeOldIds() = runBlocking {
        val uri = save()
        provider.version = "new-database"
        assertEquals(OutputDeleteResult.Blocked, storage.delete(uri))
        assertEquals(1, provider.rows.size)
    }

    @Test fun metadataRescanDoesNotInvalidateOriginalFileOwnership() = runBlocking {
        val uri = save()
        provider.row(uri).generation++
        assertEquals(OutputDeleteResult.Deleted, storage.delete(uri))
    }

    @Test @Config(sdk = [35]) fun changedRowIdentityCannotBeDeletedEvenWithTheSameNameAndSize() = runBlocking {
        val uri = save()
        provider.row(uri).generationAdded++
        assertEquals(OutputDeleteResult.Blocked, storage.delete(uri))
        assertEquals(1, provider.rows.size)
    }

    @Test fun invalidTargetsNeverReachInsert() = runBlocking {
        for (target in listOf("Download/OldRoot/../Other", "Download/OldRoot(1)", "/Download/OldRoot",
            "Download/OldRoot/./UP", "Download/OldRoot//UP")) {
            assertNull(storage.save("x.mp4", null, target, root, false, 0) { byteArrayOf().inputStream() })
        }
        assertTrue(provider.rows.isEmpty())
    }

    @Test fun safePathComparisonUsesSegmentsAndRejectsDotComponents() {
        assertFalse(DownloadPaths.contains(root, "Download/OldRoot2"))
        assertFalse(DownloadPaths.contains(root, "$root/../user"))
        assertFalse(DownloadPaths.contains(root, "Download/oldroot"))
        for (name in listOf(".. ", ".. -", ". ", " .- ")) {
            assertTrue(NamingRenderer.normalizeComponent(name).isBlank())
        }
    }

    @Test fun legacyAdoptionRequiresOriginalLocationNameAndAppOwner() {
        val uri = provider.add("video.mp4", path).toString()
        assertFalse(storage.adoptLegacy(uri, root, "$root/Other", "video.mp4", "old"))
        assertFalse(storage.adoptLegacy(uri, root, path, "different.mp4", "old"))
        provider.row(uri).owner = "other.app"
        assertFalse(storage.adoptLegacy(uri, root, path, "video.mp4", "old"))
        provider.row(uri).owner = RuntimeEnvironment.getApplication().packageName
        provider.row(uri).volume = "1234-5678"
        assertFalse(storage.adoptLegacy(uri, root, path, "video.mp4", "old"))
        provider.row(uri).volume = "external_primary"
        assertTrue(storage.adoptLegacy(uri, root, path, "video.mp4", "old"))
    }

    @Test fun providerDeleteFailurePreservesOwnershipForRetry() = runBlocking {
        val uri = save()
        provider.failure = DownloadTestMediaProvider.Failure.Delete
        assertEquals(OutputDeleteResult.Failed, storage.delete(uri))
        assertNotNull(storage.record(uri))
        provider.failure = DownloadTestMediaProvider.Failure.None
        assertEquals(OutputDeleteResult.Deleted, storage.delete(uri))
    }

    @Test fun cancellationRollsBackOnlyTheNewOutput() = runBlocking {
        val old = save(owner = "old")
        val attempted = runCatching {
            storage.save("video.mp4", "video/mp4", path, root, true, 3, "new") {
                throw kotlinx.coroutines.CancellationException("cancel save")
            }
        }
        assertTrue(attempted.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(setOf(old.substringAfterLast('/').toLong()), provider.rows.keys)
        assertNotNull(storage.record(old))
    }

    @Test fun missingUnknownAndForeignUrisCannotDeleteOtherFiles() = runBlocking {
        val known = save()
        val unknown = provider.add("personal.mp4", path).toString()
        assertEquals(OutputDeleteResult.Blocked, storage.delete(unknown))
        assertEquals(OutputDeleteResult.Blocked, storage.delete("file:///storage/emulated/0/Download/personal.mp4"))
        assertEquals(OutputDeleteResult.Blocked, storage.delete(known, setOf("other-task")))
        assertEquals(2, provider.rows.size)
    }

    @Test fun corruptOwnershipManifestIsKeptAndBlocksMutations() = runBlocking {
        val uri = save()
        val manifest = File(RuntimeEnvironment.getApplication().filesDir, "download_outputs.json")
        manifest.writeText("{broken")
        val reopened = DownloadOutputStorage(RuntimeEnvironment.getApplication())
        assertTrue(runCatching { reopened.delete(uri) }.isFailure)
        assertEquals("{broken", manifest.readText())
        assertEquals(1, provider.rows.size)
    }
}
