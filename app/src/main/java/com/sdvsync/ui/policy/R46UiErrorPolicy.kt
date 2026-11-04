package com.sdvsync.ui.policy

/** Public-facing error contexts. Error text is chosen from this allowlist, never a raw exception. */
enum class R46UiErrorArea {
    SAVES, MODS, GAME, GAME_DOWNLOAD, SMAPI, CINDERBOX, STEAM_LOGIN
}

data class R46UiErrorCopy(
    val title: String,
    val description: String,
    val retryLabel: String,
    val nextStep: String
)

/** Presentation only. This policy never triggers a retry, download, repair or sign-in. */
object R46UiErrorPolicy {
    fun forArea(area: R46UiErrorArea): R46UiErrorCopy = when (area) {
        R46UiErrorArea.SAVES -> R46UiErrorCopy(
            title = "Saves could not be loaded",
            description = "The save list is unavailable right now. This does not mean your saved files are missing or deleted.",
            retryLabel = "Retry save check",
            nextStep = "Try again, then check access in More if the problem continues."
        )
        R46UiErrorArea.MODS -> R46UiErrorCopy(
            title = "Mods could not be loaded",
            description = "The mod list is unavailable right now. Existing mods were not changed by this message.",
            retryLabel = "Retry mod check",
            nextStep = "Try again, then check access and installation health if needed."
        )
        R46UiErrorArea.GAME -> R46UiErrorCopy(
            title = "Game check needs attention",
            description = "Cinderbox could not complete the game check. The current installation has not been cleared by this message.",
            retryLabel = "Check again",
            nextStep = "Review game storage and permissions in Installation Health before starting another operation."
        )
        R46UiErrorArea.GAME_DOWNLOAD -> R46UiErrorCopy(
            title = "Game download stopped",
            description = "The game download did not complete. This message does not authorize replacing an existing installation.",
            retryLabel = "Retry download",
            nextStep = "Check connectivity and the fixed Cinderbox destination. A retry still uses the normal safety checks."
        )
        R46UiErrorArea.SMAPI -> R46UiErrorCopy(
            title = "SMAPI setup needs attention",
            description = "SMAPI could not complete its setup step. Do not remove existing game or mod files to resolve it.",
            retryLabel = "Review SMAPI",
            nextStep = "Inspect Installation Health and retry only through the existing explicit setup controls."
        )
        R46UiErrorArea.CINDERBOX -> R46UiErrorCopy(
            title = "Cinderbox download stopped",
            description = "The Cinderbox application download did not complete.",
            retryLabel = "Try again",
            nextStep = "Check connectivity, then use the existing download control if you wish to retry."
        )
        R46UiErrorArea.STEAM_LOGIN -> R46UiErrorCopy(
            title = "Steam sign-in needs attention",
            description = "Authentication did not finish. Your Steam session has not been confirmed.",
            retryLabel = "Try sign-in again",
            nextStep = "Check your credentials and Steam Guard prompt. You may also choose offline mode."
        )
    }
}
