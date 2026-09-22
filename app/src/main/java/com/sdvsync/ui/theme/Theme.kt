package com.sdvsync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ForestGreenLight,
    onPrimary = Color.White,
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = OnGreenContainerLight,
    secondary = ForestGreenLight,
    onSecondary = Color.White,
    secondaryContainer = GreenContainerLight,
    onSecondaryContainer = OnGreenContainerLight,
    tertiary = Color(0xFF795A25),
    onTertiary = Color.White,
    tertiaryContainer = GoldContainerLight,
    onTertiaryContainer = OnGoldContainerLight,
    background = WarmWhite,
    onBackground = OnWarmWhite,
    surface = ParchmentLight,
    onSurface = OnWarmWhite,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F5F0),
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = Color(0xFFE2E6DE),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    inverseSurface = Color(0xFF352D24),
    inverseOnSurface = Color(0xFFFFF0E0),
    inversePrimary = BrownDark,
    scrim = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenDark,
    onPrimary = Color(0xFF102E22),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = OnGreenContainerDark,
    secondary = ForestGreenDark,
    onSecondary = Color(0xFF1A3800),
    secondaryContainer = GreenContainerDark,
    onSecondaryContainer = OnGreenContainerDark,
    tertiary = GoldAmber,
    onTertiary = Color(0xFF3D2800),
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = OnGoldContainerDark,
    background = WarmBlack,
    onBackground = OnWarmBlack,
    surface = ParchmentDark,
    onSurface = OnWarmBlack,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = Color(0xFF101410),
    surfaceContainerLow = Color(0xFF19201B),
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = Color(0xFF303B33),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    inverseSurface = Color(0xFFEDE0D0),
    inverseOnSurface = Color(0xFF352D24),
    inversePrimary = BrownLight,
    scrim = Color.Black
)

@Immutable
data class StardewColors(
    val pullBlue: Color,
    val pushGreen: Color,
    val synced: Color,
    val conflict: Color,
    val syncError: Color,
    val seasonSpring: Color,
    val seasonSummer: Color,
    val seasonFall: Color,
    val seasonWinter: Color
)

val LocalStardewColors = staticCompositionLocalOf {
    StardewColors(
        pullBlue = SyncPullBlue,
        pushGreen = SyncPushGreen,
        synced = SyncSynced,
        conflict = SyncConflict,
        syncError = SyncError,
        seasonSpring = SeasonSpring,
        seasonSummer = SeasonSummer,
        seasonFall = SeasonFall,
        seasonWinter = SeasonWinter
    )
}

private val LightStardewColors = StardewColors(
    pullBlue = SyncPullBlue,
    pushGreen = SyncPushGreen,
    synced = SyncSynced,
    conflict = SyncConflict,
    syncError = SyncError,
    seasonSpring = SeasonSpring,
    seasonSummer = SeasonSummer,
    seasonFall = SeasonFall,
    seasonWinter = SeasonWinter
)

private val DarkStardewColors = StardewColors(
    pullBlue = Color(0xFF8FC9ED),
    pushGreen = Color(0xFFA2CFAB),
    synced = Color(0xFFA2CFAB),
    conflict = Color(0xFFF3C77E),
    syncError = Color(0xFFFFB4AB),
    seasonSpring = Color(0xFFE4AEC4),
    seasonSummer = Color(0xFFE4CC88),
    seasonFall = Color(0xFFF0B484),
    seasonWinter = Color(0xFF99D2E4)
)

val StardewShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

object SdvSyncThemeExtras {
    val colors: StardewColors
        @Composable
        get() = LocalStardewColors.current
}

@Composable
fun SdvSyncTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val stardewColors = if (darkTheme) DarkStardewColors else LightStardewColors

    CompositionLocalProvider(
        LocalStardewColors provides stardewColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = StardewShapes,
            content = content
        )
    }
}
