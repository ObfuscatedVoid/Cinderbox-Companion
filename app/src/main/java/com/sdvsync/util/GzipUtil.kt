package com.sdvsync.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

object GzipUtil {
    fun isGzip(data: ByteArray): Boolean = data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()

    fun isZip(data: ByteArray): Boolean = data.size >= 4 &&
        data[0] == 0x50.toByte() &&
        data[1] == 0x4b.toByte() &&
        data[2] == 0x03.toByte() &&
        data[3] == 0x04.toByte()

    fun decompressIfCompressed(data: ByteArray): ByteArray {
        require(data.size <= MAX_SAVE_BYTES) { "Save exceeds the supported size limit" }
        if (isZip(data)) {
            return ZipInputStream(ByteArrayInputStream(data)).use { zip ->
                val entry = checkNotNull(zip.nextEntry) { "Empty compressed save" }
                check(!entry.isDirectory) { "Compressed save contains a directory" }
                val content = zip.readLimited(MAX_SAVE_BYTES)
                check(zip.nextEntry == null) { "Compressed save contains multiple files" }
                content
            }
        }
        if (isGzip(data)) {
            return GZIPInputStream(ByteArrayInputStream(data)).use { it.readLimited(MAX_SAVE_BYTES) }
        }
        return data
    }

    fun compress(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(data) }
        return output.toByteArray()
    }
}
