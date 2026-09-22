package com.sdvsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdvsync.R
import com.sdvsync.sync.SyncHistoryEntry
import com.sdvsync.ui.components.AppButton
import com.sdvsync.ui.components.AppCard
import com.sdvsync.ui.components.AppDialog
import com.sdvsync.ui.components.AppIconButton
import com.sdvsync.ui.components.AppIcons
import com.sdvsync.ui.components.AppOutlinedButton
import com.sdvsync.ui.components.AppTopBar
import com.sdvsync.ui.components.ButtonVariant
import com.sdvsync.ui.components.EmptyState
import com.sdvsync.ui.components.LoadingIndicator
import com.sdvsync.ui.components.SyncLogIcon
import com.sdvsync.ui.theme.SdvSyncThemeExtras
import com.sdvsync.ui.viewmodels.SyncLogViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SyncLogScreen(onBack: () -> Unit, viewModel: SyncLogViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showClear by rememberSaveable { mutableStateOf(false) }
    if (showClear) {
        AppDialog(
            onDismissRequest = { showClear = false },
            title = stringResource(R.string.sync_log_clear),
            text = stringResource(R.string.sync_log_clear_confirm),
            confirmButton = {
                AppButton(onClick = {
                    showClear = false
                    viewModel.clearHistory()
                }, variant = ButtonVariant.Danger) {
                    Text(stringResource(R.string.sync_log_clear))
                }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { showClear = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.sync_log_title),
                navigationIcon = {
                    AppIconButton(
                        imageVector = AppIcons.Back,
                        onClick = onBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        AppIconButton(
                            imageVector = AppIcons.Delete,
                            onClick = { showClear = true },
                            contentDescription = stringResource(R.string.sync_log_clear)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error != null -> {
                    Column(modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        AppButton(onClick = viewModel::load) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                state.entries.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.sync_log_empty),
                        subtitle = "Sync history will appear here",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
    val statusColor = if (entry.success) {
        if (entry.direction == "pull") extras.pullBlue else extras.pushGreen
    } else {
        extras.syncError
    }

    AppCard {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SyncLogIcon(
                direction = entry.direction,
                success = entry.success,
                size = 24.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        entry.saveName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        entry.direction.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
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
                    }
                )
                Text(
                    entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
