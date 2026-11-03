package com.sdvsync.download

import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.local.PlatformStoragePaths
import java.io.File
import java.nio.file.Files

/**
 * R43-B2: READ-ONLY eligibility for a future verified Steam update.
 *
 * An eligible result does not authorize a write. It only establishes that the
 * current installation has an unchanged, app-private post-publication SHA-256
 * baseline and does not contain known user-owned files inside GameFiles.
 *
 * It does not prove Steam entitlement, content provenance, or that a new staged
 * download is complete. The live M24 verification, journal ownership, and swap
 * reconciliation must be integrated before replacement is offered.
 */
internal object R43BUpdateAdmission {
    enum class Code {
        READY_FOR_VERIFIED_UPDATE,
        WRONG_LAYOUT,
        INACCESSIBLE,
        UNSAFE_PATH,
        RECOVERY_REVIEW_REQUIRED,
        PROTECTED_USER_CONTENT,
        NO_INSTALLED_INTEGRITY_LEDGER,
        EXISTING_GAME_CHANGED
    }

    data class Assessment(val code: Code, val reason: String, val observedFiles: Int = 0) {
        val eligible: Boolean get() = code == Code.READY_FOR_VERIFIED_UPDATE
    }

    private val protectedGameChildren = listOf(
        "Mods", "Saves", "smapi-internal", "SMAPI", "save-backups",
        "ErrorLogs", "Screenshots", ".config"
    )

    /** Production is permanently bound to the known Cinderbox GameFiles path. */
    fun inspect(privateRoot: File): Assessment =
        inspectCore(privateRoot, File(CinderboxPaths.GAME_FILES_DIR), fixture = false)

    /** Synthetic temp-directory seam for host/device tests; not a production option. */
    internal fun inspectFixture(privateRoot: File, gameFiles: File): Assessment =
        inspectCore(privateRoot, gameFiles, fixture = true)

    /** Only an exact existing M24 update journal can bypass the pending-download
     * block for READ-ONLY baseline rechecks; not an installation authority.
     */
    fun inspectOwnedStage(privateRoot: File, checkpoint: GameDownloadCheckpoint): Assessment =
        inspectOwnedCore(privateRoot, File(CinderboxPaths.GAME_FILES_DIR), checkpoint, false)

    internal fun inspectOwnedStageFixture(privateRoot: File, gameFiles: File,
                                          checkpoint: GameDownloadCheckpoint): Assessment =
        inspectOwnedCore(privateRoot, gameFiles, checkpoint, true)

    private fun inspectOwnedCore(privateRoot: File, gameFiles: File,
                                 checkpoint: GameDownloadCheckpoint, fixture: Boolean): Assessment {
        if (!checkpoint.updateMode || !checkpoint.verify || checkpoint.stagingDirectory == null ||
            checkpoint.directory != gameFiles.canonicalPath) {
            return refusal(Code.RECOVERY_REVIEW_REQUIRED, "No verified owned update checkpoint")
        }
        val owned = runCatching {
            val current = GameDownloadRecovery(privateRoot).pending()
            current == checkpoint && checkpoint.matches(gameFiles,
                checkpoint.branch, checkpoint.os, true)
        }.getOrDefault(false)
        if (!owned) return refusal(Code.RECOVERY_REVIEW_REQUIRED,
            "M24 checkpoint and sibling stage ownership could not be confirmed")
        return inspectCore(privateRoot, gameFiles, fixture, ownedUpdateJournal = true)
    }

    private fun refusal(code: Code, reason: String) = Assessment(code, reason)

    private fun inspectCore(privateRoot: File, gameFiles: File, fixture: Boolean,
                            ownedUpdateJournal: Boolean = false): Assessment {
        if (!fixture && gameFiles.path != CinderboxPaths.GAME_FILES_DIR) {
            return refusal(Code.WRONG_LAYOUT, "GameFiles is not Cinderbox's configured destination")
        }
        if (!gameFiles.isAbsolute || gameFiles.name != "GameFiles" ||
            gameFiles.parentFile?.name != "desktop") {
            return refusal(Code.WRONG_LAYOUT, "Expected Cinderbox desktop/GameFiles")
        }
        val safe = try { PlatformStoragePaths.requireSafe(gameFiles) }
            catch (_: Exception) {
                return refusal(Code.UNSAFE_PATH, "GameFiles contains an unsafe or unresolved path")
            }
        if (!safe.isDirectory || !safe.canRead() || !safe.canWrite() ||
            safe.parentFile?.isDirectory != true || safe.parentFile?.canWrite() != true) {
            return refusal(Code.INACCESSIBLE, "Installed game or its parent is inaccessible")
        }
        val privateLedger = File(privateRoot, "m6-game-integrity.properties")
        val downloadJournal = File(privateRoot, "m6-game-download.properties")
        val swapJournal = File(privateRoot, "r43b-update-swap.properties")
        for (record in listOf(privateLedger, downloadJournal, swapJournal)) {
            if (Files.isSymbolicLink(record.toPath())) {
                return refusal(Code.UNSAFE_PATH, "Recovery or integrity record is a symbolic link")
            }
        }
        // A leftover owner marker or pending journal can mean an interrupted M24
        // publication. An update cannot safely start until it is reconciled.
        if (File(safe, ".cinderbox-download.session").exists() ||
            (downloadJournal.exists() && !ownedUpdateJournal) || swapJournal.exists()) {
            return refusal(Code.RECOVERY_REVIEW_REQUIRED,
                "An unfinished download or previous update must be reviewed")
        }
        // Companion's sibling Saves/Mods and smapi-internal are untouched by this
        // inspector. User content nested *inside* GameFiles blocks replacement.
        for (name in protectedGameChildren) {
            val child = File(safe, name)
            if (child.exists() || Files.isSymbolicLink(child.toPath())) {
                return refusal(Code.PROTECTED_USER_CONTENT,
                    "User-managed content exists inside GameFiles; automatic replacement is blocked")
            }
        }
        if (!privateLedger.isFile || privateLedger.length() !in 1..10485760) {
            return refusal(Code.NO_INSTALLED_INTEGRITY_LEDGER,
                "No app-owned completed-installation checksum record is available")
        }
        val result = try { GameFileIntegrity(privateRoot).verifyRecorded(safe) }
            catch (_: Exception) {
                return refusal(Code.NO_INSTALLED_INTEGRITY_LEDGER,
                    "The completed-installation checksum record cannot be validated")
            }
        if (!result.passed || result.files == 0) {
            return refusal(Code.EXISTING_GAME_CHANGED,
                "GameFiles differs from its previous checksum record; preserve the installed version")
        }
        return Assessment(Code.READY_FOR_VERIFIED_UPDATE,
            "Read-only baseline check passed. A newly verified Steam stage and protected swap are still required.",
            result.files)
    }
}
