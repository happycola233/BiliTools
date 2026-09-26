package com.happycola233.bilitools.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileNotFoundException

/** 使用真实文件描述符，并执行删除 selection；测试不能靠忽略边界条件的 mock 获得假通过。 */
internal class DownloadTestMediaProvider(private val directory: File) : ContentProvider() {
    enum class Failure { None, Insert, Write, Read, Finalize, FinalizeZeroRows, Delete, Rename, RenameZeroRows }
    data class Row(
        val file: File,
        var name: String,
        var directory: String,
        var pending: Int,
        val added: Long,
        var owner: String?,
        var generation: Long = 1,
        var generationAdded: Long = 1,
        var volume: String = "external_primary",
        var mimeType: String? = "application/octet-stream",
    )
    val rows = linkedMapOf<Long, Row>()
    val events = mutableListOf<String>()
    var failure = Failure.None
    var version = "test-version"
    var beforeDelete: ((Row) -> Unit)? = null
    var afterInsert: ((Uri) -> Unit)? = null
    private var nextId = 0L

    fun uri(id: Long): Uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())

    fun add(name: String, path: String, bytes: ByteArray = byteArrayOf(1, 2, 3), pending: Int = 0): Uri {
        val id = ++nextId
        rows[id] = Row(File(directory, "$id.bin").apply { writeBytes(bytes) }, name,
            path.trimEnd('/') + "/", pending, id + 1000, requireNotNull(context).packageName)
        return uri(id)
    }

    fun row(uri: String): Row = rows.getValue(Uri.parse(uri).lastPathSegment!!.toLong())
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle =
        Bundle().apply { putString(Intent.EXTRA_TEXT, version) }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (failure == Failure.Insert) return null
        val input = requireNotNull(values)
        val requestedName = input.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
        val path = input.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)
        var name = requestedName
        var index = 1
        while (rows.values.any { it.name == name && it.directory == path && it.volume == MediaStore.VOLUME_EXTERNAL_PRIMARY }) {
            val dot = requestedName.lastIndexOf('.')
            name = if (dot > 0) requestedName.take(dot) + " ($index)" + requestedName.substring(dot)
                else "$requestedName ($index)"
            index++
        }
        return add(name, path, byteArrayOf(), 1).also {
            events += "inserted:${it.lastPathSegment}"
            afterInsert?.invoke(it)
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment!!.toLong()
        val row = rows[id] ?: throw FileNotFoundException()
        if (mode == "r" && failure == Failure.Read && id > 1) throw FileNotFoundException()
        val readOnly = mode == "r" || failure == Failure.Write && id > 1
        return ParcelFileDescriptor.open(row.file, if (readOnly) ParcelFileDescriptor.MODE_READ_ONLY
            else ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        if (failure == Failure.Finalize) error("Publish failed")
        if (failure == Failure.FinalizeZeroRows) return 0
        val id = uri.lastPathSegment!!.toLong()
        val row = rows[id] ?: return 0
        if (!matches(row, selection, selectionArgs)) return 0
        val input = requireNotNull(values)
        input.getAsInteger(MediaStore.MediaColumns.IS_PENDING)?.let {
            row.pending = it
            events += "published:$id"
        }
        input.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)?.let { name ->
            if (failure == Failure.Rename) throw SecurityException("Rename denied")
            if (failure == Failure.RenameZeroRows) return 0
            check(rows.none { (otherId, other) -> otherId != id && other.name == name &&
                other.directory == row.directory && other.volume == row.volume })
            row.name = name
            events += "renamed:$id:$name"
        }
        row.generation++
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        if (failure == Failure.Delete) throw SecurityException("Permission revoked")
        val id = uri.lastPathSegment!!.toLong()
        val row = rows[id] ?: return 0
        beforeDelete?.also { beforeDelete = null }?.invoke(row)
        if (!matches(row, selection, selectionArgs)) return 0
        rows.remove(id)
        row.file.delete()
        events += "deleted:$id"
        return 1
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(MediaStore.MediaColumns._ID)
        val id = uri.lastPathSegment?.toLongOrNull()
        return MatrixCursor(columns).apply {
            rows.filter { (rowId, row) -> (id == null || rowId == id) && matches(row, selection, selectionArgs) }
                .forEach { (rowId, row) ->
                    addRow(columns.map { if (it == MediaStore.MediaColumns._ID) rowId else value(row, it) }.toTypedArray())
                }
        }
    }

    private fun matches(row: Row, selection: String?, args: Array<out String>?): Boolean {
        if (selection == null) return true
        val columns = selection.split(" AND ").map { it.substringBefore('=').trim() }
        return columns.zip(requireNotNull(args).toList()).all { (column, expected) -> value(row, column)?.toString() == expected }
    }

    private fun value(row: Row, column: String): Any? = when (column) {
        MediaStore.MediaColumns.DISPLAY_NAME -> row.name
        MediaStore.MediaColumns.RELATIVE_PATH -> row.directory
        MediaStore.MediaColumns.SIZE -> row.file.length()
        MediaStore.MediaColumns.IS_PENDING -> row.pending
        MediaStore.MediaColumns.VOLUME_NAME -> row.volume
        MediaStore.MediaColumns.DATE_ADDED -> row.added
        MediaStore.MediaColumns.GENERATION_MODIFIED -> row.generation
        MediaStore.MediaColumns.GENERATION_ADDED -> row.generationAdded
        MediaStore.MediaColumns.OWNER_PACKAGE_NAME -> row.owner
        MediaStore.MediaColumns.MIME_TYPE -> row.mimeType
        else -> null
    }
}
