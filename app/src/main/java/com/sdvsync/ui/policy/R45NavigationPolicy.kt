package com.sdvsync.ui.policy

/** Stable public destinations. Keep the four established routes intact for existing callers. */
enum class R45PrimaryTab(val route: String, val title: String) {
    HOME("home", "Home"),
    GAME("game_download", "Game"),
    SAVES("saves", "Saves"),
    MODS("mods", "Mods"),
    MORE("settings", "More")
}

/** Navigation-only allowlist. Never authorizes game, Steam, save, mod or update writes. */
object R45NavigationPolicy {
    val orderedTabs: List<R45PrimaryTab> = R45PrimaryTab.entries.toList()

    fun isPrimaryRoute(route: String?): Boolean =
        orderedTabs.any { it.route == route }

    fun tabForRoute(route: String?): R45PrimaryTab =
        orderedTabs.firstOrNull { it.route == route } ?: R45PrimaryTab.HOME

    /** Shortcuts can lead to known primary tabs only; sub-screens use explicit callbacks. */
    fun primaryShortcutRoute(tab: R45PrimaryTab): String = tab.route
}
