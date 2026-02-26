package com.sdvsync.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    viewModel: SyncDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showPullConfirm by remember { mutableStateOf(false) }
    var showPushConfirm by remember { mutableStateOf(false) }

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
