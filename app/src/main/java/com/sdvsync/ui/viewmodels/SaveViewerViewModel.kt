package com.sdvsync.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.logging.AppLogger
import com.sdvsync.saves.SaveFileData
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveFileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveViewerTab { FARMER, RELATIONSHIPS, ANIMALS, BUNDLES, MUSEUM, CRAFTING }

data class SaveViewerState(
    val saveFolderName: String = "",
    val data: SaveFileData? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedTab: SaveViewerTab = SaveViewerTab.FARMER
)

class SaveViewerViewModel(
    private val context: Context,
    private val saveFileManager: SaveFileManager,
    private val saveFileParser: SaveFileParser
) : ViewModel() {

    companion object {
        private const val TAG = "SaveViewerVM"
    }

    private val _state = MutableStateFlow(SaveViewerState())
    val state: StateFlow<SaveViewerState> = _state.asStateFlow()

    fun loadSave(saveFolderName: String) {
        _state.update { it.copy(saveFolderName = saveFolderName, isLoading = true, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = saveFileManager.readLocalSave(saveFolderName)
                val mainSaveData = files[saveFolderName]

                if (mainSaveData == null) {
                    _state.update { it.copy(isLoading = false, error = "Main save file not found") }
                    return@launch
                }

                val data = saveFileParser.parse(mainSaveData)
                if (data == null) {
                    _state.update { it.copy(isLoading = false, error = "Failed to parse save file") }
                    return@launch
                }

                _state.update { it.copy(data = data, isLoading = false) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load save", e)
                _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun selectTab(tab: SaveViewerTab) {
        _state.update { it.copy(selectedTab = tab) }
    }
}
