package com.sdvsync.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.ui.animation.PulseOnChange

@Composable
fun UpdateBanner(updateCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PulseOnChange(key = updateCount) {
        StardewCard(
            onClick = onClick,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelIcon(
                    pixelData = UpdateArrowData,
                    palette = listOf(Color.Transparent, MaterialTheme.colorScheme.tertiary),
                    size = 20.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (updateCount == 1) {
                        stringResource(R.string.mods_update_single)
                    } else {
                        stringResource(R.string.mods_update_banner, updateCount)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
