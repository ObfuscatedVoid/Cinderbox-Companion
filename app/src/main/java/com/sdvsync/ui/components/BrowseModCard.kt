package com.sdvsync.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sdvsync.R
import com.sdvsync.mods.models.RemoteMod

private fun formatCount(count: Int): String {
    if (count < 1000) return count.toString()
    if (count < 1_000_000) return "%.1fK".format(count / 1000.0)
    return "%.1fM".format(count / 1_000_000.0)
}

@Composable
fun BrowseModCard(
    mod: RemoteMod,
    isInstalled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StardewCard(
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // Thumbnail
            if (mod.pictureUrl != null) {
                AsyncImage(
                    model = mod.pictureUrl,
                    contentDescription = mod.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RectangleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Pixel art placeholder
                PixelIcon(
                    pixelData = PuzzleData,
                    palette = listOf(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(56.dp),
                    size = 56.dp,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mod.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mod.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (mod.summary.isNotBlank()) {
                    Text(
                        text = mod.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = stringResource(R.string.mods_downloads, formatCount(mod.downloads)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.mods_endorsements, formatCount(mod.endorsements)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (isInstalled) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.mods_installed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
