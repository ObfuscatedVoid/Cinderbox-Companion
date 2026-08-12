package com.sdvsync.ui.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.ModMetadata
import com.sdvsync.mods.api.NEXUS_MIN_SEARCH_QUERY_LENGTH
import com.sdvsync.mods.api.NexusModSource
import com.sdvsync.mods.models.InstalledMod
import com.sdvsync.mods.models.RemoteMod
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrowseCategory {
    TRENDING,
    LATEST,
    RECENTLY_UPDATED
}

data class ModBrowseState(
    val hasApiKey: Boolean = false,
    val isValidatingKey: Boolean = false,
    @StringRes val apiKeyErrorRes: Int? = null,
    val category: BrowseCategory = BrowseCategory.TRENDING,
    val searchQuery: String = "",
    val mods: List<RemoteMod> = emptyList(),
    val installedSourceIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: NexusUiError? = null,
    val searchNeedsMoreCharacters: Boolean = false,
    val error: NexusUiError? = null
)

internal class BrowseRequestGate {
    private var generation = 0L

    fun next(): Long = ++generation

    fun current(): Long = generation

    fun isCurrent(requestGeneration: Long): Boolean = generation == requestGeneration
}

internal fun remoteModSourceIdentifier(sourceId: String, modId: String): String =
    "${sourceId.trim()}:${modId.trim()}".lowercase(Locale.ROOT)

internal fun collectInstalledSourceIds(
    installedMods: List<InstalledMod>,
    metadata: Map<String, ModMetadata>
): Set<String> {
    val metadataByUniqueId = metadata.mapKeys { (uniqueId, _) -> uniqueId.lowercase(Locale.ROOT) }
    return installedMods.mapNotNullTo(mutableSetOf()) { installedMod ->
        metadataByUniqueId[installedMod.manifest.uniqueID.lowercase(Locale.ROOT)]
            ?.installedFrom
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.lowercase(Locale.ROOT)
    }
}

internal fun mergeSearchPages(current: List<RemoteMod>, next: List<RemoteMod>): List<RemoteMod> {
    val seen = current.mapTo(mutableSetOf()) { remoteModSourceIdentifier(it.sourceId, it.modId) }
    return buildList {
        addAll(current)
        next.forEach { mod ->
            if (seen.add(remoteModSourceIdentifier(mod.sourceId, mod.modId))) add(mod)
        }
    }
}

