package com.sdvsync.ui.components

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdvsync.sync.SyncDirection
import com.sdvsync.ui.theme.GoldAmber
import com.sdvsync.ui.theme.GoldBright
import com.sdvsync.ui.theme.SeasonFall
import com.sdvsync.ui.theme.SeasonSpring
import com.sdvsync.ui.theme.SeasonSummer
import com.sdvsync.ui.theme.SeasonWinter
import com.sdvsync.ui.theme.SyncConflict
import com.sdvsync.ui.theme.SyncPullBlue
import com.sdvsync.ui.theme.SyncPushGreen
import com.sdvsync.ui.theme.SyncSynced

// ── Spring: Tulip/flower ────────────────────────────────────────────────
// Palette: 1=stem green, 2=SeasonSpring pink, 3=GoldBright center
private val SpringPalette = listOf(
    Color.Transparent,
    Color(0xFF3D8B20), // stem green
    SeasonSpring,      // pink petals
    GoldBright,        // center
)

private val SpringData = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 2, 2, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 2, 2, 2, 2, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 2, 2, 3, 3, 2, 2, 0, 0, 0),
    intArrayOf(0, 0, 2, 2, 3, 3, 3, 3, 2, 2, 0, 0),
    intArrayOf(0, 0, 2, 2, 3, 3, 3, 3, 2, 2, 0, 0),
    intArrayOf(0, 0, 0, 2, 2, 3, 3, 2, 2, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 2, 2, 2, 2, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
)

// ── Summer: Sun ─────────────────────────────────────────────────────────
// Palette: 1=GoldBright, 2=SeasonSummer blue highlight
private val SummerPalette = listOf(
    Color.Transparent,
    GoldBright,
    SeasonSummer,
)

private val SummerData = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(1, 1, 0, 1, 1, 1, 2, 1, 1, 0, 1, 1),
    intArrayOf(1, 1, 0, 1, 1, 2, 1, 1, 1, 0, 1, 1),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
)

// ── Fall: Maple leaf ────────────────────────────────────────────────────
// Palette: 1=SeasonFall orange, 2=darker orange, 3=brown stem
private val FallPalette = listOf(
    Color.Transparent,
    SeasonFall,
    Color(0xFFC06820), // darker orange
    Color(0xFF5A3A1E), // brown stem
)

private val FallData = arrayOf(
    intArrayOf(0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0),
    intArrayOf(0, 0, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0),
    intArrayOf(0, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 0),
    intArrayOf(0, 1, 2, 1, 1, 1, 1, 1, 1, 2, 1, 0),
    intArrayOf(1, 1, 1, 1, 2, 1, 1, 2, 1, 1, 1, 1),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(0, 0, 1, 1, 1, 2, 2, 1, 1, 1, 0, 0),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(1, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 1),
    intArrayOf(0, 0, 1, 1, 1, 3, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 3, 3, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0),
)

// ── Winter: Snowflake ───────────────────────────────────────────────────
// Palette: 1=SeasonWinter ice blue, 2=white
private val WinterPalette = listOf(
    Color.Transparent,
    SeasonWinter,
    Color.White,
)

private val WinterData = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 1, 0, 0, 2, 2, 0, 0, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 2, 2, 1, 1, 0, 0, 0),
    intArrayOf(1, 2, 1, 1, 2, 2, 2, 2, 1, 1, 2, 1),
    intArrayOf(1, 2, 1, 1, 2, 2, 2, 2, 1, 1, 2, 1),
    intArrayOf(0, 0, 0, 1, 1, 2, 2, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 0, 2, 2, 0, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
)

// ── Pull: Cloud + down arrow ────────────────────────────────────────────
// Palette: 1=SyncPullBlue, 2=white
private val PullPalette = listOf(
    Color.Transparent,
    SyncPullBlue,
    Color.White,
)

private val PullData = arrayOf(
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 1, 2, 2, 2, 2, 1, 0, 0, 0, 0),
    intArrayOf(0, 1, 2, 2, 2, 2, 2, 2, 1, 1, 0, 0),
    intArrayOf(1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0),
    intArrayOf(1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
)

// ── Push: Cloud + up arrow ──────────────────────────────────────────────
// Palette: 1=SyncPushGreen, 2=white
private val PushPalette = listOf(
    Color.Transparent,
    SyncPushGreen,
    Color.White,
)

private val PushData = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 1, 2, 2, 2, 2, 1, 0, 0, 0, 0),
    intArrayOf(0, 1, 2, 2, 2, 2, 2, 2, 1, 1, 0, 0),
    intArrayOf(1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0),
    intArrayOf(1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
)

// ── Synced: Checkmark in circle ─────────────────────────────────────────
// Palette: 1=SyncSynced green, 2=white
private val SyncedPalette = listOf(
    Color.Transparent,
    SyncSynced,
    Color.White,
)

