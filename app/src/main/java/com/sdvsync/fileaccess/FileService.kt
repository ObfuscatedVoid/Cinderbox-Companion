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

    override fun fileExists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            Log.e(TAG, "fileExists failed: $path", e)
            false
        }
    }

    override fun listDirectory(path: String): Array<String> {
        val dir = File(path)
        if (!dir.isDirectory) {
            throw RemoteException("Not a directory: $path")
        }
        val files = dir.listFiles() ?: return emptyArray()
        return files.map { file ->
            val prefix = if (file.isDirectory) "D|" else "F|"
            "$prefix${file.name}"
        }.toTypedArray()
    }

    override fun readFileChunk(path: String, offset: Long, length: Int): ByteArray {
        return try {
            val raf = RandomAccessFile(path, "r")
            val bytes = ByteArray(length)
            raf.seek(offset)
            val bytesRead = raf.read(bytes, 0, length)
            raf.close()
            if (bytesRead < length) bytes.copyOf(bytesRead) else bytes
        } catch (e: Exception) {
            Log.e(TAG, "readFileChunk failed: $path", e)
            throw RemoteException(e.message)
        }
    }

    override fun getFileSize(path: String): Long {
        val file = File(path)
        if (!file.isFile) return -1
        return file.length()
    }

    override fun writeFileChunk(path: String, data: ByteArray, offset: Long) {
        try {
            File(path).parentFile?.mkdirs()
            val raf = RandomAccessFile(path, "rw")
            if (offset == 0L) raf.setLength(0) // Truncate if writing from start
            raf.seek(offset)
            raf.write(data)
            raf.close()
        } catch (e: Exception) {
            Log.e(TAG, "writeFileChunk failed: $path", e)
            throw RemoteException(e.message)
        }
    }

    override fun deleteFile(path: String) {
        try {
            val file = File(path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile failed: $path", e)
            throw RemoteException(e.message)
        }
    }

    override fun createDirectory(path: String) {
        try {
            File(path).mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "createDirectory failed: $path", e)
            throw RemoteException(e.message)
        }
    }

    override fun renameFile(fromPath: String, toPath: String): Boolean {
        return try {
            File(fromPath).renameTo(File(toPath))
        } catch (e: Exception) {
            Log.e(TAG, "renameFile failed: $fromPath -> $toPath", e)
            false
        }
    }
}
