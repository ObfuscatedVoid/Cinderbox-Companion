package com.sdvsync.ui.policy

import com.sdvsync.local.CinderboxDownloadDestination.Code
import com.sdvsync.local.M23InstallationDiscovery.Status
import org.junit.Assert.*
import org.junit.Test

class R43DDestinationPresentationTest {
    private val ui = R43DDestinationPresentation

    @Test fun readyDoesNotAuthorizeDownloadOrInventSteamEvidence() {
        val result = ui.from(Code.READY_NEW_INSTALL, Status.MISSING)
        assertEquals(R43DDestinationPresentation.Kind.AVAILABLE_FOR_CHECKS, result.kind)
        assertTrue(result.detail.contains("checked separately"))
    }

    @Test fun protectedInstallCannotBeDescribedAsAvailable() {
        val result = ui.from(Code.EXISTING_GAME_PROTECTED, Status.CANDIDATE_UNVERIFIED)
        assertEquals(R43DDestinationPresentation.Kind.EXISTING_PROTECTED, result.kind)
        assertTrue(result.detail.contains("cannot replace"))
    }

    @Test fun everyUnsafePathAndPermissionFailureMapsToBlocked() {
        for (code in listOf(Code.STORAGE_INACCESSIBLE, Code.UNSAFE_PATH, Code.WRONG_DESTINATION)) {
            assertEquals(R43DDestinationPresentation.Kind.ACCESS_BLOCKED, ui.from(code, Status.MISSING).kind)
        }
    }

    @Test fun absentLayoutMustRequestCinderboxSetup() {
        for (code in listOf(Code.CINDERBOX_NOT_CONFIGURED, Code.DESKTOP_NOT_CONFIGURED)) {
            assertEquals(R43DDestinationPresentation.Kind.SETUP_REQUIRED, ui.from(code, Status.MISSING).kind)
        }
    }

    @Test fun emptyExistingFolderOrInterruptedSessionNeedsManualReview() {
        assertEquals(R43DDestinationPresentation.Kind.MANUAL_REVIEW, ui.from(Code.EMPTY_GAME_DIRECTORY_PRESENT, Status.MISSING).kind)
        assertEquals(R43DDestinationPresentation.Kind.MANUAL_REVIEW,
            ui.from(Code.READY_NEW_INSTALL, Status.RECOVERY_REVIEW).kind)
        assertEquals(R43DDestinationPresentation.Kind.MANUAL_REVIEW,
            ui.from(Code.EXISTING_GAME_PROTECTED, Status.CONFLICT).kind)
        assertEquals(R43DDestinationPresentation.Kind.MANUAL_REVIEW,
            ui.from(Code.READY_NEW_INSTALL, Status.CANDIDATE_UNVERIFIED).kind)
        assertEquals(R43DDestinationPresentation.Kind.MANUAL_REVIEW,
            ui.from(Code.EXISTING_GAME_PROTECTED, Status.MISSING,
                "Unclaimed staging requires manual review").kind)
    }

    @Test fun arbitraryRecoveryTextDoesNotGrantEligibility() {
        assertEquals(R43DDestinationPresentation.Kind.EXISTING_PROTECTED,
            ui.from(Code.EXISTING_GAME_PROTECTED, Status.MISSING, "No protected update transaction").kind)
        assertFalse(ui.needsManualReview(Status.MISSING, "No protected update transaction"))
    }

    @Test fun noHumanStatusContainsRawStoragePathOrDebugDumps() {
        for (location in Code.values()) for (game in Status.values()) {
            val result = ui.from(location, game)
            val display = listOf(result.title, result.detail, result.nextStep).joinToString(" ")
            assertFalse(display.contains("/storage/"))
            assertFalse(display.contains(".GameFiles"))
            assertFalse(display.contains("Exception"))
            assertFalse(display.contains("traceback", ignoreCase = true))
        }
    }
}
