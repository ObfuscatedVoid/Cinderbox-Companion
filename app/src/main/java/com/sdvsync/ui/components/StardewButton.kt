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

    Button(
        onClick = onClick,
        modifier = modifier,
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
