package com.sdvsync.download

import java.io.File
import java.util.Properties
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Temp directories only. Never accesses the phone's Cinderbox path. */
class R43BUpdateStageSessionTest {
    @get:Rule val temp = TemporaryFolder()

    private data class Fixture(
        val privateRoot: File, val target: File, val stage: File,
        val token: String, val recovery: GameDownloadRecovery
    )

    private fun fixture(update: Boolean = true): Fixture {
        val root = temp.newFolder("cinderbox-root")
        val desktop = File(root, "desktop").apply { assertTrue(mkdir()) }
        val target = File(desktop, "GameFiles").apply { assertTrue(mkdir()) }
        File(target, "Stardew Valley.dll").writeText("original verified bytes")
        val privateRoot = temp.newFolder("app-private")
        val baseline = GameFileIntegrity(privateRoot).verifyAndRecord(target)
        assertTrue(baseline.passed)
        val token = UUID.randomUUID().toString()
        val stage = File(desktop, ".GameFiles.cinderbox-m24-stage-$token")
        assertTrue(stage.mkdir())
        File(stage, ".cinderbox-download.session").writeText(token)
        File(stage, "Stardew Valley.dll").writeText("staged updated bytes")
        val p = Properties().apply {
            setProperty("schema", "1")
            setProperty("app", "413150")
            setProperty("directory", target.canonicalPath)
            setProperty("branch", "public")
            setProperty("os", "windows")
            setProperty("verify", "true")
            setProperty("token", token)
            setProperty("steam_build_id", "100")
            setProperty("staging_directory", stage.canonicalPath)
            setProperty("pics_depot_manifest_fingerprint", "a".repeat(64))
            if (update) setProperty("update_mode", "true")
        }
        File(privateRoot, "m6-game-download.properties").outputStream().use { p.store(it, "fixture only") }
        return Fixture(privateRoot, target, stage, token, GameDownloadRecovery(privateRoot))
    }

    @Test fun ownedUpdateStageCoexistsWithOriginalWithoutClaimingPublication() {
        val f = fixture()
        val cp = f.recovery.pending()!!
        assertTrue(cp.updateMode)
        assertTrue(cp.matches(f.target, "public", "windows", true))
        assertEquals(f.stage.canonicalPath, f.recovery.requireActiveStage(f.target).canonicalPath)
        assertFalse(f.recovery.isPromotedStage(f.target))
        assertEquals(R43BUpdateAdmission.Code.READY_FOR_VERIFIED_UPDATE,
            R43BUpdateAdmission.inspectOwnedStageFixture(f.privateRoot, f.target, cp).code)
    }

    @Test fun oldM24CheckpointCannotTreatOccupiedTargetAsOwnedUpdate() {
        val f = fixture(update = false)
        assertFalse(f.recovery.pending()!!.updateMode)
        assertFalse(f.recovery.pending()!!.matches(f.target, "public", "windows", true))
        assertFalse(R43BUpdateAdmission.inspectOwnedStageFixture(
            f.privateRoot, f.target, f.recovery.pending()!!).eligible)
    }

    @Test fun mutatedInstalledGameBlocksPreparedUpdate() {
        val f = fixture()
        File(f.target, "Stardew Valley.dll").writeText("user changed the game")
        assertEquals(R43BUpdateAdmission.Code.EXISTING_GAME_CHANGED,
            R43BUpdateAdmission.inspectOwnedStageFixture(
                f.privateRoot, f.target, f.recovery.pending()!!).code)
    }

    @Test fun unknownUserFileInsideGameFilesBlocksPreparedUpdate() {
        val f = fixture()
        File(f.target, "custom-not-in-baseline.txt").writeText("preserve me")
        assertEquals(R43BUpdateAdmission.Code.EXISTING_GAME_CHANGED,
            R43BUpdateAdmission.inspectOwnedStageFixture(
                f.privateRoot, f.target, f.recovery.pending()!!).code)
    }

    @Test fun nestedModsBlocksPreparedUpdateEvenIfBaselineWasOwned() {
        val f = fixture()
        File(f.target, "Mods").mkdir()
        assertEquals(R43BUpdateAdmission.Code.PROTECTED_USER_CONTENT,
            R43BUpdateAdmission.inspectOwnedStageFixture(
                f.privateRoot, f.target, f.recovery.pending()!!).code)
    }

    @Test fun missingSessionStageNeverAuthorizesUpdate() {
        val f = fixture()
        File(f.stage, "Stardew Valley.dll").delete()
        File(f.stage, ".cinderbox-download.session").delete()
        f.stage.delete()
        assertFalse(f.recovery.pending()!!.matches(f.target, "public", "windows", true))
        assertFalse(R43BUpdateAdmission.inspectOwnedStageFixture(
            f.privateRoot, f.target, f.recovery.pending()!!).eligible)
    }

    @Test fun concurrentSwapJournalBlocksNewHandoff() {
        val f = fixture()
        File(f.privateRoot, "r43b-update-swap.properties").writeText("unresolved")
        assertEquals(R43BUpdateAdmission.Code.RECOVERY_REVIEW_REQUIRED,
            R43BUpdateAdmission.inspectOwnedStageFixture(
                f.privateRoot, f.target, f.recovery.pending()!!).code)
    }

    @Test fun defaultAdmissionStillBlocksAnyIncompleteDownload() {
        val f = fixture()
        assertEquals(R43BUpdateAdmission.Code.RECOVERY_REVIEW_REQUIRED,
            R43BUpdateAdmission.inspectFixture(f.privateRoot, f.target).code)
    }

    @Test fun invalidUpdateModeValueFailsClosed() {
        val f = fixture()
        val j = File(f.privateRoot, "m6-game-download.properties")
        val p = Properties().apply { j.inputStream().use { load(it) } }
        p.setProperty("update_mode", "maybe")
        j.outputStream().use { p.store(it, "untrusted") }
        try {
            f.recovery.pending()
            fail("Corrupt update-mode flag must be rejected")
        } catch (_: IllegalStateException) { /* expected fail-closed parse */ }
    }
}
