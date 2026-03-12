package com.sdvsync.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.sync.SyncErrorCategory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncErrorCard(
    category: SyncErrorCategory,
    rawMessage: String,
    onRetry: (() -> Unit)? = null,
    onSwitchToCinderbox: (() -> Unit)? = null,
    onSelectSafFolder: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onForceSync: (() -> Unit)? = null,
    onRunHealthCheck: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (title, description) = errorContent(category)
    var showDetails by remember { mutableStateOf(false) }

    StardewCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (showDetails) {
                    stringResource(R.string.sync_error_details_label) + " ▾"
                } else {
                    stringResource(R.string.sync_error_details_label) +
                        " ▸"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { showDetails = !showDetails }
            )

            AnimatedVisibility(visible = showDetails) {
                Text(
                    text = rawMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Action buttons
            val hasActions = when (category) {
                SyncErrorCategory.WRITE_FAILED ->
                    onSwitchToCinderbox != null || onSelectSafFolder != null || onNavigateToSettings != null
                SyncErrorCategory.NETWORK_ERROR, SyncErrorCategory.INVALID_DOWNLOAD,
                SyncErrorCategory.SAVE_IN_PROGRESS, SyncErrorCategory.BACKUP_FAILED -> onRetry != null

                SyncErrorCategory.INVALID_LOCAL -> onRunHealthCheck != null
                SyncErrorCategory.LOCAL_NEWER, SyncErrorCategory.CLOUD_NEWER -> onForceSync != null
                SyncErrorCategory.UNKNOWN -> onRetry != null || onNavigateToSettings != null
                SyncErrorCategory.NO_CLOUD_FILES, SyncErrorCategory.NO_LOCAL_FILES -> false
            }

            if (hasActions) {
                Spacer(Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (category) {
                        SyncErrorCategory.WRITE_FAILED -> {
                            if (onSwitchToCinderbox != null) {
                                StardewButton(
                                    onClick = onSwitchToCinderbox,
                                    variant = StardewButtonVariant.Primary
                                ) {
                                    Text(stringResource(R.string.sync_error_action_cinderbox))
                                }
                            }
                            if (onSelectSafFolder != null) {
                                StardewOutlinedButton(onClick = onSelectSafFolder) {
                                    Text(stringResource(R.string.sync_error_action_saf))
                                }
                            }
                            if (onNavigateToSettings != null) {
                                StardewOutlinedButton(onClick = onNavigateToSettings) {
                                    Text(stringResource(R.string.sync_error_action_settings))
                                }
                            }
                        }

                        SyncErrorCategory.NETWORK_ERROR,
                        SyncErrorCategory.INVALID_DOWNLOAD,
                        SyncErrorCategory.SAVE_IN_PROGRESS,
                        SyncErrorCategory.BACKUP_FAILED -> {
                            if (onRetry != null) {
                                StardewButton(
                                    onClick = onRetry,
                                    variant = StardewButtonVariant.Primary
                                ) {
                                    Text(stringResource(R.string.sync_error_action_retry))
                                }
                            }
                        }

                        SyncErrorCategory.INVALID_LOCAL -> {
                            if (onRunHealthCheck != null) {
                                StardewButton(
                                    onClick = onRunHealthCheck,
                                    variant = StardewButtonVariant.Primary
                                ) {
                                    Text(stringResource(R.string.sync_error_action_health_check))
                                }
                            }
                        }

                        SyncErrorCategory.LOCAL_NEWER,
                        SyncErrorCategory.CLOUD_NEWER -> {
                            if (onForceSync != null) {
                                StardewButton(
                                    onClick = onForceSync,
                                    variant = StardewButtonVariant.Danger
                                ) {
                                    Text(stringResource(R.string.sync_error_action_force_sync))
                                }
                            }
                        }

                        SyncErrorCategory.UNKNOWN -> {
                            if (onRetry != null) {
                                StardewButton(
                                    onClick = onRetry,
                                    variant = StardewButtonVariant.Primary
                                ) {
                                    Text(stringResource(R.string.sync_error_action_retry))
                                }
                            }
                            if (onNavigateToSettings != null) {
                                StardewOutlinedButton(onClick = onNavigateToSettings) {
                                    Text(stringResource(R.string.sync_error_action_settings))
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun errorContent(category: SyncErrorCategory): Pair<String, String> = when (category) {
    SyncErrorCategory.NO_CLOUD_FILES -> Pair(
        stringResource(R.string.sync_error_title_no_cloud),
        stringResource(R.string.sync_error_desc_no_cloud)
    )
    SyncErrorCategory.INVALID_DOWNLOAD -> Pair(
        stringResource(R.string.sync_error_title_invalid_download),
        stringResource(R.string.sync_error_desc_invalid_download)
    )
    SyncErrorCategory.WRITE_FAILED -> Pair(
        stringResource(R.string.sync_error_title_write_failed),
        stringResource(R.string.sync_error_desc_write_failed)
    )
    SyncErrorCategory.NETWORK_ERROR -> Pair(
        stringResource(R.string.sync_error_title_network),
        stringResource(R.string.sync_error_desc_network)
    )
    SyncErrorCategory.NO_LOCAL_FILES -> Pair(
        stringResource(R.string.sync_error_title_no_local),
        stringResource(R.string.sync_error_desc_no_local)
    )
    SyncErrorCategory.SAVE_IN_PROGRESS -> Pair(
        stringResource(R.string.sync_error_title_save_in_progress),
        stringResource(R.string.sync_error_desc_save_in_progress)
    )
    SyncErrorCategory.INVALID_LOCAL -> Pair(
        stringResource(R.string.sync_error_title_invalid_local),
        stringResource(R.string.sync_error_desc_invalid_local)
    )
    SyncErrorCategory.BACKUP_FAILED -> Pair(
        stringResource(R.string.sync_error_title_backup_failed),
        stringResource(R.string.sync_error_desc_backup_failed)
    )
    SyncErrorCategory.LOCAL_NEWER -> Pair(
        stringResource(R.string.sync_error_title_local_newer),
        stringResource(R.string.sync_error_desc_local_newer)
    )
    SyncErrorCategory.CLOUD_NEWER -> Pair(
        stringResource(R.string.sync_error_title_cloud_newer),
        stringResource(R.string.sync_error_desc_cloud_newer)
    )
    SyncErrorCategory.UNKNOWN -> Pair(
        stringResource(R.string.sync_error_title_unknown),
        stringResource(R.string.sync_error_desc_unknown)
    )
}
