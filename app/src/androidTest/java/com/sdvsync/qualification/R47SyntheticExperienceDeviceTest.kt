package com.sdvsync.qualification

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sdvsync.ui.components.BottomTab
import com.sdvsync.ui.components.R45AreaGuide
import com.sdvsync.ui.components.R46ErrorPanel
import com.sdvsync.ui.components.StardewBottomBar
import com.sdvsync.ui.policy.R45GuideArea
import com.sdvsync.ui.policy.R46UiErrorArea
import com.sdvsync.ui.policy.R46UiErrorPolicy
import com.sdvsync.ui.screens.HomeScreen
import com.sdvsync.ui.theme.AppearanceStyle
import com.sdvsync.ui.theme.SdvSyncTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R47 synthetic UI acceptance. No real MainActivity, Steam account, saves, mods,
 * storage, network, device settings, permissions, download or repair operations.
 * Every screenshot is generated from static synthetic data on a disposable emulator.
 */
@RunWith(AndroidJUnit4::class)
class R47SyntheticExperienceDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun allFiveTabsHaveCorrectSelectionSemantics() {
        var active by mutableStateOf(BottomTab.HOME)
        rule.setContent {
            SdvSyncTheme(style = AppearanceStyle.EARTHY, darkTheme = false) {
                StardewBottomBar(active, onTabSelected = { active = it })
            }
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(5, BottomTab.entries.size)
        for (tab in BottomTab.entries) {
            val label = ctx.getString(tab.labelRes)
            rule.onNodeWithText(label).performClick().assertIsSelected()
            for (other in BottomTab.entries.filter { it != tab }) {
                rule.onNodeWithText(ctx.getString(other.labelRes)).assertIsNotSelected()
            }
            rule.runOnIdle { assertEquals(tab, active) }
        }
    }

