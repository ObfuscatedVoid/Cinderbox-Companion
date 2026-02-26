package com.sdvsync.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveMetadata
import com.sdvsync.saves.SaveMetadataParser
import com.sdvsync.steam.SteamClientManager
import com.sdvsync.steam.SteamCloudService
import com.sdvsync.sync.ConflictResolver
import com.sdvsync.sync.SyncDirection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SaveEntry(
    val folderName: String,
    val cloudMeta: SaveMetadata?,
    val localMeta: SaveMetadata?,
    val syncDirection: SyncDirection,
    val statusMessage: String,
    val hasCloud: Boolean,
    val hasLocal: Boolean
)

data class DashboardState(
    val saves: List<SaveEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isStagingMode: Boolean = false
)

class DashboardViewModel(
    private val context: Context,
    private val clientManager: SteamClientManager,
    private val cloudService: SteamCloudService,
    private val saveFileManager: SaveFileManager,
    private val metadataParser: SaveMetadataParser,
    private val conflictResolver: ConflictResolver
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    fun refresh(isUserRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = !isUserRefresh,
                isRefreshing = isUserRefresh,
                error = null,
                isStagingMode = saveFileManager.isStaging
            )

            try {
                // Wait for Steam connection to be ready
                if (!clientManager.isLoggedIn) {
                    AppLogger.d(TAG, "Waiting for Steam login...")
                    val loggedIn = clientManager.awaitLoggedIn(timeoutMs = 30_000)
                    if (!loggedIn) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = context.getString(R.string.error_not_connected)
                        )
                        return@launch
                    }
                }

                AppLogger.d(TAG, "Fetching cloud saves...")

                // Fetch cloud saves with retry (Steam may disconnect/reconnect after login)
                val cloudSaves = retryOnDisconnect {
                    cloudService.listCloudSaves()
                }
                AppLogger.d(TAG, "Got ${cloudSaves.size} cloud save folders")

                val cloudMetadata = mutableMapOf<String, SaveMetadata>()

                for ((folderName, files) in cloudSaves) {
                    val infoFile = files.find { it.baseName == "SaveGameInfo" }
                    if (infoFile != null) {
                        try {
                            val data = retryOnDisconnect {
                                cloudService.downloadFile(infoFile.fullPath)
                            }
                            metadataParser.parseFromBytes(data)?.let {
                                cloudMetadata[folderName] = it
                            }
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Failed to parse SaveGameInfo for $folderName", e)
                        }
                    }
                }

                // Fetch local saves
                val localSaves = try {
                    saveFileManager.listLocalSaves()
                } catch (_: Exception) {
                    emptyList()
                }
                val localMap = localSaves.associateBy { it.folderName }

                // Merge into a unified list (use cloudSaves.keys so saves appear
                // even if SaveGameInfo metadata couldn't be parsed)
                val allFolderNames = (cloudSaves.keys + localMap.keys).distinct()
                val entries = allFolderNames.map { folderName ->
                    val cloudMeta = cloudMetadata[folderName]
                    val localMeta = localMap[folderName]?.metadata

                    val comparison = conflictResolver.compare(cloudMeta, localMeta)

                    SaveEntry(
                        folderName = folderName,
                        cloudMeta = cloudMeta,
                        localMeta = localMeta,
                        syncDirection = comparison.direction,
                        statusMessage = comparison.message,
                        hasCloud = folderName in cloudSaves,
                        hasLocal = localMeta != null
                    )
                }.sortedByDescending {
                    maxOf(
                        it.cloudMeta?.daysPlayed ?: 0,
                        it.localMeta?.daysPlayed ?: 0
                    )
                }

                _state.value = DashboardState(
                    saves = entries,
                    isLoading = false,
                    isRefreshing = false,
                    isStagingMode = saveFileManager.isStaging
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "Failed to load saves", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = context.getString(R.string.error_load_saves_failed, e.message ?: "Unknown error")
                )
            }
        }
    }

    /**
     * Retry an operation up to [maxRetries] times if it fails due to a Steam disconnect.
     * Waits for the connection to be re-established between retries.
     *
     * Steam often disconnects ~50ms after login (CM server redirect). The disconnect callback
     * takes up to ~1s to be processed by the callback loop, and auto-reconnect adds a 2s delay.
     * So we must wait long enough for the full disconnect→reconnect cycle to complete.
     */
    private suspend fun <T> retryOnDisconnect(maxRetries: Int = 5, block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                // If the coroutine itself was cancelled, propagate immediately.
                // Don't just check `is CancellationException` because
                // kotlinx.coroutines.CancellationException is a typealias for
                // java.util.concurrent.CancellationException, which Steam's
                // AsyncJobManager also throws when it cancels pending futures on disconnect.
                if (!currentCoroutineContext().isActive) throw e

                lastException = e
                AppLogger.w(TAG, "Cloud request failed (attempt ${attempt + 1}/$maxRetries): ${e::class.simpleName}")

                // Wait for the disconnect callback to be processed by the callback loop
                // and for auto-reconnect to kick in (callback loop ~1s + reconnect delay 2s)
                AppLogger.d(TAG, "Waiting 5s for disconnect processing + auto-reconnect...")
                delay(5000)

                // If still not logged in, wait for the auto-reconnect to fully complete
                if (!clientManager.isLoggedIn) {
                    AppLogger.d(
                        TAG,
                        "Not logged in yet (state=${clientManager.connectionState.value}), waiting for reconnection..."
                    )
                    val reconnected = clientManager.awaitLoggedIn(timeoutMs = 30_000)
                    if (!reconnected) {
                        throw RuntimeException("Lost connection to Steam", e)
                    }
                    // Extra stabilization delay after reconnect
                    delay(2000)
                }

                AppLogger.d(TAG, "Ready to retry (state=${clientManager.connectionState.value})")
            }
        }
        throw lastException ?: RuntimeException("Failed after $maxRetries retries")
    }

    companion object {
        private const val TAG = "Dashboard"
    }
}
