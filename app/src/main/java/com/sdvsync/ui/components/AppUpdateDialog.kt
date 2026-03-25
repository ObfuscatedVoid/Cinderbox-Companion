package com.sdvsync.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sdvsync.R
import com.sdvsync.download.GitHubReleaseInfo
import com.sdvsync.ui.formatBytes
import com.sdvsync.ui.viewmodels.AppUpdateState

@Composable
fun AppUpdateDialog(
    updateInfo: GitHubReleaseInfo,
    state: AppUpdateState,
    onDismiss: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkipVersion: () -> Unit,
    onUpdate: () -> Unit,
    onInstallPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val scale = remember { Animatable(0.92f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
        )
    }

    // Re-check install permission on resume
    if (state.showInstallPermissionPrompt) {
        LifecycleResumeEffect(Unit) {
            onInstallPermissionGranted()
            onPauseOrDispose {}
        }
    }

    Dialog(onDismissRequest = { if (!state.isDownloading) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pixelBorder()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Title row
                Text(
                    stringResource(R.string.update_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.update_version_size, updateInfo.version, formatBytes(updateInfo.assetSize)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                // Changelog
                if (updateInfo.body.isNotBlank()) {
                    Text(
                        stringResource(R.string.update_whats_new),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
                    val html = markdownToHtml(updateInfo.body)
                    HtmlText(
                        html = html,
                        textColor = textColor,
                        linkColor = linkColor,
                        textSizeSp = 13f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Progress bar (visible while downloading)
                if (state.isDownloading) {
                    Text(
                        stringResource(R.string.update_downloading, (state.downloadProgress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    PixelProgressBar(progress = state.downloadProgress, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }

                // Error message
                if (state.downloadError != null) {
                    Text(
                        state.downloadError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Buttons
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isDownloading) {
                        TextButton(onClick = onCancelDownload) {
                            Text(stringResource(R.string.update_cancel))
                        }
                    } else {
                        TextButton(onClick = onSkipVersion) {
                            Text(
                                stringResource(R.string.update_skip_version),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        StardewButton(onClick = onDismiss) {
                            Text(stringResource(R.string.update_later))
                        }
                        Spacer(Modifier.width(8.dp))
                        when {
                            state.downloadError != null -> {
                                StardewButton(onClick = onUpdate, variant = StardewButtonVariant.Action) {
                                    Text(stringResource(R.string.update_retry))
                                }
                            }
                            state.showInstallPermissionPrompt -> {
                                StardewButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    variant = StardewButtonVariant.Action
                                ) {
                                    Text(stringResource(R.string.update_allow_installs))
                                }
                            }
                            else -> {
                                StardewButton(onClick = onUpdate, variant = StardewButtonVariant.Action) {
                                    Text(stringResource(R.string.update_button))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-install when APK is ready
    val downloadedApk = state.downloadedApk
    if (downloadedApk != null) {
        LaunchedEffect(downloadedApk) {
            installCinderboxApk(context, downloadedApk)
            onDismiss()
        }
    }
}
