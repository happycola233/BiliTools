package com.happycola233.bilitools.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.AtomicFile
import com.happycola233.bilitools.core.AppLog as Log
import com.happycola233.bilitools.core.naming.NamingRenderer
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class OutputDeleteResult { Deleted, Missing, Blocked, Failed }

/**
 * 公共下载文件唯一的写入、替换和删除入口。
 * 归属清单独立于任务列表保存：清除任务记录不会把用户保留的文件变成无主文件。
 * URI 只是定位信息，执行破坏性操作前还要核对实际卷、目录和文件身份。
 */
internal class DownloadOutputStorage(private val context: Context) {
    private val resolver = context.contentResolver
    private val mutationMutex = Mutex()
    private val recordLock = Any()
    private val file = AtomicFile(File(context.filesDir, "download_outputs.json"))
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(OutputOwnershipStore::class.java)
    private val records by lazy {
        synchronized(recordLock) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
                mutableMapOf<String, OwnedDownloadOutput>()
            } else {
                // 清单无法读取时停止操作，不能丢掉归属后再靠文件名猜测。
                val saved = file.openRead().bufferedReader().use { adapter.fromJson(it.readText()) }
                requireNotNull(saved).outputs.associateBy { it.uri }.toMutableMap()
            }
        }
    }

    private fun version(): String = requireNotNull(MediaStore.getVersion(context))

    fun record(uri: String?): OwnedDownloadOutput? =
        if (uri == null) null else synchronized(recordLock) { records[uri] }

    fun outputsFor(ownerKey: String): List<OwnedDownloadOutput> = synchronized(recordLock) {
        records.values.filter { it.ownerKey == ownerKey }
    }

    /** 只恢复已持久化且身份仍匹配的本任务产物，处理文件完成后、任务写回前的进程退出。 */
    fun completedOutputFor(ownerKey: String): OwnedDownloadOutput? = runCatching {
        outputsFor(ownerKey).asReversed().firstOrNull { output ->
            output.complete && output.volume == MediaStore.VOLUME_EXTERNAL_PRIMARY &&
                DownloadPaths.contains(output.root, output.directory) &&
                output.storeVersion == version() &&
                query(Uri.parse(output.uri))?.let { matchesIdentity(output, it, false) } == true
        }
    }.onFailure { Log.w(TAG, "Cannot verify interrupted output", it) }.getOrNull()

    fun sameFile(first: String?, second: String?): Boolean {
        if (first == null || second == null) return false
        if (first == second) return true
        val a = mediaId(Uri.parse(first)) ?: return false
        val b = mediaId(Uri.parse(second)) ?: return false
        // external 是旧版使用的聚合卷 URI；实际卷仍由删除前的查询核实。
        return a.second == b.second &&
            (a.first == b.first || a.first == "external" || b.first == "external")
    }

    /** 旧记录只迁移原 URI，且必须位于已知历史根目录和原组目录，绝不搜寻同名替身。 */
    fun adoptLegacy(uri: String, root: String, directory: String, requestedName: String, ownerKey: String): Boolean =
        synchronized(recordLock) {
            if (records.containsKey(uri)) return@synchronized true
            if (!DownloadPaths.contains(root, directory)) return@synchronized false
            val row = query(Uri.parse(uri)) ?: return@synchronized false
            if (row.volume != MediaStore.VOLUME_EXTERNAL_PRIMARY || row.owner != context.packageName || row.pending != 0 ||
                row.directory != DownloadPaths.normalize(directory) ||
                row.name != requestedName || !DownloadPaths.isFileName(row.name)
            ) return@synchronized false
            remember(row.copy(root = root, storeVersion = version(), ownerKey = ownerKey))
            true
        }

    suspend fun save(
        fileName: String,
        mimeType: String?,
        directory: String,
        root: String,
        overwrite: Boolean,
        expectedSize: Long,
        ownerKey: String? = null,
        openInput: () -> InputStream,
    ): String? = mutationMutex.withLock {
        val safeRoot = DownloadPaths.normalize(root) ?: return@withLock null
        val safeDirectory = DownloadPaths.normalize(directory) ?: return@withLock null
        if (!DownloadPaths.contains(safeRoot, safeDirectory)) return@withLock null
        val name = NamingRenderer.sanitizeComponent(fileName).ifBlank { "BiliTools" }
        if (!DownloadPaths.isFileName(name)) return@withLock null
        // 在插入前确认清单可读，不能把损坏清单覆盖成新的空清单。
        synchronized(recordLock) { records.size }
        currentCoroutineContext().ensureActive()
        var created: OwnedDownloadOutput? = null
        var replacementStarted = false
        try {
            // 用户已开启覆盖时，以精确目标位置为准，不要求旧文件在应用清单中。
            val conflicts = if (overwrite) replacementTargets(name, safeDirectory, safeRoot) else emptyList()
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                mimeType?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$safeDirectory/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withLock null
            val row = query(uri) ?: return@withLock null
            if (row.volume != MediaStore.VOLUME_EXTERNAL_PRIMARY || row.directory != safeDirectory || row.owner != context.packageName ||
                !DownloadPaths.isFileName(row.name)
            ) {
                // 提供者返回了意外位置，不能为了回滚而在目录外执行删除。
                return@withLock null
            }
            created = row.copy(root = safeRoot, storeVersion = version(), complete = false, ownerKey = ownerKey)
            remember(created)
            resolver.openOutputStream(uri, "w")?.use { output ->
                openInput().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Cannot open download output")
            currentCoroutineContext().ensureActive()
            publish(uri)
            val actualSize = checkReadable(uri, expectedSize)
            val published = requireNotNull(query(uri))
            check(published.directory == safeDirectory && published.pending == 0)
            created = published.copy(root = safeRoot, size = actualSize, storeVersion = created.storeVersion,
                complete = false, ownerKey = ownerKey)
            remember(created)
            currentCoroutineContext().ensureActive()
            if (overwrite) {
                conflicts.filterNot { sameFile(it.uri, uri.toString()) }.forEach { existing ->
                    val result = deleteVerified(existing, onDeleted = { replacementStarted = true })
                    check(result == OutputDeleteResult.Deleted || result == OutputDeleteResult.Missing) {
                        "Cannot replace existing output"
                    }
                }
                val beforeRename = created
                if (beforeRename.name != name) {
                    val rename = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
                    check(resolver.update(uri, rename, identitySelection(beforeRename), identityArgs(beforeRename)) == 1)
                    val renamed = requireNotNull(query(uri))
                    check(renamed.volume == MediaStore.VOLUME_EXTERNAL_PRIMARY &&
                        renamed.directory == safeDirectory && renamed.name == name) {
                        "Provider could not restore the requested output name"
                    }
                    created = renamed.copy(root = safeRoot, size = actualSize, storeVersion = beforeRename.storeVersion,
                        complete = false, ownerKey = ownerKey)
                }
            }
            remember(requireNotNull(created).copy(complete = true))
            // 重试已成功，才回收该任务上次覆盖失败时保留的未完成副本。
            if (ownerKey != null) outputsFor(ownerKey)
                .filter { !it.complete && !sameFile(it.uri, uri.toString()) }
                .forEach { deleteVerified(it, rollback = true) }
            uri.toString()
        } catch (error: CancellationException) {
            if (!replacementStarted) created?.let { deleteVerified(it, rollback = true) }
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Save output failed, replacedExisting=$replacementStarted", error)
            // 已替换旧件时保留新件与未完成记录；返回失败，让上层保留下载源供重试。
            // 未完成记录不能在重启后被当作成功下载恢复。
            if (!replacementStarted) created?.let { deleteVerified(it, rollback = true) }
            null
        }
    }

    suspend fun delete(uri: String, ownerKeys: Set<String>? = null): OutputDeleteResult = mutationMutex.withLock {
        val output = record(uri)
        if (output == null) {
            return@withLock try {
                if (mediaId(Uri.parse(uri)) != null && query(Uri.parse(uri)) == null) OutputDeleteResult.Missing
                else OutputDeleteResult.Blocked
            } catch (_: Exception) { OutputDeleteResult.Failed }
        }
        if (ownerKeys != null && output.ownerKey !in ownerKeys) return@withLock OutputDeleteResult.Blocked
        deleteVerified(output, rollback = !output.complete)
    }

    private fun replacementTargets(name: String, directory: String, root: String): List<OwnedDownloadOutput> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.VOLUME_NAME}=?"
        val args = arrayOf(name, "$directory/", MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uris = requireNotNull(resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE), selection, args, null)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    // MediaStore 中目录没有文件 MIME；同名目录永远不能作为覆盖目标。
                    val mimeType = cursor.getString(1)
                    check(mimeType != null && mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                        "Output path is a directory"
                    }
                    add(ContentUris.withAppendedId(collection, cursor.getLong(0)))
                }
            }
        }
        val storeVersion = version()
        return uris.mapNotNull { uri ->
            query(uri)?.also { row ->
                check(row.volume == MediaStore.VOLUME_EXTERNAL_PRIMARY && row.directory == directory &&
                    row.name == name && row.pending == 0) { "Replacement target changed or is still being written" }
            }?.copy(root = root, storeVersion = storeVersion)
        }
    }

    private fun deleteVerified(
        output: OwnedDownloadOutput,
        rollback: Boolean = false,
        onDeleted: () -> Unit = {},
    ): OutputDeleteResult {
        return try {
            if (!DownloadPaths.contains(output.root, output.directory) ||
                output.volume != MediaStore.VOLUME_EXTERNAL_PRIMARY ||
                !DownloadPaths.isFileName(output.name) || output.storeVersion != version()
            ) return OutputDeleteResult.Blocked
            val uri = Uri.parse(output.uri)
            val current = query(uri) ?: run {
                forget(output.uri)
                return OutputDeleteResult.Missing
            }
            if (!matchesIdentity(output, current, rollback)) return OutputDeleteResult.Blocked
            // 条件随单文件删除一起交给提供者，防止检查后移动/重命名导致越界。
            val count = resolver.delete(uri, identitySelection(current), identityArgs(current))
            if (count == 1) {
                onDeleted()
                forget(output.uri)
                OutputDeleteResult.Deleted
            } else OutputDeleteResult.Blocked
        } catch (error: Exception) {
            Log.w(TAG, "Output deletion failed, uri=${output.uri}", error)
            OutputDeleteResult.Failed
        }
    }

    private fun matchesIdentity(output: OwnedDownloadOutput, current: OwnedDownloadOutput, rollback: Boolean): Boolean =
        current.mimeType != null && current.mimeType != DocumentsContract.Document.MIME_TYPE_DIR &&
            current.volume == output.volume && current.directory == output.directory &&
            current.name == output.name && current.added == output.added && current.owner == output.owner &&
            current.generationAdded == output.generationAdded &&
            // 用户可以原地编辑文件；大小不作为跨时间的身份条件，删除瞬间仍核对最新大小。
            (rollback || current.pending == 0 && output.complete)

    private fun identitySelection(output: OwnedDownloadOutput): String = buildList {
        addAll(listOf(
            MediaStore.MediaColumns.VOLUME_NAME, MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.IS_PENDING,
        ))
        if (output.generationModified != null) add(MediaStore.MediaColumns.GENERATION_MODIFIED)
        if (output.generationAdded != null) add(MediaStore.MediaColumns.GENERATION_ADDED)
        if (output.mimeType != null) add(MediaStore.MediaColumns.MIME_TYPE)
    }.joinToString(" AND ") { "$it=?" }

    private fun identityArgs(output: OwnedDownloadOutput): Array<String> = buildList {
        add(output.volume)
        add("${output.directory}/")
        add(output.name)
        add(output.added.toString())
        add(output.size.toString())
        add(output.pending.toString())
        output.generationModified?.let { add(it.toString()) }
        output.generationAdded?.let { add(it.toString()) }
        output.mimeType?.let { add(it) }
    }.toTypedArray()

    private fun query(uri: Uri): OwnedDownloadOutput? {
        mediaId(uri) ?: return null
        val columns = buildList {
            addAll(listOf(
                MediaStore.MediaColumns.VOLUME_NAME, MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.IS_PENDING,
                MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
            ))
            if (Build.VERSION.SDK_INT >= 30) {
                add(MediaStore.MediaColumns.GENERATION_MODIFIED)
                add(MediaStore.MediaColumns.GENERATION_ADDED)
            }
        }
        return requireNotNull(resolver.query(uri, columns.toTypedArray(), null, null, null)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            fun text(column: String) = cursor.getString(cursor.getColumnIndexOrThrow(column))
            fun number(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
            val directory = requireNotNull(DownloadPaths.normalize(text(MediaStore.MediaColumns.RELATIVE_PATH) ?: ""))
            OwnedDownloadOutput(
                uri = uri.toString(), root = "", directory = directory,
                name = text(MediaStore.MediaColumns.DISPLAY_NAME) ?: return@use null,
                volume = text(MediaStore.MediaColumns.VOLUME_NAME) ?: return@use null,
                added = number(MediaStore.MediaColumns.DATE_ADDED),
                size = number(MediaStore.MediaColumns.SIZE),
                pending = number(MediaStore.MediaColumns.IS_PENDING).toInt(),
                owner = text(MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
                generationModified = if (Build.VERSION.SDK_INT >= 30) number(MediaStore.MediaColumns.GENERATION_MODIFIED) else null,
                generationAdded = if (Build.VERSION.SDK_INT >= 30) number(MediaStore.MediaColumns.GENERATION_ADDED) else null,
                mimeType = text(MediaStore.MediaColumns.MIME_TYPE),
            )
        }
    }

    private fun mediaId(uri: Uri): Pair<String, Long>? {
        if (uri.scheme != "content" || uri.authority != "media" || uri.query != null || uri.fragment != null) return null
        val parts = uri.pathSegments
        if (parts.size != 3 || parts[1] !in setOf("downloads", "file")) return null
        val id = parts[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return parts[0] to id
    }

    private fun publish(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        check(resolver.update(uri, values, null, null) == 1) { "Cannot publish download output" }
    }

    private fun checkReadable(uri: Uri, expectedSize: Long): Long =
        requireNotNull(resolver.openFileDescriptor(uri, "r")).use { descriptor ->
            val actualSize = descriptor.statSize
            check(actualSize >= expectedSize)
            java.io.FileInputStream(descriptor.fileDescriptor).use { input ->
                check(expectedSize == 0L || input.read() >= 0)
            }
            actualSize
        }

    private fun remember(output: OwnedDownloadOutput) = synchronized(recordLock) {
        val previous = records.put(output.uri, output)
        try {
            persist()
        } catch (error: Exception) {
            if (previous == null) records.remove(output.uri) else records[output.uri] = previous
            throw error
        }
    }

    private fun forget(uri: String) = synchronized(recordLock) {
        // Files / Downloads / external URI 可能指向同一行，覆盖后同时清掉这些旧别名。
        val previous = records.filterKeys { sameFile(it, uri) }
        if (previous.isEmpty()) return@synchronized
        previous.keys.forEach(records::remove)
        try {
            persist()
        } catch (error: Exception) {
            records.putAll(previous)
            throw error
        }
    }

    private fun persist() {
        val stream = file.startWrite()
        try {
            stream.write(adapter.toJson(OutputOwnershipStore(records.values.toList())).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private companion object { const val TAG = "DownloadOutputStorage" }
}

internal data class OwnedDownloadOutput(
    val uri: String,
    val root: String,
    val directory: String,
    val name: String,
    val volume: String,
    val added: Long,
    val size: Long,
    val pending: Int,
    val owner: String?,
    // 新增代数标识媒体行身份；修改代数仅用于检查与删除之间的竞态，正常媒体扫描不能令归属失效。
    val generationAdded: Long?,
    val generationModified: Long?,
    val storeVersion: String? = null,
    val complete: Boolean = true,
    val ownerKey: String? = null,
    val mimeType: String? = null,
)

internal data class OutputOwnershipStore(val outputs: List<OwnedDownloadOutput> = emptyList())
