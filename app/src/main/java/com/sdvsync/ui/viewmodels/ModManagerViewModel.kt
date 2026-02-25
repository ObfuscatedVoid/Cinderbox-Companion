package com.sdvsync.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.api.SmapiUpdateChecker
import com.sdvsync.mods.models.InstallResult
import com.sdvsync.mods.models.InstalledMod
import com.sdvsync.mods.models.ModUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ModManagerState(
    val installedMods: List<InstalledMod> = emptyList(),
    val updates: Map<String, ModUpdateInfo> = emptyMap(),
    val isLoading: Boolean = true,
    val isCheckingUpdates: Boolean = false,
    val error: String? = null,
    val importMessage: String? = null,
)

class ModManagerViewModel(
    private val context: Context,
    private val fileManager: ModFileManager,
    private val dataStore: ModDataStore,
    private val updateChecker: SmapiUpdateChecker,
) : ViewModel() {

    companion object {
        private const val TAG = "ModManagerVM"
        private const val UPDATE_CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    private val _state = MutableStateFlow(ModManagerState())
    val state: StateFlow<ModManagerState> = _state.asStateFlow()

    init {
        loadInstalledMods()
    }

    fun loadInstalledMods() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val mods = fileManager.listInstalledMods()
                val updates = dataStore.getUpdateCache()
                _state.value = _state.value.copy(
                    installedMods = mods,
                    updates = updates,
                    isLoading = false,
                )

                // Auto-check for updates if stale
                val lastCheck = dataStore.getLastUpdateCheck()
                if (mods.isNotEmpty() && System.currentTimeMillis() - lastCheck > UPDATE_CHECK_INTERVAL_MS) {
                    checkForUpdates()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load mods", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load mods",
                )
            }
        }
    }

    fun checkForUpdates() {
        val mods = _state.value.installedMods
        if (mods.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isCheckingUpdates = true)
            try {
                // Only check enabled mods
                val enabledMods = mods.filter { it.enabled }
                val updates = updateChecker.checkForUpdates(enabledMods)

                // Cache the results
                dataStore.setUpdateCache(updates)

                _state.value = _state.value.copy(
                    updates = updates,
                    isCheckingUpdates = false,
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Update check failed", e)
                _state.value = _state.value.copy(isCheckingUpdates = false)
            }
        }
    }

    fun toggleMod(folderName: String, enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = if (enable) {
                fileManager.enableMod(folderName)
            } else {
                fileManager.disableMod(folderName)
            }
            if (success) {
                loadInstalledMods()
            }
        }
    }

    fun removeMod(folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val mod = _state.value.installedMods.find { it.folderName == folderName }
            if (fileManager.removeMod(folderName)) {
                mod?.let { dataStore.removeModMetadata(it.manifest.uniqueID) }
                loadInstalledMods()
            }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(importMessage = null)
            try {
                val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot read file")

                val result = fileManager.installFromZip(tempFile)
                tempFile.delete()

                when (result) {
                    is InstallResult.Success -> {
                        val names = result.mods.joinToString { it.manifest.name }
                        _state.value = _state.value.copy(importMessage = "Installed: $names")
                        loadInstalledMods()
                    }
                    is InstallResult.Error -> {
                        _state.value = _state.value.copy(importMessage = "Import failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Import failed", e)
                _state.value = _state.value.copy(importMessage = "Import failed: ${e.message}")
            }
        }
    }

    fun clearImportMessage() {
        _state.value = _state.value.copy(importMessage = null)
    }
}
