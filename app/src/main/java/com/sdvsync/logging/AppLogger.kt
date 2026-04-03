package com.sdvsync.logging

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sdvsync.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val MAX_LOG_SIZE = 1_000_000L
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var logFile: File? = null
    private var prevLogFile: File? = null
    private var writer: BufferedWriter? = null
    private val lock = Any()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
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

        result = result.replace(
            Regex("""(access_?[Tt]oken|refresh_?[Tt]oken|password|api_?[Kk]ey|apikey)\s*[=:]\s*["']?[^\s"',}\]]+"""),
            "$1=[REDACTED]"
        )

        result = result.replace(Regex("""\b76561\d{12}\b"""), "[STEAM_ID]")

        val usernamePattern =
            Regex("""(Resuming session for|Authenticating with credentials for|Login attempt for user:)\s+(\S+)""")
        result = result.replace(usernamePattern) { match ->
            val prefix = match.groupValues[1]
            val user = match.groupValues[2]
            "$prefix ${partialRedact(user)}"
        }

        result = result.replace(
            Regex("""access token:\s*\d+""", RegexOption.IGNORE_CASE),
            "access token: [REDACTED]"
        )

        result = result.replace(
            Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}"""),
            "[EMAIL]"
        )

        result = result.replace(
            Regex("""\b[A-Za-z0-9+/]{40,}={0,2}\b"""),
            "[TOKEN]"
        )

        return result
    }

    private fun partialRedact(value: String): String = if (value.length > 2) {
        value.take(2) + "***"
    } else {
        "***"
    }

    private fun buildDiagnosticHeader(): String = buildString {
        appendLine("=== SDV Sync Diagnostic Report ===")
        appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Date: ${DATE_FORMAT.format(Date())}")
        appendLine()

        appendLine("--- Device ---")
        appendLine("Manufacturer: ${Build.MANUFACTURER}")
        appendLine("Model: ${Build.MODEL}")
        appendLine("Device: ${Build.DEVICE}")
        appendLine("Product: ${Build.PRODUCT}")
        appendLine("Hardware: ${Build.HARDWARE}")
        appendLine("Board: ${Build.BOARD}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appendLine("SOC: ${Build.SOC_MODEL}")
            appendLine("SOC Manufacturer: ${Build.SOC_MANUFACTURER}")
        }
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine()

        appendLine("--- OS ---")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
        appendLine("Build Fingerprint: ${Build.FINGERPRINT}")
        appendLine()

        val ctx = appContext
        if (ctx != null) {
            appendLine("--- Memory ---")
            try {
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                appendLine("Total RAM: ${memInfo.totalMem / (1024 * 1024)} MB")
                appendLine("Available RAM: ${memInfo.availMem / (1024 * 1024)} MB")
                appendLine("Low Memory: ${memInfo.lowMemory}")
            } catch (_: Exception) {
            }
            appendLine()

            appendLine("--- Display ---")
            try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getMetrics(metrics)
                appendLine("Resolution: ${metrics.widthPixels}x${metrics.heightPixels}")
                appendLine("Density: ${metrics.densityDpi}dpi (${metrics.density}x)")
            } catch (_: Exception) {
            }
            appendLine()

            appendLine("--- Storage ---")
            try {
                val stat = android.os.StatFs(ctx.filesDir.absolutePath)
                val total = stat.totalBytes / (1024 * 1024 * 1024)
                val available = stat.availableBytes / (1024 * 1024 * 1024)
                appendLine("Internal Total: ${total}GB")
                appendLine("Internal Available: ${available}GB")
            } catch (_: Exception) {
            }
        }

        appendLine()
        appendLine("===================================")
    }

    fun getLogFiles(): List<File> {
        val files = mutableListOf<File>()
        logFile?.let { if (it.exists()) files.add(it) }
        prevLogFile?.let { if (it.exists()) files.add(it) }
        return files
    }

    private fun readAllLogs(): String = buildString {
        append(buildDiagnosticHeader())
        val files = getLogFiles()
        for (file in files) {
            try {
                appendLine()
                appendLine("--- ${file.name} ---")
                append(file.readText())
            } catch (_: Exception) {
            }
        }
    }

    fun shareLogs(context: Context) {
        val ctx = context.applicationContext
        val files = getLogFiles()
        if (files.isEmpty()) {
            Toast.makeText(ctx, "No logs available", Toast.LENGTH_SHORT).show()
            return
        }

        val logDir = File(ctx.cacheDir, "shared_logs").also { it.mkdirs() }
        val reportFile = File(logDir, "sdvsync-diagnostic-${System.currentTimeMillis()}.log")
        reportFile.writeText(readAllLogs())

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", reportFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share SDV Sync Logs"))
    }

    fun copyLogs(context: Context) {
        val ctx = context.applicationContext
        val logText = readAllLogs()

        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SDV Sync Logs", logText))

        Toast.makeText(ctx, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
