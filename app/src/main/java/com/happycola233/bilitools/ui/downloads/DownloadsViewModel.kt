package com.happycola233.bilitools.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.data.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    val groups = downloadRepository.groups

    init {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.ensureLoaded()
            downloadRepository.refreshOutputAvailability()
        }
    }

    fun refreshOutputAvailability() {
        downloadRepository.refreshOutputAvailability()
    }

    fun pause(id: Long) {
        downloadRepository.pause(id)
    }

    fun resume(id: Long) {
        downloadRepository.resume(id)
    }

    fun cancel(id: Long) {
        downloadRepository.cancel(id)
    }

    fun retry(id: Long) {
        downloadRepository.retry(id)
    }

    fun pauseGroup(id: Long) {
        downloadRepository.pauseGroup(id)
    }

    fun resumeGroup(id: Long) {
        downloadRepository.resumeGroup(id)
    }

    fun deleteGroup(id: Long, deleteFile: Boolean) {
        downloadRepository.deleteGroup(id, deleteFile)
    }

    fun deleteTask(id: Long, deleteFile: Boolean) {
        downloadRepository.deleteTask(id, deleteFile)
    }

    fun clearCompleted() {
        downloadRepository.clearCompletedGroups()
    }

    fun clearAll() {
        downloadRepository.clearAllGroups()
    }
}
