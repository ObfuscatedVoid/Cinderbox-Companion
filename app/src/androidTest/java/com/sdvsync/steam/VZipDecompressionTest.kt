package com.sdvsync.steam

import `in`.dragonbra.javasteam.util.VZipUtil
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import org.junit.Assert.*
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream

class VZipDecompressionTest {
    @Test
    fun decompressesSteamChunksWithTheApplicationMemoryWorkaround() {
        val content = ByteArray(128 * 1024).apply {
            Random(42).nextBytes(this)
            copyInto(this, size / 2, 0, size / 2)
        }
        val output = ByteArray(content.size)
        val count = VZipUtil.decompress(MemoryStream(compress(content)), output)
        assertEquals(content.size, count)
        assertArrayEquals(content, output)
    }

    @Test(expected = DataFormatException::class)
    fun rejectsChunksWhoseContentChecksumDoesNotMatch() {
        val content = "Steam depot checksum regression".toByteArray()
        val archive = compress(content)
        archive[archive.size - 10] = (archive[archive.size - 10].toInt() xor 1).toByte()
        VZipUtil.decompress(MemoryStream(archive), ByteArray(content.size))
    }

    private fun compress(content: ByteArray): ByteArray {
        val options = LZMA2Options().apply { dictSize = 1 shl 16 }
        val compressed = ByteArrayOutputStream()
        val encoder = LZMAOutputStream(compressed, options, false)
        val properties = encoder.props.toByte()
        encoder.use { it.write(content) }
        val payload = compressed.toByteArray()
        val crc = CRC32().apply { update(content) }.value.toInt()
        return ByteBuffer.allocate(12 + payload.size + 10).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0x5A56.toShort())
            .put('a'.code.toByte())
            .putInt(crc)
            .put(properties)
            .putInt(options.dictSize)
            .put(payload)
            .putInt(crc)
            .putInt(content.size)
            .putShort(0x767A.toShort())
            .array()
    }
}
