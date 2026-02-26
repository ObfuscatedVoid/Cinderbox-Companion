package com.sdvsync.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.theme.GoldAmber
import com.sdvsync.ui.theme.GoldBright

/**
 * Animated pixel art loading spinner — a rotating gold star.
 */
@Composable
fun PixelLoadingSpinner(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerRotation"
    )

    PixelIcon(
        pixelData = StarIconData,
        palette = listOf(
            androidx.compose.ui.graphics.Color.Transparent,
            GoldBright,
            GoldAmber
        ),
        modifier = modifier.graphicsLayer { rotationZ = rotation },
        size = size
    )
}
