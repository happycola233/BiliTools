package com.happycola233.bilitools.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.data.DownloadRepository
import com.happycola233.bilitools.data.DownloadDeletionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    val groups = downloadRepository.groups
    val historyReadFailed = downloadRepository.historyReadFailed
    private val deletionResults = Channel<DownloadDeletionResult>(Channel.BUFFERED)
    val deletionEvents = deletionResults.receiveAsFlow()

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

    fun retry(id: Long) {
        downloadRepository.retry(id)
    }

    fun pauseGroup(id: Long) {
        downloadRepository.pauseGroup(id)
    }

    fun resumeGroup(id: Long) {
        downloadRepository.resumeGroup(id)
    }

    fun pauseAll() {
        downloadRepository.pauseAll()
    }

    fun startAll() {
        downloadRepository.startAll()
    }

    fun deleteGroup(id: Long, deleteFile: Boolean) {
        delete { downloadRepository.deleteGroup(id, deleteFile) }
    }

    fun deleteTask(id: Long, deleteFile: Boolean) {
        delete { downloadRepository.deleteTask(id, deleteFile) }
    }

    fun deleteGroups(ids: Collection<Long>, deleteFile: Boolean) {
        delete { downloadRepository.deleteGroups(ids, deleteFile) }
    }

    fun clearCompleted() {
        delete { downloadRepository.clearCompletedGroups() }
    }

    fun clearAll() {
        delete { downloadRepository.clearAllGroups() }
    }

    private fun delete(block: suspend () -> DownloadDeletionResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                DownloadDeletionResult(failedFiles = 1)
            }
            deletionResults.send(result)
        }
    }
}