private val SyncedData = arrayOf(
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1),
    intArrayOf(1, 1, 2, 1, 1, 1, 2, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 2, 1, 2, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0),
)

// ── Conflict: Exclamation in triangle ───────────────────────────────────
// Palette: 1=SyncConflict orange, 2=dark, 3=white
private val ConflictPalette = listOf(
    Color.Transparent,
    SyncConflict,
    Color(0xFF3D1E00), // dark
    Color.White,
)

private val ConflictData = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 1, 2, 2, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 2, 2, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 2, 2, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 1, 1, 1, 2, 2, 1, 1, 1, 0, 0),
    intArrayOf(0, 0, 1, 1, 1, 2, 2, 1, 1, 1, 0, 0),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(0, 1, 1, 1, 1, 2, 2, 1, 1, 1, 1, 0),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
)

// ── Chicken sprite (8x8) ────────────────────────────────────────────────
// Palette: 1=white body, 2=red comb, 3=orange beak, 4=dark eye
private val ChickenPalette = listOf(
    Color.Transparent,
    Color.White,
    Color(0xFFE03030), // red comb
    Color(0xFFE8A030), // orange beak
    Color(0xFF2A1A00), // dark eye/feet
)

val ChickenIconData = arrayOf(
    intArrayOf(0, 0, 2, 2, 0, 0, 0, 0),
    intArrayOf(0, 2, 2, 2, 0, 0, 0, 0),
    intArrayOf(0, 1, 1, 4, 1, 0, 0, 0),
    intArrayOf(3, 3, 1, 1, 1, 1, 0, 0),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(0, 0, 1, 1, 1, 1, 0, 0),
    intArrayOf(0, 0, 4, 0, 0, 4, 0, 0),
)

// ── Star sparkle (8x8) ─────────────────────────────────────────────────
// Palette: 1=GoldBright, 2=GoldAmber
private val StarPalette = listOf(
    Color.Transparent,
    GoldBright,
    GoldAmber,
)

