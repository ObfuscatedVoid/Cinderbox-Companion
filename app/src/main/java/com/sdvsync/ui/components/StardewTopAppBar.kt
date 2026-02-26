package com.sdvsync.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.theme.SdvSyncThemeExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StardewTopAppBar(title: String, navigationIcon: @Composable () -> Unit = {}, actions: @Composable () -> Unit = {}) {
    val borderColors = SdvSyncThemeExtras.colors

    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        navigationIcon = navigationIcon,
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.drawBehind {
            val px = 2.dp.toPx()
            val plank = borderColors.pixelBorderPlank
            val highlight = borderColors.pixelBorderHighlight
            val y = size.height - px

            // Alternating dither pattern along the bottom edge
            var x = 0f
            var useHighlight = true
            while (x < size.width) {
                val color = if (useHighlight) highlight else plank
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(px, px)
                )
                x += px
                useHighlight = !useHighlight
            }
        }
    )
}
