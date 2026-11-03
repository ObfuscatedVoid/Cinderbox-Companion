package com.sdvsync.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.policy.R44BLayoutPolicy
import com.sdvsync.ui.policy.R44BStateKind
import com.sdvsync.ui.theme.SdvSyncThemeExtras

/** Adapts single-column content to compact, medium and large windows without fixed text height. */
@Composable
fun R44BResponsiveColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val measure = R44BLayoutPolicy.maxContentWidthDp(maxWidth.value.toInt())
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = measure.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content
        )
    }
}

/**
 * Accessible status state. Every outcome has a text label; color does not encode status alone.
 * The optional callback is supplied by its owning screen, never inferred from status.
 */
@Composable
fun R44BStatePanel(
    kind: R44BStateKind,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val earthy = SdvSyncThemeExtras.isEarthy
    val palette = MaterialTheme.colorScheme
    val background = when (kind) {
        R44BStateKind.LOADING -> palette.surfaceContainerLow
        R44BStateKind.EMPTY -> palette.surfaceContainerLow
        R44BStateKind.NEEDS_ATTENTION -> palette.errorContainer
        R44BStateKind.COMPLETED -> palette.secondaryContainer
        R44BStateKind.INFO -> palette.surfaceContainerLow
    }
    val foreground = when (kind) {
        R44BStateKind.NEEDS_ATTENTION -> palette.onErrorContainer
        R44BStateKind.COMPLETED -> palette.onSecondaryContainer
        else -> palette.onSurface
    }
    val status = when (kind) {
        R44BStateKind.LOADING -> "In progress"
        R44BStateKind.EMPTY -> "Nothing here yet"
        R44BStateKind.NEEDS_ATTENTION -> "Needs attention"
        R44BStateKind.COMPLETED -> "Completed"
        R44BStateKind.INFO -> "Information"
    }
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = if (earthy) MaterialTheme.shapes.large else RectangleShape,
        color = background,
        tonalElevation = if (earthy) 1.dp else 0.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (kind == R44BStateKind.LOADING) {
                    CircularProgressIndicator()
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(status, style = MaterialTheme.typography.labelLarge, color = foreground)
                    Spacer(Modifier.height(4.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        color = foreground, modifier = Modifier.semantics { heading() })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = foreground)
            if (!supportingText.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = foreground)
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}
