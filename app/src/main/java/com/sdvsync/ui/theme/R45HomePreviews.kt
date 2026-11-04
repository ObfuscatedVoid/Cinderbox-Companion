package com.sdvsync.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sdvsync.ui.screens.HomeScreen

/** Synthetic previews: no personal accounts, game folders, saves or mod data. */
@Composable
private fun HomePreview(style: AppearanceStyle, dark: Boolean) {
    SdvSyncTheme(style = style, darkTheme = dark) {
        HomeScreen(
            onOpenGame = {}, onOpenSaves = {}, onOpenMods = {}, onOpenMore = {},
            onOpenInstallationHealth = {}, onOpenOperationCenter = {}
        )
    }
}

@Preview(name = "R45 Home · Earthy phone", widthDp = 320, heightDp = 640, showBackground = true)
@Composable
private fun R45EarthyPhone() = HomePreview(AppearanceStyle.EARTHY, false)

@Preview(name = "R45 Home · Earthy dark", widthDp = 400, heightDp = 740, showBackground = true)
@Composable
private fun R45EarthyDark() = HomePreview(AppearanceStyle.EARTHY, true)

@Preview(name = "R45 Home · Classic", widthDp = 320, heightDp = 640, showBackground = true)
@Composable
private fun R45ClassicPhone() = HomePreview(AppearanceStyle.CLASSIC, false)
