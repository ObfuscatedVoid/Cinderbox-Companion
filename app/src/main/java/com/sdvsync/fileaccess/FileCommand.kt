package com.sdvsync.fileaccess

import com.sdvsync.util.MAX_SAVE_BYTES
import com.sdvsync.util.readLimited
import java.io.File
import java.util.concurrent.TimeUnit

internal fun shellQuote(value: String): String {
    require('\u0000' !in value) { "Invalid shell argument" }
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

internal data class ShellResult(val successful: Boolean, val bytes: ByteArray)

internal fun runFileCommand(shell: List<String>, command: String, timeoutSeconds: Long = 15): ShellResult {
    val output = File.createTempFile("sdvsync_stdout_", ".tmp")
    val errors = File.createTempFile("sdvsync_stderr_", ".tmp")
    var process: Process? = null
    try {
        process = ProcessBuilder(shell + command).redirectOutput(output).redirectError(errors).start()
        check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "File operation timed out" }
        return ShellResult(process.exitValue() == 0, output.inputStream().use { it.readLimited(MAX_SAVE_BYTES) })
    } finally {
        process?.destroyForcibly()
        output.delete()
        errors.delete()
    }
}
