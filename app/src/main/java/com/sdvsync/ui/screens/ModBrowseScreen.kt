package com.sdvsync.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdvsync.R
import com.sdvsync.ui.components.AppButton
import com.sdvsync.ui.components.AppCard
import com.sdvsync.ui.components.AppIcon
import com.sdvsync.ui.components.AppIconButton
import com.sdvsync.ui.components.AppIcons
import com.sdvsync.ui.components.AppTopBar
import com.sdvsync.ui.components.BrowseModCard
import com.sdvsync.ui.components.ButtonVariant
import com.sdvsync.ui.components.LoadingIndicator
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
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            AppTopBar(
                title = stringResource(R.string.mods_browse_title),
                navigationIcon = {
                    AppIconButton(
                        imageVector = AppIcons.Back,
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
                    AppCard {
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
                                shape = MaterialTheme.shapes.large,
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
                            AppButton(
                                onClick = { viewModel.validateAndSaveApiKey(apiKeyInput) },
                                enabled = apiKeyInput.isNotBlank() && !state.isValidatingKey,
                                variant = ButtonVariant.Primary
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
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    isError = state.searchNeedsMoreCharacters,
                    supportingText = if (state.searchNeedsMoreCharacters) {
                        { Text(stringResource(R.string.mods_search_minimum_characters)) }
                    } else {
                        null
                    },
                    leadingIcon = {
                        AppIcon(
                            imageVector = AppIcons.Search,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        LoadingIndicator()
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
                                AppButton(onClick = viewModel::retry) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }

                            NexusErrorAction.REPLACE_API_KEY -> {
                                Spacer(Modifier.height(16.dp))
                                AppButton(onClick = viewModel::removeApiKey) {
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
                itemsIndexed(state.mods, key = { _, mod -> "${mod.sourceId}/${mod.modId}" }) { _, mod ->
                    BrowseModCard(
                        mod = mod,
                        isInstalled = state.installedSourceIds.contains(
                            remoteModSourceIdentifier(mod.sourceId, mod.modId)
                        ),
                        onClick = { onModClick(mod.modId, mod.sourceId) }
                    )
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
                            state.isLoadingMore -> LoadingIndicator(size = 24.dp)
                            loadMoreError != null -> {
                                Text(
                                    stringResource(loadMoreError.messageRes),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                when (loadMoreError.action) {
                                    NexusErrorAction.RETRY -> {
                                        Spacer(Modifier.height(8.dp))
                                        AppButton(onClick = viewModel::loadMore) {
                                            Text(stringResource(R.string.action_retry))
                                        }
                                    }

                                    NexusErrorAction.REPLACE_API_KEY -> {
                                        Spacer(Modifier.height(8.dp))
                                        AppButton(onClick = viewModel::removeApiKey) {
                                            Text(stringResource(R.string.mods_api_key_replace))
                                        }
                                    }

                                    NexusErrorAction.NONE -> Unit
                                }
                            }

                            else -> AppButton(onClick = viewModel::loadMore) {
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
