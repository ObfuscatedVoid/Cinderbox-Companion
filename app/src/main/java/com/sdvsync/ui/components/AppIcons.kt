package com.sdvsync.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdvsync.sync.SyncDirection

object AppIcons {
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val Refresh = Icons.Outlined.Refresh
    val History = Icons.Outlined.History
    val Settings = Icons.Outlined.Settings
    val Delete = Icons.Outlined.DeleteOutline
    val QrCode = Icons.Outlined.QrCode2
    val Visibility = Icons.Outlined.Visibility
    val VisibilityOff = Icons.Outlined.VisibilityOff
    val Saves = Icons.Outlined.Save
    val Mods = Icons.Outlined.Extension
    val Search = Icons.Outlined.Search
    val Folder = Icons.Outlined.FolderOpen
    val Update = Icons.Outlined.SystemUpdateAlt
    val Download = Icons.Outlined.Download
    val Import = Icons.Outlined.FileOpen
    val Home = Icons.Outlined.Cottage
}

@Composable
fun AppIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    Icon(imageVector, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
fun AppIconButton(
    imageVector: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    enabled: Boolean = true
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(imageVector, contentDescription, Modifier.size(size), tint = tint)
    }
}

@Composable
fun SeasonIcon(season: Int, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    AppIcon(
        when (season) {
            0 -> Icons.Outlined.LocalFlorist
            1 -> Icons.Outlined.WbSunny
            2 -> Icons.Outlined.Eco
            else -> Icons.Outlined.AcUnit
        },
        modifier,
        tint = seasonColor(season),
        size = size
    )
}

@Composable
fun SyncIcon(direction: SyncDirection, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    AppIcon(
        when (direction) {
            SyncDirection.PULL -> Icons.Outlined.CloudDownload
            SyncDirection.PUSH -> Icons.Outlined.CloudUpload
            SyncDirection.SKIP -> Icons.Outlined.CheckCircleOutline
            SyncDirection.CONFLICT -> Icons.Outlined.SyncProblem
        },
        modifier,
        size = size
    )
}

@Composable
fun SyncLogIcon(direction: String, success: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    SyncIcon(
        if (!success) {
            SyncDirection.CONFLICT
        } else if (direction == "pull") {
            SyncDirection.PULL
        } else {
            SyncDirection.PUSH
        },
        modifier,
        size
    )
}
