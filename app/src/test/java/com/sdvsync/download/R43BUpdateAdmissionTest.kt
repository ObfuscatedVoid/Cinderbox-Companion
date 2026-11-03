package com.sdvsync.download

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** All tests are confined to TemporaryFolder; no real Cinderbox installation. */
class R43BUpdateAdmissionTest {
    @get:Rule val tmp = TemporaryFolder()
    private data class Fixture(val parent: File, val game: File, val privateDir: File)

    private fun fixture(): Fixture {
        val root = tmp.newFolder()
        val desktop = File(root, "desktop")
        assertTrue(desktop.mkdir())
        val game = File(desktop, "GameFiles")
        assertTrue(game.mkdir())
        File(game, "Stardew Valley.dll").writeText("ORIGINAL_STEAM_GAME")
        File(game, "Content").mkdir()
        File(game, "Content/game.xnb").writeText("ORIGINAL_CONTENT")
        val privateDir = tmp.newFolder()
        return Fixture(root, game, privateDir)
    }

    private fun record(f: Fixture) {
        assertTrue(GameFileIntegrity(f.privateDir).verifyAndRecord(f.game).passed)
    }

    private fun verdict(f: Fixture): R43BUpdateAdmission.Assessment =
        R43BUpdateAdmission.inspectFixture(f.privateDir, f.game)

    @Test fun unchangedInstallationWithPrivateLedgerIsOnlyEligibleCase() {
        val f = fixture()
        record(f)
        val result = verdict(f)
        assertEquals(R43BUpdateAdmission.Code.READY_FOR_VERIFIED_UPDATE, result.code)
        assertTrue(result.eligible)
        assertEquals(2, result.observedFiles)
    }

    @Test fun importedGameWithoutLocalLedgerIsNotAuthorized() {
        val f = fixture()
        assertEquals(R43BUpdateAdmission.Code.NO_INSTALLED_INTEGRITY_LEDGER, verdict(f).code)
    }

    @Test fun modifiedGameFileBlocksPreservingOriginal() {
        val f = fixture()
        record(f)
        File(f.game, "Content/game.xnb").writeText("MODIFIED")
        assertEquals(R43BUpdateAdmission.Code.EXISTING_GAME_CHANGED, verdict(f).code)
    }

    @Test fun unknownExtraFileBlocksPreservingOriginal() {
        val f = fixture()
        record(f)
        File(f.game, "user-extension.dat").writeText("KEEP")
        assertEquals(R43BUpdateAdmission.Code.EXISTING_GAME_CHANGED, verdict(f).code)
        assertEquals("KEEP", File(f.game, "user-extension.dat").readText())
    }

    @Test fun removedFileBlocksPreservingOriginal() {
        val f = fixture()
        record(f)
        assertTrue(File(f.game, "Stardew Valley.dll").delete())
        assertEquals(R43BUpdateAdmission.Code.EXISTING_GAME_CHANGED, verdict(f).code)
    }

    @Test fun nestedModsFolderBlocksEvenWhenRecordedInLocalLedger() {
        val f = fixture()
        File(f.game, "Mods").mkdir()
        File(f.game, "Mods/MyMod.dll").writeText("PERSONAL")
        record(f)
        assertEquals(R43BUpdateAdmission.Code.PROTECTED_USER_CONTENT, verdict(f).code)
    }

    @Test fun nestedSavesAndSmapiAreProtected() {
        for (child in listOf("Saves", "smapi-internal", "SMAPI")) {
            val f = fixture()
            File(f.game, child).mkdir()
            assertEquals(R43BUpdateAdmission.Code.PROTECTED_USER_CONTENT, verdict(f).code)
        }
    }

    @Test fun siblingSavesModsSmapiRemainUntouched() {
        val f = fixture()
        record(f)
        File(f.parent, "desktop/Saves").mkdir()
        File(f.parent, "desktop/Mods").mkdir()
        File(f.parent, "smapi-internal").mkdir()
        val paths = listOf("desktop/Saves/keep.dat", "desktop/Mods/keep.dat", "smapi-internal/keep.dat")
        paths.forEach { File(f.parent, it).writeText("PRESERVE") }
        assertTrue(verdict(f).eligible)
        paths.forEach { assertEquals("PRESERVE", File(f.parent, it).readText()) }
    }

    @Test fun incompleteDownloadCheckpointBlocks() {
        val f = fixture()
        record(f)
        File(f.privateDir, "m6-game-download.properties").writeText("unfinished")
        assertEquals(R43BUpdateAdmission.Code.RECOVERY_REVIEW_REQUIRED, verdict(f).code)
    }

    @Test fun previousSwapJournalBlocks() {
        val f = fixture()
        record(f)
        File(f.privateDir, "r43b-update-swap.properties").writeText("unfinished")
        assertEquals(R43BUpdateAdmission.Code.RECOVERY_REVIEW_REQUIRED, verdict(f).code)
    }

    @Test fun symlinkUnderGameFilesFailsClosed() {
        val f = fixture()
        record(f)
        val link = File(f.game, "Content/link")
        Files.createSymbolicLink(link.toPath(), File(f.game, "Stardew Valley.dll").toPath())
        assertFalse(verdict(f).eligible)
    }

    @Test fun stagedVerificationDoesNotReplaceInstalledBaseline() {
        val f = fixture()
        record(f)
        val ledger = File(f.privateDir, "m6-game-integrity.properties")
        val original = ledger.readBytes()
        val stage = File(f.game.parentFile, "stage")
        assertTrue(stage.mkdir())
        File(stage, "Stardew Valley.dll").writeText("NEW_STEAM_GAME")
        val stageCheck = GameFileIntegrity(f.privateDir).verifyAndRecord(stage, persistLedger = false)
        assertTrue(stageCheck.passed)
        assertTrue(original.contentEquals(ledger.readBytes()))
        assertTrue(verdict(f).eligible)
    }

    @Test fun finalPublishedReceiptCanReplaceInstalledBaseline() {
        val f = fixture()
        record(f)
        val installed = File(f.game, "Stardew Valley.dll")
        installed.writeText("NEW_PUBLISHED_GAME")
        assertEquals(R43BUpdateAdmission.Code.EXISTING_GAME_CHANGED, verdict(f).code)
        assertTrue(GameFileIntegrity(f.privateDir).verifyAndRecord(f.game).passed)
        assertTrue(verdict(f).eligible)
    }
}
