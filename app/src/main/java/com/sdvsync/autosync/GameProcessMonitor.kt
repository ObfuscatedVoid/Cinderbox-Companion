package com.sdvsync.autosync

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Root-only monitor that detects when Stardew Valley is running.
 * Polls for the game process at regular intervals.
 */
class GameProcessMonitor {

    companion object {
        private const val SDV_PACKAGE = "com.chucklefish.stardewvalley"
        private const val POLL_INTERVAL_MS = 5000L
    }

    private val _isGameRunning = MutableStateFlow(false)
    val isGameRunning: StateFlow<Boolean> = _isGameRunning.asStateFlow()

    private var monitorJob: Job? = null

    fun start(onGameStarted: () -> Unit = {}, onGameStopped: () -> Unit = {}) {
        if (monitorJob != null) return

        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            var wasRunning = false

            while (isActive) {
                val running = checkGameRunning()
                _isGameRunning.value = running

                if (running && !wasRunning) {
                    onGameStarted()
                } else if (!running && wasRunning) {
                    // Wait briefly for save to finish writing
                    delay(3000)
                    onGameStopped()
                }

                wasRunning = running
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        _isGameRunning.value = false
    }

    private fun checkGameRunning(): Boolean = try {
        val process = Runtime.getRuntime().exec(
            arrayOf("su", "-c", "pidof $SDV_PACKAGE")
        )
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}
