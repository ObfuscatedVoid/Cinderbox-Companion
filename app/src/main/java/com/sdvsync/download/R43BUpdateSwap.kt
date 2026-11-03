package com.sdvsync.download

import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.local.PlatformStoragePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/**
 * R43-B1: isolated, crash-recoverable same-volume swap of *already verified* game trees.
 * NOT connected to live Steam downloads. B2 must provide the independent authenticated
 * file/depot verification proof and a guard against replacing user-owned game content.
 * No path under Saves, Mods, or smapi-internal is ever an operation target.
 *
 * The old GameFiles directory is NEVER deleted; backup retention is intentional.
 * No recursive copy and no non-atomic directory rename fallback are permitted.
 */
internal class R43BUpdateSwap private constructor(
    private val privateRoot: File,
    private val gameFiles: File,
    private val isTestFixture: Boolean
) {
    enum class Phase { PREPARED, OLD_MOVED, PUBLISHED, ROLLED_BACK }
    enum class Finding { NO_JOURNAL, READY_TO_PROMOTE, ORIGINAL_RESTORED, PUBLISHED_WITH_BACKUP,
        ROLLED_BACK_WITH_NEW_PRESERVED, MANUAL_REVIEW }

    data class State(
        val phase: Phase,
        val token: String,
        val target: File,
        val stage: File,
        val backup: File,
        val displaced: File
    )

    companion object {
        private const val SCHEMA = "1"
        private const val JOURNAL = "r43b-update-swap.properties"
        private const val OWNER_MARKER = ".cinderbox-download.session"
        private val TOKEN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        /** B2 integration will use this only after an independently verified download. */
        fun forProduction(privateRoot: File): R43BUpdateSwap = R43BUpdateSwap(
            privateRoot, File(CinderboxPaths.GAME_FILES_DIR), false
        )

        /** Unit-test seam; never used by production code. */
        fun forFixture(privateRoot: File, gameFiles: File): R43BUpdateSwap =
            R43BUpdateSwap(privateRoot, gameFiles, true)
    }

    private val journal get() = File(privateRoot, JOURNAL)
    private fun safe(file: File): File = PlatformStoragePaths.requireSafe(file)

    private fun target(): File {
        val verified = safe(gameFiles)
        if (!isTestFixture) {
            check(gameFiles.path == CinderboxPaths.GAME_FILES_DIR &&
                verified.path == File(CinderboxPaths.GAME_FILES_DIR).canonicalPath) {
                "Transaction target is not Cinderbox GameFiles"
            }
        }
        check(verified.name == "GameFiles" && verified.parentFile?.name == "desktop") {
            "Unexpected Cinderbox installation layout"
        }
        return verified
    }

    private fun stageFor(target: File, token: String) =
        File(target.parentFile, ".GameFiles.cinderbox-m24-stage-$token")
    private fun backupFor(target: File, token: String) =
        File(target.parentFile, ".GameFiles.cinderbox-r43-backup-$token")
    private fun displacedFor(target: File, token: String) =
        File(target.parentFile, ".GameFiles.cinderbox-r43-rolledback-$token")

    private fun hasMarker(dir: File, token: String): Boolean {
        if (!dir.isDirectory) return false
        val marker = File(dir, OWNER_MARKER)
        return marker.isFile && !Files.isSymbolicLink(marker.toPath()) &&
            runCatching { marker.readText() == token }.getOrDefault(false)
    }

    private fun requireVerifiedStage(stage: File, token: String) {
        check(stage.isDirectory && hasMarker(stage, token)) {
            "Verified staging ownership marker is missing or changed"
        }
        check(safe(stage).path == stage.path) { "Stage path changed" }
        // No FOLLOW_LINKS. Validate every stage node; a link anywhere fails closed.
        var entries = 0
        var gameFiles = 0
        Files.walk(stage.toPath()).use { paths ->
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                check(!Files.isSymbolicLink(path)) { "Stage contains untrusted symlink" }
                entries++
                check(entries <= 200_000) { "Stage inspection limit exceeded" }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    path.fileName.toString() != OWNER_MARKER) gameFiles++
            }
        }
        check(gameFiles > 0) { "Empty staged game cannot replace installed game" }
        // Independent Steam manifest and file verification is a mandatory B2 call-site
        // condition; this class cannot establish it merely from local filenames.
    }

    @Synchronized fun inspect(): State? {
        if (!journal.exists()) return null
        check(journal.isFile && !Files.isSymbolicLink(journal.toPath()) &&
            journal.length() in 1..8192) { "Invalid R43 update journal" }
        val p = Properties().apply { journal.inputStream().use { load(it) } }
        check(p.getProperty("schema") == SCHEMA) { "Unknown R43 update journal" }
        val token = p.getProperty("token") ?: error("Missing update owner")
        check(TOKEN.matches(token)) { "Invalid R43 update token" }
        val expected = target()
        check(p.getProperty("target") == expected.path) { "Journal target changed" }
        val phase = Phase.valueOf(p.getProperty("phase") ?: error("Missing update phase"))
        val stage = safe(stageFor(expected, token))
        val backup = safe(backupFor(expected, token))
        val displaced = safe(displacedFor(expected, token))
        check(p.getProperty("stage") == stage.path &&
            p.getProperty("backup") == backup.path &&
            p.getProperty("displaced") == displaced.path) { "Journal sibling identity changed" }
        return State(phase, token, expected, stage, backup, displaced)
    }

    private fun writeJournal(s: State) {
        check(privateRoot.isDirectory || privateRoot.mkdirs()) { "Private journal unavailable" }
        val props = Properties().apply {
            setProperty("schema", SCHEMA)
            setProperty("token", s.token)
            setProperty("target", s.target.path)
            setProperty("stage", s.stage.path)
            setProperty("backup", s.backup.path)
            setProperty("displaced", s.displaced.path)
            setProperty("phase", s.phase.name)
        }
        val tmp = File(privateRoot, ".$JOURNAL-${UUID.randomUUID()}.tmp")
        try {
            tmp.outputStream().use { stream ->
                props.store(stream, "R43 game update ownership; no credentials")
                stream.flush()
                (stream as java.io.FileOutputStream).fd.sync()
            }
            // Atomic journal commit. No non-atomic replacement fallback.
            Files.move(tmp.toPath(), journal.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally { if (tmp.exists()) tmp.delete() }
    }

    /**
     * B1 low-level transaction engine. Caller must supply the already M24-verified
     * sibling stage and its journal-issued ownership token. Not a content verifier.
     */
    @Synchronized fun recordVerifiedStage(token: String): State {
        check(inspect() == null) { "Previous update requires reconciliation" }
        check(TOKEN.matches(token)) { "Invalid stage token" }
        val dest = target()
        check(dest.isDirectory && dest.canRead() && dest.canWrite()) {
            "Existing game missing or inaccessible; nothing was changed"
        }
        check(dest.parentFile.isDirectory && dest.parentFile.canWrite()) {
            "Cinderbox desktop directory inaccessible"
        }
        // The private update journal must never be confused with M24's live checkpoint.
        val stage = stageFor(dest, token)
        val backup = backupFor(dest, token)
        val displaced = displacedFor(dest, token)
        check(!backup.exists() && !displaced.exists()) { "Previous backup/rollback exists" }
        check(!Files.isSymbolicLink(dest.toPath())) { "Protected installation is a link" }
        // Modified/user-owned content inside GameFiles must be classified by B2 before
        // authorizing this operation; never remove or overwrite it automatically.
        requireVerifiedStage(stage, token)
        val state = State(Phase.PREPARED, token, dest, stage, backup, displaced)
        if (!isTestFixture) R43CUpdateSafety.requireProductionQuiescence()
        writeJournal(state)
        return state
    }

    /** Atomic rename-only promotion. Caller must independently verify staged Steam files. */
    @Synchronized fun publishVerifiedStage(): Finding {
        var s = inspect() ?: error("No R43 update journal")
        if (s.phase == Phase.PUBLISHED) return reconcile()
        check(s.phase == Phase.PREPARED) { "Update not ready for first promotion" }
        check(s.target.isDirectory && !s.backup.exists() && !s.displaced.exists()) {
            "Existing game or backup state changed"
        }
        requireVerifiedStage(s.stage, s.token)
        if (!isTestFixture) {
            R43CUpdateSafety.requireProductionQuiescence()
            R43CUpdateSafety.requirePreparedPublicationSpace(s.stage, s.target, privateRoot)
        }
        Files.move(s.target.toPath(), s.backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        // If the process dies here, reconcile() restores the original automatically.
        s = s.copy(phase = Phase.OLD_MOVED)
        writeJournal(s)
        if (!isTestFixture) R43CUpdateSafety.requireProductionQuiescence()
        Files.move(s.stage.toPath(), s.target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        if (!isTestFixture) R43CUpdateSafety.requireProductionQuiescence()
        check(hasMarker(s.target, s.token)) { "New game ownership changed after promotion" }
        s = s.copy(phase = Phase.PUBLISHED)
        writeJournal(s)
        return Finding.PUBLISHED_WITH_BACKUP
    }

    /**
     * Crash recovery must not replace an occupied target or delete any content.
     * If original was moved but new game wasn't published, restore original.
     * Published game stays published with the original backup retained.
     */
    @Synchronized fun reconcile(): Finding {
        val s = inspect() ?: return Finding.NO_JOURNAL
        // The method can execute recovery renames. Unknown game state denies them.
        if (!isTestFixture) R43CUpdateSafety.requireProductionQuiescence()
        val targetExists = s.target.exists()
        val stageExists = s.stage.exists()
        val backupExists = s.backup.exists()
        val displacedExists = s.displaced.exists()
        if (targetExists && stageExists && !backupExists && !displacedExists &&
            s.phase == Phase.PREPARED && hasMarker(s.stage, s.token)) return Finding.READY_TO_PROMOTE
        if (!targetExists && backupExists && stageExists && !displacedExists &&
            s.phase in setOf(Phase.PREPARED, Phase.OLD_MOVED) &&
            hasMarker(s.stage, s.token)) {
            Files.move(s.backup.toPath(), s.target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            writeJournal(s.copy(phase = Phase.PREPARED))
            return Finding.ORIGINAL_RESTORED
        }
        if (targetExists && !stageExists && backupExists && !displacedExists &&
            hasMarker(s.target, s.token) &&
            s.phase in setOf(Phase.PREPARED, Phase.OLD_MOVED, Phase.PUBLISHED)) {
            writeJournal(s.copy(phase = Phase.PUBLISHED))
            return Finding.PUBLISHED_WITH_BACKUP
        }
        if (!targetExists && !stageExists && backupExists && !displacedExists &&
            s.phase == Phase.PUBLISHED) {
            // The new installed game disappeared. Restore original into empty slot.
            Files.move(s.backup.toPath(), s.target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            writeJournal(s.copy(phase = Phase.ROLLED_BACK))
            return Finding.ORIGINAL_RESTORED
        }
        if (!targetExists && !stageExists && backupExists && displacedExists &&
            hasMarker(s.displaced, s.token) &&
            s.phase == Phase.PUBLISHED) {
            // Crash between the two rollback renames; restore the old game.
            Files.move(s.backup.toPath(), s.target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            writeJournal(s.copy(phase = Phase.ROLLED_BACK))
            return Finding.ORIGINAL_RESTORED
        }
        if (targetExists && !stageExists && !backupExists && displacedExists &&
            hasMarker(s.displaced, s.token) &&
            s.phase in setOf(Phase.PUBLISHED, Phase.ROLLED_BACK)) {
            writeJournal(s.copy(phase = Phase.ROLLED_BACK))
            return Finding.ROLLED_BACK_WITH_NEW_PRESERVED
        }
        return Finding.MANUAL_REVIEW
    }

    /** Preserve the new game in an owned rollback sibling; never delete either tree. */
    @Synchronized fun rollbackPublished(): Finding {
        check(reconcile() == Finding.PUBLISHED_WITH_BACKUP) {
            "Cannot roll back an unresolved update; manual review required"
        }
        val s = inspect() ?: error("Missing update journal")
        check(!s.displaced.exists() && s.backup.isDirectory &&
            hasMarker(s.target, s.token)) { "Published update identity changed" }
        if (!isTestFixture) R43CUpdateSafety.requireProductionQuiescence()
        Files.move(s.target.toPath(), s.displaced.toPath(), StandardCopyOption.ATOMIC_MOVE)
        // If restoring the backup fails, reconcile() will retry the restoration.
        if (!isTestFixture) R43CUpdateSafety.requireProductionQuiescence()
        Files.move(s.backup.toPath(), s.target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        writeJournal(s.copy(phase = Phase.ROLLED_BACK))
        return Finding.ROLLED_BACK_WITH_NEW_PRESERVED
    }
}
