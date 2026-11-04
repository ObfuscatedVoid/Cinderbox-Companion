package com.sdvsync.qualification

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sdvsync.MainActivity
import com.sdvsync.ui.components.BottomTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R48A: actual MainActivity and both Navigation Compose graphs on an isolated,
 * freshly provisioned GitHub emulator. No credentials, game files, mods, saves,
 * permission grants, root, live Steam session, update, install or publication.
 * This is NOT physical-device, TalkBack or real-game acceptance.
 */
@RunWith(AndroidJUnit4::class)
class R48RealActivityDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun tab(tab: BottomTab) = rule.onNode(
        hasText(InstrumentationRegistry.getInstrumentation().targetContext.getString(tab.labelRes)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    )

    private fun enterOffline() {
        rule.onNodeWithText("Continue offline").performScrollTo().performClick()
        rule.onNodeWithText("Your Cinderbox workspace").assertExists()
        tab(BottomTab.HOME).assertIsSelected().assertIsDisplayed()
        rule.onNodeWithText("Continue offline").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        // All captures are of real UI in a zero-account, isolated, hosted emulator.
        // Never use these helpers on user hardware or a restored/private test profile.
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width >= 320 && bitmap.height >= 480)
        ctx.openFileOutput("r48-real-$name-320dp-fs160.png", Context.MODE_PRIVATE).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun realOfflineFiveTabNavigationAndPrivatePreviews() {
        enterOffline()
        assertEquals(5, BottomTab.entries.size)
        screenshot("home")
        val areas = listOf(
            Triple(BottomTab.DOWNLOAD, "Game setup path", "game"),
            Triple(BottomTab.SAVES, "Your saves", "saves"),
            Triple(BottomTab.MODS, "Managing mods", "mods"),
            Triple(BottomTab.SETTINGS, "Personalize and manage", "more")
        )
        for ((dest, heading, imageName) in areas) {
            tab(dest).performClick().assertIsSelected().assertIsDisplayed()
            rule.onNodeWithText(heading).assertExists()
            for (other in BottomTab.entries.filter { it != dest }) {
                tab(other).assertExists()
            }
            screenshot(imageName)
        }
        tab(BottomTab.HOME).performClick().assertIsSelected().assertIsDisplayed()
        rule.onNodeWithText("Your Cinderbox workspace").assertExists()
    }

    @Test fun realHomeShortcutsAndNestedBackNavigation() {
        enterOffline()
        rule.onNodeWithText("Open Game").performScrollTo().performClick()
        tab(BottomTab.DOWNLOAD).assertIsSelected()
        rule.onNodeWithText("Game setup path").assertExists()
        Espresso.pressBack()
        tab(BottomTab.HOME).assertIsSelected()
        rule.onNodeWithText("Your Cinderbox workspace").assertExists()
        rule.onNodeWithText("View installation health").performScrollTo().performClick()
        rule.onNodeWithText("Read-only checks. Detection is not cryptographic integrity verification.")
            .assertExists()
        Espresso.pressBack()
        tab(BottomTab.HOME).assertIsSelected()
    }

    @Test fun realPreLoginSettingsBackRequiresNoSteam() {
        // Settings are accessible before Steam authentication and must return to Login.
        rule.onNodeWithText("Continue offline").assertExists()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(ctx.getString(com.sdvsync.R.string.settings_title))
            .performScrollTo().performClick()
        rule.onNodeWithText(ctx.getString(com.sdvsync.R.string.appearance_style_label)).assertExists()
        Espresso.pressBack()
        rule.onNodeWithText("Continue offline").assertExists()
        rule.onNodeWithText("Your Cinderbox workspace").assertDoesNotExist()
    }

    @Test fun realOperationCenterReadOnlyReturn() {
        enterOffline()
        rule.onNodeWithText("View operation center").performScrollTo().performClick()
        rule.onNodeWithText("Live, read-only status and recovery guidance").assertExists()
        rule.onNodeWithText("No automatic retry or recovery is performed here. Status may be incomplete after Android stops the app.")
            .assertExists()
        Espresso.pressBack()
        tab(BottomTab.HOME).assertIsSelected()
        rule.onNodeWithText("Your Cinderbox workspace").assertExists()
    }
}
