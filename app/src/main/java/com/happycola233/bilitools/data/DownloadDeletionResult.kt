package com.happycola233.bilitools.data

data class DownloadDeletionResult(
    val removedTasks: Int = 0,
    val blockedFiles: Int = 0,
    val failedFiles: Int = 0,
    val sharedFiles: Int = 0,
    val deleteFiles: Boolean = false,
)
