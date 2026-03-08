package com.sdvsync.mods

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.models.AssociatedMod
import com.sdvsync.mods.models.ModProfile
import com.sdvsync.mods.models.ModUpdateInfo
import com.sdvsync.mods.models.SaveModAssociation
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
        private val PROFILES_KEY = stringPreferencesKey("mod_profiles")
        private val SAVE_MOD_ASSOCIATIONS_KEY = stringPreferencesKey("save_mod_associations")

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
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create EncryptedSharedPreferences", e)
            null
        }
    }

    // ── API Key ─────────────────────────────────────────────────────────

    fun getNexusApiKey(): String? = encryptedPrefs?.getString(KEY_NEXUS_API_KEY, null)

    fun setNexusApiKey(key: String?) {
        encryptedPrefs?.edit()?.apply {
            if (key != null) {
                putString(KEY_NEXUS_API_KEY, key)
            } else {
                remove(KEY_NEXUS_API_KEY)
            }
            apply()
        }
    }

    // ── Update Cache ────────────────────────────────────────────────────

    suspend fun getUpdateCache(): Map<String, ModUpdateInfo> = context.modDataStore.data
        .map { prefs ->
            prefs[UPDATE_CACHE_KEY]?.let { parseUpdateCache(it) } ?: emptyMap()
        }
        .first()

    suspend fun setUpdateCache(updates: Map<String, ModUpdateInfo>) {
        context.modDataStore.edit { prefs ->
            prefs[UPDATE_CACHE_KEY] = serializeUpdateCache(updates)
            prefs[LAST_UPDATE_CHECK_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun getLastUpdateCheck(): Long = context.modDataStore.data
        .map { prefs -> prefs[LAST_UPDATE_CHECK_KEY] ?: 0L }
        .first()

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

    private suspend fun getAllMetadata(): Map<String, ModMetadata> = context.modDataStore.data
        .map { prefs ->
            prefs[MOD_METADATA_KEY]?.let { parseMetadata(it) } ?: emptyMap()
        }
        .first()

    // ── Mod Profiles ──────────────────────────────────────────────────

    suspend fun getProfiles(): List<ModProfile> = context.modDataStore.data
        .map { prefs ->
            prefs[PROFILES_KEY]?.let { parseProfiles(it) } ?: emptyList()
        }
        .first()

    suspend fun saveProfile(profile: ModProfile) {
        val profiles = getProfiles().toMutableList()
        val existingIndex = profiles.indexOfFirst { it.name == profile.name }
        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles.add(profile)
        }
        context.modDataStore.edit { prefs ->
            prefs[PROFILES_KEY] = serializeProfiles(profiles)
        }
    }

    suspend fun deleteProfile(name: String) {
        val profiles = getProfiles().filter { it.name != name }
        context.modDataStore.edit { prefs ->
            prefs[PROFILES_KEY] = serializeProfiles(profiles)
        }
    }

    private fun serializeProfiles(profiles: List<ModProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("name", profile.name)
                    put("enabledModIds", JSONArray(profile.enabledModIds.toList()))
                    put("createdAt", profile.createdAt)
                }
            )
        }
        return array.toString()
    }

    private fun parseProfiles(json: String): List<ModProfile> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val idsArray = obj.getJSONArray("enabledModIds")
            val ids = (0 until idsArray.length()).map { j -> idsArray.getString(j) }.toSet()
            ModProfile(
                name = obj.getString("name"),
                enabledModIds = ids,
                createdAt = obj.optLong("createdAt", 0)
            )
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to parse profiles", e)
        emptyList()
    }

    // ── Save Mod Associations ──────────────────────────────────────────

    suspend fun getSaveModAssociation(saveFolderName: String): SaveModAssociation? = context.modDataStore.data
        .map { prefs ->
            prefs[SAVE_MOD_ASSOCIATIONS_KEY]?.let { parseAssociations(it) }?.get(saveFolderName)
        }
        .first()

    suspend fun setSaveModAssociation(association: SaveModAssociation) {
        val all = getAllAssociations().toMutableMap()
        all[association.saveFolderName] = association
        context.modDataStore.edit { prefs ->
            prefs[SAVE_MOD_ASSOCIATIONS_KEY] = serializeAssociations(all)
        }
    }

    suspend fun deleteSaveModAssociation(saveFolderName: String) {
        val all = getAllAssociations().toMutableMap()
        all.remove(saveFolderName)
        context.modDataStore.edit { prefs ->
            prefs[SAVE_MOD_ASSOCIATIONS_KEY] = serializeAssociations(all)
        }
    }

    private suspend fun getAllAssociations(): Map<String, SaveModAssociation> = context.modDataStore.data
        .map { prefs ->
            prefs[SAVE_MOD_ASSOCIATIONS_KEY]?.let { parseAssociations(it) } ?: emptyMap()
        }
        .first()

    private fun serializeAssociations(associations: Map<String, SaveModAssociation>): String {
        val obj = JSONObject()
        associations.forEach { (saveName, assoc) ->
            val modsArray = JSONArray()
            assoc.enabledMods.forEach { mod ->
                modsArray.put(
                    JSONObject().apply {
                        put("uniqueID", mod.uniqueID)
                        put("name", mod.name)
                        put("version", mod.version)
                    }
                )
            }
            obj.put(
                saveName,
                JSONObject().apply {
                    put("capturedAt", assoc.capturedAt)
                    put("mods", modsArray)
                }
            )
        }
        return obj.toString()
    }

    private fun parseAssociations(json: String): Map<String, SaveModAssociation> = try {
        val obj = JSONObject(json)
        val result = mutableMapOf<String, SaveModAssociation>()
        obj.keys().forEach { saveName ->
            val item = obj.getJSONObject(saveName)
            val modsArray = item.getJSONArray("mods")
            val mods = (0 until modsArray.length()).map { i ->
                val modObj = modsArray.getJSONObject(i)
                AssociatedMod(
                    uniqueID = modObj.getString("uniqueID"),
                    name = modObj.getString("name"),
                    version = modObj.getString("version")
                )
            }.toSet()
            result[saveName] = SaveModAssociation(
                saveFolderName = saveName,
                enabledMods = mods,
                capturedAt = item.optLong("capturedAt", 0)
            )
        }
        result
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to parse save mod associations", e)
        emptyMap()
    }

    // ── Serialization ───────────────────────────────────────────────────

    private fun serializeUpdateCache(updates: Map<String, ModUpdateInfo>): String {
        val obj = JSONObject()
        updates.forEach { (id, info) ->
            obj.put(
                id,
                JSONObject().apply {
                    put("uniqueID", info.uniqueID)
                    put("installedVersion", info.installedVersion)
                    put("latestVersion", info.latestVersion)
                    put("updateUrl", info.updateUrl ?: JSONObject.NULL)
                    put("source", info.source)
                    put("changelogHtml", info.changelogHtml ?: JSONObject.NULL)
                }
            )
        }
        return obj.toString()
    }

    private fun parseUpdateCache(json: String): Map<String, ModUpdateInfo> = try {
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
                changelogHtml = item.optString("changelogHtml").takeIf { it != "null" && it.isNotBlank() }
            )
        }
        result
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to parse update cache", e)
        emptyMap()
    }

    private fun serializeMetadata(metadata: Map<String, ModMetadata>): String {
        val obj = JSONObject()
        metadata.forEach { (id, meta) ->
            obj.put(
                id,
                JSONObject().apply {
                    put("installedFrom", meta.installedFrom ?: JSONObject.NULL)
                    put("installedAt", meta.installedAt)
                }
            )
        }
        return obj.toString()
    }

    private fun parseMetadata(json: String): Map<String, ModMetadata> = try {
        val obj = JSONObject(json)
        val result = mutableMapOf<String, ModMetadata>()
        obj.keys().forEach { key ->
            val item = obj.getJSONObject(key)
            result[key] = ModMetadata(
                installedFrom = item.optString("installedFrom").takeIf { it != "null" && it.isNotBlank() },
                installedAt = item.optLong("installedAt", 0)
            )
        }
        result
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to parse mod metadata", e)
        emptyMap()
    }
}

data class ModMetadata(val installedFrom: String? = null, val installedAt: Long = 0)
