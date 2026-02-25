package com.sdvsync.download

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
import com.sdvsync.steam.SteamClientManager
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import android.app.ActivityManager
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

class GameDownloadService : Service() {

    companion object {
        private const val TAG = "GameDownloadService"
        private const val CHANNEL_ID = "game_download"
        private const val NOTIFICATION_ID = 2

        private const val EXTRA_BRANCH = "branch"
        private const val EXTRA_PASSWORD = "password"
        private const val EXTRA_INSTALL_DIR = "install_dir"
        private const val EXTRA_OS = "os"

        fun start(
            context: Context,
            branch: String,
            password: String?,
            installDir: String,
            os: String,
        ) {
            val intent = Intent(context, GameDownloadService::class.java).apply {
                putExtra(EXTRA_BRANCH, branch)
                putExtra(EXTRA_PASSWORD, password)
                putExtra(EXTRA_INSTALL_DIR, installDir)
                putExtra(EXTRA_OS, os)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameDownloadService::class.java))
        }
    }

    private val clientManager: SteamClientManager by inject()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var depotDownloader: DepotDownloader? = null
    private var downloadJob: Job? = null
    private var lastSpeedUpdateMs = 0L
    private var lastSpeedBytes = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.download_preparing)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val branch = intent?.getStringExtra(EXTRA_BRANCH) ?: "public"
        val password = intent?.getStringExtra(EXTRA_PASSWORD)
        val installDir = intent?.getStringExtra(EXTRA_INSTALL_DIR) ?: return START_NOT_STICKY
        val os = intent.getStringExtra(EXTRA_OS) ?: "windows"

        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            runDownload(branch, password, installDir, os)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AppLogger.d(TAG, "Service destroying, cancelling download")
        downloadJob?.cancel()
        try {
            depotDownloader?.close()
        } catch (_: Exception) {}
        depotDownloader = null
        serviceScope.cancel()

        val currentState = GameDownloadManager._progress.value.state
        if (currentState == DownloadState.DOWNLOADING || currentState == DownloadState.PREPARING) {
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.CANCELLED,
                errorMessage = getString(R.string.download_cancelled),
            )
        }

        super.onDestroy()
    }

    private suspend fun runDownload(
        branch: String,
        password: String?,
        installDir: String,
        os: String,
    ) {
        try {
            GameDownloadManager._progress.value = DownloadProgress(state = DownloadState.PREPARING)
            updateNotification(getString(R.string.download_preparing))

            val licenses = clientManager.licenses.value
            if (licenses.isEmpty()) {
                throw IllegalStateException(getString(R.string.download_error_no_licenses))
            }

            // Scale concurrency based on available heap to avoid OOM on low-end devices
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val heapMb = am.largeMemoryClass
            val (downloads, decompress) = when {
                heapMb >= 512 -> 8 to 4
                heapMb >= 384 -> 6 to 3
                heapMb >= 256 -> 4 to 2
                else -> 2 to 2
            }
            AppLogger.d(TAG, "Heap: ${heapMb}MB → maxDownloads=$downloads, maxDecompress=$decompress")

            val downloader = DepotDownloader(
                steamClient = clientManager.client,
                licenses = licenses,
                debug = false,
                maxDownloads = downloads,
                maxDecompress = decompress,
                maxFileWrites = 4,
                parentJob = currentCoroutineContext()[Job],
            )
            depotDownloader = downloader

            lastSpeedUpdateMs = System.currentTimeMillis()
            lastSpeedBytes = 0L

            downloader.addListener(object : IDownloadListener {
                override fun onDownloadStarted(item: DownloadItem) {
                    AppLogger.d(TAG, "Download started for item")
                    GameDownloadManager._progress.value = DownloadProgress(
                        state = DownloadState.DOWNLOADING,
                    )
                    updateNotification(getString(R.string.download_downloading))
                }

                override fun onChunkCompleted(
                    depotId: Int,
                    depotPercentComplete: Float,
                    compressedBytes: Long,
                    uncompressedBytes: Long,
                ) {
                    val current = GameDownloadManager._progress.value
                    val newDownloaded = current.downloadedBytes + uncompressedBytes

                    // Calculate speed every 500ms
                    val now = System.currentTimeMillis()
                    var speed = current.bytesPerSecond
                    if (now - lastSpeedUpdateMs >= 500) {
                        val elapsedMs = now - lastSpeedUpdateMs
                        val bytesDelta = newDownloaded - lastSpeedBytes
                        speed = if (elapsedMs > 0) (bytesDelta * 1000 / elapsedMs) else 0L
                        lastSpeedUpdateMs = now
                        lastSpeedBytes = newDownloaded
                    }

                    GameDownloadManager._progress.value = current.copy(
                        overallPercent = depotPercentComplete,
                        downloadedBytes = newDownloaded,
                        bytesPerSecond = speed,
                    )
                }

                override fun onFileCompleted(
                    depotId: Int,
                    fileName: String,
                    depotPercentComplete: Float,
                ) {
                    val current = GameDownloadManager._progress.value
                    GameDownloadManager._progress.value = current.copy(
                        currentFile = fileName,
                        overallPercent = depotPercentComplete,
                    )
                    updateNotification(getString(R.string.download_notification_progress, (depotPercentComplete * 100).toInt(), fileName))
                }

                override fun onDepotCompleted(
                    depotId: Int,
                    compressedBytes: Long,
                    uncompressedBytes: Long,
                ) {
                    AppLogger.d(TAG, "Depot $depotId completed: ${uncompressedBytes / 1024 / 1024} MB")
                    val current = GameDownloadManager._progress.value
                    GameDownloadManager._progress.value = current.copy(
                        totalBytes = current.totalBytes + uncompressedBytes,
                    )
                }

                override fun onDownloadCompleted(item: DownloadItem) {
                    AppLogger.d(TAG, "Download completed!")
                    GameDownloadManager._progress.value = DownloadProgress(
                        state = DownloadState.COMPLETED,
                        overallPercent = 1f,
                    )
                    updateNotification(getString(R.string.download_complete))
                }

                override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                    AppLogger.e(TAG, "Download failed", error)
                    GameDownloadManager._progress.value = DownloadProgress(
                        state = DownloadState.ERROR,
                        errorMessage = error.message ?: getString(R.string.download_error),
                    )
                    updateNotification(getString(R.string.download_notification_failed, error.message ?: getString(R.string.download_error_unknown)))
                }

                override fun onStatusUpdate(message: String) {
                    AppLogger.d(TAG, "Status: $message")
                }
            })

            val appItem = AppItem(
                appId = 413150,
                installToGameNameDirectory = false,
                installDirectory = installDir,
                branch = branch,
                branchPassword = password ?: "",
                downloadAllPlatforms = false,
                os = os,
                downloadAllArchs = false,
                osArch = "64",
                downloadAllLanguages = false,
                language = "english",
                lowViolence = false,
                depot = emptyList(),
                manifest = emptyList(),
            )

            downloader.add(appItem)
            downloader.finishAdding()
            downloader.awaitCompletion()

            AppLogger.d(TAG, "Download pipeline finished")

        } catch (e: CancellationException) {
            AppLogger.d(TAG, "Download cancelled")
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.CANCELLED,
                errorMessage = getString(R.string.download_cancelled),
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Download error", e)
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.ERROR,
                errorMessage = e.message ?: getString(R.string.download_error_unknown),
            )
            updateNotification(getString(R.string.download_notification_error, e.message ?: getString(R.string.download_error_unknown)))
        } finally {
            try {
                depotDownloader?.close()
            } catch (_: Exception) {}
            depotDownloader = null
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.download_notification_channel_desc)
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

        val progress = GameDownloadManager._progress.value
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (progress.state == DownloadState.DOWNLOADING) {
            builder.setProgress(100, (progress.overallPercent * 100).toInt(), false)
        } else if (progress.state == DownloadState.PREPARING) {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(text: String) {
        try {
            val notification = buildNotification(text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Ignore notification update failures
        }
    }
}
