package com.sdvsync.download

import android.app.ActivityManager
import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.local.M23CinderboxFirstPolicy
import com.sdvsync.local.CinderboxDownloadDestination
import com.sdvsync.ui.policy.DeveloperFeaturePolicy
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.sdvsync.MainActivity
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.local.InstallationScanner
import com.sdvsync.local.InstallHealth
import com.sdvsync.local.StoragePreflight
import com.sdvsync.steam.SteamClientManager
import com.sdvsync.steam.SteamContentService
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
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
        private const val EXTRA_BUILD_ID = "steam_build_id"

        fun start(
            context: Context,
            branch: String,
            password: String?,
            installDir: String,
            os: String,
            verify: Boolean = true,
            expectedBuildId: Long = 0L
        ) {
            val intent = Intent(context, GameDownloadService::class.java).apply {
                putExtra(EXTRA_BRANCH, branch)
                putExtra(EXTRA_PASSWORD, password)
                putExtra(EXTRA_INSTALL_DIR, installDir)
                putExtra(EXTRA_OS, os)
                putExtra(EXTRA_VERIFY, verify)
                putExtra(EXTRA_BUILD_ID, expectedBuildId)
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
    private val failedItems = AtomicBoolean(false)
    // Monotonic progress probes: status text cannot hide a zero-byte stalled transfer.
    private val lastDepotContentMs = AtomicLong(0L)
    private val depotContentObserved = AtomicBoolean(false)

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
        val expectedBuildId = intent.getLongExtra(EXTRA_BUILD_ID, 0L)

        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            runDownload(branch, password, installDir, os, verify, expectedBuildId)
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
        verify: Boolean,
        expectedBuildId: Long
    ) {
        try {
            // This verifies PICS branch identity, NOT actual license/depot entitlement.
            require(verify && expectedBuildId > 0L) { "Unverified Steam build selection" }
            check(clientManager.isLoggedIn) { "Steam sign-in required" }
            val content = SteamContentService(clientManager)
            var sessionManifestFingerprint: String? = null
            var sessionDepotManifestIds: Map<Int, String>? = null
            suspend fun recheckBranch() {
                val selected = content.getAppBranches().branches.firstOrNull { it.name == branch }
                M24SteamSelectionPolicy.requireBound(clientManager.isLoggedIn, expectedBuildId, selected?.buildId)
                val fingerprint = selected?.manifestFingerprint
                    ?: error("PICS branch has no usable depot manifest IDs; no live transfer permitted")
                if (sessionManifestFingerprint != null) {
                    check(sessionManifestFingerprint == fingerprint) {
                        "Steam depot manifest identity changed during download; staged bytes preserved"
                    }
                }
                val directIds = selected?.depotManifestIds ?: emptyMap()
                check(directIds.isNotEmpty() && directIds.size == selected?.depotCount) {
                    "PICS depot manifest identities missing"
                }
                val sharedSources = selected?.sharedDepotFromApps ?: emptyMap()
                // Parent GIDs must be obtained independently from fresh authenticated
                // Steam PICS; JavaSteam's cached .manifest/.sha is not an authority.
                val inheritedIds = content.resolveSharedDepotManifests(branch, sharedSources)
                val ids = M24SharedDepotBinding.merge(directIds, sharedSources, inheritedIds)
                if (sessionDepotManifestIds != null) {
                    check(sessionDepotManifestIds == ids) { "PICS depot manifests changed" }
                }
                sessionDepotManifestIds = ids
                sessionManifestFingerprint = fingerprint
            }
            recheckBranch()
            val installRoot = File(installDir)
            // Recheck the production destination independently of the UI and
            // manager. A direct service intent is never an installation authority.
            val recovery = GameDownloadRecovery(filesDir)
            val debugAlternative = DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(this) &&
                !CinderboxDownloadDestination.isExactDestination(installRoot.path)
            val scope = M23CinderboxFirstPolicy.assess(installRoot, File(CinderboxPaths.ROOT_DIR))
            val ownedUpdate = recovery.pending()?.takeIf { it.updateMode }
            if (ownedUpdate != null) {
                check(R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED &&
                    R43BLiveUpdatePolicy.DEVICE_ACCEPTED && !debugAlternative &&
                    installRoot.path == CinderboxPaths.GAME_FILES_DIR) {
                    "Unqualified or redirected update service intent"
                }
                // Service intents do not bypass the R43-C game-launch interlock.
                // No device-qualified exclusion protocol exists: this fails closed.
                R43CUpdateSafety.requireProductionQuiescence()
                check(R43BUpdateAdmission.inspectOwnedStage(filesDir, ownedUpdate).eligible) {
                    "Existing GameFiles changed after update session creation"
                }
            } else {
                if (debugAlternative) {
                    check(scope.code != M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE &&
                    scope.code != M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION &&
                    scope.code != M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE) {
                    "Debug destination is protected, unsafe or unreadable"
                }
            } else {
                val promoted = runCatching { recovery.isPromotedStage(installRoot) }.getOrDefault(false)
                CinderboxDownloadDestination.requireWritableForInstall(installRoot, promoted)
                }
            }
            StoragePreflight.requireWritable(installRoot)
            // Only the manager's checkpoint, matching target, Steam options and
            // ownership marker authorize resuming this exact download.
            recovery.requireMatching(installRoot, branch, os, verify)
            recovery.requireSteamBuildId(expectedBuildId)
            val manifestFingerprint = checkNotNull(sessionManifestFingerprint)
            recovery.bindDepotManifestFingerprint(manifestFingerprint)
            recovery.requireDepotManifestFingerprint(manifestFingerprint)
            if (recovery.isPromotedStage(installRoot)) {
                // Crash occurred after atomic rename, before final ledger/journal
                // commit. Rehash the installed bytes without contacting downloader.
                verifyPromotedFiles(installRoot, checkNotNull(sessionDepotManifestIds))
                return
            }
            val stageRoot = recovery.requireActiveStage(installRoot)
            GameDownloadManager._progress.value = DownloadProgress(state = DownloadState.PREPARING)
            updateNotification(getString(R.string.download_preparing))

            val licenses = clientManager.licenses.value
            if (licenses.isEmpty()) {
                throw IllegalStateException(getString(R.string.download_error_no_licenses))
            }

            // VZipUtil allocates an 8MB ThreadLocal per decompress thread, plus ~2MB chunk data.
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val heapBytes = am.largeMemoryClass.toLong() * 1024 * 1024
            val decompressCostBytes = 10L * 1024 * 1024

            // Tighter budget on low-heap devices where fragmentation limits contiguous allocation.
            val heapBudgetRatio = when {
                heapBytes >= 1024L * 1024 * 1024 -> 0.25 // 1GB+ heap: 25% budget
                heapBytes >= 768L * 1024 * 1024 -> 0.15 // 768MB heap: 15% budget
                else -> 0.08 // <=512MB heap: 8% budget (~41MB, ~4 threads max)
            }
            val maxDecompressByMem = (heapBytes * heapBudgetRatio / decompressCostBytes).toInt().coerceAtLeast(1)
            val maxDecompressByCpu = (cpuCores * 0.5).toInt().coerceIn(1, 4)
            val maxDecompress = minOf(maxDecompressByMem, maxDecompressByCpu)
            val maxDownloads = (cpuCores * 1.0).toInt().coerceIn(2, 8)
            val maxFileWrites = maxDecompress.coerceIn(1, 4)

            AppLogger.d(
                TAG,
                "Heap: ${am.largeMemoryClass}MB, CPU: $cpuCores cores → " +
                    "downloads=$maxDownloads, decompress=$maxDecompress, writes=$maxFileWrites"
            )

            val downloader = DepotDownloader(
                steamClient = clientManager.client,
                licenses = licenses,
                debug = false,
                maxDownloads = maxDownloads,
                maxDecompress = maxDecompress,
                maxFileWrites = maxFileWrites,
                parentJob = currentCoroutineContext()[Job]
            )
            depotDownloader = downloader

            lastSpeedUpdateMs.set(System.currentTimeMillis())
            lastSpeedBytes.set(0L)
            depotBytes.clear()
            failedItems.set(false)
            depotContentObserved.set(false)
            lastDepotContentMs.set(SystemClock.elapsedRealtime())
            val completedFiles = Collections.synchronizedSet(mutableSetOf<String>())
            val completedDepots = Collections.synchronizedSet(mutableSetOf<Int>())

            downloader.addListener(object : IDownloadListener {
                override fun onDownloadStarted(item: DownloadItem) {
                    try {
                        AppLogger.d(TAG, "Download started for item")
                        // A second item can start after other depots already transferred data.
                        GameDownloadManager._progress.update { it.copy(
                            state = DownloadState.DOWNLOADING,
                            depotPhase = "Locating Steam depots and requesting content…"
                        ) }
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
                        depotContentObserved.set(true)
                        lastDepotContentMs.set(SystemClock.elapsedRealtime())
                        // Callbacks are cumulative per depot and may arrive from parallel workers.
                        depotBytes.compute(depotId) { _, previous ->
                            maxOf(previous ?: 0L, uncompressedBytes.coerceAtLeast(0L))
                        }
                        val totalDownloaded = depotBytes.values.sum()
                        val depotPercent = if (depotPercentComplete.isFinite()) {
                            depotPercentComplete.coerceIn(0f, 1f)
                        } else 0f

                        val now = System.currentTimeMillis()
                        val lastUpdate = lastSpeedUpdateMs.get()
                        var speed = GameDownloadManager._progress.value.bytesPerSecond
                        if (now - lastUpdate >= 500) {
                            if (lastSpeedUpdateMs.compareAndSet(lastUpdate, now)) {
                                val prevBytes = lastSpeedBytes.getAndSet(totalDownloaded)
                                val elapsedMs = now - lastUpdate
                                val bytesDelta = totalDownloaded - prevBytes
                                speed = if (elapsedMs > 0) (bytesDelta.coerceAtLeast(0L) * 1000 / elapsedMs) else 0L
                            }
                        }

                        GameDownloadManager._progress.update { current ->
                            current.copy(
                                // Do not misrepresent one depot's progress as game-wide progress.
                                currentDepotId = depotId,
                                currentDepotPercent = depotPercent,
                                downloadedBytes = maxOf(current.downloadedBytes, totalDownloaded),
                                bytesPerSecond = speed
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in onChunkCompleted", e)
                    }
                }

                override fun onFileCompleted(depotId: Int, fileName: String, depotPercentComplete: Float) {
                    try {
                        depotContentObserved.set(true)
                        lastDepotContentMs.set(SystemClock.elapsedRealtime())
                        completedFiles.add(fileName)
                        GameDownloadManager._progress.update { current ->
                            current.copy(
                                currentFile = fileName,
                                filesDownloaded = completedFiles.size,
                                currentDepotId = depotId,
                                currentDepotPercent = if (depotPercentComplete.isFinite()) {
                                    depotPercentComplete.coerceIn(0f, 1f)
                                } else current.currentDepotPercent
                            )
                        }
                        updateNotification(getString(R.string.download_downloading))
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in onFileCompleted", e)
                    }
                }

                override fun onDepotCompleted(depotId: Int, compressedBytes: Long, uncompressedBytes: Long) {
                    try {
                        AppLogger.d(TAG, "Depot $depotId completed: ${uncompressedBytes / 1024 / 1024} MB")
                        depotContentObserved.set(true)
                        lastDepotContentMs.set(SystemClock.elapsedRealtime())
                        completedDepots.add(depotId)
                        depotBytes.compute(depotId) { _, previous ->
                            maxOf(previous ?: 0L, uncompressedBytes.coerceAtLeast(0L))
                        }
                        GameDownloadManager._progress.update { current ->
                            current.copy(downloadedBytes = maxOf(current.downloadedBytes, depotBytes.values.sum()))
                        }
                        // A completed depot does not reveal the total size of all depots.
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in onDepotCompleted", e)
                    }
                }

                override fun onDownloadCompleted(item: DownloadItem) {
                    AppLogger.d(TAG, "Download completed! (${completedFiles.size} files)")
                }

                override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                    failedItems.set(true)
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
                    // The SDK can include actual file paths in its status messages.
                    // Use an allowlist of static labels, never raw SDK status text.
                    val phase = M24DepotHandoffPolicy.safePhase(message) ?: return
                    GameDownloadManager._progress.update { it.copy(depotPhase = phase) }
                    AppLogger.d(TAG, "Depot phase: $phase")
                }
            })

            val appItem = AppItem(
                appId = 413150,
                installToGameNameDirectory = false,
                installDirectory = stageRoot.absolutePath,
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

            val waitStartMs = SystemClock.elapsedRealtime()
            downloader.add(appItem)
            downloader.finishAdding()
            // JavaSteam.awaitCompletion() calls CompletableFuture.join(), blocking
            // a coroutine thread and masking an SDK failure before completion.
            // Poll cancellably, detect SDK failure and missing bytes, then await
            // the exact same future to propagate any terminal exception.
            val completion = downloader.getCompletion()
            while (!completion.isDone) {
                currentCoroutineContext().ensureActive()
                M24DepotHandoffPolicy.failureCode(
                    SystemClock.elapsedRealtime(), waitStartMs, lastDepotContentMs.get(),
                    depotContentObserved.get(), failedItems.get()
                )?.let { code ->
                    AppLogger.w(TAG, "Steam depot handoff stopped: $code")
                    error(code)
                }
                delay(1_000L)
            }
            completion.await()

            // A per-item callback can report failure without throwing awaitCompletion().
            check(!failedItems.get() && GameDownloadManager._progress.value.state != DownloadState.ERROR) {
                "Steam reported a failed depot or file download"
            }
            // An interrupted network session or branch update must not be recorded
            // as a successful download even if the SDK returned from awaitCompletion.
            recheckBranch()
            AppLogger.d(TAG, "Download pipeline finished; PICS build rechecked")

            // Every M24 live transfer requires a local SHA-256 ledger. This does
            // not attest an independent signed Steam depot manifest.
            AppLogger.i(TAG, "M24 downloaded depot IDs=${completedDepots.toSortedSet().joinToString(",")}; " +
                "PICS-bound IDs=${checkNotNull(sessionDepotManifestIds).keys.sorted().joinToString(",")}")
            verifyFiles(installDir, stageRoot, completedFiles, ::recheckBranch,
                checkNotNull(sessionManifestFingerprint), checkNotNull(sessionDepotManifestIds),
                completedDepots.toSet(), refreshSelection = {
                    recheckBranch()
                    R43BUpdateCoordinator.Selection(
                        branch, os, expectedBuildId,
                        checkNotNull(sessionManifestFingerprint),
                        checkNotNull(sessionDepotManifestIds), completedDepots.toSet())
                })
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

    private fun verifyingStage(phase: String, done: Int = 0, total: Int = 0,
                               name: String = "") {
        GameDownloadManager._progress.update { current ->
            current.copy(state = DownloadState.VERIFYING, verificationPhase = phase,
                verifiedFiles = done, totalFilesToVerify = total, currentFile = name,
                // This is a per-stage percentage, never overall verification.
                overallPercent = if (total > 0) done.toFloat() / total else 0f)
        }
    }

    private suspend fun verifyPromotedFiles(destination: File, depotIds: Map<Int, String>,
        completedDepots: Set<Int>? = null) {
        val activeJob = currentCoroutineContext()[Job]
        verifyingStage("Rechecking published Steam depot files")
        M24DepotManifestCrosscheck.verify(destination, depotIds, completedDepots) { done, total ->
            activeJob?.ensureActive()
            verifyingStage("Rechecking published Steam depot files", done, total)
        }
        verifyingStage("Writing final integrity receipt")
        // Interrupted after atomic promotion: no transfer or destructive replay.
        val report = GameFileIntegrity(filesDir).verifyAndRecord(destination,
            phaseProgress = { phase, done, total, name ->
                activeJob?.ensureActive()
                verifyingStage("Final integrity: $phase", done, total, name)
            })
        check(report.passed) { "Promoted files failed local SHA-256 verification" }
        verifyingStage("Completing recovery journal")
        GameDownloadRecovery(filesDir).completed()
        GameDownloadManager._progress.value = DownloadProgress(
            state = DownloadState.COMPLETED, overallPercent = 1f,
            verifiedFiles = report.files, totalFilesToVerify = report.files,
            verificationPassed = true,
            integrityScope = "PICS_BOUND_DEPOT_SHA1_UNSIGNED"
        )
    }

    /** The number shown during verification belongs to the current hashing step,
     * not to the entire integrity/publication pipeline. Stage bytes are preserved
     * on any failure and only an absent target may be atomically published.
     */
    private suspend fun verifyFiles(installDir: String, stageRoot: File,
        completedFiles: Set<String>, recheckBranch: suspend () -> Unit,
        expectedManifestFingerprint: String, depotIds: Map<Int, String>,
        completedDepots: Set<Int>,
        refreshSelection: suspend () -> R43BUpdateCoordinator.Selection) {
        val root = stageRoot
        verifyingStage("Checking staged files")
        updateNotification("Verifying staged files")
        val activeJob = currentCoroutineContext()[Job]
        val report = GameFileIntegrity(filesDir).verifyAndRecord(root,
            phaseProgress = { phase, done, total, name ->
                activeJob?.ensureActive()
                verifyingStage("Local integrity: $phase", done, total, name)
            }, persistLedger = false)
        if (!report.passed) {
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.ERROR, verificationPassed = false,
                verifiedFiles = report.files, totalFilesToVerify = report.files,
                verificationErrors = report.errors,
                errorMessage = "Local game file integrity verification failed"
            )
            updateNotification("File verification failed. Retry to repair this download.")
            return
        }
        verifyingStage("Checking Steam depot manifests")
        updateNotification("Checking Steam depot manifests")
        M24DepotManifestCrosscheck.verify(root, depotIds, completedDepots) { done, total ->
            activeJob?.ensureActive()
            verifyingStage("Checking Steam depot manifests", done, total)
        }
        // Fresh network PICS requests can be slow; never leave '100% complete'
        // displayed after the file-count phase finishes.
        verifyingStage("Rechecking Steam branch and depot identity")
        updateNotification("Rechecking Steam branch")
        recheckBranch()
        GameDownloadRecovery(filesDir).requireDepotManifestFingerprint(expectedManifestFingerprint)
        verifyingStage("Publishing verified files atomically")
        updateNotification("Publishing verified files")
        val checkpoint = GameDownloadRecovery(filesDir).pending()
        if (checkpoint?.updateMode == true) {
            check(R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED &&
                R43BLiveUpdatePolicy.DEVICE_ACCEPTED) {
                "Live update remains disabled pending device acceptance"
            }
            val selection = R43BUpdateCoordinator.Selection(
                checkpoint.branch, checkpoint.os, checkpoint.steamBuildId,
                expectedManifestFingerprint, depotIds, completedDepots)
            R43BUpdateCoordinator.forProduction(filesDir)
                .publishFromSteam(selection, refreshSelection)
            val installed = GameFileIntegrity(filesDir).verifyRecorded(File(installDir))
            check(installed.passed && installed.files > 0) {
                "Published update receipt did not verify"
            }
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.COMPLETED, overallPercent = 1f,
                verifiedFiles = installed.files, totalFilesToVerify = installed.files,
                verificationPassed = true,
                integrityScope = "PICS_BOUND_DEPOT_SHA1_UNSIGNED")
        } else {
            GameDownloadRecovery(filesDir).promoteStaged(File(installDir))
            verifyPromotedFiles(File(installDir), depotIds, completedDepots)
        }
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
            DownloadState.DOWNLOADING, DownloadState.PREPARING -> {
                // JavaSteam supplies a percentage for an individual depot, not
                // a trustworthy game-wide total. An indeterminate bar is truthful.
                builder.setProgress(0, 0, true)
            }
            DownloadState.VERIFYING -> {
                // The current step can reach 100% before manifest and publish checks.
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
