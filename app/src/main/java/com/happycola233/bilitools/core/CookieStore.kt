package com.happycola233.bilitools.core

import android.content.Context
import okhttp3.Headers
import java.util.concurrent.ConcurrentHashMap

class CookieStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cookies = ConcurrentHashMap<String, String>()
    private val loadLock = Any()
    @Volatile
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            val cached = prefs.getString(KEY_COOKIE_RAW, "").orEmpty()
            if (cached.isNotBlank()) {
                cached.split(";").map { it.trim() }.forEach { part ->
                    val pair = part.split("=", limit = 2)
                    if (pair.size == 2) {
                        cookies[pair[0]] = pair[1]
                    }
                }
            }
            loaded = true
        }
    }

    fun updateFromHeaders(headers: Headers) {
        ensureLoaded()
        val setCookies = headers.values("Set-Cookie")
        if (setCookies.isEmpty()) return
        for (header in setCookies) {
            val nameValue = header.substringBefore(";").trim()
            val pair = nameValue.split("=", limit = 2)
            if (pair.size == 2) {
                cookies[pair[0]] = pair[1]
            }
        }
        persist()
    }

    fun cookieHeader(): String {
        ensureLoaded()
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun isLoggedIn(): Boolean {
        ensureLoaded()
        return cookies["SESSDATA"]?.isNotBlank() == true
    }

    fun clear() {
        ensureLoaded()
        cookies.clear()
        prefs.edit().remove(KEY_COOKIE_RAW).apply()
    }

    private fun persist() {
        prefs.edit().putString(KEY_COOKIE_RAW, cookieHeader()).apply()
    }

    companion object {
        private const val PREFS_NAME = "bili_cookies"
        private const val KEY_COOKIE_RAW = "cookie_raw"
    }
}
