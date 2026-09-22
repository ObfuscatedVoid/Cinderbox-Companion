package com.sdvsync.download

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val MAX_INSTALL_ENTRIES = 20_000
private const val MAX_SMAPI_BYTES = 2L * 1024 * 1024 * 1024

internal class StagedInstallation(val target: File) : AutoCloseable {
    val directory: File
    val backup = File(target.parentFile, ".${target.name}.install-backup")

    init {
        require(!backup.exists()) { "An interrupted installation needs recovery: ${backup.path}" }
        Files.createDirectories(target.parentFile!!.toPath())
        directory = Files.createTempDirectory(target.parentFile!!.toPath(), ".${target.name}.install-").toFile()
    }

    override fun close() {
        directory.deleteRecursively()
    }
}

internal fun publishInstallations(
    installations: List<StagedInstallation>,
    move: (File, File) -> Unit = { from, to ->
        Files.move(from.toPath(), to.toPath())
        Unit
    }
) {
    val movedOriginals = mutableListOf<StagedInstallation>()
    val published = mutableListOf<StagedInstallation>()
    require(installations.map { it.target.canonicalPath }.distinct().size == installations.size)
    require(installations.none { it.backup.exists() }) { "An interrupted installation needs recovery" }
    try {
        for (installation in installations) {
            if (installation.target.exists()) {
                move(installation.target, installation.backup)
                movedOriginals += installation
            }
            move(installation.directory, installation.target)
            published += installation
        }
    } catch (failure: Exception) {
        for (installation in installations.asReversed()) {
            try {
                if (installation in published) {
                    check(installation.target.deleteRecursively()) { "Cannot remove incomplete installation" }
                }
                if (installation in movedOriginals) move(installation.backup, installation.target)
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
        }
        throw failure
    }
    for (installation in movedOriginals) {
        check(installation.backup.deleteRecursively()) {
            "Installed files, but could not remove the previous installation: ${installation.backup.path}"
        }
    }
}

internal fun smapiFileCount(archive: File): Int = ZipFile(archive).use { zip ->
    val names = mutableSetOf<String>()
    var count = 0
    var bytes = 0L
    val entries = zip.entries()
    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        require(names.size < MAX_INSTALL_ENTRIES) { "Too many SMAPI archive entries" }
        val name = entry.name.trimEnd('/')
        require(name.isNotBlank() && !name.startsWith('/') && '\\' !in name) { "Invalid SMAPI archive path" }
        require(name.split('/').none { it.isEmpty() || it == "." || it == ".." || it.any(Char::isISOControl) }) {
            "Invalid SMAPI archive path"
        }
        require(names.add(name)) { "Duplicate SMAPI archive entry" }
        if (!entry.isDirectory) {
            require(entry.size in 0..MAX_SMAPI_BYTES) { "Invalid SMAPI archive entry size" }
            bytes += entry.size
            require(bytes <= MAX_SMAPI_BYTES) { "SMAPI archive is too large" }
            count++
        } else {
            require(entry.size == 0L) { "SMAPI directory entry contains file data" }
        }
    }
    require(count > 0) { "SMAPI archive contains no files" }
    count
}

internal suspend fun extractSmapi(archive: File, destination: File, onFile: (String) -> Unit) {
    val expectedFiles = smapiFileCount(archive)
    val root = destination.canonicalPath + File.separator
    val buffer = ByteArray(256 * 1024)
    var total = 0L
    var count = 0
    val names = mutableSetOf<String>()
    archive.inputStream().use { input ->
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                currentCoroutineContext().ensureActive()
                require(names.size < MAX_INSTALL_ENTRIES && names.add(entry.name)) { "Invalid SMAPI archive entries" }
                val output = File(destination, entry.name)
                require(output.canonicalPath.startsWith(root)) { "SMAPI entry outside target directory" }
                if (entry.isDirectory) {
                    require(zip.read() == -1) { "SMAPI directory entry contains file data" }
                    Files.createDirectories(output.toPath())
                } else {
                    Files.createDirectories(output.parentFile!!.toPath())
                    check(!output.exists()) { "Duplicate SMAPI archive file" }
                    output.outputStream().buffered().use { stream ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = zip.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_SMAPI_BYTES) { "SMAPI archive is too large" }
                            stream.write(buffer, 0, read)
                        }
                    }
                    count++
                    onFile(entry.name)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
    check(count == expectedFiles) { "Incomplete SMAPI archive" }
}

internal suspend fun copyGameFile(source: File, destination: File) {
    Files.createDirectories(destination.parentFile!!.toPath())
    val expectedBytes = source.length()
    var copied = 0L
    val buffer = ByteArray(256 * 1024)
    source.inputStream().buffered().use { input ->
        destination.outputStream().buffered().use { output ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read
            }
        }
    }
    check(copied == expectedBytes) { "Source game file changed while copying: ${source.name}" }
}
