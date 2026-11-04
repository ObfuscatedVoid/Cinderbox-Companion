package com.sdvsync.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R46UiErrorPolicyTest {
    @Test fun savedFilesAreNotDeclaredMissing() {
        val copy = R46UiErrorPolicy.forArea(R46UiErrorArea.SAVES)
        assertTrue(copy.title.contains("Saves"))
        assertTrue(copy.description.contains("does not mean"))
        assertTrue(copy.retryLabel.startsWith("Retry"))
    }

    @Test fun modsAreNotDeclaredChanged() {
        val copy = R46UiErrorPolicy.forArea(R46UiErrorArea.MODS)
        assertTrue(copy.title.contains("Mods"))
        assertTrue(copy.description.contains("not changed"))
        assertTrue(copy.retryLabel.startsWith("Retry"))
    }

    @Test fun gameAndSteamFailuresAreContextualNotRaw() {
        for (area in listOf(R46UiErrorArea.GAME, R46UiErrorArea.GAME_DOWNLOAD,
                          R46UiErrorArea.SMAPI, R46UiErrorArea.CINDERBOX,
                          R46UiErrorArea.STEAM_LOGIN)) {
            val copy = R46UiErrorPolicy.forArea(area)
            assertTrue(copy.title.isNotBlank())
            assertTrue(copy.description.isNotBlank())
            assertTrue(copy.nextStep.isNotBlank())
        }
    }

    @Test fun publicCopyCannotContainRawPaths() {
        R46UiErrorArea.entries.forEach { area ->
            val copy = R46UiErrorPolicy.forArea(area)
            val text = listOf(copy.title, copy.description, copy.nextStep, copy.retryLabel).joinToString(" ")
            assertFalse(text.contains("/storage/"))
            assertFalse(text.contains("/data/"))
            assertFalse(text.contains("Exception"))
            assertFalse(text.contains("stacktrace"))
        }
        assertEquals(7, R46UiErrorArea.entries.size)
    }
}
