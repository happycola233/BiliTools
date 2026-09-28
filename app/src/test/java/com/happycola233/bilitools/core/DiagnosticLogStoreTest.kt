package com.happycola233.bilitools.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DiagnosticLogStoreTest {
    @Test fun rotatesCapsRetentionAndRedactsBeforeWriting() = runBlocking {
        val directory = File("../.tmp/diagnostic-store-test-${System.nanoTime()}").apply { mkdirs() }
        var now = 2_000_000_000_000L
        val expired = File(directory, "expired.log").apply { writeText("old"); setLastModified(now - DiagnosticLogStore.RETENTION_MILLIS - 1) }
        val store = DiagnosticLogStore(directory, LogRedactor { "987654321" }, now = { now }, maxFileBytes = 512, maxTotalBytes = 1536)
        repeat(30) { store.append(5, "test", "record=$it SESSDATA=test-secret uid=987654321 " + "中".repeat(100), null) }
        store.flush()
        val snapshots = store.snapshots()
        assertFalse(expired.exists())
        assertTrue(snapshots.size > 1)
        assertTrue(snapshots.all { it.sizeBytes <= 512 })
        assertTrue(snapshots.sumOf { it.sizeBytes } <= 1536)
        val disk = directory.listFiles()!!.joinToString { it.readText() }
        assertFalse(disk.contains("test-secret"))
        assertFalse(disk.contains("987654321"))
        assertTrue(disk.contains("<self-uid>"))
        assertFalse(disk.contains('\uFFFD'))
        now += DiagnosticLogStore.RETENTION_MILLIS + 1
        assertTrue(store.snapshots().isEmpty())
        store.append(4, "test", "new", null)
        store.clear()
        assertTrue(store.snapshots().isEmpty())
        store.append(4, "test", "after-clear", null)
        assertTrue(store.snapshots().single().content.contains("after-clear"))
    }

    @Test fun oversizedUnicodeEntryKeepsEveryCodePoint() = runBlocking {
        val directory = File("../.tmp/diagnostic-unicode-test-${System.nanoTime()}")
        val store = DiagnosticLogStore(directory, maxFileBytes = 512)
        val message = "abc😀中".repeat(500)
        store.append(4, "test", message, null)
        val snapshots = store.snapshots()
        assertTrue(snapshots.all { it.sizeBytes <= 512 })
        assertTrue(snapshots.joinToString("") { it.content }.contains(message))
    }
}
