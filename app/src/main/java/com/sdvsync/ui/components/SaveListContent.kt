package com.sdvsync.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.ui.viewmodels.DashboardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveListContent(
    state: DashboardState,
    onRefresh: () -> Unit,
    onSaveClick: (String, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("save-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading && state.saves.isEmpty()) {
                item(key = "loading") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            } else {
                state.error?.let { error ->
                    item(key = "error") {
                        AppCard {
                            Column(Modifier.padding(16.dp)) {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                AppOutlinedButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }
                }
                if (state.isStagingMode) {
                    item(key = "staging") {
                        AppCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.staging_mode_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    stringResource(R.string.staging_mode_description),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                if (state.saves.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.dashboard_no_saves),
                            subtitle = stringResource(R.string.dashboard_empty_hint),
                            modifier = Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().padding(24.dp)
                        )
                    }
                }
                items(state.saves, key = { it.folderName }) { save ->
                    SaveCard(save, onClick = { onSaveClick(save.folderName, save.hasCloud, save.hasLocal) })
                }
            }
        }
    }
}
