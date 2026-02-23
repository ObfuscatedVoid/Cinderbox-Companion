package com.sdvsync.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.R
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.sync.SyncEngine
import com.sdvsync.sync.SyncHistoryStore
import com.sdvsync.sync.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SyncDetailState(
    val isSyncing: Boolean = false,
    val progressMessage: String = "",
    val result: SyncResult? = null,
    val isStagingMode: Boolean = false,
)

class SyncDetailViewModel(
    private val context: Context,
    private val syncEngine: SyncEngine,
    private val historyStore: SyncHistoryStore,
    private val saveFileManager: SaveFileManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncDetailState(isStagingMode = saveFileManager.isStaging))
    val state: StateFlow<SyncDetailState> = _state.asStateFlow()

    fun pullSave(saveFolderName: String, force: Boolean = false) {
        viewModelScope.launch {
            _state.value = SyncDetailState(isSyncing = true, isStagingMode = saveFileManager.isStaging)

            val result = syncEngine.pullSave(
                saveFolderName = saveFolderName,
                force = force,
                onProgress = { msg ->
                    _state.value = _state.value.copy(progressMessage = msg)
                },
            )

            _state.value = SyncDetailState(isSyncing = false, result = result, isStagingMode = saveFileManager.isStaging)

            // Log to history
            val success = result is SyncResult.Success
            val message = when (result) {
                is SyncResult.Success -> result.message
                is SyncResult.Error -> result.message
                is SyncResult.NeedsConflictResolution -> context.getString(R.string.sync_conflict_detected)
            }
            historyStore.addEntry(saveFolderName, "pull", success, message)
        }
    }

    fun pushSave(saveFolderName: String, force: Boolean = false) {
        viewModelScope.launch {
            _state.value = SyncDetailState(isSyncing = true, isStagingMode = saveFileManager.isStaging)

            val result = syncEngine.pushSave(
                saveFolderName = saveFolderName,
                force = force,
                onProgress = { msg ->
                    _state.value = _state.value.copy(progressMessage = msg)
                },
            )

            _state.value = SyncDetailState(isSyncing = false, result = result, isStagingMode = saveFileManager.isStaging)

            val success = result is SyncResult.Success
            val message = when (result) {
                is SyncResult.Success -> result.message
                is SyncResult.Error -> result.message
                is SyncResult.NeedsConflictResolution -> context.getString(R.string.sync_conflict_detected)
            }
            historyStore.addEntry(saveFolderName, "push", success, message)
        }
    }

    fun clearResult() {
        _state.value = _state.value.copy(result = null)
    }
}
