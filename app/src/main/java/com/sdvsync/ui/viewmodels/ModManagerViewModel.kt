package com.sdvsync.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.cinderbox.CinderboxLayoutEvents
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.api.SmapiUpdateChecker
import com.sdvsync.mods.models.InstallResult
import com.sdvsync.mods.models.InstalledMod
import com.sdvsync.mods.models.ModProfile
import com.sdvsync.mods.models.ModUpdateInfo
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val isRefreshing: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val error: String? = null,
    val importMessage: String? = null,
    val sortOrder: ModSortOrder = ModSortOrder.NAME,
    val filter: ModFilter = ModFilter.ALL,
    val searchQuery: String = "",
    val displayedMods: List<InstalledMod> = emptyList(),
    val profiles: List<ModProfile> = emptyList(),
    val activeProfileName: String? = null,
    val isApplyingProfile: Boolean = false,
    val profileMessage: String? = null
)

class ModManagerViewModel(
    context: Context,
    private val fileManager: ModFileManager,
    private val dataStore: ModDataStore,
    private val updateChecker: SmapiUpdateChecker
) : AndroidViewModel(context.applicationContext as Application) {

    private val context get() = getApplication<Application>()

    companion object {
        private const val TAG = "ModManagerVM"
        private const val UPDATE_CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    private val _state = MutableStateFlow(ModManagerState())
    val state: StateFlow<ModManagerState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadInstalledMods()
        viewModelScope.launch {
            CinderboxLayoutEvents.changed.collect { loadInstalledMods() }
        }
    }

    @Synchronized
    fun loadInstalledMods(isUserRefresh: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isLoading = it.installedMods.isEmpty() && !isUserRefresh,
                    isRefreshing = isUserRefresh,
                    error = null
                )
            }
            try {
                val mods = fileManager.listInstalledMods()
                val updates = dataStore.getUpdateCache()
                _state.update {
                    it.copy(installedMods = mods, updates = updates, isLoading = false, isRefreshing = false)
                }
                updateDisplayedMods()
                loadProfiles()

                // Auto-check for updates if stale
                val lastCheck = dataStore.getLastUpdateCheck()
                if (mods.isNotEmpty() && System.currentTimeMillis() - lastCheck > UPDATE_CHECK_INTERVAL_MS) {
                    checkForUpdates()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "Failed to load mods", e)
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Failed to load mods")
                }
            }
        }
    }

    fun checkForUpdates() {
        val mods = _state.value.installedMods
        if (mods.isEmpty() || _state.value.isCheckingUpdates) return
        _state.update { it.copy(isCheckingUpdates = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Only check enabled mods
                val enabledMods = mods.filter { it.enabled }
                val updates = updateChecker.checkForUpdates(enabledMods)

                // Cache the results
                dataStore.setUpdateCache(updates)

                _state.update { it.copy(updates = updates, isCheckingUpdates = false) }
                updateDisplayedMods()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "Update check failed", e)
                _state.update { it.copy(isCheckingUpdates = false, error = e.message ?: "Could not check for updates") }
            }
        }
    }

    fun toggleMod(folderName: String, enable: Boolean) {
        changeMods {
            val success = if (enable) {
                fileManager.enableMod(folderName)
            } else {
                fileManager.disableMod(folderName)
            }
            if (success) {
                loadInstalledMods()
            } else {
                _state.update {
                    it.copy(error = "Could not change the mod’s enabled state")
                }
            }
        }
    }

    fun removeMod(folderName: String) {
        changeMods {
            val mod = _state.value.installedMods.find { it.folderName == folderName }
            if (fileManager.removeMod(folderName)) {
                mod?.let { dataStore.removeModMetadata(it.manifest.uniqueID) }
                loadInstalledMods()
            } else {
                _state.update { it.copy(error = "Could not remove the mod") }
            }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val tempFile = File.createTempFile("mod-import-", ".zip", context.cacheDir)
            _state.update { it.copy(importMessage = null) }
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot read file")

                val result = fileManager.installFromZip(tempFile)

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
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "Import failed", e)
                _state.update { it.copy(importMessage = "Import failed: ${e.message}") }
            } finally {
                tempFile.delete()
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

    // ── Profiles ─────────────────────────────────────────────────────────

    private fun loadProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = dataStore.getProfiles()
                val activeName = detectActiveProfile(profiles)
                _state.update { it.copy(profiles = profiles, activeProfileName = activeName) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "Failed to load profiles", e)
            }
        }
    }

    fun saveCurrentAsProfile(name: String) {
        changeMods {
            val enabledIds = _state.value.installedMods
                .filter { it.enabled }
                .map { it.manifest.uniqueID.lowercase() }
                .toSet()

            val profile = ModProfile(name = name, enabledModIds = enabledIds)
            dataStore.saveProfile(profile)

            val profiles = dataStore.getProfiles()
            _state.update {
                it.copy(
                    profiles = profiles,
                    activeProfileName = name,
                    profileMessage = "Profile \"$name\" saved"
                )
            }
        }
    }

    fun applyProfile(profile: ModProfile) {
        if (_state.value.isApplyingProfile) return
        _state.update { it.copy(isApplyingProfile = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val failed = fileManager.applyProfile(profile.enabledModIds)
                _state.update {
                    it.copy(
                        isApplyingProfile = false,
                        profileMessage = if (failed.isEmpty()) {
                            "Profile \"${profile.name}\" applied"
                        } else {
                            "Could not change: ${failed.joinToString()}"
                        }
                    )
                }
                loadInstalledMods()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "Failed to apply profile", e)
                _state.update {
                    it.copy(isApplyingProfile = false, profileMessage = "Failed to apply profile")
                }
            }
        }
    }

    fun deleteProfile(name: String) {
        changeMods {
            dataStore.deleteProfile(name)
            loadProfiles()
        }
    }

    fun clearProfileMessage() {
        _state.update { it.copy(profileMessage = null) }
    }

    private fun changeMods(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "Mod operation failed", e)
                _state.update { it.copy(error = e.message ?: "Mod operation failed") }
            }
        }
    }

    private fun detectActiveProfile(profiles: List<ModProfile>): String? {
        val currentEnabled = _state.value.installedMods
            .filter { it.enabled }
            .map { it.manifest.uniqueID.lowercase() }
            .toSet()

        return profiles.find { it.enabledModIds.map { id -> id.lowercase() }.toSet() == currentEnabled }?.name
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
