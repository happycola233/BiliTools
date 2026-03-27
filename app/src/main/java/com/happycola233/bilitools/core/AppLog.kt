package com.happycola233.bilitools.core

import android.util.Log as AndroidLog

object AppLog {
    @Volatile
    private var diagnosticLogStore: DiagnosticLogStore? = null

    fun install(store: DiagnosticLogStore) {
        diagnosticLogStore = store
    }

    fun startNewDiagnosticSession(reason: String? = null) {
        diagnosticLogStore?.startNewSession(reason)
    }

    suspend fun flushDiagnosticLogs() {
        diagnosticLogStore?.flush()
    }

    fun d(tag: String, message: String): Int = log(AndroidLog.DEBUG, tag, message, null)

    fun d(tag: String, message: String, throwable: Throwable?): Int {
        return log(AndroidLog.DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String): Int = log(AndroidLog.INFO, tag, message, null)

    fun i(tag: String, message: String, throwable: Throwable?): Int {
        return log(AndroidLog.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String): Int = log(AndroidLog.WARN, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable?): Int {
        return log(AndroidLog.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String): Int = log(AndroidLog.ERROR, tag, message, null)

    fun e(tag: String, message: String, throwable: Throwable?): Int {
        return log(AndroidLog.ERROR, tag, message, throwable)
    }

    fun priorityLabel(priority: Int): String {
        return when (priority) {
            AndroidLog.VERBOSE -> "V"
            AndroidLog.DEBUG -> "D"
            AndroidLog.INFO -> "I"
            AndroidLog.WARN -> "W"
            AndroidLog.ERROR -> "E"
            AndroidLog.ASSERT -> "A"
            else -> priority.toString()
        }
    }

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable?): Int {
        val platformResult = when (priority) {
            AndroidLog.DEBUG -> if (throwable == null) {
                AndroidLog.d(tag, message)
            } else {
                AndroidLog.d(tag, message, throwable)
            }

            AndroidLog.INFO -> if (throwable == null) {
                AndroidLog.i(tag, message)
            } else {
                AndroidLog.i(tag, message, throwable)
            }

            AndroidLog.WARN -> if (throwable == null) {
                AndroidLog.w(tag, message)
            } else {
                AndroidLog.w(tag, message, throwable)
            }

            AndroidLog.ERROR -> if (throwable == null) {
                AndroidLog.e(tag, message)
            } else {
                AndroidLog.e(tag, message, throwable)
            }

            AndroidLog.VERBOSE -> if (throwable == null) {
                AndroidLog.v(tag, message)
            } else {
                AndroidLog.v(tag, message, throwable)
            }

            else -> AndroidLog.println(priority, tag, message)
        }

        diagnosticLogStore?.append(
            priority = priority,
            tag = tag,
            message = message,
            throwable = throwable,
        )

        return platformResult
    }
}
