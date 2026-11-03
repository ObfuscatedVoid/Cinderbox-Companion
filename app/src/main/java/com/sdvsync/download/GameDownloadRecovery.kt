package com.sdvsync.download

import java.io.File
import com.sdvsync.local.PlatformStoragePaths
import com.sdvsync.cinderbox.CinderboxPaths
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/**
 * Durable, app-private ownership record for an interrupted Steam download.
 * No Steam credentials or beta passwords are written to disk.
 * A checkpoint authorizes continuation only for the exact same destination,
 * Steam branch, OS, and verification choice. It never authorizes arbitrary files.
 */
data class GameDownloadCheckpoint(
    val directory: String,
    val branch: String,
    val os: String,
    val verify: Boolean,
    val token: String,
    val steamBuildId: Long = 0L,
    val stagingDirectory: String? = null,
    val depotManifestFingerprint: String? = null,
    val updateMode: Boolean = false
) {
    fun matches(directory: File, branch: String, os: String, verify: Boolean): Boolean {
        val safe = GameDownloadRecovery.safeDestination(directory)
        if (this.directory != safe.path || this.branch != branch ||
            this.os != os || this.verify != verify) return false
        // A path alone cannot establish ownership of a partial installation.
        val stage = stagingDirectory?.let { GameDownloadRecovery.safeDestination(File(it)) }
        // Staged receipts are either PRE-PROMOTION (stage only) or AFTER-PROMOTION
        // (destination only). Both present means a conflict, never trusted recovery.
        if (stage != null) {
            if (updateMode) {
                // PRE-PROMOTION ONLY. Recovery after a swap belongs to R43-B1,
                // never the old M24 absent-target path.
                if (!stage.isDirectory || !safe.isDirectory) return false
            } else if (stage.exists() == safe.exists()) return false
        }
        val marker = File(if (stage?.exists() == true) stage else safe, ".cinderbox-download.session")
        return runCatching { marker.isFile && !Files.isSymbolicLink(marker.toPath()) &&
            marker.readText() == token }.getOrDefault(false)
    }
}

class GameDownloadRecovery(private val privateRoot: File) {
    private val journal get() = File(privateRoot, "m6-game-download.properties")

    companion object {
        private const val VERSION = "1"
        private const val APP_ID = "413150"

        /** Accept only OS-managed storage aliases; never game-controlled symlinks. */
        fun safeDestination(directory: File): File = PlatformStoragePaths.requireSafe(directory)
    }

    @Synchronized fun pending(): GameDownloadCheckpoint? {
        if (!journal.exists()) return null
        check(journal.isFile && journal.length() in 1..8192) { "Invalid interrupted download journal" }
        val props = Properties().apply { journal.inputStream().use { load(it) } }
        check(props.getProperty("schema") == VERSION && props.getProperty("app") == APP_ID) {
            "Unrecognized game download journal; refusing to continue"
        }
        val directory = props.getProperty("directory") ?: error("Missing download destination")
        val branch = props.getProperty("branch") ?: error("Missing Steam branch")
        val os = props.getProperty("os") ?: error("Missing target OS")
        val token = props.getProperty("token") ?: error("Missing session ownership marker")
        check(token.matches(Regex("[0-9a-f-]{36}"))) { "Invalid session marker" }
        val verify = when (props.getProperty("verify")) {
            "true" -> true
            "false" -> false
            else -> error("Invalid download verification preference")
        }
        check(branch.length in 1..128 && os in setOf("windows", "linux", "macos")) {
            "Invalid interrupted download options"
        }
        val safe = safeDestination(File(directory))
        check(safe.path == directory) { "Download path changed since checkpoint" }
        val rawBuild = props.getProperty("steam_build_id")
        val buildId = rawBuild?.toLongOrNull() ?: if (rawBuild == null) 0L else error("Invalid branch build identity")
        check(buildId >= 0L) { "Invalid branch build identity" }
        val stagePath = props.getProperty("staging_directory")
        if (stagePath != null) {
            val stage = safeDestination(File(stagePath))
            check(stage.path == stagePath && stage.parentFile == safe.parentFile &&
                stage.name == ".${safe.name}.cinderbox-m24-stage-$token") {
                "Invalid M24 staging directory identity"
            }
        }
        val updateMode = when (props.getProperty("update_mode")) {
            null, "false" -> false
            "true" -> true
            else -> error("Invalid update-mode checkpoint flag")
        }
        check(!updateMode || (stagePath != null && verify)) {
            "Update mode requires verified sibling staging"
        }
        val manifestHash = props.getProperty("pics_depot_manifest_fingerprint")
        check(manifestHash == null || manifestHash.matches(Regex("[0-9a-f]{64}"))) {
            "Corrupt PICS depot manifest fingerprint in journal"
        }
        return GameDownloadCheckpoint(directory, branch, os, verify, token, buildId, stagePath, manifestHash, updateMode)
    }

