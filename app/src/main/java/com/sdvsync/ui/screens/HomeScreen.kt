package com.sdvsync.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.components.R44BResponsiveColumn
import com.sdvsync.ui.components.R44BStatePanel
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.policy.R44BStateKind

/**
 * R45-A: a source-only navigation hub. No read of game locations, Steam accounts,
 * saves, mod inventories, credentials or update journals. Each action opens an
 * existing screen; none starts a download, repair, sync or file operation.
 */
@Composable
fun HomeScreen(
    onOpenGame: () -> Unit,
    onOpenSaves: () -> Unit,
    onOpenMods: () -> Unit,
    onOpenMore: () -> Unit,
    onOpenInstallationHealth: () -> Unit,
    onOpenOperationCenter: () -> Unit
) {
    Scaffold(topBar = { StardewTopAppBar("Home") }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())) {
            R44BResponsiveColumn {
                Text(
                    "Your Cinderbox workspace",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Find your game, saves and mods in one place. The Home screen only opens other screens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Text("Explore", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(10.dp))
                R45HomeShortcut("Game", "Choose a game version and inspect Cinderbox installation checks.",
                    "Open Game", onOpenGame)
                R45HomeShortcut("Saves", "View your existing save dashboard and its sync controls.",
                    "Open Saves", onOpenSaves)
                R45HomeShortcut("Mods", "Browse and manage mods from the dedicated screen.",
                    "Open Mods", onOpenMods)
                R45HomeShortcut("More", "Appearance, access settings and account controls.",
                    "Open More", onOpenMore)
                Spacer(Modifier.height(12.dp))
                Text("Inspect", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(10.dp))
                R45HomeShortcut("Installation health",
                    "Inspect local installation evidence before considering an operation.",
                    "View installation health", onOpenInstallationHealth)
                R45HomeShortcut("Operation center",
                    "Review current operation status and recommended next steps.",
                    "Open operation center", onOpenOperationCenter)
                Spacer(Modifier.height(12.dp))
                R44BStatePanel(
                    kind = R44BStateKind.INFO,
                    title = "Shortcuts do not change files",
                    description = "Opening a screen does not install, update, repair or replace your game. " +
                        "The destination and permission checks remain in their existing workflows."
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun R45HomeShortcut(
    title: String,
    description: String,
    actionLabel: String,
    onOpen: () -> Unit
) {
    StardewCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            StardewOutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