internal fun searchNeedsMoreCharacters(query: String): Boolean {
    val normalizedQuery = query.trim()
    return normalizedQuery.isNotEmpty() && normalizedQuery.length < NEXUS_MIN_SEARCH_QUERY_LENGTH
}

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

    private var browseJob: Job? = null
    private val requestGate = BrowseRequestGate()

    init {
        checkApiKey()
        loadInstalledSourceIds()
    }

    private fun checkApiKey() {
        val hasApiKey = !dataStore.getNexusApiKey().isNullOrBlank()
        _state.value = _state.value.copy(hasApiKey = hasApiKey)
        if (hasApiKey) {
            loadCategory(BrowseCategory.TRENDING)
        }
    }

    private fun loadInstalledSourceIds() {
        viewModelScope.launch(Dispatchers.IO) {
            val installedMods = fileManager.listInstalledMods()
            val metadata = dataStore.getAllModMetadata()
            _state.update {
                it.copy(installedSourceIds = collectInstalledSourceIds(installedMods, metadata))
            }
        }
    }

    fun validateAndSaveApiKey(key: String) {
        val normalizedKey = key.trim()
        if (normalizedKey.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isValidatingKey = true, apiKeyErrorRes = null) }
            try {
                val valid = nexusSource.validateApiKey(normalizedKey)
                if (valid) {
                    dataStore.setNexusApiKey(normalizedKey)
                    _state.update { it.copy(hasApiKey = true, isValidatingKey = false) }
                    loadCategory(BrowseCategory.TRENDING)
                } else {
                    _state.update {
                        it.copy(
                            isValidatingKey = false,
                            apiKeyErrorRes = R.string.mods_api_key_invalid
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "API key validation failed", e)
                _state.update {
                    it.copy(
                        isValidatingKey = false,
                        apiKeyErrorRes = classifyNexusError(
                            e,
                            R.string.mods_api_key_validation_failed
                        ).messageRes
                    )
                }
            }
        }
    }

    fun removeApiKey() {
        requestGate.next()
        browseJob?.cancel()
        browseJob = null
        dataStore.setNexusApiKey(null)
        _state.value = _state.value.copy(
            hasApiKey = false,
            isValidatingKey = false,
            apiKeyErrorRes = null,
            searchQuery = "",
            mods = emptyList(),
            isLoading = false,
            page = 0,
            hasMore = false,
            isLoadingMore = false,
            loadMoreError = null,
            searchNeedsMoreCharacters = false,
            error = null
        )
    }

    fun loadCategory(category: BrowseCategory) {
        val requestGeneration = beginBrowseRequest()
        _state.value = _state.value.copy(
            category = category,
            searchQuery = "",
            isLoading = true,
            page = 0,
            hasMore = false,
            isLoadingMore = false,
            loadMoreError = null,
            searchNeedsMoreCharacters = false,
            error = null
        )
        browseJob = viewModelScope.launch {
            try {
                val mods = when (category) {
                    BrowseCategory.TRENDING -> nexusSource.getTrending()
                    BrowseCategory.LATEST -> nexusSource.getLatestAdded()
                    BrowseCategory.RECENTLY_UPDATED -> nexusSource.getLatestUpdated()
                }
                updateIfCurrent(requestGeneration) { it.copy(mods = mods, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load category", e)
                updateIfCurrent(requestGeneration) {
                    it.copy(
                        isLoading = false,
                        error = classifyNexusError(e, R.string.mods_error_load_failed)
                    )
                }
            }
        }
    }

    fun search(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            loadCategory(_state.value.category)
            return
        }

        val requestGeneration = beginBrowseRequest()
        val needsMoreCharacters = searchNeedsMoreCharacters(query)
        _state.value = _state.value.copy(
            searchQuery = query,
            mods = emptyList(),
            isLoading = !needsMoreCharacters,
            page = 0,
            hasMore = false,
            isLoadingMore = false,
            loadMoreError = null,
            searchNeedsMoreCharacters = needsMoreCharacters,
            error = null
        )
        if (needsMoreCharacters) return

        browseJob = viewModelScope.launch {
            delay(300)
            try {
                val result = nexusSource.search(normalizedQuery)
                updateIfCurrent(requestGeneration) {
                    it.copy(
                        mods = result.mods,
                        isLoading = false,
                        page = 1,
                        hasMore = result.hasMore
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Search failed", e)
                updateIfCurrent(requestGeneration) {
                    it.copy(
                        isLoading = false,
                        error = classifyNexusError(e, R.string.mods_error_search_failed)
                    )
                }
            }
        }
    }

    fun loadMore() {
        val currentState = _state.value
        val query = currentState.searchQuery.trim()
        if (
            query.length < NEXUS_MIN_SEARCH_QUERY_LENGTH ||
            currentState.isLoading ||
            currentState.isLoadingMore ||
            !currentState.hasMore
        ) {
            return
        }

        val requestGeneration = requestGate.current()
        val nextPage = currentState.page + 1
        _state.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        browseJob = viewModelScope.launch {
            try {
                val result = nexusSource.search(query, page = nextPage)
                updateIfCurrent(requestGeneration) {
                    it.copy(
                        mods = mergeSearchPages(it.mods, result.mods),
                        page = nextPage,
                        hasMore = result.hasMore,
                        isLoadingMore = false,
                        loadMoreError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load more search results", e)
                updateIfCurrent(requestGeneration) {
                    it.copy(
                        isLoadingMore = false,
                        loadMoreError = classifyNexusError(e, R.string.mods_error_load_more_failed)
                    )
                }
            }
        }
    }

    fun retry() {
        val currentState = _state.value
        if (currentState.searchQuery.isNotBlank()) {
            search(currentState.searchQuery)
        } else {
            loadCategory(currentState.category)
        }
    }

    private fun beginBrowseRequest(): Long {
        val requestGeneration = requestGate.next()
        browseJob?.cancel()
        browseJob = null
        return requestGeneration
    }

    private inline fun updateIfCurrent(requestGeneration: Long, transform: (ModBrowseState) -> ModBrowseState) {
        if (requestGate.isCurrent(requestGeneration)) {
            _state.update(transform)
        }
    }
}
