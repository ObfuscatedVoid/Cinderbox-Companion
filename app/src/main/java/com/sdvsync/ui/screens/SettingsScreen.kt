package com.sdvsync.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.PixelDivider
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.viewmodels.SettingsViewModel
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

@Composable
private fun strategyDisplayName(name: String): String = when (name.lowercase()) {
    "root" -> stringResource(R.string.settings_file_access_root)
    "shizuku" -> stringResource(R.string.settings_file_access_shizuku)
    "all files" -> stringResource(R.string.settings_file_access_all_files)
    "saf" -> stringResource(R.string.settings_file_access_saf)
    "saf (staging)" -> stringResource(R.string.settings_file_access_saf_staging)
    "manual" -> stringResource(R.string.settings_file_access_manual)
    else -> name
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val context = LocalContext.current

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.load()
    }

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

    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
    )
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
    )
    val filterChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    PixelIconButton(
                        pixelData = ArrowLeftData,
                        onClick = onBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // File access mode
            SectionHeader(stringResource(R.string.settings_file_access))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    val currentModeDisplay = strategyDisplayName(state.fileAccessMode)
                    val availableModesDisplay = state.availableModes.map { strategyDisplayName(it) }
                    Text(
                        stringResource(R.string.settings_file_access_current, currentModeDisplay),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(
                            R.string.settings_file_access_available,
                            availableModesDisplay.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.settings_file_access_choose),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = state.preferredStrategy == null,
                            onClick = { viewModel.setFileAccessMode(null) },
                            label = { Text(stringResource(R.string.settings_file_access_auto)) },
                            colors = filterChipColors,
                        )
                        state.availableModes.forEach { mode ->
                            FilterChip(
                                selected = state.preferredStrategy.equals(mode, ignoreCase = true),
                                onClick = { viewModel.setFileAccessMode(mode) },
                                label = { Text(strategyDisplayName(mode)) },
                                colors = filterChipColors,
                            )
                        }
                    }
                }
            }

            // Shizuku status
            if (!state.hasRoot) {
                Spacer(Modifier.height(12.dp))
                ShizukuStatusSection(
                    installed = state.shizukuInstalled,
                    running = state.shizukuRunning,
                    permissionGranted = state.shizukuPermissionGranted,
                    onRequestPermission = { viewModel.requestShizukuPermission() },
                    onBindService = { viewModel.bindShizukuService() },
                )
            }

            // All Files Access
            if (state.allFilesEligible && !state.hasRoot) {
                Spacer(Modifier.height(12.dp))
                AllFilesAccessSection(
                    permissionGranted = state.allFilesPermissionGranted,
                    accessWorking = state.allFilesAccessWorking,
                    onGrantPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        allFilesLauncher.launch(intent)
                    },
                )
            }

            // SAF
            if (state.safEligible && !state.hasRoot) {
                Spacer(Modifier.height(12.dp))
                SAFAccessSection(
                    configured = state.safConfigured,
                    isStaging = state.safIsStaging,
                    onSelectDirectory = { safLauncher.launch(null) },
                    onRevoke = { viewModel.clearSafAccess() },
                )
            }

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            // Backups
            SectionHeader(stringResource(R.string.settings_backups_title))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
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
                        colors = sliderColors,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            // Auto-sync
            SectionHeader(stringResource(R.string.settings_auto_sync_title))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
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
                                colors = switchColors,
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.settings_auto_sync_root_only),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            // Account
            SectionHeader(stringResource(R.string.settings_steam_account))
            Spacer(Modifier.height(16.dp))
            StardewButton(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                variant = StardewButtonVariant.Danger,
            ) {
                Text(stringResource(R.string.settings_logout))
            }

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            // About
            SectionHeader(stringResource(R.string.settings_about_title))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
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

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            // Diagnostics
            SectionHeader(stringResource(R.string.settings_diagnostics_title))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.settings_share_logs_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    StardewOutlinedButton(onClick = { AppLogger.shareLogs(context) }) {
                        Text(stringResource(R.string.settings_share_logs))
                    }
                }
            }
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
    StardewCard {
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
                StardewOutlinedButton(onClick = onRevoke) {
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
                StardewOutlinedButton(onClick = onSelectDirectory) {
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
    StardewCard {
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
                    StardewOutlinedButton(onClick = onRequestPermission) {
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
                    StardewOutlinedButton(onClick = onBindService) {
                        Text(stringResource(R.string.settings_shizuku_connect))
                    }
                }
            }
        }
    }
}

@Composable
private fun AllFilesAccessSection(
    permissionGranted: Boolean,
    accessWorking: Boolean,
    onGrantPermission: () -> Unit,
) {
    StardewCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.settings_all_files_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            when {
                accessWorking -> {
                    Text(
                        stringResource(R.string.settings_all_files_granted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                permissionGranted -> {
                    Text(
                        stringResource(R.string.settings_all_files_no_access),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {
                    Text(
                        stringResource(R.string.settings_all_files_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    StardewOutlinedButton(onClick = onGrantPermission) {
                        Text(stringResource(R.string.settings_all_files_grant))
                    }
                }
            }
        }
    }
}
