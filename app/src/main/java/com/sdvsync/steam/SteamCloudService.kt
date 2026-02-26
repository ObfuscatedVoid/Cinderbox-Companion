package com.sdvsync.steam

import com.sdvsync.logging.AppLogger
import com.sdvsync.util.GzipUtil
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.*
import java.security.MessageDigest
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class CloudFile(
    val filename: String,
    val size: Int,
    val sha: ByteArray,
    val timestamp: Long,
    val pathPrefix: String
) {
    /** Full path including prefix, e.g. "%WinAppDataRoaming%StardewValley/Saves/CHAD_419795178/SaveGameInfo" */
    val fullPath: String get() {
        if (pathPrefix.isEmpty()) return filename
        // Prefix already ends with "/" — don't add another
        return if (pathPrefix.endsWith("/")) "$pathPrefix$filename" else "$pathPrefix/$filename"
    }

    /** Just the file name without any directory components, e.g. "SaveGameInfo" */
    val baseName: String get() = filename.substringAfterLast("/")

    /** The save folder name (parent directory), e.g. "CHAD_419795178" */
    val saveFolderName: String? get() {
        val parts = fullPath.split("/")
        // Save folder is the second-to-last component (parent dir of the file)
        return if (parts.size >= 2) parts[parts.size - 2] else null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CloudFile) return false
        return filename == other.filename && fullPath == other.fullPath
    }

    override fun hashCode(): Int = fullPath.hashCode()
}

class SteamCloudService(private val clientManager: SteamClientManager, private val httpClient: OkHttpClient) {
    companion object {
        private const val TAG = "SteamCloud"
        const val STARDEW_APP_ID = 413150

        /** Default Steam Cloud path prefix for Stardew Valley saves (matches PC convention) */
        private const val SDV_CLOUD_PATH_PREFIX = "%WinAppDataRoaming%StardewValley/Saves/"
    }

    /**
     * Enumerate all cloud files for Stardew Valley.
     * Uses getAppFileListChange with syncedChangeNumber=0 to get ALL files.
     */
    suspend fun listCloudFiles(): List<CloudFile> = withContext(Dispatchers.IO) {
        val cloud = clientManager.cloud

        AppLogger.d(TAG, "Requesting cloud file list for AppID $STARDEW_APP_ID...")
        val changeList = cloud.getAppFileListChange(STARDEW_APP_ID, 0).await()

        AppLogger.d(
            TAG,
            "Cloud response: changeNumber=${changeList.currentChangeNumber}, " +
                "files=${changeList.files.size}, isOnlyDelta=${changeList.isOnlyDelta}, " +
                "pathPrefixes=${changeList.pathPrefixes}, machineNames=${changeList.machineNames}"
        )

        changeList.files.map { file ->
            val prefix = if (file.pathPrefixIndex in changeList.pathPrefixes.indices) {
                changeList.pathPrefixes[file.pathPrefixIndex]
            } else {
                ""
            }

            AppLogger.d(
                TAG,
                "  File: prefix='$prefix' filename='${file.filename}' " +
                    "size=${file.rawFileSize} prefixIdx=${file.pathPrefixIndex}"
            )

            CloudFile(
                filename = file.filename,
                size = file.rawFileSize,
                sha = file.shaFile,
                timestamp = file.timestamp.time,
                pathPrefix = prefix
            )
        }
    }

    /**
     * Group cloud files into save folders.
     * Returns map of saveFolderName -> list of CloudFiles.
     *
     * Deduplicates files with the same baseName within a save folder,
     * preferring the one with the canonical Stardew Valley path prefix
     * (contains "%WinAppDataRoaming%"). This handles orphaned files
     * from uploads that used the wrong path prefix.
     */
    suspend fun listCloudSaves(): Map<String, List<CloudFile>> = listCloudFiles()
        .filter { it.saveFolderName != null }
        .groupBy { it.saveFolderName!! }
        .mapValues { (_, files) ->
            files.groupBy { it.baseName }.map { (_, dupes) ->
                if (dupes.size == 1) {
                    dupes.first()
                } else {
                    // Prefer canonical prefix (%WinAppDataRoaming%), fall back to longest prefix
                    dupes.firstOrNull { it.pathPrefix.contains("%WinAppDataRoaming%") }
                        ?: dupes.maxByOrNull { it.pathPrefix.length }
                        ?: dupes.first()
                }
            }
        }

