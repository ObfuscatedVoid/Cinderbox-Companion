package com.sdvsync.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.ui.animation.StaggeredAnimatedItem
import com.sdvsync.ui.components.ClockData
import com.sdvsync.ui.components.EmptyState
import com.sdvsync.ui.components.ImportData
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.RefreshData
import com.sdvsync.ui.components.SaveCard
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewDialog
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.components.R45SectionIntro
import com.sdvsync.ui.components.R46ErrorPanel
import com.sdvsync.ui.components.R45AreaGuide
import com.sdvsync.ui.components.R44BStatePanel
import com.sdvsync.ui.policy.R44BStateKind
import com.sdvsync.ui.policy.R45GuideArea
import com.sdvsync.ui.theme.SdvSyncThemeExtras
import com.sdvsync.ui.policy.R46UiErrorArea
import com.sdvsync.ui.viewmodels.DashboardViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSaveClick: (String, Boolean, Boolean) -> Unit,
    onSyncLogClick: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onBackupsClick: (String) -> Unit = {},
    onViewSaveClick: (String) -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
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
    LaunchedEffect(Unit) {
        val activity = toastContext as? com.sdvsync.MainActivity
        val pendingUri = activity?.consumePendingImportUri()
        if (pendingUri != null) {
            viewModel.previewImport(pendingUri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
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
        StardewDialog(
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
                StardewButton(
                    onClick = { viewModel.confirmImport() },
                    variant = StardewButtonVariant.Action
                ) {
                    Text(stringResource(R.string.import_confirm))
                }
            },
            dismissButton = {
                StardewOutlinedButton(onClick = { viewModel.dismissImportPreview() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.dashboard_title),
                actions = {
                    if (state.offlineMode) {
                        TextButton(onClick = onSignInClick) { Text("Steam login") }
                    }
                    PixelIconButton(
                        pixelData = ImportData,
                        onClick = {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                        contentDescription = stringResource(R.string.import_button)
                    )
                    PixelIconButton(
                        pixelData = RefreshData,
                        onClick = { viewModel.refresh(isUserRefresh = true) },
                        contentDescription = stringResource(R.string.action_refresh)
                    )
                    PixelIconButton(
                        pixelData = ClockData,
                        onClick = onSyncLogClick,
                        contentDescription = stringResource(R.string.sync_log_title)
                    )
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(isUserRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (SdvSyncThemeExtras.isEarthy) {
                            R44BStatePanel(
                                kind = R44BStateKind.LOADING,
                                title = "Loading your saves",
                                description = "Checking local save information. No sync has been started."
                            )
                        } else {
                            PixelLoadingSpinner(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center) {
                        R46ErrorPanel(
                            area = R46UiErrorArea.SAVES,
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }
                state.saves.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmptyState(
                            title = stringResource(R.string.dashboard_no_saves),
                            subtitle = if (state.offlineMode) "Offline: no local saves found. Check your file access in Settings."
                            else "Pull to refresh or check your save files",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            R45SectionIntro(
                                "Saves",
                                "Review your saves, backups and sync status. Changes require explicit action."
                            )
                        }
                        item {
                            R45AreaGuide(R45GuideArea.SAVES)
                        }
                        if (state.isStagingMode) {
                            item {
                                StardewCard {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            stringResource(R.string.staging_mode_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            stringResource(R.string.staging_mode_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (state.offlineMode) {
                            item {
                                StardewCard {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("Offline mode — local saves only", style = MaterialTheme.typography.titleSmall)
                                        Text("Steam Cloud requires sign-in. Existing local files remain available.")
                                        Spacer(Modifier.height(8.dp))
                                        StardewOutlinedButton(onClick = onSignInClick) { Text("Sign in to Steam") }
                                    }
                                }
                            }
                        }
                        itemsIndexed(state.saves) { index, save ->
                            StaggeredAnimatedItem(index = index) {
                                Column {
                                    SaveCard(
                                        save = save,
                                        onClick = {
                                            if (state.offlineMode) onViewSaveClick(save.folderName)
                                            else onSaveClick(save.folderName, save.hasCloud, save.hasLocal)
                                        }
                                    )
                                    if (state.offlineMode && save.hasLocal) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            StardewOutlinedButton(onClick = { onViewSaveClick(save.folderName) }) {
                                                Text("View local save")
                                            }
                                            StardewOutlinedButton(onClick = { onBackupsClick(save.folderName) }) {
                                                Text("Backups")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
