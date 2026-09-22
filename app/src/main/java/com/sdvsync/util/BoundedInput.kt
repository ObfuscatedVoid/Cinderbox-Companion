package com.sdvsync.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MAX_SAVE_BYTES = 128 * 1024 * 1024

internal fun InputStream.readLimited(limit: Int): ByteArray {
    require(limit >= 0)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size.toLong(), limit.toLong() - total + 1).toInt())
        if (count == -1) break
        check(count > 0) { "Input made no progress" }
        check(count <= limit - total) { "File exceeds the supported size limit" }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}
