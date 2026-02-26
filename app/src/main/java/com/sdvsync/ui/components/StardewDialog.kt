package com.sdvsync.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Stardew-styled dialog with pixel border, replacing Material AlertDialog.
 */
@Composable
fun StardewDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pixelBorder(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = 8.dp
                )
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.align(Alignment.End)
                ) {
                    dismissButton()
                    Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}
