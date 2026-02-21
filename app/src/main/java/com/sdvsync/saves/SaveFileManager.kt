package com.sdvsync.saves

import com.sdvsync.fileaccess.FileAccessStrategy
import java.io.File

class SaveFileManager(
    private val fileAccess: FileAccessStrategy,
    private val metadataParser: SaveMetadataParser,
) {
    companion object {
        const val SDV_SAVE_PATH =
            "/storage/emulated/0/Android/data/com.chucklefish.stardewvalley/files/Saves"
    }

    /**
     * List all local saves with their metadata.
     */
    suspend fun listLocalSaves(): List<LocalSave> {
        val savesDir = File(SDV_SAVE_PATH)
        val folders = fileAccess.listDirectories(savesDir) ?: return emptyList()

        return folders.mapNotNull { folderName ->
            val saveDir = File(savesDir, folderName)
            val infoFile = File(saveDir, "SaveGameInfo")

            val infoData = fileAccess.readFile(infoFile) ?: return@mapNotNull null
            val metadata = metadataParser.parseFromBytes(infoData) ?: return@mapNotNull null

            LocalSave(
                folderName = folderName,
                metadata = metadata,
                directory = saveDir,
            )
        }
    }

    /**
     * Read all files for a specific local save.
     * Returns map of filename -> bytes.
     */
    suspend fun readLocalSave(saveFolderName: String): Map<String, ByteArray> {
        val saveDir = File(SDV_SAVE_PATH, saveFolderName)
        val files = fileAccess.listFiles(saveDir) ?: return emptyMap()

        val result = mutableMapOf<String, ByteArray>()
        for (filename in files) {
            val data = fileAccess.readFile(File(saveDir, filename))
            if (data != null) {
                result[filename] = data
            }
        }
        return result
    }

    /**
     * Write save files to the local game directory.
     * Creates the save folder if it doesn't exist.
     */
    suspend fun writeLocalSave(saveFolderName: String, files: Map<String, ByteArray>): Boolean {
        val saveDir = File(SDV_SAVE_PATH, saveFolderName)

        // Ensure directory exists
        if (!fileAccess.mkdirs(saveDir)) {
            // Directory might already exist, that's ok
        }

        // Write each file atomically (temp file then rename)
        for ((filename, data) in files) {
            val targetFile = File(saveDir, filename)
            val tempFile = File(saveDir, "${filename}.sdvsync_tmp")

            // Write to temp file
            if (!fileAccess.writeFile(tempFile, data)) {
                return false
            }

            // Rename temp to target (atomic on same filesystem)
            if (!fileAccess.renameFile(tempFile, targetFile)) {
                // Fallback: direct write
                fileAccess.deleteFile(tempFile)
                if (!fileAccess.writeFile(targetFile, data)) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * Check if Stardew Valley save directory exists and is accessible.
     */
    suspend fun isSaveDirectoryAccessible(): Boolean {
        return fileAccess.exists(File(SDV_SAVE_PATH))
    }
}

data class LocalSave(
    val folderName: String,
    val metadata: SaveMetadata,
    val directory: File,
)
