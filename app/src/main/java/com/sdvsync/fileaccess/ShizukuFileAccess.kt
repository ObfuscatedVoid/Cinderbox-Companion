package com.sdvsync.fileaccess

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.sdvsync.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

/**
 * File access using the Shizuku framework.
 * Runs a UserService with shell (UID 2000) or root (UID 0) privileges.
 * Can access /storage/emulated/0/Android/data/ without root.
 */
class ShizukuFileAccess : FileAccessStrategy {

    override val name = "Shizuku"

    companion object {
        private const val TAG = "ShizukuFileAccess"
        private const val CHUNK_SIZE = 51200 // 50KB - safe for Binder IPC

        @Volatile
        private var fileService: IFileService? = null

        private val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    fileService = IFileService.Stub.asInterface(binder)
                    AppLogger.d(TAG, "FileService connected")
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                fileService = null
                AppLogger.d(TAG, "FileService disconnected")
            }
        }

        private val userServiceArgs by lazy {
            Shizuku.UserServiceArgs(
                ComponentName(
                    "com.sdvsync",
                    FileService::class.java.name,
                )
            )
                .processNameSuffix("file_service")
                .version(1)
        }

        fun isAvailable(): Boolean {
            return try {
                Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        }

        fun isInstalled(packageManager: PackageManager): Boolean {
            return try {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        fun isRunning(): Boolean {
            return try {
                Shizuku.pingBinder()
            } catch (e: Exception) {
                false
            }
        }

        fun isPermissionGranted(): Boolean {
            return try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        }

        fun requestPermission(requestCode: Int = 1001) {
            try {
                Shizuku.requestPermission(requestCode)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to request permission", e)
            }
        }

        fun bindService() {
            try {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to bind service", e)
            }
        }

        fun unbindService() {
            try {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to unbind service", e)
            }
            fileService = null
        }

        fun isServiceBound(): Boolean {
            return fileService != null
        }
    }

    private fun requireService(): IFileService {
        return fileService ?: throw IllegalStateException(
            "Shizuku FileService not bound. Call ShizukuFileAccess.bindService() first."
        )
    }

    override suspend fun exists(file: File): Boolean = withContext(Dispatchers.IO) {
        requireService().fileExists(file.absolutePath)
    }

    override suspend fun listDirectories(dir: File): List<String>? = withContext(Dispatchers.IO) {
        try {
            val entries = requireService().listDirectory(dir.absolutePath)
            entries.filter { it.startsWith("D|") }
                .map { it.removePrefix("D|") }
                .ifEmpty { null }
        } catch (e: Exception) {
            AppLogger.e(TAG, "listDirectories failed: ${dir.absolutePath}", e)
            null
        }
    }

    override suspend fun listFiles(dir: File): List<String>? = withContext(Dispatchers.IO) {
        try {
            val entries = requireService().listDirectory(dir.absolutePath)
            entries.filter { it.startsWith("F|") }
                .map { it.removePrefix("F|") }
                .ifEmpty { null }
        } catch (e: Exception) {
            AppLogger.e(TAG, "listFiles failed: ${dir.absolutePath}", e)
            null
        }
    }

    override suspend fun readFile(file: File): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val service = requireService()
            val size = service.getFileSize(file.absolutePath)
            if (size <= 0) return@withContext null

            val result = ByteArray(size.toInt())
            var offset = 0L
            while (offset < size) {
                val chunkLen = minOf(CHUNK_SIZE.toLong(), size - offset).toInt()
                val chunk = service.readFileChunk(file.absolutePath, offset, chunkLen)
                chunk.copyInto(result, offset.toInt())
                offset += chunk.size
            }
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "readFile failed: ${file.absolutePath}", e)
            null
        }
    }

    override suspend fun writeFile(file: File, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val service = requireService()
                val path = file.absolutePath

                if (data.size <= CHUNK_SIZE) {
                    service.writeFileChunk(path, data, 0L)
                } else {
                    var offset = 0L
                    while (offset < data.size) {
                        val end = minOf(offset + CHUNK_SIZE, data.size.toLong())
                        val chunk = data.copyOfRange(offset.toInt(), end.toInt())
                        service.writeFileChunk(path, chunk, offset)
                        offset += chunk.size
                    }
                }
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "writeFile failed: ${file.absolutePath}", e)
                false
            }
        }

    override suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            requireService().deleteFile(file.absolutePath)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteFile failed: ${file.absolutePath}", e)
            false
        }
    }

    override suspend fun renameFile(from: File, to: File): Boolean = withContext(Dispatchers.IO) {
        try {
            requireService().renameFile(from.absolutePath, to.absolutePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "renameFile failed: ${from.absolutePath}", e)
            false
        }
    }

    override suspend fun mkdirs(dir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            requireService().createDirectory(dir.absolutePath)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "mkdirs failed: ${dir.absolutePath}", e)
            false
        }
    }
}
