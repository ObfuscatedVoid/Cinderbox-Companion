package com.sdvsync.fileaccess

import android.os.RemoteException
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import kotlin.system.exitProcess

/**
 * Shizuku UserService implementation that runs in a privileged process.
 * Has shell (UID 2000) or root (UID 0) privileges depending on how Shizuku was started.
 * Can access /storage/emulated/0/Android/data/ which is blocked by scoped storage.
 */
class FileService : IFileService.Stub() {

    companion object {
        private const val TAG = "ShizukuFileService"
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun fileExists(path: String): Boolean = try {
        File(path).exists()
    } catch (e: Exception) {
        Log.e(TAG, "fileExists failed: $path", e)
        false
    }

    override fun listDirectory(path: String): Array<String> {
        val dir = File(path)
        if (!dir.isDirectory) {
            throw RemoteException("Not a directory: $path")
        }
        val files = dir.listFiles() ?: throw RemoteException("Cannot read directory: $path")
        return files.map { file ->
            val prefix = if (file.isDirectory) "D|" else "F|"
            "$prefix${file.name}"
        }.toTypedArray()
    }

    override fun readFileChunk(path: String, offset: Long, length: Int): ByteArray = try {
        require(offset >= 0 && length in 0..(512 * 1024)) { "Invalid read range" }
        RandomAccessFile(path, "r").use { raf ->
            val bytes = ByteArray(length)
            raf.seek(offset)
            val bytesRead = raf.read(bytes)
            if (bytesRead < 0) byteArrayOf() else bytes.copyOf(bytesRead)
        }
    } catch (e: Exception) {
        Log.e(TAG, "readFileChunk failed: $path", e)
        throw RemoteException(e.message)
    }

    override fun getFileSize(path: String): Long {
        val file = File(path)
        if (!file.isFile) return -1
        return file.length()
    }

    override fun writeFileChunk(path: String, data: ByteArray, offset: Long) {
        try {
            File(path).parentFile?.mkdirs()
            require(offset >= 0) { "Invalid write offset" }
            RandomAccessFile(path, "rw").use { raf ->
                if (offset == 0L) raf.setLength(0)
                raf.seek(offset)
                raf.write(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeFileChunk failed: $path", e)
            throw RemoteException(e.message)
        }
    }

    override fun deleteFile(path: String) {
        try {
            val file = File(path)
            check(!file.exists() || if (file.isDirectory) file.deleteRecursively() else file.delete()) {
                "Cannot delete file"
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile failed: $path", e)
            throw RemoteException(e.message)
        }
    }

    override fun createDirectory(path: String) {
        try {
            val directory = File(path)
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create directory" }
        } catch (e: Exception) {
            Log.e(TAG, "createDirectory failed: $path", e)
            throw RemoteException(e.message)
        }
    }

    override fun renameFile(fromPath: String, toPath: String): Boolean = try {
        File(fromPath).renameTo(File(toPath))
    } catch (e: Exception) {
        Log.e(TAG, "renameFile failed: $fromPath -> $toPath", e)
        false
    }
}
