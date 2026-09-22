package com.sdvsync.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    CircularProgressIndicator(modifier = modifier.size(size), strokeWidth = 3.dp)
}

@Composable
fun DownloadProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = {
        if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    }, modifier.fillMaxWidth())
}
