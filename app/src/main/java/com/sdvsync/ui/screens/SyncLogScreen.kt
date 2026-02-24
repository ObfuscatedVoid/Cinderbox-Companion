package com.sdvsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.sync.SyncHistoryEntry
import com.sdvsync.ui.components.EmptyState
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.theme.SdvSyncThemeExtras
import com.sdvsync.ui.viewmodels.SyncLogViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SyncLogScreen(
    onBack: () -> Unit,
    viewModel: SyncLogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.sync_log_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(Icons.Default.DeleteSweep, stringResource(R.string.sync_log_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                state.entries.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.sync_log_empty),
                        subtitle = "Sync history will appear here",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.entries) { entry ->
                            SyncLogEntryCard(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncLogEntryCard(entry: SyncHistoryEntry) {
    val extras = SdvSyncThemeExtras.colors
    val icon = if (entry.direction == "pull") Icons.Default.CloudDownload else Icons.Default.CloudUpload
    val statusColor = if (entry.success) {
        if (entry.direction == "pull") extras.pullBlue else extras.pushGreen
    } else {
        extras.syncError
    }

    StardewCard {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = entry.direction,
                tint = statusColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        entry.saveName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        entry.direction.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.success) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        extras.syncError
                    },
                )
                Text(
                    entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
