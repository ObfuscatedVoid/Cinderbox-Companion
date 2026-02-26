package com.sdvsync.mods

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sdvsync.MainActivity
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.models.InstallResult
import com.sdvsync.mods.models.ModDownloadProgress
import com.sdvsync.mods.models.ModDownloadState
import java.io.File
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.android.ext.android.inject

class ModDownloadService : Service() {

    companion object {
        private const val TAG = "ModDownloadService"
        private const val CHANNEL_ID = "mod_download"
        private const val NOTIFICATION_ID = 3

        private const val EXTRA_URL = "url"
        private const val EXTRA_MOD_NAME = "mod_name"
        private const val EXTRA_MOD_ID = "mod_id"
        private const val EXTRA_SOURCE = "source"

        fun start(context: Context, url: String, modName: String, modId: String, source: String) {
            val intent =
                Intent(context, ModDownloadService::class.java).apply {
                    putExtra(EXTRA_URL, url)
                    putExtra(EXTRA_MOD_NAME, modName)
                    putExtra(EXTRA_MOD_ID, modId)
                    putExtra(EXTRA_SOURCE, source)
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ModDownloadService::class.java))
        }
    }

    private val httpClient: OkHttpClient by inject()
    private val fileManager: ModFileManager by inject()
    private val dataStore: ModDataStore by inject()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.download_preparing)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val modName = intent.getStringExtra(EXTRA_MOD_NAME) ?: "Unknown Mod"
        val modId = intent.getStringExtra(EXTRA_MOD_ID) ?: ""
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: ""

        downloadJob?.cancel()
        downloadJob = serviceScope.launch { runDownload(url, modName, modId, source) }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceScope.cancel()
        val currentState = ModDownloadManager._progress.value.state
        if (currentState == ModDownloadState.DOWNLOADING ||
            currentState == ModDownloadState.EXTRACTING
        ) {
            ModDownloadManager._progress.value =
                ModDownloadProgress(
                    state = ModDownloadState.ERROR,
                    errorMessage = "Download cancelled"
                )
        }
        super.onDestroy()
    }

    private suspend fun runDownload(url: String, modName: String, modId: String, source: String) {
        try {
            // Download
            ModDownloadManager._progress.value =
                ModDownloadProgress(
                    state = ModDownloadState.DOWNLOADING,
                    modName = modName
                )
            updateNotification(getString(R.string.mods_download_downloading, modName))

            val tempFile = File(cacheDir, "mod_download_${System.currentTimeMillis()}.zip")
            downloadFile(url, tempFile)

            // Extract + Install
            ModDownloadManager._progress.value =
                ModDownloadProgress(
                    state = ModDownloadState.INSTALLING,
                    modName = modName
                )
            updateNotification(getString(R.string.mods_download_installing))

            val result = fileManager.installFromZip(tempFile)
            tempFile.delete()

            when (result) {
                is InstallResult.Success -> {
                    // Save metadata for each installed mod
                    for (mod in result.mods) {
                        dataStore.setModMetadata(
                            mod.manifest.uniqueID,
                            ModMetadata(
                                installedFrom = "$source:$modId",
                                installedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    ModDownloadManager._progress.value =
                        ModDownloadProgress(
                            state = ModDownloadState.COMPLETED,
                            modName = modName
                        )
                    updateNotification(getString(R.string.mods_download_complete, modName))
                }
                is InstallResult.Error -> {
                    ModDownloadManager._progress.value =
                        ModDownloadProgress(
                            state = ModDownloadState.ERROR,
                            modName = modName,
                            errorMessage = result.message
                        )
                    updateNotification(getString(R.string.mods_download_failed, result.message))
                }
            }
        } catch (e: CancellationException) {
            AppLogger.d(TAG, "Download cancelled")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Download failed", e)
            ModDownloadManager._progress.value =
                ModDownloadProgress(
                    state = ModDownloadState.ERROR,
                    modName = modName,
                    errorMessage = e.message ?: "Unknown error"
                )
            updateNotification(
                getString(R.string.mods_download_failed, e.message ?: "Unknown error")
            )
        } finally {
            stopSelf()
        }
    }

    private suspend fun downloadFile(url: String, destFile: File) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            val buffer = ByteArray(64 * 1024)

            destFile.outputStream().buffered().use { output ->
                body.byteStream().use { input ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        ModDownloadManager._progress.value =
                            ModDownloadManager._progress.value.copy(
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.mods_download_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
                    .apply {
                        description =
                            getString(R.string.mods_download_notification_channel_desc)
                    }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.mods_download_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val notification = buildNotification(text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }
}
