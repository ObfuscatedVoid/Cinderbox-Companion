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
import com.sdvsync.ui.components.EmptyState
import com.sdvsync.ui.components.FolderData
import com.sdvsync.ui.components.ModCard
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.RefreshData
import com.sdvsync.ui.components.SearchData
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.components.UpdateBanner
import com.sdvsync.ui.viewmodels.ModManagerViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModManagerScreen(
    onBrowseClick: () -> Unit = {},
    onModClick: (String) -> Unit = {},
    viewModel: ModManagerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // File picker for zip import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(uri)
        }
    }

    // Show import result toast
    LaunchedEffect(state.importMessage) {
        state.importMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.mods_title),
                actions = {
                    PixelIconButton(
                        pixelData = RefreshData,
                        onClick = { viewModel.checkForUpdates() },
                        contentDescription = stringResource(R.string.mods_check_update),
                    )
                    PixelIconButton(
                        pixelData = SearchData,
                        onClick = onBrowseClick,
                        contentDescription = stringResource(R.string.mods_browse_title),
                    )
                    PixelIconButton(
                        pixelData = FolderData,
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                        contentDescription = stringResource(R.string.mods_import),
                    )
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { viewModel.loadInstalledMods() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PixelLoadingSpinner(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(16.dp))
                            StardewButton(onClick = { viewModel.loadInstalledMods() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }

                state.installedMods.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EmptyState(
                                title = stringResource(R.string.mods_empty_title),
                                subtitle = stringResource(R.string.mods_empty_subtitle),
                            )
                            Spacer(Modifier.height(24.dp))
                            StardewButton(
                                onClick = onBrowseClick,
                                variant = StardewButtonVariant.Gold,
                            ) {
                                Text(stringResource(R.string.mods_browse_button))
                            }
                            Spacer(Modifier.height(12.dp))
                            StardewButton(
                                onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                            ) {
                                Text(stringResource(R.string.mods_import_button))
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Update checking indicator
                        if (state.isCheckingUpdates) {
                            item {
                                StardewCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            stringResource(R.string.mods_checking_updates),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // Update banner
                        val updateCount = state.updates.size
                        if (updateCount > 0) {
                            item {
                                UpdateBanner(
                                    updateCount = updateCount,
                                    onClick = { /* TODO: show update list */ },
                                )
                            }
                        }

                        itemsIndexed(state.installedMods) { index, mod ->
                            StaggeredAnimatedItem(index = index) {
                                ModCard(
                                    mod = mod,
                                    hasUpdate = state.updates.containsKey(mod.manifest.uniqueID),
                                    onToggle = { enabled ->
                                        viewModel.toggleMod(
                                            mod.folderName,
                                            enabled,
                                        )
                                    },
                                    onClick = { onModClick(mod.manifest.uniqueID) },
                                )
                            }
                        }

                        // Browse mods button at bottom
                        item {
                            Spacer(Modifier.height(8.dp))
                            StardewButton(
                                onClick = onBrowseClick,
                                variant = StardewButtonVariant.Gold,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.mods_browse_button))
                            }
                        }
                    }
                }
            }
        }
    }
}
