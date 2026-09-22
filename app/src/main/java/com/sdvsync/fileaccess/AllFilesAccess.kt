package com.sdvsync.fileaccess

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.sdvsync.logging.AppLogger
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.util.MAX_SAVE_BYTES
import com.sdvsync.util.readLimited
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
        private const val TAG = "AllFilesAccess"

        /**
         * True when the permission is granted AND the save directory is actually accessible.
         * Some devices grant the permission but still block /Android/data/.
         */
        fun isAvailable(context: Context): Boolean {
            if (!isPermissionGranted(context)) return false
            val target = File(SaveFileManager.SDV_SAVE_PATH)
            return target.exists() && target.canRead()
        }

        /**
         * True when the MANAGE_EXTERNAL_STORAGE permission is granted,
         * regardless of whether the save directory is actually accessible.
         */
        fun isPermissionGranted(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
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

    override suspend fun readFile(file: File): ByteArray? = try {
        if (file.exists()) file.inputStream().use { it.readLimited(MAX_SAVE_BYTES) } else null
    } catch (e: Exception) {
        AppLogger.e(TAG, "readFile failed: ${file.absolutePath}", e)
        null
    }

    override suspend fun writeFile(file: File, data: ByteArray): Boolean = try {
        file.parentFile?.mkdirs()
        file.writeBytes(data)
        true
    } catch (e: Exception) {
        AppLogger.e(TAG, "writeFile failed: ${file.absolutePath}", e)
        false
    }

    override suspend fun deleteFile(file: File): Boolean = try {
        file.delete()
    } catch (e: Exception) {
        AppLogger.e(TAG, "deleteFile failed: ${file.absolutePath}", e)
        false
    }

    override suspend fun renameFile(from: File, to: File): Boolean = try {
        from.renameTo(to)
    } catch (e: Exception) {
        AppLogger.e(TAG, "renameFile failed: ${from.absolutePath}", e)
        false
    }

    override suspend fun mkdirs(dir: File): Boolean = try {
        dir.mkdirs()
    } catch (e: Exception) {
        AppLogger.e(TAG, "mkdirs failed: ${dir.absolutePath}", e)
        false
    }
}
