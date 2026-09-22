package com.sdvsync.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.R
import com.sdvsync.fileaccess.FileAccessDetector
import com.sdvsync.fileaccess.SAFFileAccess
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

data class ModMismatchInfo(val missingMods: List<AssociatedMod>, val extraMods: List<String>, val lastSyncTime: Long)

enum class SyncOperation { PULL, PUSH }

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
    val exportError: String? = null,
    val lastOperation: SyncOperation? = null,
    val lastSaveFolderName: String? = null,
    val showStoragePermissionPrompt: Boolean = false
)

class SyncDetailViewModel(
    context: Context,
    private val syncEngine: SyncEngine,
    private val historyStore: SyncHistoryStore,
    private val saveFileManager: SaveFileManager,
    private val saveValidator: SaveValidator,
    private val modFileManager: ModFileManager,
    private val modDataStore: ModDataStore,
    private val bundleManager: SaveBundleManager,
    private val fileAccessDetector: FileAccessDetector
) : AndroidViewModel(context.applicationContext as Application),
    KoinComponent {

    private val context get() = getApplication<Application>()

    companion object {
        private const val TAG = "SyncDetailVM"
    }

    private val _state = MutableStateFlow(SyncDetailState(isStagingMode = saveFileManager.isStaging))
    val state: StateFlow<SyncDetailState> = _state.asStateFlow()

    fun pullSave(saveFolderName: String, force: Boolean = false, engine: SyncEngine = syncEngine) =
        startSync(saveFolderName, SyncOperation.PULL, force, engine)

    fun pushSave(saveFolderName: String, force: Boolean = false, engine: SyncEngine = syncEngine) =
        startSync(saveFolderName, SyncOperation.PUSH, force, engine)

    private fun startSync(folder: String, operation: SyncOperation, force: Boolean, engine: SyncEngine) {
        if (_state.value.isSyncing) return
        if (fileAccessDetector.needsStoragePermission()) {
            _state.update {
                it.copy(lastOperation = operation, lastSaveFolderName = folder, showStoragePermissionPrompt = true)
            }
            return
        }
        _state.update {
            it.copy(
                isSyncing = true,
                result = null,
                progressMessage = "",
                isStagingMode = saveFileManager.isStaging,
                lastOperation = operation,
                lastSaveFolderName = folder
            )
        }
        viewModelScope.launch {
            try {
                val progress: (String) -> Unit = { message -> _state.update { it.copy(progressMessage = message) } }
                val result = when (operation) {
                    SyncOperation.PULL -> engine.pullSave(folder, force, progress)
                    SyncOperation.PUSH -> engine.pushSave(folder, force, progress)
                }
                if (result is SyncResult.Success) captureModAssociation(folder)
                val message = when (result) {
                    is SyncResult.Success -> result.message
                    is SyncResult.Error -> result.message
                    is SyncResult.NeedsConflictResolution -> context.getString(R.string.sync_conflict_detected)
                }
                try {
                    historyStore.addEntry(folder, operation.name.lowercase(), result is SyncResult.Success, message)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    AppLogger.e(TAG, "Could not record sync history", e)
                }
                _state.update { it.copy(result = result) }
            } finally {
                _state.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun retrySync() {
        if (_state.value.isSyncing) return
        val folder = _state.value.lastSaveFolderName ?: return
        when (_state.value.lastOperation) {
            SyncOperation.PULL -> pullSave(folder)
            SyncOperation.PUSH -> pushSave(folder)
            null -> {}
        }
    }

    fun forceSync() {
        if (_state.value.isSyncing) return
        val folder = _state.value.lastSaveFolderName ?: return
        when (_state.value.lastOperation) {
            SyncOperation.PULL -> pullSave(folder, force = true)
            SyncOperation.PUSH -> pushSave(folder, force = true)
            null -> {}
        }
    }

    fun switchToCinderbox() {
        if (_state.value.isSyncing) return
        if (fileAccessDetector.needsStoragePermission(cinderboxMode = true)) {
            _state.update { it.copy(showStoragePermissionPrompt = true) }
            return
        }
        fileAccessDetector.setCinderboxMode(true)
        AppLogger.d(TAG, "Switched to Cinderbox mode, retrying with fresh SyncEngine")
        retrySyncWithFreshEngine()
    }

    fun onStoragePermissionResult() {
        _state.update { it.copy(showStoragePermissionPrompt = false) }
        if (!fileAccessDetector.needsStoragePermission(cinderboxMode = true)) {
            fileAccessDetector.setCinderboxMode(true)
            AppLogger.d(TAG, "Storage permission granted, retrying with fresh SyncEngine")
            retrySyncWithFreshEngine()
        }
    }

    fun dismissStoragePermissionPrompt() {
        _state.update { it.copy(showStoragePermissionPrompt = false) }
    }

    fun onSafDirectorySelected(uri: Uri) {
        if (_state.value.isSyncing) return
        SAFFileAccess.persistUri(context, uri)
        fileAccessDetector.setPreferredStrategy("SAF")
        AppLogger.d(TAG, "SAF directory selected, retrying with fresh SyncEngine")
        retrySyncWithFreshEngine()
    }

    private fun retrySyncWithFreshEngine() {
        val folder = _state.value.lastSaveFolderName ?: return
        val freshEngine: SyncEngine = get()
        when (_state.value.lastOperation) {
            SyncOperation.PULL -> pullSave(folder, engine = freshEngine)
            SyncOperation.PUSH -> pushSave(folder, engine = freshEngine)
            null -> {}
        }
    }

    fun checkSaveHealth(saveFolderName: String) {
        if (_state.value.isCheckingHealth) return
        _state.update { it.copy(isCheckingHealth = true, healthCheck = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = saveFileManager.readLocalSave(saveFolderName)
                val mainSaveData = files[saveFolderName]
                val saveGameInfoData = files["SaveGameInfo"]
                val result = saveValidator.deepValidateSaveData(mainSaveData, saveGameInfoData)
                _state.update { it.copy(healthCheck = result, isCheckingHealth = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update {
                    it.copy(
                        healthCheck = ValidationResult(
                            valid = false,
                            errors = listOf("Health check failed: ${e.message}")
                        ),
                        isCheckingHealth = false
                    )
                }
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
                    _state.update {
                        it.copy(
                            modMismatch = ModMismatchInfo(
                                missingMods = missingMods,
                                extraMods = extraMods,
                                lastSyncTime = association.capturedAt
                            )
                        )
                    }
                } else {
                    _state.update { it.copy(modMismatch = null) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
                _state.update { it.copy(modMismatch = null) }
                AppLogger.d(TAG, "Captured mod association for $saveFolderName: ${currentMods.size} mods")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.w(TAG, "Failed to capture mod association", e)
            }
        }
    }

    fun updateModAssociation(saveFolderName: String) {
        captureModAssociation(saveFolderName)
    }

    fun exportSave(saveFolderName: String) {
        if (_state.value.isExporting) return
        _state.update { it.copy(isExporting = true, exportError = null, exportFile = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = bundleManager.exportBundle(saveFolderName)
                _state.update { it.copy(isExporting = false, exportFile = file) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "Export failed", e)
                _state.update {
                    it.copy(
                        isExporting = false,
                        exportError = e.message ?: "Export failed"
                    )
                }
            }
        }
    }

    fun clearExportFile() {
        _state.update { it.copy(exportFile = null) }
    }

    fun clearResult() {
        _state.update { it.copy(result = null) }
    }
}
