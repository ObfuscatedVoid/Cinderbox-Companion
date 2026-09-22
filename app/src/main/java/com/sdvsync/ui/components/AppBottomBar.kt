package com.sdvsync.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sdvsync.R

enum class BottomTab(val route: String, val labelRes: Int) {
    SAVES("saves", R.string.tab_saves),
    MODS("mods", R.string.tab_mods),
    DOWNLOAD("game_download", R.string.tab_download),
    SETTINGS("settings", R.string.tab_settings)
}

@Composable
fun AppBottomBar(selectedTab: BottomTab, onTabSelected: (BottomTab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        BottomTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { if (tab != selectedTab) onTabSelected(tab) },
                icon = {
                    AppIcon(
                        when (tab) {
                            BottomTab.SAVES -> AppIcons.Saves
                            BottomTab.MODS -> AppIcons.Mods
                            BottomTab.DOWNLOAD -> AppIcons.Download
                            BottomTab.SETTINGS -> AppIcons.Settings
                        }
                    )
                },
                label = { Text(stringResource(tab.labelRes)) }
            )
        }
    }
}
