package com.sdvsync.saves

import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.fileaccess.FileAccessStrategy
import com.sdvsync.logging.AppLogger
import java.io.File

class SaveFileManager(private val metadataParser: SaveMetadataParser, private val resolveLocation: () -> SaveLocation) {
    companion object {
        private const val TAG = "SaveFileManager"
        const val SDV_SAVE_PATH =
            "/storage/emulated/0/Android/data/com.chucklefish.stardewvalley/files/Saves"
        const val CINDERBOX_SAVE_PATH = CinderboxPaths.SAVES_DIR
    }

    /** True when SAF points to a staging directory instead of the game folder. */
    val isStaging: Boolean
        get() = resolveLocation().basePath.let { it != SDV_SAVE_PATH && it != CINDERBOX_SAVE_PATH }

    /**
     * List all local saves with their metadata.
     */
    suspend fun listLocalSaves(): List<LocalSave> {
        val (fileAccess, basePath) = resolveLocation()
        val savesDir = File(basePath)
        val folders = fileAccess.listDirectories(savesDir) ?: return emptyList()
        AppLogger.d(TAG, "listLocalSaves: found ${folders.size} folders in $basePath")

        return folders.mapNotNull { folderName ->
            val saveDir = File(savesDir, folderName)
            val infoFile = File(saveDir, "SaveGameInfo")

            val infoData = fileAccess.readFile(infoFile) ?: return@mapNotNull null
            val metadata = metadataParser.parseFromBytes(infoData) ?: return@mapNotNull null

            LocalSave(
                folderName = folderName,
                metadata = metadata,
                directory = saveDir
            )
        }
    }

    /**
     * Read all files for a specific local save.
     * Returns map of filename -> bytes.
     */
    suspend fun readLocalSave(saveFolderName: String): Map<String, ByteArray> {
        requireSafeName(saveFolderName)
        val (fileAccess, basePath) = resolveLocation()
        val saveDir = File(basePath, saveFolderName)
        val files = fileAccess.listFiles(saveDir) ?: run {
            check(!fileAccess.exists(saveDir)) { "Cannot read save folder: $saveFolderName" }
            return emptyMap()
        }

        val result = mutableMapOf<String, ByteArray>()
        for (filename in files) {
            requireSafeName(filename)
            if (filename.endsWith(".sdvsync_tmp")) continue
            result[filename] = checkNotNull(fileAccess.readFile(File(saveDir, filename))) {
                "Cannot read save file: $filename"
            }
        }
        AppLogger.d(TAG, "readLocalSave($saveFolderName): read ${result.size}/${files.size} files")
        return result
    }

    /**
     * Write save files to the local game directory (or staging directory).
     * Creates the save folder if it doesn't exist.
     */
    suspend fun writeLocalSave(saveFolderName: String, files: Map<String, ByteArray>): Boolean {
        requireSafeName(saveFolderName)
        val (fileAccess, basePath) = resolveLocation()
        val saveDir = File(basePath, saveFolderName)

        files.keys.forEach(::requireSafeName)
        require(files.isNotEmpty()) { "No save files to write" }

        // Ensure directory exists
        val mkdirsResult = fileAccess.mkdirs(saveDir)
        AppLogger.d(
            TAG,
            "writeLocalSave($saveFolderName): mkdirs=${if (mkdirsResult) "created" else "already exists or failed"}"
        )

        // Write each file atomically (temp file then rename)
        for ((filename, data) in files) {
            val targetFile = File(saveDir, filename)
            val tempFile = File(saveDir, "$filename.sdvsync_tmp")

            // Write to temp file
            if (!fileAccess.writeFile(tempFile, data)) {
                AppLogger.e(TAG, "writeLocalSave: temp write failed for $filename (${data.size} bytes)")
                return false
            }

            // Rename temp to target (atomic on same filesystem)
            if (!fileAccess.renameFile(tempFile, targetFile)) {
                AppLogger.w(TAG, "writeLocalSave: rename failed for $filename, falling back to direct write")
                fileAccess.deleteFile(tempFile)
                if (!fileAccess.writeFile(targetFile, data)) {
                    AppLogger.e(TAG, "writeLocalSave: direct write also failed for $filename")
                    return false
                }
            }
        }

        AppLogger.d(TAG, "writeLocalSave($saveFolderName): wrote ${files.size} files successfully")
        return true
    }

    /**
     * Check if save directory exists and is accessible.
     */
    suspend fun isSaveDirectoryAccessible(): Boolean {
        val (fileAccess, basePath) = resolveLocation()
        return fileAccess.exists(File(basePath))
    }

    internal fun requireSafeName(name: String) {
        require(
            name.isNotBlank() &&
                name != "." &&
                name != ".." &&
                '/' !in name &&
                '\\' !in name &&
                '\u0000' !in name
        ) { "Invalid save file name" }
    }
}

data class LocalSave(val folderName: String, val metadata: SaveMetadata, val directory: File)

data class SaveLocation(val fileAccess: FileAccessStrategy, val basePath: String)
