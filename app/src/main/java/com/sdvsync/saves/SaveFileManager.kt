package com.sdvsync.saves

import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.fileaccess.FileAccessStrategy
import com.sdvsync.logging.AppLogger
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SaveFileManager(private val metadataParser: SaveMetadataParser, private val resolveLocation: () -> SaveLocation) {
    private val writeMutex = Mutex()

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
        val folders = fileAccess.listDirectories(savesDir) ?: run {
            check(!fileAccess.exists(savesDir)) { "Cannot read the save directory" }
            return emptyList()
        }
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

        check(
            files.none {
                it.endsWith(".sdvsync_bak")
            }
        ) { "Interrupted save detected; restore its backup before syncing" }
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

        files.keys.forEach {
            requireSafeName(it)
            require(!it.endsWith(".sdvsync_tmp") && !it.endsWith(".sdvsync_bak")) { "Reserved save filename" }
        }
        require(files.isNotEmpty()) { "No save files to write" }

        return writeMutex.withLock { writeSaveFiles(fileAccess, saveDir, files) }
    }

    /**
     * Check if save directory exists and is accessible.
     */
    suspend fun isSaveDirectoryAccessible(): Boolean {
        val (fileAccess, basePath) = resolveLocation()
        return fileAccess.exists(File(basePath))
    }

    internal fun requireSafeName(name: String) = requireSafeSaveName(name)
}

data class LocalSave(val folderName: String, val metadata: SaveMetadata, val directory: File)

data class SaveLocation(val fileAccess: FileAccessStrategy, val basePath: String)
