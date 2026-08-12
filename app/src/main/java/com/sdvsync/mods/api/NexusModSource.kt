package com.sdvsync.mods.api

import com.sdvsync.BuildConfig
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
class NexusModSource(private val httpClient: OkHttpClient, private val dataStore: ModDataStore) : ModSource {

    companion object {
        private const val BASE_URL = "https://api.nexusmods.com/v1"
        private const val GRAPHQL_URL = "https://api.nexusmods.com/v2/graphql"
        private const val GAME_DOMAIN = "stardewvalley"
        private const val APPLICATION_NAME = "Cinderbox Companion"
        private const val CACHE_DURATION_MS = 60 * 60 * 1000L
    }

    override val sourceId = "nexus"
    override val displayName = "Nexus Mods"

    private val cacheMutex = Mutex()
    private var trendingCache: Pair<Long, List<RemoteMod>>? = null
    private var latestAddedCache: Pair<Long, List<RemoteMod>>? = null
    private var latestUpdatedCache: Pair<Long, List<RemoteMod>>? = null

    private fun getApiKey(): String? = dataStore.getNexusApiKey()?.trim()?.takeIf(String::isNotEmpty)

    override suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedApiKey = apiKey.trim()
        if (normalizedApiKey.isEmpty()) {
            throw NexusApiException(
                NexusApiFailure.API_KEY_REQUIRED,
                message = "A Nexus API key is required for validation"
            )
        }

