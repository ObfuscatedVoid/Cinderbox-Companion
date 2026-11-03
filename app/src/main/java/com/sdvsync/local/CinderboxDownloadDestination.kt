package com.sdvsync.local

import com.sdvsync.cinderbox.CinderboxPaths
import java.io.File

/**
 * R43: one production game destination. This is a filesystem guard, not an Android
 * permission grant or authority to replace an existing Cinderbox installation.
 *
 * All reads in [inspect] are non-mutating. The separate explicit start operation
 * checks writable storage after the destination and ownership gates have passed.
 */
object CinderboxDownloadDestination {
    enum class Code {
        READY_NEW_INSTALL,
        WRONG_DESTINATION,
        UNSAFE_PATH,
        CINDERBOX_NOT_CONFIGURED,
        DESKTOP_NOT_CONFIGURED,
        STORAGE_INACCESSIBLE,
        EMPTY_GAME_DIRECTORY_PRESENT,
        EXISTING_GAME_PROTECTED
    }

    data class Assessment(val code: Code, val explanation: String) {
        val canStartNewDownload: Boolean get() = code == Code.READY_NEW_INSTALL
    }

    val destination: File get() = File(CinderboxPaths.GAME_FILES_DIR)

    fun isExactDestination(path: String): Boolean = path == CinderboxPaths.GAME_FILES_DIR

    fun inspect(selected: File): Assessment = inspectForLayout(
        selected,
        File(CinderboxPaths.ROOT_DIR),
        File(CinderboxPaths.DESKTOP_DIR),
        destination
    )

    /** Isolated fixture seam: production always uses [inspect] with fixed paths. */
    internal fun inspectForLayout(
        selected: File, root: File, desktop: File, gameFiles: File
    ): Assessment {
        if (!selected.isAbsolute || selected.path != gameFiles.absolutePath) {
            return Assessment(Code.WRONG_DESTINATION,
                "Downloads can only be installed in Cinderbox's GameFiles folder.")
        }
        if (!PlatformStoragePaths.inspect(selected).safe) {
            return Assessment(Code.UNSAFE_PATH,
                "Cinderbox's game path contains an unsafe or unavailable storage link.")
        }
        if (!root.isDirectory) {
            return Assessment(Code.CINDERBOX_NOT_CONFIGURED,
                "Cinderbox's storage folder was not found. Open Cinderbox to set it up first.")
        }
        if (!desktop.isDirectory) {
            return Assessment(Code.DESKTOP_NOT_CONFIGURED,
                "Cinderbox's desktop folder was not found. Open Cinderbox before downloading.")
        }
        if (!desktop.canRead() || !desktop.canWrite()) {
            return Assessment(Code.STORAGE_INACCESSIBLE,
                "Cinderbox's storage isn't accessible for writing. Check Android file access.")
        }
        if (!selected.exists()) {
            return Assessment(Code.READY_NEW_INSTALL,
                "Game files will be installed in Cinderbox. Write access will be checked when you start.")
        }
        if (!selected.isDirectory || !selected.canRead()) {
            return Assessment(Code.STORAGE_INACCESSIBLE,
                "Cinderbox's GameFiles folder cannot be accessed. No game files were changed.")
        }
        return when (M23InstallationDiscovery.game(selected).status) {
            M23InstallationDiscovery.Status.MISSING -> Assessment(
                Code.EMPTY_GAME_DIRECTORY_PRESENT,
                "GameFiles already exists. To protect existing data, automatic replacement is blocked."
            )
            M23InstallationDiscovery.Status.INACCESSIBLE -> Assessment(
                Code.STORAGE_INACCESSIBLE,
                "Cinderbox's GameFiles folder cannot be inspected. Check file access."
            )
            M23InstallationDiscovery.Status.UNSAFE_LINK -> Assessment(
                Code.UNSAFE_PATH,
                "Cinderbox's GameFiles folder contains unsafe links. No files were changed."
            )
            else -> Assessment(
                Code.EXISTING_GAME_PROTECTED,
                "An existing Cinderbox game was found. Updating it requires a safe rollback transaction, which is not enabled yet."
            )
        }
    }

    /**
     * The sole exception to an absent target is finalizing our own M24 promotion,
     * which must be proven by the private checkpoint and token in the caller.
     * This boolean is not an authority by itself; the manager/service must also
     * execute GameDownloadRecovery.requireMatching and isPromotedStage.
     */
    fun requireWritableForInstall(selected: File, ownedPromotedRecovery: Boolean = false) {
        val assessment = inspect(selected)
        check(assessment.canStartNewDownload ||
            (ownedPromotedRecovery && assessment.code == Code.EXISTING_GAME_PROTECTED)) {
            assessment.explanation
        }
        StoragePreflight.requireWritable(selected)
    }
}
