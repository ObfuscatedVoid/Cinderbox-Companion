package com.sdvsync.fileaccess

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File access using root (su) shell commands.
 * Bypasses Android 13+ scoped storage restrictions.
 */
class RootFileAccess : FileAccessStrategy {

    override val name = "Root"

    override suspend fun exists(file: File): Boolean = withContext(Dispatchers.IO) {
        execRoot("test -e '${file.absolutePath}' && echo 'yes' || echo 'no'")
            .trim() == "yes"
    }

    override suspend fun listDirectories(dir: File): List<String>? = withContext(Dispatchers.IO) {
        val output = execRoot("ls -1 '${dir.absolutePath}' 2>/dev/null")
        if (output.isBlank()) return@withContext null

        // Filter to only directories
        output.lines()
            .filter { it.isNotBlank() }
            .filter { name ->
                val check = execRoot("test -d '${dir.absolutePath}/$name' && echo 'yes' || echo 'no'")
                check.trim() == "yes"
            }
    }

    override suspend fun listFiles(dir: File): List<String>? = withContext(Dispatchers.IO) {
        val output = execRoot("ls -1 '${dir.absolutePath}' 2>/dev/null")
        if (output.isBlank()) return@withContext null

        output.lines()
            .filter { it.isNotBlank() }
            .filter { name ->
                val check = execRoot("test -f '${dir.absolutePath}/$name' && echo 'yes' || echo 'no'")
                check.trim() == "yes"
            }
    }

    override suspend fun readFile(file: File): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat '${file.absolutePath}'"))
            val data = process.inputStream.readBytes()
            val exitCode = process.waitFor()
            if (exitCode == 0 && data.isNotEmpty()) data else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun writeFile(file: File, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Write to a temp location first, then move with su
                val tempFile = File.createTempFile("sdvsync_", ".tmp")
                tempFile.writeBytes(data)

                val result = execRoot(
                    "cp '${tempFile.absolutePath}' '${file.absolutePath}' && " +
                        "chmod 660 '${file.absolutePath}'"
                )
                tempFile.delete()

                // Verify write
                exists(file)
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        execRoot("rm -f '${file.absolutePath}'")
        !exists(file)
    }

    override suspend fun renameFile(from: File, to: File): Boolean = withContext(Dispatchers.IO) {
        execRoot("mv '${from.absolutePath}' '${to.absolutePath}'")
        exists(to)
    }

    override suspend fun mkdirs(dir: File): Boolean = withContext(Dispatchers.IO) {
        execRoot("mkdir -p '${dir.absolutePath}' && chmod 770 '${dir.absolutePath}'")
        exists(dir)
    }

    private fun execRoot(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        /**
         * Check if root access is available.
         */
        fun isAvailable(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output.contains("uid=0")
            } catch (e: Exception) {
                false
            }
        }
    }
}
