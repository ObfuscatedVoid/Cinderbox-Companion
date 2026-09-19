package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.cinderbox.CinderboxLayout
import com.sdvsync.cinderbox.CinderboxLayoutEvents
import com.sdvsync.cinderbox.CinderboxMigrationResult
import com.sdvsync.fileaccess.FileAccessDetector
import com.sdvsync.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CinderboxMigrationState(
    val showDialog: Boolean = false,
    val isMigrating: Boolean = false,
    val result: CinderboxMigrationResult? = null
)

class CinderboxMigrationViewModel(
    private val fileAccessDetector: FileAccessDetector,
    private val layout: CinderboxLayout = CinderboxLayout()
) : ViewModel() {

    companion object {
        private const val TAG = "CinderboxMigrate"
    }

    private val _state = MutableStateFlow(CinderboxMigrationState())
    val state: StateFlow<CinderboxMigrationState> = _state.asStateFlow()

    private var skippedThisSession = false

    fun check() {
        if (skippedThisSession || _state.value.isMigrating || _state.value.result != null) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!fileAccessDetector.isCinderboxMode()) return@launch
            if (fileAccessDetector.isCinderboxMigrationDontAsk()) return@launch
            val hasLegacy = layout.hasLegacyContent()
            _state.update { current ->
                if (skippedThisSession || current.isMigrating || current.result != null) {
                    current
                } else {
                    current.copy(showDialog = hasLegacy, result = if (hasLegacy) current.result else null)
                }
            }
        }
    }

    fun showPrompt() {
        if (!_state.value.isMigrating) {
            _state.update { it.copy(showDialog = true, result = null) }
        }
    }

    fun migrate() {
        if (_state.value.isMigrating) return
        _state.update { it.copy(isMigrating = true, result = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { layout.migrate() }
            AppLogger.i(
                TAG,
                "migrate moved=${result.moved} conflicts=${result.leftoverConflicts} errors=${result.errors}"
            )
            skippedThisSession = true
            CinderboxLayoutEvents.notifyChanged()
            _state.update { it.copy(showDialog = true, isMigrating = false, result = result) }
        }
    }

    fun remindLater() {
        skippedThisSession = true
        _state.update { it.copy(showDialog = false) }
    }

    fun dontAskAgain() {
        skippedThisSession = true
        fileAccessDetector.setCinderboxMigrationDontAsk(true)
        _state.update { it.copy(showDialog = false) }
    }

    fun dismissResult() {
        skippedThisSession = true
        _state.update { it.copy(showDialog = false, result = null) }
    }
}
