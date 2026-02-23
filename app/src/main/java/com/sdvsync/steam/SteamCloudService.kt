package com.sdvsync.steam

import android.util.Log
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Date

data class CloudFile(
    val filename: String,
    val size: Int,
    val sha: ByteArray,
    val timestamp: Long,
    val pathPrefix: String,
) {
    /** Full path including prefix, e.g. "%WinAppDataRoaming%StardewValley/Saves/CHAD_419795178/SaveGameInfo" */
    val fullPath: String get() {
        if (pathPrefix.isEmpty()) return filename
        // Prefix already ends with "/" — don't add another
        return if (pathPrefix.endsWith("/")) "$pathPrefix$filename" else "$pathPrefix/$filename"
    }

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

class SteamCloudService(
    private val clientManager: SteamClientManager,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "SteamCloud"
        const val STARDEW_APP_ID = 413150
    }

    /**
     * Enumerate all cloud files for Stardew Valley.
     * Uses getAppFileListChange with syncedChangeNumber=0 to get ALL files.
     */
    suspend fun listCloudFiles(): List<CloudFile> = withContext(Dispatchers.IO) {
        val cloud = clientManager.cloud

        Log.d(TAG, "Requesting cloud file list for AppID $STARDEW_APP_ID...")
        val changeList = cloud.getAppFileListChange(STARDEW_APP_ID, 0).await()

        Log.d(TAG, "Cloud response: changeNumber=${changeList.currentChangeNumber}, " +
                "files=${changeList.files.size}, isOnlyDelta=${changeList.isOnlyDelta}, " +
                "pathPrefixes=${changeList.pathPrefixes}, machineNames=${changeList.machineNames}")

        changeList.files.map { file ->
            val prefix = if (file.pathPrefixIndex in changeList.pathPrefixes.indices) {
                changeList.pathPrefixes[file.pathPrefixIndex]
            } else ""

            Log.d(TAG, "  File: prefix='$prefix' filename='${file.filename}' " +
                    "size=${file.rawFileSize} prefixIdx=${file.pathPrefixIndex}")

            CloudFile(
                filename = file.filename,
                size = file.rawFileSize,
                sha = file.shaFile,
                timestamp = file.timestamp.time,
                pathPrefix = prefix,
            )
        }
    }

    /**
     * Group cloud files into save folders.
     * Returns map of saveFolderName -> list of CloudFiles.
     */
    suspend fun listCloudSaves(): Map<String, List<CloudFile>> {
        return listCloudFiles()
            .filter { it.saveFolderName != null }
            .groupBy { it.saveFolderName!! }
    }

    /**
     * Download a single file from Steam Cloud.
     * Returns the raw file bytes.
     */
    suspend fun downloadFile(
        filename: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val cloud = clientManager.cloud

        val downloadInfo = cloud.clientFileDownload(
            STARDEW_APP_ID,
            filename,
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

        response.body?.bytes() ?: throw RuntimeException("Empty response body")
    }

    /**
     * Download all files for a specific save folder.
     * Returns map of filename -> bytes.
     */
    suspend fun downloadSave(
        saveFolderName: String,
        onProgress: ((downloaded: Int, total: Int) -> Unit)? = null,
    ): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val allFiles = listCloudFiles()
        val saveFiles = allFiles.filter { it.saveFolderName == saveFolderName }

        if (saveFiles.isEmpty()) {
            throw RuntimeException("No files found for save: $saveFolderName")
        }

        val result = mutableMapOf<String, ByteArray>()
        saveFiles.forEachIndexed { index, file ->
            onProgress?.invoke(index, saveFiles.size)
            val data = downloadFile(file.fullPath)
            result[file.filename] = data
        }
        onProgress?.invoke(saveFiles.size, saveFiles.size)
        result
    }

    /**
     * Upload all files for a save to Steam Cloud.
     * Files map: filename (relative to save folder) -> file bytes.
     */
    suspend fun uploadSave(
        saveFolderName: String,
        files: Map<String, ByteArray>,
        onProgress: ((uploaded: Int, total: Int) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val cloud = clientManager.cloud

        val filePaths = files.keys.map { "$saveFolderName/$it" }

        // Begin batch
        val batchResponse = cloud.beginAppUploadBatch(
            appId = STARDEW_APP_ID,
            machineName = "SDV-Sync-Android",
            filesToUpload = filePaths,
            filesToDelete = emptyList(),
            clientId = 0L,
            appBuildId = 0L,
        ).await()

        val batchId = batchResponse.batchID

        try {
            var uploadedCount = 0
            for ((filename, data) in files) {
                val fullPath = "$saveFolderName/$filename"
                val sha = sha1(data)

                onProgress?.invoke(uploadedCount, files.size)

                // beginFileUpload parameter order:
                // appId: Int, fileSize: Int, rawFileSize: Int, fileSha: ByteArray,
                // timestamp: Date, filename: String, ..., uploadBatchId: Long
                val uploadInfo = cloud.beginFileUpload(
                    appId = STARDEW_APP_ID,
                    fileSize = data.size,
                    rawFileSize = data.size,
                    fileSha = sha,
                    timestamp = Date(),
                    filename = fullPath,
                    uploadBatchId = batchId,
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
                    filename = fullPath,
                ).await()

                if (!committed) {
                    throw RuntimeException("Failed to commit upload for $fullPath")
                }

                uploadedCount++
            }

            onProgress?.invoke(files.size, files.size)

            // Complete the batch
            cloud.completeAppUploadBatch(
                appId = STARDEW_APP_ID,
                batchId = batchId,
                batchEResult = EResult.OK,
            ).await()

        } catch (e: Exception) {
            // Always complete batch, even on failure
            try {
                cloud.completeAppUploadBatch(
                    appId = STARDEW_APP_ID,
                    batchId = batchId,
                    batchEResult = EResult.Fail,
                ).await()
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
            throw e
        }
    }

    private fun sha1(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-1").digest(data)
    }
}