        val request = nexusRequest("$BASE_URL/users/validate.json", normalizedApiKey)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            handleApiKeyValidationResponse(response.code, response.body?.string().orEmpty())
        }
    }

    override suspend fun search(query: String, page: Int): ModSearchResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw missingApiKeyException()

        val normalizedQuery = query.trim()
        require(normalizedQuery.length >= NEXUS_MIN_SEARCH_QUERY_LENGTH) {
            "Nexus search requires at least $NEXUS_MIN_SEARCH_QUERY_LENGTH characters"
        }
        val operation = createNexusSearchOperation(normalizedQuery, page)
        val variables = JSONObject(operation.variables)

        val body = JSONObject().apply {
            put("query", operation.document)
            put("variables", variables)
        }

        val request = nexusRequest(GRAPHQL_URL, apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val bodyString = executeRequest(request)
        val json = parseJsonObject(bodyString, "GraphQL")

        val errors = json.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val msg = errors.optJSONObject(0)?.optString("message", "Unknown GraphQL error")
                ?: "Unknown GraphQL error"
            throw NexusApiException(NexusApiFailure.GRAPHQL, message = msg)
        }

        val data = json.optJSONObject("data")?.optJSONObject("mods")
            ?: throw NexusApiException(
                NexusApiFailure.INVALID_RESPONSE,
                message = "Unexpected Nexus GraphQL response format"
            )
        val nodes = data.optJSONArray("nodes")
            ?: throw NexusApiException(
                NexusApiFailure.INVALID_RESPONSE,
                message = "Nexus GraphQL response did not contain mod nodes"
            )
        if (!data.has("totalCount")) {
            throw NexusApiException(
                NexusApiFailure.INVALID_RESPONSE,
                message = "Nexus GraphQL response did not contain a total count"
            )
        }
        val totalCount = data.optInt("totalCount", 0)
        val offset = operation.variables.getValue("offset") as Int

        val mods = (0 until nodes.length()).mapNotNull { i ->
            val node = nodes.optJSONObject(i) ?: return@mapNotNull null
            RemoteMod(
                sourceId = sourceId,
                modId = node.optInt("modId", 0).toString(),
                name = node.optString("name", "Unknown"),
                author = node.optString("author", "Unknown"),
                summary = node.optString("summary", ""),
                version = node.optString("version", ""),
                categoryName = node.optString("categoryName", "")
                    .takeIf { it != "null" && it.isNotBlank() },
                pictureUrl = node.optString("pictureUrl", "")
                    .takeIf { it != "null" && it.isNotBlank() },
                endorsements = node.optInt("endorsementCount", 0),
                downloads = node.optInt("modDownloadCount", 0),
                lastUpdated = 0
            )
        }

        ModSearchResult(
            mods = mods,
            totalResults = totalCount,
            hasMore = offset + nodes.length() < totalCount
        )
    }

    override suspend fun getTrending(): List<RemoteMod> = getCachedOrFetch(trendingCache, { trendingCache = it }) {
        fetchModList("$BASE_URL/games/$GAME_DOMAIN/mods/trending.json")
    }

    override suspend fun getLatestAdded(): List<RemoteMod> = getCachedOrFetch(latestAddedCache, {
        latestAddedCache = it
    }) {
        fetchModList("$BASE_URL/games/$GAME_DOMAIN/mods/latest_added.json")
    }

    override suspend fun getLatestUpdated(): List<RemoteMod> = getCachedOrFetch(latestUpdatedCache, {
        latestUpdatedCache = it
    }) {
        fetchModList("$BASE_URL/games/$GAME_DOMAIN/mods/latest_updated.json")
    }

    override suspend fun getModDetails(modId: String): RemoteMod = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw missingApiKeyException()

        val request = nexusRequest("$BASE_URL/games/$GAME_DOMAIN/mods/$modId.json", apiKey)
            .get()
            .build()

        val json = parseJsonObject(executeRequest(request), "mod details")

        parseModFromV1(json)
    }

    override suspend fun getModFiles(modId: String): List<RemoteModFile> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw missingApiKeyException()

        val request = nexusRequest("$BASE_URL/games/$GAME_DOMAIN/mods/$modId/files.json", apiKey)
            .get()
            .build()

        val json = parseJsonObject(executeRequest(request), "mod files")
        val files = json.optJSONArray("files") ?: JSONArray()

        (0 until files.length()).mapNotNull { i ->
            val file = files.optJSONObject(i) ?: return@mapNotNull null
            RemoteModFile(
                fileId = file.optInt("file_id", 0).toString(),
                fileName = file.optString("file_name", "unknown"),
                fileVersion = file.optString("version", "").takeIf { it != "null" }?.trim() ?: "",
                fileSize = file.optLong("size_in_bytes", 0),
                isPrimary = file.optBoolean("is_primary", false),
                categoryName = file.optString("category_name", "").let {
                    if (it == "null" ||
                        it.isBlank()
                    ) {
                        "MAIN"
                    } else {
                        it
                    }
                },
                uploadedAt = file.optLong("uploaded_timestamp", 0) * 1000,
                description = file.optString("description", "").takeIf { it != "null" } ?: "",
                changelogHtml = file.optString("changelog_html", "").takeIf { it != "null" && it.isNotBlank() },
                modVersion = file.optString("mod_version", "").takeIf { it != "null" && it.isNotBlank() }
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
    suspend fun getDownloadUrl(modId: String, fileId: String, nxmKey: String?, nxmExpires: String?): String =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey() ?: throw missingApiKeyException()

            val baseUrl = "$BASE_URL/games/$GAME_DOMAIN/mods/$modId/files/$fileId/download_link.json"
            val url = if (nxmKey != null && nxmExpires != null) {
                "$baseUrl?key=$nxmKey&expires=$nxmExpires"
            } else {
                baseUrl
            }

            val request = nexusRequest(url, apiKey)
                .get()
                .build()

            val json = parseJsonArray(executeRequest(request), "download links")

            if (json.length() == 0) throw IllegalStateException("No download links available")

            // Return the first (preferred) CDN URL
            json.optJSONObject(0)?.optString("URI")
                ?: throw IllegalStateException("No download URL in response")
        }

    private suspend fun fetchModList(url: String): List<RemoteMod> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw missingApiKeyException()

        val request = nexusRequest(url, apiKey)
            .get()
            .build()

        val json = parseJsonArray(executeRequest(request), "mod list")

        (0 until json.length()).mapNotNull { i ->
            val obj = json.optJSONObject(i) ?: return@mapNotNull null
            parseModFromV1(obj)
        }
    }

    private fun parseModFromV1(json: JSONObject): RemoteMod = RemoteMod(
        sourceId = sourceId,
        modId = json.optInt("mod_id", 0).toString(),
        name = json.optString("name", "Unknown"),
        author = json.optString("author", "Unknown"),
        summary = json.optString("summary", ""),
        description = json.optString("description", "").takeIf { it != "null" && it.isNotBlank() },
        version = json.optString("version", ""),
        categoryName = json.optString("category_name", "").takeIf { it != "null" && it.isNotBlank() },
        pictureUrl = json.optString("picture_url", "").takeIf { it != "null" && it.isNotBlank() },
        endorsements = json.optInt("endorsement_count", 0),
        downloads = json.optInt("mod_downloads", 0),
        lastUpdated = json.optLong("updated_timestamp", 0) * 1000
    )

    private fun missingApiKeyException() = NexusApiException(
        NexusApiFailure.API_KEY_REQUIRED,
        message = "No Nexus API key configured"
    )

    private fun nexusRequest(url: String, apiKey: String): Request.Builder = Request.Builder()
        .url(url)
        .header("apikey", apiKey)
        .header("Application-Name", APPLICATION_NAME)
        .header("Application-Version", BuildConfig.VERSION_NAME)

    private fun executeRequest(request: Request): String = httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw nexusHttpException(response.code, body)
        body
    }

    private fun parseJsonObject(body: String, endpoint: String): JSONObject = try {
        JSONObject(body)
    } catch (e: Exception) {
        throw NexusApiException(
            NexusApiFailure.INVALID_RESPONSE,
            message = "Nexus $endpoint response was not a JSON object",
            cause = e
        )
    }

    private fun parseJsonArray(body: String, endpoint: String): JSONArray = try {
        JSONArray(body)
    } catch (e: Exception) {
        throw NexusApiException(
            NexusApiFailure.INVALID_RESPONSE,
            message = "Nexus $endpoint response was not a JSON array",
            cause = e
        )
    }

    private suspend fun getCachedOrFetch(
        cache: Pair<Long, List<RemoteMod>>?,
        setCache: (Pair<Long, List<RemoteMod>>) -> Unit,
        fetch: suspend () -> List<RemoteMod>
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
