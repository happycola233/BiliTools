package com.happycola233.bilitools.core

import android.content.Context
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DiagnosticLogSnapshot(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedAtMillis: Long,
    val content: String,
)

/** 单写协程直接追加文件；Flush / Snapshot / Clear 与写入在同一队列中串行。 */
class DiagnosticLogStore internal constructor(
    private val directory: File,
    val redactor: LogRedactor = LogRedactor(),
    private val now: () -> Long = System::currentTimeMillis,
    private val maxFileBytes: Int = 512 * 1024,
    private val maxTotalBytes: Long = 3L * 1024 * 1024,
) {
    constructor(context: Context, redactor: LogRedactor = LogRedactor()) : this(
        File(context.filesDir, "diagnostics"), redactor,
    ) {
        // 旧日志可能含未脱敏的 CDN 查询参数，不迁移到新的存储。
        File(context.filesDir, "issue-report-logs").deleteRecursively()
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeFile: File? = null
    private var sequence = 0L
    private var writeFailure: IOException? = null
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT)

    init {
        scope.launch {
            directory.mkdirs()
            prune()
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Write -> appendBlock(command.text)
                        is Command.Flush -> {
                            writeFailure?.let { throw it }
                            files().forEach { file -> java.io.FileOutputStream(file, true).use { it.fd.sync() } }
                            command.done.complete(Unit)
                        }
                        is Command.Snapshot -> {
                            prune()
                            command.done.complete(files().map { file ->
                                DiagnosticLogSnapshot(file.name, file.length(), file.lastModified(), file.readText())
                            })
                        }
                        is Command.Clear -> {
                            files().forEach { if (!it.delete()) throw IOException("Unable to clear diagnostic log") }
                            activeFile = null
                            writeFailure = null
                            command.done.complete(Unit)
                        }
                    }
                } catch (error: IOException) {
                    writeFailure = error
                    // 存储边界失败不能杀死写入协程，也不能递归写回同一日志。
                    android.util.Log.w("DiagnosticLogStore", "Diagnostic storage unavailable", error)
                    when (command) {
                        is Command.Flush -> command.done.completeExceptionally(error)
                        is Command.Snapshot -> command.done.completeExceptionally(error)
                        is Command.Clear -> command.done.completeExceptionally(error)
                        is Command.Write -> Unit
                    }
                }
            }
        }
    }

    fun startNewSession(reason: String? = null) {
        commands.trySend(Command.Write(redactor.redact("=== Session ${Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault())} ${reason.orEmpty()} ===\n")))
    }

    fun append(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val thread = if (Thread.currentThread() === Looper.getMainLooper().thread) "main" else Thread.currentThread().name.take(24)
        val prefix = "${timeFormatter.format(Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()))} ${AppLog.priorityLabel(priority)} $tag [$thread] "
        val content = buildString {
            append(prefix)
            appendLine(message.replace("\r\n", "\n").replace('\r', '\n'))
            throwable?.let { appendLine(it.stackTraceToString()) }
        }
        // 在入队前脱敏，避免登录状态变化使排队中的文本漏掉自身 UID。
        commands.trySend(Command.Write(redactor.redact(content)))
    }

    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Flush(done))
        done.await()
    }

    suspend fun snapshots(): List<DiagnosticLogSnapshot> {
        val done = CompletableDeferred<List<DiagnosticLogSnapshot>>()
        commands.send(Command.Snapshot(done))
        return done.await()
    }

    suspend fun clear() {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Clear(done))
        done.await()
    }

    private fun appendBlock(text: String) {
        prune()
        // 超长异常按 UTF-8 字符边界切块，不能拆开 emoji 的代理对或多字节字符。
        val encoded = text.toByteArray(Charsets.UTF_8)
        var offset = 0
        while (offset < encoded.size) {
            var end = minOf(offset + maxFileBytes, encoded.size)
            if (end < encoded.size) {
                while (encoded[end].toInt() and 0xC0 == 0x80) end--
            }
            val bytes = encoded.copyOfRange(offset, end)
            var target = activeFile
            if (target == null || !target.exists() || target.length() + bytes.size > maxFileBytes) {
                target = File(directory, "run-${now()}-${(sequence++).toString().padStart(6, '0')}.log")
                activeFile = target
            }
            target.appendBytes(bytes)
            target.setLastModified(now())
            offset = end
        }
        prune()
    }

    private fun files() = directory.listFiles()?.filter { it.isFile && it.extension == "log" }
        ?.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name }).orEmpty()

    private fun prune() {
        val files = files().toMutableList()
        var total = files.sumOf { it.length() }
        val cutoff = now() - RETENTION_MILLIS
        for (file in files) {
            if (file.lastModified() < cutoff || total > maxTotalBytes) {
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
    }

    private sealed interface Command {
        data class Write(val text: String) : Command
        data class Flush(val done: CompletableDeferred<Unit>) : Command
        data class Snapshot(val done: CompletableDeferred<List<DiagnosticLogSnapshot>>) : Command
        data class Clear(val done: CompletableDeferred<Unit>) : Command
    }

    companion object {
        const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
