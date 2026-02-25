package com.sdvsync.mods

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.models.ModUpdateInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.modDataStore by preferencesDataStore(name = "mod_data")

/**
 * Persistent storage for mod-related data.
 * API key stored in EncryptedSharedPreferences, other data in DataStore.
 */
class ModDataStore(private val context: Context) {

    companion object {
        private const val TAG = "ModDataStore"
        private val UPDATE_CACHE_KEY = stringPreferencesKey("update_cache")
        private val LAST_UPDATE_CHECK_KEY = longPreferencesKey("last_update_check")
        private val MOD_METADATA_KEY = stringPreferencesKey("mod_metadata")

        private const val ENCRYPTED_PREFS_NAME = "mod_encrypted_prefs"
        private const val KEY_NEXUS_API_KEY = "nexus_api_key"
    }

    private val encryptedPrefs by lazy {
        try {
            val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                ENCRYPTED_PREFS_NAME,
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create EncryptedSharedPreferences", e)
            null
        }
    }

    // ── API Key ─────────────────────────────────────────────────────────

    fun getNexusApiKey(): String? {
        return encryptedPrefs?.getString(KEY_NEXUS_API_KEY, null)
    }

    fun setNexusApiKey(key: String?) {
        encryptedPrefs?.edit()?.apply {
            if (key != null) putString(KEY_NEXUS_API_KEY, key)
            else remove(KEY_NEXUS_API_KEY)
            apply()
        }
    }

    // ── Update Cache ────────────────────────────────────────────────────

    suspend fun getUpdateCache(): Map<String, ModUpdateInfo> {
        return context.modDataStore.data
            .map { prefs ->
                prefs[UPDATE_CACHE_KEY]?.let { parseUpdateCache(it) } ?: emptyMap()
            }
            .first()
    }

    suspend fun setUpdateCache(updates: Map<String, ModUpdateInfo>) {
        context.modDataStore.edit { prefs ->
            prefs[UPDATE_CACHE_KEY] = serializeUpdateCache(updates)
            prefs[LAST_UPDATE_CHECK_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun getLastUpdateCheck(): Long {
        return context.modDataStore.data
            .map { prefs -> prefs[LAST_UPDATE_CHECK_KEY] ?: 0L }
            .first()
    }

    // ── Mod Metadata (install source, timestamps) ───────────────────────

    suspend fun getModMetadata(uniqueId: String): ModMetadata? {
        val all = getAllMetadata()
        return all[uniqueId]
    }

    suspend fun setModMetadata(uniqueId: String, metadata: ModMetadata) {
        val all = getAllMetadata().toMutableMap()
        all[uniqueId] = metadata
        context.modDataStore.edit { prefs ->
            prefs[MOD_METADATA_KEY] = serializeMetadata(all)
        }
    }

    suspend fun removeModMetadata(uniqueId: String) {
        val all = getAllMetadata().toMutableMap()
        all.remove(uniqueId)
        context.modDataStore.edit { prefs ->
            prefs[MOD_METADATA_KEY] = serializeMetadata(all)
        }
    }

    private suspend fun getAllMetadata(): Map<String, ModMetadata> {
        return context.modDataStore.data
            .map { prefs ->
                prefs[MOD_METADATA_KEY]?.let { parseMetadata(it) } ?: emptyMap()
            }
            .first()
    }

    // ── Serialization ───────────────────────────────────────────────────

    private fun serializeUpdateCache(updates: Map<String, ModUpdateInfo>): String {
        val obj = JSONObject()
        updates.forEach { (id, info) ->
            obj.put(id, JSONObject().apply {
                put("uniqueID", info.uniqueID)
                put("installedVersion", info.installedVersion)
                put("latestVersion", info.latestVersion)
                put("updateUrl", info.updateUrl ?: JSONObject.NULL)
                put("source", info.source)
            })
        }
        return obj.toString()
    }

    private fun parseUpdateCache(json: String): Map<String, ModUpdateInfo> {
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, ModUpdateInfo>()
            obj.keys().forEach { key ->
                val item = obj.getJSONObject(key)
                result[key] = ModUpdateInfo(
                    uniqueID = item.getString("uniqueID"),
                    installedVersion = item.getString("installedVersion"),
                    latestVersion = item.getString("latestVersion"),
                    updateUrl = item.optString("updateUrl").takeIf { it != "null" && it.isNotBlank() },
                    source = item.getString("source"),
                )
            }
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse update cache", e)
            emptyMap()
        }
    }

    private fun serializeMetadata(metadata: Map<String, ModMetadata>): String {
        val obj = JSONObject()
        metadata.forEach { (id, meta) ->
            obj.put(id, JSONObject().apply {
                put("installedFrom", meta.installedFrom ?: JSONObject.NULL)
                put("installedAt", meta.installedAt)
            })
        }
        return obj.toString()
    }

    private fun parseMetadata(json: String): Map<String, ModMetadata> {
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, ModMetadata>()
            obj.keys().forEach { key ->
                val item = obj.getJSONObject(key)
                result[key] = ModMetadata(
                    installedFrom = item.optString("installedFrom").takeIf { it != "null" && it.isNotBlank() },
                    installedAt = item.optLong("installedAt", 0),
                )
            }
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse mod metadata", e)
            emptyMap()
        }
    }
}

data class ModMetadata(
    val installedFrom: String? = null,
    val installedAt: Long = 0,
)
