package com.happycola233.bilitools.core

import com.happycola233.bilitools.data.SettingsRepository
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

fun createHttpDiagnosticLoggingInterceptor(
    tag: String,
    settingsRepository: SettingsRepository,
): Interceptor {
    return Interceptor { chain ->
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            AppLog.d(tag, "[http] $message")
        }.apply {
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("Authorization")
            level = if (settingsRepository.currentSettings().issueReportDetailedLoggingEnabled) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }
        loggingInterceptor.intercept(chain)
    }
}