    /**
     * Download a single file from Steam Cloud.
     * Returns the raw file bytes.
     */
    suspend fun downloadFile(filename: String): ByteArray = withContext(Dispatchers.IO) {
        val cloud = clientManager.cloud

        val downloadInfo = cloud.clientFileDownload(
            STARDEW_APP_ID,
            filename
        ).await()

        val protocol = if (downloadInfo.useHttps) "https" else "http"
        val url = "$protocol://${downloadInfo.urlHost}${downloadInfo.urlPath}"

        val requestBuilder = Request.Builder().url(url).get()
        for (header in downloadInfo.requestHeaders) {
            requestBuilder.addHeader(header.name, header.value)
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Download failed: HTTP ${response.code}")
        }

        val rawBytes = response.body?.bytes() ?: throw RuntimeException("Empty response body")

        AppLogger.d(
            TAG,
            "Downloaded '$filename': ${rawBytes.size} bytes, " +
                "isGzip=${GzipUtil.isGzip(rawBytes)}, " +
                "first4=${rawBytes.take(4).joinToString(" ") { "%02x".format(it) }}"
        )

        // Stardew Valley 1.6+ saves are gzip-compressed XML.
        // Steam Cloud stores them as-is, so decompress for all callers.
        val result = GzipUtil.decompressIfGzip(rawBytes)
        AppLogger.d(
            TAG,
            "After decompression: ${result.size} bytes, " +
                "first50=${String(result, 0, minOf(50, result.size))}"
        )
        result
    }

    /**
     * Download all files for a specific save folder.
     * Returns map of baseName -> bytes (e.g. "SaveGameInfo" -> bytes).
     * Uses the deduped cloud file list to avoid downloading orphaned duplicates.
     */
    suspend fun downloadSave(
        saveFolderName: String,
        onProgress: ((downloaded: Int, total: Int) -> Unit)? = null
    ): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val cloudSaves = listCloudSaves()
        val saveFiles = cloudSaves[saveFolderName]

        if (saveFiles.isNullOrEmpty()) {
            throw RuntimeException("No files found for save: $saveFolderName")
        }

