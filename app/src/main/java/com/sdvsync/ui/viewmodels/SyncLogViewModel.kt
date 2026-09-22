package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.sync.SyncHistoryEntry
import com.sdvsync.sync.SyncHistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncLogState(
    val entries: List<SyncHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SyncLogViewModel(private val historyStore: SyncHistoryStore) : ViewModel() {

    private val _state = MutableStateFlow(SyncLogState())
    val state: StateFlow<SyncLogState> = _state.asStateFlow()

    fun load() = updateHistory(clear = false)
    fun clearHistory() = updateHistory(clear = true)

    private fun updateHistory(clear: Boolean) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (clear) historyStore.clear()
                _state.value = SyncLogState(entries = historyStore.getHistory())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(isLoading = false, error = "Could not read or update sync history") }
            }
        }
    }
}
