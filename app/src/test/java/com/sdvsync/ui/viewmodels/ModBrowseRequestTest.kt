package com.sdvsync.ui.viewmodels

import com.sdvsync.R
import com.sdvsync.mods.ModMetadata
import com.sdvsync.mods.api.NexusApiException
import com.sdvsync.mods.api.NexusApiFailure
import com.sdvsync.mods.models.InstalledMod
import com.sdvsync.mods.models.ModManifest
import com.sdvsync.mods.models.RemoteMod
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModBrowseRequestTest {
    @Test
    fun `installed badges use source and mod identifiers from metadata`() {
        val installedMods = listOf(
            installedMod("Pathoschild.ContentPatcher"),
            installedMod("Example.ManualInstall")
        )
        val metadata = mapOf(
            "pathoschild.contentpatcher" to ModMetadata(installedFrom = "Nexus:1915"),
            "Unused.Metadata" to ModMetadata(installedFrom = "nexus:999")
        )

        val result = collectInstalledSourceIds(installedMods, metadata)

        assertEquals(setOf("nexus:1915"), result)
        assertTrue(result.contains(remoteModSourceIdentifier("nexus", "1915")))
    }

    @Test
    fun `later search pages append unique source identifiers`() {
        val firstPage = listOf(remoteMod("1", "First"), remoteMod("2", "Second"))
        val secondPage = listOf(remoteMod("2", "Duplicate"), remoteMod("3", "Third"))

        val result = mergeSearchPages(firstPage, secondPage)

        assertEquals(listOf("1", "2", "3"), result.map(RemoteMod::modId))
        assertEquals("Second", result[1].name)
    }

    @Test
    fun `a one-character query stays local`() {
        assertTrue(searchNeedsMoreCharacters("d"))
        assertTrue(searchNeedsMoreCharacters(" d "))
        assertFalse(searchNeedsMoreCharacters("da"))
        assertFalse(searchNeedsMoreCharacters(" "))
    }

    @Test
    fun `only the newest browse request remains current`() {
        val gate = BrowseRequestGate()
        val first = gate.next()
        val second = gate.next()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
        assertEquals(second, gate.current())
    }

    @Test
    fun `network errors remain retryable`() {
        val result = classifyNexusError(IOException(), R.string.mods_error_load_failed)

        assertEquals(R.string.mods_error_network, result.messageRes)
        assertEquals(NexusErrorAction.RETRY, result.action)
    }

    @Test
    fun `authentication errors ask for a replacement key`() {
        val result = classifyNexusError(
            NexusApiException(NexusApiFailure.AUTHENTICATION, 401, "Unauthorized"),
            R.string.mods_error_load_failed
        )

        assertEquals(R.string.mods_error_api_key_rejected, result.messageRes)
        assertEquals(NexusErrorAction.REPLACE_API_KEY, result.action)
    }

    @Test
    fun `rate limits do not offer an immediate retry`() {
        val result = classifyNexusError(
            NexusApiException(NexusApiFailure.RATE_LIMITED, 429, "Rate limited"),
            R.string.mods_error_load_failed
        )

        assertEquals(R.string.mods_error_rate_limited, result.messageRes)
        assertEquals(NexusErrorAction.NONE, result.action)
    }

    @Test
    fun `schema failures direct users to an app update`() {
        val result = classifyNexusError(
            NexusApiException(NexusApiFailure.GRAPHQL, message = "Schema mismatch"),
            R.string.mods_error_search_failed
        )

        assertEquals(R.string.mods_error_incompatible_response, result.messageRes)
        assertEquals(NexusErrorAction.NONE, result.action)
    }

    private fun installedMod(uniqueId: String) = InstalledMod(
        manifest = ModManifest(
            name = uniqueId,
            author = "Author",
            version = "1.0.0",
            uniqueID = uniqueId,
            description = ""
        ),
        folderName = uniqueId,
        folderPath = "/mods/$uniqueId",
        enabled = true
    )

    private fun remoteMod(modId: String, name: String) = RemoteMod(
        sourceId = "nexus",
        modId = modId,
        name = name,
        author = "Author",
        summary = "",
        version = "1.0.0"
    )
}
