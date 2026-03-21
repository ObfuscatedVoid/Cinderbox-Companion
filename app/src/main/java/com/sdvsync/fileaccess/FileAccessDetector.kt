package com.sdvsync.fileaccess

import android.content.Context
import android.os.Build
import android.os.Environment
import com.sdvsync.logging.AppLogger

class FileAccessDetector(private val context: Context) {

    companion object {
        private const val TAG = "FileAccessDetector"
        private const val PREF_NAME = "file_access"
        private const val KEY_PREFERRED = "preferred_strategy"
        private const val KEY_CINDERBOX_MODE = "cinderbox_mode"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_SETUP_TYPE = "setup_type"
    }

    /**
     * Detect the best available file access strategy.
     * Priority: Root > Shizuku > All Files > SAF > Manual
     */
    fun detectBestStrategy(): FileAccessStrategy {
        if (RootFileAccess.isAvailable()) {
            AppLogger.d(TAG, "detectBestStrategy: selected Root")
            return RootFileAccess()
        }
        if (ShizukuFileAccess.isAvailable()) {
            AppLogger.d(TAG, "detectBestStrategy: selected Shizuku")
            return ShizukuFileAccess()
        }
        if (AllFilesAccess.isAvailable()) {
            AppLogger.d(TAG, "detectBestStrategy: selected All Files")
            return AllFilesAccess()
        }
        SAFFileAccess.createInstance(context)?.let {
            AppLogger.d(TAG, "detectBestStrategy: selected ${it.name}")
            return it
        }
        AppLogger.d(TAG, "detectBestStrategy: falling back to Manual")
        return ManualFileAccess()
    }

    /**
     * Resolve strategy: use user preference if set and available, else auto-detect.
     */
    fun resolveStrategy(): FileAccessStrategy {
        val preferred = getPreferredStrategy() ?: return detectBestStrategy()
        val available = availableMethods()
        if (available.any { it.equals(preferred, ignoreCase = true) }) {
            AppLogger.d(TAG, "resolveStrategy: using preferred '$preferred'")
            return getStrategy(preferred)
        }
        AppLogger.d(TAG, "resolveStrategy: preferred '$preferred' not available, auto-detecting")
        return detectBestStrategy()
    }

    /**
     * Get a specific strategy by name.
     */
    fun getStrategy(name: String): FileAccessStrategy = when (name.lowercase()) {
        "root" -> RootFileAccess()
        "shizuku" -> ShizukuFileAccess()
        "all files" -> AllFilesAccess()
        "saf", "saf (staging)" -> SAFFileAccess.createInstance(context) ?: ManualFileAccess()
        "manual" -> ManualFileAccess()
        else -> detectBestStrategy()
    }

    /**
     * Check which access methods are currently available.
     */
    fun availableMethods(): List<String> {
        val methods = mutableListOf<String>()
        if (RootFileAccess.isAvailable()) methods.add("Root")
        if (ShizukuFileAccess.isAvailable()) methods.add("Shizuku")
        if (AllFilesAccess.isAvailable()) methods.add("All Files")
        if (SAFFileAccess.isAvailable(context)) methods.add("SAF")
        methods.add("Manual") // Always available as fallback
        return methods
    }

    fun getPreferredStrategy(): String? = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PREFERRED, null)

    fun setPreferredStrategy(name: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().apply {
                if (name == null) {
                    remove(KEY_PREFERRED)
                } else {
                    putString(KEY_PREFERRED, name)
                }
            }.apply()
    }

    fun isCinderboxMode(): Boolean = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_CINDERBOX_MODE, true)

    fun setCinderboxMode(enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CINDERBOX_MODE, enabled).apply()
    }

    fun isSetupCompleted(): Boolean = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_SETUP_COMPLETED, false)

    fun setSetupCompleted(type: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .putString(KEY_SETUP_TYPE, type)
            .apply()
    }

    fun getSetupType(): String? = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .getString(KEY_SETUP_TYPE, null)

    fun clearSetupChoice() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SETUP_COMPLETED)
            .remove(KEY_SETUP_TYPE)
            .apply()
    }

    /**
     * Whether the current configuration needs MANAGE_EXTERNAL_STORAGE but doesn't have it.
     * Cinderbox path (/storage/emulated/0/StardewValley/Saves) is external storage
     * and requires this permission on Android 11+.
     */
    fun needsStoragePermission(cinderboxMode: Boolean = isCinderboxMode()): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        if (!cinderboxMode) return false
        if (Environment.isExternalStorageManager()) return false
        if (RootFileAccess.isAvailable() || ShizukuFileAccess.isAvailable()) return false
        return true
    }
}
