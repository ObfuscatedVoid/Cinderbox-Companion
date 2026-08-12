package com.sdvsync.mods.api

import org.json.JSONObject

enum class NexusApiFailure {
    API_KEY_REQUIRED,
    AUTHENTICATION,
    RATE_LIMITED,
    SERVER,
    GRAPHQL,
    INVALID_RESPONSE,
    HTTP
}

internal fun classifyNexusHttpFailure(statusCode: Int): NexusApiFailure = when (statusCode) {
    401, 403 -> NexusApiFailure.AUTHENTICATION
    429 -> NexusApiFailure.RATE_LIMITED
    in 500..599 -> NexusApiFailure.SERVER
    else -> NexusApiFailure.HTTP
}

internal fun nexusHttpException(statusCode: Int, body: String): NexusApiException {
    val serverMessage = runCatching {
        JSONObject(body).optString("message").takeIf(String::isNotBlank)
    }.getOrNull()
    return NexusApiException(
        failure = classifyNexusHttpFailure(statusCode),
        statusCode = statusCode,
        message = serverMessage ?: "Nexus API request failed with HTTP $statusCode"
    )
}

internal fun handleApiKeyValidationResponse(statusCode: Int, body: String): Boolean = when {
    statusCode in 200..299 -> true
    statusCode == 401 || statusCode == 403 -> false
    else -> throw nexusHttpException(statusCode, body)
}

class NexusApiException(
    val failure: NexusApiFailure,
    val statusCode: Int? = null,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
