package com.sdvsync.steam

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class CloudUploadPlanTest {
    private val folder = "Farmer_123"
    private val prefix = "%WinAppDataRoaming%StardewValley/Saves/$folder/"
    private val data = "save".toByteArray()

    private fun remote(filename: String = "SaveGameInfo", pathPrefix: String = prefix) =
        CloudFile(filename, data.size, MessageDigest.getInstance("SHA-1").digest(data), 1L, pathPrefix)

    @Test
    fun `unchanged files are not recommitted`() {
        assertTrue(planCloudUploads(folder, mapOf("SaveGameInfo" to data), listOf(remote())).isEmpty())
    }

    @Test
    fun `only changed files are uploaded`() {
        val updated = "updated".toByteArray()
        val plan =
            planCloudUploads(folder, mapOf("SaveGameInfo" to updated, folder to data), listOf(remote(), remote(folder)))
        assertEquals(setOf("${prefix}SaveGameInfo"), plan.keys)
        assertArrayEquals(updated, plan.values.single())
    }

    @Test
    fun `new saves use the desktop cloud path`() {
        assertEquals(
            setOf("${prefix}SaveGameInfo"),
            planCloudUploads(folder, mapOf("SaveGameInfo" to data), emptyList()).keys
        )
    }

    @Test
    fun `full paths are preserved when steam splits a directory into the filename`() {
        val file = remote("StardewValley/Saves/$folder/SaveGameInfo", "%WinAppDataRoaming%")
        val plan = planCloudUploads(folder, mapOf("SaveGameInfo" to "new".toByteArray()), listOf(file))
        assertEquals(setOf(file.fullPath), plan.keys)
    }

    @Test
    fun `unrelated saves and duplicate copies are untouched`() {
        val canonical = remote()
        val duplicate = remote(pathPrefix = "$folder/")
        val unrelated = remote(pathPrefix = "Other_456/")
        val plan =
            planCloudUploads(
                folder,
                mapOf("SaveGameInfo" to "new".toByteArray()),
                listOf(duplicate, unrelated, canonical)
            )
        assertEquals(setOf(canonical.fullPath), plan.keys)
    }
}
