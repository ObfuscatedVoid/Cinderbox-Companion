package com.sdvsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.theme.SdvSyncThemeExtras

@Composable
fun seasonColor(season: Int): Color {
    val colors = SdvSyncThemeExtras.colors
    return when (season) {
        0 -> colors.seasonSpring
        1 -> colors.seasonSummer
        2 -> colors.seasonFall
        3 -> colors.seasonWinter
        else -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun SeasonAccentBar(season: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(seasonColor(season))
    )
}

@Composable
fun SeasonBadge(season: Int, seasonName: String, modifier: Modifier = Modifier) {
    val color = seasonColor(season)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PixelSeasonIcon(season = season, size = 14.dp)
        Text(
            seasonName,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
