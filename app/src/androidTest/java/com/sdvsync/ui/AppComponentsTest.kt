package com.sdvsync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.components.*
import com.sdvsync.ui.theme.SdvSyncTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun navigationExposesSelectionAndOnlyNavigatesWhenChangingTabs() {
        var changes = 0
        compose.setContent {
            var selected by remember { mutableStateOf(MainTab.SAVES) }
            SdvSyncTheme {
                AppNavigationScaffold(selected, {
                    selected = it
                    changes++
                }) { Text("Screen content") }
            }
        }
        compose.onNodeWithText("Saves").assertIsSelected().performClick()
        compose.onNodeWithText("Mods").performClick().assertIsSelected()
        compose.onNodeWithText("Saves").assertIsNotSelected()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    @Test
    fun wideNavigationKeepsAllDestinationsVisibleAtLargeFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                SdvSyncTheme {
                    Box(Modifier.requiredSize(800.dp, 400.dp)) {
                        var selected by remember { mutableStateOf(MainTab.SAVES) }
                        AppNavigationScaffold(selected, { selected = it }) { Text("Screen content") }
                    }
                }
            }
        }
        listOf("Saves", "Mods", "Download", "Settings").forEach {
            compose.onNodeWithText(it).assertIsDisplayed().assertHasClickAction()
        }
        compose.onNodeWithText("Download").performClick().assertIsSelected()
        val destination = compose.onNodeWithText("Download").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithText("Screen content").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(destination.right < content.left)
    }

    @Test
    fun disabledActionCannotRunAndHasAFullTouchTarget() {
        var clicks = 0
        compose.setContent {
            SdvSyncTheme {
                Column {
                    AppButton(onClick = { clicks++ }, enabled = false) { Text("Unavailable") }
                    AppButton(onClick = { clicks++ }) { Text("Available") }
                }
            }
        }
        compose.onNodeWithText("Unavailable").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Available").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun longDialogKeepsActionsVisibleAtLargeFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                SdvSyncTheme(darkTheme = true) {
                    AppDialog(
                        onDismissRequest = {},
                        title = "Confirm download",
                        text = "This is a long explanation of the save operation.\n".repeat(50),
                        confirmButton = { AppButton(onClick = {}) { Text("Download") } },
                        dismissButton = { AppOutlinedButton(onClick = {}) { Text("Cancel") } }
                    )
                }
            }
        }
        compose.onNodeWithText("Download").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHasClickAction()
    }
}
