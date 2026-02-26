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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModSortOrder { NAME, AUTHOR, STATUS }
enum class ModFilter { ALL, ENABLED, DISABLED, HAS_UPDATE }

data class ModManagerState(
    val installedMods: List<InstalledMod> = emptyList(),
    val updates: Map<String, ModUpdateInfo> = emptyMap(),
    val isLoading: Boolean = true,
    val isCheckingUpdates: Boolean = false,
    val error: String? = null,
    val importMessage: String? = null,
    val sortOrder: ModSortOrder = ModSortOrder.NAME,
    val filter: ModFilter = ModFilter.ALL,
    val searchQuery: String = "",
    val displayedMods: List<InstalledMod> = emptyList()
)

class ModManagerViewModel(
    private val context: Context,
    private val fileManager: ModFileManager,
    private val dataStore: ModDataStore,
    private val updateChecker: SmapiUpdateChecker
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
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val mods = fileManager.listInstalledMods()
                val updates = dataStore.getUpdateCache()
                _state.update {
                    it.copy(installedMods = mods, updates = updates, isLoading = false)
                }
                updateDisplayedMods()

                // Auto-check for updates if stale
                val lastCheck = dataStore.getLastUpdateCheck()
                if (mods.isNotEmpty() && System.currentTimeMillis() - lastCheck > UPDATE_CHECK_INTERVAL_MS) {
                    checkForUpdates()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load mods", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load mods")
                }
            }
        }
    }

    fun checkForUpdates() {
        val mods = _state.value.installedMods
        if (mods.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCheckingUpdates = true) }
            try {
                // Only check enabled mods
                val enabledMods = mods.filter { it.enabled }
                val updates = updateChecker.checkForUpdates(enabledMods)

                // Cache the results
                dataStore.setUpdateCache(updates)

                _state.update { it.copy(updates = updates, isCheckingUpdates = false) }
                updateDisplayedMods()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Update check failed", e)
                _state.update { it.copy(isCheckingUpdates = false) }
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
            _state.update { it.copy(importMessage = null) }
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
                        _state.update { it.copy(importMessage = "Installed: $names") }
                        loadInstalledMods()
                    }
                    is InstallResult.Error -> {
                        _state.update { it.copy(importMessage = "Import failed: ${result.message}") }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Import failed", e)
                _state.update { it.copy(importMessage = "Import failed: ${e.message}") }
            }
        }
    }

    fun clearImportMessage() {
        _state.update { it.copy(importMessage = null) }
    }

    fun setFilter(filter: ModFilter) {
        _state.update { it.copy(filter = filter) }
        updateDisplayedMods()
    }

    fun setSortOrder(order: ModSortOrder) {
        _state.update { it.copy(sortOrder = order) }
        updateDisplayedMods()
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        updateDisplayedMods()
    }

    private fun updateDisplayedMods() {
        _state.update { state ->
            var mods = state.installedMods

            // Filter
            mods = when (state.filter) {
                ModFilter.ALL -> mods
                ModFilter.ENABLED -> mods.filter { it.enabled }
                ModFilter.DISABLED -> mods.filter { !it.enabled }
                ModFilter.HAS_UPDATE -> mods.filter { state.updates.containsKey(it.manifest.uniqueID) }
            }

            // Search
            if (state.searchQuery.isNotBlank()) {
                val query = state.searchQuery.lowercase()
                mods = mods.filter {
                    it.manifest.name.lowercase().contains(query) ||
                        it.manifest.author.lowercase().contains(query) ||
                        it.manifest.uniqueID.lowercase().contains(query)
                }
            }

            // Sort
            mods = when (state.sortOrder) {
                ModSortOrder.NAME -> mods.sortedBy { it.manifest.name.lowercase() }
                ModSortOrder.AUTHOR -> mods.sortedBy { it.manifest.author.lowercase() }
                ModSortOrder.STATUS -> mods.sortedWith(
                    compareByDescending<InstalledMod> { it.enabled }
                        .thenBy { it.manifest.name.lowercase() }
                )
            }

            state.copy(displayedMods = mods)
        }
    }
}
