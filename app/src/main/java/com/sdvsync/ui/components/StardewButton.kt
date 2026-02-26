package com.sdvsync.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.theme.ErrorLight
import com.sdvsync.ui.theme.ForestGreen
import com.sdvsync.ui.theme.GoldAmber
import com.sdvsync.ui.theme.SdvSyncThemeExtras

enum class StardewButtonVariant {
    Primary,
    Action,
    Gold,
    Danger
}

private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

@Composable
fun StardewButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: StardewButtonVariant = StardewButtonVariant.Primary,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val (containerColor, contentColor) = when (variant) {
        StardewButtonVariant.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        StardewButtonVariant.Action -> ForestGreen to Color.White
        StardewButtonVariant.Gold -> GoldAmber to Color(0xFF3D2800)
        StardewButtonVariant.Danger -> ErrorLight to Color.White
    }

    val borderColors = SdvSyncThemeExtras.colors
    val borderDark = borderColors.pixelBorderDark
    val borderHighlight = Color.White.copy(alpha = 0.30f)
    val borderShadow = Color.Black.copy(alpha = 0.30f)

    Button(
        onClick = onClick,
        modifier = modifier.drawBehind {
            val px = 2.dp.toPx()
            val w = size.width
            val h = size.height

            // Outer dark edge (1px border)
            // Top
            drawRect(borderDark, Offset(0f, 0f), Size(w, px))
            // Bottom
            drawRect(borderDark, Offset(0f, h - px), Size(w, px))
            // Left
            drawRect(borderDark, Offset(0f, px), Size(px, h - 2 * px))
            // Right
            drawRect(borderDark, Offset(w - px, px), Size(px, h - 2 * px))

            // Inner bevel: highlight top+left, shadow bottom+right
            // Top highlight
            drawRect(borderHighlight, Offset(px, px), Size(w - 2 * px, px))
            // Left highlight
            drawRect(borderHighlight, Offset(px, 2 * px), Size(px, h - 4 * px))
            // Bottom shadow
            drawRect(borderShadow, Offset(px, h - 2 * px), Size(w - 2 * px, px))
            // Right shadow
            drawRect(borderShadow, Offset(w - 2 * px, 2 * px), Size(px, h - 4 * px))
        },
        enabled = enabled,
        shape = RectangleShape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        content = content
    )
}

@Composable
fun StardewOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val borderColors = SdvSyncThemeExtras.colors
    val borderDark = borderColors.pixelBorderDark
    val borderPlank = borderColors.pixelBorderPlank

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.drawBehind {
            val px = 2.dp.toPx()
            val w = size.width
            val h = size.height

            // Outer dark edge
            drawRect(borderDark, Offset(0f, 0f), Size(w, px))
            drawRect(borderDark, Offset(0f, h - px), Size(w, px))
            drawRect(borderDark, Offset(0f, px), Size(px, h - 2 * px))
            drawRect(borderDark, Offset(w - px, px), Size(px, h - 2 * px))

            // Inner plank edge
            drawRect(borderPlank, Offset(px, px), Size(w - 2 * px, px))
            drawRect(borderPlank, Offset(px, h - 2 * px), Size(w - 2 * px, px))
            drawRect(borderPlank, Offset(px, 2 * px), Size(px, h - 4 * px))
            drawRect(borderPlank, Offset(w - 2 * px, 2 * px), Size(px, h - 4 * px))
        },
        enabled = enabled,
        shape = RectangleShape,
        contentPadding = ButtonPadding,
        border = null,
        content = content
    )
}
