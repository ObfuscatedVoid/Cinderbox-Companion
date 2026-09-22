package com.sdvsync.saves

import com.sdvsync.fileaccess.FileAccessStrategy
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal suspend fun writeSaveFiles(
    access: FileAccessStrategy,
    directory: File,
    files: Map<String, ByteArray>
): Boolean {
    val transaction = UUID.randomUUID().toString()
    val staged = files.keys.associateWith { File(directory, "$it.$transaction.sdvsync_tmp") }
    val backups = files.keys.associateWith { File(directory, "$it.$transaction.sdvsync_bak") }
    val moved = mutableSetOf<String>()
    val installed = mutableSetOf<String>()
    try {
        check(access.exists(directory) || access.mkdirs(directory)) { "Cannot create save folder" }
        check(access.listFiles(directory)?.none { it.endsWith(".sdvsync_bak") } == true) {
            "An interrupted save needs recovery from its backup before writing"
        }
        for ((name, bytes) in files) {
            check(access.writeFile(staged.getValue(name), bytes)) { "Cannot stage $name" }
            check(access.readFile(staged.getValue(name))?.contentEquals(bytes) == true) { "Incomplete write: $name" }
        }
        for (name in files.keys) {
            val target = File(directory, name)
            if (access.exists(target)) {
                check(access.renameFile(target, backups.getValue(name))) { "Cannot preserve $name" }
                moved.add(name)
            }
            check(access.renameFile(staged.getValue(name), target)) { "Cannot replace $name" }
            installed.add(name)
        }
    } catch (failure: Exception) {
        withContext(NonCancellable) {
            val failures = mutableListOf<String>()
            for (name in (moved + installed).reversed()) {
                try {
                    val target = File(directory, name)
                    if (name in installed) check(access.deleteFile(target))
                    if (name in moved) check(access.renameFile(backups.getValue(name), target))
                } catch (_: Exception) {
                    failures.add(name)
                }
            }
            if (failures.isNotEmpty()) {
                throw IllegalStateException(
                    "Save recovery required for ${failures.joinToString()}; original backup files were retained",
                    failure
                )
            }
        }
        if (failure is CancellationException) throw failure
        return false
    } finally {
        withContext(NonCancellable) {
            staged.values.forEach { runCatching { access.deleteFile(it) } }
        }
    }
    withContext(NonCancellable) {
        for (name in moved) {
            check(access.deleteFile(backups.getValue(name))) {
                "Saved successfully, but temporary backup cleanup failed"
            }
        }
    }
    return true
}
