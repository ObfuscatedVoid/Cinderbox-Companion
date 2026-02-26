package com.sdvsync.ui.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

@Composable
fun StaggeredAnimatedItem(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * 50L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(300)) + slideInVertically(
            animationSpec = tween(300),
            initialOffsetY = { it / 4 }
        )
    ) {
        content()
    }
}

@Composable
fun PulseOnChange(key: Any, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(key) {
        scale.animateTo(1.15f, tween(100))
        scale.animateTo(1f, tween(150))
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        content()
    }
}
