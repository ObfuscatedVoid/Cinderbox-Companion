package com.sdvsync.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.ui.theme.SdvSyncThemeExtras

enum class BottomTab(val route: String, val labelRes: Int) {
    SAVES("saves", R.string.tab_saves),
    MODS("mods", R.string.tab_mods),
    DOWNLOAD("game_download", R.string.tab_download),
    SETTINGS("settings", R.string.tab_settings),
}

@Composable
fun StardewBottomBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColors = SdvSyncThemeExtras.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val px = 2.dp.toPx()
                val plank = borderColors.pixelBorderPlank
                val highlight = borderColors.pixelBorderHighlight

                // Alternating dither pattern along the top edge (mirror of TopAppBar)
                var x = 0f
                var useHighlight = true
                while (x < size.width) {
                    val color = if (useHighlight) highlight else plank
                    drawRect(
                        color = color,
                        topLeft = Offset(x, 0f),
                        size = Size(px, px),
                    )
                    x += px
                    useHighlight = !useHighlight
                }
            },
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                BottomTabItem(
                    tab = tab,
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val iconData = when (tab) {
        BottomTab.SAVES -> WheatData
        BottomTab.MODS -> PuzzleData
        BottomTab.DOWNLOAD -> DownloadData
        BottomTab.SETTINGS -> GearData
    }

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelIcon(
            pixelData = iconData,
            palette = listOf(Color.Transparent, tint),
            size = 20.dp,
        )
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
