package com.happycola233.bilitools.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.happycola233.bilitools.core.DownloadNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

class ExportRepository(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val resolver: ContentResolver = context.contentResolver

    suspend fun saveText(
        fileName: String,
        mimeType: String?,
        content: String,
        relativePath: String = "${Environment.DIRECTORY_DOWNLOADS}/BiliTools",
    ): Uri? {
        return saveBytes(
            fileName,
            mimeType,
            content.toByteArray(Charsets.UTF_8),
            relativePath,
        )
    }

    suspend fun saveBytes(
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        relativePath: String = "${Environment.DIRECTORY_DOWNLOADS}/BiliTools",
    ): Uri? = withContext(Dispatchers.IO) {
        val overwrite = settingsRepository.shouldOverwriteExistingNamingTargets()
        val targetFileName = resolveOutputFileName(fileName, relativePath, overwrite)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, targetFileName)
            if (!mimeType.isNullOrBlank()) {
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
            }
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            val update = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, update, null, null)
        }
        uri
    }

    private fun resolveOutputFileName(
        fileName: String,
        relativePath: String,
        overwrite: Boolean,
    ): String {
        val normalized = DownloadNaming.sanitizeComponent(fileName).ifBlank { "BiliTools" }
        if (overwrite) {
            deleteExistingOutputs(normalized, relativePath)
            return normalized
        }
        if (!existsOutput(normalized, relativePath)) {
            return normalized
        }
        for (index in 1..200) {
            val candidate = buildAlternativeFileName(normalized, index)
            if (!existsOutput(candidate, relativePath)) {
                return candidate
            }
        }
        return buildAlternativeFileName(normalized, System.currentTimeMillis().toInt().absoluteValue)
    }

    private fun buildAlternativeFileName(fileName: String, index: Int): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex <= 0 || dotIndex == fileName.lastIndex) {
            "${fileName}($index)"
        } else {
            val base = fileName.substring(0, dotIndex)
            val ext = fileName.substring(dotIndex)
            "$base($index)$ext"
        }
    }

    private fun existsOutput(
        fileName: String,
        relativePath: String,
    ): Boolean {
        val collection = MediaStore.Files.getContentUri("external")
        val normalizedPath = normalizeRelativePath(relativePath)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(fileName, normalizedPath)
        return resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            cursor.moveToFirst()
        } ?: false
    }

    private fun deleteExistingOutputs(
        fileName: String,
        relativePath: String,
    ) {
        val collection = MediaStore.Files.getContentUri("external")
        val normalizedPath = normalizeRelativePath(relativePath)
        if (!isManagedRelativePath(normalizedPath)) return
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(fileName, normalizedPath)
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            while (idIndex >= 0 && cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                resolver.delete(Uri.withAppendedPath(collection, id.toString()), null, null)
            }
        }
    }

    private fun isManagedRelativePath(relativePath: String): Boolean {
        val normalizedPath = normalizeRelativePath(relativePath)
        if (normalizedPath.isBlank()) return false
        return managedRelativeRoots().any { normalizedPath.startsWith(it, ignoreCase = true) }
    }

    private fun managedRelativeRoots(): List<String> {
        return listOf(
            settingsRepository.downloadRootRelativePath(),
            "${Environment.DIRECTORY_DOWNLOADS}/BiliTools",
        ).map(::normalizeRelativePath)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalizeRelativePath(relativePath: String): String {
        val trimmed = relativePath
            .replace('\\', '/')
            .trim()
            .trimEnd('/')
        return if (trimmed.isBlank()) "" else "$trimmed/"
    }
}
