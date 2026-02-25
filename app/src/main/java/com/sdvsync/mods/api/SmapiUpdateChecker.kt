package com.sdvsync.mods.api

import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.models.InstalledMod
import com.sdvsync.mods.models.ModUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Checks for mod updates via the SMAPI web API.
 * No authentication required.
 */
class SmapiUpdateChecker(
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "SmapiUpdateChecker"
        private const val API_URL = "https://smapi.io/api/v4.0.0/mods"
    }

    /**
     * Check all installed mods for updates in a single batch request.
     * Returns a map of uniqueID -> ModUpdateInfo for mods with available updates.
     */
    suspend fun checkForUpdates(mods: List<InstalledMod>): Map<String, ModUpdateInfo> = withContext(Dispatchers.IO) {
        if (mods.isEmpty()) return@withContext emptyMap()

        try {
            val modsArray = JSONArray()
            for (mod in mods) {
                val modObj = JSONObject().apply {
                    put("id", mod.manifest.uniqueID)
                    put("installedVersion", mod.manifest.version)
                    if (mod.manifest.updateKeys.isNotEmpty()) {
                        put("updateKeys", JSONArray(mod.manifest.updateKeys))
                    }
                }
                modsArray.put(modObj)
            }

            val body = JSONObject().apply {
                put("mods", modsArray)
                put("includeExtendedMetadata", true)
            }

            val request = Request.Builder()
                .url(API_URL)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.use { it.body?.string() } ?: return@withContext emptyMap()
            val results = JSONArray(responseBody)

            val updates = mutableMapOf<String, ModUpdateInfo>()

            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                val id = result.getString("id")
                val suggestedUpdate = result.optJSONObject("suggestedUpdate") ?: continue

                val latestVersion = suggestedUpdate.optString("version", "")
                    .takeIf { it.isNotBlank() } ?: continue
                val updateUrl = suggestedUpdate.optString("url", "")
                    .takeIf { it.isNotBlank() }

                // Find the installed mod to get current version
                val installed = mods.find { it.manifest.uniqueID.equals(id, ignoreCase = true) }
                    ?: continue

                // Only add if the version actually differs
                if (latestVersion != installed.manifest.version) {
                    updates[id] = ModUpdateInfo(
                        uniqueID = id,
                        installedVersion = installed.manifest.version,
                        latestVersion = latestVersion,
                        updateUrl = updateUrl,
                        source = buildSourceString(installed),
                    )
                }
            }

            AppLogger.d(TAG, "Update check: ${mods.size} mods checked, ${updates.size} updates found")
            updates
        } catch (e: Exception) {
            AppLogger.e(TAG, "Update check failed", e)
            emptyMap()
        }
    }

    private fun buildSourceString(mod: InstalledMod): String {
        val nexusKey = mod.manifest.updateKeys.find { it.startsWith("Nexus:", ignoreCase = true) }
        return nexusKey ?: mod.manifest.updateKeys.firstOrNull() ?: ""
    }
}
