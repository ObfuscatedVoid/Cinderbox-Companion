package com.sdvsync.mods.api

import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.models.ModSearchResult
import com.sdvsync.mods.models.RemoteMod
import com.sdvsync.mods.models.RemoteModFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Nexus Mods API v1 + GraphQL search implementation.
 * Requires an API key for all requests.
 */
class NexusModSource(
    private val httpClient: OkHttpClient,
    private val dataStore: ModDataStore,
) : ModSource {

    companion object {
        private const val TAG = "NexusModSource"
        private const val BASE_URL = "https://api.nexusmods.com/v1"
        private const val GRAPHQL_URL = "https://api.nexusmods.com/v2/graphql"
        private const val GAME_DOMAIN = "stardewvalley"
        private const val GAME_ID = 1303 // Nexus GraphQL gameId for SDV
        private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour
    }

    override val sourceId = "nexus"
    override val displayName = "Nexus Mods"

    // In-memory cache for list endpoints
    private val cacheMutex = Mutex()
    private var trendingCache: Pair<Long, List<RemoteMod>>? = null
    private var latestAddedCache: Pair<Long, List<RemoteMod>>? = null
    private var latestUpdatedCache: Pair<Long, List<RemoteMod>>? = null

    private fun getApiKey(): String? = dataStore.getNexusApiKey()

    override suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/users/validate.json")
                .header("apikey", apiKey)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            AppLogger.e(TAG, "API key validation failed", e)
            false
        }
    }

    override suspend fun search(query: String, page: Int): ModSearchResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw IllegalStateException("No API key configured")

        val graphqlQuery = """
            query SearchMods(${'$'}search: String!, ${'$'}gameId: Int!) {
              mods(
                filter: { gameId: { value: ${'$'}gameId, op: EQUALS } }
                searchQuery: ${'$'}search
                sort: { endorsements: DESC }
              ) {
                nodes {
                  modId
                  name
                  summary
                  author
                  pictureUrl
                  endorsementCount
                  modDownloadCount
                  updatedAt
                  version
                  categoryName
                }
                totalCount
              }
            }
        """.trimIndent()

        val variables = JSONObject().apply {
            put("search", query)
            put("gameId", GAME_ID)
        }

        val body = JSONObject().apply {
            put("query", graphqlQuery)
            put("variables", variables)
        }

        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .header("apikey", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val (bodyString, code, isSuccessful) = httpClient.newCall(request).execute().use {
            Triple(it.body?.string() ?: "", it.code, it.isSuccessful)
        }
        if (!isSuccessful) {
            val msg = try { JSONObject(bodyString).optString("message", "HTTP $code") }
                      catch (_: Exception) { "HTTP $code" }
            throw IllegalStateException(msg)
        }
        val json = JSONObject(bodyString)

        val data = json.getJSONObject("data").getJSONObject("mods")
        val nodes = data.getJSONArray("nodes")
        val totalCount = data.getInt("totalCount")

        val mods = (0 until nodes.length()).map { i ->
            val node = nodes.getJSONObject(i)
            RemoteMod(
                sourceId = sourceId,
                modId = node.getInt("modId").toString(),
                name = node.getString("name"),
                author = node.optString("author", "Unknown"),
                summary = node.optString("summary", ""),
                version = node.optString("version", ""),
                categoryName = node.optString("categoryName", "")
                    .takeIf { it.isNotBlank() },
                pictureUrl = node.optString("pictureUrl", "")
                    .takeIf { it != "null" && it.isNotBlank() },
                endorsements = node.optInt("endorsementCount", 0),
                downloads = node.optInt("modDownloadCount", 0),
                lastUpdated = 0,
            )
        }

        ModSearchResult(
            mods = mods,
            totalResults = totalCount,
            hasMore = nodes.length() < totalCount,
        )
    }

    override suspend fun getTrending(): List<RemoteMod> {
        return getCachedOrFetch(trendingCache, { trendingCache = it }) {
            fetchModList("$BASE_URL/games/$GAME_DOMAIN/mods/trending.json")
        }
    }

    override suspend fun getLatestAdded(): List<RemoteMod> {
        return getCachedOrFetch(latestAddedCache, { latestAddedCache = it }) {
            fetchModList("$BASE_URL/games/$GAME_DOMAIN/mods/latest_added.json")
        }
    }

    override suspend fun getLatestUpdated(): List<RemoteMod> {
        return getCachedOrFetch(latestUpdatedCache, { latestUpdatedCache = it }) {
            fetchModList("$BASE_URL/games/$GAME_DOMAIN/mods/latest_updated.json")
        }
    }

    override suspend fun getModDetails(modId: String): RemoteMod = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw IllegalStateException("No API key configured")

        val request = Request.Builder()
            .url("$BASE_URL/games/$GAME_DOMAIN/mods/$modId.json")
            .header("apikey", apiKey)
            .get()
            .build()

        val (bodyString, code, isSuccessful) = httpClient.newCall(request).execute().use {
            Triple(it.body?.string() ?: "", it.code, it.isSuccessful)
        }
        if (!isSuccessful) {
            val msg = try { JSONObject(bodyString).optString("message", "HTTP $code") }
                      catch (_: Exception) { "HTTP $code" }
            throw IllegalStateException(msg)
        }
        val json = JSONObject(bodyString)

        parseModFromV1(json)
    }

    override suspend fun getModFiles(modId: String): List<RemoteModFile> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw IllegalStateException("No API key configured")

        val request = Request.Builder()
            .url("$BASE_URL/games/$GAME_DOMAIN/mods/$modId/files.json")
            .header("apikey", apiKey)
            .get()
            .build()

        val (bodyString, code, isSuccessful) = httpClient.newCall(request).execute().use {
            Triple(it.body?.string() ?: "", it.code, it.isSuccessful)
        }
        if (!isSuccessful) {
            val msg = try { JSONObject(bodyString).optString("message", "HTTP $code") }
                      catch (_: Exception) { "HTTP $code" }
            throw IllegalStateException(msg)
        }
        val json = JSONObject(bodyString)
        val files = json.getJSONArray("files")

        (0 until files.length()).map { i ->
            val file = files.getJSONObject(i)
            RemoteModFile(
                fileId = file.getInt("file_id").toString(),
                fileName = file.getString("file_name"),
                fileVersion = file.optString("version", "").takeIf { it != "null" }?.trim() ?: "",
                fileSize = file.optLong("size_in_bytes", 0),
                isPrimary = file.optBoolean("is_primary", false),
                categoryName = file.optString("category_name", "").let { if (it == "null" || it.isBlank()) "MAIN" else it },
                uploadedAt = file.optLong("uploaded_timestamp", 0) * 1000,
                description = file.optString("description", "").takeIf { it != "null" } ?: "",
                changelogHtml = file.optString("changelog_html", "").takeIf { it != "null" && it.isNotBlank() },
                modVersion = file.optString("mod_version", "").takeIf { it != "null" && it.isNotBlank() },
            )
        }
    }

    override suspend fun getDownloadUrl(modId: String, fileId: String): String =
        getDownloadUrl(modId, fileId, nxmKey = null, nxmExpires = null)

    /**
     * Get download URL with optional NXM key/expires for free accounts.
     * Premium users: pass null for nxmKey/nxmExpires.
     * Free users: pass key/expires from the nxm:// URL generated by Nexus website.
     */
    suspend fun getDownloadUrl(
        modId: String,
        fileId: String,
        nxmKey: String?,
        nxmExpires: String?,
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw IllegalStateException("No API key configured")

        val baseUrl = "$BASE_URL/games/$GAME_DOMAIN/mods/$modId/files/$fileId/download_link.json"
        val url = if (nxmKey != null && nxmExpires != null) {
            "$baseUrl?key=$nxmKey&expires=$nxmExpires"
        } else {
            baseUrl
        }

        val request = Request.Builder()
            .url(url)
            .header("apikey", apiKey)
            .get()
            .build()

        val (bodyString, code, isSuccessful) = httpClient.newCall(request).execute().use {
            Triple(it.body?.string() ?: "", it.code, it.isSuccessful)
        }
        if (!isSuccessful) {
            val msg = try { JSONObject(bodyString).optString("message", "HTTP $code") }
                      catch (_: Exception) { "HTTP $code" }
            throw IllegalStateException(msg)
        }
        val json = JSONArray(bodyString)

        if (json.length() == 0) throw IllegalStateException("No download links available")

        // Return the first (preferred) CDN URL
        json.getJSONObject(0).getString("URI")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private suspend fun fetchModList(url: String): List<RemoteMod> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw IllegalStateException("No API key configured")

        val request = Request.Builder()
            .url(url)
            .header("apikey", apiKey)
            .get()
            .build()

        val (bodyString, code, isSuccessful) = httpClient.newCall(request).execute().use {
            Triple(it.body?.string() ?: "", it.code, it.isSuccessful)
        }
        if (!isSuccessful) {
            val msg = try { JSONObject(bodyString).optString("message", "HTTP $code") }
                      catch (_: Exception) { "HTTP $code" }
            throw IllegalStateException(msg)
        }
        val json = JSONArray(bodyString)

        (0 until json.length()).map { i ->
            parseModFromV1(json.getJSONObject(i))
        }
    }

    private fun parseModFromV1(json: JSONObject): RemoteMod {
        return RemoteMod(
            sourceId = sourceId,
            modId = json.getInt("mod_id").toString(),
            name = json.getString("name"),
            author = json.optString("author", "Unknown"),
            summary = json.optString("summary", ""),
            description = json.optString("description", "").takeIf { it != "null" && it.isNotBlank() },
            version = json.optString("version", ""),
            categoryName = json.optString("category_name", "").takeIf { it != "null" && it.isNotBlank() },
            pictureUrl = json.optString("picture_url", "").takeIf { it != "null" && it.isNotBlank() },
            endorsements = json.optInt("endorsement_count", 0),
            downloads = json.optInt("mod_downloads", 0),
            lastUpdated = json.optLong("updated_timestamp", 0) * 1000,
        )
    }

    private suspend fun getCachedOrFetch(
        cache: Pair<Long, List<RemoteMod>>?,
        setCache: (Pair<Long, List<RemoteMod>>) -> Unit,
        fetch: suspend () -> List<RemoteMod>,
    ): List<RemoteMod> {
        cacheMutex.withLock {
            val now = System.currentTimeMillis()
            if (cache != null && now - cache.first < CACHE_DURATION_MS) {
                return cache.second
            }
        }
        val result = fetch()
        cacheMutex.withLock {
            setCache(System.currentTimeMillis() to result)
        }
        return result
    }
}
