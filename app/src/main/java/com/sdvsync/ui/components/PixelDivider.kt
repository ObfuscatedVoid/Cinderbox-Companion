package com.sdvsync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.theme.SdvSyncThemeExtras

/**
 * A dithered pixel divider line matching the StardewTopAppBar bottom edge pattern.
 * Alternates plank and highlight color squares in a 2dp checkerboard.
 */
@Composable
fun PixelDivider(modifier: Modifier = Modifier) {
    val borderColors = SdvSyncThemeExtras.colors
    val plank = borderColors.pixelBorderPlank
    val highlight = borderColors.pixelBorderHighlight

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        val px = 2.dp.toPx()
        var x = 0f
        var useHighlight = true
        while (x < size.width) {
            val color = if (useHighlight) highlight else plank
            drawRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(px, size.height),
            )
            x += px
            useHighlight = !useHighlight
        }
    }
}
