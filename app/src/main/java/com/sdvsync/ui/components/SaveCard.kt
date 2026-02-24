package com.sdvsync.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.saves.SaveMetadata
import com.sdvsync.sync.SyncDirection
import com.sdvsync.ui.animation.PulseOnChange
import com.sdvsync.ui.theme.SdvSyncThemeExtras
import com.sdvsync.ui.viewmodels.SaveEntry

@Composable
fun SaveCard(
    save: SaveEntry,
    onClick: () -> Unit,
) {
    val meta = save.cloudMeta ?: save.localMeta

    StardewCard(onClick = onClick) {
        if (meta != null) {
            SeasonAccentBar(season = meta.season)
        }

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
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (meta?.farmName?.isNotEmpty() == true) {
                        Text(
                            stringResource(R.string.save_farm_name, meta.farmName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PulseOnChange(key = save.syncDirection) {
                    SyncStatusBadge(save)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (meta != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatDisplayDate(meta),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SeasonBadge(
                        season = meta.season,
                        seasonName = seasonName(meta.season),
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
fun SyncStatusBadge(save: SaveEntry) {
    val extras = SdvSyncThemeExtras.colors
    val (direction, color, label) = when (save.syncDirection) {
        SyncDirection.PULL -> Triple(
            save.syncDirection,
            extras.pullBlue,
            if (!save.hasLocal) stringResource(R.string.save_status_cloud)
            else stringResource(R.string.save_status_cloud_newer),
        )
        SyncDirection.PUSH -> Triple(
            save.syncDirection,
            extras.pushGreen,
            if (!save.hasCloud) stringResource(R.string.save_status_local)
            else stringResource(R.string.save_status_local_newer),
        )
        SyncDirection.SKIP -> Triple(
            save.syncDirection,
            extras.synced,
            stringResource(R.string.save_status_synced),
        )
        SyncDirection.CONFLICT -> Triple(
            save.syncDirection,
            extras.conflict,
            stringResource(R.string.save_status_conflict),
        )
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PixelSyncIcon(
                direction = direction,
                size = 14.dp,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
fun seasonName(season: Int): String = when (season) {
    0 -> stringResource(R.string.save_season_spring)
    1 -> stringResource(R.string.save_season_summer)
    2 -> stringResource(R.string.save_season_fall)
    3 -> stringResource(R.string.save_season_winter)
    else -> stringResource(R.string.save_season_unknown)
}

@Composable
private fun formatDisplayDate(meta: SaveMetadata): String {
    val name = seasonName(meta.season)
    return stringResource(R.string.save_display_date, name, meta.dayOfMonth, meta.year)
}
