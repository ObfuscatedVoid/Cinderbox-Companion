package com.sdvsync.mods.api

import com.sdvsync.mods.models.ModSearchResult
import com.sdvsync.mods.models.RemoteMod
import com.sdvsync.mods.models.RemoteModFile

interface ModSource {
    val sourceId: String
    val displayName: String

    suspend fun search(query: String, page: Int = 1): ModSearchResult
    suspend fun getTrending(): List<RemoteMod>
    suspend fun getLatestAdded(): List<RemoteMod>
    suspend fun getLatestUpdated(): List<RemoteMod>
    suspend fun getModDetails(modId: String): RemoteMod
    suspend fun getModFiles(modId: String): List<RemoteModFile>
    suspend fun getDownloadUrl(modId: String, fileId: String): String
    suspend fun validateApiKey(apiKey: String): Boolean
}
