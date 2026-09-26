package com.happycola233.bilitools.data

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], shadows = [DirectoryRemovalLinuxShadow::class])
class DownloadDirectoryCleanupTest {
    @get:Rule val directoryRemoval = DirectoryRemovalLinuxShadow.Fixture()
    private lateinit var external: File
    private lateinit var cleanup: DownloadDirectoryCleanup
    private val root = "Download/Original"

    @Before fun setUp() {
        val base = File("../.tmp/directory-cleanup-tests").apply { mkdirs() }
        external = Files.createTempDirectory(base.toPath(), "case-").toFile()
        cleanup = DownloadDirectoryCleanup(external)
    }

    @After fun tearDown() { external.deleteRecursively() }

    private fun directory(path: String) = File(external, path).apply { mkdirs() }

    @Test fun removesEmptyAncestorsAndAlwaysKeepsTheDownloadRoot() {
        val leaf = directory("$root/Batch/UP")
        cleanup.removeEmptyParents("$root/Batch/UP", root, emptyList())
        assertFalse(leaf.exists())
        assertFalse(File(external, "$root/Batch").exists())
        assertTrue(File(external, root).isDirectory)
        cleanup.removeEmptyParents(root, root, emptyList())
        assertTrue(File(external, root).isDirectory)
    }

    @Test fun stopsAtNonEmptyParentAndDoesNotScanOtherDirectories() {
        directory("$root/Batch/UP")
        val sibling = directory("$root/Batch/Other")
        val unrelated = directory("$root/Unrelated")
        cleanup.removeEmptyParents("$root/Batch/UP", root, emptyList())
        assertFalse(File(external, "$root/Batch/UP").exists())
        assertTrue(sibling.isDirectory)
        assertTrue(unrelated.isDirectory)
        assertTrue(File(external, "$root/Batch").isDirectory)
    }

    @Test fun keepsNonEmptyTaskDirectoryIncludingHiddenFiles() {
        val marker = File(directory("$root/UP"), ".nomedia").apply { createNewFile() }
        cleanup.removeEmptyParents("$root/UP", root, emptyList())
        assertTrue(marker.isFile)
    }

    @Test fun keepsCurrentHistoricalAndActiveTaskDirectories() {
        directory("$root/Batch/UP")
        cleanup.removeEmptyParents("$root/Batch/UP", root, listOf("$root/Batch"))
        assertFalse(File(external, "$root/Batch/UP").exists())
        assertTrue(File(external, "$root/Batch").isDirectory)
        val active = directory("$root/Active")
        cleanup.removeEmptyParents("$root/Active", root, listOf("$root/Active"))
        assertTrue(active.isDirectory)
    }

    @Test fun refusesOutsidePathsAndSimilarPrefixes() {
        val outside = directory("Download/Other")
        val prefix = directory("${root}Backup")
        for (path in listOf("Download/Other", "${root}Backup", "$root/../Other", "/$root/UP")) {
            cleanup.removeEmptyParents(path, root, emptyList())
        }
        assertTrue(outside.isDirectory)
        assertTrue(prefix.isDirectory)
    }

    @Test fun aFileAtTheDirectoryPathIsNeverUnlinked() {
        val file = File(directory(root), "UP").apply { writeText("user file") }
        cleanup.removeEmptyParents("$root/UP", root, emptyList())
        assertEquals("user file", file.readText())
    }

    @Test fun directoryReplacedWithAFileDuringCleanupIsPreserved() {
        val target = directory("$root/UP")
        DirectoryRemovalLinuxShadow.beforeRemove = { path ->
            Files.delete(path)
            Files.write(path, "user file".toByteArray())
        }
        cleanup.removeEmptyParents("$root/UP", root, emptyList())
        assertEquals("user file", target.readText())
    }

    @Test fun fileCreatedDuringCleanupMakesDirectoryNonEmptyAndIsPreserved() {
        val target = directory("$root/UP")
        DirectoryRemovalLinuxShadow.beforeRemove = { path -> Files.write(path.resolve("new.mp4"), byteArrayOf(1)) }
        cleanup.removeEmptyParents("$root/UP", root, emptyList())
        assertTrue(File(target, "new.mp4").isFile)
    }
}
