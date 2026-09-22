package com.sdvsync.sync

import com.sdvsync.steam.CloudFile
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SaveContentTest {
    private val files = mapOf("Farmer_1" to "save".toByteArray(), "SaveGameInfo" to "info".toByteArray())

    @Test
    fun onlyMatchingBytesAndFileSetsAreInSync() {
        assertTrue(saveContentMatches(files, files.mapValues { it.value.copyOf() }))
        assertFalse(saveContentMatches(files, files + ("Farmer_1" to "changed".toByteArray())))
        assertFalse(saveContentMatches(files, files - "Farmer_1"))
        assertFalse(saveContentMatches(emptyMap(), emptyMap<String, ByteArray>()))
    }

    @Test
    fun cloudHashesMustMatchEveryFile() {
        val cloud = files.map { (name, bytes) ->
            CloudFile(name, bytes.size, MessageDigest.getInstance("SHA-1").digest(bytes), 0, "Saves/Farmer_1")
        }
        assertTrue(saveContentMatches(files, cloud))
        assertFalse(saveContentMatches(files + ("Farmer_1" to "different".toByteArray()), cloud))
        assertFalse(saveContentMatches(files, cloud.drop(1)))
    }
}
