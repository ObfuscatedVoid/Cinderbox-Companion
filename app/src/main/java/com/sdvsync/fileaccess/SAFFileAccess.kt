package com.sdvsync.fileaccess

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.sdvsync.logging.AppLogger
import com.sdvsync.saves.SaveFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File access using SAF (Storage Access Framework).
 * Works on Android 11+ (API 30+) for accessing Android/data/ via user-granted tree URI.
 * On Android 14+ the system picker may block navigation into Android/data/ on some devices,
 * but it still works on emulators and some custom ROMs.
 */
class SAFFileAccess(
    private val context: Context,
    private val treeUri: Uri,
    val basePath: String = SaveFileManager.SDV_SAVE_PATH,
) : FileAccessStrategy {

    override val name: String
        get() = if (basePath == STAGING_BASE_PATH) "SAF (Staging)" else "SAF"

    companion object {
        private const val TAG = "SAFFileAccess"
        private const val PREF_NAME = "saf_prefs"
        private const val KEY_TREE_URI = "tree_uri"

        /** Synthetic base path used when SAF points to a staging directory. */
        const val STAGING_BASE_PATH = "/staging"

        /** SAF for Android/data/ works on API 30+. May be restricted on API 34+ on some devices. */
        fun isDeviceEligible(): Boolean {
            return Build.VERSION.SDK_INT >= 30
        }

        /** Check if a persisted URI grant exists and the device is eligible. */
        fun isAvailable(context: Context): Boolean {
            if (!isDeviceEligible()) return false
            return getPersistedUri(context) != null
        }

        /**
         * Check if the persisted SAF URI points to a staging directory
         * (i.e. NOT the actual Stardew Valley save folder).
         */
        fun isStaging(context: Context): Boolean {
            val uri = getPersistedUri(context) ?: return false
            return !uri.toString().contains("com.chucklefish.stardewvalley")
        }

        /** Persist a tree URI grant across app restarts. */
        fun persistUri(context: Context, uri: Uri) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TREE_URI, uri.toString())
                .apply()
        }

        /** Remove persisted URI and release the permission. */
        fun clearPersistedUri(context: Context) {
            val uriStr = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TREE_URI, null)
            if (uriStr != null) {
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(uriStr),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to release URI permission", e)
                }
            }
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TREE_URI)
                .apply()
        }

        fun getPersistedUri(context: Context): Uri? {
            val uriStr = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TREE_URI, null) ?: return null
            val uri = Uri.parse(uriStr)
            // Verify the grant is still valid
            val validGrants = context.contentResolver.persistedUriPermissions
            val valid = validGrants.any { it.uri == uri && it.isReadPermission }
            return if (valid) uri else null
        }

        /** Create an instance if available, null otherwise. Auto-detects staging vs direct. */
        fun createInstance(context: Context): SAFFileAccess? {
            val uri = getPersistedUri(context) ?: return null
            val basePath = if (isStaging(context)) STAGING_BASE_PATH else SaveFileManager.SDV_SAVE_PATH
            return SAFFileAccess(context, uri, basePath)
        }
    }

    private val treeRoot: DocumentFile by lazy {
        DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Cannot open tree URI: $treeUri")
    }

    /**
     * Map a java.io.File path to a DocumentFile by stripping the SDV_SAVE_PATH prefix
     * and walking the tree.
     */
    private fun resolveDocument(file: File): DocumentFile? {
        val relativePath = file.absolutePath.removePrefix(basePath)
            .trimStart('/')
        if (relativePath.isEmpty()) return treeRoot

        var current = treeRoot
        for (segment in relativePath.split("/")) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    /**
     * Like resolveDocument, but creates intermediate directories as needed.
     * Returns the parent DocumentFile for the given file.
     */
    private fun resolveParentCreating(file: File): DocumentFile? {
        val parentPath = file.parentFile?.absolutePath ?: return null
        val relativePath = parentPath.removePrefix(basePath)
            .trimStart('/')
        if (relativePath.isEmpty()) return treeRoot

        var current = treeRoot
        for (segment in relativePath.split("/")) {
            current = current.findFile(segment)
                ?: current.createDirectory(segment)
                ?: return null
        }
        return current
    }

    override suspend fun exists(file: File): Boolean = withContext(Dispatchers.IO) {
        resolveDocument(file) != null
    }

    override suspend fun listDirectories(dir: File): List<String>? = withContext(Dispatchers.IO) {
        try {
            val doc = resolveDocument(dir) ?: return@withContext null
            doc.listFiles()
                .filter { it.isDirectory }
                .map { it.name ?: "" }
                .filter { it.isNotEmpty() }
                .ifEmpty { null }
        } catch (e: Exception) {
            AppLogger.e(TAG, "listDirectories failed: ${dir.absolutePath}", e)
            null
        }
    }

    override suspend fun listFiles(dir: File): List<String>? = withContext(Dispatchers.IO) {
        try {
            val doc = resolveDocument(dir) ?: return@withContext null
            doc.listFiles()
                .filter { it.isFile }
                .map { it.name ?: "" }
                .filter { it.isNotEmpty() }
                .ifEmpty { null }
        } catch (e: Exception) {
            AppLogger.e(TAG, "listFiles failed: ${dir.absolutePath}", e)
            null
        }
    }

    override suspend fun readFile(file: File): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val doc = resolveDocument(file) ?: return@withContext null
            context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "readFile failed: ${file.absolutePath}", e)
            null
        }
    }

    override suspend fun writeFile(file: File, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val parent = resolveParentCreating(file) ?: return@withContext false
                val fileName = file.name

                // If existing, delete first (SAF doesn't overwrite)
                parent.findFile(fileName)?.delete()

                val newDoc = parent.createFile("application/octet-stream", fileName)
                    ?: return@withContext false
                context.contentResolver.openOutputStream(newDoc.uri)?.use { it.write(data) }
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "writeFile failed: ${file.absolutePath}", e)
                false
            }
        }

    override suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            resolveDocument(file)?.delete() ?: false
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteFile failed: ${file.absolutePath}", e)
            false
        }
    }

    override suspend fun renameFile(from: File, to: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = resolveDocument(from) ?: return@withContext false
            doc.renameTo(to.name)
        } catch (e: Exception) {
            AppLogger.e(TAG, "renameFile failed: ${from.absolutePath}", e)
            false
        }
    }

    override suspend fun mkdirs(dir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val relativePath = dir.absolutePath.removePrefix(basePath)
                .trimStart('/')
            if (relativePath.isEmpty()) return@withContext treeRoot.exists()

            var current = treeRoot
            for (segment in relativePath.split("/")) {
                current = current.findFile(segment)
                    ?: current.createDirectory(segment)
                    ?: return@withContext false
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "mkdirs failed: ${dir.absolutePath}", e)
            false
        }
    }
}
