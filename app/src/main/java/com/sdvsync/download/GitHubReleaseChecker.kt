package com.sdvsync.download

import android.content.Context
import com.sdvsync.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class GitHubReleaseInfo(
    val tagName: String,
    val version: String,
    val assetUrl: String,
    val assetSize: Long,
    val assetName: String,
    val publishedAt: String
)

class GitHubReleaseChecker(private val context: Context, private val httpClient: OkHttpClient) {

    companion object {
        private const val TAG = "GitHubReleaseChecker"
        private const val PREFS_NAME = "github_releases"
        private const val CACHE_STALENESS_MS = 15 * 60 * 1000L

        const val CINDERBOX_REPO = "Ekyso/Cinderbox"
        const val SMAPI_REPO = "Ekyso/SMAPI-for-Cinderbox"
        val CINDERBOX_ASSET_PATTERN = Regex("Cinderbox-.*\\.apk")
        val SMAPI_ASSET_PATTERN = Regex("smapi-internal\\.zip")

        const val KEY_CINDERBOX_VERSION = "cinderbox_installed_version"
        const val KEY_SMAPI_VERSION = "smapi_installed_version"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<GitHubReleaseInfo, Long>>()

    suspend fun getLatestRelease(repo: String, assetNamePattern: Regex): GitHubReleaseInfo? {
        // Check in-memory cache
        val cached = memoryCache[repo]
        if (cached != null && System.currentTimeMillis() - cached.second < CACHE_STALENESS_MS) {
            return cached.first
        }

        // Check SharedPreferences cache on cold start
        val cachedJson = prefs.getString("cache_$repo", null)
        val cachedTime = prefs.getLong("cache_time_$repo", 0L)
        if (cachedJson != null && System.currentTimeMillis() - cachedTime < CACHE_STALENESS_MS) {
            val info = parseReleaseInfoFromCache(cachedJson)
            if (info != null) {
                memoryCache[repo] = info to cachedTime
                return info
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$repo/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "GitHub API error: ${response.code} for $repo")
                    return@withContext cachedJson?.let { parseReleaseInfoFromCache(it) }
                }

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tagName = json.getString("tag_name")
                val publishedAt = json.optString("published_at", "")

                val assets = json.getJSONArray("assets")
                var matchedInfo: GitHubReleaseInfo? = null

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (assetNamePattern.matches(name)) {
                        matchedInfo = GitHubReleaseInfo(
                            tagName = tagName,
                            version = tagName.removePrefix("v"),
                            assetUrl = asset.getString("browser_download_url"),
                            assetSize = asset.getLong("size"),
                            assetName = name,
                            publishedAt = publishedAt
                        )
                        break
                    }
                }

                if (matchedInfo != null) {
                    memoryCache[repo] = matchedInfo to System.currentTimeMillis()
                    // Persist to SharedPreferences
                    prefs.edit()
                        .putString("cache_$repo", serializeReleaseInfo(matchedInfo))
                        .putLong("cache_time_$repo", System.currentTimeMillis())
                        .apply()
                }

                matchedInfo
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to check GitHub release for $repo", e)
                cachedJson?.let { parseReleaseInfoFromCache(it) }
            }
        }
    }

    fun getInstalledVersion(key: String): String? = prefs.getString(key, null)

    fun setInstalledVersion(key: String, version: String) {
        prefs.edit().putString(key, version).apply()
    }

    fun isUpdateAvailable(installed: String?, latest: String?): Boolean {
        if (installed == null || latest == null) return false
        // Strip "v" prefix and pre-release suffix (e.g. "-beta.2")
        val installedParts = installed.removePrefix("v").split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.removePrefix("v").split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(installedParts.size, latestParts.size)) {
            val inst = installedParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > inst) return true
            if (lat < inst) return false
        }
        return false
    }

    fun invalidateCache(repo: String) {
        memoryCache.remove(repo)
        prefs.edit()
            .remove("cache_$repo")
            .remove("cache_time_$repo")
            .apply()
    }

    private fun serializeReleaseInfo(info: GitHubReleaseInfo): String = JSONObject().apply {
        put("tagName", info.tagName)
        put("version", info.version)
        put("assetUrl", info.assetUrl)
        put("assetSize", info.assetSize)
        put("assetName", info.assetName)
        put("publishedAt", info.publishedAt)
    }.toString()

    private fun parseReleaseInfoFromCache(json: String): GitHubReleaseInfo? = try {
        val obj = JSONObject(json)
        GitHubReleaseInfo(
            tagName = obj.getString("tagName"),
            version = obj.getString("version"),
            assetUrl = obj.getString("assetUrl"),
            assetSize = obj.getLong("assetSize"),
            assetName = obj.getString("assetName"),
            publishedAt = obj.optString("publishedAt", "")
        )
    } catch (e: Exception) {
        null
    }
}
