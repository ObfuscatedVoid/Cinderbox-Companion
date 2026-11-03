package com.sdvsync.qualification

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sdvsync.download.GameDownloadRecovery
import com.sdvsync.download.GameFileIntegrity
import com.sdvsync.download.R43BUpdateCoordinator
import com.sdvsync.download.R43BUpdateSwap
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device-local synthetic fixtures ONLY; never uses Cinderbox's external GameFiles. */
@RunWith(AndroidJUnit4::class)
class R43B4ConsolidatedDeviceTest {
    private data class Fixture(val owner: File, val game: File, val stage: File,
        val token: String, val swap: R43BUpdateSwap, val coordinator: R43BUpdateCoordinator,
        val root: File)

    private fun fixture(block: (Fixture) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.canonicalFile
        val root = File(cache, "r43b4-device-${UUID.randomUUID()}")
        assertTrue(root.mkdir())
        try {
            val desktop = File(root, "desktop").apply { assertTrue(mkdir()) }
            val game = File(desktop, "GameFiles").apply { assertTrue(mkdir()) }
            File(game, "game.dll").writeText("old")
            File(desktop, "Saves").apply { assertTrue(mkdir()) }
            File(desktop, "Saves/saved.txt").writeText("save")
            val owner = File(root, "app-private").apply { assertTrue(mkdir()) }
            assertTrue(GameFileIntegrity(owner).verifyAndRecord(game).passed)
            val token = UUID.randomUUID().toString()
            val stage = File(desktop, ".GameFiles.cinderbox-m24-stage-$token")
                .apply { assertTrue(mkdir()) }
            File(stage, ".cinderbox-download.session").writeText(token)
            File(stage, "game.dll").writeText("new")
            val p = Properties().apply {
                setProperty("schema", "1")
                setProperty("app", "413150")
                setProperty("directory", game.canonicalPath)
                setProperty("branch", "public")
                setProperty("os", "windows")
                setProperty("verify", "true")
                setProperty("update_mode", "true")
                setProperty("token", token)
                setProperty("steam_build_id", "100")
                setProperty("staging_directory", stage.canonicalPath)
                setProperty("pics_depot_manifest_fingerprint", "a".repeat(64))
            }
            File(owner, "m6-game-download.properties").outputStream().use { p.store(it, "fixture") }
            block(Fixture(owner, game, stage, token,
                R43BUpdateSwap.forFixture(owner, game),
                R43BUpdateCoordinator.forFixture(owner, game), root))
        } finally {
            assertEquals(cache, root.canonicalFile.parentFile)
            assertTrue(root.name.startsWith("r43b4-device-"))
            assertTrue(root.deleteRecursively())
        }
    }

    @Test fun r43b4d_01PublishedUpdateKeepsRollbackAndSaves() = fixture { f -> runBlocking {
        val result = f.coordinator.publishPrepared({ f.swap.recordVerifiedStage(f.token) }) {
            assertEquals("new", File(it, "game.dll").readText())
        }
        assertEquals(R43BUpdateCoordinator.Result.PUBLISHED_VERIFIED_WITH_BACKUP, result)
        assertEquals("old", File(f.swap.inspect()!!.backup, "game.dll").readText())
        assertEquals("save", File(f.root, "desktop/Saves/saved.txt").readText())
    } }

    @Test fun r43b4d_02ExplicitRollbackKeepsNewVersion() = fixture { f -> runBlocking {
        f.coordinator.publishPrepared({ f.swap.recordVerifiedStage(f.token) }) { }
        assertEquals(R43BUpdateCoordinator.Result.ROLLED_BACK_NEW_PRESERVED,
            f.coordinator.rollbackPublished())
        assertEquals("old", File(f.game, "game.dll").readText())
        assertEquals("new", File(f.swap.inspect()!!.displaced, "game.dll").readText())
        assertTrue(GameFileIntegrity(f.owner).verifyRecorded(f.game).passed)
    } }

    @Test fun r43b4d_03CrashBetweenRenamesRestoresOldGame() = fixture { f ->
        com.sdvsync.download.R43BLedgerArchive(f.owner).capture(f.token, f.game)
        val state = f.swap.recordVerifiedStage(f.token)
        Files.move(f.game.toPath(), state.backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertEquals(R43BUpdateCoordinator.Result.ORIGINAL_RESTORED,
            f.coordinator.reconcileWithoutSteam())
        assertEquals("old", File(f.game, "game.dll").readText())
        assertEquals("new", File(f.stage, "game.dll").readText())
    }

    @Test fun r43b4d_04ChangedOriginalRefusesPublication() = fixture { f -> runBlocking {
        File(f.game, "game.dll").writeText("changed by user")
        try {
            f.coordinator.publishPrepared({ f.swap.recordVerifiedStage(f.token) }) { }
            fail("An altered installed game was accepted")
        } catch (_: IllegalStateException) { }
        assertEquals("changed by user", File(f.game, "game.dll").readText())
        assertTrue(f.stage.exists())
    } }

    @Test fun r43b4d_05TargetConflictNeverOverwritesForeignData() = fixture { f ->
        com.sdvsync.download.R43BLedgerArchive(f.owner).capture(f.token, f.game)
        val state = f.swap.recordVerifiedStage(f.token)
        Files.move(f.game.toPath(), state.backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertTrue(f.game.mkdir())
        File(f.game, "foreign.bin").writeText("untouched")
        assertEquals(R43BUpdateCoordinator.Result.MANUAL_REVIEW,
            f.coordinator.reconcileWithoutSteam())
        assertEquals("untouched", File(f.game, "foreign.bin").readText())
        assertEquals("old", File(state.backup, "game.dll").readText())
    }
}
