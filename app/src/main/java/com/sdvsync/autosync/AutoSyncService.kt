package com.sdvsync.autosync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sdvsync.MainActivity
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.sync.SyncEngine
import com.sdvsync.sync.SyncHistoryStore
import com.sdvsync.sync.SyncResult
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

/**
 * Foreground service for automatic save sync.
 * Root-only: monitors Stardew Valley process and syncs on game close.
 */
class AutoSyncService : Service() {

    companion object {
        private const val TAG = "AutoSyncService"
        private const val CHANNEL_ID = "auto_sync"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, AutoSyncService::class.java)
            context.startForegroundService(intent)
        }

        // Android matches service Intent components, not Intent object identity.
        @android.annotation.SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) {
            context.stopService(Intent(context, AutoSyncService::class.java))
        }
    }

    private val syncEngine: SyncEngine by inject()
    private val saveFileManager: com.sdvsync.saves.SaveFileManager by inject()
    private val syncHistory: SyncHistoryStore by inject()
    private val processMonitor = GameProcessMonitor()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var saveBaseline: Map<String, Map<String, String>>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring for Stardew Valley..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        processMonitor.start(
            scope = serviceScope,
            onGameStarted = {
                updateNotification("Stardew Valley is running")
                saveBaseline = snapshotSaves()
            },
            onGameStopped = {
                updateNotification("Game closed, syncing...")
                AppLogger.d(TAG, "Game stopped, triggering auto-push")
                autoSync()
            }
        )
        return START_STICKY
    }

    override fun onDestroy() {
        processMonitor.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun autoSync() {
        try {
            val after = snapshotSaves() ?: return
            val changed = changedSaveNames(saveBaseline, after)
            saveBaseline = null
            if (changed.isEmpty()) {
                updateNotification("Monitoring for Stardew Valley… No changed saves")
                return
            }
            for (folderName in changed) {
                if (processMonitor.checkGameRunning() != false) return
                updateNotification("Uploading $folderName…")
                val result = syncEngine.pushSave(folderName) { updateNotification(it) }
                val message = when (result) {
                    is SyncResult.Success -> result.message
                    is SyncResult.Error -> "Error: ${result.message}"
                    is SyncResult.NeedsConflictResolution -> "Conflict detected, manual sync needed"
                }
                syncHistory.addEntry(
                    saveName = folderName,
                    direction = "push",
                    success = result is SyncResult.Success,
                    message = message
                )
                updateNotification("Monitoring… Last: $message")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e(TAG, "Auto-sync failed", e)
            updateNotification("Auto-sync failed: ${e.message}")
        }
    }

    private suspend fun snapshotSaves(): Map<String, Map<String, String>>? = try {
        check(saveFileManager.isSaveDirectoryAccessible()) { "Save directory is unavailable" }
        saveFileManager.listLocalSaves().associate { save ->
            save.folderName to saveFingerprint(saveFileManager.readLocalSave(save.folderName))
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        AppLogger.e(TAG, "Cannot safely determine changed saves", e)
        updateNotification("Cannot read saves. Open the app to check storage access.")
        null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background save sync monitoring"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
