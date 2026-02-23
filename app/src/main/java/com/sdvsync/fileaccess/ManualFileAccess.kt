package com.sdvsync.fileaccess

import com.sdvsync.logging.AppLogger
import java.io.File

/**
 * Fallback file access that uses standard filesystem operations.
 * Only works for paths the app has direct access to (e.g., app-private storage, Downloads).
 *
 * For the actual SDV save directory (Android/data/...), this won't work on Android 13+
 * without root or Shizuku. In manual mode, saves are placed in app-private storage
 * and the user copies them manually.
 */
class ManualFileAccess : FileAccessStrategy {

    companion object {
        private const val TAG = "ManualFileAccess"
    }

    override val name = "Manual"

    override suspend fun exists(file: File): Boolean = file.exists()

    override suspend fun listDirectories(dir: File): List<String>? {
        if (!dir.exists()) return null
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
    }

    override suspend fun listFiles(dir: File): List<String>? {
        if (!dir.exists()) return null
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
    }

    override suspend fun readFile(file: File): ByteArray? {
        return try {
            if (file.exists()) file.readBytes() else null
        } catch (e: Exception) {
            AppLogger.e(TAG, "readFile failed: ${file.absolutePath}", e)
            null
        }
    }

    override suspend fun writeFile(file: File, data: ByteArray): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "writeFile failed: ${file.absolutePath}", e)
            false
        }
    }

    override suspend fun deleteFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteFile failed: ${file.absolutePath}", e)
            false
        }
    }

    override suspend fun renameFile(from: File, to: File): Boolean {
        return try {
            from.renameTo(to)
        } catch (e: Exception) {
            AppLogger.e(TAG, "renameFile failed: ${from.absolutePath}", e)
            false
        }
    }

    override suspend fun mkdirs(dir: File): Boolean {
        return try {
            dir.mkdirs()
        } catch (e: Exception) {
            AppLogger.e(TAG, "mkdirs failed: ${dir.absolutePath}", e)
            false
        }
    }
}
