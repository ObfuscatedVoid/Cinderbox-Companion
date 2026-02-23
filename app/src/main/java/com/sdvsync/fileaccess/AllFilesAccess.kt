package com.sdvsync.fileaccess

import android.os.Build
import android.os.Environment
import com.sdvsync.saves.SaveFileManager
import java.io.File

/**
 * File access strategy using MANAGE_EXTERNAL_STORAGE permission (Android 11+).
 *
 * On many OEM ROMs (Samsung, Xiaomi, OnePlus, etc.), this permission grants access
 * to /Android/data/ even though official Android docs say it shouldn't.
 * We verify actual access to the save directory before declaring availability.
 */
class AllFilesAccess : FileAccessStrategy {

    override val name = "All Files"

    companion object {
        /**
         * True when the permission is granted AND the save directory is actually accessible.
         * Some devices grant the permission but still block /Android/data/.
         */
        fun isAvailable(): Boolean {
            if (Build.VERSION.SDK_INT < 30) return false
            if (!Environment.isExternalStorageManager()) return false
            val target = File(SaveFileManager.SDV_SAVE_PATH)
            return target.exists() && target.canRead()
        }

        /**
         * True when the MANAGE_EXTERNAL_STORAGE permission is granted,
         * regardless of whether the save directory is actually accessible.
         */
        fun isPermissionGranted(): Boolean {
            if (Build.VERSION.SDK_INT < 30) return false
            return Environment.isExternalStorageManager()
        }
    }

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
            null
        }
    }

    override suspend fun writeFile(file: File, data: ByteArray): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun deleteFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun renameFile(from: File, to: File): Boolean {
        return try {
            from.renameTo(to)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun mkdirs(dir: File): Boolean {
        return try {
            dir.mkdirs()
        } catch (e: Exception) {
            false
        }
    }
}
