package com.sdvsync.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.sync.SyncDirection
import com.sdvsync.ui.theme.*
import com.sdvsync.ui.viewmodels.SaveEntry

@Composable
fun SaveCard(
    save: SaveEntry,
    onClick: () -> Unit,
) {
    val meta = save.cloudMeta ?: save.localMeta

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        meta?.farmerName ?: save.folderName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (meta?.farmName?.isNotEmpty() == true) {
                        Text(
                            stringResource(R.string.save_farm_name, meta.farmName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SyncStatusBadge(save.syncDirection)
            }

            Spacer(Modifier.height(8.dp))

            if (meta != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        meta.displayDate,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (meta.millisecondsPlayed > 0) {
                        val hours = meta.millisecondsPlayed / 3_600_000
                        val mins = (meta.millisecondsPlayed % 3_600_000) / 60_000
                        Text(
                            stringResource(R.string.save_play_time, hours, mins),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (meta.gameVersion.isNotEmpty()) {
                        Text(
                            stringResource(R.string.save_version, meta.gameVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                save.statusMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SyncStatusBadge(direction: SyncDirection) {
    val (icon, color, label) = when (direction) {
        SyncDirection.PULL -> Triple(Icons.Default.CloudDownload, CloudBlue, stringResource(R.string.action_pull))
        SyncDirection.PUSH -> Triple(Icons.Default.CloudUpload, StardewGreen, stringResource(R.string.action_push))
        SyncDirection.SKIP -> Triple(Icons.Default.CheckCircle, SyncedGreen, stringResource(R.string.save_status_synced))
        SyncDirection.CONFLICT -> Triple(Icons.Default.Warning, ConflictOrange, stringResource(R.string.save_status_conflict))
    }

    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = color,
            )
        },
    )
}
