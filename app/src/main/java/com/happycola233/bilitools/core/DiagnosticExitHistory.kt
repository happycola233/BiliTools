package com.happycola233.bilitools.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.IOException
import java.time.Instant

data class DiagnosticExit(
    val timestamp: Long,
    val reason: String,
    val description: String?,
    val importance: Int,
    val pss: Long = 0,
    val rss: Long = 0,
    val trace: String? = null,
    val isProblem: Boolean = false,
) {
    fun summary() = "time=${Instant.ofEpochMilli(timestamp)} reason=$reason description=$description importance=$importance pssKb=$pss rssKb=$rss"
}

class DiagnosticExitHistory(private val context: Context) {
    private val preferences = context.getSharedPreferences("diagnostic-exits", Context.MODE_PRIVATE)

    fun initialize() {
        @Suppress("DEPRECATION")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        if (preferences.getLong("version", -1) != version) {
            preferences.edit().putLong("version", version)
                .putLong("handledThrough", System.currentTimeMillis()).commit()
        }
    }

    fun recordUncaughtException() {
        // API 29 没有系统退出历史，标记必须在原异常处理器终止进程之前同步保存。
        preferences.edit().putLong("uncaughtAt", System.currentTimeMillis()).commit()
    }

    fun recent(includeTrace: Boolean = false): List<DiagnosticExit> {
        if (Build.VERSION.SDK_INT < 30) {
            val timestamp = preferences.getLong("uncaughtAt", 0)
            return if (timestamp == 0L) emptyList() else listOf(
                DiagnosticExit(timestamp, "CRASH", "Uncaught exception", 0, isProblem = true),
            )
        }
        val manager = context.getSystemService(ActivityManager::class.java)
        return manager.getHistoricalProcessExitReasons(context.packageName, 0, 5).map { exit ->
            val trace = if (includeTrace && exit.reason == ApplicationExitInfo.REASON_ANR) {
                try {
                    exit.traceInputStream?.use { input ->
                        // ANR trace 是系统边界，限制读取量；原生崩溃 tombstone 是 protobuf，不当文本读取。
                        val buffer = ByteArray(16 * 1024)
                        var count = 0
                        while (count < buffer.size) {
                            val read = input.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        buffer.decodeToString(0, count).lineSequence().take(40).joinToString("\n")
                    }
                } catch (error: IOException) {
                    AppLog.w("DiagnosticExitHistory", "[exit] trace unavailable", error)
                    "unavailable (${error.javaClass.simpleName})"
                }
            } else null
            DiagnosticExit(
                timestamp = exit.timestamp,
                reason = "${reasonName(exit.reason)}(${exit.reason})",
                description = exit.description,
                importance = exit.importance,
                pss = exit.pss,
                rss = exit.rss,
                trace = trace,
                isProblem = exit.reason in setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR),
            )
        }.sortedByDescending { it.timestamp }
    }

    fun pendingProblem(): DiagnosticExit? = recent().firstOrNull {
        it.isProblem && it.timestamp > preferences.getLong("handledThrough", 0)
    }

    fun acknowledge(exit: DiagnosticExit) {
        preferences.edit().putLong("handledThrough", exit.timestamp).apply()
    }

    private fun reasonName(reason: Int): String = when (reason) {
        0 -> "UNKNOWN"
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "UNKNOWN"
    }
}
