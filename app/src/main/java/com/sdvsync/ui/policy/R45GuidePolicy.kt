package com.sdvsync.ui.policy

/** Read-only journey copy, not authorization for any privileged operation. */
enum class R45GuideArea { GAME, SAVES, MODS, MORE }

data class R45GuideCopy(val heading: String, val steps: List<String>)

object R45GuidePolicy {
    fun forArea(area: R45GuideArea): R45GuideCopy = when (area) {
        R45GuideArea.GAME -> R45GuideCopy("Game setup path", listOf(
            "Inspect the fixed Cinderbox game destination",
            "Choose a version and review download prerequisites",
            "Use explicit download controls only after the checks succeed"
        ))
        R45GuideArea.SAVES -> R45GuideCopy("Your saves", listOf(
            "Review discovered local and cloud save information",
            "Inspect backups and sync differences",
            "Confirm any import or synchronization before it changes data"
        ))
        R45GuideArea.MODS -> R45GuideCopy("Managing mods", listOf(
            "Review installed mods and compatibility warnings",
            "Check profiles and any available updates",
            "Confirm enable, import or restore operations explicitly"
        ))
        R45GuideArea.MORE -> R45GuideCopy("Personalize and manage", listOf(
            "Choose an appearance and brightness mode",
            "Review Android storage access and Steam settings",
            "Open diagnostics or installation health when needed"
        ))
    }
}