    /** Only call this for a NEW, already preflighted empty installation directory. */
    @Synchronized fun beginNew(directory: File, branch: String, os: String, verify: Boolean) =
        beginOwned(directory, branch, os, verify, false)

    /** For live M24 traffic: create an owned sibling stage, never write to the
     * selected destination until the complete staging tree has been checked.
     * A preexisting destination, even empty, is deliberately not replaced.
     */
    @Synchronized fun beginStaged(directory: File, branch: String, os: String, verify: Boolean) =
        beginOwned(directory, branch, os, verify, true)

    /** R43-B3: owned stage beside a still-occupied game; NOT called by live UI/service.
     * The old installation and its integrity ledger must pass read-only admission
     * before any owned stage can be created. Publication remains separate.
     */
    @Synchronized fun beginStagedUpdate(directory: File, branch: String, os: String, verify: Boolean) {
        check(verify && directory.path == CinderboxPaths.GAME_FILES_DIR) {
            "Update staging is restricted to verified Cinderbox GameFiles"
        }
        check(R43BUpdateAdmission.inspect(privateRoot).eligible) {
            "Installed game is not eligible for protected staging"
        }
        beginOwned(directory, branch, os, verify, true, updateMode = true)
    }

    private fun beginOwned(directory: File, branch: String, os: String, verify: Boolean,
                           staged: Boolean, updateMode: Boolean = false) {
        check(!updateMode || staged) { "Update requires sibling staging" }
        check(pending() == null) { "Resolve the previous download before starting another" }
        val safe = safeDestination(directory)
        require(branch.length in 1..128 && os in setOf("windows", "linux", "macos")) {
            "Unsupported Steam download selection"
        }
        check(privateRoot.isDirectory || privateRoot.mkdirs()) { "App-private download storage inaccessible" }
        val token = UUID.randomUUID().toString()
        val stage = if (staged) {
            if (updateMode) {
                check(safe.isDirectory && safe.parentFile?.isDirectory == true &&
                    R43BUpdateAdmission.inspect(privateRoot).eligible) {
                    "Existing GameFiles no longer satisfies read-only update admission"
                }
            } else {
                check(!safe.exists() && safe.parentFile?.isDirectory == true) {
                    "Staged download requires an absent target and existing parent"
                }
            }
            File(safe.parentFile, ".${safe.name}.cinderbox-m24-stage-$token").also {
                check(it.mkdir()) { "Cannot create exclusive M24 staging directory" }
            }
        } else {
            check(safe.isDirectory || safe.mkdirs()) { "Game download directory is inaccessible" }
            safe
        }
        val marker = File(stage, ".cinderbox-download.session")
        check(marker.createNewFile()) { "Game directory already contains a download ownership marker" }
        try { marker.writeText(token) }
        catch (e: Exception) { marker.delete(); throw e }
        val props = Properties().apply {
            setProperty("schema", VERSION)
            setProperty("app", APP_ID)
            setProperty("directory", safe.path)
            setProperty("branch", branch)
            setProperty("os", os)
            setProperty("verify", verify.toString())
            setProperty("token", token)
            if (staged) setProperty("staging_directory", stage.path)
            if (updateMode) setProperty("update_mode", "true")
        }
        val temp = File(privateRoot, "m6-game-download-${System.nanoTime()}.tmp")
        try {
            temp.outputStream().use { stream ->
                props.store(stream, "Interrupted game download checkpoint (no credentials)")
                stream.flush()
                if (stream is java.io.FileOutputStream) stream.fd.sync()
            }
            try {
                Files.move(temp.toPath(), journal.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), journal.toPath())
            }
        } catch (e: Exception) {
            // Failure before publishing a journal leaves no authorized resume session.
            if (!journal.exists()) {
                marker.delete()
                if (staged) stage.delete() // only our empty private stage
            }
            throw e
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    @Synchronized fun requireMatching(directory: File, branch: String, os: String, verify: Boolean) {
        check(pending()?.matches(directory, branch, os, verify) == true) {
            "No matching interrupted download. Existing files will not be overwritten"
        }
    }

    /** Bind a freshly obtained PICS build ID to the existing owned checkpoint.
     * A crash before this atomic update leaves an unbound checkpoint that cannot
     * authorize the M24 live service; historical v1 fixtures remain readable.
     */
    @Synchronized fun bindSteamBuildId(buildId: Long) {
        require(buildId > 0L) { "Steam branch build identity unavailable" }
        val current = pending() ?: error("Missing owned download checkpoint")
        check(current.matches(File(current.directory), current.branch, current.os, current.verify)) {
            "Download checkpoint owner changed"
        }
        check(current.steamBuildId == 0L || current.steamBuildId == buildId) {
            "Steam branch build changed during checkpoint binding"
        }
        val props = Properties().apply { journal.inputStream().use { load(it) } }
        props.setProperty("steam_build_id", buildId.toString())
        val temp = File(privateRoot, "m24-build-${System.nanoTime()}.tmp")
        try {
            temp.outputStream().use { out ->
                props.store(out, "Steam branch build binding; no account data")
                out.flush()
                if (out is java.io.FileOutputStream) out.fd.sync()
            }
            try {
                Files.move(temp.toPath(), journal.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), journal.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temp.delete() }
    }


    /** Bind the fresh PICS depot manifest *identity* to an owned M24 stage.
     * Does not establish authentic depot file contents or a Steam entitlement.
     */
    @Synchronized fun bindDepotManifestFingerprint(fingerprint: String) {
        check(fingerprint.matches(Regex("[0-9a-f]{64}"))) { "Invalid PICS depot fingerprint" }
        val current = pending() ?: error("Missing owned M24 journal")
        check(current.stagingDirectory != null && current.steamBuildId > 0L &&
            current.matches(File(current.directory), current.branch, current.os, current.verify)) {
            "PICS manifest cannot bind an unowned or legacy download"
        }
        check(current.depotManifestFingerprint == null || current.depotManifestFingerprint == fingerprint) {
            "Steam depot manifest identity changed; partial bytes preserved"
        }
        if (current.depotManifestFingerprint == fingerprint) return
        val props = Properties().apply { journal.inputStream().use { load(it) } }
        props.setProperty("pics_depot_manifest_fingerprint", fingerprint)
        val temp = File(privateRoot, "m24-pics-${System.nanoTime()}.tmp")
        try {
            temp.outputStream().use { out ->
                props.store(out, "PICS depot manifest identity only; not signed file proof")
                out.flush()
                if (out is java.io.FileOutputStream) out.fd.sync()
            }
            // Do not fall back to non-atomic replacement for this binding.
            Files.move(temp.toPath(), journal.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { temp.delete() }
    }

    @Synchronized fun requireDepotManifestFingerprint(fingerprint: String) {
        val current = pending() ?: error("Missing owned M24 journal")
        check(current.stagingDirectory != null && current.steamBuildId > 0L &&
            current.depotManifestFingerprint != null &&
            current.depotManifestFingerprint == fingerprint &&
            current.matches(File(current.directory), current.branch, current.os, current.verify)) {
            "Steam PICS depot manifest identity missing or changed; bytes preserved"
        }
    }

    /** Resolve a journal-owned stage. No metadata or path alone can authorize a
     * write into a destination or into a different staging tree.
     */
    @Synchronized fun requireActiveStage(destination: File): File {
        val current = pending() ?: error("M24 staged checkpoint missing")
        check(current.stagingDirectory != null && current.steamBuildId > 0L &&
            current.directory == safeDestination(destination).path &&
            current.matches(destination, current.branch, current.os, current.verify)) {
            "Staging identity or ownership changed"
        }
        val stage = safeDestination(File(current.stagingDirectory))
        check(stage.isDirectory && (if (current.updateMode) destination.isDirectory
            else !destination.exists())) {
            "Staged transfer already promoted or destination changed; do not redownload"
        }
        return stage
    }

    @Synchronized fun isPromotedStage(destination: File): Boolean {
        val current = pending() ?: return false
        if (current.updateMode || current.stagingDirectory == null || current.steamBuildId <= 0L ||
            current.directory != safeDestination(destination).path) return false
        val stage = safeDestination(File(current.stagingDirectory))
        return !stage.exists() && destination.isDirectory &&
            current.matches(destination, current.branch, current.os, current.verify)
    }

    /** An atomic same-filesystem directory rename; never fall back to copying or
     * replace preexisting content. Integrity and live identity checks precede it.
     * If a process dies after the rename, the unchanged journal/marker allow
     * completion reconciliation without a second download.
     */
    @Synchronized fun promoteStaged(destination: File) {
        val current = pending() ?: error("M24 staging journal missing")
        val safe = safeDestination(destination)
        val stage = requireActiveStage(safe)
        check(current.matches(safe, current.branch, current.os, current.verify)) {
            "Staging marker mismatch"
        }
        check(!safe.exists()) { "Target appeared during staged transfer; no overwrite" }
        var gameFileCount = 0
        stage.walkTopDown().forEach { node ->
            check(!Files.isSymbolicLink(node.toPath())) { "Staged tree contains a symbolic link" }
            if (node.isFile && node.name != ".cinderbox-download.session") gameFileCount++
        }
        check(gameFileCount > 0) { "No game files in staged transfer" }
        // AtomicMoveNotSupportedException deliberately propagates: non-atomic
        // fallback could expose a partially copied game to Cinderbox.
        Files.move(stage.toPath(), safe.toPath(), StandardCopyOption.ATOMIC_MOVE)
        check(isPromotedStage(safe)) { "Promotion ownership could not be reconciled" }
    }

    /** Old or corrupt checkpoints cannot be used for a current authenticated download. */
    @Synchronized fun requireSteamBuildId(buildId: Long) {
        require(buildId > 0L) { "Steam build identity unavailable" }
        val current = pending() ?: error("Download checkpoint missing")
        check(current.steamBuildId > 0L && current.steamBuildId == buildId) {
            "Unbound or stale Steam build checkpoint; files preserved for review"
        }
    }

    /** A completed install is the ONLY automatic point where recovery is cleared. */
    @Synchronized fun completed() {
        val current = pending()
        if (current != null) {
            val marker = File(current.directory, ".cinderbox-download.session")
            check(!current.updateMode) {
                "M24 completion cannot finalize an occupied-target update; use R43 recovery"
            }
            if (current.stagingDirectory != null) {
                check(isPromotedStage(File(current.directory))) {
                    "Cannot finalize unpromoted or conflicting staged download"
                }
            }
            if (marker.exists()) {
                check(!Files.isSymbolicLink(marker.toPath()) && marker.isFile &&
                    marker.readText() == current.token) { "Game download ownership changed" }
                check(marker.delete()) { "Cannot remove download ownership marker" }
            }
        }
        check(journal.delete() || !journal.exists()) { "Could not finalize download checkpoint" }
    }

    /** R43: the verified replacement keeps its ownership marker while an
     * independent R43 rollback journal and original backup still exist.
     * Never allow the old M24 completed() path to remove that marker.
     */
    @Synchronized fun completeUpdateJournal(token: String) {
        val current = pending() ?: error("M24 update checkpoint missing")
        check(current.updateMode && current.verify && current.token == token &&
            current.stagingDirectory != null && current.steamBuildId > 0L &&
            current.depotManifestFingerprint != null) {
            "Unbound update checkpoint cannot be finalized"
        }
        val original = safeDestination(File(current.directory))
        val stage = safeDestination(File(current.stagingDirectory))
        check(!stage.exists() && original.isDirectory) {
            "Update was not atomically published"
        }
        val marker = File(original, ".cinderbox-download.session")
        check(marker.isFile && !Files.isSymbolicLink(marker.toPath()) &&
            marker.readText() == token) { "Published update owner changed" }
        val transaction = File(privateRoot, "r43b-update-swap.properties")
        check(transaction.isFile && !Files.isSymbolicLink(transaction.toPath())) {
            "R43 rollback journal required before closing M24"
        }
        check(journal.delete()) { "Could not finalize M24 update checkpoint" }
    }

    /** Rollback preserves the new bytes in the owned displaced sibling and
     * closes M24 only after the original is back in its exact GameFiles path.
     */
    @Synchronized fun completeRolledBackUpdate(token: String) {
        val current = pending() ?: return
        check(current.updateMode && current.token == token &&
            current.stagingDirectory != null) { "Rollback journal owner changed" }
        val original = safeDestination(File(current.directory))
        val stage = safeDestination(File(current.stagingDirectory))
        val displaced = safeDestination(File(original.parentFile,
            ".GameFiles.cinderbox-r43-rolledback-$token"))
        val marker = File(displaced, ".cinderbox-download.session")
        check(original.isDirectory && !stage.exists() && displaced.isDirectory &&
            marker.isFile && !Files.isSymbolicLink(marker.toPath()) &&
            marker.readText() == token) { "Rollback has not preserved both game versions" }
        check(journal.delete()) { "Could not close the rolled-back M24 journal" }
    }

    /** Explicit user discard; forgets the checkpoint but keeps game bytes. */
    @Synchronized fun discard() {
        val current = pending()
        if (current != null) {
            check(!current.updateMode ||
                !File(privateRoot, "r43b-update-swap.properties").exists()) {
                "Prepared update swap cannot be discarded before recovery review"
            }
            val path = current.stagingDirectory?.let { File(it) }
                ?.takeIf { it.exists() } ?: File(current.directory)
            val marker = File(path, ".cinderbox-download.session")
            if (marker.isFile && !Files.isSymbolicLink(marker.toPath()) &&
                runCatching { marker.readText() == current.token }.getOrDefault(false)) marker.delete()
        }
        check(journal.delete() || !journal.exists()) { "Cannot discard game download recovery journal" }
    }
}
