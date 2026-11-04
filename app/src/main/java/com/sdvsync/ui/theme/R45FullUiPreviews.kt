package com.sdvsync.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdvsync.ui.components.R45SectionIntro
import com.sdvsync.ui.components.R45AreaGuide
import com.sdvsync.ui.policy.R45GuideArea
import com.sdvsync.ui.components.R46ErrorPanel
import com.sdvsync.ui.policy.R46UiErrorArea

/** Synthetic privacy-safe layout sample: no network, accounts, paths or file operations. */
@Preview(name = "R45 — Earthy • daylight", showBackground = true, widthDp = 360)
@Composable
private fun Daylight() = Sample(appearance = AppearanceStyle.EARTHY, dark = false)

@Preview(name = "R45 — Earthy • nighttime", showBackground = true, widthDp = 360)
@Composable
private fun Nighttime() = Sample(appearance = AppearanceStyle.EARTHY, dark = true)

@Preview(name = "R45 — Classic • phone", showBackground = true, widthDp = 320)
@Composable
private fun ClassicPhone() = Sample(appearance = AppearanceStyle.CLASSIC, dark = false)

@Preview(name = "R45 — Earthy • tablet", showBackground = true, widthDp = 840)
@Composable
private fun EarthyTablet() = Sample(appearance = AppearanceStyle.EARTHY, dark = false)

@Composable
private fun Sample(appearance: AppearanceStyle, dark: Boolean) {
    SdvSyncTheme(style = appearance, darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                R45SectionIntro("Saves", "View saves and check local/cloud status.")
                R46ErrorPanel(R46UiErrorArea.SAVES, onRetry = {})
            }
        }
    }
}

/** Additional synthetic previews cover the new user-facing Game and authentication states. */
@Preview(name = "R45 — Game overview", showBackground = true, widthDp = 360)
@Composable
private fun GameOverview() {
    SdvSyncTheme(style = AppearanceStyle.EARTHY, darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                R45AreaGuide(R45GuideArea.GAME)
                R46ErrorPanel(R46UiErrorArea.GAME_DOWNLOAD)
            }
        }
    }
}

@Preview(name = "R45 — Mods overview", showBackground = true, widthDp = 360)
@Composable
private fun ModsOverview() {
    SdvSyncTheme(style = AppearanceStyle.EARTHY, darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                R45AreaGuide(R45GuideArea.MODS)
                R46ErrorPanel(R46UiErrorArea.MODS, onRetry = {})
            }
        }
    }
}

@Preview(name = "R45 — More overview", showBackground = true, widthDp = 840)
@Composable
private fun MoreOverview() {
    SdvSyncTheme(style = AppearanceStyle.EARTHY, darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                R45AreaGuide(R45GuideArea.MORE)
                R46ErrorPanel(R46UiErrorArea.STEAM_LOGIN)
            }
        }
    }
}
