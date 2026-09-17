package com.happycola233.bilitools.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import java.io.File
import java.io.FileNotFoundException
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadOutputSaveTest {
    @Test fun failedInsertKeepsExistingOutputAndDownloadedInput() = verifyFailure(Failure.Insert, overwrite = true)
    @Test fun failedWriteCannotMistakeAnOldSameNameFileForThisDownload() = verifyFailure(Failure.Write, overwrite = false)
    @Test fun failedFinalizeKeepsExistingOutputAndDownloadedInput() = verifyFailure(Failure.Finalize, overwrite = true)
    @Test fun zeroRowFinalizeIsNotAReportedSuccess() = verifyFailure(Failure.FinalizeZeroRows, overwrite = true)
    @Test fun unreadableNewOutputCannotSucceedUsingItsDatabaseSize() = verifyFailure(Failure.Read, overwrite = true)

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
        val provider = TestMediaProvider(directory, failure)
        private val repository: DownloadRepository

        init {
            val context = RuntimeEnvironment.getApplication()
            provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
            ShadowContentResolver.registerProviderInternal("media", provider)
            provider.addExisting(originalBytes)
            val cookies = CookieStore(context)
            val settings = SettingsRepository(context).apply { setNamingOverwriteExistingFiles(overwrite) }
            val bili = BiliHttpClient(cookies, settings)
            val signer = WbiSigner(bili)
            repository = DownloadRepository(
                context, cookies, settings, MediaRepository(bili, signer, cookies, OpusRepository(bili, cookies)),
                ExtrasRepository(bili, signer), ExportRepository(context, settings),
            )
            ReflectionHelpers.getField<CoroutineScope>(repository, "scope").cancel()
        }

        suspend fun save(): String? = repository.saveToDownloads(source, "clip.m4a", "Download/BiliTools")
    }

    private enum class Failure { None, Insert, Write, Read, Finalize, FinalizeZeroRows }

    /** 真实 ContentResolver 调用落到可控媒体提供者；文件复制使用真实描述符，失败不会被 mock 掩盖。 */
    private class TestMediaProvider(private val directory: File, private val failure: Failure) : ContentProvider() {
        data class Row(val file: File, var name: String, val relativePath: String, var pending: Int)
        val rows = linkedMapOf<Long, Row>()
        val events = mutableListOf<String>()
        private var nextId = 1L

        fun uri(id: Long): Uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())

        fun addExisting(bytes: ByteArray) {
            rows[1] = Row(File(directory, "old.bin").apply { writeBytes(bytes) }, "clip.m4a", "Download/BiliTools/", 0)
        }

        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String = "audio/mp4"

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            if (failure == Failure.Insert) return null
            val id = ++nextId
            val name = requireNotNull(values).getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
            val distinctName = if (rows.values.any { it.name == name }) "clip (1).m4a" else name
            rows[id] = Row(
                File(directory, "new-$id.bin").apply { writeBytes(byteArrayOf()) }, distinctName,
                values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH).trimEnd('/') + "/", 1,
            )
            events += "inserted:$id"
            return this.uri(id)
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val id = requireNotNull(uri.lastPathSegment).toLong()
            if (mode == "r" && failure == Failure.Read && id != 1L) throw FileNotFoundException("Output is not readable")
            val readOnly = mode == "r" || failure == Failure.Write && id != 1L
            return ParcelFileDescriptor.open(rows.getValue(id).file, if (readOnly) {
                ParcelFileDescriptor.MODE_READ_ONLY
            } else ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            val id = requireNotNull(uri.lastPathSegment).toLong()
            if (failure == Failure.Finalize) throw IllegalStateException("Media provider failed to publish")
            if (failure == Failure.FinalizeZeroRows) return 0
            val row = rows[id] ?: return 0
            requireNotNull(values).getAsInteger(MediaStore.MediaColumns.IS_PENDING)?.let {
                row.pending = it
                events += "published:$id"
            }
            values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)?.let {
                row.name = it
                events += "renamed:$id:$it"
            }
            return 1
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            val id = requireNotNull(uri.lastPathSegment).toLong()
            val row = rows.remove(id) ?: return 0
            row.file.delete()
            events += "deleted:$id"
            return 1
        }

        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor {
            val columns = projection ?: arrayOf(MediaStore.MediaColumns._ID)
            val id = uri.lastPathSegment?.toLongOrNull()
            val matching = rows.filter { (rowId, row) ->
                (id == null || rowId == id) && (selectionArgs.isNullOrEmpty() || row.name == selectionArgs[0])
            }
            return MatrixCursor(columns).apply {
                matching.forEach { (rowId, row) ->
                    addRow(columns.map { column ->
                        when (column) {
                            MediaStore.MediaColumns._ID -> rowId
                            MediaStore.MediaColumns.DISPLAY_NAME -> row.name
                            MediaStore.MediaColumns.RELATIVE_PATH -> row.relativePath
                            MediaStore.MediaColumns.SIZE -> row.file.length()
                            MediaStore.MediaColumns.IS_PENDING -> row.pending
                            else -> null
                        }
                    }.toTypedArray<Any?>())
                }
            }
        }
    }
}
