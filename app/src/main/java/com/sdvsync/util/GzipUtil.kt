package com.sdvsync.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GzipUtil {

    /** Gzip magic bytes: 0x1f 0x8b */
    fun isGzip(data: ByteArray): Boolean {
        return data.size >= 2 &&
            data[0] == 0x1f.toByte() &&
            data[1] == 0x8b.toByte()
    }

    /** Decompress if gzip, return as-is otherwise. */
    fun decompressIfGzip(data: ByteArray): ByteArray {
        if (!isGzip(data)) return data
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    /** Gzip-compress data. */
    fun compress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }
}
