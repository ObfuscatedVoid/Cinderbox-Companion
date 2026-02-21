package com.sdvsync.fileaccess

import java.io.File

/**
 * Abstraction for file system access.
 * Different implementations handle root, Shizuku, and manual access modes.
 */
interface FileAccessStrategy {
    val name: String

    suspend fun exists(file: File): Boolean
    suspend fun listDirectories(dir: File): List<String>?
    suspend fun listFiles(dir: File): List<String>?
    suspend fun readFile(file: File): ByteArray?
    suspend fun writeFile(file: File, data: ByteArray): Boolean
    suspend fun deleteFile(file: File): Boolean
    suspend fun renameFile(from: File, to: File): Boolean
    suspend fun mkdirs(dir: File): Boolean
}
