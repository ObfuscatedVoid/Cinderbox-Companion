package com.sdvsync.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.saves.BackupInfo
import com.sdvsync.saves.SaveMetadata
import com.sdvsync.ui.animation.StaggeredAnimatedItem
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.EmptyState
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewDialog
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.formatBytes
import com.sdvsync.ui.viewmodels.BackupListViewModel
import java.io.File
import org.koin.androidx.compose.koinViewModel

@Composable
fun BackupListScreen(saveFolderName: String, onBack: () -> Unit, viewModel: BackupListViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmRestore by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(saveFolderName) {
        viewModel.loadBackups(saveFolderName)
    }

    LaunchedEffect(state.restoreResult) {
        state.restoreResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearRestoreResult()
        }
    }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.backups_title),
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
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    PixelLoadingSpinner(modifier = Modifier.align(Alignment.Center))
                }
            }

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            state.backups.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = stringResource(R.string.backups_empty),
                        subtitle = stringResource(R.string.backups_empty_subtitle)
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current save reference card
                    state.currentMetadata?.let { meta ->
                        item {
                            StardewCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        stringResource(R.string.backups_current_save),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${meta.farmerName} — ${meta.displayDate}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(state.backups, key = { _, b -> b.backupDir.absolutePath }) { index, backup ->
                        StaggeredAnimatedItem(index = index) {
                            BackupCard(
                                backup = backup,
                                currentMetadata = state.currentMetadata,
                                isRestoring = state.isRestoring,
                                onRestore = { confirmRestore = backup.backupDir }
                            )
                        }
                    }
                }
            }
        }
    }

    confirmRestore?.let { backupDir ->
        StardewDialog(
            onDismissRequest = { confirmRestore = null },
            title = stringResource(R.string.backups_restore_title),
            text = stringResource(R.string.backups_restore_message),
            confirmButton = {
                StardewButton(
                    onClick = {
                        confirmRestore = null
                        viewModel.restoreBackup(backupDir)
                    },
                    variant = StardewButtonVariant.Danger
                ) {
                    Text(stringResource(R.string.backups_restore))
                }
            },
            dismissButton = {
                StardewOutlinedButton(onClick = { confirmRestore = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun BackupCard(
    backup: BackupInfo,
    currentMetadata: SaveMetadata?,
    isRestoring: Boolean,
    onRestore: () -> Unit
) {
    StardewCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                backup.timestamp,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(4.dp))

            backup.metadata?.let { meta ->
                Text(
                    "${meta.farmerName} — ${meta.displayDate}",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Comparison with current save
                if (currentMetadata != null) {
                    val diff = currentMetadata.daysPlayed - meta.daysPlayed
                    val comparisonText = when {
                        diff > 0 -> stringResource(R.string.backups_days_behind, diff)
                        diff < 0 -> stringResource(R.string.backups_days_ahead, -diff)
                        else -> stringResource(R.string.backups_same_as_current)
                    }
                    Text(
                        comparisonText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } ?: Text(
                stringResource(R.string.backups_no_metadata),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            Text(
                stringResource(R.string.backups_files, backup.fileCount, formatBytes(backup.totalSize)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            StardewButton(
                onClick = onRestore,
                variant = StardewButtonVariant.Danger,
                enabled = !isRestoring,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.backups_restore))
            }
        }
    }
}
