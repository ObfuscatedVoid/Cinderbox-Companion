package com.sdvsync.download

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Host/JVM fixtures ONLY. Never interact with an installed phone game. */
class R43BUpdateCoordinatorTest {
    @get:Rule val tmp = TemporaryFolder()
    private data class Fixture(
        val privateRoot: File, val target: File, val stage: File,
        val token: String, val swap: R43BUpdateSwap,
        val coordinator: R43BUpdateCoordinator, val root: File
    )

    private fun fixture(): Fixture {
        val root = tmp.newFolder("game-root")
        val desktop = File(root, "desktop").apply { assertTrue(mkdir()) }
        val game = File(desktop, "GameFiles").apply { assertTrue(mkdir()) }
        File(game, "Stardew Valley.dll").writeText("installed-original")
        File(desktop, "Saves").apply { assertTrue(mkdir()) }
        File(desktop, "Saves/keep.sav").writeText("save")
        File(desktop, "Mods").apply { assertTrue(mkdir()) }
        File(desktop, "Mods/keep.json").writeText("mod")
        File(root, "smapi-internal").apply { assertTrue(mkdir()) }
        File(root, "smapi-internal/keep.ini").writeText("smapi")
        val owner = tmp.newFolder("app-data")
        assertTrue(GameFileIntegrity(owner).verifyAndRecord(game).passed)
        val token = UUID.randomUUID().toString()
        val stage = File(desktop, ".GameFiles.cinderbox-m24-stage-$token").apply {
            assertTrue(mkdir())
        }
        File(stage, ".cinderbox-download.session").writeText(token)
        File(stage, "Stardew Valley.dll").writeText("verified-new-build")
        val props = Properties().apply {
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
        File(owner, "m6-game-download.properties").outputStream().use { props.store(it, "fixture") }
        return Fixture(owner, game, stage, token,
            R43BUpdateSwap.forFixture(owner, game),
            R43BUpdateCoordinator.forFixture(owner, game), root)
    }

    private fun assertUserContentPreserved(f: Fixture) {
        assertEquals("save", File(f.root, "desktop/Saves/keep.sav").readText())
        assertEquals("mod", File(f.root, "desktop/Mods/keep.json").readText())
        assertEquals("smapi", File(f.root, "smapi-internal/keep.ini").readText())
    }

    private suspend fun Fixture.publish(verify: suspend (File) -> Unit = { }) =
        coordinator.publishPrepared({ swap.recordVerifiedStage(token) }, verify)

    @Test fun publishRetainsOriginalAndCommitsNewLedger() = runBlocking {
        val f = fixture()
        assertEquals(R43BUpdateCoordinator.Result.PUBLISHED_VERIFIED_WITH_BACKUP,
            f.publish { assertEquals("verified-new-build", File(it, "Stardew Valley.dll").readText()) })
        val state = f.swap.inspect()!!
        assertEquals("installed-original", File(state.backup, "Stardew Valley.dll").readText())
        assertEquals("verified-new-build", File(f.target, "Stardew Valley.dll").readText())
        assertTrue(GameFileIntegrity(f.privateRoot).verifyRecorded(f.target).passed)
        assertFalse(File(f.privateRoot, "m6-game-download.properties").exists())
        assertTrue(File(f.target, ".cinderbox-download.session").isFile)
        assertUserContentPreserved(f)
    }

    @Test fun explicitRollbackRestoresOriginalReceiptAndRetainsNewGame() = runBlocking {
        val f = fixture()
        f.publish()
        assertEquals(R43BUpdateCoordinator.Result.ROLLED_BACK_NEW_PRESERVED,
            f.coordinator.rollbackPublished())
        assertEquals("installed-original", File(f.target, "Stardew Valley.dll").readText())
        assertTrue(GameFileIntegrity(f.privateRoot).verifyRecorded(f.target).passed)
        assertEquals("verified-new-build", File(f.swap.inspect()!!.displaced,
            "Stardew Valley.dll").readText())
        assertUserContentPreserved(f)
    }

    @Test fun postPromotionVerificationFailureRestoresOriginal() = runBlocking {
        val f = fixture()
        try {
            f.publish { throw IllegalStateException("Synthetic post-publish identity drift") }
            fail("Identity drift must abort completion")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Synthetic"))
        }
        assertEquals("installed-original", File(f.target, "Stardew Valley.dll").readText())
        assertEquals("verified-new-build", File(f.swap.inspect()!!.displaced,
            "Stardew Valley.dll").readText())
        assertTrue(GameFileIntegrity(f.privateRoot).verifyRecorded(f.target).passed)
        assertUserContentPreserved(f)
    }

    @Test fun changedOriginalBlocksBeforeAnyRename() = runBlocking {
        val f = fixture()
        File(f.target, "Stardew Valley.dll").writeText("modified after baseline")
        try { f.publish(); fail("Changed original was accepted") }
        catch (_: IllegalStateException) { }
        assertEquals("modified after baseline", File(f.target, "Stardew Valley.dll").readText())
        assertTrue(f.stage.isDirectory)
        assertEquals(null, f.swap.inspect())
        assertUserContentPreserved(f)
    }

    @Test fun newUserManagedFolderBlocksBeforeAnyRename() = runBlocking {
        val f = fixture()
        File(f.target, "Mods").apply { assertTrue(mkdir()) }
        try { f.publish(); fail("Nested Mods were accepted") }
        catch (_: IllegalStateException) { }
        assertTrue(f.target.exists())
        assertTrue(f.stage.exists())
        assertEquals(null, f.swap.inspect())
        assertUserContentPreserved(f)
    }

    @Test fun unknownEmptyUserDirectoryBlocksBeforeAnyRename() = runBlocking {
        val f = fixture()
        assertTrue(File(f.target, "CustomEmptyDirectory").mkdir())
        try { f.publish(); fail("Untracked empty directory must block update") }
        catch (_: IllegalStateException) { }
        assertTrue(File(f.target, "CustomEmptyDirectory").isDirectory)
        assertTrue(f.stage.isDirectory)
        assertEquals(null, f.swap.inspect())
        assertUserContentPreserved(f)
    }

    @Test fun missingRollbackLedgerBlocksBeforeAnyRename() = runBlocking {
        val f = fixture()
        assertTrue(File(f.privateRoot, "m6-game-integrity.properties").delete())
        try { f.publish(); fail("Missing receipt was accepted") }
        catch (_: Exception) { }
        assertTrue(f.target.exists())
        assertTrue(f.stage.exists())
        assertEquals(null, f.swap.inspect())
    }

    @Test fun conflictingRollbackWitnessStopsPublication() = runBlocking {
        val f = fixture()
        f.coordinator // keep constructor in use
        R43BLedgerArchive(f.privateRoot).snapshot(f.token).writeText("conflicting data")
        try { f.publish(); fail("Conflicting snapshot was accepted") }
        catch (_: IllegalStateException) { }
        assertTrue(f.target.isDirectory)
        assertTrue(f.stage.isDirectory)
        assertEquals(R43BUpdateSwap.Finding.READY_TO_PROMOTE, f.swap.reconcile())
        assertUserContentPreserved(f)
    }

    @Test fun interruptedOriginalMoveAutomaticallyRestoresWithoutOverwrite() {
        val f = fixture()
        R43BLedgerArchive(f.privateRoot).capture(f.token, f.target)
        val state = f.swap.recordVerifiedStage(f.token)
        Files.move(f.target.toPath(), state.backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertEquals(R43BUpdateCoordinator.Result.ORIGINAL_RESTORED,
            f.coordinator.reconcileWithoutSteam())
        assertEquals("installed-original", File(f.target, "Stardew Valley.dll").readText())
        assertEquals("verified-new-build", File(f.stage, "Stardew Valley.dll").readText())
        assertUserContentPreserved(f)
    }

    @Test fun interruptedPublicationRequiresReviewWithoutSteam() {
        val f = fixture()
        R43BLedgerArchive(f.privateRoot).capture(f.token, f.target)
        f.swap.recordVerifiedStage(f.token)
        f.swap.publishVerifiedStage()
        assertEquals(R43BUpdateCoordinator.Result.MANUAL_REVIEW,
            f.coordinator.reconcileWithoutSteam())
        assertEquals("installed-original", File(f.swap.inspect()!!.backup,
            "Stardew Valley.dll").readText())
        assertEquals(R43BUpdateCoordinator.Result.ROLLED_BACK_NEW_PRESERVED,
            f.coordinator.rollbackPublished())
        assertUserContentPreserved(f)
    }

    @Test fun finalizedPublicationIsRecognizedReadOnlyAfterRestart() = runBlocking {
        val f = fixture()
        f.publish()
        val newCoordinator = R43BUpdateCoordinator.forFixture(f.privateRoot, f.target)
        assertEquals(R43BUpdateCoordinator.Result.PUBLISHED_VERIFIED_WITH_BACKUP,
            newCoordinator.reconcileWithoutSteam())
        assertUserContentPreserved(f)
    }

    @Test fun fabricatedStageMarkerCannotAuthorizePublication() = runBlocking {
        val f = fixture()
        File(f.stage, ".cinderbox-download.session").writeText(UUID.randomUUID().toString())
        try { f.publish(); fail("Unowned stage was accepted") }
        catch (_: IllegalStateException) { }
        assertEquals("installed-original", File(f.target, "Stardew Valley.dll").readText())
        assertEquals(null, f.swap.inspect())
        assertUserContentPreserved(f)
    }

    @Test fun conflictAfterBackupRenameNeverOverwritesThirdPartyGame() {
        val f = fixture()
        R43BLedgerArchive(f.privateRoot).capture(f.token, f.target)
        val state = f.swap.recordVerifiedStage(f.token)
        Files.move(f.target.toPath(), state.backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertTrue(f.target.mkdir())
        File(f.target, "foreign.bin").writeText("someone else")
        assertEquals(R43BUpdateCoordinator.Result.MANUAL_REVIEW,
            f.coordinator.reconcileWithoutSteam())
        assertEquals("someone else", File(f.target, "foreign.bin").readText())
        assertEquals("installed-original", File(state.backup,
            "Stardew Valley.dll").readText())
        assertUserContentPreserved(f)
    }
}
