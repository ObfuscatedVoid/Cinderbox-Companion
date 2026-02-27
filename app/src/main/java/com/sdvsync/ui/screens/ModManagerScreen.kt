package com.sdvsync.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.mods.models.ModProfile
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
import com.sdvsync.ui.components.StardewDialog
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.components.UpdateBanner
import com.sdvsync.ui.components.pixelBorder
import com.sdvsync.ui.viewmodels.ModFilter
import com.sdvsync.ui.viewmodels.ModManagerViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModManagerScreen(
    onBrowseClick: () -> Unit = {},
    onModClick: (String) -> Unit = {},
    viewModel: ModManagerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // File picker for zip import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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

    // Show profile message toast
    LaunchedEffect(state.profileMessage) {
        state.profileMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearProfileMessage()
        }
    }

    // Profile dialogs state
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var showApplyProfileConfirm by remember { mutableStateOf<ModProfile?>(null) }
    var showDeleteProfileConfirm by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = stringResource(R.string.mods_title),
                actions = {
                    PixelIconButton(
                        pixelData = RefreshData,
                        onClick = { viewModel.checkForUpdates() },
                        contentDescription = stringResource(R.string.mods_check_update)
                    )
                    PixelIconButton(
                        pixelData = SearchData,
                        onClick = onBrowseClick,
                        contentDescription = stringResource(R.string.mods_browse_title)
                    )
                    PixelIconButton(
                        pixelData = FolderData,
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                        contentDescription = stringResource(R.string.mods_import)
                    )
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { viewModel.loadInstalledMods() },
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
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            EmptyState(
                                title = stringResource(R.string.mods_empty_title),
                                subtitle = stringResource(R.string.mods_empty_subtitle)
                            )
                            Spacer(Modifier.height(24.dp))
                            StardewButton(
                                onClick = onBrowseClick,
                                variant = StardewButtonVariant.Gold
                            ) {
                                Text(stringResource(R.string.mods_browse_button))
                            }
                            Spacer(Modifier.height(12.dp))
                            StardewButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                                }
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Profiles section
                        if (state.profiles.isNotEmpty() || state.installedMods.isNotEmpty()) {
                            item {
                                ProfileSection(
                                    profiles = state.profiles,
                                    activeProfileName = state.activeProfileName,
                                    isApplying = state.isApplyingProfile,
                                    onSaveCurrent = { showSaveProfileDialog = true },
                                    onApply = { showApplyProfileConfirm = it },
                                    onDelete = { showDeleteProfileConfirm = it }
                                )
                            }
                        }

                        // Search bar
                        item {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(stringResource(R.string.mods_search_installed_hint)) },
                                singleLine = true,
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Filter chips
                        item {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val filterChipColors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                FilterChip(
                                    selected = state.filter == ModFilter.ALL,
                                    onClick = { viewModel.setFilter(ModFilter.ALL) },
                                    label = { Text(stringResource(R.string.mods_filter_all)) },
                                    colors = filterChipColors
                                )
                                FilterChip(
                                    selected = state.filter == ModFilter.ENABLED,
                                    onClick = { viewModel.setFilter(ModFilter.ENABLED) },
                                    label = { Text(stringResource(R.string.mods_filter_enabled)) },
                                    colors = filterChipColors
                                )
                                FilterChip(
                                    selected = state.filter == ModFilter.DISABLED,
                                    onClick = { viewModel.setFilter(ModFilter.DISABLED) },
                                    label = { Text(stringResource(R.string.mods_filter_disabled)) },
                                    colors = filterChipColors
                                )
                                if (state.updates.isNotEmpty()) {
                                    FilterChip(
                                        selected = state.filter == ModFilter.HAS_UPDATE,
                                        onClick = { viewModel.setFilter(ModFilter.HAS_UPDATE) },
                                        label = { Text(stringResource(R.string.mods_filter_has_update)) },
                                        colors = filterChipColors
                                    )
                                }
                            }
                        }

                        // Update checking indicator
                        if (state.isCheckingUpdates) {
                            item {
                                StardewCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            stringResource(R.string.mods_checking_updates),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Update banner
                        val updateCount = state.updates.size
                        if (updateCount > 0 && state.filter != ModFilter.HAS_UPDATE) {
                            item {
                                UpdateBanner(
                                    updateCount = updateCount,
                                    onClick = { viewModel.setFilter(ModFilter.HAS_UPDATE) }
                                )
                            }
                        }

                        if (state.displayedMods.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.mods_no_matching),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        itemsIndexed(state.displayedMods, key = { _, mod -> mod.manifest.uniqueID }) { index, mod ->
                            StaggeredAnimatedItem(index = index) {
                                ModCard(
                                    mod = mod,
                                    hasUpdate = state.updates.containsKey(mod.manifest.uniqueID),
                                    onToggle = { enabled ->
                                        viewModel.toggleMod(
                                            mod.folderName,
                                            enabled
                                        )
                                    },
                                    onClick = { onModClick(mod.manifest.uniqueID) }
                                )
                            }
                        }

                        // Browse mods button at bottom
                        item {
                            Spacer(Modifier.height(8.dp))
                            StardewButton(
                                onClick = onBrowseClick,
                                variant = StardewButtonVariant.Gold,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.mods_browse_button))
                            }
                        }
                    }
                }
            }
        }
    }

    // Save profile dialog
    if (showSaveProfileDialog) {
        SaveProfileDialog(
            onDismiss = { showSaveProfileDialog = false },
            onSave = { name ->
                showSaveProfileDialog = false
                viewModel.saveCurrentAsProfile(name)
            }
        )
    }

    // Apply profile confirmation
    showApplyProfileConfirm?.let { profile ->
        StardewDialog(
            onDismissRequest = { showApplyProfileConfirm = null },
            title = stringResource(R.string.profiles_apply_confirm_title),
            text = stringResource(R.string.profiles_apply_confirm_message, profile.name),
            confirmButton = {
                StardewButton(onClick = {
                    showApplyProfileConfirm = null
                    viewModel.applyProfile(profile)
                }) {
                    Text(stringResource(R.string.sync_confirm_continue))
                }
            },
            dismissButton = {
                StardewOutlinedButton(onClick = { showApplyProfileConfirm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Delete profile confirmation
    showDeleteProfileConfirm?.let { name ->
        StardewDialog(
            onDismissRequest = { showDeleteProfileConfirm = null },
            title = stringResource(R.string.profiles_delete_title),
            text = stringResource(R.string.profiles_delete_message, name),
            confirmButton = {
                StardewButton(
                    onClick = {
                        showDeleteProfileConfirm = null
                        viewModel.deleteProfile(name)
                    },
                    variant = StardewButtonVariant.Danger
                ) {
                    Text(stringResource(R.string.profiles_delete_button))
                }
            },
            dismissButton = {
                StardewOutlinedButton(onClick = { showDeleteProfileConfirm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileSection(
    profiles: List<ModProfile>,
    activeProfileName: String?,
    isApplying: Boolean,
    onSaveCurrent: () -> Unit,
    onApply: (ModProfile) -> Unit,
    onDelete: (String) -> Unit
) {
    StardewCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.profiles_title),
                    style = MaterialTheme.typography.titleSmall
                )
                StardewOutlinedButton(onClick = onSaveCurrent) {
                    Text(stringResource(R.string.profiles_save_current))
                }
            }

            if (activeProfileName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.profiles_active, activeProfileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (profiles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.name == activeProfileName,
                            onClick = {
                                if (profile.name != activeProfileName) {
                                    onApply(profile)
                                }
                            },
                            label = { Text(profile.name) },
                            enabled = !isApplying,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (profile.name != activeProfileName) {
                                        onApply(profile)
                                    }
                                },
                                onLongClick = { onDelete(profile.name) }
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveProfileDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_save_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.profiles_save_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.profiles_save_hint)) },
                    singleLine = true,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            StardewButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.profiles_save_button))
            }
        },
        dismissButton = {
            StardewOutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.pixelBorder()
    )
}
