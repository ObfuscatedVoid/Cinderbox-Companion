package com.sdvsync.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.ui.animation.StaggeredAnimatedItem
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.BrowseModCard
import com.sdvsync.ui.components.PixelIcon
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.SearchData
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.viewmodels.BrowseCategory
import com.sdvsync.ui.viewmodels.ModBrowseViewModel
import com.sdvsync.ui.viewmodels.NexusErrorAction
import com.sdvsync.ui.viewmodels.remoteModSourceIdentifier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModBrowseScreen(
    viewModel: ModBrowseViewModel,
    onBack: () -> Unit,
    onModClick: (modId: String, source: String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val browseError = state.error
    val loadMoreError = state.loadMoreError
    var apiKeyInput by remember { mutableStateOf("") }

    LaunchedEffect(state.hasApiKey) {
        if (state.hasApiKey) {
            apiKeyInput = ""
        }
    }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.mods_browse_title),
                navigationIcon = {
                    PixelIconButton(
                        pixelData = ArrowLeftData,
                        onClick = onBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.hasApiKey) {
                item {
                    StardewCard {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.mods_api_key_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.mods_api_key_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text(stringResource(R.string.mods_api_key_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RectangleShape,
                                singleLine = true
                            )
                            state.apiKeyErrorRes?.let { apiKeyErrorRes ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(apiKeyErrorRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            StardewButton(
                                onClick = { viewModel.validateAndSaveApiKey(apiKeyInput) },
                                enabled = apiKeyInput.isNotBlank() && !state.isValidatingKey,
                                variant = StardewButtonVariant.Action
                            ) {
                                if (state.isValidatingKey) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.mods_api_key_save))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.mods_api_key_get),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDecoration = TextDecoration.Underline
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://www.nexusmods.com/users/myaccount?tab=api+access")
                                }
                            )
                        }
                    }
                }
                return@LazyColumn
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.search(it) },
                    placeholder = { Text(stringResource(R.string.mods_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    singleLine = true,
                    isError = state.searchNeedsMoreCharacters,
                    supportingText = if (state.searchNeedsMoreCharacters) {
                        { Text(stringResource(R.string.mods_search_minimum_characters)) }
                    } else {
                        null
                    },
                    leadingIcon = {
                        PixelIcon(
                            pixelData = SearchData,
                            palette = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            size = 16.dp
                        )
                    }
                )
            }

            if (state.searchQuery.isBlank()) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.category == BrowseCategory.TRENDING,
                            onClick = { viewModel.loadCategory(BrowseCategory.TRENDING) },
                            label = { Text(stringResource(R.string.mods_trending)) }
                        )
                        FilterChip(
                            selected = state.category == BrowseCategory.LATEST,
                            onClick = { viewModel.loadCategory(BrowseCategory.LATEST) },
                            label = { Text(stringResource(R.string.mods_latest)) }
                        )
                        FilterChip(
                            selected = state.category == BrowseCategory.RECENTLY_UPDATED,
                            onClick = { viewModel.loadCategory(BrowseCategory.RECENTLY_UPDATED) },
                            label = { Text(stringResource(R.string.mods_recently_updated)) }
                        )
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelLoadingSpinner()
                    }
                }
            } else if (browseError != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(browseError.messageRes),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        when (browseError.action) {
                            NexusErrorAction.RETRY -> {
                                Spacer(Modifier.height(16.dp))
                                StardewButton(onClick = viewModel::retry) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }

                            NexusErrorAction.REPLACE_API_KEY -> {
                                Spacer(Modifier.height(16.dp))
                                StardewButton(onClick = viewModel::removeApiKey) {
                                    Text(stringResource(R.string.mods_api_key_replace))
                                }
                            }

                            NexusErrorAction.NONE -> Unit
                        }
                    }
                }
            } else if (state.mods.isEmpty() && !state.searchNeedsMoreCharacters) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.mods_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(state.mods) { index, mod ->
                    StaggeredAnimatedItem(index = index) {
                        BrowseModCard(
                            mod = mod,
                            isInstalled = state.installedSourceIds.contains(
                                remoteModSourceIdentifier(mod.sourceId, mod.modId)
                            ),
                            onClick = { onModClick(mod.modId, mod.sourceId) }
                        )
                    }
                }
            }

            if (
                state.searchQuery.isNotBlank() &&
                !state.isLoading &&
                browseError == null &&
                (state.hasMore || state.isLoadingMore || loadMoreError != null)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when {
                            state.isLoadingMore -> PixelLoadingSpinner(size = 24.dp)
                            loadMoreError != null -> {
                                Text(
                                    stringResource(loadMoreError.messageRes),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                when (loadMoreError.action) {
                                    NexusErrorAction.RETRY -> {
                                        Spacer(Modifier.height(8.dp))
                                        StardewButton(onClick = viewModel::loadMore) {
                                            Text(stringResource(R.string.action_retry))
                                        }
                                    }

                                    NexusErrorAction.REPLACE_API_KEY -> {
                                        Spacer(Modifier.height(8.dp))
                                        StardewButton(onClick = viewModel::removeApiKey) {
                                            Text(stringResource(R.string.mods_api_key_replace))
                                        }
                                    }

                                    NexusErrorAction.NONE -> Unit
                                }
                            }

                            else -> StardewButton(onClick = viewModel::loadMore) {
                                Text(stringResource(R.string.mods_load_more))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.removeApiKey() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.mods_api_key_remove),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
