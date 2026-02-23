package com.sdvsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.sync.SyncResult
import com.sdvsync.ui.viewmodels.SyncDetailViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDetailScreen(
    saveFolderName: String,
    onBack: () -> Unit,
    viewModel: SyncDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(saveFolderName.substringBefore("_")) },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                saveFolderName,
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(Modifier.height(32.dp))

            // Staging push reminder (shown before user presses push)
            if (state.isStagingMode) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.staging_push_reminder),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = { viewModel.pullSave(saveFolderName) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSyncing,
                ) {
                    Icon(Icons.Default.CloudDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_pull))
                }

                Button(
                    onClick = { viewModel.pushSave(saveFolderName) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSyncing,
                ) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_push))
                }
            }

            Spacer(Modifier.height(24.dp))

            // Sync status
            if (state.isSyncing) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(state.progressMessage, style = MaterialTheme.typography.bodyMedium)
            }

            state.result?.let { result ->
                when (result) {
                    is SyncResult.Success -> {
                        Text(
                            result.message,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (state.isStagingMode) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(R.string.staging_pull_reminder),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                    is SyncResult.Error -> {
                        Text(
                            result.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    is SyncResult.NeedsConflictResolution -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.sync_conflict_title),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    result.comparison.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.clearResult()
                                            viewModel.pullSave(saveFolderName, force = true)
                                        },
                                    ) {
                                        Text(stringResource(R.string.sync_keep_cloud))
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.clearResult()
                                            viewModel.pushSave(saveFolderName, force = true)
                                        },
                                    ) {
                                        Text(stringResource(R.string.sync_keep_local))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
