package com.sdvsync.fileaccess

import android.content.Context

class FileAccessDetector(private val context: Context) {

    /**
     * Detect the best available file access strategy.
     * Priority: Root > Shizuku > Manual
     */
    fun detectBestStrategy(): FileAccessStrategy {
        if (RootFileAccess.isAvailable()) {
            return RootFileAccess()
        }
        if (ShizukuFileAccess.isAvailable()) {
            return ShizukuFileAccess()
        }
        return ManualFileAccess()
    }

    /**
     * Get a specific strategy by name.
     */
    fun getStrategy(name: String): FileAccessStrategy {
        return when (name.lowercase()) {
            "root" -> RootFileAccess()
            "shizuku" -> ShizukuFileAccess()
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
        methods.add("Manual") // Always available as fallback
        return methods
    }
}
