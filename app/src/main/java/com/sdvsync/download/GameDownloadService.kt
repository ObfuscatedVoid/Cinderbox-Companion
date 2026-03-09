package com.sdvsync.download

import android.app.ActivityManager
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
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
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
        private const val EXTRA_VERIFY = "verify"

        fun start(
            context: Context,
            branch: String,
            password: String?,
            installDir: String,
            os: String,
            verify: Boolean = true
        ) {
            val intent = Intent(context, GameDownloadService::class.java).apply {
                putExtra(EXTRA_BRANCH, branch)
                putExtra(EXTRA_PASSWORD, password)
                putExtra(EXTRA_INSTALL_DIR, installDir)
                putExtra(EXTRA_OS, os)
                putExtra(EXTRA_VERIFY, verify)
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
    private val lastSpeedUpdateMs = AtomicLong(0L)
    private val lastSpeedBytes = AtomicLong(0L)

    // Track cumulative bytes per depot (callback values are cumulative, not deltas)
    private val depotBytes = ConcurrentHashMap<Int, Long>()

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
        val verify = intent.getBooleanExtra(EXTRA_VERIFY, true)

        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            runDownload(branch, password, installDir, os, verify)
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
        if (currentState == DownloadState.DOWNLOADING ||
            currentState == DownloadState.PREPARING ||
            currentState == DownloadState.VERIFYING
        ) {
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.CANCELLED,
                errorMessage = getString(R.string.download_cancelled)
            )
        }

        super.onDestroy()
    }

    private suspend fun runDownload(
        branch: String,
        password: String?,
        installDir: String,
        os: String,
        verify: Boolean
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
            data class ConcurrencyProfile(val downloads: Int, val decompress: Int, val writes: Int)
            val profile = when {
                heapMb >= 1024 -> ConcurrencyProfile(16, 8, 8)
                heapMb >= 768 -> ConcurrencyProfile(12, 6, 6)
                heapMb >= 512 -> ConcurrencyProfile(8, 4, 4)
                heapMb >= 384 -> ConcurrencyProfile(6, 3, 2)
                heapMb >= 256 -> ConcurrencyProfile(4, 2, 1)
                else -> ConcurrencyProfile(2, 2, 1)
            }
            AppLogger.d(
                TAG,
                "Heap: ${heapMb}MB → downloads=${profile.downloads}, decompress=${profile.decompress}, writes=${profile.writes}"
            )

            val downloader = DepotDownloader(
                steamClient = clientManager.client,
                licenses = licenses,
                debug = false,
                maxDownloads = profile.downloads,
                maxDecompress = profile.decompress,
                maxFileWrites = profile.writes,
                parentJob = currentCoroutineContext()[Job]
            )
            depotDownloader = downloader

            lastSpeedUpdateMs.set(System.currentTimeMillis())
            lastSpeedBytes.set(0L)
            depotBytes.clear()

            // Track completed files for verification
            val completedFiles = Collections.synchronizedSet(mutableSetOf<String>())

            downloader.addListener(object : IDownloadListener {
                override fun onDownloadStarted(item: DownloadItem) {
                    try {
                        AppLogger.d(TAG, "Download started for item")
                        GameDownloadManager._progress.value = DownloadProgress(
                            state = DownloadState.DOWNLOADING
                        )
                        updateNotification(getString(R.string.download_downloading))
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in onDownloadStarted", e)
                    }
                }

                override fun onChunkCompleted(
                    depotId: Int,
                    depotPercentComplete: Float,
                    compressedBytes: Long,
                    uncompressedBytes: Long
                ) {
                    try {
                        depotBytes[depotId] = uncompressedBytes
                        val totalDownloaded = depotBytes.values.sum()

                        // Calculate speed every 500ms
                        val now = System.currentTimeMillis()
                        val lastUpdate = lastSpeedUpdateMs.get()
                        var speed = GameDownloadManager._progress.value.bytesPerSecond
                        if (now - lastUpdate >= 500) {
                            if (lastSpeedUpdateMs.compareAndSet(lastUpdate, now)) {
                                val prevBytes = lastSpeedBytes.getAndSet(totalDownloaded)
                                val elapsedMs = now - lastUpdate
                                val bytesDelta = totalDownloaded - prevBytes
                                speed = if (elapsedMs > 0) (bytesDelta * 1000 / elapsedMs) else 0L
                            }
                        }

                        GameDownloadManager._progress.update { current ->
                            current.copy(
                                overallPercent = depotPercentComplete,
                                downloadedBytes = totalDownloaded,
                                bytesPerSecond = speed
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in onChunkCompleted", e)
                    }
                }

                override fun onFileCompleted(depotId: Int, fileName: String, depotPercentComplete: Float) {
                    try {
                        completedFiles.add(fileName)
                        GameDownloadManager._progress.update { current ->
                            current.copy(
                                currentFile = fileName,
                                overallPercent = depotPercentComplete
                            )
                        }
                        updateNotification(
                            getString(
                                R.string.download_notification_progress,
                                (depotPercentComplete * 100).toInt(),
                                fileName
                            )
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in onFileCompleted", e)
                    }
                }

                override fun onDepotCompleted(depotId: Int, compressedBytes: Long, uncompressedBytes: Long) {
                    try {
                        AppLogger.d(TAG, "Depot $depotId completed: ${uncompressedBytes / 1024 / 1024} MB")
                        depotBytes[depotId] = uncompressedBytes
                        GameDownloadManager._progress.update { current ->
                            current.copy(totalBytes = depotBytes.values.sum())
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in onDepotCompleted", e)
                    }
                }

                override fun onDownloadCompleted(item: DownloadItem) {
                    AppLogger.d(TAG, "Download completed! (${completedFiles.size} files)")
                    // Don't set COMPLETED here — verification may follow after awaitCompletion()
                }

                override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                    try {
                        AppLogger.e(TAG, "Download failed", error)
                        GameDownloadManager._progress.value = DownloadProgress(
                            state = DownloadState.ERROR,
                            errorMessage = error.message ?: getString(R.string.download_error)
                        )
                        updateNotification(
                            getString(
                                R.string.download_notification_failed,
                                error.message ?: getString(R.string.download_error_unknown)
                            )
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in onDownloadFailed", e)
                    }
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
                manifest = emptyList()
            )

            downloader.add(appItem)
            downloader.finishAdding()
            downloader.awaitCompletion()

            AppLogger.d(TAG, "Download pipeline finished")

            if (verify) {
                verifyFiles(installDir, completedFiles)
            } else {
                GameDownloadManager._progress.value = DownloadProgress(
                    state = DownloadState.COMPLETED,
                    overallPercent = 1f
                )
                updateNotification(getString(R.string.download_complete))
            }
        } catch (e: CancellationException) {
            AppLogger.d(TAG, "Download cancelled")
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.CANCELLED,
                errorMessage = getString(R.string.download_cancelled)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Download error", e)
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.ERROR,
                errorMessage = e.message ?: getString(R.string.download_error_unknown)
            )
            updateNotification(
                getString(
                    R.string.download_notification_error,
                    e.message ?: getString(R.string.download_error_unknown)
                )
            )
        } finally {
            try {
                depotDownloader?.close()
            } catch (_: Exception) {}
            depotDownloader = null
            stopSelf()
        }
    }

    private suspend fun verifyFiles(installDir: String, completedFiles: Set<String>) {
        AppLogger.d(TAG, "Starting file verification for ${completedFiles.size} tracked files")

        val installRoot = File(installDir)
        // Walk the install directory, excluding .DepotDownloader metadata
        val allFiles = installRoot.walk()
            .filter { it.isFile && !it.relativeTo(installRoot).path.startsWith(".DepotDownloader") }
            .toList()

        val totalFiles = allFiles.size
        AppLogger.d(TAG, "Found $totalFiles files on disk (tracked ${completedFiles.size} during download)")

        GameDownloadManager._progress.value = DownloadProgress(
            state = DownloadState.VERIFYING,
            overallPercent = 0f,
            totalFilesToVerify = totalFiles
        )
        updateNotification(getString(R.string.download_notification_verifying, 0))

        val errors = mutableListOf<String>()
        val buffer = ByteArray(64 * 1024) // 64KB read buffer

        for ((index, file) in allFiles.withIndex()) {
            // Yield to allow cancellation
            yield()

            val relativePath = file.relativeTo(installRoot).path
            try {
                if (file.length() == 0L) {
                    // Zero-byte files are valid in some depots, just log
                    AppLogger.d(TAG, "Verify: $relativePath is 0 bytes (ok)")
                } else {
                    // Read every byte to confirm disk integrity
                    file.inputStream().use { input ->
                        while (input.read(buffer) != -1) {
                            // Just reading to verify readability
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Verify failed: $relativePath", e)
                errors.add(relativePath)
            }

            val verified = index + 1
            val percent = verified.toFloat() / totalFiles
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.VERIFYING,
                overallPercent = percent,
                verifiedFiles = verified,
                totalFilesToVerify = totalFiles,
                currentFile = relativePath
            )

            // Throttle notification updates to every ~5%
            if (verified % maxOf(1, totalFiles / 20) == 0 || verified == totalFiles) {
                updateNotification(getString(R.string.download_notification_verifying, (percent * 100).toInt()))
            }
        }

        val passed = errors.isEmpty()
        AppLogger.d(TAG, "Verification ${if (passed) "PASSED" else "FAILED"}: $totalFiles files, ${errors.size} errors")

        GameDownloadManager._progress.value = DownloadProgress(
            state = DownloadState.COMPLETED,
            overallPercent = 1f,
            verifiedFiles = totalFiles,
            totalFilesToVerify = totalFiles,
            verificationPassed = passed,
            verificationErrors = errors
        )
        updateNotification(getString(R.string.download_complete))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val progress = GameDownloadManager._progress.value
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        when (progress.state) {
            DownloadState.DOWNLOADING, DownloadState.VERIFYING -> {
                builder.setProgress(100, (progress.overallPercent * 100).toInt(), false)
            }
            DownloadState.PREPARING -> {
                builder.setProgress(0, 0, true)
            }
            else -> {}
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
