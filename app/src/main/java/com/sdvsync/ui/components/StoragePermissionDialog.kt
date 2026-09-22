package com.sdvsync.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sdvsync.R
import com.sdvsync.fileaccess.AllFilesAccess

@Composable
fun StoragePermissionDialog(onPermissionResult: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val requestPermission = rememberStoragePermissionRequest {
        if (AllFilesAccess.isPermissionGranted(context)) onPermissionResult()
    }
    LifecycleResumeEffect(Unit) {
        if (AllFilesAccess.isPermissionGranted(context)) onPermissionResult()
        onPauseOrDispose {}
    }
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.storage_permission_title),
        text = stringResource(R.string.storage_permission_description),
        confirmButton = {
            AppButton(onClick = requestPermission) { Text(stringResource(R.string.storage_permission_grant)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
