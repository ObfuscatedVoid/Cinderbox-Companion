package com.sdvsync.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R45NavigationPolicyTest {
    @Test fun fiveOrderedPublicTabs() {
        assertEquals(listOf("Home", "Game", "Saves", "Mods", "More"),
            R45NavigationPolicy.orderedTabs.map { it.title })
    }

    @Test fun existingRoutesRemainUnchanged() {
        assertEquals("game_download", R45PrimaryTab.GAME.route)
        assertEquals("saves", R45PrimaryTab.SAVES.route)
        assertEquals("mods", R45PrimaryTab.MODS.route)
        assertEquals("settings", R45PrimaryTab.MORE.route)
    }

    @Test fun unknownRouteFallsBackToHomeWithoutNavigation() {
        assertEquals(R45PrimaryTab.HOME, R45NavigationPolicy.tabForRoute("unknown"))
        assertEquals(R45PrimaryTab.HOME, R45NavigationPolicy.tabForRoute(null))
        assertFalse(R45NavigationPolicy.isPrimaryRoute("steam_diagnostics"))
        assertFalse(R45NavigationPolicy.isPrimaryRoute("backups/private"))
    }

    @Test fun allFivePrimaryRoutesAreRecognized() {
        assertEquals(5, R45NavigationPolicy.orderedTabs.map { it.route }.toSet().size)
        assertTrue(R45NavigationPolicy.orderedTabs.all {
            R45NavigationPolicy.isPrimaryRoute(it.route) &&
                R45NavigationPolicy.tabForRoute(it.route) == it &&
                R45NavigationPolicy.primaryShortcutRoute(it) == it.route
        })
    }
}
