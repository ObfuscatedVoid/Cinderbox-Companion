package com.sdvsync.fileaccess

import com.sdvsync.saves.requireSafeSaveName
import java.io.File

internal fun documentSegments(basePath: String, file: File): List<String> {
    val base = File(basePath).absolutePath.trimEnd('/')
    val path = file.absolutePath
    if (path == base) return emptyList()
    require(path.startsWith("$base/")) { "Path is outside the selected directory" }
    return path.removePrefix("$base/").split('/').onEach(::requireSafeSaveName)
}
