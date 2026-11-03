package com.sdvsync.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.theme.SdvSyncThemeExtras

@Composable
fun StardewCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val earthy = SdvSyncThemeExtras.isEarthy
    // R44: Earthy uses Material 3 surfaces. Classic retains its pixel border.
    val cardModifier = if (earthy) modifier.fillMaxWidth() else modifier
        .fillMaxWidth()
        .pixelBorder()
        .padding(8.dp) // Classic's original 4-layer border inset

    Surface(
        modifier = if (onClick != null) {
            cardModifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            cardModifier
        },
        shape = if (earthy) MaterialTheme.shapes.large else RectangleShape,
        color = if (earthy) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (earthy) 2.dp else 0.dp,
        shadowElevation = if (earthy) 1.dp else 0.dp
    ) {
        Column { content() }
    }
}
