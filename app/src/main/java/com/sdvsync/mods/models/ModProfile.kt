package com.sdvsync.mods.models

data class ModProfile(
    val name: String,
    val enabledModIds: Set<String>,
    val createdAt: Long = System.currentTimeMillis()
)
