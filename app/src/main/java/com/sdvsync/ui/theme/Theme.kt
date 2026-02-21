package com.sdvsync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SteamBlue,
    onPrimary = White,
    primaryContainer = SteamBlueDark,
    secondary = StardewGreen,
    onSecondary = White,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = White,
    onSurface = White,
    error = ErrorRed,
)

private val LightColorScheme = lightColorScheme(
    primary = SteamBlue,
    onPrimary = White,
    primaryContainer = SteamBlueLight,
    secondary = StardewGreen,
    onSecondary = White,
    background = LightBackground,
    surface = LightSurface,
    onBackground = DarkBackground,
    onSurface = DarkBackground,
    error = ErrorRed,
)

@Composable
fun SdvSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
