package com.sdvsync.autosync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sdvsync.MainActivity
import com.sdvsync.R
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoSyncService::class.java))
        }
    }

    private val syncEngine: SyncEngine by inject()
    private val syncHistory: SyncHistoryStore by inject()
    private val processMonitor = GameProcessMonitor()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring for Stardew Valley..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        processMonitor.start(
            onGameStarted = {
                updateNotification("Stardew Valley is running")
                Log.d(TAG, "Game started")
            },
            onGameStopped = {
                updateNotification("Game closed, syncing...")
                Log.d(TAG, "Game stopped, triggering auto-push")
                serviceScope.launch { autoSync() }
            },
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
            // Find which save was most recently modified and push it
            val localSaves = com.sdvsync.saves.SaveFileManager(
                com.sdvsync.fileaccess.RootFileAccess(),
                com.sdvsync.saves.SaveMetadataParser(),
            ).listLocalSaves()

            if (localSaves.isEmpty()) {
                updateNotification("No local saves found")
                return
            }

            // Push the most recently played save (highest daysPlayed)
            val latestSave = localSaves.maxByOrNull { it.metadata.daysPlayed }
                ?: return

            updateNotification("Pushing ${latestSave.folderName}...")

            val result = syncEngine.pushSave(latestSave.folderName) { message ->
                updateNotification(message)
            }

            val message = when (result) {
                is SyncResult.Success -> result.message
                is SyncResult.Error -> "Error: ${result.message}"
                is SyncResult.NeedsConflictResolution -> "Conflict detected, manual sync needed"
            }

            syncHistory.addEntry(
                saveName = latestSave.folderName,
                direction = "push",
                success = result is SyncResult.Success,
                message = message,
            )

            updateNotification("Monitoring... Last: $message")

        } catch (e: Exception) {
            Log.e(TAG, "Auto-sync failed", e)
            updateNotification("Auto-sync failed: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Sync",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background save sync monitoring"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SDV Sync")
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
