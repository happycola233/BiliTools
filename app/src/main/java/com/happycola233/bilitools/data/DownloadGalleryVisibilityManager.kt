package com.happycola233.bilitools.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

internal class DownloadGalleryVisibilityManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val filesCollection = MediaStore.Files.getContentUri("external")

    fun applyPolicy(
        downloadRootRelativePath: String,
        hideFromSystemAlbum: Boolean,
    ) {
        val normalizedRoot = normalizeRootPath(downloadRootRelativePath)
        if (normalizedRoot.isBlank()) return
        val mediaStoreRoot = toMediaStoreRelativePath(normalizedRoot)

        val fileUpdated = if (hideFromSystemAlbum) {
            ensureNoMediaFile(mediaStoreRoot)
        } else {
            removeNoMediaFile(mediaStoreRoot)
        }
        refreshMediaIndex(mediaStoreRoot)
        Log.i(
            TAG,
            "[nomedia] policy applied, root=$mediaStoreRoot, hide=$hideFromSystemAlbum, fileUpdated=$fileUpdated",
        )
    }

    private fun ensureNoMediaFile(mediaStoreRoot: String): Boolean {
        normalizeWrongNoMediaName(mediaStoreRoot)

        if (hasExactNoMediaFile(mediaStoreRoot)) {
            Log.d(TAG, "[nomedia] exact file already exists, root=$mediaStoreRoot")
            return true
        }

        val fsCreated = createNoMediaViaFileSystem(mediaStoreRoot)
        if (fsCreated && hasExactNoMediaFile(mediaStoreRoot)) {
            Log.i(TAG, "[nomedia] created exact file via filesystem, root=$mediaStoreRoot")
            return true
        }

        val existing = queryNoMediaUris(mediaStoreRoot)
        if (existing.isNotEmpty()) {
            Log.d(
                TAG,
                "[nomedia] exact file found in MediaStore, root=$mediaStoreRoot, count=${existing.size}",
            )
            return true
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, NO_MEDIA_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, mediaStoreRoot)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val insertedUri = runCatching {
            resolver.insert(filesCollection, values)
        }.onFailure { err ->
            Log.w(
                TAG,
                "[nomedia] insert failed, root=$mediaStoreRoot",
                err,
            )
        }.getOrNull()
        if (insertedUri == null) {
            val retry = createNoMediaViaFileSystem(mediaStoreRoot)
            return retry && hasExactNoMediaFile(mediaStoreRoot)
        }

        val inserted = runCatching {
            resolver.openOutputStream(insertedUri, "w")?.use { output ->
                output.flush()
            } ?: error("openOutputStream returned null")
            val finalizeValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(insertedUri, finalizeValues, null, null)
            true
        }.onFailure { err ->
            Log.w(
                TAG,
                "[nomedia] create via MediaStore failed, uri=$insertedUri, root=$mediaStoreRoot",
                err,
            )
            runCatching { resolver.delete(insertedUri, null, null) }
        }.getOrDefault(false)
        if (!inserted) {
            val retry = createNoMediaViaFileSystem(mediaStoreRoot)
            return retry && hasExactNoMediaFile(mediaStoreRoot)
        }

        if (normalizeInsertedNoMediaName(insertedUri, mediaStoreRoot)) {
            Log.i(
                TAG,
                "[nomedia] created exact file via MediaStore, uri=$insertedUri, root=$mediaStoreRoot",
            )
            return true
        }

        runCatching { resolver.delete(insertedUri, null, null) }
        val retry = createNoMediaViaFileSystem(mediaStoreRoot)
        return retry && hasExactNoMediaFile(mediaStoreRoot)
    }

    private fun normalizeInsertedNoMediaName(insertedUri: Uri, mediaStoreRoot: String): Boolean {
        val actualName = readDisplayName(insertedUri)
        if (actualName == NO_MEDIA_FILE_NAME) return true

        if (tryForceDisplayName(insertedUri, NO_MEDIA_FILE_NAME)) {
            return true
        }

        if (renameNoMediaFileViaFileSystem(insertedUri, mediaStoreRoot)) {
            normalizeWrongNoMediaName(mediaStoreRoot)
            return hasExactNoMediaFile(mediaStoreRoot)
        }

        Log.w(
            TAG,
            "[nomedia] failed to normalize display name, uri=$insertedUri, actualName=$actualName, root=$mediaStoreRoot",
        )
        return hasExactNoMediaFile(mediaStoreRoot)
    }

    private fun removeNoMediaFile(mediaStoreRoot: String): Boolean {
        val uris = (queryNoMediaUris(mediaStoreRoot) + queryWrongNoMediaUris(mediaStoreRoot))
            .distinct()
        var deletedRows = 0
        uris.forEach { uri ->
            deletedRows += runCatching {
                resolver.delete(uri, null, null)
            }.onFailure { err ->
                Log.w(
                    TAG,
                    "[nomedia] delete row failed, uri=$uri, root=$mediaStoreRoot",
                    err,
                )
            }.getOrDefault(0)
        }
        val mediaStoreDeleted = deletedRows > 0
        if (mediaStoreDeleted) {
            Log.i(
                TAG,
                "[nomedia] removed via MediaStore, root=$mediaStoreRoot, rows=$deletedRows",
            )
        }

        val fileDeleted = deleteNoMediaViaFileSystem(mediaStoreRoot)
        val wrongFileDeleted = deleteWrongNoMediaViaFileSystem(mediaStoreRoot)
        return mediaStoreDeleted || fileDeleted || wrongFileDeleted
    }

    private fun queryNoMediaUris(mediaStoreRoot: String): List<Uri> {
        return queryUrisByDisplayName(
            mediaStoreRoot = mediaStoreRoot,
            displayName = NO_MEDIA_FILE_NAME,
        )
    }

    private fun queryWrongNoMediaUris(mediaStoreRoot: String): List<Uri> {
        return queryUrisByDisplayName(
            mediaStoreRoot = mediaStoreRoot,
            displayName = WRONG_NO_MEDIA_FILE_NAME,
        )
    }

    private fun queryUrisByDisplayName(
        mediaStoreRoot: String,
        displayName: String,
    ): List<Uri> {
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val rootNoSlash = mediaStoreRoot.trimEnd('/')
        val selection = buildString {
            append("${MediaStore.MediaColumns.DISPLAY_NAME}=?")
            append(" AND (")
            append("${MediaStore.MediaColumns.RELATIVE_PATH}=?")
            append(" OR ")
            append("${MediaStore.MediaColumns.RELATIVE_PATH}=?")
            append(')')
        }
        val selectionArgs = arrayOf(displayName, mediaStoreRoot, rootNoSlash)
        return runCatching {
            buildList {
                resolver.query(
                    filesCollection,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    while (idIndex >= 0 && cursor.moveToNext()) {
                        add(ContentUris.withAppendedId(filesCollection, cursor.getLong(idIndex)))
                    }
                }
            }
        }.onFailure { err ->
            Log.w(
                TAG,
                "[nomedia] query failed, root=$mediaStoreRoot, displayName=$displayName",
                err,
            )
        }.getOrDefault(emptyList())
    }

    private fun normalizeWrongNoMediaName(mediaStoreRoot: String) {
        val rootDir = resolveRootDirectory(mediaStoreRoot)
        if (rootDir != null) {
            val wrong = File(rootDir, WRONG_NO_MEDIA_FILE_NAME)
            val correct = File(rootDir, NO_MEDIA_FILE_NAME)
            if (wrong.exists() && !correct.exists()) {
                val renamed = runCatching { wrong.renameTo(correct) }.getOrDefault(false)
                if (renamed) {
                    cleanupWrongNoMediaRows(mediaStoreRoot)
                    Log.i(
                        TAG,
                        "[nomedia] renamed wrong file to exact name, from=${wrong.absolutePath}, to=${correct.absolutePath}",
                    )
                    return
                }
            }
            if (wrong.exists() && correct.exists()) {
                runCatching { wrong.delete() }
            }
        }

        val wrongUris = queryWrongNoMediaUris(mediaStoreRoot)
        wrongUris.forEach { uri ->
            if (tryForceDisplayName(uri, NO_MEDIA_FILE_NAME)) {
                Log.i(TAG, "[nomedia] renamed wrong row to exact name, uri=$uri")
            }
        }
        cleanupWrongNoMediaRows(mediaStoreRoot)
    }

    private fun cleanupWrongNoMediaRows(mediaStoreRoot: String) {
        queryWrongNoMediaUris(mediaStoreRoot).forEach { uri ->
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    private fun refreshMediaIndex(mediaStoreRoot: String) {
        val rootDir = resolveRootDirectory(mediaStoreRoot)
        val scanTargets = linkedSetOf<String>()
        if (rootDir != null) {
            scanTargets += rootDir.absolutePath
            scanTargets += File(rootDir, NO_MEDIA_FILE_NAME).absolutePath
            scanTargets += File(rootDir, WRONG_NO_MEDIA_FILE_NAME).absolutePath
        }
        queryMediaPathsUnderRoot(mediaStoreRoot).forEach { scanTargets += it }

        if (scanTargets.isNotEmpty()) {
            MediaScannerConnection.scanFile(
                appContext,
                scanTargets.toTypedArray(),
                null,
                null,
            )
            Log.d(
                TAG,
                "[nomedia] media scan dispatched, root=$mediaStoreRoot, targets=${scanTargets.size}",
            )
        }

        if (rootDir != null) {
            val renamed = tryRenameRoundTrip(rootDir)
            if (renamed) {
                MediaScannerConnection.scanFile(
                    appContext,
                    arrayOf(rootDir.absolutePath),
                    null,
                    null,
                )
                Log.d(
                    TAG,
                    "[nomedia] rename-refresh succeeded, root=${rootDir.absolutePath}",
                )
            } else {
                Log.d(
                    TAG,
                    "[nomedia] rename-refresh skipped or failed, root=${rootDir.absolutePath}",
                )
            }
        }
    }

    private fun queryMediaPathsUnderRoot(mediaStoreRoot: String): List<String> {
        val likeArg = "$mediaStoreRoot%"
        val projection = arrayOf(
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(likeArg)
        return runCatching {
            buildList {
                resolver.query(
                    filesCollection,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
                )?.use { cursor ->
                    val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (pathIndex >= 0 && nameIndex >= 0 && cursor.moveToNext()) {
                        if (size >= MAX_SCAN_PATHS) break
                        val relativePath = cursor.getString(pathIndex).orEmpty()
                        val displayName = cursor.getString(nameIndex).orEmpty()
                        toAbsolutePath(relativePath, displayName)?.let { add(it) }
                    }
                }
            }
        }.onFailure { err ->
            Log.w(
                TAG,
                "[nomedia] query media rows failed, root=$mediaStoreRoot",
                err,
            )
        }.getOrDefault(emptyList())
    }

    private fun createNoMediaViaFileSystem(mediaStoreRoot: String): Boolean {
        val rootDir = resolveRootDirectory(mediaStoreRoot) ?: return false
        val ensuredDir = if (rootDir.exists()) {
            true
        } else {
            runCatching { rootDir.mkdirs() }.getOrDefault(false)
        }
        if (!ensuredDir) {
            Log.w(
                TAG,
                "[nomedia] mkdirs failed for filesystem creation, root=${rootDir.absolutePath}",
            )
            return false
        }

        val wrong = File(rootDir, WRONG_NO_MEDIA_FILE_NAME)
        if (wrong.exists()) {
            runCatching { wrong.delete() }
        }

        val noMedia = File(rootDir, NO_MEDIA_FILE_NAME)
        if (noMedia.exists()) return true
        val created = runCatching { noMedia.createNewFile() }
            .onFailure { err ->
                Log.w(
                    TAG,
                    "[nomedia] filesystem create failed, path=${noMedia.absolutePath}",
                    err,
                )
            }
            .getOrDefault(false)
        if (created) {
            Log.i(
                TAG,
                "[nomedia] created via filesystem, path=${noMedia.absolutePath}",
            )
        }
        return created
    }

    private fun hasExactNoMediaFile(mediaStoreRoot: String): Boolean {
        val rootDir = resolveRootDirectory(mediaStoreRoot)
        val fsExists = rootDir?.let { File(it, NO_MEDIA_FILE_NAME).exists() } == true
        if (fsExists) return true
        return queryNoMediaUris(mediaStoreRoot).isNotEmpty()
    }

    private fun deleteNoMediaViaFileSystem(mediaStoreRoot: String): Boolean {
        val rootDir = resolveRootDirectory(mediaStoreRoot) ?: return false
        val noMedia = File(rootDir, NO_MEDIA_FILE_NAME)
        if (!noMedia.exists()) return false
        val deleted = runCatching { noMedia.delete() }
            .onFailure { err ->
                Log.w(
                    TAG,
                    "[nomedia] filesystem delete failed, path=${noMedia.absolutePath}",
                    err,
                )
            }
            .getOrDefault(false)
        if (deleted) {
            Log.i(
                TAG,
                "[nomedia] removed via filesystem, path=${noMedia.absolutePath}",
            )
        }
        return deleted
    }

    private fun deleteWrongNoMediaViaFileSystem(mediaStoreRoot: String): Boolean {
        val rootDir = resolveRootDirectory(mediaStoreRoot) ?: return false
        val wrong = File(rootDir, WRONG_NO_MEDIA_FILE_NAME)
        if (!wrong.exists()) return false
        return runCatching { wrong.delete() }
            .onFailure { err ->
                Log.w(
                    TAG,
                    "[nomedia] delete wrong filename failed, path=${wrong.absolutePath}",
                    err,
                )
            }
            .getOrDefault(false)
    }

    private fun renameNoMediaFileViaFileSystem(insertedUri: Uri, mediaStoreRoot: String): Boolean {
        val rootDir = resolveRootDirectory(mediaStoreRoot) ?: return false
        val target = File(rootDir, NO_MEDIA_FILE_NAME)
        if (target.exists()) return true

        val sourcePath = queryDataPath(insertedUri) ?: resolvePathFromFileDescriptor(insertedUri)
        if (sourcePath.isNullOrBlank()) return false

        val source = File(sourcePath)
        if (!source.exists()) return false
        if (source.name == NO_MEDIA_FILE_NAME) return true
        val renamed = runCatching { source.renameTo(target) }
            .onFailure { err ->
                Log.w(
                    TAG,
                    "[nomedia] filesystem rename failed, from=${source.absolutePath}, to=${target.absolutePath}",
                    err,
                )
            }
            .getOrDefault(false)
        if (!renamed) return false

        MediaScannerConnection.scanFile(
            appContext,
            arrayOf(target.absolutePath),
            null,
            null,
        )
        return target.exists()
    }

    private fun tryForceDisplayName(uri: Uri, displayName: String): Boolean {
        val rows = runCatching {
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                },
                null,
                null,
            )
        }.onFailure { err ->
            Log.w(
                TAG,
                "[nomedia] force display name failed, uri=$uri, target=$displayName",
                err,
            )
        }.getOrDefault(0)
        if (rows <= 0) return false
        return readDisplayName(uri) == displayName
    }

    private fun readDisplayName(uri: Uri): String? {
        return runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index).orEmpty()
                } else {
                    null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    @Suppress("DEPRECATION")
    private fun queryDataPath(uri: Uri): String? {
        return runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index).orEmpty()
                } else {
                    null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun resolvePathFromFileDescriptor(uri: Uri): String? {
        return runCatching {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                File("/proc/self/fd/${pfd.fd}").canonicalPath
            }
        }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.contains(" (deleted)") }
    }

    private fun resolveRootDirectory(mediaStoreRoot: String): File? {
        val normalized = mediaStoreRoot
            .replace('\\', '/')
            .trim()
            .trim('/')
            .takeIf { it.isNotBlank() }
            ?: return null
        return File(Environment.getExternalStorageDirectory(), normalized)
    }

    private fun toAbsolutePath(relativePath: String, displayName: String): String? {
        val normalizedPath = relativePath
            .replace('\\', '/')
            .trim()
            .trim('/')
        val normalizedName = displayName.trim()
        if (normalizedPath.isBlank() || normalizedName.isBlank()) return null
        return File(
            Environment.getExternalStorageDirectory(),
            "$normalizedPath/$normalizedName",
        ).absolutePath
    }

    private fun tryRenameRoundTrip(rootDir: File): Boolean {
        if (!rootDir.exists() || !rootDir.isDirectory) return false
        val parent = rootDir.parentFile ?: return false
        val tmp = File(parent, "${rootDir.name}.bt_refresh_${System.currentTimeMillis()}")
        if (tmp.exists()) return false
        val moved = runCatching { rootDir.renameTo(tmp) }.getOrDefault(false)
        if (!moved) return false

        val restored = runCatching { tmp.renameTo(rootDir) }.getOrDefault(false)
        if (!restored) {
            runCatching { tmp.renameTo(rootDir) }
            return false
        }
        return true
    }

    private fun normalizeRootPath(rawPath: String): String {
        val cleaned = rawPath
            .replace('\\', '/')
            .trim()
            .trim('/')
        return if (cleaned.isBlank()) {
            SettingsRepository.DEFAULT_DOWNLOAD_ROOT
        } else {
            cleaned
        }
    }

    private fun toMediaStoreRelativePath(relativePath: String): String {
        val cleaned = relativePath
            .replace('\\', '/')
            .trim()
            .trim('/')
        return if (cleaned.isBlank()) "" else "$cleaned/"
    }

    companion object {
        private const val TAG = "DownloadGalleryVisibility"
        private const val NO_MEDIA_FILE_NAME = ".nomedia"
        private const val WRONG_NO_MEDIA_FILE_NAME = "_.nomedia"
        private const val MAX_SCAN_PATHS = 256
    }
}
