package com.sdvsync.saves

internal fun requireSafeSaveName(name: String) {
    require(
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            '/' !in name &&
            '\\' !in name &&
            name.none { it.isISOControl() }
    ) { "Invalid save file name" }
}
