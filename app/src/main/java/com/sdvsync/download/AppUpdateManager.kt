package com.sdvsync.download

import android.content.Context
import androidx.core.content.edit
import com.sdvsync.BuildConfig
import com.sdvsync.logging.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

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
        prefs.edit { putBoolean(KEY_UPDATE_CHECK_ENABLED, enabled) }
    }

    fun getSkippedVersion(): String? = prefs.getString(KEY_SKIPPED_VERSION, null)

    fun setSkippedVersion(version: String?) {
        prefs.edit {
            if (version == null) remove(KEY_SKIPPED_VERSION) else putString(KEY_SKIPPED_VERSION, version)
        }
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
            com.sdvsync.saves.requireSafeSaveName(assetName)
            try {
                downloadToFile(httpClient, assetUrl, File(context.cacheDir, assetName)) { bytes, total ->
                    if (total > 0) onProgress(bytes.toFloat() / total)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "App update download failed", e)
                null
            }
        }
}
