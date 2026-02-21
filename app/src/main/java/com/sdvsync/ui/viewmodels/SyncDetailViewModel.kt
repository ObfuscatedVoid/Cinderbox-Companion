package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

class SyncDetailViewModel(
    private val syncEngine: SyncEngine,
    private val historyStore: SyncHistoryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncDetailState())
    val state: StateFlow<SyncDetailState> = _state.asStateFlow()

    fun pullSave(saveFolderName: String, force: Boolean = false) {
        viewModelScope.launch {
            _state.value = SyncDetailState(isSyncing = true)

            val result = syncEngine.pullSave(
                saveFolderName = saveFolderName,
                force = force,
                onProgress = { msg ->
                    _state.value = _state.value.copy(progressMessage = msg)
                },
            )

            _state.value = SyncDetailState(isSyncing = false, result = result)

            // Log to history
            val success = result is SyncResult.Success
            val message = when (result) {
                is SyncResult.Success -> result.message
                is SyncResult.Error -> result.message
                is SyncResult.NeedsConflictResolution -> "Conflict detected"
            }
            historyStore.addEntry(saveFolderName, "pull", success, message)
        }
    }

    fun pushSave(saveFolderName: String, force: Boolean = false) {
        viewModelScope.launch {
            _state.value = SyncDetailState(isSyncing = true)

            val result = syncEngine.pushSave(
                saveFolderName = saveFolderName,
                force = force,
                onProgress = { msg ->
                    _state.value = _state.value.copy(progressMessage = msg)
                },
            )

            _state.value = SyncDetailState(isSyncing = false, result = result)

            val success = result is SyncResult.Success
            val message = when (result) {
                is SyncResult.Success -> result.message
                is SyncResult.Error -> result.message
                is SyncResult.NeedsConflictResolution -> "Conflict detected"
            }
            historyStore.addEntry(saveFolderName, "push", success, message)
        }
    }

    fun clearResult() {
        _state.value = _state.value.copy(result = null)
    }
}
