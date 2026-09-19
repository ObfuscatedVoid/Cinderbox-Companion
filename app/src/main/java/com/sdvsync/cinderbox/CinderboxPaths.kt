package com.sdvsync.cinderbox

object CinderboxPaths {
    const val ROOT_DIR = "/storage/emulated/0/StardewValley"
    const val DESKTOP_DIR = "$ROOT_DIR/desktop"

    const val GAME_FILES_DIR = "$DESKTOP_DIR/GameFiles"
    const val SAVES_DIR = "$DESKTOP_DIR/Saves"
    const val MODS_DIR = "$DESKTOP_DIR/Mods"
    const val SMAPI_DIR = "$ROOT_DIR/smapi-internal"

    const val LEGACY_GAME_FILES_DIR = "$ROOT_DIR/GameFiles"
    const val LEGACY_SAVES_DIR = "$ROOT_DIR/Saves"
    const val LEGACY_MODS_DIR = "$ROOT_DIR/Mods"

    fun isInstalled(): Boolean = CinderboxLayout().isInstalled()
}
