package com.sdvsync.download

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Per-transaction, app-private copy of the OLD GameFiles SHA-256 ledger.
 * The installed ledger may be replaced only after a verified publication;
 * this snapshot preserves an independent rollback witness.
 */
internal class R43BLedgerArchive(private val privateRoot: File) {
    private val tokenPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val installed get() = File(privateRoot, "m6-game-integrity.properties")

    fun snapshot(token: String): File {
        require(tokenPattern.matches(token)) { "Invalid rollback owner token" }
        return File(privateRoot, "r43b-original-integrity-$token.properties")
    }

    /** Capture before moving the original. A collision is never overwritten. */
    @Synchronized fun capture(token: String, gameFiles: File): File {
        val source = installed
        check(source.isFile && !Files.isSymbolicLink(source.toPath()) &&
            source.length() in 1..10_485_760) { "Original game ledger unavailable" }
        val check = GameFileIntegrity(privateRoot).verifyRecorded(gameFiles)
        check(check.passed && check.files > 0) { "Original installation changed before update" }
        val witness = snapshot(token)
        check(!Files.isSymbolicLink(witness.toPath())) { "Rollback witness is linked" }
        if (witness.exists()) {
            check(witness.isFile && witness.length() == source.length() &&
                witness.readBytes().contentEquals(source.readBytes())) {
                "Rollback witness differs from original integrity baseline"
            }
            return witness
        }
        atomicCopy(source, witness, replace = false)
        return witness
    }

    fun verifyOriginal(token: String, savedOriginal: File, liveTarget: File) {
        val witness = snapshot(token)
        check(witness.isFile && !Files.isSymbolicLink(witness.toPath()) &&
            witness.length() in 1..10_485_760) { "Rollback integrity witness missing" }
        val result = GameFileIntegrity(privateRoot).verifyRecorded(
            savedOriginal, recordedDestination = liveTarget, receiptFile = witness)
        check(result.passed && result.files > 0) { "Original backup differs from its verified baseline" }
    }

    /** Restore ledger after the original directory is back at GameFiles. */
    @Synchronized fun restore(token: String, liveTarget: File) {
        verifyOriginal(token, liveTarget, liveTarget)
        atomicCopy(snapshot(token), installed, replace = true)
        check(GameFileIntegrity(privateRoot).verifyRecorded(liveTarget).passed) {
            "Restored original integrity receipt did not validate"
        }
    }

    private fun atomicCopy(source: File, destination: File, replace: Boolean) {
        check(privateRoot.isDirectory && !Files.isSymbolicLink(privateRoot.toPath())) {
            "Private update storage missing or linked"
        }
        check(!Files.isSymbolicLink(source.toPath()) &&
            !Files.isSymbolicLink(destination.toPath())) { "Linked integrity receipt" }
        val temp = File(privateRoot, ".r43b-ledger-${UUID.randomUUID()}.tmp")
        try {
            source.inputStream().use { input ->
                java.io.FileOutputStream(temp).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            if (replace) {
                Files.move(temp.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } else {
                check(!destination.exists()) { "Rollback witness collision" }
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
        } finally { if (temp.exists()) temp.delete() }
    }
}
