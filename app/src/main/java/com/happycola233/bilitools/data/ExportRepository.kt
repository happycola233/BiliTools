package com.happycola233.bilitools.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportRepository(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {
    internal val outputStorage = DownloadOutputStorage(context)

    suspend fun saveText(
        fileName: String,
        mimeType: String?,
        content: String,
        relativePath: String = "${Environment.DIRECTORY_DOWNLOADS}/BiliTools",
    ): Uri? = saveBytes(fileName, mimeType, content.toByteArray(Charsets.UTF_8), relativePath)

    suspend fun saveBytes(
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        relativePath: String = "${Environment.DIRECTORY_DOWNLOADS}/BiliTools",
        downloadRoot: String? = settingsRepository.historicalRootFor(relativePath),
        ownerKey: String? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        val root = downloadRoot ?: return@withContext null
        outputStorage.save(
            fileName, mimeType, relativePath, root,
            settingsRepository.shouldOverwriteExistingNamingTargets(), bytes.size.toLong(), ownerKey,
        ) { bytes.inputStream() }?.let(Uri::parse)
    }
}
