package com.sdvsync.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.ui.animation.StaggeredAnimatedItem
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.BrowseModCard
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.SearchData
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.viewmodels.BrowseCategory
import com.sdvsync.ui.viewmodels.ModBrowseViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModBrowseScreen(
    viewModel: ModBrowseViewModel,
    onBack: () -> Unit,
    onModClick: (modId: String, source: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.mods_browse_title),
                navigationIcon = {
                    PixelIconButton(
                        pixelData = ArrowLeftData,
                        onClick = onBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // API key setup card
            if (!state.hasApiKey) {
                item {
                    StardewCard {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.mods_api_key_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.mods_api_key_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text(stringResource(R.string.mods_api_key_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RectangleShape,
                                singleLine = true,
                            )
                            if (state.apiKeyError != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    state.apiKeyError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            StardewButton(
                                onClick = { viewModel.validateAndSaveApiKey(apiKeyInput) },
                                enabled = apiKeyInput.isNotBlank() && !state.isValidatingKey,
                                variant = StardewButtonVariant.Action,
                            ) {
                                if (state.isValidatingKey) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.mods_api_key_save))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.mods_api_key_get),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDecoration = TextDecoration.Underline,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.nexusmods.com/users/myaccount?tab=api+access"),
                                    )
                                    context.startActivity(intent)
                                },
                            )
                        }
                    }
                }
                return@LazyColumn
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.search(it) },
                    placeholder = { Text(stringResource(R.string.mods_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    singleLine = true,
                    leadingIcon = {
                        PixelIconButton(
                            pixelData = SearchData,
                            onClick = {},
                            contentDescription = "Search",
                            size = 16.dp,
                        )
                    },
                )
            }

            // Category chips
            if (state.searchQuery.isBlank()) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.category == BrowseCategory.TRENDING,
                            onClick = { viewModel.loadCategory(BrowseCategory.TRENDING) },
                            label = { Text(stringResource(R.string.mods_trending)) },
                        )
                        FilterChip(
                            selected = state.category == BrowseCategory.LATEST,
                            onClick = { viewModel.loadCategory(BrowseCategory.LATEST) },
                            label = { Text(stringResource(R.string.mods_latest)) },
                        )
                        FilterChip(
                            selected = state.category == BrowseCategory.RECENTLY_UPDATED,
                            onClick = { viewModel.loadCategory(BrowseCategory.RECENTLY_UPDATED) },
                            label = { Text(stringResource(R.string.mods_recently_updated)) },
                        )
                    }
                }
            }

            // Loading / Error / Results
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelLoadingSpinner()
                    }
                }
            } else if (state.error != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(16.dp))
                        StardewButton(
                            onClick = { viewModel.loadCategory(state.category) },
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            } else if (state.mods.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.mods_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(state.mods) { index, mod ->
                    StaggeredAnimatedItem(index = index) {
                        BrowseModCard(
                            mod = mod,
                            isInstalled = state.installedUniqueIds.contains(mod.name.lowercase()),
                            onClick = { onModClick(mod.modId, mod.sourceId) },
                        )
                    }
                }
            }

            // Remove API key option at bottom
            item {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.removeApiKey() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.mods_api_key_remove),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
