package com.sdvsync.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.R
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveValidator
import com.sdvsync.saves.ValidationResult
import com.sdvsync.sync.SyncEngine
import com.sdvsync.sync.SyncHistoryStore
import com.sdvsync.sync.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SyncDetailState(
    val isSyncing: Boolean = false,
    val progressMessage: String = "",
    val result: SyncResult? = null,
    val isStagingMode: Boolean = false,
    val healthCheck: ValidationResult? = null,
    val isCheckingHealth: Boolean = false
)

class SyncDetailViewModel(
    private val context: Context,
    private val syncEngine: SyncEngine,
    private val historyStore: SyncHistoryStore,
    private val saveFileManager: SaveFileManager,
    private val saveValidator: SaveValidator
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
                }
            )

            _state.value =
                SyncDetailState(isSyncing = false, result = result, isStagingMode = saveFileManager.isStaging)

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
                }
            )

            _state.value =
                SyncDetailState(isSyncing = false, result = result, isStagingMode = saveFileManager.isStaging)

            val success = result is SyncResult.Success
            val message = when (result) {
                is SyncResult.Success -> result.message
                is SyncResult.Error -> result.message
                is SyncResult.NeedsConflictResolution -> context.getString(R.string.sync_conflict_detected)
            }
            historyStore.addEntry(saveFolderName, "push", success, message)
        }
    }

    fun checkSaveHealth(saveFolderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isCheckingHealth = true, healthCheck = null)
            try {
                val files = saveFileManager.readLocalSave(saveFolderName)
                val mainSaveData = files[saveFolderName]
                val saveGameInfoData = files["SaveGameInfo"]
                val result = saveValidator.deepValidateSaveData(mainSaveData, saveGameInfoData)
                _state.value = _state.value.copy(healthCheck = result, isCheckingHealth = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    healthCheck = ValidationResult(
                        valid = false,
                        errors = listOf("Health check failed: ${e.message}")
                    ),
                    isCheckingHealth = false
                )
            }
        }
    }

    fun clearResult() {
        _state.value = _state.value.copy(result = null)
    }
}
