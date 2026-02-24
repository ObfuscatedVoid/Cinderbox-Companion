package com.sdvsync.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chickenBob")
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = with(LocalDensity.current) { -3.dp.toPx() },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chickenBobOffset",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelIcon(
            pixelData = ChickenIconData,
            palette = listOf(
                androidx.compose.ui.graphics.Color.Transparent,
                androidx.compose.ui.graphics.Color.White,
                androidx.compose.ui.graphics.Color(0xFFE03030),
                androidx.compose.ui.graphics.Color(0xFFE8A030),
                androidx.compose.ui.graphics.Color(0xFF2A1A00),
            ),
            modifier = Modifier.graphicsLayer { translationY = bobOffset },
            size = 48.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}
