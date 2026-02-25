package com.sdvsync.mods

import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.models.ContentPackFor
import com.sdvsync.mods.models.ModDependency
import com.sdvsync.mods.models.ModManifest
import org.json.JSONObject

/**
 * Parses SMAPI manifest.json files.
 * Case-insensitive key lookup handles variations (UniqueID vs uniqueID).
 */
class ModManifestParser {

    companion object {
        private const val TAG = "ModManifestParser"
    }

    fun parse(jsonString: String): ModManifest? {
        return try {
            val obj = JSONObject(jsonString)
            ModManifest(
                name = obj.getStringCI("Name") ?: return null,
                author = obj.getStringCI("Author") ?: "Unknown",
                version = obj.getStringCI("Version") ?: return null,
                uniqueID = obj.getStringCI("UniqueID") ?: return null,
                description = obj.getStringCI("Description") ?: "",
                minimumApiVersion = obj.getStringCI("MinimumApiVersion"),
                entryDll = obj.getStringCI("EntryDll"),
                contentPackFor = obj.getObjectCI("ContentPackFor")?.let { cpf ->
                    ContentPackFor(
                        uniqueID = cpf.getStringCI("UniqueID") ?: return@let null,
                        minimumVersion = cpf.getStringCI("MinimumVersion"),
                    )
                },
                dependencies = obj.getArrayCI("Dependencies")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val dep = arr.optJSONObject(i) ?: return@mapNotNull null
                        ModDependency(
                            uniqueID = dep.getStringCI("UniqueID") ?: return@mapNotNull null,
                            minimumVersion = dep.getStringCI("MinimumVersion"),
                            isRequired = dep.getBooleanCI("IsRequired") ?: true,
                        )
                    }
                } ?: emptyList(),
                updateKeys = obj.getArrayCI("UpdateKeys")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList(),
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse manifest: ${e.message}", e)
            null
        }
    }

    /** Case-insensitive string lookup. */
    private fun JSONObject.getStringCI(key: String): String? {
        keys().forEach { k ->
            if (k.equals(key, ignoreCase = true)) return optString(k).takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun JSONObject.getObjectCI(key: String): JSONObject? {
        keys().forEach { k ->
            if (k.equals(key, ignoreCase = true)) return optJSONObject(k)
        }
        return null
    }

    private fun JSONObject.getArrayCI(key: String): org.json.JSONArray? {
        keys().forEach { k ->
            if (k.equals(key, ignoreCase = true)) return optJSONArray(k)
        }
        return null
    }

    private fun JSONObject.getBooleanCI(key: String): Boolean? {
        keys().forEach { k ->
            if (k.equals(key, ignoreCase = true)) {
                return try { getBoolean(k) } catch (_: Exception) { null }
            }
        }
        return null
    }
}