val StarIconData = arrayOf(
    intArrayOf(0, 0, 0, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 2, 1, 2, 0, 0, 0),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(0, 0, 2, 1, 2, 0, 0, 0),
    intArrayOf(0, 0, 1, 0, 1, 0, 0, 0),
    intArrayOf(0, 1, 0, 0, 0, 1, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
)

// ── Arrow Left (8x8) — back navigation ────────────────────────────────
// Palette: 1=icon color (provided at render time)
val ArrowLeftData = arrayOf(
    intArrayOf(0, 0, 0, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 1, 1, 0, 0, 0, 0),
    intArrayOf(0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(0, 1, 1, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 1, 1, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 0, 0, 0, 0),
)

// ── Refresh (10x10) — circular arrows ─────────────────────────────────
val RefreshData = arrayOf(
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 1, 0, 0, 0, 0, 1, 0, 1),
    intArrayOf(0, 1, 0, 0, 0, 0, 0, 0, 1, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1, 1),
    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 1, 0, 0, 0, 0, 0, 0, 0, 1),
    intArrayOf(1, 1, 0, 0, 0, 0, 0, 0, 1, 0),
    intArrayOf(1, 0, 1, 0, 0, 0, 0, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
)

// ── Clock (10x10) — history/time icon ─────────────────────────────────
val ClockData = arrayOf(
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 1, 1, 1, 1, 1, 1, 0, 0),
    intArrayOf(0, 1, 1, 0, 0, 0, 0, 1, 1, 0),
    intArrayOf(1, 1, 0, 0, 1, 0, 0, 0, 1, 1),
    intArrayOf(1, 1, 0, 0, 1, 0, 0, 0, 1, 1),
    intArrayOf(1, 1, 0, 0, 1, 1, 1, 0, 1, 1),
    intArrayOf(1, 1, 0, 0, 0, 0, 0, 0, 1, 1),
    intArrayOf(0, 1, 1, 0, 0, 0, 0, 1, 1, 0),
    intArrayOf(0, 0, 1, 1, 1, 1, 1, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
)

// ── Gear (10x10) — settings icon ──────────────────────────────────────
val GearData = arrayOf(
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 1, 1, 0, 0, 1, 1, 0, 0),
    intArrayOf(0, 1, 1, 0, 0, 0, 0, 1, 1, 0),
    intArrayOf(1, 1, 0, 0, 1, 1, 0, 0, 1, 1),
    intArrayOf(1, 0, 0, 1, 1, 1, 1, 0, 0, 1),
    intArrayOf(1, 0, 0, 1, 1, 1, 1, 0, 0, 1),
    intArrayOf(1, 1, 0, 0, 1, 1, 0, 0, 1, 1),
    intArrayOf(0, 1, 1, 0, 0, 0, 0, 1, 1, 0),
    intArrayOf(0, 0, 1, 1, 0, 0, 1, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
)

// ── Trash (10x10) — delete icon ───────────────────────────────────────
val TrashData = arrayOf(
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 0),
    intArrayOf(0, 0, 1, 1, 1, 1, 1, 1, 0, 0),
    intArrayOf(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
    intArrayOf(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
    intArrayOf(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
    intArrayOf(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
    intArrayOf(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
    intArrayOf(0, 0, 1, 1, 1, 1, 1, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
)

// ── QR Code (10x10) — simplified QR icon ──────────────────────────────
val QrCodeData = arrayOf(
    intArrayOf(1, 1, 1, 0, 0, 0, 0, 1, 1, 1),
    intArrayOf(1, 0, 1, 0, 1, 0, 0, 1, 0, 1),
    intArrayOf(1, 1, 1, 0, 0, 1, 0, 1, 1, 1),
    intArrayOf(0, 0, 0, 0, 1, 0, 1, 0, 0, 0),
    intArrayOf(0, 1, 0, 1, 0, 1, 0, 0, 1, 0),
    intArrayOf(0, 0, 1, 0, 1, 0, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 0, 1, 0, 0, 0, 0),
    intArrayOf(1, 1, 1, 0, 0, 0, 0, 1, 0, 1),
    intArrayOf(1, 0, 1, 0, 1, 0, 0, 0, 1, 0),
    intArrayOf(1, 1, 1, 0, 0, 1, 0, 1, 0, 1),
)

// ── Eye Open (10x10) — password visibility ────────────────────────────
val EyeOpenData = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 1, 0, 0, 0, 0, 1, 0, 0),
    intArrayOf(0, 1, 0, 0, 1, 1, 0, 0, 1, 0),
    intArrayOf(0, 1, 0, 0, 1, 1, 0, 0, 1, 0),
    intArrayOf(0, 0, 1, 0, 0, 0, 0, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
)

// ── Eye Closed (10x10) — password hidden ──────────────────────────────
val EyeClosedData = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 0, 0),
    intArrayOf(0, 0, 1, 0, 0, 0, 1, 0, 0, 0),
    intArrayOf(0, 1, 0, 0, 1, 1, 0, 0, 1, 0),
    intArrayOf(0, 1, 0, 1, 1, 0, 0, 0, 1, 0),
    intArrayOf(0, 0, 1, 0, 0, 0, 0, 1, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 1, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
)

// ── Barn (16x12) — pixel farm scene for login ─────────────────────────
// Palette: 1=brown wall, 2=red roof, 3=green ground, 4=gold star, 5=dark door, 6=lighter brown
val BarnPalette = listOf(
    Color.Transparent,
    Color(0xFF8B5E3C), // brown wall
    Color(0xFFC03020), // red roof
    Color(0xFF4CAF50), // green ground
    GoldBright,        // gold star
    Color(0xFF3D1E00), // dark door
    Color(0xFFA87048), // lighter brown
)

val BarnData = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 0, 4, 4, 4, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0),
    intArrayOf(0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 6, 6, 6, 6, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 6, 6, 6, 6, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 5, 5, 5, 5, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 5, 5, 5, 5, 1, 1, 1, 0, 0, 0),
    intArrayOf(0, 0, 0, 1, 1, 1, 5, 5, 5, 5, 1, 1, 1, 0, 0, 0),
    intArrayOf(3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
    intArrayOf(3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
)

// ── Convenience composables ─────────────────────────────────────────────

/**
 * Pixel art icon button for toolbars.
 * Uses a single-color palette (color index 1).
 */
@Composable
fun PixelIconButton(
    pixelData: Array<IntArray>,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 20.dp,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        PixelIcon(
            pixelData = pixelData,
            palette = listOf(Color.Transparent, tint),
            size = size,
        )
    }
}

@Composable
fun PixelSeasonIcon(
    season: Int,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    when (season) {
        0 -> PixelIcon(SpringData, SpringPalette, modifier, size)
        1 -> PixelIcon(SummerData, SummerPalette, modifier, size)
        2 -> PixelIcon(FallData, FallPalette, modifier, size)
        3 -> PixelIcon(WinterData, WinterPalette, modifier, size)
    }
}

@Composable
fun PixelSyncIcon(
    direction: SyncDirection,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    when (direction) {
        SyncDirection.PULL -> PixelIcon(PullData, PullPalette, modifier, size)
        SyncDirection.PUSH -> PixelIcon(PushData, PushPalette, modifier, size)
        SyncDirection.SKIP -> PixelIcon(SyncedData, SyncedPalette, modifier, size)
        SyncDirection.CONFLICT -> PixelIcon(ConflictData, ConflictPalette, modifier, size)
    }
}

@Composable
fun PixelSyncLogIcon(
    direction: String,
    success: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    if (!success) {
        // Error state: show conflict icon
        PixelIcon(ConflictData, ConflictPalette, modifier, size)
    } else if (direction == "pull") {
        PixelIcon(PullData, PullPalette, modifier, size)
    } else {
        PixelIcon(PushData, PushPalette, modifier, size)
    }
}
