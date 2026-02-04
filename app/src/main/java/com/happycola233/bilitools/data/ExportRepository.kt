package com.happycola233.bilitools.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportRepository(context: Context) {
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
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
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
}
