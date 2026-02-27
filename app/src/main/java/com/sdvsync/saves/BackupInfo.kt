package com.sdvsync.saves

import java.io.File

data class BackupInfo(
    val backupDir: File,
    val timestamp: String,
    val metadata: SaveMetadata?,
    val fileCount: Int,
    val totalSize: Long
)
