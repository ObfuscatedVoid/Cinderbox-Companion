package com.sdvsync.fileaccess

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.sdvsync.logging.AppLogger
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.util.MAX_SAVE_BYTES
import com.sdvsync.util.readLimited
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File access within a directory selected through the system document picker. */
class SAFFileAccess(
    private val context: Context,
    private val treeUri: Uri,
    val basePath: String = SaveFileManager.SDV_SAVE_PATH
) : FileAccessStrategy {

    override val name: String
        get() = if (basePath == STAGING_BASE_PATH) "SAF (Staging)" else "SAF"

    companion object {
        private const val TAG = "SAFFileAccess"
        private const val PREF_NAME = "saf_prefs"
        private const val KEY_TREE_URI = "tree_uri"

        /** Synthetic base path used when SAF points to a staging directory. */
        const val STAGING_BASE_PATH = "/staging"

        /** Check if a persisted URI grant exists. */
        fun isAvailable(context: Context): Boolean = getPersistedUri(context) != null

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
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit { putString(KEY_TREE_URI, uri.toString()) }
        }

        /** Remove persisted URI and release the permission. */
        fun clearPersistedUri(context: Context) {
            val uriStr = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TREE_URI, null)
            if (uriStr != null) {
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        uriStr.toUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to release URI permission", e)
                }
            }
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit { remove(KEY_TREE_URI) }
        }

        fun getPersistedUri(context: Context): Uri? {
            val uriStr = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TREE_URI, null) ?: return null
            val uri = uriStr.toUri()
            // Verify the grant is still valid
            val validGrants = context.contentResolver.persistedUriPermissions
            val valid = validGrants.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
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
        val segments = documentSegments(basePath, file)

        var current = treeRoot
        for (segment in segments) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    /**
     * Like resolveDocument, but creates intermediate directories as needed.
     * Returns the parent DocumentFile for the given file.
     */
    private fun resolveParentCreating(file: File): DocumentFile? {
        val segments = documentSegments(basePath, file)
        require(segments.isNotEmpty()) { "Cannot overwrite the selected directory" }

        var current = treeRoot
        for (segment in segments.dropLast(1)) {
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
        } catch (e: Exception) {
            AppLogger.e(TAG, "listFiles failed: ${dir.absolutePath}", e)
            null
        }
    }

    override suspend fun readFile(file: File): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val doc = resolveDocument(file) ?: return@withContext null
            context.contentResolver.openInputStream(doc.uri)?.use { it.readLimited(MAX_SAVE_BYTES) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "readFile failed: ${file.absolutePath}", e)
            null
        }
    }

    override suspend fun writeFile(file: File, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = resolveParentCreating(file) ?: return@withContext false
            val fileName = file.name

            val document = parent.findFile(fileName)
                ?: parent.createFile("application/octet-stream", fileName)
                ?: return@withContext false
            val output = context.contentResolver.openOutputStream(document.uri, "wt")
                ?: return@withContext false
            output.use { it.write(data) }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "writeFile failed: ${file.absolutePath}", e)
            false
        }
    }

    override suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            require(documentSegments(basePath, file).isNotEmpty()) { "Cannot delete the selected directory" }
            resolveDocument(file)?.delete() ?: false
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteFile failed: ${file.absolutePath}", e)
            false
        }
    }

    override suspend fun renameFile(from: File, to: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = documentSegments(basePath, from)
            val destination = documentSegments(basePath, to)
            require(source.isNotEmpty() && destination.isNotEmpty()) { "Cannot rename the selected directory" }
            require(source.dropLast(1) == destination.dropLast(1)) { "Document rename cannot change directories" }
            if (resolveDocument(to) != null) return@withContext false
            val doc = resolveDocument(from) ?: return@withContext false
            doc.renameTo(to.name)
        } catch (e: Exception) {
            AppLogger.e(TAG, "renameFile failed: ${from.absolutePath}", e)
            false
        }
    }

    override suspend fun mkdirs(dir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val segments = documentSegments(basePath, dir)
            if (segments.isEmpty()) return@withContext treeRoot.exists()

            var current = treeRoot
            for (segment in segments) {
                current = current.findFile(segment)
                    ?: current.createDirectory(segment)
                    ?: return@withContext false
                if (!current.isDirectory) return@withContext false
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "mkdirs failed: ${dir.absolutePath}", e)
            false
        }
    }
}
