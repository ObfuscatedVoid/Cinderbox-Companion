package com.sdvsync.download

import android.content.Context
import com.sdvsync.BuildConfig
import com.sdvsync.logging.AppLogger
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class AppUpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val releaseChecker: GitHubReleaseChecker
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val PREFS_NAME = "app_update"
        private const val KEY_UPDATE_CHECK_ENABLED = "update_check_enabled"
        private const val KEY_SKIPPED_VERSION = "skipped_version"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isUpdateCheckEnabled(): Boolean = prefs.getBoolean(KEY_UPDATE_CHECK_ENABLED, true)

    fun setUpdateCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_CHECK_ENABLED, enabled).apply()
    }

    fun getSkippedVersion(): String? = prefs.getString(KEY_SKIPPED_VERSION, null)

    fun setSkippedVersion(version: String?) {
        prefs.edit().apply {
            if (version == null) remove(KEY_SKIPPED_VERSION) else putString(KEY_SKIPPED_VERSION, version)
        }.apply()
    }

    fun shouldShowUpdate(latestVersion: String): Boolean {
        if (!isUpdateCheckEnabled()) return false
        if (!releaseChecker.isUpdateAvailable(BuildConfig.VERSION_NAME, latestVersion)) return false
        if (latestVersion == getSkippedVersion()) return false
        return true
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun downloadUpdate(assetUrl: String, assetName: String, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val destFile = File(context.cacheDir, assetName)
            try {
                val request = Request.Builder().url(assetUrl).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    AppLogger.e(TAG, "HTTP ${response.code} downloading $assetName")
                    return@withContext null
                }

                val body = response.body ?: run {
                    response.close()
                    AppLogger.e(TAG, "Empty response body for $assetName")
                    return@withContext null
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                val buffer = ByteArray(64 * 1024)

                destFile.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                onProgress(downloadedBytes.toFloat() / totalBytes)
                            }
                        }
                    }
                }

                AppLogger.i(TAG, "App update downloaded: ${destFile.length()} bytes")
                destFile
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "App update download failed", e)
                destFile.delete()
                null
            }
        }
}
