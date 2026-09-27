package com.happycola233.bilitools.ui.downloads

import com.happycola233.bilitools.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsConfirmationPreferencesTest {
    @Test fun rememberedConfirmationOnlySkipsTheChosenActionAcrossAllEntryPoints() {
        val context = RuntimeEnvironment.getApplication()
        val repository = SettingsRepository(context)
        for (deleteFiles in listOf(false, true)) {
            repository.resetDismissedConfirmations()
            repository.skipDownloadDeletionConfirmation(deleteFiles)
            val restored = SettingsRepository(context).currentSettings()
            assertEquals(deleteFiles, restored.confirmDownloadRecordRemoval)
            assertEquals(!deleteFiles, restored.confirmDownloadedFileDeletion)

            for (request in listOf(
                DownloadsDialogState.DeleteTask(1, deleteFiles),
                DownloadsDialogState.DeleteGroup(2, deleteFiles),
                DownloadsDialogState.BatchDelete(setOf(2, 3), deleteFiles),
            )) {
                val state = DownloadsRouteUiState()
                val deleted = mutableListOf<DownloadsDialogState>()
                state.requestDelete(request, restored) { deleted += it }
                assertNull(state.dialogState)
                assertEquals(listOf(request), deleted)
            }
            val otherAction = DownloadsDialogState.DeleteGroup(2, !deleteFiles)
            val state = DownloadsRouteUiState()
            state.requestDelete(otherAction, restored) { error("另一种操作仍应确认") }
            assertEquals(otherAction, state.dialogState)
        }
    }

    @Test fun resetRestoresBothConfirmationsAfterRestartAndKeepsOtherSettings() {
        val context = RuntimeEnvironment.getApplication()
        val repository = SettingsRepository(context)
        repository.setConfirmCellularDownload(false)
        repository.skipDownloadDeletionConfirmation(false)
        repository.skipDownloadDeletionConfirmation(true)
        repository.resetDismissedConfirmations()

        val restored = SettingsRepository(context).currentSettings()
        assertTrue(restored.confirmDownloadRecordRemoval)
        assertTrue(restored.confirmDownloadedFileDeletion)
        assertFalse(restored.confirmCellularDownload)
        for (deleteFiles in listOf(false, true)) {
            val request = DownloadsDialogState.DeleteGroup(1, deleteFiles)
            val state = DownloadsRouteUiState(swipedGroupId = 1)
            state.requestDelete(request, restored) { error("重置后应恢复确认") }
            assertEquals(request, state.dialogState)
            state.dismissDialog()
            assertNull(state.dialogState)
            assertNull(state.swipedGroupId)
        }
    }
}
