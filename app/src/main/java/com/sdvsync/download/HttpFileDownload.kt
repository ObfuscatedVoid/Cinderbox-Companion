package com.sdvsync.download

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal suspend fun downloadToFile(
    client: OkHttpClient,
    url: String,
    destination: File,
    onProgress: (Long, Long) -> Unit = { _, _ -> }
): File = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).get().build()
    require(request.url.isHttps) { "Downloads require HTTPS" }
    val call = client.newBuilder().followSslRedirects(false).build().newCall(request)
    coroutineScope {
        // Close the socket on cancellation even while a response-body read is blocked.
        val cancellation = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = checkNotNull(response.body) { "Empty response body" }
                body.byteStream().use { copyDownload(it, destination, body.contentLength(), onProgress) }
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            cancellation.cancel()
        }
    }
}

internal suspend fun copyDownload(
    input: InputStream,
    destination: File,
    expectedSize: Long,
    onProgress: (Long, Long) -> Unit = { _, _ -> }
): File {
    val temporary = File.createTempFile(".download-", ".part", destination.parentFile)
    try {
        var total = 0L
        temporary.outputStream().buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                check(count > 0) { "Download made no progress" }
                output.write(buffer, 0, count)
                total += count
                onProgress(total, expectedSize)
            }
        }
        check(total > 0 && (expectedSize < 0 || total == expectedSize)) { "Incomplete download" }
        currentCoroutineContext().ensureActive()
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
        return destination
    } finally {
        temporary.delete()
    }
}
