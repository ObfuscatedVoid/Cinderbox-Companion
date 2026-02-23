package com.sdvsync.fileaccess

import android.content.Context

class FileAccessDetector(private val context: Context) {

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
     * Get a specific strategy by name.
     */
    fun getStrategy(name: String): FileAccessStrategy {
        return when (name.lowercase()) {
            "root" -> RootFileAccess()
            "shizuku" -> ShizukuFileAccess()
            "all files" -> AllFilesAccess()
            "saf" -> SAFFileAccess.createInstance(context) ?: ManualFileAccess()
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
}
