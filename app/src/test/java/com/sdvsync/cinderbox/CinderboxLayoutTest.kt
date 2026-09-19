package com.sdvsync.cinderbox

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinderboxLayoutTest {

    @Test
    fun `empty root is not installed and has no leftover content`() {
        withLayout { layout ->
            assertFalse(layout.isInstalled())
            assertFalse(layout.hasLegacyContent())
        }
    }

    @Test
    fun `desktop directory counts as installed`() {
        withLayout { layout ->
            assertTrue(layout.desktopDir.mkdirs())
            assertTrue(layout.isInstalled())
            assertFalse(layout.hasLegacyContent())
        }
    }

    @Test
    fun `populated legacy saves count as leftover content`() {
        withLayout { layout ->
            val save = layout.legacySavesDir.resolve("Farmer_1")
            assertTrue(save.mkdirs())
            save.resolve("SaveGameInfo").writeText("<Farmer/>")

            assertTrue(layout.hasLegacyContent())
            assertTrue(layout.isInstalled())
        }
    }

    @Test
    fun `empty legacy directory is not leftover content`() {
        withLayout { layout ->
            assertTrue(layout.legacySavesDir.mkdirs())
            assertFalse(layout.hasLegacyContent())
        }
    }

    @Test
    fun `migrate moves saves mods and game files into desktop`() {
        withLayout { layout ->
            layout.legacySavesDir.resolve("Farmer_1").mkdirs()
            layout.legacySavesDir.resolve("Farmer_1").resolve("SaveGameInfo").writeText("save")
            layout.legacyModsDir.resolve("CoolMod").mkdirs()
            layout.legacyModsDir.resolve("CoolMod").resolve("manifest.json").writeText("{}")
            layout.legacyGameFilesDir.mkdirs()
            layout.legacyGameFilesDir.resolve("Stardew Valley.dll").writeText("dll")

            val result = layout.migrate()

            assertTrue(result.isSuccess)
            assertEquals(listOf("Saves", "Mods", "GameFiles"), result.moved)
            assertTrue(result.leftoverConflicts.isEmpty())
            assertFalse(layout.hasLegacyContent())
            assertTrue(layout.savesDir.resolve("Farmer_1").resolve("SaveGameInfo").isFile)
            assertTrue(layout.modsDir.resolve("CoolMod").resolve("manifest.json").isFile)
            assertTrue(layout.gameFilesDir.resolve("Stardew Valley.dll").isFile)
            assertFalse(layout.legacySavesDir.exists())
            assertFalse(layout.legacyModsDir.exists())
            assertFalse(layout.legacyGameFilesDir.exists())
        }
    }

    @Test
    fun `migrate merges into an existing desktop tree`() {
        withLayout { layout ->
            layout.savesDir.mkdirs()
            layout.savesDir.resolve("Existing_2").mkdirs()
            layout.legacySavesDir.resolve("Farmer_1").mkdirs()
            layout.legacySavesDir.resolve("Farmer_1").resolve("SaveGameInfo").writeText("save")

            val result = layout.migrate()

            assertTrue(result.isSuccess)
            assertEquals(listOf("Saves"), result.moved)
            assertTrue(layout.savesDir.resolve("Farmer_1").resolve("SaveGameInfo").isFile)
            assertTrue(layout.savesDir.resolve("Existing_2").isDirectory)
            assertFalse(layout.hasLegacyContent())
        }
    }

    @Test
    fun `migrate leaves conflicting names in the old location`() {
        withLayout { layout ->
            layout.savesDir.resolve("Farmer_1").mkdirs()
            layout.savesDir.resolve("Farmer_1").resolve("SaveGameInfo").writeText("new")
            layout.legacySavesDir.resolve("Farmer_1").mkdirs()
            layout.legacySavesDir.resolve("Farmer_1").resolve("SaveGameInfo").writeText("old")

            val result = layout.migrate()

            assertTrue(result.isSuccess)
            assertTrue(result.leftoverConflicts.any { it.contains("Farmer_1") })
            assertEquals("new", layout.savesDir.resolve("Farmer_1").resolve("SaveGameInfo").readText())
            assertEquals("old", layout.legacySavesDir.resolve("Farmer_1").resolve("SaveGameInfo").readText())
            assertTrue(layout.hasLegacyContent())
        }
    }

    @Test
    fun `migrate does not touch sibling folders outside Saves Mods GameFiles`() {
        withLayout { layout ->
            layout.root.resolve("smapi-internal").mkdirs()
            layout.root.resolve("smapi-internal").resolve("SMAPI.dll").writeText("smapi")
            layout.root.resolve("config.ini").writeText("keep")
            layout.legacySavesDir.resolve("Farmer_1").mkdirs()
            layout.legacySavesDir.resolve("Farmer_1").resolve("SaveGameInfo").writeText("save")

            layout.migrate()

            assertTrue(layout.root.resolve("smapi-internal").resolve("SMAPI.dll").isFile)
            assertEquals("keep", layout.root.resolve("config.ini").readText())
            assertEquals("save", layout.savesDir.resolve("Farmer_1").resolve("SaveGameInfo").readText())
        }
    }

    @Test
    fun `migrate is a no-op when there is nothing leftover`() {
        withLayout { layout ->
            layout.desktopDir.mkdirs()
            val result = layout.migrate()
            assertTrue(result.isSuccess)
            assertTrue(result.moved.isEmpty())
            assertTrue(result.leftoverConflicts.isEmpty())
        }
    }

    @Test
    fun `conflicting saves stay whole even when different files exist`() {
        withLayout { layout ->
            val old = layout.legacySavesDir.resolve("Farmer_1").apply { mkdirs() }
            old.resolve("Farmer_1").writeText("old main")
            old.resolve("SaveGameInfo").writeText("old metadata")
            val current = layout.savesDir.resolve("Farmer_1").apply { mkdirs() }
            current.resolve("SaveGameInfo").writeText("new metadata")
            val result = layout.migrate()
            assertEquals(listOf("Saves/Farmer_1"), result.leftoverConflicts)
            assertEquals("old main", old.resolve("Farmer_1").readText())
            assertFalse(current.resolve("Farmer_1").exists())
        }
    }

    @Test
    fun `conflicting mods and game files are not mixed`() {
        withLayout { layout ->
            val oldMod = layout.legacyModsDir.resolve("Example").apply { mkdirs() }
            oldMod.resolve("old.dll").writeText("old")
            val newMod = layout.modsDir.resolve("Example").apply { mkdirs() }
            newMod.resolve("new.dll").writeText("new")
            layout.legacyGameFilesDir.mkdirs()
            layout.legacyGameFilesDir.resolve("old.dll").writeText("old")
            layout.gameFilesDir.mkdirs()
            layout.gameFilesDir.resolve("new.dll").writeText("new")
            val result = layout.migrate()
            assertEquals(listOf("Mods/Example", "GameFiles"), result.leftoverConflicts)
            assertFalse(newMod.resolve("old.dll").exists())
            assertFalse(layout.gameFilesDir.resolve("old.dll").exists())
            assertTrue(oldMod.resolve("old.dll").exists())
            assertTrue(layout.legacyGameFilesDir.resolve("old.dll").exists())
        }
    }

    @Test
    fun `a blocked destination reports failure and preserves the source`() {
        withLayout { layout ->
            layout.legacySavesDir.mkdirs()
            layout.legacySavesDir.resolve("keep").writeText("keep")
            layout.desktopDir.writeText("blocked")
            val result = layout.migrate()
            assertFalse(result.isSuccess)
            assertEquals("keep", layout.legacySavesDir.resolve("keep").readText())
            assertEquals("blocked", layout.desktopDir.readText())
        }
    }

    @Test
    fun `an empty migration does not create an installation`() {
        withLayout { layout ->
            assertTrue(layout.migrate().isSuccess)
            assertFalse(layout.isInstalled())
        }
    }

    private fun withLayout(block: (CinderboxLayout) -> Unit) {
        val root = Files.createTempDirectory("cinderbox-layout").toFile()
        try {
            block(CinderboxLayout(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
