package com.sdvsync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.theme.GoldAmber
import com.sdvsync.ui.theme.GoldBright
import kotlin.random.Random
import kotlinx.coroutines.isActive

private data class Sparkle(
    var x: Float,
    var y: Float,
    var alpha: Float,
    var speed: Float,
    var radius: Float,
    var color: Color,
    // 1.0 → 0.0
    var life: Float
)

/**
 * Ambient floating gold sparkle particles — a lightweight Canvas-based particle system.
 * Spawns tiny gold dots that drift upward and fade out.
 */
@Composable
fun SparkleOverlay(modifier: Modifier = Modifier, particleCount: Int = 7) {
    val sparkles = remember { mutableStateListOf<Sparkle>() }

    LaunchedEffect(Unit) {
        val frameTimeMs = 16L // ~60fps
        var timeSinceSpawn = 0L
        val spawnInterval = 400L

        while (isActive) {
            val startTime = System.nanoTime()

            // Spawn new particles
            timeSinceSpawn += frameTimeMs
            if (sparkles.size < particleCount && timeSinceSpawn >= spawnInterval) {
                timeSinceSpawn = 0L
                sparkles.add(
                    Sparkle(
                        x = Random.nextFloat(),
                        y = 0.8f + Random.nextFloat() * 0.2f,
                        alpha = 0.6f,
                        speed = 0.0003f + Random.nextFloat() * 0.0004f,
                        radius = 2f + Random.nextFloat() * 2f,
                        color = if (Random.nextBoolean()) GoldBright else GoldAmber,
                        life = 1f
                    )
                )
            }

            // Update existing particles
            val iterator = sparkles.listIterator()
            while (iterator.hasNext()) {
                val s = iterator.next()
                s.y -= s.speed * frameTimeMs
                s.life -= (frameTimeMs / 2500f)
                s.alpha = (s.life * 0.6f).coerceIn(0f, 0.6f)
                if (s.life <= 0f) {
                    iterator.remove()
                }
            }

            // Wait for next frame
            val elapsed = (System.nanoTime() - startTime) / 1_000_000
            val sleepTime = (frameTimeMs - elapsed).coerceAtLeast(1)
            kotlinx.coroutines.delay(sleepTime)
        }
    }

    Canvas(modifier = modifier) {
        val dpToPx = 1.dp.toPx()
        for (s in sparkles) {
            drawCircle(
                color = s.color,
                radius = s.radius * dpToPx,
                center = androidx.compose.ui.geometry.Offset(
                    s.x * size.width,
                    s.y * size.height
                ),
                alpha = s.alpha
            )
        }
    }
}
