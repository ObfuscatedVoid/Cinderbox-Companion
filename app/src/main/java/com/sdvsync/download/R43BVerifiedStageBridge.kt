package com.sdvsync.download

import com.sdvsync.cinderbox.CinderboxPaths
import java.io.File

/**
 * R43-B3: a deliberately UNWIRED handoff from an M24-owned, manifest-checked
 * staged update to the R43-B1 swap journal. This does not publish or rename
 * GameFiles. The live service does not invoke it until interrupted-update
 * reconciliation and post-swap verification are independently qualified.
 *
 * PICS identities must be obtained and freshly rechecked from the authenticated
 * Steam connection by the eventual caller; local cached depot manifests are not
 * a Valve signature or an independent proof of entitlement.
 */
internal object R43BVerifiedStageBridge {
    data class BoundDepotSelection(
        val branch: String,
        val os: String,
        val buildId: Long,
        val manifestFingerprint: String,
        val picsManifestIds: Map<Int, String>,
        val completedDepots: Set<Int>
    )

    /**
     * PREPARE ONLY: performs two local hashes, checks every completed depot
     * against its PICS-bound local manifest, and rechecks the existing game's
     * original checksum baseline. Only then writes the swap preparation journal.
     * The live PICS recheck callback is mandatory and must throw on identity drift.
     * Future production wiring must not substitute an empty callback.
     */
    suspend fun prepareForProduction(
        privateRoot: File,
        selection: BoundDepotSelection,
        recheckAuthenticatedSelection: suspend () -> BoundDepotSelection
    ): R43BUpdateSwap.State {
        val destination = File(CinderboxPaths.GAME_FILES_DIR)
        val recovery = GameDownloadRecovery(privateRoot)
        val checkpoint = recovery.pending() ?: error("No owned M24 update checkpoint")
        check(checkpoint.updateMode && checkpoint.verify && selection.buildId > 0L) {
            "Live update must use a verified, owned M24 update checkpoint"
        }
        check(selection.manifestFingerprint.matches(Regex("[0-9a-f]{64}")) &&
            selection.completedDepots.isNotEmpty() && selection.picsManifestIds.isNotEmpty()) {
            "Authenticated PICS depot selection is incomplete"
        }
        recovery.requireMatching(destination, selection.branch, selection.os, true)
        recovery.requireSteamBuildId(selection.buildId)
        recovery.requireDepotManifestFingerprint(selection.manifestFingerprint)
        val stage = recovery.requireActiveStage(destination)
        check(R43BUpdateAdmission.inspectOwnedStage(privateRoot, checkpoint).eligible) {
            "Original Cinderbox installation changed or has protected user files"
        }
        val local = GameFileIntegrity(privateRoot).verifyAndRecord(stage, persistLedger = false)
        check(local.passed && local.files > 0) { "Staged download failed local SHA-256 verification" }
        M24DepotManifestCrosscheck.verify(
            stage, selection.picsManifestIds, selection.completedDepots
        )
        check(recheckAuthenticatedSelection() == selection) {
            "Authenticated Steam build or depot selection changed during handoff"
        }
        recovery.requireDepotManifestFingerprint(selection.manifestFingerprint)
        recovery.requireMatching(destination, selection.branch, selection.os, true)
        check(R43BUpdateAdmission.inspectOwnedStage(privateRoot, checkpoint).eligible) {
            "Original installation changed before swap journal preparation"
        }
        // A durable SWAP PREPARATION record is NOT permission to publish. Only
        // the separate, future post-swap recovery coordinator may promote.
        return R43BUpdateSwap.forProduction(privateRoot).recordVerifiedStage(checkpoint.token)
    }
}
