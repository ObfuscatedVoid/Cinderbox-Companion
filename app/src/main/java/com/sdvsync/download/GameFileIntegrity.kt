package com.sdvsync.download

import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Local SHA-256 change detection, NOT a replacement for Steam's signed depot manifests.
 * The DepotDownloader library performs Steam chunk/download validation; this ledger
 * detects changed or unreadable files after a completed transfer.
 */
data class GameIntegrityResult(val files: Int, val errors: List<String>) {
    val passed: Boolean get() = errors.isEmpty()
}

class GameFileIntegrity(private val privateRoot: File) {
    private val ledger get() = File(privateRoot, "m6-game-integrity.properties")

    private fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun files(root: File): List<File> {
        GameDownloadRecovery.safeDestination(root)
        require(root.isDirectory) { "Download destination is missing" }
        return root.walkTopDown().onEnter { dir ->
            require(!Files.isSymbolicLink(dir.toPath())) { "Symlink found during verification" }
            dir == root || dir.name != ".DepotDownloader"
        }.filter { file ->
            require(!Files.isSymbolicLink(file.toPath())) { "Symlink found during verification" }
            file.isFile && file.name != ".cinderbox-download.session"
        }.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.toList()
    }

    /** Hash twice and compare; abort on changed files or unreadable content. */
    fun verifyAndRecord(root: File, progress: (Int, Int, String) -> Unit = { _, _, _ -> },
                        phaseProgress: (String, Int, Int, String) -> Unit = { _, _, _, _ -> },
                        persistLedger: Boolean = true): GameIntegrityResult {
        val selected = files(root)
        require(selected.isNotEmpty()) { "No downloaded game files to verify" }
        val first = LinkedHashMap<String, Pair<Long, String>>()
        for ((i, file) in selected.withIndex()) {
            val path = file.relativeTo(root).invariantSeparatorsPath
            first[path] = file.length() to digest(file)
            phaseProgress("hashing pass 1 of 2", i + 1, selected.size, path)
        }
        val second = files(root)
        val errors = mutableListOf<String>()
        if (selected.map { it.relativeTo(root).invariantSeparatorsPath } !=
            second.map { it.relativeTo(root).invariantSeparatorsPath }) {
            errors.add("Game file list changed while verifying")
        }
        for ((i, file) in second.withIndex()) {
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val expected = first[relative]
            if (expected == null || file.length() != expected.first || digest(file) != expected.second) {
                errors.add(relative)
            }
            progress(i + 1, second.size, relative)
            phaseProgress("hashing pass 2 of 2", i + 1, second.size, relative)
        }
        if (errors.isNotEmpty()) return GameIntegrityResult(second.size, errors)
        // A verified stage must not overwrite the last published installation's
        // private SHA-256 baseline. The ledger is committed after publication.
        if (!persistLedger) return GameIntegrityResult(second.size, emptyList())
        check(privateRoot.isDirectory || privateRoot.mkdirs()) { "Cannot store integrity ledger" }
        val props = Properties().apply {
            setProperty("schema", "1")
            setProperty("directory", GameDownloadRecovery.safeDestination(root).path)
            setProperty("count", first.size.toString())
            first.entries.forEachIndexed { i, (name, data) ->
                setProperty("file.$i.path", name)
                setProperty("file.$i.size", data.first.toString())
                setProperty("file.$i.sha256", data.second)
            }
        }
        val temp = File(privateRoot, "m6-game-integrity-${System.nanoTime()}.tmp")
        try {
            temp.outputStream().use { output ->
                props.store(output, "Game file SHA-256 ledger")
                output.flush()
                if (output is java.io.FileOutputStream) output.fd.sync()
            }
            try {
                Files.move(temp.toPath(), ledger.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), ledger.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temp.delete() }
        return GameIntegrityResult(second.size, emptyList())
    }

    /** Compare the current installation with the last successfully recorded SHA-256 ledger. */
    fun verifyRecorded(root: File, recordedDestination: File = root,
                       receiptFile: File = ledger): GameIntegrityResult {
        check(receiptFile.isFile && !Files.isSymbolicLink(receiptFile.toPath()) &&
            receiptFile.length() in 1..10485760) { "No safe game integrity record" }
        val props = Properties().apply { receiptFile.inputStream().use { load(it) } }
        check(props.getProperty("schema") == "1" &&
            props.getProperty("directory") == GameDownloadRecovery.safeDestination(recordedDestination).path) {
            "Integrity ledger does not belong to this game folder"
        }
        val count = props.getProperty("count")?.toIntOrNull() ?: error("Invalid verification ledger")
        require(count in 1..100000) { "Invalid ledger file count" }
        val expected = (0 until count).associate { i ->
            val path = props.getProperty("file.$i.path") ?: error("Missing verified path")
            require(path.isNotBlank() && !path.startsWith('/') &&
                path.split('/').none { it == ".." || it == "." || it.isBlank() }) {
                "Invalid verification path"
            }
            val size = props.getProperty("file.$i.size")?.toLongOrNull() ?: error("Invalid verified size")
            val hash = props.getProperty("file.$i.sha256") ?: error("Missing verified hash")
            require(hash.matches(Regex("[0-9a-f]{64}")) && size >= 0) { "Corrupt hash ledger" }
            path to (size to hash)
        }
        check(expected.size == count) { "Duplicate path in integrity ledger" }
        val expectedDirectories = buildSet<String> {
            for (relative in expected.keys) {
                val segments = relative.split('/')
                for (depth in 1 until segments.size) {
                    add(segments.take(depth).joinToString("/"))
                }
            }
        }
        val actual = files(root)
        val actualPaths = actual.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        val errors = mutableListOf<String>()
        for (file in actual) {
            val rel = file.relativeTo(root).invariantSeparatorsPath
            val pair = expected[rel]
            if (pair == null || pair.first != file.length() || pair.second != digest(file)) errors.add(rel)
        }
        for (missing in expected.keys - actualPaths) errors.add(missing)
        // Fail closed for untracked empty folders and symlinks. The Steam SDK
        // metadata tree is excluded from game-content tracking by design.
        root.walkTopDown().onEnter { dir ->
            // Reject a linked directory without traversing into its target.
            val linked = Files.isSymbolicLink(dir.toPath())
            if (linked) errors.add("Linked directory: ${dir.relativeTo(root).invariantSeparatorsPath}")
            !linked
        }.forEach { node ->
            val relative = node.relativeTo(root).invariantSeparatorsPath
            if (Files.isSymbolicLink(node.toPath())) errors.add("Linked path: $relative")
            if (node != root && node.isDirectory &&
                relative != ".DepotDownloader" &&
                !relative.startsWith(".DepotDownloader/") &&
                relative !in expectedDirectories) {
                errors.add("Untracked directory: $relative")
            }
        }
        return GameIntegrityResult(actual.size, errors.distinct())
    }
}
