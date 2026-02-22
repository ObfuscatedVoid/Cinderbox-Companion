package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.sync.SyncHistoryEntry
import com.sdvsync.sync.SyncHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SyncLogState(
    val entries: List<SyncHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
)

class SyncLogViewModel(
    private val historyStore: SyncHistoryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncLogState())
    val state: StateFlow<SyncLogState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = SyncLogState(isLoading = true)
            val entries = historyStore.getHistory()
            _state.value = SyncLogState(entries = entries)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyStore.clear()
            _state.value = SyncLogState(entries = emptyList())
        }
    }
}
