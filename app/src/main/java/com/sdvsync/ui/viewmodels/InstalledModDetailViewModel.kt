package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.ModMetadata
import com.sdvsync.mods.api.NexusModSource
import com.sdvsync.mods.api.SmapiUpdateChecker
import com.sdvsync.mods.models.InstalledMod
import com.sdvsync.mods.models.ModUpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstalledModDetailState(
    val mod: InstalledMod? = null,
    val isLoading: Boolean = true,
    val showRemoveDialog: Boolean = false,
    val removed: Boolean = false,
    val updateInfo: ModUpdateInfo? = null,
    val metadata: ModMetadata? = null,
    val error: String? = null,
    val isWorking: Boolean = false,
    val isCheckingUpdate: Boolean = false
)

class InstalledModDetailViewModel(
    private val fileManager: ModFileManager,
    private val dataStore: ModDataStore,
    private val updateChecker: SmapiUpdateChecker,
    private val nexusSource: NexusModSource,
    private val uniqueId: String
) : ViewModel() {

    companion object {
        private const val TAG = "InstalledModDetailVM"
    }

    private val _state = MutableStateFlow(InstalledModDetailState())
    val state: StateFlow<InstalledModDetailState> = _state.asStateFlow()

    init {
        loadMod()
    }

    private fun loadMod() = runOperation { reloadMod() }

    private suspend fun reloadMod() {
        val mod = fileManager.listInstalledMods().find { it.manifest.uniqueID.equals(uniqueId, true) }
        val metadata = dataStore.getModMetadata(uniqueId)
        val info = dataStore.getUpdateCache().entries.firstOrNull { it.key.equals(uniqueId, true) }?.value
        _state.update { it.copy(mod = mod, isLoading = false, metadata = metadata, updateInfo = info) }
    }

    private fun runOperation(action: suspend () -> Unit) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "Mod operation failed", e)
                _state.update { it.copy(error = e.message ?: "Mod operation failed", showRemoveDialog = false) }
            } finally {
                _state.update { it.copy(isWorking = false, isLoading = false) }
            }
        }
    }

    fun toggleMod(enable: Boolean) {
        val mod = _state.value.mod ?: return
        runOperation {
            val success = if (enable) {
                fileManager.enableMod(mod.folderName)
            } else {
                fileManager.disableMod(mod.folderName)
            }
            check(success) { "Could not change the mod’s enabled state" }
            reloadMod()
        }
    }

    fun showRemoveDialog() {
        _state.update { it.copy(showRemoveDialog = true) }
    }

    fun dismissRemoveDialog() {
        _state.update { it.copy(showRemoveDialog = false) }
    }

    fun confirmRemove() {
        val mod = _state.value.mod ?: return
        runOperation {
            check(fileManager.removeMod(mod.folderName)) { "Could not remove the mod" }
            try {
                dataStore.removeModMetadata(mod.manifest.uniqueID)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.w(TAG, "Mod removed but its cached metadata could not be cleared", e)
            }
            _state.update { it.copy(removed = true, showRemoveDialog = false) }
        }
    }

    fun checkForUpdate() {
        if (_state.value.isCheckingUpdate) return
        val mod = _state.value.mod ?: return
        _state.update { it.copy(isCheckingUpdate = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updates = updateChecker.checkForUpdates(listOf(mod))
                var info = updates[mod.manifest.uniqueID]

                // Fetch changelog from Nexus if source is a Nexus update key
                if (info != null && info.source.startsWith("Nexus:", ignoreCase = true)) {
                    val nexusModId = info.source.substringAfter(":").trim()
                    val latestVersion = info.latestVersion
                    try {
                        val files = nexusSource.getModFiles(nexusModId)
                        // Find file matching latest version, fall back to most recent primary file
                        val matchingFile = files.find {
                            it.modVersion == latestVersion || it.fileVersion == latestVersion
                        } ?: files.filter { it.isPrimary }.maxByOrNull { it.uploadedAt }
                        if (matchingFile?.changelogHtml != null) {
                            info = info.copy(changelogHtml = matchingFile.changelogHtml)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        AppLogger.w(TAG, "Failed to fetch Nexus changelog for mod $nexusModId", e)
                    }
                }

                _state.update { it.copy(updateInfo = info, isCheckingUpdate = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "Update check failed", e)
                _state.update { it.copy(isCheckingUpdate = false, error = e.message ?: "Could not check for updates") }
            }
        }
    }
}
