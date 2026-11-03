package com.sdvsync.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.components.R44BStatePanel
import com.sdvsync.ui.policy.R44BStateKind

/** Synthetic and privacy-safe preview: no account, installation, save or device data. */
@Preview(name = "R44B Earthy • phone", showBackground = true, widthDp = 360)
@Composable
private fun EarthyPhonePreview() = ExperiencePreview(earthy = true, dark = false)

@Preview(name = "R44B Earthy • dark", showBackground = true, widthDp = 420)
@Composable
private fun EarthyNightPreview() = ExperiencePreview(earthy = true, dark = true)

@Preview(name = "R44B Earthy • tablet", showBackground = true, widthDp = 900)
@Composable
private fun EarthyTabletPreview() = ExperiencePreview(earthy = true, dark = false)

@Preview(name = "R44B Classic • phone", showBackground = true, widthDp = 360)
@Composable
private fun ClassicPhonePreview() = ExperiencePreview(earthy = false, dark = false)

@Composable
private fun ExperiencePreview(earthy: Boolean, dark: Boolean) {
    SdvSyncTheme(style = if (earthy) AppearanceStyle.EARTHY else AppearanceStyle.CLASSIC,
        darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                R44BStatePanel(
                    kind = R44BStateKind.LOADING, title = "Checking installation",
                    description = "Reading local status without changing your files.")
                Spacer(Modifier.height(12.dp))
                R44BStatePanel(
                    kind = R44BStateKind.NEEDS_ATTENTION, title = "Review needed",
                    description = "An existing folder needs manual inspection.",
                    actionLabel = "Open Installation Health", onAction = {})
                Spacer(Modifier.height(12.dp))
                R44BStatePanel(
                    kind = R44BStateKind.EMPTY, title = "No activity yet",
                    description = "Your next operation will appear here.")
            }
        }
    }
}
