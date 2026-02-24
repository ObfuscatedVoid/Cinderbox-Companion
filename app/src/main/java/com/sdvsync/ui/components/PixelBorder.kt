package com.sdvsync.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.theme.SdvSyncThemeExtras

/**
 * Draws a Stardew-inspired 4-layer wooden pixel border around the content.
 * Each "pixel" = [pixelSize]. Total border = 4 pixels wide.
 *
 * Layers (outside → inside):
 * 1. Outer edge: dark brown outline
 * 2. Plank fill: medium brown
 * 3. Bevel: highlight (top+left), shadow (bottom+right)
 * 4. Inner plank: medium brown
 */
@Composable
fun Modifier.pixelBorder(pixelSize: Dp = 2.dp): Modifier {
    val colors = SdvSyncThemeExtras.colors
    val dark = colors.pixelBorderDark
    val plank = colors.pixelBorderPlank
    val highlight = colors.pixelBorderHighlight
    val shadow = colors.pixelBorderShadow
    val px = with(LocalDensity.current) { pixelSize.toPx() }

    return this.drawBehind {
        val w = size.width
        val h = size.height

        // Helper to draw a single pixel
        fun pixel(x: Float, y: Float, color: Color) {
            drawRect(color, Offset(x, y), Size(px, px))
        }

        // Helper to draw a horizontal line of pixels
        fun hLine(x1: Float, x2: Float, y: Float, color: Color) {
            drawRect(color, Offset(x1, y), Size(x2 - x1, px))
        }

        // Helper to draw a vertical line of pixels
        fun vLine(x: Float, y1: Float, y2: Float, color: Color) {
            drawRect(color, Offset(x, y1), Size(px, y2 - y1))
        }

        // Layer 1: Outer dark edge (outermost ring)
        // Top edge — skip corners for notch effect
        hLine(px, w - px, 0f, dark)
        // Bottom edge
        hLine(px, w - px, h - px, dark)
        // Left edge
        vLine(0f, px, h - px, dark)
        // Right edge
        vLine(w - px, px, h - px, dark)

        // Layer 2: Plank fill (second ring)
        hLine(px, w - px, px, plank)
        hLine(px, w - px, h - 2 * px, plank)
        vLine(px, 2 * px, h - 2 * px, plank)
        vLine(w - 2 * px, 2 * px, h - 2 * px, plank)
        // Fill corner pixels at layer 1 that were notched
        pixel(px, px, plank) // TL inner
        pixel(w - 2 * px, px, plank) // TR inner
        pixel(px, h - 2 * px, plank) // BL inner
        pixel(w - 2 * px, h - 2 * px, plank) // BR inner

        // Layer 3: Bevel — highlight on top+left, shadow on bottom+right
        // Top highlight
        hLine(2 * px, w - 2 * px, 2 * px, highlight)
        // Left highlight
        vLine(2 * px, 3 * px, h - 3 * px, highlight)
        // Bottom shadow
        hLine(3 * px, w - 2 * px, h - 3 * px, shadow)
        // Right shadow
        vLine(w - 3 * px, 3 * px, h - 3 * px, shadow)
        // Corner: TL gets highlight
        pixel(2 * px, 2 * px, highlight)
        // Corner: BR gets shadow
        pixel(w - 3 * px, h - 3 * px, shadow)

        // Layer 4: Inner plank (innermost ring)
        hLine(3 * px, w - 3 * px, 3 * px, plank)
        hLine(3 * px, w - 3 * px, h - 4 * px, plank)
        vLine(3 * px, 4 * px, h - 4 * px, plank)
        vLine(w - 4 * px, 4 * px, h - 4 * px, plank)
    }
}

/**
 * Thinner 2-layer pixel border for smaller elements. Total = 2 pixels wide.
 */
@Composable
fun Modifier.pixelBorderThin(pixelSize: Dp = 2.dp): Modifier {
    val colors = SdvSyncThemeExtras.colors
    val dark = colors.pixelBorderDark
    val plank = colors.pixelBorderPlank

    val px = with(LocalDensity.current) { pixelSize.toPx() }

    return this.drawBehind {
        val w = size.width
        val h = size.height

        fun hLine(x1: Float, x2: Float, y: Float, color: Color) {
            drawRect(color, Offset(x1, y), Size(x2 - x1, px))
        }

        fun vLine(x: Float, y1: Float, y2: Float, color: Color) {
            drawRect(color, Offset(x, y1), Size(px, y2 - y1))
        }

        // Outer dark edge
        hLine(px, w - px, 0f, dark)
        hLine(px, w - px, h - px, dark)
        vLine(0f, px, h - px, dark)
        vLine(w - px, px, h - px, dark)

        // Inner plank
        hLine(px, w - px, px, plank)
        hLine(px, w - px, h - 2 * px, plank)
        vLine(px, 2 * px, h - 2 * px, plank)
        vLine(w - 2 * px, 2 * px, h - 2 * px, plank)
    }
}
