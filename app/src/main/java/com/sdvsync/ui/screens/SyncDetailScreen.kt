package com.sdvsync.ui.screens

import android.content.Intent
import android.text.format.DateUtils
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdvsync.R
import com.sdvsync.sync.SyncDirection
import com.sdvsync.sync.SyncResult
import com.sdvsync.ui.components.AppButton
import com.sdvsync.ui.components.AppCard
import com.sdvsync.ui.components.AppDialog
import com.sdvsync.ui.components.AppIconButton
import com.sdvsync.ui.components.AppIcons
import com.sdvsync.ui.components.AppOutlinedButton
import com.sdvsync.ui.components.AppTopBar
import com.sdvsync.ui.components.ButtonVariant
import com.sdvsync.ui.components.LoadingIndicator
import com.sdvsync.ui.components.StoragePermissionDialog
import com.sdvsync.ui.components.SyncErrorCard
import com.sdvsync.ui.components.SyncIcon
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
    onNavigateToSettings: () -> Unit = {},
    viewModel: SyncDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasDeviceSave =
        hasLocal ||
            (state.result is SyncResult.Success && state.lastOperation == com.sdvsync.ui.viewmodels.SyncOperation.PULL)
    val hasSteamSave =
        hasCloud ||
            (state.result is SyncResult.Success && state.lastOperation == com.sdvsync.ui.viewmodels.SyncOperation.PUSH)
    var showPullConfirm by remember { mutableStateOf(false) }
    var showPushConfirm by remember { mutableStateOf(false) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onSafDirectorySelected(uri)
        }
    }

    if (state.showStoragePermissionPrompt) {
        StoragePermissionDialog(
            onPermissionResult = { viewModel.onStoragePermissionResult() },
            onDismiss = { viewModel.dismissStoragePermissionPrompt() }
        )
    }

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
            AppTopBar(
                title = saveFolderName.substringBefore("_"),
                navigationIcon = {
                    AppIconButton(
                        imageVector = AppIcons.Back,
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
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(24.dp))

            if (state.isStagingMode) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppButton(
                    onClick = {
                        if (hasDeviceSave) {
                            showPullConfirm = true
                        } else {
                            viewModel.pullSave(saveFolderName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Primary,
                    enabled = hasSteamSave && !state.isSyncing
                ) {
                    SyncIcon(direction = SyncDirection.PULL, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sync_download_device))
                }

                AppButton(
                    onClick = {
                        if (hasSteamSave) {
                            showPushConfirm = true
                        } else {
                            viewModel.pushSave(saveFolderName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Primary,
                    enabled = hasDeviceSave && !state.isSyncing
                ) {
                    SyncIcon(direction = SyncDirection.PUSH, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sync_upload_steam))
                }
            }

            // Extra action buttons (View Save, View Backups, Export)
            if (hasDeviceSave) {
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppButton(
                        onClick = onViewSaveClick,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Secondary,
                        enabled = !state.isSyncing
                    ) {
                        Text(stringResource(R.string.save_viewer_button))
                    }

                    AppOutlinedButton(
                        onClick = onBackupsClick,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSyncing
                    ) {
                        Text(stringResource(R.string.backups_view))
                    }
                }

                Spacer(Modifier.height(8.dp))

                AppOutlinedButton(
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
                AppCard(modifier = Modifier.fillMaxWidth()) {
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
                        AppOutlinedButton(
                            onClick = { viewModel.updateModAssociation(saveFolderName) }
                        ) {
                            Text(stringResource(R.string.mod_mismatch_update))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Sync progress
            if (state.isSyncing) {
                LoadingIndicator()
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
                            AppCard(modifier = Modifier.fillMaxWidth()) {
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
                            AppCard(modifier = Modifier.fillMaxWidth()) {
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
                        SyncErrorCard(
                            category = result.category,
                            rawMessage = result.message,
                            onRetry = { viewModel.retrySync() },
                            onSwitchToCinderbox = { viewModel.switchToCinderbox() },
                            onSelectSafFolder = { safLauncher.launch(null) },
                            onNavigateToSettings = onNavigateToSettings,
                            onForceSync = { viewModel.forceSync() },
                            onRunHealthCheck = { viewModel.checkSaveHealth(saveFolderName) }
                        )
                    }
                    is SyncResult.NeedsConflictResolution -> {
                        AppCard {
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
                                    AppOutlinedButton(
                                        onClick = {
                                            viewModel.clearResult()
                                            viewModel.pullSave(saveFolderName, force = true)
                                        }
                                    ) {
                                        Text(stringResource(R.string.sync_keep_cloud))
                                    }
                                    AppButton(
                                        onClick = {
                                            viewModel.clearResult()
                                            viewModel.pushSave(saveFolderName, force = true)
                                        },
                                        variant = ButtonVariant.Primary
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
            if (hasDeviceSave) {
                Spacer(Modifier.height(24.dp))

                AppCard(modifier = Modifier.fillMaxWidth()) {
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
                            AppOutlinedButton(
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
        AppDialog(
            onDismissRequest = { showPullConfirm = false },
            title = stringResource(R.string.sync_confirm_pull_title),
            text = stringResource(R.string.sync_confirm_pull_message),
            confirmButton = {
                AppButton(onClick = {
                    showPullConfirm = false
                    viewModel.pullSave(saveFolderName)
                }) {
                    Text(stringResource(R.string.sync_confirm_continue))
                }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { showPullConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showPushConfirm) {
        AppDialog(
            onDismissRequest = { showPushConfirm = false },
            title = stringResource(R.string.sync_confirm_push_title),
            text = stringResource(R.string.sync_confirm_push_message),
            confirmButton = {
                AppButton(onClick = {
                    showPushConfirm = false
                    viewModel.pushSave(saveFolderName)
                }) {
                    Text(stringResource(R.string.sync_confirm_continue))
                }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { showPushConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
