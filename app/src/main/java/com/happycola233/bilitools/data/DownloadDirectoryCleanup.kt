package com.happycola233.bilitools.data

import android.os.Environment
import android.system.Os
import java.io.File

/** 仅沿任务原目录向上清理空目录，不递归遍历或删除目录内容。 */
internal class DownloadDirectoryCleanup(
    private val externalRoot: File = Environment.getExternalStorageDirectory(),
) {
    fun removeEmptyParents(directory: String, root: String, protectedDirectories: Collection<String>) {
        val safeRoot = DownloadPaths.normalize(root) ?: return
        var path = DownloadPaths.normalize(directory) ?: return
        if (!DownloadPaths.contains(safeRoot, path)) return
        val protected = protectedDirectories.mapNotNull(DownloadPaths::normalize).toSet() + safeRoot
        runCatching {
            val base = externalRoot.canonicalFile.toPath()
            while (path !in protected && DownloadPaths.contains(safeRoot, path)) {
                val target = File(externalRoot, path)
                if (target.canonicalFile.toPath() != base.resolve(path) || !target.isDirectory) return
                // 保留末尾 /，让 Linux 系统调用只接受目录；路径被替换成文件时会失败。
                // remove 由系统检查目录为空，不会递归删除内容。
                // 非空、权限不足或已不存在时停止，不扩大清理范围。
                if (runCatching { Os.remove(target.absolutePath + "/") }.isFailure) return
                path = path.substringBeforeLast('/')
            }
        }
    }
}
