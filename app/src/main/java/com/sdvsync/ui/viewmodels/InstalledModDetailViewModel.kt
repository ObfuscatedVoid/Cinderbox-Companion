package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.ModMetadata
import com.sdvsync.mods.api.SmapiUpdateChecker
import com.sdvsync.mods.models.InstalledMod
import com.sdvsync.mods.models.ModUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InstalledModDetailState(
    val mod: InstalledMod? = null,
    val isLoading: Boolean = true,
    val showRemoveDialog: Boolean = false,
    val removed: Boolean = false,
    val updateInfo: ModUpdateInfo? = null,
    val metadata: ModMetadata? = null,
    val isCheckingUpdate: Boolean = false,
)

class InstalledModDetailViewModel(
    private val fileManager: ModFileManager,
    private val dataStore: ModDataStore,
    private val updateChecker: SmapiUpdateChecker,
    private val uniqueId: String,
) : ViewModel() {

    companion object {
        private const val TAG = "InstalledModDetailVM"
    }

    private val _state = MutableStateFlow(InstalledModDetailState())
    val state: StateFlow<InstalledModDetailState> = _state.asStateFlow()

    init {
        loadMod()
    }

    private fun loadMod() {
        viewModelScope.launch(Dispatchers.IO) {
            val mods = fileManager.listInstalledMods()
            val mod = mods.find { it.manifest.uniqueID.equals(uniqueId, ignoreCase = true) }
            val metadata = dataStore.getModMetadata(uniqueId)
            val updateCache = dataStore.getUpdateCache()
            val updateInfo = updateCache[uniqueId]
            _state.value = _state.value.copy(
                mod = mod,
                isLoading = false,
                metadata = metadata,
                updateInfo = updateInfo,
            )
        }
    }

    fun toggleMod(enable: Boolean) {
        val mod = _state.value.mod ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val success = if (enable) {
                fileManager.enableMod(mod.folderName)
            } else {
                fileManager.disableMod(mod.folderName)
            }
            if (success) loadMod()
        }
    }

    fun showRemoveDialog() {
        _state.value = _state.value.copy(showRemoveDialog = true)
    }

    fun dismissRemoveDialog() {
        _state.value = _state.value.copy(showRemoveDialog = false)
    }

    fun confirmRemove() {
        val mod = _state.value.mod ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (fileManager.removeMod(mod.folderName)) {
                dataStore.removeModMetadata(mod.manifest.uniqueID)
                _state.value = _state.value.copy(removed = true, showRemoveDialog = false)
            }
        }
    }

    fun checkForUpdate() {
        val mod = _state.value.mod ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isCheckingUpdate = true)
            try {
                val updates = updateChecker.checkForUpdates(listOf(mod))
                val info = updates[mod.manifest.uniqueID]
                _state.value = _state.value.copy(
                    updateInfo = info,
                    isCheckingUpdate = false,
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Update check failed", e)
                _state.value = _state.value.copy(isCheckingUpdate = false)
            }
        }
    }
}
