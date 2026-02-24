package com.sdvsync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders a pixel art icon from a 2D grid.
 *
 * @param pixelData 2D array where each int is an index into [palette]. 0 = transparent.
 * @param palette List of colors. Index 0 is unused (transparent).
 * @param size The rendered size of the icon.
 */
@Composable
fun PixelIcon(
    pixelData: Array<IntArray>,
    palette: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val rows = pixelData.size
        val cols = pixelData.maxOfOrNull { it.size } ?: return@Canvas
        val cellW = this.size.width / cols
        val cellH = this.size.height / rows

        for (row in pixelData.indices) {
            for (col in pixelData[row].indices) {
                val idx = pixelData[row][col]
                if (idx > 0 && idx < palette.size) {
                    drawRect(
                        color = palette[idx],
                        topLeft = androidx.compose.ui.geometry.Offset(
                            col * cellW,
                            row * cellH,
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            cellW + 0.5f,
                            cellH + 0.5f,
                        ),
                    )
                }
            }
        }
    }
}