        val result = mutableMapOf<String, ByteArray>()
        saveFiles.forEachIndexed { index, file ->
            onProgress?.invoke(index, saveFiles.size)
            val data = downloadFile(file.fullPath)
            result[file.baseName] = data
        }
        onProgress?.invoke(saveFiles.size, saveFiles.size)
        result
    }

    /**
     * Upload all files for a save to Steam Cloud.
     * Files map: baseName (e.g. "SaveGameInfo") -> file bytes.
     *
     * @param pathPrefix The cloud path prefix for this save's files. If null, looks up
     *   existing cloud files to match their prefix, or falls back to the default
     *   Stardew Valley convention (%WinAppDataRoaming%StardewValley/Saves/saveFolderName/).
     */
    suspend fun uploadSave(
        saveFolderName: String,
        files: Map<String, ByteArray>,
        pathPrefix: String? = null,
        onProgress: ((uploaded: Int, total: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val cloud = clientManager.cloud

        // Determine the correct path prefix for uploads
        val effectivePrefix = pathPrefix
            ?: run {
                // Look up existing cloud files to match their prefix
                val existing = listCloudFiles()
                    .filter { it.saveFolderName == saveFolderName }
                existing.firstOrNull { it.pathPrefix.contains("%WinAppDataRoaming%") }?.pathPrefix
                    ?: existing.maxByOrNull { it.pathPrefix.length }?.pathPrefix
                    ?: "$SDV_CLOUD_PATH_PREFIX$saveFolderName/"
            }

        AppLogger.d(TAG, "uploadSave: using pathPrefix='$effectivePrefix' for $saveFolderName")

        // Construct full paths using the correct prefix
        val filePaths = files.keys.map { "$effectivePrefix$it" }

        // Find orphaned files with wrong prefix to clean up
        val orphanedPaths = if (pathPrefix == null) {
            // We already listed files above; find any with different prefix
            val allFiles = listCloudFiles()
            allFiles.filter { it.saveFolderName == saveFolderName && it.pathPrefix != effectivePrefix }
                .map { it.fullPath }
        } else {
            emptyList()
        }

        if (orphanedPaths.isNotEmpty()) {
            AppLogger.d(TAG, "uploadSave: will delete ${orphanedPaths.size} orphaned files: $orphanedPaths")
        }

        // Begin batch
        val batchResponse = cloud.beginAppUploadBatch(
            appId = STARDEW_APP_ID,
            machineName = "SDV-Sync-Android",
            filesToUpload = filePaths,
            filesToDelete = orphanedPaths,
            clientId = 0L,
            appBuildId = 0L
        ).await()

        val batchId = batchResponse.batchID
        AppLogger.d(TAG, "uploadSave: batch started, batchId=$batchId, files=${files.size}")

        try {
            var uploadedCount = 0
            for ((baseName, data) in files) {
                val fullPath = "$effectivePrefix$baseName"
                val sha = sha1(data)

                AppLogger.d(TAG, "uploadSave: uploading $fullPath (${data.size} bytes)")
                onProgress?.invoke(uploadedCount, files.size)

                val uploadInfo = cloud.beginFileUpload(
                    appId = STARDEW_APP_ID,
                    fileSize = data.size,
                    rawFileSize = data.size,
                    fileSha = sha,
                    timestamp = Date(),
                    filename = fullPath,
                    uploadBatchId = batchId
                ).await()

                // Upload each block via HTTP
                for (block in uploadInfo.blockRequests) {
                    val protocol = if (block.useHttps) "https" else "http"
                    val url = "$protocol://${block.urlHost}${block.urlPath}"

                    val blockData = if (block.blockLength < data.size) {
                        data.copyOfRange(
                            block.blockOffset.toInt(),
                            (block.blockOffset + block.blockLength).toInt()
                        )
                    } else {
                        data
                    }

                    val body = blockData.toRequestBody("application/octet-stream".toMediaType())
                    val requestBuilder = Request.Builder().url(url).put(body)
                    for (header in block.requestHeaders) {
                        requestBuilder.addHeader(header.name, header.value)
                    }

                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful) {
                        throw RuntimeException(
                            "Upload failed for $fullPath block: HTTP ${response.code}"
                        )
                    }
                    response.close()
                }

                // Commit the file upload
                val committed = cloud.commitFileUpload(
                    transferSucceeded = true,
                    appId = STARDEW_APP_ID,
                    fileSha = sha,
                    filename = fullPath
                ).await()

                if (!committed) {
                    throw RuntimeException("Failed to commit upload for $fullPath")
                }

                uploadedCount++
                AppLogger.d(TAG, "uploadSave: committed $fullPath ($uploadedCount/${files.size})")
            }

            onProgress?.invoke(files.size, files.size)

            // Complete the batch
            cloud.completeAppUploadBatch(
                appId = STARDEW_APP_ID,
                batchId = batchId,
                batchEResult = EResult.OK
            ).await()
            AppLogger.d(TAG, "uploadSave: batch completed successfully")
        } catch (e: Exception) {
            // Always complete batch, even on failure
            try {
                cloud.completeAppUploadBatch(
                    appId = STARDEW_APP_ID,
                    batchId = batchId,
                    batchEResult = EResult.Fail
                ).await()
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
            throw e
        }
    }

    private fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)
}
