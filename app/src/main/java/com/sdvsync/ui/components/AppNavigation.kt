package com.sdvsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R

enum class MainTab(val route: String, val labelRes: Int) {
    SAVES("saves", R.string.tab_saves),
    MODS("mods", R.string.tab_mods),
    DOWNLOAD("game_download", R.string.tab_download),
    SETTINGS("settings", R.string.tab_settings)
}

@Composable
fun AppNavigationScaffold(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp && showNavigation) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                        .width(120.dp)
                        .verticalScroll(rememberScrollState()),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
                ) {
                    MainTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = tab == selectedTab,
                            onClick = { if (tab != selectedTab) onTabSelected(tab) },
                            icon = { TabIcon(tab) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).consumeWindowInsets(
                        if (showNavigation) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0)
                    )
                ) { content() }
                if (showNavigation) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == selectedTab,
                                onClick = { if (tab != selectedTab) onTabSelected(tab) },
                                icon = { TabIcon(tab) },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab) {
    AppIcon(
        when (tab) {
            MainTab.SAVES -> AppIcons.Saves
            MainTab.MODS -> AppIcons.Mods
            MainTab.DOWNLOAD -> AppIcons.Download
            MainTab.SETTINGS -> AppIcons.Settings
        }
    )
}
