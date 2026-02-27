package com.sdvsync.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.models.AssociatedMod
import com.sdvsync.mods.models.SaveModAssociation
import com.sdvsync.saves.SaveBundleManager
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveValidator
import com.sdvsync.saves.ValidationResult
import com.sdvsync.sync.SyncEngine
import com.sdvsync.sync.SyncHistoryStore
import com.sdvsync.sync.SyncResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModMismatchInfo(val missingMods: List<AssociatedMod>, val extraMods: List<String>, val lastSyncTime: Long)

data class SyncDetailState(
    val isSyncing: Boolean = false,
    val progressMessage: String = "",
    val result: SyncResult? = null,
    val isStagingMode: Boolean = false,
    val healthCheck: ValidationResult? = null,
    val isCheckingHealth: Boolean = false,
    val modMismatch: ModMismatchInfo? = null,
    val isExporting: Boolean = false,
    val exportFile: File? = null,
    val exportError: String? = null
)

class SyncDetailViewModel(
    private val context: Context,
    private val syncEngine: SyncEngine,
    private val historyStore: SyncHistoryStore,
    private val saveFileManager: SaveFileManager,
    private val saveValidator: SaveValidator,
    private val modFileManager: ModFileManager,
    private val modDataStore: ModDataStore,
    private val bundleManager: SaveBundleManager
) : ViewModel() {

    companion object {
        private const val TAG = "SyncDetailVM"
    }

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

            // Capture mod association on success
            if (result is SyncResult.Success) {
                captureModAssociation(saveFolderName)
            }

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

            // Capture mod association on success
            if (result is SyncResult.Success) {
                captureModAssociation(saveFolderName)
            }

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

    fun loadModAssociation(saveFolderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val association = modDataStore.getSaveModAssociation(saveFolderName) ?: return@launch
                val currentMods = modFileManager.listInstalledMods()
                    .filter { it.enabled }
                val currentModIds = currentMods.map { it.manifest.uniqueID.lowercase() }.toSet()
                val associatedModIds = association.enabledMods.map { it.uniqueID.lowercase() }.toSet()

                val missingMods = association.enabledMods.filter {
                    it.uniqueID.lowercase() !in currentModIds
                }
                val extraMods = currentMods.filter {
                    it.manifest.uniqueID.lowercase() !in associatedModIds
                }.map { it.manifest.name }

                if (missingMods.isNotEmpty() || extraMods.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        modMismatch = ModMismatchInfo(
                            missingMods = missingMods,
                            extraMods = extraMods,
                            lastSyncTime = association.capturedAt
                        )
                    )
                } else {
                    _state.value = _state.value.copy(modMismatch = null)
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to load mod association", e)
            }
        }
    }

    private fun captureModAssociation(saveFolderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentMods = modFileManager.listInstalledMods()
                    .filter { it.enabled }
                    .map {
                        AssociatedMod(
                            uniqueID = it.manifest.uniqueID,
                            name = it.manifest.name,
                            version = it.manifest.version
                        )
                    }.toSet()
                modDataStore.setSaveModAssociation(
                    SaveModAssociation(
                        saveFolderName = saveFolderName,
                        enabledMods = currentMods
                    )
                )
                _state.value = _state.value.copy(modMismatch = null)
                AppLogger.d(TAG, "Captured mod association for $saveFolderName: ${currentMods.size} mods")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to capture mod association", e)
            }
        }
    }

    fun updateModAssociation(saveFolderName: String) {
        captureModAssociation(saveFolderName)
    }

    fun exportSave(saveFolderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isExporting = true, exportError = null, exportFile = null)
            try {
                val file = bundleManager.exportBundle(saveFolderName)
                _state.value = _state.value.copy(isExporting = false, exportFile = file)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Export failed", e)
                _state.value = _state.value.copy(
                    isExporting = false,
                    exportError = e.message ?: "Export failed"
                )
            }
        }
    }

    fun clearExportFile() {
        _state.value = _state.value.copy(exportFile = null)
    }

    fun clearResult() {
        _state.value = _state.value.copy(result = null)
    }
}
