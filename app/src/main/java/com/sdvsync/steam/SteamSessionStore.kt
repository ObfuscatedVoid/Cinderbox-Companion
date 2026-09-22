package com.sdvsync.steam

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

class SteamSessionStore(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "steam_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var username: String?
        get() = prefs.getString("username", null)
        set(value) = prefs.edit().putString("username", value).apply()

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var steamId: Long
        get() = prefs.getLong("steam_id", 0L)
        set(value) = prefs.edit().putLong("steam_id", value).apply()

    var cellId: Int
        get() = prefs.getInt("cell_id", 0)
        set(value) = prefs.edit().putInt("cell_id", value).apply()

    fun hasSession(): Boolean = !username.isNullOrEmpty() && !refreshToken.isNullOrEmpty()

    fun clear() {
        prefs.edit().clear().apply()
        File(context.filesDir, "sentry").listFiles()?.forEach { it.delete() }
    }
}
