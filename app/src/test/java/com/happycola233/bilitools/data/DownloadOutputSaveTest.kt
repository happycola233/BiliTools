package com.happycola233.bilitools.data

import android.content.pm.ProviderInfo
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers

private typealias Failure = DownloadTestMediaProvider.Failure

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadOutputSaveTest {
    @Test fun failedInsertKeepsExistingOutputAndDownloadedInput() = verifyFailure(Failure.Insert, overwrite = true)
    @Test fun failedWriteCannotMistakeAnOldSameNameFileForThisDownload() = verifyFailure(Failure.Write, overwrite = false)
    @Test fun failedFinalizeKeepsExistingOutputAndDownloadedInput() = verifyFailure(Failure.Finalize, overwrite = true)
    @Test fun zeroRowFinalizeIsNotAReportedSuccess() = verifyFailure(Failure.FinalizeZeroRows, overwrite = true)
    @Test fun unreadableNewOutputCannotSucceedUsingItsDatabaseSize() = verifyFailure(Failure.Read, overwrite = true)

    @Test fun renameFailureKeepsDownloadedInputAndRetryRemovesTheIncompleteCopy() = withFixture(true, Failure.Rename) { fixture ->
        assertNull(fixture.save())
        assertTrue(fixture.source.exists())
        assertArrayEquals(fixture.downloadedBytes, fixture.provider.rows.getValue(2).file.readBytes())
        fixture.provider.failure = Failure.None
        assertNotNull(fixture.save())
        assertFalse(fixture.source.exists())
        assertEquals(1, fixture.provider.rows.size)
        assertEquals("clip.m4a", fixture.provider.rows.values.single().name)
    }

    private fun verifyFailure(failure: Failure, overwrite: Boolean) = withFixture(overwrite, failure) { fixture ->
        assertNull(fixture.save())
        assertArrayEquals(fixture.downloadedBytes, fixture.source.readBytes())
        assertArrayEquals(fixture.originalBytes, fixture.provider.rows.getValue(1).file.readBytes())
        assertEquals(setOf(1L), fixture.provider.rows.keys)
        assertFalse(fixture.provider.events.contains("deleted:1"))
    }

    @Test fun overwritePublishesNewDataBeforeDeletingOldOutputAndRestoresTheRequestedName() = withFixture(true) { fixture ->
        val saved = fixture.save()
        assertEquals(fixture.provider.uri(2).toString(), saved)
        assertEquals(setOf(2L), fixture.provider.rows.keys)
        val output = fixture.provider.rows.getValue(2)
        assertEquals("clip.m4a", output.name)
        assertEquals(0, output.pending)
        assertArrayEquals(fixture.downloadedBytes, output.file.readBytes())
        assertFalse(fixture.source.exists())
        val events = fixture.provider.events
        assertTrue(events.toString(), events.indexOf("published:2") < events.indexOf("deleted:1"))
        assertTrue(events.toString(), events.indexOf("deleted:1") < events.indexOf("renamed:2:clip.m4a"))
    }

    @Test fun withoutOverwriteTheOldFileRemainsAndOnlyTheNewUriIsReturned() = withFixture(false) { fixture ->
        assertEquals(fixture.provider.uri(2).toString(), fixture.save())
        assertEquals(setOf(1L, 2L), fixture.provider.rows.keys)
        assertArrayEquals(fixture.originalBytes, fixture.provider.rows.getValue(1).file.readBytes())
        assertArrayEquals(fixture.downloadedBytes, fixture.provider.rows.getValue(2).file.readBytes())
        assertEquals("clip (1).m4a", fixture.provider.rows.getValue(2).name)
        assertFalse(fixture.source.exists())
    }

    private fun withFixture(
        overwrite: Boolean,
        failure: Failure = Failure.None,
        block: suspend (Fixture) -> Unit,
    ) = runBlocking {
        val base = File("../.tmp/save-output-tests").apply { mkdirs() }
        val directory = Files.createTempDirectory(base.toPath(), "case-").toFile()
        try {
            block(Fixture(directory, overwrite, failure))
        } finally {
            directory.listFiles().orEmpty().forEach { it.delete() }
            directory.delete()
        }
    }

    private class Fixture(directory: File, overwrite: Boolean, failure: Failure) {
        val originalBytes = byteArrayOf(9, 8, 7, 6, 5, 4, 3)
        val downloadedBytes = byteArrayOf(1, 2, 3, 4)
        val source = File(directory, "download.part.m4a").apply { writeBytes(downloadedBytes) }
        val provider = DownloadTestMediaProvider(directory).apply { this.failure = failure }
        private val repository: DownloadRepository

        init {
            val context = RuntimeEnvironment.getApplication()
            provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
            ShadowContentResolver.registerProviderInternal("media", provider)
            provider.add("clip.m4a", "Download/BiliTools", originalBytes)
            val cookies = CookieStore(context)
            val settings = SettingsRepository(context).apply { setNamingOverwriteExistingFiles(overwrite) }
            val bili = BiliHttpClient(cookies, settings)
            val signer = WbiSigner(bili)
            val exports = ExportRepository(context, settings)
            exports.outputStorage.adoptLegacy(provider.uri(1).toString(), "Download/BiliTools", "Download/BiliTools", "clip.m4a", "old")
            repository = DownloadRepository(
                context, cookies, settings, MediaRepository(bili, signer, cookies, OpusRepository(bili, cookies)),
                ExtrasRepository(bili, signer), exports,
            )
            ReflectionHelpers.getField<CoroutineScope>(repository, "scope").cancel()
        }

        suspend fun save(): String? = repository.saveToDownloads(source, "clip.m4a", "Download/BiliTools", ownerKey = "fixture-task")
    }

}
