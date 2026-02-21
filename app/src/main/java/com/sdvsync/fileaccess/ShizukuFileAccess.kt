package com.sdvsync.fileaccess

import java.io.File

/**
 * File access using the Shizuku framework.
 * Requires Shizuku app to be installed and running.
 * Provides system-level file access without root.
 *
 * TODO: Implement in Phase 5. This is a placeholder.
 */
class ShizukuFileAccess : FileAccessStrategy {

    override val name = "Shizuku"

    override suspend fun exists(file: File): Boolean {
        // TODO: Implement using Shizuku IPC
        throw NotImplementedError("Shizuku file access not yet implemented")
    }

    override suspend fun listDirectories(dir: File): List<String>? {
        throw NotImplementedError("Shizuku file access not yet implemented")
    }

    override suspend fun listFiles(dir: File): List<String>? {
        throw NotImplementedError("Shizuku file access not yet implemented")
    }

    override suspend fun readFile(file: File): ByteArray? {
        throw NotImplementedError("Shizuku file access not yet implemented")
    }

    override suspend fun writeFile(file: File, data: ByteArray): Boolean {
        throw NotImplementedError("Shizuku file access not yet implemented")
    }

    override suspend fun deleteFile(file: File): Boolean {
        throw NotImplementedError("Shizuku file access not yet implemented")
    }

    override suspend fun renameFile(from: File, to: File): Boolean {
        throw NotImplementedError("Shizuku file access not yet implemented")
    }

    override suspend fun mkdirs(dir: File): Boolean {
        throw NotImplementedError("Shizuku file access not yet implemented")
    }

    companion object {
        fun isAvailable(): Boolean {
            // TODO: Check if Shizuku is installed and running
            return false
        }
    }
}
