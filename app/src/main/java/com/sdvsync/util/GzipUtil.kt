package com.sdvsync.util

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

object GzipUtil {

    private const val TAG = "GzipUtil"

    /** Gzip magic bytes: 0x1f 0x8b */
    fun isGzip(data: ByteArray): Boolean {
        return data.size >= 2 &&
            data[0] == 0x1f.toByte() &&
            data[1] == 0x8b.toByte()
    }

    /** ZIP magic bytes: 0x50 0x4b 0x03 0x04 ("PK\x03\x04") */
    fun isZip(data: ByteArray): Boolean {
        return data.size >= 4 &&
            data[0] == 0x50.toByte() &&
            data[1] == 0x4b.toByte() &&
            data[2] == 0x03.toByte() &&
            data[3] == 0x04.toByte()
    }

    /**
     * Decompress data if it's gzip or ZIP compressed, return as-is otherwise.
     * Stardew Valley 1.6+ saves are stored as ZIP archives on Steam Cloud.
     */
    fun decompressIfCompressed(data: ByteArray): ByteArray {
        if (isZip(data)) {
            return try {
                val extracted = extractFirstZipEntry(data)
                Log.d(TAG, "Extracted ZIP: ${data.size} -> ${extracted.size} bytes")
                extracted
            } catch (e: Exception) {
                Log.e(TAG, "ZIP extraction failed (size=${data.size})", e)
                data
            }
        }
        if (isGzip(data)) {
            return try {
                val decompressed = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
                Log.d(TAG, "Decompressed gzip: ${data.size} -> ${decompressed.size} bytes")
                decompressed
            } catch (e: Exception) {
                Log.e(TAG, "Gzip decompression failed (size=${data.size})", e)
                data
            }
        }
        Log.d(TAG, "Data is not compressed (size=${data.size}, first4=${data.take(4).joinToString(" ") { "%02x".format(it) }})")
        return data
    }

    /** Legacy alias — delegates to decompressIfCompressed. */
    fun decompressIfGzip(data: ByteArray): ByteArray = decompressIfCompressed(data)

    /** Extract the first entry from a ZIP archive. */
    private fun extractFirstZipEntry(data: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            val entry = zis.nextEntry
                ?: throw RuntimeException("ZIP archive is empty")
            Log.d(TAG, "ZIP entry: name='${entry.name}', compressedSize=${entry.compressedSize}, size=${entry.size}")
            val content = zis.readBytes()
            zis.closeEntry()
            return content
        }
    }

    /** Gzip-compress data. */
    fun compress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }
}
