package com.sdvsync.steam

import java.security.MessageDigest

internal fun planCloudUploads(
    saveFolderName: String,
    files: Map<String, ByteArray>,
    existingFiles: List<CloudFile>
): Map<String, ByteArray> {
    require(
        saveFolderName.isNotBlank() &&
            saveFolderName != "." &&
            saveFolderName != ".." &&
            '/' !in saveFolderName &&
            '\\' !in saveFolderName
    )
    val existing = existingFiles.filter { it.saveFolderName == saveFolderName }
        .groupBy { it.baseName }
        .mapValues { (_, copies) ->
            copies.firstOrNull { it.fullPath.contains("%WinAppDataRoaming%") } ?: copies.first()
        }
    val directory = existing.values.firstOrNull { it.fullPath.contains("%WinAppDataRoaming%") }
        ?.fullPath?.substringBeforeLast('/')
        ?: existing.values.firstOrNull()?.fullPath?.substringBeforeLast('/')
        ?: "%WinAppDataRoaming%StardewValley/Saves/$saveFolderName"
    return buildMap {
        for ((name, data) in files) {
            require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name)
            val remote = existing[name]
            val sha = MessageDigest.getInstance("SHA-1").digest(data)
            if (remote == null || !remote.sha.contentEquals(sha)) {
                put(remote?.fullPath ?: "$directory/$name", data)
            }
        }
    }
}
