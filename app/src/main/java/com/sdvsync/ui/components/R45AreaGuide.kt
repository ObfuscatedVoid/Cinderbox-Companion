package com.sdvsync.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

import com.sdvsync.ui.policy.R45GuideArea
import com.sdvsync.ui.policy.R45GuidePolicy

/** Compact layout that wraps long text and keeps original Classic/Earthy theming. */
@Composable
fun R45AreaGuide(area: R45GuideArea, modifier: Modifier = Modifier) {
    val copy = R45GuidePolicy.forArea(area)
    StardewCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(copy.heading, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            copy.steps.forEachIndexed { index, label ->
                Text("${index + 1}. $label", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
