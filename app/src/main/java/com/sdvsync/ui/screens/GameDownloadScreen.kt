package com.sdvsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sdvsync.R
import com.sdvsync.download.CinderboxDownloadProgress
import com.sdvsync.download.DownloadState
import com.sdvsync.download.M24DepotProgressPolicy
import com.sdvsync.download.SmapiSetupProgress
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.CinderboxWizardDialog
import com.sdvsync.ui.components.PixelDivider
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.PixelProgressBar
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.components.installCinderboxApk
import com.sdvsync.ui.formatBytes
import com.sdvsync.local.M23InstallationDiscovery
import com.sdvsync.local.M23CinderboxFirstPolicy
import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.ui.policy.DeveloperFeaturePolicy
import com.sdvsync.ui.policy.DownloadPresentation
import java.io.File
import com.sdvsync.ui.viewmodels.GameDownloadViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun formatDate(epochSeconds: Long): String {
    if (epochSeconds == 0L) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochSeconds * 1000))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameDownloadScreen(onBack: () -> Unit, onOpenInstallationHealth: () -> Unit,
                       viewModel: GameDownloadViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val wizardState by viewModel.wizardState.collectAsState()
    val directoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.chooseAndroidFolder(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.loadBranches()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshInstalledVersions()
        viewModel.refreshSelectedDirectoryHealth()
        onPauseOrDispose {}
    }

    // Cinderbox setup wizard dialog
    CinderboxWizardDialog(
        wizardState = wizardState,
        cinderboxProgress = state.cinderboxDownloadProgress,
        onChooseCinderbox = viewModel::onWizardChooseCinderbox,
        onChooseOther = viewModel::onWizardChooseOther,
        onRefreshPermissions = viewModel::refreshPermissions,
        onApkDownloadComplete = viewModel::onApkDownloadComplete,
        onApkInstalled = viewModel::onApkInstalled,
        onCheckDirectory = viewModel::checkCinderboxDirectory,
        onSkipVerification = viewModel::skipVerification,
        onStartDownload = viewModel::onWizardStartDownload,
        onDismiss = viewModel::dismissWizard
    )

    val filterChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.download_title),
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
            // App info card
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.download_app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.download_app_id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // Main Cinderbox is the game owner. This card only reads its known
            // installation and navigates to read-only Installation Health.
            StardewCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Main app · Cinderbox", style = MaterialTheme.typography.titleMedium)
                    Text("Game: ${state.primaryGameStatus.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("SMAPI: ${state.primarySmapiStatus.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Cinderbox game location: ${CinderboxPaths.GAME_FILES_DIR}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("An imported game can be used through Cinderbox without " +
                        "downloading another copy. Detection is read-only; versions " +
                        "and Steam integrity are not verified.",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    StardewOutlinedButton(onClick = onOpenInstallationHealth) {
                        Text("Inspect existing Cinderbox installation")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            // Version / Branch selection
            SectionHeader(stringResource(R.string.download_section_version))
            Spacer(Modifier.height(8.dp))

            if (state.isLoadingBranches) {
                StardewCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelLoadingSpinner()
                    }
                }
            } else if (state.branches.isNotEmpty()) {
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            state.branches.forEach { branch ->
                                FilterChip(
                                    selected = state.selectedBranch == branch.name,
                                    onClick = { viewModel.selectBranch(branch.name) },
                                    label = { Text(branch.name) },
                                    colors = filterChipColors
                                )
                            }
                        }

                        // Show selected branch info
                        val selectedBranch = state.branches.find { it.name == state.selectedBranch }
                        if (selectedBranch != null) {
                            Spacer(Modifier.height(8.dp))
                            val branchDetail = if (selectedBranch.timeUpdated > 0) {
                                stringResource(
                                    R.string.download_branch_build_date,
                                    selectedBranch.buildId,
                                    formatDate(selectedBranch.timeUpdated)
                                )
                            } else {
                                stringResource(R.string.download_branch_build, selectedBranch.buildId)
                            }
                            Text(
                                branchDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Password field for password-protected branches
                            if (selectedBranch.passwordRequired) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = state.branchPassword,
                                    onValueChange = { viewModel.updateBranchPassword(it) },
                                    label = { Text(stringResource(R.string.download_branch_password)) },
                                    singleLine = true,
                                    shape = RectangleShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Always provide a recovery action, including when cached versions remain
            // on screen but the Steam session has subsequently disconnected.
            if (!state.isLoadingBranches &&
                (state.branchLoadError != null || (!state.steamLoggedIn && state.branches.isNotEmpty()))
            ) {
                Spacer(Modifier.height(12.dp))
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            state.branchLoadError ?: stringResource(R.string.download_error_steam_offline),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        StardewOutlinedButton(
                            onClick = viewModel::retryBranchLoad,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    if (state.branchLoadNeedsReconnect || !state.steamLoggedIn) {
                                        R.string.download_retry_steam
                                    } else {
                                        R.string.download_retry_branches
                                    }
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            // Platform selection
            SectionHeader(stringResource(R.string.download_section_platform))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "windows" to stringResource(R.string.download_platform_windows),
                            "macos" to stringResource(R.string.download_platform_macos),
                            "linux" to stringResource(R.string.download_platform_linux)
                        ).forEach { (os, label) ->
                            FilterChip(
                                selected = state.selectedOs == os,
                                onClick = { viewModel.selectOs(os) },
                                label = { Text(label) },
                                colors = filterChipColors
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current)) {
                // Debug-only folder override; M23/M24 safety gates still apply.
            SectionHeader("Optional separate download location")
            Spacer(Modifier.height(8.dp))

            var editingPath by remember { mutableStateOf(false) }

            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    StardewButton(
                        onClick = { directoryPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Choose folder with Android") }
                    Spacer(Modifier.height(8.dp))
                    if (editingPath) {
                        OutlinedTextField(
                            value = state.installDirectory,
                            onValueChange = { viewModel.updateInstallDirectory(it) },
                            label = { Text(stringResource(R.string.download_install_path)) },
                            singleLine = true,
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        StardewOutlinedButton(onClick = { editingPath = false }) {
                            Text(stringResource(R.string.download_done))
                        }
                    } else {
                        Text(
                            state.installDirectory,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        StardewOutlinedButton(onClick = { editingPath = true }) {
                            Text("Enter path manually")
                        }
                    }
                }
            }

            } else {
                StardewCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("Game destination · Cinderbox",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Game files are downloaded only to Cinderbox's GameFiles folder. " +
                            "Existing installations are protected until safe updates are supported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Alternative path inspector belongs to unlocked debug mode only.
            if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current) &&
                state.installDirectory != CinderboxPaths.GAME_FILES_DIR) {
            state.selectedInstallHealth?.let { health ->
                Text("Selected folder: ${health.health.name.replace('_', ' ')}",
                    style = MaterialTheme.typography.titleSmall)
                Text(health.details, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }
            }
            Text(state.storageDetail, style = MaterialTheme.typography.bodySmall)
            if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current) &&
                state.installDirectory != CinderboxPaths.GAME_FILES_DIR &&
                state.destinationCode != M23CinderboxFirstPolicy.Code.EMPTY_SEPARATE_DESTINATION) {
                Text(DownloadPresentation.destinationStatus(state.destinationCode),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            // M24: mandatory local verification (not independent Steam manifest proof).
            StardewCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "File integrity verification — required",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Always checked before publishing this download. " +
                                "This does not provide independent signed proof of the Steam files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Do not display a disabled switch that looks switched off.
                    Spacer(Modifier.width(12.dp))
                    Text("Required", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Error
            if (state.error != null) {
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Download button / progress
            val downloadState = state.downloadProgress.state

            when (downloadState) {
                DownloadState.IDLE -> {
                    StardewButton(
                        onClick = { viewModel.onDownloadButtonClicked() },
                        variant = StardewButtonVariant.Gold,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.branches.isNotEmpty() && state.steamLoggedIn && !state.isLoadingBranches
                    ) {
                        Text(stringResource(R.string.download_button))
                    }
                }

                DownloadState.PREPARING -> {
                    StardewCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PixelLoadingSpinner()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.download_preparing),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                DownloadState.DOWNLOADING -> {
                    DownloadProgressSection(
                        currentFile = state.downloadProgress.currentFile,
                        downloadedBytes = state.downloadProgress.downloadedBytes,
                        bytesPerSecond = state.downloadProgress.bytesPerSecond,
                        currentDepotId = state.downloadProgress.currentDepotId,
                        currentDepotPercent = state.downloadProgress.currentDepotPercent,
                        depotPhase = state.downloadProgress.depotPhase,
                        filesDownloaded = state.downloadProgress.filesDownloaded,
                        onCancel = { viewModel.cancelDownload() }
                    )
                }

                DownloadState.VERIFYING -> {
                    StardewCard {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Verifying download", style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(state.downloadProgress.verificationPhase.ifBlank { "Preparing integrity checks" },
                                style = MaterialTheme.typography.bodyMedium)
                            if (state.downloadProgress.totalFilesToVerify > 0) {
                                Spacer(Modifier.height(8.dp))
                                Text("This step: ${state.downloadProgress.verifiedFiles} of " +
                                    "${state.downloadProgress.totalFilesToVerify} files",
                                    style = MaterialTheme.typography.bodySmall)
                                PixelProgressBar(progress = state.downloadProgress.overallPercent)
                                Text("${(state.downloadProgress.overallPercent * 100).toInt()}% of this step only",
                                    style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Checking… this step has no percentage.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Other checks and final publication may follow; " +
                                "100% of a step does not mean the download is complete.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                DownloadState.COPYING -> {
                    CopyProgressSection(
                        currentFile = state.downloadProgress.currentFile,
                        copiedFiles = state.downloadProgress.copiedFiles,
                        totalFiles = state.downloadProgress.totalFilesToCopy,
                        overallPercent = state.downloadProgress.overallPercent
                    )
                }

                DownloadState.COMPLETED -> {
                    val progress = state.downloadProgress
                    val copyFailed = progress.copyCompleted && progress.copyErrors.isNotEmpty()
                    val copySucceeded = progress.copyCompleted && progress.copyErrors.isEmpty()
                    val showCopyButton = state.isCinderboxSetup && !copySucceeded &&
                        DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current) &&
                        state.installDirectory != CinderboxPaths.GAME_FILES_DIR

                    StardewCard {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.download_complete),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (progress.integrityScope == "LOCAL_SHA256_ONLY_MANIFEST_UNATTESTED") {
                                Text("Local SHA-256 consistency passed. Independent Steam depot " +
                                    "manifest integrity and ownership are not yet attested.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.download_complete_desc, state.installDirectory),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Verification results
                            if (progress.totalFilesToVerify > 0) {
                                Spacer(Modifier.height(8.dp))
                                if (progress.verificationPassed) {
                                    Text(
                                        stringResource(R.string.download_verify_passed, progress.verifiedFiles),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        stringResource(
                                            R.string.download_verify_failed,
                                            progress.verificationErrors.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    progress.verificationErrors.forEach { errorFile ->
                                        Text(
                                            errorFile,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            // Copy results
                            if (copySucceeded) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.cinderbox_copy_success, progress.copiedFiles),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (copyFailed) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.cinderbox_copy_failed, progress.copyErrors.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                progress.copyErrors.forEach { errorFile ->
                                    Text(
                                        errorFile,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (showCopyButton) {
                        StardewButton(
                            onClick = { viewModel.copyCinderbox() },
                            variant = if (copyFailed) StardewButtonVariant.Primary else StardewButtonVariant.Action,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    if (copyFailed) R.string.cinderbox_copy_retry else R.string.cinderbox_copy_button
                                )
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    StardewOutlinedButton(
                        onClick = { viewModel.resetDownload() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.download_again))
                    }
                }

                DownloadState.ERROR -> {
                    StardewCard {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.download_error),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.downloadProgress.errorMessage ?: stringResource(R.string.download_error_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    StardewButton(
                        onClick = {
                            viewModel.resetDownload()
                            viewModel.startDownload()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.hasRecoverableDownload) "Resume interrupted download"
                            else stringResource(R.string.action_retry))
                    }
                    if (state.hasRecoverableDownload) {
                        Spacer(Modifier.height(8.dp))
                        StardewOutlinedButton(
                            onClick = { viewModel.discardInterruptedDownload() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Discard recovery record — Keep game files") }
                    }
                }

                DownloadState.CANCELLED -> {
                    StardewCard {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.download_cancelled),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    StardewButton(
                        onClick = {
                            viewModel.resetDownload()
                            viewModel.startDownload()
                        },
                        variant = StardewButtonVariant.Gold,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.hasRecoverableDownload) "Resume interrupted download"
                            else stringResource(R.string.download_button))
                    }
                    if (state.hasRecoverableDownload) {
                        Spacer(Modifier.height(8.dp))
                        StardewOutlinedButton(
                            onClick = { viewModel.discardInterruptedDownload() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Discard recovery record — Keep game files") }
                    }
                }
            }

            // SMAPI Setup section (always visible)
            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            SmapiSetupSection(
                smapiProgress = state.smapiSetupProgress,
                isCopying = downloadState == DownloadState.COPYING,
                onExtract = { viewModel.extractSmapi() },
                onReset = { viewModel.resetSmapiSetup() },
                updateAvailable = state.smapiUpdateAvailable,
                latestVersion = state.latestSmapiVersion,
                installedVersion = state.installedSmapiVersion,
                installedStatus = state.primarySmapiStatus
            )

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            CinderboxSection(
                cinderboxProgress = state.cinderboxDownloadProgress,
                onDownload = { viewModel.downloadCinderbox() },
                onReset = { viewModel.resetCinderboxDownload() },
                updateAvailable = state.cinderboxUpdateAvailable,
                latestVersion = state.latestCinderboxVersion,
                installedVersion = state.installedCinderboxVersion,
                packageInstalled = state.cinderboxPackageInstalled
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SmapiSetupSection(
    smapiProgress: SmapiSetupProgress,
    isCopying: Boolean,
    onExtract: () -> Unit,
    onReset: () -> Unit,
    updateAvailable: Boolean = false,
    latestVersion: String? = null,
    installedVersion: String? = null,
    installedStatus: M23InstallationDiscovery.Status = M23InstallationDiscovery.Status.MISSING
) {
    var confirmSmapiReplacement by remember { mutableStateOf(false) }
    if (confirmSmapiReplacement) {
        AlertDialog(
            onDismissRequest = { confirmSmapiReplacement = false },
            title = { Text("Replace existing SMAPI?") },
            text = { Text("Existing SMAPI files were detected. Updating could replace them. Back up your SMAPI setup first; this action does not touch Steam game files.") },
            confirmButton = { TextButton(onClick = {
                confirmSmapiReplacement = false
                onExtract()
            }) { Text("Update SMAPI") } },
            dismissButton = { TextButton(onClick = {
                confirmSmapiReplacement = false
            }) { Text("Cancel") } }
        )
    }
    SectionHeader(stringResource(R.string.smapi_setup_title))
    Spacer(Modifier.height(8.dp))

    when {
        smapiProgress.isDownloading -> {
            // Download phase
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.smapi_downloading),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    Text(
                        stringResource(
                            R.string.smapi_download_progress,
                            formatBytes(smapiProgress.downloadedBytes),
                            if (smapiProgress.totalDownloadBytes > 0) {
                                formatBytes(smapiProgress.totalDownloadBytes)
                            } else {
                                "?"
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PixelProgressBar(progress = smapiProgress.downloadPercent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(smapiProgress.downloadPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        smapiProgress.isRunning -> {
            // Running state
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.smapi_extracting),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (smapiProgress.currentFile.isNotEmpty()) {
                        Text(
                            smapiProgress.currentFile,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        stringResource(
                            R.string.smapi_extract_progress,
                            smapiProgress.extractedFiles,
                            smapiProgress.totalFiles
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))
                    PixelProgressBar(progress = smapiProgress.percent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(smapiProgress.percent * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        smapiProgress.errorMessage != null && !smapiProgress.completed -> {
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("SMAPI setup could not start: ${smapiProgress.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            StardewOutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss error")
            }
        }

        smapiProgress.completed -> {
            // Completed state
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (smapiProgress.errorMessage != null) {
                        Text(
                            stringResource(R.string.smapi_extract_error, smapiProgress.errorMessage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            stringResource(R.string.smapi_extract_success),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.smapi_extract_success_desc, smapiProgress.extractedFiles),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            StardewOutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.smapi_extract_again))
            }
        }

        else -> {
            // Idle state; detected SMAPI is never automatically overwritten.
            val alreadyPresent = installedStatus == M23InstallationDiscovery.Status.CANDIDATE_UNVERIFIED
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.smapi_setup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (alreadyPresent) {
                        Spacer(Modifier.height(4.dp))
                        Text("Existing SMAPI detected (version/compatibility not authenticated).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (installedVersion != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.github_installed_version, installedVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (updateAvailable && latestVersion != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.github_update_available, latestVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!alreadyPresent || (updateAvailable && latestVersion != null)) StardewButton(
                onClick = { if (alreadyPresent) confirmSmapiReplacement = true else onExtract() },
                variant = StardewButtonVariant.Action,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCopying
            ) {
                Text(
                    if (updateAvailable) {
                        stringResource(R.string.smapi_update_button)
                    } else if (installedVersion != null) {
                        stringResource(R.string.smapi_extract_button)
                    } else {
                        stringResource(R.string.smapi_download_extract_button)
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadProgressSection(
    currentFile: String,
    downloadedBytes: Long,
    bytesPerSecond: Long,
    currentDepotId: Int?,
    currentDepotPercent: Float,
    depotPhase: String,
    filesDownloaded: Int,
    onCancel: () -> Unit
) {
    StardewCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.download_downloading),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(Modifier.height(12.dp))

            if (currentFile.isNotEmpty()) {
                Text(
                    currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                "Received: ${formatBytes(downloadedBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (bytesPerSecond > 0) "Last reported transfer rate: ${formatBytes(bytesPerSecond)}/s"
                else "Transfer rate: waiting for next data event",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Completed files: $filesDownloaded  •  Total game size: unknown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                depotPhase.ifBlank { "Contacting Steam CDN; waiting for first depot chunk…" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (currentDepotId != null) {
                val depotPercentage = M24DepotProgressPolicy.displayedDepotPercent(
                    currentDepotPercent, downloadedBytes)
                if (depotPercentage != null) {
                    Text(
                        "Depot $currentDepotId: $depotPercentage% of this depot (not the game)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    PixelProgressBar(progress = currentDepotPercent)
                } else {
                    Text(
                        "Receiving depot $currentDepotId • Depot percentage not available yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("No reliable overall download percentage or ETA is available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(12.dp))

            StardewOutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun CinderboxSection(
    cinderboxProgress: CinderboxDownloadProgress,
    onDownload: () -> Unit,
    onReset: () -> Unit,
    updateAvailable: Boolean = false,
    latestVersion: String? = null,
    installedVersion: String? = null,
    packageInstalled: Boolean = false
) {
    val context = LocalContext.current

    SectionHeader(stringResource(R.string.cinderbox_section_title))
    Spacer(Modifier.height(8.dp))

    when {
        cinderboxProgress.completed && cinderboxProgress.errorMessage == null -> {
            // Download complete — offer install
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.cinderbox_download_complete),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.cinderbox_download_complete_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StardewButton(
                onClick = {
                    cinderboxProgress.apkFile?.let { installCinderboxApk(context, it) }
                },
                variant = StardewButtonVariant.Action,
                modifier = Modifier.fillMaxWidth(),
                enabled = cinderboxProgress.apkFile != null
            ) {
                Text(stringResource(R.string.cinderbox_install_button))
            }
            Spacer(Modifier.height(8.dp))
            StardewOutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cinderbox_download_again))
            }
        }

        cinderboxProgress.isDownloading -> {
            // Downloading
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.cinderbox_downloading),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    Text(
                        stringResource(
                            R.string.cinderbox_download_progress,
                            formatBytes(cinderboxProgress.downloadedBytes),
                            if (cinderboxProgress.totalBytes > 0) {
                                formatBytes(cinderboxProgress.totalBytes)
                            } else {
                                "?"
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PixelProgressBar(progress = cinderboxProgress.percent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(cinderboxProgress.percent * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        cinderboxProgress.errorMessage != null -> {
            // Error
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.cinderbox_download_error, cinderboxProgress.errorMessage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StardewButton(
                onClick = {
                    onReset()
                    onDownload()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cinderbox_download_button))
            }
        }

        else -> {
            // Package presence comes from Android, not just the last downloaded version.
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.cinderbox_section_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (packageInstalled) {
                        Spacer(Modifier.height(4.dp))
                        Text("Cinderbox is already installed on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (installedVersion != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.github_installed_version, installedVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (updateAvailable && latestVersion != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.github_update_available, latestVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!packageInstalled || (updateAvailable && latestVersion != null)) StardewButton(
                onClick = onDownload,
                variant = StardewButtonVariant.Action,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (updateAvailable) {
                        stringResource(R.string.cinderbox_update_button)
                    } else {
                        stringResource(R.string.cinderbox_download_button)
                    }
                )
            }
        }
    }
}

@Composable
private fun CopyProgressSection(currentFile: String, copiedFiles: Int, totalFiles: Int, overallPercent: Float) {
    StardewCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.cinderbox_copying),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(Modifier.height(12.dp))

            if (currentFile.isNotEmpty()) {
                Text(
                    currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                stringResource(R.string.cinderbox_copy_progress, copiedFiles, totalFiles),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            PixelProgressBar(progress = overallPercent)

            Spacer(Modifier.height(4.dp))
            Text(
                "${(overallPercent * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
