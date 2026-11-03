package com.sdvsync.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CinderboxDownloadDestinationTest {
    @get:Rule val temp = TemporaryFolder()

    private fun fixture(): Triple<File, File, File> {
        val root = temp.newFolder("StardewValley")
        val desktop = File(root, "desktop")
        assertTrue(desktop.mkdir())
        return Triple(root, desktop, File(desktop, "GameFiles"))
    }

    @Test fun absentManagedTargetIsAllowed() {
        val (root, desktop, target) = fixture()
        val result = CinderboxDownloadDestination.inspectForLayout(target, root, desktop, target)
        assertEquals(CinderboxDownloadDestination.Code.READY_NEW_INSTALL, result.code)
        assertTrue(result.canStartNewDownload)
    }

    @Test fun wrongDestinationIsNeverProductionEligible() {
        val (root, desktop, target) = fixture()
        val alternate = File(root, "Downloads/StardewValley")
        val result = CinderboxDownloadDestination.inspectForLayout(alternate, root, desktop, target)
        assertEquals(CinderboxDownloadDestination.Code.WRONG_DESTINATION, result.code)
        assertFalse(result.canStartNewDownload)
    }

    @Test fun absentCinderboxRootBlocksWithExplanation() {
        val root = File(temp.root, "not-set-up")
        val desktop = File(root, "desktop")
        val target = File(desktop, "GameFiles")
        val result = CinderboxDownloadDestination.inspectForLayout(target, root, desktop, target)
        assertEquals(CinderboxDownloadDestination.Code.CINDERBOX_NOT_CONFIGURED, result.code)
        assertTrue(result.explanation.contains("Cinderbox"))
    }

    @Test fun absentDesktopBlocks() {
        val root = temp.newFolder("root-only")
        val desktop = File(root, "desktop")
        val target = File(desktop, "GameFiles")
        val result = CinderboxDownloadDestination.inspectForLayout(target, root, desktop, target)
        assertEquals(CinderboxDownloadDestination.Code.DESKTOP_NOT_CONFIGURED, result.code)
    }

    @Test fun evenEmptyExistingGameDirectoryIsProtected() {
        val (root, desktop, target) = fixture()
        assertTrue(target.mkdir())
        val result = CinderboxDownloadDestination.inspectForLayout(target, root, desktop, target)
        assertEquals(CinderboxDownloadDestination.Code.EMPTY_GAME_DIRECTORY_PRESENT, result.code)
        assertFalse(result.canStartNewDownload)
    }

    @Test fun existingGameBytesAreProtected() {
        val (root, desktop, target) = fixture()
        assertTrue(target.mkdir())
        File(target, "Stardew Valley.dll").writeText("game")
        val result = CinderboxDownloadDestination.inspectForLayout(target, root, desktop, target)
        assertEquals(CinderboxDownloadDestination.Code.EXISTING_GAME_PROTECTED, result.code)
        assertFalse(result.canStartNewDownload)
    }

    @Test fun anUnrelatedTargetFileIsBlocked() {
        val (root, desktop, target) = fixture()
        target.writeText("do not overwrite")
        val result = CinderboxDownloadDestination.inspectForLayout(target, root, desktop, target)
        assertEquals(CinderboxDownloadDestination.Code.STORAGE_INACCESSIBLE, result.code)
    }
}
