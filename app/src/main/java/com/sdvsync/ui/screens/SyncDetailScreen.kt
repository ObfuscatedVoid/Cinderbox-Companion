package com.sdvsync.ui.screens

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sdvsync.R
import com.sdvsync.sync.SyncDirection
import com.sdvsync.sync.SyncResult
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.PixelSyncIcon
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewDialog
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.viewmodels.SyncDetailViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SyncDetailScreen(
    saveFolderName: String,
    hasCloud: Boolean,
    hasLocal: Boolean,
    onBack: () -> Unit,
    onBackupsClick: () -> Unit = {},
    onViewSaveClick: () -> Unit = {},
    viewModel: SyncDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showPullConfirm by remember { mutableStateOf(false) }
    var showPushConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(saveFolderName) {
        viewModel.loadModAssociation(saveFolderName)
    }

    // Share export file when ready
    val exportContext = LocalContext.current
    LaunchedEffect(state.exportFile) {
        val file = state.exportFile ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(
            exportContext,
            "${exportContext.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        exportContext.startActivity(Intent.createChooser(shareIntent, null))
        viewModel.clearExportFile()
    }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = saveFolderName.substringBefore("_"),
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                saveFolderName,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(32.dp))

            if (state.isStagingMode) {
                StardewCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.staging_push_reminder),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StardewButton(
                    onClick = {
                        if (hasLocal) {
                            showPullConfirm = true
                        } else {
                            viewModel.pullSave(saveFolderName)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variant = StardewButtonVariant.Primary,
                    enabled = !state.isSyncing
                ) {
                    PixelSyncIcon(direction = SyncDirection.PULL, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_pull))
                }

                StardewButton(
                    onClick = {
                        if (hasCloud) {
                            showPushConfirm = true
                        } else {
                            viewModel.pushSave(saveFolderName)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variant = StardewButtonVariant.Action,
                    enabled = !state.isSyncing
                ) {
                    PixelSyncIcon(direction = SyncDirection.PUSH, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_push))
                }
            }

            // Extra action buttons (View Save, View Backups, Export)
            if (hasLocal) {
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StardewButton(
                        onClick = onViewSaveClick,
                        modifier = Modifier.weight(1f),
                        variant = StardewButtonVariant.Gold,
                        enabled = !state.isSyncing
                    ) {
                        Text(stringResource(R.string.save_viewer_button))
                    }

                    StardewOutlinedButton(
                        onClick = onBackupsClick,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSyncing
                    ) {
                        Text(stringResource(R.string.backups_view))
                    }
                }

                Spacer(Modifier.height(8.dp))

                StardewOutlinedButton(
                    onClick = { viewModel.exportSave(saveFolderName) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSyncing && !state.isExporting
                ) {
                    if (state.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_exporting))
                    } else {
                        Text(stringResource(R.string.export_button))
                    }
                }

                if (state.exportError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.export_error, state.exportError!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Mod mismatch warning
            val mismatch = state.modMismatch
            if (mismatch != null && hasLocal) {
                Spacer(Modifier.height(16.dp))
                val ctx = LocalContext.current
                StardewCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.mod_mismatch_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.height(4.dp))
                        if (mismatch.missingMods.isNotEmpty()) {
                            Text(
                                stringResource(R.string.mod_mismatch_missing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                mismatch.missingMods.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (mismatch.extraMods.isNotEmpty()) {
                            if (mismatch.missingMods.isNotEmpty()) Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.mod_mismatch_extra),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                mismatch.extraMods.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                R.string.mod_mismatch_last_synced,
                                DateUtils.getRelativeTimeSpanString(
                                    mismatch.lastSyncTime,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                ).toString()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        StardewOutlinedButton(
                            onClick = { viewModel.updateModAssociation(saveFolderName) }
                        ) {
                            Text(stringResource(R.string.mod_mismatch_update))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Sync status with pulsing pixel spinner
            if (state.isSyncing) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                PixelLoadingSpinner(
                    modifier = Modifier.alpha(pulseAlpha)
                )
                Spacer(Modifier.height(12.dp))
                Text(state.progressMessage, style = MaterialTheme.typography.bodyMedium)
            }

            state.result?.let { result ->
                when (result) {
                    is SyncResult.Success -> {
                        Text(
                            result.message,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (result.warning != null) {
                            Spacer(Modifier.height(12.dp))
                            StardewCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    result.warning,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (state.isStagingMode) {
                            Spacer(Modifier.height(12.dp))
                            StardewCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(R.string.staging_pull_reminder),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                    is SyncResult.Error -> {
                        Text(
                            result.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    is SyncResult.NeedsConflictResolution -> {
                        StardewCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.sync_conflict_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    result.comparison.message,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StardewOutlinedButton(
                                        onClick = {
                                            viewModel.clearResult()
                                            viewModel.pullSave(saveFolderName, force = true)
                                        }
                                    ) {
                                        Text(stringResource(R.string.sync_keep_cloud))
                                    }
                                    StardewButton(
                                        onClick = {
                                            viewModel.clearResult()
                                            viewModel.pushSave(saveFolderName, force = true)
                                        },
                                        variant = StardewButtonVariant.Action
                                    ) {
                                        Text(stringResource(R.string.sync_keep_local))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Health Check section
            if (hasLocal) {
                Spacer(Modifier.height(24.dp))

                StardewCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.health_check_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            StardewOutlinedButton(
                                onClick = { viewModel.checkSaveHealth(saveFolderName) },
                                enabled = !state.isCheckingHealth && !state.isSyncing
                            ) {
                                Text(stringResource(R.string.health_check_run))
                            }
                        }

                        if (state.isCheckingHealth) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.health_check_checking),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        state.healthCheck?.let { check ->
                            Spacer(Modifier.height(12.dp))
                            if (check.valid && check.warnings.isEmpty()) {
                                Text(
                                    stringResource(R.string.health_check_healthy),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (check.warnings.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.health_check_warnings),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                check.warnings.forEach { warning ->
                                    Text(
                                        "• $warning",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            if (check.errors.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.health_check_errors),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                check.errors.forEach { error ->
                                    Text(
                                        "• $error",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPullConfirm) {
        StardewDialog(
            onDismissRequest = { showPullConfirm = false },
            title = stringResource(R.string.sync_confirm_pull_title),
            text = stringResource(R.string.sync_confirm_pull_message),
            confirmButton = {
                StardewButton(onClick = {
                    showPullConfirm = false
                    viewModel.pullSave(saveFolderName)
                }) {
                    Text(stringResource(R.string.sync_confirm_continue))
                }
            },
            dismissButton = {
                StardewOutlinedButton(onClick = { showPullConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showPushConfirm) {
        StardewDialog(
            onDismissRequest = { showPushConfirm = false },
            title = stringResource(R.string.sync_confirm_push_title),
            text = stringResource(R.string.sync_confirm_push_message),
            confirmButton = {
                StardewButton(onClick = {
                    showPushConfirm = false
                    viewModel.pushSave(saveFolderName)
                }) {
                    Text(stringResource(R.string.sync_confirm_continue))
                }
            },
            dismissButton = {
                StardewOutlinedButton(onClick = { showPushConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