    @Test fun homeShortcutInvokesOnlyItsProvidedCallback() {
        val calls = mutableStateListOf<String>()
        rule.setContent {
            SdvSyncTheme(style = AppearanceStyle.EARTHY, darkTheme = false) {
                HomeScreen(
                    onOpenGame = { calls.add("game") },
                    onOpenSaves = { calls.add("saves") },
                    onOpenMods = { calls.add("mods") },
                    onOpenMore = { calls.add("more") },
                    onOpenInstallationHealth = { calls.add("health") },
                    onOpenOperationCenter = { calls.add("operations") }
                )
            }
        }
        val shortcuts = listOf(
            "Open Game" to "game",
            "Open Saves" to "saves",
            "Open Mods" to "mods",
            "Open More" to "more",
            "View installation health" to "health",
            "Open operation center" to "operations"
        )
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
        shortcuts.forEachIndexed { index, (label, expected) ->
            rule.onNodeWithText(label).performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(shortcuts.take(index + 1).map { it.second }, calls.toList())
                assertEquals(expected, calls.last())
            }
        }
    }

    @Test fun errorPanelNeverRetriesBeforeExplicitTap() {
        var retryCount by mutableStateOf(0)
        rule.setContent {
            SdvSyncTheme(style = AppearanceStyle.EARTHY, darkTheme = false) {
                R46ErrorPanel(R46UiErrorArea.SAVES, onRetry = { retryCount++ })
            }
        }
        rule.onNodeWithText("Saves could not be loaded").assertExists()
        rule.runOnIdle { assertEquals(0, retryCount) }
        rule.onNodeWithText("Retry save check").performClick()
        rule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test fun staticErrorCannotExposeAnUnwiredAction() {
        rule.setContent {
            SdvSyncTheme(style = AppearanceStyle.CLASSIC, darkTheme = true) {
                R46ErrorPanel(R46UiErrorArea.STEAM_LOGIN)
            }
        }
        rule.onNodeWithText("Steam sign-in needs attention").assertExists()
        rule.onNodeWithText("Try sign-in again").assertDoesNotExist()
    }

    @Test fun earthyHomeDay320Font160() = capture("home-earthy-day", AppearanceStyle.EARTHY, false, null)
    @Test fun earthyHomeNight320Font160() = capture("home-earthy-night", AppearanceStyle.EARTHY, true, null)
    @Test fun classicHomeDay320Font160() = capture("home-classic-day", AppearanceStyle.CLASSIC, false, null)
    @Test fun earthyGame320Font160() = capture("game-earthy-day", AppearanceStyle.EARTHY, false, R45GuideArea.GAME)
    @Test fun earthySaves320Font160() = capture("saves-earthy-night", AppearanceStyle.EARTHY, true, R45GuideArea.SAVES)
    @Test fun earthyMods320Font160() = capture("mods-earthy-day", AppearanceStyle.EARTHY, false, R45GuideArea.MODS)
    @Test fun earthyMore320Font160() = capture("more-earthy-day", AppearanceStyle.EARTHY, false, R45GuideArea.MORE)

    /**
     * Synthetic viewport only, not a claim about real screen / TalkBack focus order.
     * A separate scroll container and fixed bottom navigation let large-font
     * content reflow without hiding the primary tab controls.
     */
    private fun capture(name: String, style: AppearanceStyle, dark: Boolean, area: R45GuideArea?) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale = 1.6f)) {
                SdvSyncTheme(style = style, darkTheme = dark) {
                    Surface(
                        modifier = Modifier.width(320.dp).height(720.dp).testTag("r47-synthetic"),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val tab = when (area) {
                            null -> BottomTab.HOME
                            R45GuideArea.GAME -> BottomTab.DOWNLOAD
                            R45GuideArea.SAVES -> BottomTab.SAVES
                            R45GuideArea.MODS -> BottomTab.MODS
                            R45GuideArea.MORE -> BottomTab.SETTINGS
                        }
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                if (area == null) {
                                    HomeScreen(
                                        onOpenGame = {}, onOpenSaves = {}, onOpenMods = {}, onOpenMore = {},
                                        onOpenInstallationHealth = {}, onOpenOperationCenter = {}
                                    )
                                } else {
                                    val error = when (area) {
                                        R45GuideArea.GAME -> R46UiErrorArea.GAME
                                        R45GuideArea.SAVES -> R46UiErrorArea.SAVES
                                        R45GuideArea.MODS -> R46UiErrorArea.MODS
                                        R45GuideArea.MORE -> R46UiErrorArea.STEAM_LOGIN
                                    }
                                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        R45AreaGuide(area)
                                        R46ErrorPanel(error)
                                    }
                                }
                            }
                            StardewBottomBar(tab, onTabSelected = {})
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("r47-synthetic").assertIsDisplayed()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedTab = when (area) {
            null -> BottomTab.HOME
            R45GuideArea.GAME -> BottomTab.DOWNLOAD
            R45GuideArea.SAVES -> BottomTab.SAVES
            R45GuideArea.MODS -> BottomTab.MODS
            R45GuideArea.MORE -> BottomTab.SETTINGS
        }
        rule.onNode(hasText(ctx.getString(expectedTab.labelRes)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected().assertIsDisplayed()
        if (area == null) {
            rule.onNodeWithText("Your Cinderbox workspace").assertIsDisplayed()
        } else {
            val error = when (area) {
                R45GuideArea.GAME -> R46UiErrorArea.GAME
                R45GuideArea.SAVES -> R46UiErrorArea.SAVES
                R45GuideArea.MODS -> R46UiErrorArea.MODS
                R45GuideArea.MORE -> R46UiErrorArea.STEAM_LOGIN
            }
            rule.onNodeWithText(R46UiErrorPolicy.forArea(error).title)
                .performScrollTo().assertIsDisplayed()
        }
        val bitmap = rule.onNodeWithTag("r47-synthetic").captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width >= 320 && bitmap.height > 100)
        // Test app's private synthetic fixture, never a real account or installed-game screen.
        ctx.openFileOutput("r47-synthetic-$name-320dp-fs160.png", Context.MODE_PRIVATE).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
