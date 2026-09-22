package com.sdvsync.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdvsync.R
import com.sdvsync.ui.components.AppButton
import com.sdvsync.ui.components.AppDialog
import com.sdvsync.ui.components.AppIconButton
import com.sdvsync.ui.components.AppIcons
import com.sdvsync.ui.components.AppOutlinedButton
import com.sdvsync.ui.components.AppTopBar
import com.sdvsync.ui.components.ButtonVariant
import com.sdvsync.ui.components.SaveListContent
import com.sdvsync.ui.viewmodels.DashboardViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSaveClick: (String, Boolean, Boolean) -> Unit,
    onSyncLogClick: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastContext = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.previewImport(uri)
        }
    }

    // Show import result as toast
    LaunchedEffect(state.importResult) {
        val result = state.importResult ?: return@LaunchedEffect
        Toast.makeText(toastContext, result, Toast.LENGTH_LONG).show()
        viewModel.clearImportResult()
    }

    // Handle pending .sdvsync import from intent
    val activity = toastContext as? com.sdvsync.MainActivity
    LaunchedEffect(activity?.pendingImportUri) {
        val pendingUri = activity?.consumePendingImportUri()
        if (pendingUri != null) {
            viewModel.previewImport(pendingUri)
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    // Import preview dialog
    state.importPreview?.let { manifest ->
        val seasonName = when (manifest.save.season) {
            0 -> stringResource(R.string.save_season_spring)
            1 -> stringResource(R.string.save_season_summer)
            2 -> stringResource(R.string.save_season_fall)
            3 -> stringResource(R.string.save_season_winter)
            else -> stringResource(R.string.save_season_unknown)
        }
        AppDialog(
            onDismissRequest = { viewModel.dismissImportPreview() },
            title = stringResource(R.string.import_preview_title),
            text = buildString {
                appendLine(stringResource(R.string.import_preview_farmer, manifest.save.farmerName))
                appendLine(stringResource(R.string.import_preview_farm, manifest.save.farmName))
                appendLine(
                    stringResource(
                        R.string.import_preview_date,
                        seasonName,
                        manifest.save.dayOfMonth,
                        manifest.save.year
                    )
                )
                if (manifest.mods.isNotEmpty()) {
                    appendLine()
                    appendLine(stringResource(R.string.import_preview_mods, manifest.mods.size))
                }
            },
            confirmButton = {
                AppButton(
                    onClick = { viewModel.confirmImport() },
                    variant = ButtonVariant.Primary
                ) {
                    Text(stringResource(R.string.import_confirm))
                }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { viewModel.dismissImportPreview() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.dashboard_title),
                actions = {
                    AppIconButton(
                        imageVector = AppIcons.Import,
                        onClick = {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                        contentDescription = stringResource(R.string.import_button),
                        enabled = !state.isImporting
                    )
                    AppIconButton(
                        imageVector = AppIcons.Refresh,
                        onClick = { viewModel.refresh(isUserRefresh = true) },
                        contentDescription = stringResource(R.string.action_refresh),
                        enabled = !state.isLoading && !state.isRefreshing
                    )
                    AppIconButton(
                        imageVector = AppIcons.History,
                        onClick = onSyncLogClick,
                        contentDescription = stringResource(R.string.sync_log_title)
                    )
                }
            )
        }
    ) { padding ->
        SaveListContent(
            state = state,
            onRefresh = { viewModel.refresh(isUserRefresh = true) },
            onSaveClick = onSaveClick,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}
