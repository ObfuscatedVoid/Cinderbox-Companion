package com.sdvsync.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.syncHistoryStore: DataStore<Preferences> by preferencesDataStore(name = "sync_history")

data class SyncHistoryEntry(
    val timestamp: String,
    val saveName: String,
    val direction: String, // "pull" or "push"
    val success: Boolean,
    val message: String,
)

class SyncHistoryStore(private val context: Context) {

    companion object {
        private val HISTORY_KEY = stringPreferencesKey("history")
        private const val MAX_ENTRIES = 50
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    suspend fun addEntry(
        saveName: String,
        direction: String,
        success: Boolean,
        message: String,
    ) {
        context.syncHistoryStore.edit { prefs ->
            val existing = prefs[HISTORY_KEY]?.let { parseEntries(it) } ?: emptyList()
            val entry = SyncHistoryEntry(
                timestamp = DATE_FORMAT.format(Date()),
                saveName = saveName,
                direction = direction,
                success = success,
                message = message,
            )
            val updated = (listOf(entry) + existing).take(MAX_ENTRIES)
            prefs[HISTORY_KEY] = serializeEntries(updated)
        }
    }

    suspend fun getHistory(): List<SyncHistoryEntry> {
        return context.syncHistoryStore.data
            .map { prefs -> prefs[HISTORY_KEY]?.let { parseEntries(it) } ?: emptyList() }
            .first()
    }

    suspend fun clear() {
        context.syncHistoryStore.edit { it.remove(HISTORY_KEY) }
    }

    private fun serializeEntries(entries: List<SyncHistoryEntry>): String {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject().apply {
                put("timestamp", entry.timestamp)
                put("saveName", entry.saveName)
                put("direction", entry.direction)
                put("success", entry.success)
                put("message", entry.message)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseEntries(json: String): List<SyncHistoryEntry> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SyncHistoryEntry(
                    timestamp = obj.getString("timestamp"),
                    saveName = obj.getString("saveName"),
                    direction = obj.getString("direction"),
                    success = obj.getBoolean("success"),
                    message = obj.getString("message"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
