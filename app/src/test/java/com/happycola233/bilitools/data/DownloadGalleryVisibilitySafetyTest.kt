package com.happycola233.bilitools.data

import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class DownloadGalleryVisibilitySafetyTest {
    private lateinit var external: File
    private lateinit var manager: DownloadGalleryVisibilityManager
    private lateinit var provider: DownloadTestMediaProvider
    private val root = "Download/BiliTools"

    @Before fun setUp() {
        val base = File("../.tmp/gallery-safety-tests").apply { mkdirs() }
        external = Files.createTempDirectory(base.toPath(), "case-").toFile()
        val context = RuntimeEnvironment.getApplication()
        provider = DownloadTestMediaProvider(File(external, "provider").apply { mkdirs() })
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        manager = DownloadGalleryVisibilityManager(context, external)
    }

    @After fun tearDown() { external.deleteRecursively() }

    @Test fun switchRemovesExistingMarkerRegardlessOfCreatorAndPreservesOtherFiles() {
        val directory = File(external, root).apply { mkdirs() }
        val marker = File(directory, ".nomedia").apply { writeText("user marker") }
        val alternative = File(directory, "_.nomedia").apply { writeText("user file") }
        val video = File(directory, "video.mp4").apply { writeText("video") }
        manager.applyPolicy(root, true)
        assertEquals("user marker", marker.readText())
        manager.applyPolicy(root, false, true)
        assertFalse(marker.exists())
        assertEquals("user file", alternative.readText())
        assertEquals("video", video.readText())
    }

    @Test fun newMarkerIsEmptyAndSystemRewrittenMarkerCanBeRemovedAfterRestart() {
        manager.applyPolicy(root, true)
        val marker = File(external, "$root/.nomedia")
        assertTrue(marker.isFile)
        assertEquals(0L, marker.length())
        // AOSP 会把目录路径写进标记，不能将这种正常变化当作归属丢失。
        marker.writeText(marker.parentFile!!.absolutePath)
        manager = DownloadGalleryVisibilityManager(RuntimeEnvironment.getApplication(), external)
        manager.applyPolicy(root, false)
        assertFalse(marker.exists())
    }

    @Test fun legacyEmptyMarkerNeedsNoOwnershipMigration() {
        val directory = File(external, root).apply { mkdirs() }
        val marker = File(directory, ".nomedia").apply { createNewFile() }
        manager.applyPolicy(root, false)
        assertFalse(marker.exists())
    }

    @Test fun invalidPathsCannotCreateOrDeleteMarkersOutsideDownloads() {
        val outside = File(external, "Personal").apply { mkdirs() }
        val marker = File(outside, ".nomedia").apply { writeText("private") }
        for (path in listOf("Personal", "Download/../Personal", "/Personal")) {
            manager.applyPolicy(path, true)
            manager.applyPolicy(path, false)
        }
        assertEquals("private", marker.readText())
        assertTrue(provider.events.isEmpty())
    }

    @Test fun switchingRootsOnlyRemovesThePreviousRootMarker() {
        manager.applyPolicy(root, true)
        manager.applyPolicy("Download/NewRoot", true)
        manager.applyPolicy(root, false)
        assertFalse(File(external, "$root/.nomedia").exists())
        assertTrue(File(external, "Download/NewRoot/.nomedia").exists())
    }

    @Test fun sameNamedDirectoryIsNeverDeleted() {
        val directory = File(external, "$root/.nomedia").apply { mkdirs() }
        val video = File(directory, "video.mp4").apply { writeText("video") }
        manager.applyPolicy(root, false)
        assertEquals("video", video.readText())
        assertTrue(provider.events.isEmpty())
    }

    @Test fun mediaStoreRemovalIsRestrictedToExactMarkerDirectoryAndPrimaryVolume() {
        val target = provider.add(".nomedia", root)
        provider.row(target.toString()).owner = "another.app"
        provider.add("video.mp4", root)
        provider.add("_.nomedia", root)
        provider.add(".nomedia", "$root/Child")
        provider.add(".nomedia", "${root}Backup")
        provider.add(".nomedia", "Personal")
        val sdCard = provider.add(".nomedia", root)
        provider.row(sdCard.toString()).volume = "1234-5678"
        val directory = provider.add(".nomedia", root)
        provider.row(directory.toString()).mimeType = null
        val documentDirectory = provider.add(".nomedia", root)
        provider.row(documentDirectory.toString()).mimeType = DocumentsContract.Document.MIME_TYPE_DIR
        manager.applyPolicy(root, false)
        assertEquals(8, provider.rows.size)
        assertEquals(listOf("deleted:${target.lastPathSegment}"), provider.events)
    }

    @Test fun mediaStoreMarkerMovedAfterQueryIsNotDeleted() {
        val marker = provider.add(".nomedia", root)
        provider.beforeDelete = { it.directory = "Personal/" }
        manager.applyPolicy(root, false)
        assertTrue(provider.rows.containsKey(marker.lastPathSegment!!.toLong()))
        assertTrue(provider.events.isEmpty())
    }

    @Test fun mediaStoreFilenameRewriteOnlyMovesTheNewlyCreatedFile() {
        val directory = File(external, root).apply { mkdirs() }
        val existing = File(directory, "_.nomedia").apply { writeText("existing") }
        simulateMediaStoreFilenameRewrite(directory, "_.nomedia (1)")
        assertTrue(createViaMediaStore(directory))
        val marker = File(directory, ".nomedia")
        assertTrue(marker.isFile)
        assertEquals(0L, marker.length())
        assertFalse(File(directory, "_.nomedia (1)").exists())
        assertEquals("existing", existing.readText())
    }

    @Test fun failedFilenameCorrectionDoesNotReplaceAnExistingMarker() {
        val directory = File(external, root).apply { mkdirs() }
        val marker = File(directory, ".nomedia").apply { writeText("existing") }
        simulateMediaStoreFilenameRewrite(directory, "_.nomedia")
        assertTrue(runCatching { createViaMediaStore(directory) }.isFailure)
        assertEquals("existing", marker.readText())
        assertTrue(provider.rows.isEmpty())
        assertFalse(File(directory, "_.nomedia").exists())
    }

    private fun simulateMediaStoreFilenameRewrite(directory: File, actualName: String) {
        provider.afterInsert = { uri ->
            val id = uri.lastPathSegment!!.toLong()
            val row = provider.rows.getValue(id)
            val file = File(directory, actualName)
            Files.move(row.file.toPath(), file.toPath())
            provider.rows[id] = row.copy(file = file, name = actualName)
        }
    }

    // 直接调用受系统文件访问权限影响的创建分支，模拟 MediaStore 改名后的真实文件。
    private fun createViaMediaStore(directory: File): Boolean = ReflectionHelpers.callInstanceMethod(
        manager, "createMarkerViaMediaStore",
        ReflectionHelpers.ClassParameter.from(String::class.java, root),
        ReflectionHelpers.ClassParameter.from(File::class.java, directory),
    )
}
