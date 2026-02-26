package com.sdvsync.logging

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val MAX_LOG_SIZE = 1_000_000L // 1MB
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var logFile: File? = null
    private var prevLogFile: File? = null
    private var writer: BufferedWriter? = null
    private val lock = Any()

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        logDir.mkdirs()
        logFile = File(logDir, "sdvsync.log")
        prevLogFile = File(logDir, "sdvsync.prev.log")
        openWriter()
    }

    private fun openWriter() {
        writer?.runCatching { close() }
        writer = BufferedWriter(FileWriter(logFile!!, true))
    }

    private fun rotateIfNeeded() {
        val file = logFile ?: return
        if (file.length() > MAX_LOG_SIZE) {
            writer?.runCatching { close() }
            prevLogFile?.delete()
            file.renameTo(prevLogFile!!)
            openWriter()
        }
    }

    private fun writeLog(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        val timestamp = DATE_FORMAT.format(Date())
        val line = "$timestamp $level/$tag: $sanitized"

        synchronized(lock) {
            try {
                writer?.apply {
                    write(line)
                    newLine()
                    if (throwable != null) {
                        write(sanitize(throwable.stackTraceToString()))
                        newLine()
                    }
                    flush()
                }
                rotateIfNeeded()
            } catch (_: Exception) {
                // Don't crash if logging fails
            }
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeLog("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeLog("I", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        writeLog("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        writeLog("E", tag, message, throwable)
    }

    internal fun sanitize(message: String): String {
        var result = message
        // Token values: accessToken=..., refreshToken=..., access_token=..., password=...
        result = result.replace(
            Regex("""(access_?[Tt]oken|refresh_?[Tt]oken|password)\s*[=:]\s*["']?[^\s"',}\]]+"""),
            "$1=[REDACTED]"
        )
        // Steam IDs (76561... 17-digit numbers)
        result = result.replace(Regex("""\b76561\d{12}\b"""), "[STEAM_ID]")
        return result
    }

    fun getLogFiles(): List<File> {
        val files = mutableListOf<File>()
        logFile?.let { if (it.exists()) files.add(it) }
        prevLogFile?.let { if (it.exists()) files.add(it) }
        return files
    }

    fun shareLogs(context: Context) {
        val files = getLogFiles()
        if (files.isEmpty()) return

        val uris = files.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share SDV Sync Logs"))
    }
}
