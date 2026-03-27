package com.happycola233.bilitools.core

import android.content.Context
import android.os.Process
import com.happycola233.bilitools.data.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DiagnosticLogStats(
    val fileCount: Int,
    val totalBytes: Long,
    val latestModifiedAtMillis: Long?,
)

data class DiagnosticLogSnapshot(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedAtMillis: Long,
    val content: String,
)

class DiagnosticLogStore(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandChannel = Channel<Command>(Channel.UNLIMITED)
    private val logDirectory = File(appContext.filesDir, LOG_DIRECTORY_NAME).apply { mkdirs() }
    private val _stats = MutableStateFlow(computeStats(listLogFiles()))
    val statsFlow: StateFlow<DiagnosticLogStats> = _stats.asStateFlow()
    private val lineTimeFormatter = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS XXX",
        Locale.ROOT,
    )
    private val fileTimeFormatter = DateTimeFormatter.ofPattern(
        "yyyyMMdd-HHmmss",
        Locale.ROOT,
    )

    @Volatile
    private var currentSessionId: String = createSessionId()

    init {
        scope.launch {
            processCommands()
        }
    }

    fun currentSessionId(): String = currentSessionId

    fun append(priority: Int, tag: String, message: String, throwable: Throwable?) {
        if (!settingsRepository.currentSettings().issueReportDetailedLoggingEnabled) {
            return
        }

        val sessionId = currentSessionId
        commandChannel.trySend(
            Command.Write(
                formatLogLines(
                    sessionId = sessionId,
                    priority = priority,
                    tag = tag,
                    message = message,
                    throwable = throwable,
                ),
            ),
        )
    }

    fun startNewSession(reason: String? = null) {
        val nextSessionId = createSessionId()
        currentSessionId = nextSessionId
        commandChannel.trySend(
            Command.StartNewSession(
                sessionId = nextSessionId,
                reason = reason,
            ),
        )
    }

    suspend fun flush() {
        val completion = CompletableDeferred<Unit>()
        commandChannel.send(Command.Flush(completion))
        completion.await()
    }

    suspend fun clear() {
        val completion = CompletableDeferred<Unit>()
        commandChannel.send(Command.Clear(completion))
        completion.await()
    }

    fun stats(): DiagnosticLogStats {
        return _stats.value
    }

    fun snapshots(): List<DiagnosticLogSnapshot> {
        return listLogFiles().map { file ->
            DiagnosticLogSnapshot(
                name = file.name,
                sizeBytes = file.length(),
                lastModifiedAtMillis = file.lastModified(),
                content = runCatching {
                    file.readText(StandardCharsets.UTF_8)
                }.getOrElse { error ->
                    "Unable to read ${file.name}: ${error.message}"
                },
            )
        }
    }

    private suspend fun processCommands() {
        var writerState = WriterState(sessionId = currentSessionId)

        for (command in commandChannel) {
            when (command) {
                is Command.Write -> {
                    val result = writeBlock(writerState, command.textBlock)
                    writerState = result.state
                    _stats.value = result.stats
                }

                is Command.StartNewSession -> {
                    writerState = writerState.copy(
                        sessionId = command.sessionId,
                        activeFile = null,
                        partIndex = 0,
                    )
                    if (!command.reason.isNullOrBlank()) {
                        val result = writeBlock(
                            writerState,
                            textBlock = formatSessionMarker(
                                sessionId = command.sessionId,
                                reason = command.reason,
                            ),
                        )
                        writerState = result.state
                        _stats.value = result.stats
                    }
                }

                is Command.Flush -> {
                    command.completion.complete(Unit)
                }

                is Command.Clear -> {
                    logDirectory.deleteRecursively()
                    logDirectory.mkdirs()
                    writerState = WriterState(sessionId = currentSessionId)
                    _stats.value = EMPTY_STATS
                    command.completion.complete(Unit)
                }
            }
        }
    }

    private fun writeBlock(state: WriterState, textBlock: String): WriteResult {
        var activeState = state
        val bytes = textBlock.toByteArray(StandardCharsets.UTF_8)
        val target = ensureWritableTarget(activeState, bytes.size)
        if (target.file !== activeState.activeFile || target.partIndex != activeState.partIndex) {
            activeState = activeState.copy(
                activeFile = target.file,
                partIndex = target.partIndex,
            )
        }
        target.file.appendText(textBlock, StandardCharsets.UTF_8)
        val files = pruneLogsIfNeeded()
        return WriteResult(
            state = activeState,
            stats = computeStats(files),
        )
    }

    private fun ensureWritableTarget(state: WriterState, incomingByteCount: Int): WriterTarget {
        val currentFile = state.activeFile
        if (currentFile == null) {
            return WriterTarget(
                file = createSessionFile(state.sessionId, 0),
                partIndex = 0,
            )
        }
        if (currentFile.length() + incomingByteCount <= MAX_FILE_BYTES) {
            return WriterTarget(
                file = currentFile,
                partIndex = state.partIndex,
            )
        }
        val nextPartIndex = state.partIndex + 1
        return WriterTarget(
            file = createSessionFile(state.sessionId, nextPartIndex),
            partIndex = nextPartIndex,
        )
    }

    private fun createSessionFile(sessionId: String, partIndex: Int): File {
        val baseName = buildString {
            append("issue-log-")
            append(fileTimeFormatter.format(Instant.now().atZone(ZoneId.systemDefault())))
            append('-')
            append(sessionId)
            if (partIndex > 0) {
                append("-part")
                append(partIndex + 1)
            }
            append(".log")
        }
        return File(logDirectory, baseName).apply {
            parentFile?.mkdirs()
            if (!exists()) {
                createNewFile()
            }
        }
    }

    private fun pruneLogsIfNeeded(): List<File> {
        val files = listLogFiles().toMutableList()
        var totalBytes = files.sumOf(File::length)

        while (files.size > MAX_FILE_COUNT || totalBytes > MAX_TOTAL_BYTES) {
            val staleFile = files.removeFirstOrNull() ?: break
            totalBytes -= staleFile.length()
            staleFile.delete()
        }
        return files
    }

    private fun listLogFiles(): List<File> {
        return logDirectory.listFiles()
            ?.filter { it.isFile && it.extension.equals("log", ignoreCase = true) }
            ?.sortedBy(File::lastModified)
            .orEmpty()
    }

    private fun formatLogLines(
        sessionId: String,
        priority: Int,
        tag: String,
        message: String,
        throwable: Throwable?,
    ): String {
        val prefix = buildPrefix(sessionId, priority, tag)
        val body = normalizeMultiline(message)
            .lineSequence()
            .joinToString(separator = "\n") { line -> "$prefix$line" }

        val throwableBlock = throwable?.let { error ->
            val stacktraceWriter = StringWriter()
            PrintWriter(stacktraceWriter).use { writer ->
                error.printStackTrace(writer)
            }
            normalizeMultiline(stacktraceWriter.toString())
                .lineSequence()
                .joinToString(separator = "\n") { line -> "$prefix! $line" }
        }

        return buildString {
            append(body)
            append('\n')
            if (!throwableBlock.isNullOrBlank()) {
                append(throwableBlock)
                append('\n')
            }
        }
    }

    private fun formatSessionMarker(sessionId: String, reason: String): String {
        val prefix = buildPrefix(sessionId, android.util.Log.INFO, TAG)
        return "$prefix[session] $reason\n"
    }

    private fun buildPrefix(sessionId: String, priority: Int, tag: String): String {
        val timestamp = lineTimeFormatter.format(Instant.now().atZone(ZoneId.systemDefault()))
        val thread = Thread.currentThread()
        return buildString {
            append(timestamp)
            append(" | pid=")
            append(Process.myPid())
            append(" | thread=")
            append(thread.name)
            append(" | session=")
            append(sessionId)
            append(" | ")
            append(AppLog.priorityLabel(priority))
            append(" | ")
            append(tag)
            append(" | ")
        }
    }

    private fun normalizeMultiline(rawValue: String): String {
        return rawValue.replace("\r\n", "\n").replace('\r', '\n').trimEnd()
    }

    private fun createSessionId(): String {
        return buildString {
            append(fileTimeFormatter.format(Instant.now().atZone(ZoneId.systemDefault())))
            append('-')
            append(Integer.toHexString((System.nanoTime() and 0xFFFFFF).toInt()))
        }
    }

    private fun computeStats(files: List<File>): DiagnosticLogStats {
        return DiagnosticLogStats(
            fileCount = files.size,
            totalBytes = files.sumOf(File::length),
            latestModifiedAtMillis = files.maxOfOrNull(File::lastModified)?.takeIf { it > 0L },
        )
    }

    private data class WriterState(
        val sessionId: String,
        val activeFile: File? = null,
        val partIndex: Int = 0,
    )

    private data class WriteResult(
        val state: WriterState,
        val stats: DiagnosticLogStats,
    )

    private data class WriterTarget(
        val file: File,
        val partIndex: Int,
    )

    private sealed interface Command {
        data class Write(val textBlock: String) : Command

        data class StartNewSession(
            val sessionId: String,
            val reason: String?,
        ) : Command

        data class Flush(val completion: CompletableDeferred<Unit>) : Command

        data class Clear(val completion: CompletableDeferred<Unit>) : Command
    }

    companion object {
        private const val TAG = "DiagnosticLogStore"
        private const val LOG_DIRECTORY_NAME = "issue-report-logs"
        private const val MAX_FILE_COUNT = 8
        private const val MAX_FILE_BYTES = 768 * 1024L
        private const val MAX_TOTAL_BYTES = 4L * 1024L * 1024L
        private val EMPTY_STATS = DiagnosticLogStats(
            fileCount = 0,
            totalBytes = 0L,
            latestModifiedAtMillis = null,
        )
    }
}
