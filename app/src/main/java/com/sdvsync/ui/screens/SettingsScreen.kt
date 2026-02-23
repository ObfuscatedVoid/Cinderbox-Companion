package com.sdvsync.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.viewmodels.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.onSafDirectorySelected(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // File access mode
            Text(
                "File Access",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Current: ${state.fileAccessMode}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Available: ${state.availableModes.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Shizuku status
            if (!state.availableModes.contains("Root")) {
                Spacer(Modifier.height(12.dp))
                ShizukuStatusSection(
                    installed = state.shizukuInstalled,
                    running = state.shizukuRunning,
                    permissionGranted = state.shizukuPermissionGranted,
                    onRequestPermission = { viewModel.requestShizukuPermission() },
                    onBindService = { viewModel.bindShizukuService() },
                )
            }

            // SAF (Android 11-13)
            if (state.safEligible && !state.availableModes.contains("Root")) {
                Spacer(Modifier.height(12.dp))
                SAFAccessSection(
                    configured = state.safConfigured,
                    onSelectDirectory = { safLauncher.launch(null) },
                    onRevoke = { viewModel.clearSafAccess() },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Auto-sync (root only)
            Text(
                "Auto Sync",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            if (state.autoSyncAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sync on game close",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Automatically push save when Stardew Valley exits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.autoSyncEnabled,
                        onCheckedChange = { viewModel.toggleAutoSync(it) },
                    )
                }
            } else {
                Text(
                    "Requires root access. Not available on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Account
            Text(
                "Steam Account",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Log Out")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // About
            Text(
                "About",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "SDV Sync v0.1.0",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Sync Stardew Valley saves between Steam Cloud and Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SAFAccessSection(
    configured: Boolean,
    onSelectDirectory: () -> Unit,
    onRevoke: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Storage Access Framework",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            if (configured) {
                Text(
                    "Save directory access configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRevoke) {
                    Text("Revoke Access")
                }
            } else {
                Text(
                    "Grant access to Stardew Valley saves without root or Shizuku. " +
                        "Navigate to: Android > data > com.chucklefish.stardewvalley > files > Saves",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSelectDirectory) {
                    Text("Select Save Directory")
                }
            }
        }
    }
}

@Composable
private fun ShizukuStatusSection(
    installed: Boolean,
    running: Boolean,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onBindService: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Shizuku (non-root file access)",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            when {
                !installed -> {
                    Text(
                        "Shizuku app not installed. Install from Play Store or GitHub.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !running -> {
                    Text(
                        "Shizuku is installed but not running. Start it via ADB or wireless debugging.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !permissionGranted -> {
                    Text(
                        "Shizuku is running. Grant permission to enable file access.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequestPermission) {
                        Text("Grant Permission")
                    }
                }
                else -> {
                    Text(
                        "Shizuku is ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBindService) {
                        Text("Connect File Service")
                    }
                }
            }
        }
    }
}
