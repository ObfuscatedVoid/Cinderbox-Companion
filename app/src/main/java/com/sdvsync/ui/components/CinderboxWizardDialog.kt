package com.sdvsync.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sdvsync.R
import com.sdvsync.download.CinderboxDownloadProgress
import com.sdvsync.ui.formatBytes
import com.sdvsync.ui.theme.GoldAmber
import com.sdvsync.ui.theme.SuccessGreen
import com.sdvsync.ui.viewmodels.CinderboxWizardState
import com.sdvsync.ui.viewmodels.CinderboxWizardStep
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal fun installCinderboxApk(context: Context, apkFile: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Failed to launch installer: ${e.message}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
fun CinderboxWizardDialog(
    wizardState: CinderboxWizardState,
    cinderboxProgress: CinderboxDownloadProgress,
    onChooseCinderbox: () -> Unit,
    onChooseOther: () -> Unit,
    onRefreshPermissions: (hasStorage: Boolean, hasInstall: Boolean) -> Unit,
    onApkDownloadComplete: () -> Unit,
    onApkInstalled: () -> Unit,
    onCheckDirectory: () -> Boolean,
    onSkipVerification: () -> Unit,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!wizardState.isVisible) return

    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Permission launchers — LifecycleResumeEffect handles refresh on return
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* refresh handled by LifecycleResumeEffect */ }

    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* refresh handled by LifecycleResumeEffect */ }

    // Auto-refresh permissions on resume (for when user returns from Settings)
    if (wizardState.currentStep == CinderboxWizardStep.PERMISSIONS) {
        LifecycleResumeEffect(Unit) {
            onRefreshPermissions(
                Environment.isExternalStorageManager(),
                context.packageManager.canRequestPackageInstalls()
            )
            onPauseOrDispose {}
        }
    }

    // Auto-advance from download to install when complete
    LaunchedEffect(cinderboxProgress.completed, wizardState.currentStep) {
        if (cinderboxProgress.completed &&
            wizardState.currentStep == CinderboxWizardStep.DOWNLOAD_APK
        ) {
            onApkDownloadComplete()
        }
    }

    // Auto-poll directory existence in verify step
    LaunchedEffect(wizardState.currentStep) {
        if (wizardState.currentStep == CinderboxWizardStep.VERIFY_LAUNCHED) {
            while (true) {
                coroutineContext.ensureActive()
                if (onCheckDirectory()) break
                delay(3000)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.85f)
                .pixelBorder(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AnimatedContent(
                    targetState = wizardState.currentStep,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 3 })
                            .togetherWith(fadeOut() + slideOutHorizontally { -it / 3 })
                    },
                    label = "wizardStep"
                ) { step ->
                    when (step) {
                        CinderboxWizardStep.SETUP_CHOICE -> SetupChoiceStep(
                            onChooseCinderbox = onChooseCinderbox,
                            onChooseOther = onChooseOther
                        )

                        CinderboxWizardStep.PERMISSIONS -> PermissionsStep(
                            hasStorage = wizardState.hasStoragePermission,
                            hasInstall = wizardState.hasInstallPermission,
                            onGrantStorage = {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                storageLauncher.launch(intent)
                            },
                            onGrantInstall = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}")
                                )
                                installLauncher.launch(intent)
                            }
                        )

                        CinderboxWizardStep.DOWNLOAD_APK -> DownloadApkStep(
                            cinderboxProgress = cinderboxProgress
                        )

                        CinderboxWizardStep.INSTALL_APK -> InstallApkStep(
                            apkFile = cinderboxProgress.apkFile,
                            onInstall = { file ->
                                installCinderboxApk(context, file)
                            },
                            onInstalled = onApkInstalled
                        )

                        CinderboxWizardStep.VERIFY_LAUNCHED -> VerifyLaunchedStep(
                            onCheckAgain = {
                                if (!onCheckDirectory()) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Cinderbox folder not found yet. Make sure you've opened Cinderbox at least once.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onSkip = onSkipVerification
                        )

                        CinderboxWizardStep.READY -> ReadyStep(
                            onStartDownload = onStartDownload
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupChoiceStep(onChooseCinderbox: () -> Unit, onChooseOther: () -> Unit) {
    Column {
        Text(
            stringResource(R.string.wizard_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        // Cinderbox option — gold highlighted card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, GoldAmber, RectangleShape)
                .clickable(onClick = onChooseCinderbox),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.wizard_cinderbox_option),
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldAmber,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.wizard_cinderbox_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Other option — outlined card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RectangleShape
                )
                .clickable(onClick = onChooseOther),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.wizard_other_option),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.wizard_other_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    hasStorage: Boolean,
    hasInstall: Boolean,
    onGrantStorage: () -> Unit,
    onGrantInstall: () -> Unit
) {
    Column {
        Text(
            stringResource(R.string.wizard_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        // Storage permission
        PermissionRow(
            title = stringResource(R.string.wizard_storage_permission),
            description = stringResource(R.string.wizard_storage_permission_desc),
            granted = hasStorage,
            onGrant = onGrantStorage
        )

        Spacer(Modifier.height(12.dp))

        // Install permission
        PermissionRow(
            title = stringResource(R.string.wizard_install_permission),
            description = stringResource(R.string.wizard_install_permission_desc),
            granted = hasInstall,
            onGrant = onGrantInstall
        )
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onGrant: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Text(
                    stringResource(R.string.wizard_granted),
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen,
                    fontWeight = FontWeight.Bold
                )
            } else {
                StardewButton(
                    onClick = onGrant,
                    variant = StardewButtonVariant.Action
                ) {
                    Text(stringResource(R.string.wizard_grant_button))
                }
            }
        }
    }
}

@Composable
private fun DownloadApkStep(cinderboxProgress: CinderboxDownloadProgress) {
    Column {
        Text(
            stringResource(R.string.wizard_download_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        if (cinderboxProgress.isDownloading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelLoadingSpinner(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.cinderbox_downloading),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(
                    R.string.cinderbox_download_progress,
                    formatBytes(cinderboxProgress.downloadedBytes),
                    if (cinderboxProgress.totalBytes > 0) {
                        formatBytes(cinderboxProgress.totalBytes)
                    } else {
                        "?"
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            PixelProgressBar(progress = cinderboxProgress.percent)
            Spacer(Modifier.height(4.dp))
            Text(
                "${(cinderboxProgress.percent * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (cinderboxProgress.errorMessage != null) {
            Text(
                stringResource(R.string.cinderbox_download_error, cinderboxProgress.errorMessage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            // Waiting to start
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                PixelLoadingSpinner()
            }
        }
    }
}

@Composable
private fun InstallApkStep(apkFile: File?, onInstall: (File) -> Unit, onInstalled: () -> Unit) {
    Column {
        Text(
            stringResource(R.string.wizard_install_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.wizard_install_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        StardewButton(
            onClick = { apkFile?.let { onInstall(it) } },
            variant = StardewButtonVariant.Action,
            modifier = Modifier.fillMaxWidth(),
            enabled = apkFile != null
        ) {
            Text(stringResource(R.string.wizard_install_button))
        }

        Spacer(Modifier.height(8.dp))

        StardewOutlinedButton(
            onClick = onInstalled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wizard_installed_button))
        }
    }
}

@Composable
private fun VerifyLaunchedStep(onCheckAgain: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.wizard_verify_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.wizard_verify_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        PixelLoadingSpinner(size = 40.dp)

        Spacer(Modifier.height(16.dp))

        StardewOutlinedButton(
            onClick = { onCheckAgain() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wizard_check_again))
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onSkip) {
            Text(
                stringResource(R.string.wizard_skip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadyStep(onStartDownload: () -> Unit) {
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.wizard_ready_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.wizard_ready_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            StardewButton(
                onClick = onStartDownload,
                variant = StardewButtonVariant.Gold,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.wizard_start_download))
            }
        }

        // Sparkle overlay on top
        SparkleOverlay(
            modifier = Modifier
                .matchParentSize(),
            particleCount = 5
        )
    }
}
