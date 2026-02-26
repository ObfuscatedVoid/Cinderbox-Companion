package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.api.NexusModSource
import com.sdvsync.mods.models.RemoteMod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BrowseCategory {
    TRENDING,
    LATEST,
    RECENTLY_UPDATED
}

data class ModBrowseState(
    val hasApiKey: Boolean = false,
    val isValidatingKey: Boolean = false,
    val apiKeyError: String? = null,
    val category: BrowseCategory = BrowseCategory.TRENDING,
    val searchQuery: String = "",
    val mods: List<RemoteMod> = emptyList(),
    val installedUniqueIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ModBrowseViewModel(
    private val nexusSource: NexusModSource,
    private val dataStore: ModDataStore,
    private val fileManager: ModFileManager
) : ViewModel() {

    companion object {
        private const val TAG = "ModBrowseVM"
    }

    private val _state = MutableStateFlow(ModBrowseState())
    val state: StateFlow<ModBrowseState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        checkApiKey()
        loadInstalledIds()
    }

    private fun checkApiKey() {
        val key = dataStore.getNexusApiKey()
        _state.value = _state.value.copy(hasApiKey = key != null)
        if (key != null) {
            loadCategory(BrowseCategory.TRENDING)
        }
    }

    private fun loadInstalledIds() {
        viewModelScope.launch(Dispatchers.IO) {
            val mods = fileManager.listInstalledMods()
            _state.value = _state.value.copy(
                installedUniqueIds = mods.map { it.manifest.uniqueID.lowercase() }.toSet()
            )
        }
    }

    fun validateAndSaveApiKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isValidatingKey = true, apiKeyError = null)
            val valid = nexusSource.validateApiKey(key)
            if (valid) {
                dataStore.setNexusApiKey(key)
                _state.value = _state.value.copy(
                    hasApiKey = true,
                    isValidatingKey = false
                )
                loadCategory(BrowseCategory.TRENDING)
            } else {
                _state.value = _state.value.copy(
                    isValidatingKey = false,
                    apiKeyError = "Invalid API key"
                )
            }
        }
    }

    fun removeApiKey() {
        dataStore.setNexusApiKey(null)
        _state.value = _state.value.copy(
            hasApiKey = false,
            mods = emptyList()
        )
    }

    fun loadCategory(category: BrowseCategory) {
        _state.value = _state.value.copy(
            category = category,
            searchQuery = "",
            isLoading = true,
            error = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mods = when (category) {
                    BrowseCategory.TRENDING -> nexusSource.getTrending()
                    BrowseCategory.LATEST -> nexusSource.getLatestAdded()
                    BrowseCategory.RECENTLY_UPDATED -> nexusSource.getLatestUpdated()
                }
                _state.value = _state.value.copy(mods = mods, isLoading = false)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load category", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load mods"
                )
            }
        }
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            loadCategory(_state.value.category)
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300) // Debounce
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = nexusSource.search(query)
                _state.value = _state.value.copy(mods = result.mods, isLoading = false)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Search failed", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }
}
