package com.sdvsync.mods.models

data class SaveModAssociation(
    val saveFolderName: String,
    val enabledMods: Set<AssociatedMod>,
    val capturedAt: Long = System.currentTimeMillis()
)

data class AssociatedMod(val uniqueID: String, val name: String, val version: String)
