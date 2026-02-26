package com.sdvsync.ui.screens

import android.content.Context
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sdvsync.R
import com.sdvsync.download.CinderboxDownloadProgress
import com.sdvsync.download.DownloadState
import com.sdvsync.download.SmapiSetupProgress
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.PixelDivider
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.PixelProgressBar
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.viewmodels.GameDownloadViewModel
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

private fun formatDate(epochSeconds: Long): String {
    if (epochSeconds == 0L) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochSeconds * 1000))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameDownloadScreen(
    onBack: () -> Unit,
    viewModel: GameDownloadViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadBranches()
    }

    val filterChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
            // App info card
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.download_app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.download_app_id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelLoadingSpinner()
                    }
                }
            } else if (state.branches.isNotEmpty()) {
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.branches.forEach { branch ->
                                FilterChip(
                                    selected = state.selectedBranch == branch.name,
                                    onClick = { viewModel.selectBranch(branch.name) },
                                    label = { Text(branch.name) },
                                    colors = filterChipColors,
                                )
                            }
                        }

                        // Show selected branch info
                        val selectedBranch = state.branches.find { it.name == state.selectedBranch }
                        if (selectedBranch != null) {
                            Spacer(Modifier.height(8.dp))
                            val branchDetail = if (selectedBranch.timeUpdated > 0) {
                                stringResource(R.string.download_branch_build_date, selectedBranch.buildId, formatDate(selectedBranch.timeUpdated))
                            } else {
                                stringResource(R.string.download_branch_build, selectedBranch.buildId)
                            }
                            Text(
                                branchDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
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
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            "windows" to stringResource(R.string.download_platform_windows),
                            "macos" to stringResource(R.string.download_platform_macos),
                            "linux" to stringResource(R.string.download_platform_linux),
                        ).forEach { (os, label) ->
                            FilterChip(
                                selected = state.selectedOs == os,
                                onClick = { viewModel.selectOs(os) },
                                label = { Text(label) },
                                colors = filterChipColors,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            // Download location
            SectionHeader(stringResource(R.string.download_section_location))
            Spacer(Modifier.height(8.dp))

            var editingPath by remember { mutableStateOf(false) }

            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (editingPath) {
                        OutlinedTextField(
                            value = state.installDirectory,
                            onValueChange = { viewModel.updateInstallDirectory(it) },
                            label = { Text(stringResource(R.string.download_install_path)) },
                            singleLine = true,
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth(),
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
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        StardewOutlinedButton(onClick = { editingPath = true }) {
                            Text(stringResource(R.string.download_change_folder))
                        }
                    }
                }
            }

            // Verification toggle
            StardewCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.download_verify_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.download_verify_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.verifyAfterDownload,
                        onCheckedChange = { viewModel.toggleVerification() },
                    )
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
                            color = MaterialTheme.colorScheme.error,
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
                        onClick = { viewModel.startDownload() },
                        variant = StardewButtonVariant.Gold,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.branches.isNotEmpty(),
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PixelLoadingSpinner()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.download_preparing),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                DownloadState.DOWNLOADING -> {
                    DownloadProgressSection(
                        currentFile = state.downloadProgress.currentFile,
                        downloadedBytes = state.downloadProgress.downloadedBytes,
                        totalBytes = state.downloadProgress.totalBytes,
                        bytesPerSecond = state.downloadProgress.bytesPerSecond,
                        overallPercent = state.downloadProgress.overallPercent,
                        onCancel = { viewModel.cancelDownload() },
                    )
                }

                DownloadState.VERIFYING -> {
                    StardewCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.download_verifying),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Spacer(Modifier.height(12.dp))

                            if (state.downloadProgress.currentFile.isNotEmpty()) {
                                Text(
                                    state.downloadProgress.currentFile,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            Text(
                                stringResource(
                                    R.string.download_verify_progress,
                                    state.downloadProgress.verifiedFiles,
                                    state.downloadProgress.totalFilesToVerify,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))

                            PixelProgressBar(progress = state.downloadProgress.overallPercent)

                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${(state.downloadProgress.overallPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                DownloadState.COPYING -> {
                    CopyProgressSection(
                        currentFile = state.downloadProgress.currentFile,
                        copiedFiles = state.downloadProgress.copiedFiles,
                        totalFiles = state.downloadProgress.totalFilesToCopy,
                        overallPercent = state.downloadProgress.overallPercent,
                    )
                }

                DownloadState.COMPLETED -> {
                    StardewCard {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.download_complete),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.download_complete_desc, state.installDirectory),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // Show verification result if verification was performed
                            if (state.downloadProgress.totalFilesToVerify > 0) {
                                Spacer(Modifier.height(8.dp))
                                if (state.downloadProgress.verificationPassed) {
                                    Text(
                                        stringResource(
                                            R.string.download_verify_passed,
                                            state.downloadProgress.verifiedFiles,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Text(
                                        stringResource(
                                            R.string.download_verify_failed,
                                            state.downloadProgress.verificationErrors.size,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    state.downloadProgress.verificationErrors.forEach { errorFile ->
                                        Text(
                                            errorFile,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }

                            // Cinderbox copy results
                            if (state.downloadProgress.copyCompleted) {
                                Spacer(Modifier.height(8.dp))
                                if (state.downloadProgress.copyErrors.isEmpty()) {
                                    Text(
                                        stringResource(
                                            R.string.cinderbox_copy_success,
                                            state.downloadProgress.copiedFiles,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Text(
                                        stringResource(
                                            R.string.cinderbox_copy_failed,
                                            state.downloadProgress.copyErrors.size,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    state.downloadProgress.copyErrors.forEach { errorFile ->
                                        Text(
                                            errorFile,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // Cinderbox copy button (only before copy has been done)
                    if (!state.downloadProgress.copyCompleted) {
                        StardewButton(
                            onClick = { viewModel.copyCinderbox() },
                            variant = StardewButtonVariant.Action,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.cinderbox_copy_button))
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    StardewOutlinedButton(
                        onClick = { viewModel.resetDownload() },
                        modifier = Modifier.fillMaxWidth(),
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
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.downloadProgress.errorMessage ?: stringResource(R.string.download_error_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    StardewButton(
                        onClick = {
                            viewModel.resetDownload()
                            viewModel.startDownload()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }

                DownloadState.CANCELLED -> {
                    StardewCard {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.download_cancelled),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.download_button))
                    }
                }
            }

            // Cinderbox App section
            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            CinderboxSection(
                cinderboxProgress = state.cinderboxDownloadProgress,
                onDownload = { viewModel.downloadCinderbox() },
                onReset = { viewModel.resetCinderboxDownload() },
            )

            // SMAPI Setup section (always visible)
            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            SmapiSetupSection(
                smapiProgress = state.smapiSetupProgress,
                isCopying = downloadState == DownloadState.COPYING,
                onExtract = { viewModel.extractSmapi() },
                onReset = { viewModel.resetSmapiSetup() },
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
) {
    SectionHeader(stringResource(R.string.smapi_setup_title))
    Spacer(Modifier.height(8.dp))

    when {
        smapiProgress.isRunning -> {
            // Running state
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.smapi_extracting),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (smapiProgress.currentFile.isNotEmpty()) {
                        Text(
                            smapiProgress.currentFile,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        stringResource(
                            R.string.smapi_extract_progress,
                            smapiProgress.extractedFiles,
                            smapiProgress.totalFiles,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))
                    PixelProgressBar(progress = smapiProgress.percent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(smapiProgress.percent * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            stringResource(R.string.smapi_extract_success),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.smapi_extract_success_desc, smapiProgress.extractedFiles),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            StardewOutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.smapi_extract_again))
            }
        }

        else -> {
            // Idle state
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.smapi_setup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StardewButton(
                onClick = onExtract,
                variant = StardewButtonVariant.Action,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCopying,
            ) {
                Text(stringResource(R.string.smapi_extract_button))
            }
        }
    }
}

private fun installCinderboxApk(context: Context, apkFile: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
private fun CinderboxSection(
    cinderboxProgress: CinderboxDownloadProgress,
    onDownload: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current

    var canInstall by remember {
        mutableStateOf(context.packageManager.canRequestPackageInstalls())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        canInstall = context.packageManager.canRequestPackageInstalls()
    }

    SectionHeader(stringResource(R.string.cinderbox_section_title))
    Spacer(Modifier.height(8.dp))

    when {
        cinderboxProgress.isDownloading -> {
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.cinderbox_downloading),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        stringResource(
                            R.string.cinderbox_download_progress,
                            formatBytes(cinderboxProgress.downloadedBytes),
                            if (cinderboxProgress.totalBytes > 0) formatBytes(cinderboxProgress.totalBytes) else "?",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))
                    PixelProgressBar(progress = cinderboxProgress.percent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(cinderboxProgress.percent * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        cinderboxProgress.completed -> {
            if (!canInstall) {
                // Permission not granted — show grant UI
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.cinderbox_download_complete),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.cinderbox_install_permission_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.cinderbox_install_permission_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                StardewButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        )
                        permissionLauncher.launch(intent)
                    },
                    variant = StardewButtonVariant.Action,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cinderbox_grant_permission))
                }
            } else {
                // Permission granted — show install button
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.cinderbox_download_complete),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.cinderbox_download_complete_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                ) {
                    Text(stringResource(R.string.cinderbox_install_button))
                }
                Spacer(Modifier.height(8.dp))
                StardewOutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cinderbox_download_again))
                }
            }
        }

        cinderboxProgress.errorMessage != null -> {
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.cinderbox_download_error, cinderboxProgress.errorMessage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StardewButton(
                onClick = {
                    onReset()
                    onDownload()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }

        else -> {
            // Idle state
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.cinderbox_section_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StardewButton(
                onClick = onDownload,
                variant = StardewButtonVariant.Gold,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cinderbox_download_button))
            }
        }
    }
}

@Composable
private fun DownloadProgressSection(
    currentFile: String,
    downloadedBytes: Long,
    totalBytes: Long,
    bytesPerSecond: Long,
    overallPercent: Float,
    onCancel: () -> Unit,
) {
    StardewCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.download_downloading),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (currentFile.isNotEmpty()) {
                Text(
                    currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Size + speed
            val sizeText = buildString {
                append(formatBytes(downloadedBytes))
                if (totalBytes > 0) {
                    append(" / ")
                    append(formatBytes(totalBytes))
                }
                if (bytesPerSecond > 0) {
                    append(" \u2022 ")
                    append(formatBytes(bytesPerSecond))
                    append("/s")
                }
            }
            Text(
                sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            PixelProgressBar(progress = overallPercent)

            Spacer(Modifier.height(4.dp))
            Text(
                "${(overallPercent * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            StardewOutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun CopyProgressSection(
    currentFile: String,
    copiedFiles: Int,
    totalFiles: Int,
    overallPercent: Float,
) {
    StardewCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.cinderbox_copying),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (currentFile.isNotEmpty()) {
                Text(
                    currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                stringResource(R.string.cinderbox_copy_progress, copiedFiles, totalFiles),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            PixelProgressBar(progress = overallPercent)

            Spacer(Modifier.height(4.dp))
            Text(
                "${(overallPercent * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
