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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.saves.SaveBackupManager
import kotlin.math.roundToInt
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
                stringResource(R.string.settings_file_access),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_file_access_current, state.fileAccessMode),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.settings_file_access_available, state.availableModes.joinToString(", ")),
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

            // SAF
            if (state.safEligible && !state.availableModes.contains("Root")) {
                Spacer(Modifier.height(12.dp))
                SAFAccessSection(
                    configured = state.safConfigured,
                    isStaging = state.safIsStaging,
                    onSelectDirectory = { safLauncher.launch(null) },
                    onRevoke = { viewModel.clearSafAccess() },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Backups
            Text(
                stringResource(R.string.settings_backups_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_backups_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            var sliderValue by remember(state.maxBackups) {
                mutableFloatStateOf(state.maxBackups.toFloat())
            }
            Text(
                stringResource(R.string.settings_backups_count, sliderValue.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    viewModel.setMaxBackups(sliderValue.roundToInt())
                },
                valueRange = SaveBackupManager.MIN_MAX_BACKUPS.toFloat()..SaveBackupManager.MAX_MAX_BACKUPS.toFloat(),
                steps = SaveBackupManager.MAX_MAX_BACKUPS - SaveBackupManager.MIN_MAX_BACKUPS - 1,
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Auto-sync (root only)
            Text(
                stringResource(R.string.settings_auto_sync_title),
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
                            stringResource(R.string.settings_auto_sync),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.settings_auto_sync_desc),
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
                    stringResource(R.string.settings_auto_sync_root_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Account
            Text(
                stringResource(R.string.settings_steam_account),
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
                Text(stringResource(R.string.settings_logout))
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // About
            Text(
                stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_about),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.settings_about_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SAFAccessSection(
    configured: Boolean,
    isStaging: Boolean,
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
                stringResource(R.string.settings_saf_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            if (configured) {
                Text(
                    if (isStaging) {
                        stringResource(R.string.settings_saf_staging_configured)
                    } else {
                        stringResource(R.string.settings_saf_configured)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRevoke) {
                    Text(stringResource(R.string.settings_saf_revoke))
                }
            } else {
                Text(
                    stringResource(R.string.settings_saf_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_saf_staging_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSelectDirectory) {
                    Text(stringResource(R.string.settings_saf_select))
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
                stringResource(R.string.settings_shizuku_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            when {
                !installed -> {
                    Text(
                        stringResource(R.string.settings_shizuku_not_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !running -> {
                    Text(
                        stringResource(R.string.settings_shizuku_not_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !permissionGranted -> {
                    Text(
                        stringResource(R.string.settings_shizuku_grant_prompt),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequestPermission) {
                        Text(stringResource(R.string.settings_shizuku_grant_permission))
                    }
                }
                else -> {
                    Text(
                        stringResource(R.string.settings_shizuku_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBindService) {
                        Text(stringResource(R.string.settings_shizuku_connect))
                    }
                }
            }
        }
    }
}
