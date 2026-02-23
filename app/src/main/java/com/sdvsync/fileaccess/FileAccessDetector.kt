package com.sdvsync.fileaccess

import android.content.Context

class FileAccessDetector(private val context: Context) {

    companion object {
        private const val PREF_NAME = "file_access"
        private const val KEY_PREFERRED = "preferred_strategy"
    }

    /**
     * Detect the best available file access strategy.
     * Priority: Root > Shizuku > All Files > SAF > Manual
     */
    fun detectBestStrategy(): FileAccessStrategy {
        if (RootFileAccess.isAvailable()) {
            return RootFileAccess()
        }
        if (ShizukuFileAccess.isAvailable()) {
            return ShizukuFileAccess()
        }
        if (AllFilesAccess.isAvailable()) {
            return AllFilesAccess()
        }
        SAFFileAccess.createInstance(context)?.let { return it }
        return ManualFileAccess()
    }

    /**
     * Resolve strategy: use user preference if set and available, else auto-detect.
     */
    fun resolveStrategy(): FileAccessStrategy {
        val preferred = getPreferredStrategy() ?: return detectBestStrategy()
        val available = availableMethods()
        if (available.any { it.equals(preferred, ignoreCase = true) }) {
            return getStrategy(preferred)
        }
        return detectBestStrategy()
    }

    /**
     * Get a specific strategy by name.
     */
    fun getStrategy(name: String): FileAccessStrategy {
        return when (name.lowercase()) {
            "root" -> RootFileAccess()
            "shizuku" -> ShizukuFileAccess()
            "all files" -> AllFilesAccess()
            "saf", "saf (staging)" -> SAFFileAccess.createInstance(context) ?: ManualFileAccess()
            "manual" -> ManualFileAccess()
            else -> detectBestStrategy()
        }
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

    fun getPreferredStrategy(): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PREFERRED, null)
    }

    fun setPreferredStrategy(name: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().apply {
                if (name == null) remove(KEY_PREFERRED)
                else putString(KEY_PREFERRED, name)
            }.apply()
    }
}
