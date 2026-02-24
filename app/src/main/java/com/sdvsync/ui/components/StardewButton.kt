package com.sdvsync.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.theme.ErrorLight
import com.sdvsync.ui.theme.ForestGreen
import com.sdvsync.ui.theme.GoldAmber

enum class StardewButtonVariant {
    Primary,
    Action,
    Gold,
    Danger,
}

private val ButtonShape = RoundedCornerShape(8.dp)
private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

@Composable
fun StardewButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: StardewButtonVariant = StardewButtonVariant.Primary,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val (containerColor, contentColor) = when (variant) {
        StardewButtonVariant.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        StardewButtonVariant.Action -> ForestGreen to Color.White
        StardewButtonVariant.Gold -> GoldAmber to Color(0xFF3D2800)
        StardewButtonVariant.Danger -> ErrorLight to Color.White
    }

    val highlightColor = Color.White.copy(alpha = 0.25f)
    val shadowColor = Color.Black.copy(alpha = 0.25f)

    Button(
        onClick = onClick,
        modifier = modifier.drawBehind {
            val strokeWidth = 1.dp.toPx()
            // Top highlight
            drawLine(
                highlightColor,
                Offset(strokeWidth, strokeWidth),
                Offset(size.width - strokeWidth, strokeWidth),
                strokeWidth,
            )
            // Left highlight
            drawLine(
                highlightColor,
                Offset(strokeWidth, strokeWidth),
                Offset(strokeWidth, size.height - strokeWidth),
                strokeWidth,
            )
            // Bottom shadow
            drawLine(
                shadowColor,
                Offset(strokeWidth, size.height - strokeWidth),
                Offset(size.width - strokeWidth, size.height - strokeWidth),
                strokeWidth,
            )
            // Right shadow
            drawLine(
                shadowColor,
                Offset(size.width - strokeWidth, strokeWidth),
                Offset(size.width - strokeWidth, size.height - strokeWidth),
                strokeWidth,
            )
        },
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        content = content,
    )
}

@Composable
fun StardewOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = ButtonPadding,
        content = content,
    )
}
