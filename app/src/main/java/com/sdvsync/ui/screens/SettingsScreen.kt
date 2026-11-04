package com.sdvsync.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.sdvsync.BuildConfig
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.ui.policy.DeveloperFeaturePolicy
import com.sdvsync.steam.SteamBackgroundService
import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.CinderboxMigrationDialog
import com.sdvsync.ui.components.PixelDivider
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.components.R45SectionIntro
import com.sdvsync.ui.components.R45AreaGuide
import com.sdvsync.ui.policy.R45GuideArea
import com.sdvsync.ui.components.StoragePermissionDialog
import com.sdvsync.ui.theme.AppearanceMode
import com.sdvsync.ui.theme.AppearancePreferences
import com.sdvsync.ui.theme.AppearanceSettings
import com.sdvsync.ui.theme.AppearanceStyle
import kotlinx.coroutines.launch
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
    PixelDivider()
    Spacer(Modifier.height(16.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    preLogin: Boolean = false,
    onOpenDeveloperDiagnostics: () -> Unit = {},
    onOpenInstallationHealth: () -> Unit = {},
    onOpenOperationCenter: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val migrationViewModel: CinderboxMigrationViewModel = koinViewModel()
    val migrationState by migrationViewModel.state.collectAsState()

    val context = LocalContext.current
    val appearanceStore = remember(context) { AppearancePreferences(context.applicationContext) }
    val appearance by appearanceStore.settings.collectAsState(initial = AppearanceSettings())
    val appearanceScope = rememberCoroutineScope()
    var steamBackgroundEnabled by remember {
        mutableStateOf(SteamBackgroundService.isEnabled(context))
    }
    var confirmRoot by remember { mutableStateOf(false) }
    val developerPrefs = remember(context) { context.getSharedPreferences("hidden_developer_features", android.content.Context.MODE_PRIVATE) }
    var developerUnlocked by remember { mutableStateOf(developerPrefs.getBoolean("steam_diagnostics_enabled", false)) }
    var developerTaps by rememberSaveable { mutableIntStateOf(0) }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.load()
    }

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

    if (confirmRoot) {
        AlertDialog(
            onDismissRequest = { confirmRoot = false },
            title = { Text("Enable Root file access?") },
            text = { Text("Root can modify Stardew saves as superuser. Prefer standard storage or Shizuku when possible. Continue only if needed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRoot = false
                    viewModel.setFileAccessMode("Root")
                }) { Text("Enable Root") }
            },
            dismissButton = { TextButton(onClick = { confirmRoot = false }) { Text("Cancel") } }
        )
    }

    if (state.showStoragePermissionPrompt) {
        StoragePermissionDialog(
            onPermissionResult = { viewModel.onStoragePermissionResult() },
            onDismiss = { viewModel.dismissStoragePermissionPrompt() }
        )
    }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    PixelIconButton(
                        pixelData = ArrowLeftData,
                        onClick = onBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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
            if (!preLogin) {
                R45SectionIntro(
                    "More",
                    "Appearance, permissions and account settings. Sensitive actions retain their confirmations."
                )
            }
            if (!preLogin) {
                R45AreaGuide(R45GuideArea.MORE)
            }
            // Offline, app-private visual preferences. No Steam or storage permissions required.
            SectionHeader(stringResource(R.string.appearance_section))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.appearance_style_label), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.appearance_style_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = appearance.style == AppearanceStyle.CLASSIC,
                            onClick = { appearanceScope.launch { appearanceStore.setStyle(AppearanceStyle.CLASSIC) } },
                            label = { Text(stringResource(R.string.appearance_classic)) },
                            colors = filterChipColors
                        )
                        FilterChip(
                            selected = appearance.style == AppearanceStyle.EARTHY,
                            onClick = { appearanceScope.launch { appearanceStore.setStyle(AppearanceStyle.EARTHY) } },
                            label = { Text(stringResource(R.string.appearance_earthy)) },
                            colors = filterChipColors
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.appearance_brightness_label), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppearanceMode.entries.forEach { mode ->
                            val text = when (mode) {
                                AppearanceMode.SYSTEM -> stringResource(R.string.appearance_system)
                                AppearanceMode.LIGHT -> stringResource(R.string.appearance_light)
                                AppearanceMode.DARK -> stringResource(R.string.appearance_dark)
                            }
                            FilterChip(
                                selected = appearance.mode == mode,
                                onClick = { appearanceScope.launch { appearanceStore.setMode(mode) } },
                                label = { Text(text) },
                                colors = filterChipColors
                            )
                        }
                    }
                }
            }

            SettingsSectionGap()

            // Game mode
            SectionHeader(stringResource(R.string.settings_game_mode_title))
            Spacer(Modifier.height(8.dp))
            StardewCard {
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
                StardewCard {
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
                        StardewButton(
                            onClick = migrationViewModel::showPrompt,
                            variant = StardewButtonVariant.Gold,
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
            StardewCard {
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
                            shape = RectangleShape
                        )
                        // Root is never auto-probed. Selecting this chip is explicit opt-in.
                        FilterChip(
                            selected = state.preferredStrategy.equals("root", ignoreCase = true),
                            onClick = { confirmRoot = true },
                            label = { Text("Root (opt-in)") },
                            colors = filterChipColors,
                            shape = RectangleShape
                        )
                        state.availableModes.filterNot { it.equals("root", ignoreCase = true) }.forEach { mode ->
                            FilterChip(
                                selected = state.preferredStrategy.equals(mode, ignoreCase = true),
                                onClick = { viewModel.setFileAccessMode(mode) },
                                label = { Text(strategyDisplayName(mode)) },
                                colors = filterChipColors,
                                shape = RectangleShape
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Root is optional and grants elevated filesystem access. Select it only if needed; " +
                            "the app will never enable it automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Shizuku status
            Spacer(Modifier.height(12.dp))
            ShizukuStatusSection(
                installed = state.shizukuInstalled,
                running = state.shizukuRunning,
                permissionGranted = state.shizukuPermissionGranted,
                onRequestPermission = { viewModel.requestShizukuPermission() },
                onBindService = { viewModel.bindShizukuService() }
            )

            // All Files Access
            if (state.allFilesEligible) {
                Spacer(Modifier.height(12.dp))
                AllFilesAccessSection(
                    permissionGranted = state.allFilesPermissionGranted,
                    accessWorking = state.allFilesAccessWorking,
                    onGrantPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                            allFilesLauncher.launch(intent)
                        }
                    }
                )
            }

            // SAF
            if (state.safEligible) {
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
            StardewCard {
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
            if (!preLogin && state.isLoggedIn) {
                SectionHeader(stringResource(R.string.settings_auto_sync_title))
                Spacer(Modifier.height(8.dp))
                StardewCard {
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

            }

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
            if (!preLogin && state.isLoggedIn) {
                SectionHeader(stringResource(R.string.settings_steam_account))
                Spacer(Modifier.height(8.dp))
                StardewButton(
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    },
                    variant = StardewButtonVariant.Danger
                ) {
                    Text(stringResource(R.string.settings_logout))
                }

                SettingsSectionGap()

            }

            // About
            SectionHeader(stringResource(R.string.settings_about_title))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable {
                            if (!developerUnlocked) {
                                developerTaps++
                                if (developerTaps >= 7) {
                                    developerUnlocked = true
                                    developerPrefs.edit().putBoolean("steam_diagnostics_enabled", true).apply()
                                }
                            }
                        }
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

            // Local-first unified operation history; no Steam session required.
            SectionHeader("Operation center")
            Spacer(Modifier.height(8.dp))
            StardewOutlinedButton(onClick = onOpenOperationCenter, modifier = Modifier.fillMaxWidth()) {
                Text("View downloads, mod reviews, sync outcomes and recovery guidance")
            }
            SettingsSectionGap()

            // Local-first installation assessment is available without a Steam session.
            SectionHeader("Installation health")
            Spacer(Modifier.height(8.dp))
            StardewOutlinedButton(onClick = onOpenInstallationHealth, modifier = Modifier.fillMaxWidth()) {
                Text("Inspect game, Cinderbox, SMAPI and storage")
            }
            SettingsSectionGap()

            // Diagnostics
            if (!preLogin) {
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Keep Steam connected in background", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Shows an ongoing notification and may use more battery. Android battery restrictions can still interrupt the connection.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = steamBackgroundEnabled, onCheckedChange = { enabled ->
                                steamBackgroundEnabled = enabled
                                SteamBackgroundService.setEnabled(context, enabled)
                                if (enabled && state.isLoggedIn) SteamBackgroundService.start(context)
                            })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            SectionHeader(stringResource(R.string.settings_diagnostics_title))
            Spacer(Modifier.height(8.dp))
            if (developerUnlocked && DeveloperFeaturePolicy.canOpenDiagnostics(context)) {
                StardewOutlinedButton(onClick = onOpenDeveloperDiagnostics) {
                    Text("Developer: Steam diagnostics & 1h connection history")
                }
                Spacer(Modifier.height(12.dp))
            }
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.settings_share_logs_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onRevoke: () -> Unit
) {
    StardewCard {
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
                StardewOutlinedButton(onClick = onRevoke) {
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
    onBindService: () -> Unit
) {
    StardewCard {
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
                    StardewOutlinedButton(onClick = onRequestPermission) {
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
                    StardewOutlinedButton(onClick = onBindService) {
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
    StardewCard {
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
                    StardewOutlinedButton(
                        onClick = {
                            keyInput = ""
                            onClearError()
                            showChangeDialog = true
                        },
                        enabled = !isValidating
                    ) {
                        Text(stringResource(R.string.settings_nexus_key_change))
                    }
                    StardewButton(
                        onClick = onRemoveKey,
                        variant = StardewButtonVariant.Danger,
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
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorRes != null,
                    supportingText = if (errorRes != null) {
                        { Text(stringResource(errorRes)) }
                    } else {
                        null
                    }
                )
                Spacer(Modifier.height(8.dp))
                StardewButton(
                    onClick = { onSaveKey(keyInput) },
                    variant = StardewButtonVariant.Action,
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
                        shape = RectangleShape,
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
                StardewButton(
                    onClick = {
                        onSaveKey(keyInput)
                        showChangeDialog = false
                    },
                    variant = StardewButtonVariant.Action,
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
                StardewButton(onClick = { showChangeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AllFilesAccessSection(permissionGranted: Boolean, accessWorking: Boolean, onGrantPermission: () -> Unit) {
    StardewCard {
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
                    StardewOutlinedButton(onClick = onGrantPermission) {
                        Text(stringResource(R.string.settings_all_files_grant))
                    }
                }
            }
        }
    }
}
