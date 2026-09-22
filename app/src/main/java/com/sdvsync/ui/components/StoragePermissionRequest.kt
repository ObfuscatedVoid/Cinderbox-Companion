package com.sdvsync.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

@Composable
fun rememberStoragePermissionRequest(onResult: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult() }
    return {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                settings.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                )
            } catch (_: ActivityNotFoundException) {
                settings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacy.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
