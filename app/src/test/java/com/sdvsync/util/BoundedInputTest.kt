package com.sdvsync.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test
    fun exactLimitSucceedsAndLargerInputStopsAtLimitPlusOne() {
        assertArrayEquals(ByteArray(16), ByteArrayInputStream(ByteArray(16)).readLimited(16))
        val input = ByteArrayInputStream(ByteArray(1000))
        try {
            input.readLimited(16)
            fail("Oversized input accepted")
        } catch (_: IllegalStateException) { }
        assertEquals(983, input.available())
    }

    @Test
    fun compressedSavesRoundTripWithoutModifyingRawInput() {
        val original = "<Farmer>test</Farmer>".toByteArray()
        val compressed = GzipUtil.compress(original)
        assertArrayEquals(original, GzipUtil.decompressIfCompressed(compressed))
        assertArrayEquals(original, GzipUtil.decompressIfCompressed(original))
    }

    @Test
    fun corruptCompressedDataAndMultiFileArchivesAreRejected() {
        try {
            GzipUtil.decompressIfCompressed(byteArrayOf(0x1f, 0x8b.toByte(), 1))
            fail("Corrupt gzip accepted")
        } catch (_: java.io.IOException) { }
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for (name in listOf("a", "b")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }
        try {
            GzipUtil.decompressIfCompressed(bytes.toByteArray())
            fail("Ambiguous archive accepted")
        } catch (_: IllegalStateException) { }
    }
}
