package com.happycola233.bilitools.data

/**
 * 一次下载操作里已经确定下来的分组文件夹。
 *
 * 「重名时直接覆盖」关闭时，新建分组会避开已有的同名文件夹并追加序号；但同一批里本就该放在一起的
 * 条目（番剧整季、多 P 稿件、项目文件夹留空时的整批）请求的是同一个文件夹，应当共用第一次确定的结果，
 * 而不是各自散进「名称(1)」「名称(2)」。
 */
class DownloadFolderBatch {
    private val resolvedPaths = mutableMapOf<String, String>()

    /** 这一批里是否已经有条目确定过这个文件夹。 */
    fun hasResolved(requestedPath: String): Boolean = requestedPath in resolvedPaths

    internal fun resolve(requestedPath: String, resolveNew: (String) -> String): String =
        resolvedPaths.getOrPut(requestedPath) { resolveNew(requestedPath) }
}
