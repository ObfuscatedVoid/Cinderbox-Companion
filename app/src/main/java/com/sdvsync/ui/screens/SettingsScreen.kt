package com.sdvsync.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdvsync.BuildConfig
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.ui.components.AppButton
import com.sdvsync.ui.components.AppCard
import com.sdvsync.ui.components.AppDialog
import com.sdvsync.ui.components.AppDivider
import com.sdvsync.ui.components.AppOutlinedButton
import com.sdvsync.ui.components.AppTopBar
import com.sdvsync.ui.components.ButtonVariant
import com.sdvsync.ui.components.CinderboxMigrationDialog
import com.sdvsync.ui.components.StoragePermissionDialog
import com.sdvsync.ui.components.rememberStoragePermissionRequest
import com.sdvsync.ui.viewmodels.CinderboxMigrationViewModel
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
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsSectionGap() {
    Spacer(Modifier.height(16.dp))
    AppDivider()
    Spacer(Modifier.height(16.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onLogout: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val migrationViewModel: CinderboxMigrationViewModel = koinViewModel()
    val migrationState by migrationViewModel.state.collectAsStateWithLifecycle()

    var showLogout by rememberSaveable { mutableStateOf(false) }
    if (showLogout) {
        AppDialog(
            onDismissRequest = { showLogout = false },
            title = stringResource(R.string.settings_logout),
            text = stringResource(R.string.settings_logout_confirm),
            confirmButton = {
                AppButton(onClick = {
                    showLogout = false
                    viewModel.logout()
                    onLogout()
                }, variant = ButtonVariant.Danger) {
                    Text(stringResource(R.string.settings_logout))
                }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { showLogout = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
    val context = LocalContext.current

    val requestStoragePermission = rememberStoragePermissionRequest { viewModel.load() }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onSafDirectorySelected(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    if (migrationState.showDialog) {
        CinderboxMigrationDialog(
            state = migrationState,
            onMove = migrationViewModel::migrate,
            onLater = migrationViewModel::remindLater,
            onDontAsk = migrationViewModel::dontAskAgain,
            onDismissResult = migrationViewModel::dismissResult
        )
    }

    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary
    )
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary
    )
    val filterChipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.onSurface,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    if (state.showStoragePermissionPrompt) {
        StoragePermissionDialog(
            onPermissionResult = { viewModel.onStoragePermissionResult() },
            onDismiss = { viewModel.dismissStoragePermissionPrompt() }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Game mode
            SectionHeader(stringResource(R.string.settings_game_mode_title))
            Spacer(Modifier.height(8.dp))
            AppCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_cinderbox_mode),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (state.cinderboxMode) {
                                stringResource(R.string.settings_cinderbox_mode_desc)
                            } else {
                                stringResource(R.string.settings_cinderbox_mode_off_desc)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.cinderboxMode,
                        onCheckedChange = { viewModel.toggleCinderboxMode(it) },
                        colors = switchColors
                    )
                }
            }

            if (state.cinderboxMode && state.hasLegacyCinderboxFiles) {
                Spacer(Modifier.height(12.dp))
                AppCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.cinderbox_migrate_settings_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.cinderbox_migrate_settings_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        AppButton(
                            onClick = migrationViewModel::showPrompt,
                            variant = ButtonVariant.Secondary,
                            enabled = !migrationState.isMigrating
                        ) {
                            Text(
                                if (migrationState.isMigrating) {
                                    stringResource(R.string.cinderbox_migrate_running)
                                } else {
                                    stringResource(R.string.cinderbox_migrate_move)
                                }
                            )
                        }
                    }
                }
            }

            SettingsSectionGap()

            // File access mode
            SectionHeader(stringResource(R.string.settings_file_access))
            Spacer(Modifier.height(8.dp))
            AppCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    val currentModeDisplay = strategyDisplayName(state.fileAccessMode)
                    val availableModesDisplay = state.availableModes.map { strategyDisplayName(it) }
                    Text(
                        stringResource(R.string.settings_file_access_current, currentModeDisplay),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(
                            R.string.settings_file_access_available,
                            availableModesDisplay.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.settings_file_access_choose),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = state.preferredStrategy == null,
                            onClick = { viewModel.setFileAccessMode(null) },
                            label = { Text(stringResource(R.string.settings_file_access_auto)) },
                            colors = filterChipColors,
                            shape = MaterialTheme.shapes.large
                        )
                        state.availableModes.forEach { mode ->
                            FilterChip(
                                selected = state.preferredStrategy.equals(mode, ignoreCase = true),
                                onClick = { viewModel.setFileAccessMode(mode) },
                                label = { Text(strategyDisplayName(mode)) },
                                colors = filterChipColors,
                                shape = MaterialTheme.shapes.large
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
                    onBindService = { viewModel.bindShizukuService() }
                )
            }

            // All Files Access
            if (state.allFilesEligible && !state.hasRoot) {
                Spacer(Modifier.height(12.dp))
                AllFilesAccessSection(
                    permissionGranted = state.allFilesPermissionGranted,
                    accessWorking = state.allFilesAccessWorking,
                    onGrantPermission = requestStoragePermission
                )
            }

            // SAF
            if (state.safEligible && !state.hasRoot) {
                Spacer(Modifier.height(12.dp))
                SAFAccessSection(
                    configured = state.safConfigured,
                    isStaging = state.safIsStaging,
                    onSelectDirectory = { safLauncher.launch(null) },
                    onRevoke = { viewModel.clearSafAccess() }
                )
            }

            SettingsSectionGap()

            // Backups
            SectionHeader(stringResource(R.string.settings_backups_title))
            Spacer(Modifier.height(8.dp))
            AppCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.settings_backups_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    var sliderValue by remember(state.maxBackups) {
                        mutableFloatStateOf(state.maxBackups.toFloat())
                    }
                    Text(
                        stringResource(R.string.settings_backups_count, sliderValue.roundToInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            viewModel.setMaxBackups(sliderValue.roundToInt())
                        },
                        valueRange =
                        SaveBackupManager.MIN_MAX_BACKUPS.toFloat()..SaveBackupManager.MAX_MAX_BACKUPS.toFloat(),
                        steps = SaveBackupManager.MAX_MAX_BACKUPS - SaveBackupManager.MIN_MAX_BACKUPS - 1,
                        colors = sliderColors
                    )
                }
            }

            SettingsSectionGap()

            // Auto-sync
            SectionHeader(stringResource(R.string.settings_auto_sync_title))
            Spacer(Modifier.height(8.dp))
            AppCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (state.autoSyncAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_auto_sync),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    stringResource(R.string.settings_auto_sync_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.autoSyncEnabled,
                                onCheckedChange = { viewModel.toggleAutoSync(it) },
                                colors = switchColors
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.settings_auto_sync_root_only),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsSectionGap()

            // Mods - Nexus API Key
            NexusApiKeySection(
                hasKey = state.hasNexusApiKey,
                maskedKey = state.nexusApiKeyMasked,
                isValidating = state.isValidatingApiKey,
                errorRes = state.apiKeyErrorRes,
                onSaveKey = { viewModel.validateAndSaveApiKey(it) },
                onRemoveKey = { viewModel.removeNexusApiKey() },
                onClearError = { viewModel.clearApiKeyError() }
            )

            SettingsSectionGap()

            // Account
            SectionHeader(stringResource(R.string.settings_steam_account))
            Spacer(Modifier.height(8.dp))
            AppButton(
                onClick = { showLogout = true },
                variant = ButtonVariant.Danger
            ) {
                Text(stringResource(R.string.settings_logout))
            }

            SettingsSectionGap()

            // About
            SectionHeader(stringResource(R.string.settings_about_title))
            Spacer(Modifier.height(8.dp))
            AppCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.settings_about_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Cinderbox version
                    val cinderboxVer = state.installedCinderboxVersion
                    if (cinderboxVer != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_cinderbox_version, cinderboxVer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val latestCinderbox = state.latestCinderboxVersion
                            if (state.cinderboxUpdateAvailable && latestCinderbox != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.github_update_available_short, latestCinderbox),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // SMAPI version
                    val smapiVer = state.installedSmapiVersion
                    if (smapiVer != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_smapi_version, smapiVer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val latestSmapi = state.latestSmapiVersion
                            if (state.smapiUpdateAvailable && latestSmapi != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.github_update_available_short, latestSmapi),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_update_check_title),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(R.string.settings_update_check_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.updateCheckEnabled,
                            onCheckedChange = { viewModel.toggleUpdateCheck(it) },
                            colors = switchColors
                        )
                    }
                }
            }

            SettingsSectionGap()

            // Diagnostics
            SectionHeader(stringResource(R.string.settings_diagnostics_title))
            Spacer(Modifier.height(8.dp))
            AppCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.settings_share_logs_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    AppOutlinedButton(onClick = { AppLogger.shareLogs(context) }) {
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
    onRevoke: () -> Unit
) {
    AppCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.settings_saf_title),
                style = MaterialTheme.typography.bodyMedium
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
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
                AppOutlinedButton(onClick = onRevoke) {
                    Text(stringResource(R.string.settings_saf_revoke))
                }
            } else {
                Text(
                    stringResource(R.string.settings_saf_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_saf_staging_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                AppOutlinedButton(onClick = onSelectDirectory) {
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
    onBindService: () -> Unit
) {
    AppCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.settings_shizuku_title),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))

            when {
                !installed -> {
                    Text(
                        stringResource(R.string.settings_shizuku_not_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                !running -> {
                    Text(
                        stringResource(R.string.settings_shizuku_not_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                !permissionGranted -> {
                    Text(
                        stringResource(R.string.settings_shizuku_grant_prompt),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    AppOutlinedButton(onClick = onRequestPermission) {
                        Text(stringResource(R.string.settings_shizuku_grant_permission))
                    }
                }
                else -> {
                    Text(
                        stringResource(R.string.settings_shizuku_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    AppOutlinedButton(onClick = onBindService) {
                        Text(stringResource(R.string.settings_shizuku_connect))
                    }
                }
            }
        }
    }
}

@Composable
private fun NexusApiKeySection(
    hasKey: Boolean,
    maskedKey: String?,
    isValidating: Boolean,
    errorRes: Int?,
    onSaveKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
    onClearError: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var showChangeDialog by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }

    SectionHeader(stringResource(R.string.settings_mods_title))
    Spacer(Modifier.height(8.dp))
    AppCard {
        Column(modifier = Modifier.padding(12.dp)) {
            if (hasKey) {
                Text(
                    stringResource(R.string.settings_nexus_key_configured),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (maskedKey != null) {
                    Text(
                        maskedKey,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isValidating) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                errorRes?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        onClick = {
                            keyInput = ""
                            onClearError()
                            showChangeDialog = true
                        },
                        enabled = !isValidating
                    ) {
                        Text(stringResource(R.string.settings_nexus_key_change))
                    }
                    AppButton(
                        onClick = onRemoveKey,
                        variant = ButtonVariant.Danger,
                        enabled = !isValidating
                    ) {
                        Text(stringResource(R.string.settings_nexus_key_remove))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.mods_api_key_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        onClearError()
                    },
                    label = { Text(stringResource(R.string.mods_api_key_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorRes != null,
                    supportingText = if (errorRes != null) {
                        { Text(stringResource(errorRes)) }
                    } else {
                        null
                    }
                )
                Spacer(Modifier.height(8.dp))
                AppButton(
                    onClick = { onSaveKey(keyInput) },
                    variant = ButtonVariant.Primary,
                    enabled = keyInput.isNotBlank() && !isValidating
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.mods_api_key_save))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.mods_api_key_get),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://www.nexusmods.com/users/myaccount?tab=api+access")
                    }
                )
            }
        }
    }

    if (showChangeDialog) {
        AlertDialog(
            onDismissRequest = { showChangeDialog = false },
            title = { Text(stringResource(R.string.settings_nexus_key_change_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            onClearError()
                        },
                        label = { Text(stringResource(R.string.mods_api_key_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorRes != null,
                        supportingText = if (errorRes != null) {
                            { Text(stringResource(errorRes)) }
                        } else {
                            null
                        }
                    )
                }
            },
            confirmButton = {
                AppButton(
                    onClick = {
                        onSaveKey(keyInput)
                        showChangeDialog = false
                    },
                    variant = ButtonVariant.Primary,
                    enabled = keyInput.isNotBlank() && !isValidating
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.mods_api_key_save))
                    }
                }
            },
            dismissButton = {
                AppButton(onClick = { showChangeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AllFilesAccessSection(permissionGranted: Boolean, accessWorking: Boolean, onGrantPermission: () -> Unit) {
    AppCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.settings_all_files_title),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))

            when {
                accessWorking -> {
                    Text(
                        stringResource(R.string.settings_all_files_granted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                permissionGranted -> {
                    Text(
                        stringResource(R.string.settings_all_files_no_access),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Text(
                        stringResource(R.string.settings_all_files_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    AppOutlinedButton(onClick = onGrantPermission) {
                        Text(stringResource(R.string.settings_all_files_grant))
                    }
                }
            }
        }
    }
}
