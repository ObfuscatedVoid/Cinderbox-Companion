package com.sdvsync

import android.net.Uri

internal data class NxmDownloadParams(val modId: String, val fileId: String, val key: String, val expires: String)

internal fun parseNxmUrl(uri: Uri): NxmDownloadParams? {
    if (uri.scheme != "nxm" || uri.host != "stardewvalley") return null
    val segments = uri.pathSegments
    if (segments.size != 4 || segments[0] != "mods" || segments[2] != "files") return null
    if (segments[1].toLongOrNull()?.let { it > 0 } != true ||
        segments[3].toLongOrNull()?.let { it > 0 } != true
    ) {
        return null
    }
    val key = uri.getQueryParameter("key")?.takeIf { it.isNotBlank() } ?: return null
    val expires =
        uri.getQueryParameter("expires")?.takeIf { it.toLongOrNull()?.let { time -> time > 0 } == true } ?: return null
    return NxmDownloadParams(segments[1], segments[3], key, expires)
}
