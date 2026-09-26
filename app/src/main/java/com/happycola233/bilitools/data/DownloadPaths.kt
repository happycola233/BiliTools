package com.happycola233.bilitools.data

/** 用户模板、历史记录和媒体库都属于存储边界，不能把点段当成普通目录名。 */
internal object DownloadPaths {
    fun normalize(path: String): String? {
        val value = path.replace('\\', '/').trim().trimEnd('/')
        if (value.startsWith('/') || value.any { it.code < 32 }) return null
        val segments = value.split('/')
        if (segments.firstOrNull() != "Download" ||
            segments.any { it.isBlank() || it != it.trim() || it == "." || it == ".." }
        ) return null
        return segments.joinToString("/")
    }

    fun contains(root: String, directory: String): Boolean {
        val safeRoot = normalize(root) ?: return false
        val safeDirectory = normalize(directory) ?: return false
        return safeDirectory == safeRoot || safeDirectory.startsWith("$safeRoot/")
    }

    fun isFileName(name: String): Boolean = name.isNotBlank() &&
        name != "." && name != ".." && name.none { it == '/' || it == '\\' || it.code < 32 }
}
