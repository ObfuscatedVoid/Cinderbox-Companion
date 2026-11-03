package com.sdvsync.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.policy.R44StatusTone

/**
 * A readable, text-first status summary. Color is supportive, never the only
 * signal; no action is inferred or issued from the visual status.
 */
@Composable
fun R44StatusSummary(
    title: String,
    detail: String,
    nextStep: String,
    tone: R44StatusTone,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val container = when (tone) {
        R44StatusTone.AVAILABLE -> colors.secondaryContainer
        R44StatusTone.SETUP -> colors.primaryContainer
        R44StatusTone.BLOCKED -> colors.errorContainer
        R44StatusTone.PROTECTED -> colors.surfaceContainerHighest
        R44StatusTone.REVIEW -> colors.tertiaryContainer
    }
    val foreground = when (tone) {
        R44StatusTone.AVAILABLE -> colors.onSecondaryContainer
        R44StatusTone.SETUP -> colors.onPrimaryContainer
        R44StatusTone.BLOCKED -> colors.onErrorContainer
        R44StatusTone.PROTECTED -> colors.onSurface
        R44StatusTone.REVIEW -> colors.onTertiaryContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(tone.label, style = MaterialTheme.typography.labelMedium, color = foreground)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = foreground)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = foreground)
            Spacer(Modifier.height(8.dp))
            Text("Next step: $nextStep", style = MaterialTheme.typography.bodySmall, color = foreground)
        }
    }
}
