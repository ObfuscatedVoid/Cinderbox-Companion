package com.sdvsync.autosync

import com.sdvsync.fileaccess.runFileCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameProcessMonitor {
    private var monitorJob: Job? = null

    fun start(scope: CoroutineScope, onGameStarted: suspend () -> Unit, onGameStopped: suspend () -> Unit) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            var wasRunning = false
            while (isActive) {
                val running = checkGameRunning()
                if (running == true && !wasRunning) {
                    onGameStarted()
                    wasRunning = true
                } else if (running == false && wasRunning) {
                    delay(3000)
                    if (checkGameRunning() == false) {
                        onGameStopped()
                        wasRunning = false
                    }
                }
                delay(5000)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun checkGameRunning(): Boolean? = try {
        // A failed root request must not be mistaken for the game closing.
        val result = runFileCommand(
            listOf("su", "-c"),
            "test \"\$(id -u)\" = 0 || exit 2; pidof com.chucklefish.stardewvalley com.game.cinderbox; test \$? -le 1",
            timeoutSeconds = 2
        )
        if (result.successful) result.bytes.decodeToString().isNotBlank() else null
    } catch (_: Exception) {
        null
    }
}
