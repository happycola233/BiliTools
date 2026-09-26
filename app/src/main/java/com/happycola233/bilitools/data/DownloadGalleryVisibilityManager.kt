package com.happycola233.bilitools.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.happycola233.bilitools.core.AppLog as Log
import java.io.File
import java.nio.file.Files

/** 相册开关管理指定下载目录的 .nomedia；标记的来源和内容不影响开关行为。 */
internal class DownloadGalleryVisibilityManager(
    context: Context,
    private val externalRoot: File = Environment.getExternalStorageDirectory(),
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val filesCollection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @Synchronized
    fun applyPolicy(downloadRootRelativePath: String, hideFromSystemAlbum: Boolean, forceRefresh: Boolean = false) {
        val root = DownloadPaths.normalize(downloadRootRelativePath) ?: return
        val directory = File(externalRoot, root)
        try {
            val expected = externalRoot.canonicalFile.toPath().resolve(root).normalize()
            if (directory.canonicalFile.toPath() != expected) return
            val marker = File(directory, MARKER_NAME)
            if (marker.canonicalFile != File(directory.canonicalFile, MARKER_NAME)) return
            // 只处理文件，不把同名目录交给可能递归删除目录内容的 MediaProvider。
            if (marker.exists() && !marker.isFile) return
            val changed = if (hideFromSystemAlbum) ensureMarker(root, directory) else removeMarker(root, marker)
            if (changed || forceRefresh) {
                // 不改名整棵目录，也不按历史临时目录前缀搬动用户文件。
                MediaScannerConnection.scanFile(appContext,
                    arrayOf(directory.absolutePath, marker.absolutePath), null, null)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Cannot update album visibility, root=$root", error)
        }
    }

    private fun ensureMarker(root: String, directory: File): Boolean {
        val marker = File(directory, MARKER_NAME)
        if (marker.exists()) return false
        val created = runCatching {
            directory.mkdirs()
            marker.createNewFile()
        }.getOrDefault(false)
        if (created || marker.exists()) return created
        return createMarkerViaMediaStore(root, directory)
    }

    private fun createMarkerViaMediaStore(root: String, directory: File): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, MARKER_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$root/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(filesCollection, values) ?: return false
        val rootArgs = arrayOf("$root/", MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var created = false
        try {
            resolver.openOutputStream(uri, "w")?.use { it.flush() } ?: return false
            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, publish, ROOT_SELECTION, rootArgs) != 1) return false
            val name = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                ROOT_SELECTION, rootArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: return false
            if (!DownloadPaths.isFileName(name)) return false
            if (name != MARKER_NAME) {
                // MediaStore 可能把点开头的名称改成 _.nomedia（重名时还会加序号）。
                // 只修正本次 insert 返回的文件，不按名字查找或清理已有的 _.nomedia。
                val source = File(directory, name)
                if (source.canonicalFile != File(directory.canonicalFile, name) || !source.isFile) return false
                Files.move(source.toPath(), File(directory, MARKER_NAME).toPath())
            }
            created = true
            return true
        } finally {
            // 创建失败只回收本次插入的文件；move 默认不会覆盖已有的目标。
            if (!created) runCatching { resolver.delete(uri, ROOT_SELECTION, rootArgs) }
                .onFailure { Log.w(TAG, "Cannot remove failed album marker, uri=$uri", it) }
        }
    }

    private fun removeMarker(root: String, marker: File): Boolean {
        // .nomedia 内容可能由系统扫描器改写，不能用内容或创建者决定是否关闭隐藏。
        var changed = marker.isFile && marker.delete()
        val selection = "$ROOT_SELECTION AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val args = arrayOf("$root/", MediaStore.VOLUME_EXTERNAL_PRIMARY, MARKER_NAME)
        val rows = resolver.query(filesCollection, arrayOf(MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE), selection, args, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    // MediaStore 的目录记录没有文件 MIME；不通过媒体库删除这类记录。
                    val mimeType = cursor.getString(1) ?: continue
                    if (mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                        add(ContentUris.withAppendedId(filesCollection, cursor.getLong(0)) to mimeType)
                    }
                }
            }
        }.orEmpty()
        for ((uri, mimeType) in rows) {
            changed = resolver.delete(uri, "$selection AND ${MediaStore.MediaColumns.MIME_TYPE}=?",
                args + mimeType) > 0 || changed
        }
        return changed
    }

    private companion object {
        const val TAG = "DownloadGalleryVisibility"
        const val MARKER_NAME = ".nomedia"
        const val ROOT_SELECTION = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.VOLUME_NAME}=?"
    }
}
