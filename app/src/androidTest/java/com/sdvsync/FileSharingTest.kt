package com.sdvsync

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sdvsync.logging.AppLogger
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class FileSharingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun diagnosticReportsUseTheAppNameAndAnAllowedExportDirectory() {
        val report = AppLogger.createDiagnosticReport(context)
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
            assertTrue(uri.path!!.startsWith("/exports/logs/cinderbox-companion-diagnostic-"))
            context.contentResolver.openInputStream(uri)!!.bufferedReader().use {
                assertEquals("=== Cinderbox-Companion Diagnostic Report ===", it.readLine())
            }
        } finally {
            report.delete()
        }
    }

    @Test
    fun onlyExplicitExportDirectoriesCanProduceSharedUris() {
        for (directory in listOf("updates", "installers", "exports")) {
            val file = File(context.cacheDir, "$directory/fixture")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            assertEquals("/$directory/fixture", uri.path)
        }
        val log = File(context.filesDir, "logs/fixture.log")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", log)
        assertEquals("/logs/fixture.log", uri.path)
    }

    @Test
    fun privateCacheAndPreferenceFilesCannotProduceSharedUris() {
        val privateFiles = listOf(
            File(context.cacheDir, "private-import.zip"),
            File(context.cacheDir, "exports/../private-import.zip"),
            File(context.applicationInfo.dataDir, "shared_prefs/steam_session.xml")
        )
        for (file in privateFiles) {
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                fail("Private file was shareable")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
