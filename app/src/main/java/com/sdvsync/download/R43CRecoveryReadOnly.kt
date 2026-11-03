package com.sdvsync.download

import java.io.File
import java.nio.file.Files

/**
 * Read-only cold-start assessment. The swap reconciliation routine can
 * rename directories, so status inspection MUST NOT invoke it.
 */
internal object R43CRecoveryReadOnly {
    data class Finding(val description: String, val rollbackPossible: Boolean,
                       val recoveryPossible: Boolean, val manualReview: Boolean)

    private val siblingPrefixes = listOf(
        ".GameFiles.cinderbox-m24-stage-", ".GameFiles.cinderbox-r43-backup-",
        ".GameFiles.cinderbox-r43-rolledback-")

    fun inspect(state: R43BUpdateSwap.State?, destination: File,
                privateRoot: File): Finding {
        if (state == null) {
            val siblings = runCatching {
                destination.parentFile?.listFiles()?.any { candidate ->
                    siblingPrefixes.any { candidate.name.startsWith(it) }
                }
            }.getOrNull()
            return if (siblings == false)
                Finding("No protected update transaction", false, false, false)
            else Finding("Unclaimed staging or recovery data requires manual review",
                false, false, true)
        }
        if (state.target.path != destination.canonicalPath ||
            listOf(state.stage, state.backup, state.displaced, state.target).any {
                Files.isSymbolicLink(it.toPath())
            }) return Finding("Update path or journal identity changed; manual review required",
                false, false, true)
        val owned = File(state.stage, ".cinderbox-download.session")
        val liveMarker = File(state.target, ".cinderbox-download.session")
        val displacedMarker = File(state.displaced, ".cinderbox-download.session")
        fun marker(file: File) = file.isFile && !Files.isSymbolicLink(file.toPath()) &&
            runCatching { file.readText() == state.token }.getOrDefault(false)
        val archivedOriginal = state.backup.isDirectory && runCatching {
            R43BLedgerArchive(privateRoot).verifyOriginal(state.token, state.backup, destination)
            true
        }.getOrDefault(false)
        val pending = runCatching { GameDownloadRecovery(privateRoot).pending() }.getOrNull()
        return when {
            state.phase == R43BUpdateSwap.Phase.PREPARED &&
                state.target.isDirectory && state.stage.isDirectory &&
                marker(owned) && !state.backup.exists() && !state.displaced.exists() ->
                    Finding("Verified stage retained; publishing requires renewed authorization",
                        false, false, false)
            !state.target.exists() && state.backup.isDirectory &&
                state.stage.isDirectory && marker(owned) && archivedOriginal &&
                !state.displaced.exists() &&
                state.phase in setOf(R43BUpdateSwap.Phase.PREPARED,
                    R43BUpdateSwap.Phase.OLD_MOVED) ->
                    Finding("Interrupted update; original backup retained for reviewed recovery",
                        false, true, false)
            state.phase == R43BUpdateSwap.Phase.PUBLISHED &&
                state.target.isDirectory && marker(liveMarker) &&
                archivedOriginal && !state.stage.exists() && !state.displaced.exists() &&
                pending?.updateMode != true &&
                runCatching { GameFileIntegrity(privateRoot).verifyRecorded(state.target).passed }
                    .getOrDefault(false) ->
                    Finding("Verified new game; original backup available for reviewed rollback",
                        true, false, false)
            state.phase == R43BUpdateSwap.Phase.ROLLED_BACK &&
                state.target.isDirectory && state.displaced.isDirectory &&
                marker(displacedMarker) && !state.backup.exists() &&
                runCatching { GameFileIntegrity(privateRoot).verifyRecorded(state.target).passed }
                    .getOrDefault(false) ->
                    Finding("Original game restored; displaced version retained", false, false, false)
            else -> Finding("Update records or filesystem state are incomplete; manual review required",
                false, false, true)
        }
    }
}
