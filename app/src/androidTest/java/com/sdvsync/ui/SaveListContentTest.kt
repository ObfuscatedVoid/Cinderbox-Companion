package com.sdvsync.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sdvsync.saves.SaveMetadata
import com.sdvsync.sync.SyncDirection
import com.sdvsync.ui.components.SaveListContent
import com.sdvsync.ui.theme.SdvSyncTheme
import com.sdvsync.ui.viewmodels.DashboardState
import com.sdvsync.ui.viewmodels.SaveEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SaveListContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val save = SaveEntry(
        "Test_1",
        null,
        SaveMetadata("Test Farmer", "Test Farm", 1, 0, 1, "1.6.15", 0, "1"),
        SyncDirection.PUSH,
        "On this device",
        false,
        true
    )

    @Test
    fun loadedSaveRemainsVisibleDuringRefresh() {
        content(DashboardState(saves = listOf(save), isRefreshing = true))
        compose.onNodeWithText("Test Farmer", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun failedRefreshKeepsSaveAndOffersRetry() {
        content(DashboardState(saves = listOf(save), error = "Steam is unavailable"))
        compose.onNodeWithText("Test Farmer", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Steam is unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertHasClickAction()
    }

    @Test
    fun emptyListStillAcceptsPullToRefresh() {
        var refreshes = 0
        content(DashboardState(), onRefresh = { refreshes++ })
        compose.onNodeWithTag("save-list").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    private fun content(state: DashboardState, onRefresh: () -> Unit = {}) {
        compose.setContent {
            SdvSyncTheme {
                SaveListContent(state, onRefresh, { _, _, _ -> }, Modifier.fillMaxSize())
            }
        }
    }
}
