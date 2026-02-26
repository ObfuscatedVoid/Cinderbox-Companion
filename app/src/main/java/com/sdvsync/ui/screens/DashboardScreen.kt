package com.sdvsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.ui.animation.StaggeredAnimatedItem
import com.sdvsync.ui.components.ClockData
import com.sdvsync.ui.components.EmptyState
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.RefreshData
import com.sdvsync.ui.components.SaveCard
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.viewmodels.DashboardViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSaveClick: (String, Boolean, Boolean) -> Unit,
    onSyncLogClick: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.dashboard_title),
                actions = {
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
                        PixelLoadingSpinner(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            StardewButton(onClick = { viewModel.refresh() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
                state.saves.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmptyState(
                            title = stringResource(R.string.dashboard_no_saves),
                            subtitle = "Pull to refresh or check your save files",
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
                        itemsIndexed(state.saves) { index, save ->
                            StaggeredAnimatedItem(index = index) {
                                SaveCard(
                                    save = save,
                                    onClick = { onSaveClick(save.folderName, save.hasCloud, save.hasLocal) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
