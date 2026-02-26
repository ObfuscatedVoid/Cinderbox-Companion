package com.sdvsync.mods.models

/** SMAPI manifest.json representation. */
data class ModManifest(
    val name: String,
    val author: String,
    val version: String,
    val uniqueID: String,
    val description: String,
    val minimumApiVersion: String? = null,
    val entryDll: String? = null,
    val contentPackFor: ContentPackFor? = null,
    val dependencies: List<ModDependency> = emptyList(),
    val updateKeys: List<String> = emptyList()
)

data class ContentPackFor(val uniqueID: String, val minimumVersion: String? = null)

data class ModDependency(val uniqueID: String, val minimumVersion: String? = null, val isRequired: Boolean = true)

/** Installed mod with metadata beyond manifest. */
data class InstalledMod(
    val manifest: ModManifest,
    val folderName: String,
    val folderPath: String,
    val enabled: Boolean,
    val installedAt: Long = 0,
    val installedFrom: String? = null,
    val fileSize: Long = 0
)

/** Remote mod from Nexus or other source. */
data class RemoteMod(
    val sourceId: String,
    val modId: String,
    val name: String,
    val author: String,
    val summary: String,
    val description: String? = null,
    val version: String,
    val categoryName: String? = null,
    val pictureUrl: String? = null,
    val endorsements: Int = 0,
    val downloads: Int = 0,
    val lastUpdated: Long = 0
)

data class RemoteModFile(
    val fileId: String,
    val fileName: String,
    val fileVersion: String,
    val fileSize: Long,
    val isPrimary: Boolean,
    val categoryName: String,
    val uploadedAt: Long,
    val description: String = "",
    val changelogHtml: String? = null,
    val modVersion: String? = null
)

data class ModUpdateInfo(
    val uniqueID: String,
    val installedVersion: String,
    val latestVersion: String,
    val updateUrl: String?,
    val source: String
)

data class ModDownloadProgress(
    val state: ModDownloadState = ModDownloadState.IDLE,
    val modName: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val currentFile: String = "",
    val errorMessage: String? = null
)

enum class ModDownloadState {
    IDLE,
    DOWNLOADING,
    EXTRACTING,
    INSTALLING,
    COMPLETED,
    ERROR
}

data class ModSearchResult(val mods: List<RemoteMod>, val totalResults: Int, val hasMore: Boolean)

/** Result of installing a mod from a zip. */
sealed class InstallResult {
    data class Success(val mods: List<InstalledMod>) : InstallResult()
    data class Error(val message: String) : InstallResult()
}
