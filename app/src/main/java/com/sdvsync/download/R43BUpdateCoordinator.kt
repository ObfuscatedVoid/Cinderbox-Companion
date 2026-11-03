package com.sdvsync.download

import com.sdvsync.cinderbox.CinderboxPaths
import java.io.File
import java.nio.file.Files

/**
 * Consolidated R43 update coordinator.
 *
 * All production entry points remain closed until staged, recovery and device
 * acceptance is explicitly completed. The fixture seam cannot be instantiated
 * through the production path. No existing game tree is ever deleted or copied.
 */
internal object R43BLiveUpdatePolicy {
    const val LIVE_UPDATES_ENABLED: Boolean = false
    const val DEVICE_ACCEPTED: Boolean = false
}

internal class R43BUpdateCoordinator private constructor(
    private val privateRoot: File,
    private val destination: File,
    private val swap: R43BUpdateSwap,
    private val fixture: Boolean
) {
    enum class Result {
        NO_TRANSACTION, STAGE_READY, ORIGINAL_RESTORED,
        PUBLISHED_VERIFIED_WITH_BACKUP, ROLLED_BACK_NEW_PRESERVED, MANUAL_REVIEW
    }

    data class Selection(
        val branch: String,
        val os: String,
        val buildId: Long,
        val manifestFingerprint: String,
        val picsManifestIds: Map<Int, String>,
        val completedDepots: Set<Int>
    ) {
        fun toBridge() = R43BVerifiedStageBridge.BoundDepotSelection(
            branch, os, buildId, manifestFingerprint, picsManifestIds, completedDepots)
    }

    companion object {
        fun forProduction(privateRoot: File): R43BUpdateCoordinator = R43BUpdateCoordinator(
            privateRoot, File(CinderboxPaths.GAME_FILES_DIR),
            R43BUpdateSwap.forProduction(privateRoot), false
        )

        /** Temp folders ONLY. Never a production-supplied destination. */
        fun forFixture(privateRoot: File, gameFiles: File): R43BUpdateCoordinator =
            R43BUpdateCoordinator(privateRoot, gameFiles,
                R43BUpdateSwap.forFixture(privateRoot, gameFiles), true)
    }

    /** No filesystem mutation. Used to show recovery and rollback choices. */
    data class ReadOnlyStatus(
        val description: String?, val canRollback: Boolean, val canRecover: Boolean
    )

    fun inspectReadOnly(): ReadOnlyStatus {
        val state = swap.inspect() ?: return ReadOnlyStatus(null, false, false)
        val enabled = fixture || (R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED &&
            R43BLiveUpdatePolicy.DEVICE_ACCEPTED)
        val description = when (state.phase) {
            R43BUpdateSwap.Phase.PREPARED -> "Prepared game update: review before publication"
            R43BUpdateSwap.Phase.OLD_MOVED -> "Interrupted update: restore original game"
            R43BUpdateSwap.Phase.PUBLISHED -> "Published game: original backup retained"
            R43BUpdateSwap.Phase.ROLLED_BACK -> "Original restored; new version retained"
        }
        return ReadOnlyStatus(description,
            enabled && state.phase == R43BUpdateSwap.Phase.PUBLISHED,
            enabled && state.phase != R43BUpdateSwap.Phase.ROLLED_BACK)
    }

    private val recovery get() = GameDownloadRecovery(privateRoot)
    private val archive get() = R43BLedgerArchive(privateRoot)
    private val integrity get() = GameFileIntegrity(privateRoot)

    private fun requireEnabled() {
        if (!fixture) check(R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED &&
            R43BLiveUpdatePolicy.DEVICE_ACCEPTED) {
            "Existing-game updates are disabled pending device recovery acceptance"
        }
    }

    private fun requireUnchangedOriginal() {
        check(destination.isDirectory && !Files.isSymbolicLink(destination.toPath())) {
            "Existing GameFiles is not a safe directory"
        }
        // The admission policy explicitly protects empty as well as populated
        // user-owned directories, which a file-only hash ledger cannot see.
        for (name in listOf("Mods", "Saves", "smapi-internal", "SMAPI", "save-backups",
                             "ErrorLogs", "Screenshots", ".config")) {
            val child = File(destination, name)
            check(!child.exists() && !Files.isSymbolicLink(child.toPath())) {
                "User-managed data inside GameFiles blocks automatic updating"
            }
        }
        val result = integrity.verifyRecorded(destination)
        check(result.passed && result.files > 0) {
            "Installed game differs from the saved checksum baseline"
        }
    }

    /** Production call is intentionally gated; never changes the occupied game. */
    fun beginOwnedUpdate(branch: String, os: String, verify: Boolean) {
        requireEnabled()
        check(!fixture && destination.path == CinderboxPaths.GAME_FILES_DIR) {
            "Live update must use Cinderbox GameFiles"
        }
        check(swap.inspect() == null) { "Previous update needs recovery" }
        recovery.beginStagedUpdate(destination, branch, os, verify)
    }

    /**
     * Core commit path shared with synthetic crash fixtures.
     * Preparation must have *already* verified a new M24 owned stage against
     * authenticated Steam depot manifests. The verifier must recheck the actual
     * published bytes and fresh Steam selection, and throw on any disagreement.
     */
    suspend fun publishPrepared(
        prepared: suspend () -> R43BUpdateSwap.State,
        verifyPublished: suspend (File) -> Unit
    ): Result {
        requireEnabled()
        val checkpoint = recovery.pending() ?: error("Missing M24 update checkpoint")
        check(checkpoint.updateMode && checkpoint.verify && checkpoint.stagingDirectory != null) {
            "Only a verified, owned M24 update may be published"
        }
        check(checkpoint.matches(destination, checkpoint.branch, checkpoint.os, true)) {
            "M24 stage and installed game no longer match update owner"
        }
        check(swap.inspect() == null) { "Previous update transaction requires reconciliation" }
        requireUnchangedOriginal()
        val state = prepared()
        check(state.token == checkpoint.token && state.target.canonicalPath == destination.canonicalPath &&
            state.phase == R43BUpdateSwap.Phase.PREPARED) {
            "Prepared swap journal does not match the M24 stage"
        }
        // Creation order is intentional: journaled stage, ledger witness, rename.
        // Do not overwrite the installed integrity baseline before publication.
        archive.capture(state.token, destination)
        requireUnchangedOriginal()
        check(swap.reconcile() == R43BUpdateSwap.Finding.READY_TO_PROMOTE) {
            "Prepared swap has an unresolved filesystem state"
        }
        try {
            // BOTH same-volume renames belong inside the guarded boundary.
            // The original implementation only caught failures after this call,
            // leaving a failed second rename unreconciled until manual recovery.
            swap.publishVerifiedStage()
            verifyPublished(destination)
            finishVerifiedPublication(state.token)
        } catch (failure: Exception) {
            // If the backup move completed but the stage move failed, restore
            // the original into an EMPTY slot. Never overwrite a foreign tree.
            // A pre-publication failure must not attempt a published rollback.
            try {
                when (swap.reconcile()) {
                    R43BUpdateSwap.Finding.PUBLISHED_WITH_BACKUP ->
                        rollbackOwnedPublication(state.token)
                    R43BUpdateSwap.Finding.ORIGINAL_RESTORED,
                    R43BUpdateSwap.Finding.READY_TO_PROMOTE -> Unit
                    else -> throw IllegalStateException(
                        "Update interrupted with conflicting filesystem state; manual review required")
                }
            } catch (recoveryFailure: Exception) {
                failure.addSuppressed(recoveryFailure)
            }
            throw failure
        }
        return Result.PUBLISHED_VERIFIED_WITH_BACKUP
    }

    /** Real production boundary: authenticated selection is rechecked twice. */
    suspend fun publishFromSteam(
        selection: Selection,
        refreshSelection: suspend () -> Selection
    ): Result {
        requireEnabled()
        check(!fixture && destination.path == CinderboxPaths.GAME_FILES_DIR)
        return publishPrepared(
            prepared = {
                R43BVerifiedStageBridge.prepareForProduction(
                    privateRoot, selection.toBridge(), { refreshSelection().toBridge() })
            },
            verifyPublished = { published ->
                check(refreshSelection() == selection) {
                    "Steam depot selection changed after transaction publication"
                }
                M24DepotManifestCrosscheck.verify(
                    published, selection.picsManifestIds, selection.completedDepots)
                check(refreshSelection() == selection) {
                    "Steam identity changed during post-publication verification"
                }
            }
        )
    }

    private fun finishVerifiedPublication(token: String) {
        val state = swap.inspect() ?: error("Published update journal unavailable")
        check(state.token == token &&
            swap.reconcile() == R43BUpdateSwap.Finding.PUBLISHED_WITH_BACKUP) {
            "Cannot finalize unowned or unresolved game publication"
        }
        archive.verifyOriginal(token, state.backup, destination)
        val report = integrity.verifyAndRecord(destination)
        check(report.passed && report.files > 0) { "Published game failed final integrity" }
        // M24 journal closes; its marker remains while the rollback journal
        // and backup are retained. The marker makes rollback fail closed if
        // another tool replaces the published GameFiles tree.
        recovery.completeUpdateJournal(token)
    }

    /** The old game is verified before ANY rollback rename. */
    private fun rollbackOwnedPublication(token: String): Result {
        val state = swap.inspect() ?: error("Missing rollback journal")
        check(state.token == token) { "Rollback token mismatch" }
        archive.verifyOriginal(token, state.backup, destination)
        check(swap.rollbackPublished() ==
            R43BUpdateSwap.Finding.ROLLED_BACK_WITH_NEW_PRESERVED) {
            "Rollback could not reconcile the published game"
        }
        archive.restore(token, destination)
        // A cancelled/incomplete update retains staged/displaced bytes, but
        // its stale M24 checkpoint must not authorize another write.
        if (recovery.pending()?.updateMode == true) recovery.completeRolledBackUpdate(token)
        return Result.ROLLED_BACK_NEW_PRESERVED
    }

    /** Explicit rollback only. Never delete either version. */
    fun rollbackPublished(): Result {
        requireEnabled()
        val state = swap.inspect() ?: return Result.NO_TRANSACTION
        check(swap.reconcile() == R43BUpdateSwap.Finding.PUBLISHED_WITH_BACKUP) {
            "Rollback requires a published, owned game and intact backup"
        }
        return rollbackOwnedPublication(state.token)
    }

    /**
     * Recovery is bounded and refuses to overwrite conflicts. If a crash
     * interrupted two renames, the R43-B1 engine restores the original.
     * Published-but-unfinalized updates do NOT replay an unauthenticated transfer.
     */
    fun reconcileWithoutSteam(): Result {
        requireEnabled()
        val state = swap.inspect() ?: return Result.NO_TRANSACTION
        return when (swap.reconcile()) {
            R43BUpdateSwap.Finding.NO_JOURNAL -> Result.NO_TRANSACTION
            R43BUpdateSwap.Finding.READY_TO_PROMOTE -> Result.STAGE_READY
            R43BUpdateSwap.Finding.ORIGINAL_RESTORED -> {
                // The original integrity receipt survives until commit. If a
                // previous crash already replaced it, restore the archived one.
                if (state.displaced.exists()) {
                    archive.restore(state.token, destination)
                    if (recovery.pending()?.updateMode == true)
                        recovery.completeRolledBackUpdate(state.token)
                }
                Result.ORIGINAL_RESTORED
            }
            R43BUpdateSwap.Finding.PUBLISHED_WITH_BACKUP -> {
                // Do not label this completed if the M24 journal still exists.
                if (recovery.pending()?.updateMode == true) Result.MANUAL_REVIEW
                else {
                    archive.verifyOriginal(state.token, state.backup, destination)
                    val report = integrity.verifyRecorded(destination)
                    if (report.passed && report.files > 0)
                        Result.PUBLISHED_VERIFIED_WITH_BACKUP
                    else Result.MANUAL_REVIEW
                }
            }
            R43BUpdateSwap.Finding.ROLLED_BACK_WITH_NEW_PRESERVED -> {
                archive.restore(state.token, destination)
                if (recovery.pending()?.updateMode == true)
                    recovery.completeRolledBackUpdate(state.token)
                Result.ROLLED_BACK_NEW_PRESERVED
            }
            R43BUpdateSwap.Finding.MANUAL_REVIEW -> Result.MANUAL_REVIEW
        }
    }
}
