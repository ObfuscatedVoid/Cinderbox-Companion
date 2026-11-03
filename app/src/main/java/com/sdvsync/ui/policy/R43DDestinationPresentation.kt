package com.sdvsync.ui.policy

import com.sdvsync.local.CinderboxDownloadDestination
import com.sdvsync.local.M23InstallationDiscovery

/**
 * R43-D: read-only, public-facing representation of the single Cinderbox location.
 * Presentation is NEVER write authorization. Backend Manager/Service gates own that.
 * No absolute paths, directory pickers, protected journal secrets or raw exceptions.
 */
object R43DDestinationPresentation {
    enum class Kind { AVAILABLE_FOR_CHECKS, SETUP_REQUIRED, ACCESS_BLOCKED, EXISTING_PROTECTED, MANUAL_REVIEW }

    data class Notice(
        val kind: Kind,
        val title: String,
        val detail: String,
        val nextStep: String
    )

    /** Never use free-form recovery text as evidence that a write is allowed. */
    fun needsManualReview(game: M23InstallationDiscovery.Status, recoveryStatus: String?): Boolean =
        game in setOf(M23InstallationDiscovery.Status.RECOVERY_REVIEW,
            M23InstallationDiscovery.Status.CONFLICT) ||
            recoveryStatus?.contains("manual review", ignoreCase = true) == true ||
            recoveryStatus?.contains("requires review", ignoreCase = true) == true

    fun from(
        location: CinderboxDownloadDestination.Code,
        game: M23InstallationDiscovery.Status,
        recoveryStatus: String? = null
    ): Notice {
        // A stale or racing discovery must not make an occupied location look empty.
        if (needsManualReview(game, recoveryStatus) ||
            (location == CinderboxDownloadDestination.Code.READY_NEW_INSTALL &&
             game != M23InstallationDiscovery.Status.MISSING)) return Notice(
            Kind.MANUAL_REVIEW,
            "Review needed before continuing",
            "Cinderbox has files or recovery information that need careful inspection. No files were changed.",
            "Open Installation Health. Do not remove game files or restart an update blindly."
        )
        return when (location) {
            CinderboxDownloadDestination.Code.READY_NEW_INSTALL -> Notice(
                Kind.AVAILABLE_FOR_CHECKS,
                "Location available for setup",
                "Cinderbox's game location is available. Steam, permissions, free space and file integrity are checked separately before any download.",
                "Choose a game version and start the normal checks."
            )
            CinderboxDownloadDestination.Code.CINDERBOX_NOT_CONFIGURED,
            CinderboxDownloadDestination.Code.DESKTOP_NOT_CONFIGURED -> Notice(
                Kind.SETUP_REQUIRED,
                "Finish Cinderbox setup",
                "The main Cinderbox app has not prepared its game storage yet.",
                "Open Cinderbox once, finish setup, then return and refresh."
            )
            CinderboxDownloadDestination.Code.STORAGE_INACCESSIBLE,
            CinderboxDownloadDestination.Code.UNSAFE_PATH,
            CinderboxDownloadDestination.Code.WRONG_DESTINATION -> Notice(
                Kind.ACCESS_BLOCKED,
                "Game storage needs attention",
                "Cinderbox's fixed game location cannot be used safely right now. No files were changed.",
                "Check Android file access and Installation Health; do not select another download folder."
            )
            CinderboxDownloadDestination.Code.EMPTY_GAME_DIRECTORY_PRESENT -> Notice(
                Kind.MANUAL_REVIEW,
                "Existing game folder needs review",
                "The folder already exists. Even an empty folder cannot be silently replaced by a new download.",
                "Inspect the location in Installation Health before choosing an action."
            )
            CinderboxDownloadDestination.Code.EXISTING_GAME_PROTECTED -> Notice(
                Kind.EXISTING_PROTECTED,
                "Existing game is protected",
                "Cinderbox already has game files here. This build cannot replace them with a Steam update.",
                "Review the installation; keep the original game, saves, mods and SMAPI intact."
            )
        }
    }

    /** Human-readable discovery summary; not a statement of authenticated Steam integrity. */
    fun installedLabel(status: M23InstallationDiscovery.Status): String = when (status) {
        M23InstallationDiscovery.Status.MISSING -> "Not found"
        M23InstallationDiscovery.Status.INACCESSIBLE -> "Not accessible"
        M23InstallationDiscovery.Status.UNSAFE_LINK -> "Needs storage review"
        M23InstallationDiscovery.Status.PARTIAL -> "Incomplete or unverified"
        M23InstallationDiscovery.Status.RECOVERY_REVIEW -> "Recovery review needed"
        M23InstallationDiscovery.Status.CANDIDATE_UNVERIFIED -> "Found (not Steam-verified)"
        M23InstallationDiscovery.Status.CONFLICT -> "Conflicting files need review"
    }
}
