package com.sdvsync.fileaccess

import com.sdvsync.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootFileAccess(private val shell: List<String> = listOf("su", "-c")) : FileAccessStrategy {
    override val name = "Root"

    override suspend fun exists(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching { execute("test -e ${shellQuote(file.absolutePath)}").successful }.getOrDefault(false)
    }

    override suspend fun listDirectories(dir: File): List<String>? = listEntries(dir, "d")

    override suspend fun listFiles(dir: File): List<String>? = listEntries(dir, "f")

    private suspend fun listEntries(dir: File, type: String): List<String>? = withContext(Dispatchers.IO) {
        try {
            val result = execute("ls -1A ${shellQuote(dir.absolutePath)}")
            if (!result.successful) return@withContext null
            result.bytes.decodeToString().lineSequence().filter { it.isNotEmpty() }.filter { name ->
                execute("test -$type ${shellQuote(File(dir, name).absolutePath)}").successful
            }.toList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e(TAG, "Cannot list directory", e)
            null
        }
    }

    override suspend fun readFile(file: File): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val result = execute("cat ${shellQuote(file.absolutePath)}")
            if (result.successful) result.bytes else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e(TAG, "Cannot read file", e)
            null
        }
    }

    override suspend fun writeFile(file: File, data: ByteArray): Boolean = mutate {
        val temporary = File.createTempFile("sdvsync_", ".tmp")
        try {
            temporary.writeBytes(data)
            execute("cp ${shellQuote(temporary.absolutePath)} ${shellQuote(file.absolutePath)}").successful
        } finally {
            temporary.delete()
        }
    }

    override suspend fun deleteFile(file: File): Boolean = mutate {
        execute("rm -f ${shellQuote(file.absolutePath)}").successful
    }

    override suspend fun renameFile(from: File, to: File): Boolean = mutate {
        execute("mv ${shellQuote(from.absolutePath)} ${shellQuote(to.absolutePath)}").successful
    }

    override suspend fun mkdirs(dir: File): Boolean = mutate {
        execute("mkdir -p ${shellQuote(dir.absolutePath)}").successful
    }

    private suspend fun mutate(operation: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            operation()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e(TAG, "File operation failed", e)
            false
        }
    }

    private fun execute(command: String): ShellResult = runFileCommand(shell, command)

    companion object {
        private const val TAG = "RootFileAccess"

        fun isAvailable(): Boolean = try {
            val result = runFileCommand(listOf("su", "-c"), "id", timeoutSeconds = 2)
            result.successful && result.bytes.decodeToString().contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }
}
